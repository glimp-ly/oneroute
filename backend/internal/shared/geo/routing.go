package geo

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const (
	defaultOSRMTimeout   = 8 * time.Second
	mockAverageSpeedKmH  = 25.0
	mockRoadFactor       = 1.25
	mockMinDistanceKm    = 0.1
	maxOSRMResponseBytes = 10 << 20
)

// RoutingProvider computes road or geodesic routes between an origin and destination.
type RoutingProvider interface {
	ComputeRoute(ctx context.Context, origin, destination Point, waypoints []Point) (*RouteGeometry, error)
}

// OSRMProvider implements RoutingProvider against an OSRM HTTP API server.
type OSRMProvider struct {
	baseURL string
	client  *http.Client
}

// MockRoutingProvider implements RoutingProvider offline using geodesic segments.
type MockRoutingProvider struct{}

var (
	_ RoutingProvider = (*OSRMProvider)(nil)
	_ RoutingProvider = (*MockRoutingProvider)(nil)
)

// NewOSRMProvider constructs an OSRMProvider with the given base URL and HTTP timeout.
func NewOSRMProvider(baseURL string, timeout time.Duration) (*OSRMProvider, error) {
	trimmed := strings.TrimRight(strings.TrimSpace(baseURL), "/")
	if trimmed == "" {
		return nil, errors.New("geo: OSRM base URL cannot be empty")
	}
	parsed, err := url.Parse(trimmed)
	if err != nil || (parsed.Scheme != "http" && parsed.Scheme != "https") || parsed.Host == "" {
		return nil, fmt.Errorf("geo: invalid OSRM base URL %q", baseURL)
	}
	if timeout <= 0 {
		timeout = defaultOSRMTimeout
	}

	return &OSRMProvider{
		baseURL: trimmed,
		client:  &http.Client{Timeout: timeout},
	}, nil
}

// NewMockRoutingProvider constructs an offline MockRoutingProvider.
func NewMockRoutingProvider() *MockRoutingProvider {
	return &MockRoutingProvider{}
}

// NewRoutingProvider builds a RoutingProvider for "osrm_public", "osrm_local", or "mock".
func NewRoutingProvider(providerType, osrmPublicURL, osrmLocalURL string, timeout time.Duration) (RoutingProvider, error) {
	switch strings.ToLower(strings.TrimSpace(providerType)) {
	case "osrm_public":
		return NewOSRMProvider(osrmPublicURL, timeout)
	case "osrm_local":
		return NewOSRMProvider(osrmLocalURL, timeout)
	case "mock":
		return NewMockRoutingProvider(), nil
	default:
		return nil, fmt.Errorf("geo: unsupported routing provider %q", providerType)
	}
}

type osrmResponse struct {
	Code    string      `json:"code"`
	Message string      `json:"message"`
	Routes  []osrmRoute `json:"routes"`
}

type osrmRoute struct {
	Geometry json.RawMessage `json:"geometry"`
	Distance float64         `json:"distance"`
	Duration float64         `json:"duration"`
}

type geoJSONLineString struct {
	Type        string      `json:"type"`
	Coordinates [][]float64 `json:"coordinates"`
}

