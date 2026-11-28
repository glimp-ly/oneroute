package geo

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"strconv"
	"strings"

	"github.com/jackc/pgx/v5"
)

const earthRadiusKm = 6371.0

var (
	ErrInsufficientPoints  = errors.New("geo: linestring requires at least 2 points")
	ErrInvalidCoordinate   = errors.New("geo: invalid WGS84 coordinate")
	ErrInvalidFraction     = errors.New("geo: route fraction must be finite and in [0, 1]")
	ErrPickupBehindVehicle = errors.New("geo: pickup fraction cannot be behind vehicle fraction")
	ErrDropoffNotAhead     = errors.New("geo: dropoff fraction must be strictly greater than pickup fraction")
	ErrInvalidSegmentRange = errors.New("geo: segment start fraction cannot exceed end fraction")
)

// Point represents a WGS84 geographic coordinate.
type Point struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

// RouteGeometry holds a computed route polyline and its summary metrics.
type RouteGeometry struct {
	LineStringWKT string          `json:"line_string_wkt"`
	GeoJSON       json.RawMessage `json:"geojson"`
	DistanceKm    float64         `json:"distance_km"`
	DurationMin   int             `json:"duration_min"`
	Waypoints     []Point         `json:"waypoints"`
}

// Projection represents the normalized position and perpendicular detour of a point relative to a route.
type Projection struct {
	Fraction float64 `json:"fraction"`
	DetourKm float64 `json:"detour_km"`
}

// QueryRower abstracts pgxpool.Pool, pgx.Conn, and pgx.Tx for PostGIS spatial queries.
type QueryRower interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

func validatePoint(p Point) error {
	if math.IsNaN(p.Lat) || math.IsInf(p.Lat, 0) || math.IsNaN(p.Lng) || math.IsInf(p.Lng, 0) {
		return fmt.Errorf("%w: non-finite value (%v, %v)", ErrInvalidCoordinate, p.Lat, p.Lng)
	}
	if p.Lat < -90.0 || p.Lat > 90.0 {
		return fmt.Errorf("%w: latitude %v out of [-90, 90]", ErrInvalidCoordinate, p.Lat)
	}
	if p.Lng < -180.0 || p.Lng > 180.0 {
		return fmt.Errorf("%w: longitude %v out of [-180, 180]", ErrInvalidCoordinate, p.Lng)
	}
	return nil
}

func validateFraction(f float64) error {
	if math.IsNaN(f) || math.IsInf(f, 0) || f < 0.0 || f > 1.0 {
		return fmt.Errorf("%w: got %v", ErrInvalidFraction, f)
	}
	return nil
}

func formatCoord(v float64) string {
	return strconv.FormatFloat(v, 'f', -1, 64)
}

// PointToEWKT converts a Point to PostGIS EWKT using longitude-first order: "SRID=4326;POINT(lng lat)".
func PointToEWKT(p Point) string {
	return fmt.Sprintf("SRID=4326;POINT(%s %s)", formatCoord(p.Lng), formatCoord(p.Lat))
}

// PointsToLineStringEWKT converts a slice of at least 2 Points into PostGIS EWKT:
// "SRID=4326;LINESTRING(lng1 lat1, lng2 lat2, ...)".
func PointsToLineStringEWKT(pts []Point) (string, error) {
	if len(pts) < 2 {
		return "", ErrInsufficientPoints
	}

	coords := make([]string, len(pts))
	for i, pt := range pts {
		if err := validatePoint(pt); err != nil {
			return "", fmt.Errorf("geo: point at index %d: %w", i, err)
		}
		coords[i] = formatCoord(pt.Lng) + " " + formatCoord(pt.Lat)
	}

	return "SRID=4326;LINESTRING(" + strings.Join(coords, ", ") + ")", nil
}

// HaversineKm returns the great-circle distance in kilometers between two WGS84 points.
func HaversineKm(a, b Point) float64 {
	if a.Lat == b.Lat && a.Lng == b.Lng {
		return 0
	}

	const degToRad = math.Pi / 180.0
	lat1 := a.Lat * degToRad
	lat2 := b.Lat * degToRad
	dLat := (b.Lat - a.Lat) * degToRad
	dLng := (b.Lng - a.Lng) * degToRad

	sinDLat := math.Sin(dLat / 2.0)
	sinDLng := math.Sin(dLng / 2.0)
	h := sinDLat*sinDLat + math.Cos(lat1)*math.Cos(lat2)*sinDLng*sinDLng
	if h < 0 {
		h = 0
	} else if h > 1 {
		h = 1
	}

	return 2.0 * earthRadiusKm * math.Atan2(math.Sqrt(h), math.Sqrt(1.0-h))
}