// ComputeRoute requests a driving route from OSRM and returns its geometry and summary metrics.
func (p *OSRMProvider) ComputeRoute(ctx context.Context, origin, destination Point, waypoints []Point) (*RouteGeometry, error) {
	allPts, err := collectAndValidatePoints(origin, destination, waypoints)
	if err != nil {
		return nil, err
	}

	coordParts := make([]string, len(allPts))
	for i, pt := range allPts {
		coordParts[i] = formatCoord(pt.Lng) + "," + formatCoord(pt.Lat)
	}

	endpoint := fmt.Sprintf(
		"%s/route/v1/driving/%s?overview=full&geometries=geojson&steps=false",
		p.baseURL,
		strings.Join(coordParts, ";"),
	)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, fmt.Errorf("geo: build OSRM request: %w", err)
	}

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("geo: OSRM request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, maxOSRMResponseBytes))
	if err != nil {
		return nil, fmt.Errorf("geo: read OSRM response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("geo: OSRM returned HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}

	var parsed osrmResponse
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, fmt.Errorf("geo: decode OSRM response: %w", err)
	}
	if parsed.Code != "Ok" {
		return nil, fmt.Errorf("geo: OSRM error code %q: %s", parsed.Code, parsed.Message)
	}
	if len(parsed.Routes) == 0 {
		return nil, errors.New("geo: OSRM returned no routes")
	}

	route := parsed.Routes[0]
	var gj geoJSONLineString
	if err := json.Unmarshal(route.Geometry, &gj); err != nil {
		return nil, fmt.Errorf("geo: decode OSRM geometry: %w", err)
	}
	if !strings.EqualFold(gj.Type, "LineString") {
		return nil, fmt.Errorf("geo: expected GeoJSON LineString, got %q", gj.Type)
	}
	if len(gj.Coordinates) < 2 {
		return nil, ErrInsufficientPoints
	}

	routePts := make([]Point, len(gj.Coordinates))
	for i, pair := range gj.Coordinates {
		if len(pair) < 2 {
			return nil, fmt.Errorf("geo: invalid coordinate pair at index %d", i)
		}
		pt := Point{Lat: pair[1], Lng: pair[0]}
		if err := validatePoint(pt); err != nil {
			return nil, fmt.Errorf("geo: decoded coordinate at index %d: %w", i, err)
		}
		routePts[i] = pt
	}

	wkt, err := PointsToLineStringEWKT(routePts)
	if err != nil {
		return nil, err
	}

	distKm := route.Distance / 1000.0
	durMin := durationMinutes(route.Duration / 60.0)

	return &RouteGeometry{
		LineStringWKT: wkt,
		GeoJSON:       route.Geometry,
		DistanceKm:    distKm,
		DurationMin:   durMin,
		Waypoints:     routePts,
	}, nil
}

// ComputeRoute computes a geodesic straight-line route (including waypoints) offline.
func (p *MockRoutingProvider) ComputeRoute(ctx context.Context, origin, destination Point, waypoints []Point) (*RouteGeometry, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	pts, err := collectAndValidatePoints(origin, destination, waypoints)
	if err != nil {
		return nil, err
	}

	wkt, err := PointsToLineStringEWKT(pts)
	if err != nil {
		return nil, err
	}

	rawKm := 0.0
	hasDistinct := false
	coords := make([][]float64, len(pts))
	for i, pt := range pts {
		coords[i] = []float64{pt.Lng, pt.Lat}
		if i > 0 {
			if pts[i-1] != pt {
				hasDistinct = true
			}
			rawKm += HaversineKm(pts[i-1], pt)
		}
	}

	distKm := rawKm * mockRoadFactor
	if hasDistinct && distKm < mockMinDistanceKm {
		distKm = mockMinDistanceKm
	}

	durMin := durationMinutes((distKm / mockAverageSpeedKmH) * 60.0)
	if distKm > 0 && durMin < 1 {
		durMin = 1
	}

	rawGeoJSON, err := json.Marshal(geoJSONLineString{
		Type:        "LineString",
		Coordinates: coords,
	})
	if err != nil {
		return nil, fmt.Errorf("geo: marshal mock GeoJSON: %w", err)
	}

	return &RouteGeometry{
		LineStringWKT: wkt,
		GeoJSON:       rawGeoJSON,
		DistanceKm:    distKm,
		DurationMin:   durMin,
		Waypoints:     pts,
	}, nil
}

func collectAndValidatePoints(origin, destination Point, waypoints []Point) ([]Point, error) {
	if err := validatePoint(origin); err != nil {
		return nil, fmt.Errorf("geo: origin: %w", err)
	}
	for i, wp := range waypoints {
		if err := validatePoint(wp); err != nil {
			return nil, fmt.Errorf("geo: waypoint %d: %w", i, err)
		}
	}
	if err := validatePoint(destination); err != nil {
		return nil, fmt.Errorf("geo: destination: %w", err)
	}

	pts := make([]Point, 0, 2+len(waypoints))
	pts = append(pts, origin)
	pts = append(pts, waypoints...)
	pts = append(pts, destination)
	return pts, nil
}

func durationMinutes(minutes float64) int {
	if minutes <= 0 {
		return 0
	}
	rounded := int(math.Round(minutes))
	if rounded < 1 {
		return 1
	}
	return rounded
}