// ProjectPointOnRoute projects a point onto a route EWKT linestring in PostGIS,
// returning its normalized route fraction in [0, 1] and geodesic detour distance in km.
func ProjectPointOnRoute(ctx context.Context, q QueryRower, routeGeoEWKT string, p Point) (Projection, error) {
	if q == nil {
		return Projection{}, errors.New("geo: QueryRower cannot be nil")
	}
	if strings.TrimSpace(routeGeoEWKT) == "" {
		return Projection{}, errors.New("geo: route EWKT cannot be empty")
	}
	if err := validatePoint(p); err != nil {
		return Projection{}, err
	}

	const query = `
WITH route AS (
	SELECT ST_GeogFromText($1) AS geometria
),
pt AS (
	SELECT ST_GeogFromText($2) AS punto
)
SELECT
	ST_LineLocatePoint(route.geometria::geometry, pt.punto::geometry)::double precision,
	(ST_Distance(route.geometria, pt.punto) / 1000.0)::double precision
FROM route, pt`

	var proj Projection
	if err := q.QueryRow(ctx, query, routeGeoEWKT, PointToEWKT(p)).Scan(&proj.Fraction, &proj.DetourKm); err != nil {
		return Projection{}, fmt.Errorf("geo: project point on route: %w", err)
	}
	return proj, nil
}

// SegmentDistanceKm computes the geodesic distance in kilometers along the sub-segment
// [fStart, fEnd] of a route EWKT linestring using PostGIS ST_LineSubstring and ST_Length.
func SegmentDistanceKm(ctx context.Context, q QueryRower, routeGeoEWKT string, fStart, fEnd float64) (float64, error) {
	if q == nil {
		return 0, errors.New("geo: QueryRower cannot be nil")
	}
	if strings.TrimSpace(routeGeoEWKT) == "" {
		return 0, errors.New("geo: route EWKT cannot be empty")
	}
	if err := validateFraction(fStart); err != nil {
		return 0, fmt.Errorf("geo: start fraction: %w", err)
	}
	if err := validateFraction(fEnd); err != nil {
		return 0, fmt.Errorf("geo: end fraction: %w", err)
	}
	if fStart > fEnd {
		return 0, fmt.Errorf("%w: start=%v end=%v", ErrInvalidSegmentRange, fStart, fEnd)
	}

	const query = `
SELECT (
	ST_Length(
		ST_LineSubstring(ST_GeogFromText($1)::geometry, $2::double precision, $3::double precision)::geography
	) / 1000.0
)::double precision`

	var distKm float64
	if err := q.QueryRow(ctx, query, routeGeoEWKT, fStart, fEnd).Scan(&distKm); err != nil {
		return 0, fmt.Errorf("geo: segment distance km: %w", err)
	}
	return distKm, nil
}

// ValidateOrderAhead enforces 0 <= vehicleFrac <= pickupFrac < dropoffFrac <= 1.
func ValidateOrderAhead(vehicleFrac, pickupFrac, dropoffFrac float64) error {
	if err := validateFraction(vehicleFrac); err != nil {
		return fmt.Errorf("geo: vehicle fraction: %w", err)
	}
	if err := validateFraction(pickupFrac); err != nil {
		return fmt.Errorf("geo: pickup fraction: %w", err)
	}
	if err := validateFraction(dropoffFrac); err != nil {
		return fmt.Errorf("geo: dropoff fraction: %w", err)
	}
	if pickupFrac < vehicleFrac {
		return fmt.Errorf("%w: vehicle=%v pickup=%v", ErrPickupBehindVehicle, vehicleFrac, pickupFrac)
	}
	if dropoffFrac <= pickupFrac {
		return fmt.Errorf("%w: pickup=%v dropoff=%v", ErrDropoffNotAhead, pickupFrac, dropoffFrac)
	}
	return nil
}
