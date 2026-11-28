package geo_test

import (
	"context"
	"encoding/json"
	"errors"
	"math"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/glimp-ly/oneroute/backend/internal/shared/geo"
)

type stubRow struct {
	scanFn func(dest ...any) error
}

func (r stubRow) Scan(dest ...any) error {
	return r.scanFn(dest...)
}

type stubQueryRower struct {
	lastSQL  string
	lastArgs []any
	row      pgx.Row
}

func (q *stubQueryRower) QueryRow(_ context.Context, sql string, args ...any) pgx.Row {
	q.lastSQL = sql
	q.lastArgs = args
	return q.row
}

func TestPointToEWKT_LngFirstOrder(t *testing.T) {
	tests := []struct {
		name string
		pt   geo.Point
		want string
	}{
		{
			name: "Lima Plaza Mayor coordinates put longitude before latitude",
			pt:   geo.Point{Lat: -12.046374, Lng: -77.042793},
			want: "SRID=4326;POINT(-77.042793 -12.046374)",
		},
		{
			name: "Miraflores Kennedy Park",
			pt:   geo.Point{Lat: -12.1219, Lng: -77.0297},
			want: "SRID=4326;POINT(-77.0297 -12.1219)",
		},
		{
			name: "Origin zero coordinates",
			pt:   geo.Point{Lat: 0, Lng: 0},
			want: "SRID=4326;POINT(0 0)",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got := geo.PointToEWKT(tc.pt)
			if got != tc.want {
				t.Fatalf("PointToEWKT(%+v) = %q, want %q", tc.pt, got, tc.want)
			}
		})
	}
}

func TestPointsToLineStringEWKT(t *testing.T) {
	tests := []struct {
		name    string
		pts     []geo.Point
		want    string
		wantErr bool
	}{
		{
			name: "Valid two-point Lima route",
			pts: []geo.Point{
				{Lat: -12.046374, Lng: -77.042793},
				{Lat: -12.1219, Lng: -77.0297},
			},
			want: "SRID=4326;LINESTRING(-77.042793 -12.046374, -77.0297 -12.1219)",
		},
		{
			name: "Valid three-point Lima route with San Isidro waypoint",
			pts: []geo.Point{
				{Lat: -12.046374, Lng: -77.042793},
				{Lat: -12.0931, Lng: -77.0465},
				{Lat: -12.1219, Lng: -77.0297},
			},
			want: "SRID=4326;LINESTRING(-77.042793 -12.046374, -77.0465 -12.0931, -77.0297 -12.1219)",
		},
		{
			name:    "Empty slice returns error",
			pts:     nil,
			wantErr: true,
		},
		{
			name: "Single point returns error",
			pts: []geo.Point{
				{Lat: -12.046374, Lng: -77.042793},
			},
			wantErr: true,
		},
		{
			name: "Latitude out of range returns error",
			pts: []geo.Point{
				{Lat: -91.0, Lng: -77.042793},
				{Lat: -12.1219, Lng: -77.0297},
			},
			wantErr: true,
		},
		{
			name: "Longitude out of range returns error",
			pts: []geo.Point{
				{Lat: -12.046374, Lng: 181.0},
				{Lat: -12.1219, Lng: -77.0297},
			},
			wantErr: true,
		},
		{
			name: "NaN coordinate returns error",
			pts: []geo.Point{
				{Lat: math.NaN(), Lng: -77.042793},
				{Lat: -12.1219, Lng: -77.0297},
			},
			wantErr: true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got, err := geo.PointsToLineStringEWKT(tc.pts)
			if tc.wantErr {
				if err == nil {
					t.Fatalf("expected error, got nil (value=%q)", got)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if got != tc.want {
				t.Fatalf("PointsToLineStringEWKT() = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestHaversineKm(t *testing.T) {
	plazaMayor := geo.Point{Lat: -12.046374, Lng: -77.042793}
	miraflores := geo.Point{Lat: -12.121900, Lng: -77.029700}

	t.Run("Identical points return zero", func(t *testing.T) {
		if d := geo.HaversineKm(plazaMayor, plazaMayor); d != 0 {
			t.Fatalf("expected 0 km, got %v", d)
		}
	})

	t.Run("Distance between Plaza Mayor and Miraflores is around 8.5 km and symmetric", func(t *testing.T) {
		d1 := geo.HaversineKm(plazaMayor, miraflores)
		d2 := geo.HaversineKm(miraflores, plazaMayor)

		if d1 < 8.0 || d1 > 9.0 {
			t.Fatalf("expected distance between 8.0 and 9.0 km, got %v", d1)
		}
		if math.Abs(d1-d2) > 1e-12 {
			t.Fatalf("expected symmetric distance: %v vs %v", d1, d2)
		}
	})
}

func TestValidateOrderAhead(t *testing.T) {
	tests := []struct {
		name        string
		vehicleFrac float64
		pickupFrac  float64
		dropoffFrac float64
		wantErr     bool
	}{
		{
			name:        "Strictly increasing fractions within [0,1]",
			vehicleFrac: 0.1,
			pickupFrac:  0.25,
			dropoffFrac: 0.85,
			wantErr:     false,
		},
		{
			name:        "Vehicle exactly at pickup and dropoff at end",
			vehicleFrac: 0.0,
			pickupFrac:  0.0,
			dropoffFrac: 1.0,
			wantErr:     false,
		},
		{
			name:        "Pickup behind vehicle fails",
			vehicleFrac: 0.4,
			pickupFrac:  0.3,
			dropoffFrac: 0.8,
			wantErr:     true,
		},
		{
			name:        "Dropoff equal to pickup fails",
			vehicleFrac: 0.1,
			pickupFrac:  0.5,
			dropoffFrac: 0.5,
			wantErr:     true,
		},
		{
			name:        "Dropoff behind pickup fails",
			vehicleFrac: 0.1,
			pickupFrac:  0.6,
			dropoffFrac: 0.4,
			wantErr:     true,
		},
		{
			name:        "Negative vehicle fraction fails",
			vehicleFrac: -0.01,
			pickupFrac:  0.2,
			dropoffFrac: 0.8,
			wantErr:     true,
		},
		{
			name:        "Dropoff above 1 fails",
			vehicleFrac: 0.1,
			pickupFrac:  0.2,
			dropoffFrac: 1.01,
			wantErr:     true,
		},
		{
			name:        "NaN fraction fails",
			vehicleFrac: 0.1,
			pickupFrac:  math.NaN(),
			dropoffFrac: 0.8,
			wantErr:     true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			err := geo.ValidateOrderAhead(tc.vehicleFrac, tc.pickupFrac, tc.dropoffFrac)
			if tc.wantErr && err == nil {
				t.Fatalf("expected error for (%v, %v, %v), got nil", tc.vehicleFrac, tc.pickupFrac, tc.dropoffFrac)
			}
			if !tc.wantErr && err != nil {
				t.Fatalf("unexpected error for (%v, %v, %v): %v", tc.vehicleFrac, tc.pickupFrac, tc.dropoffFrac, err)
			}
		})
	}
}

func TestProjectPointOnRoute_Unit(t *testing.T) {
	ctx := context.Background()
	routeEWKT := "SRID=4326;LINESTRING(-77.042793 -12.046374, -77.0297 -12.1219)"
	pt := geo.Point{Lat: -12.0931, Lng: -77.0465}

	t.Run("Executes PostGIS ST_LineLocatePoint and ST_Distance query with EWKT lng-first", func(t *testing.T) {
		q := &stubQueryRower{
			row: stubRow{
				scanFn: func(dest ...any) error {
					*(dest[0].(*float64)) = 0.55
					*(dest[1].(*float64)) = 1.23
					return nil
				},
			},
		}

		proj, err := geo.ProjectPointOnRoute(ctx, q, routeEWKT, pt)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if proj.Fraction != 0.55 || proj.DetourKm != 1.23 {
			t.Fatalf("unexpected projection: %+v", proj)
		}
		if !strings.Contains(q.lastSQL, "ST_LineLocatePoint") || !strings.Contains(q.lastSQL, "ST_Distance") {
			t.Fatalf("expected PostGIS functions in SQL, got: %s", q.lastSQL)
		}
		if len(q.lastArgs) != 2 || q.lastArgs[0] != routeEWKT || q.lastArgs[1] != "SRID=4326;POINT(-77.0465 -12.0931)" {
			t.Fatalf("unexpected query args: %#v", q.lastArgs)
		}
	})

	t.Run("Rejects invalid inputs and propagates DB error", func(t *testing.T) {
		if _, err := geo.ProjectPointOnRoute(ctx, nil, routeEWKT, pt); err == nil {
			t.Fatal("expected error for nil QueryRower")
		}
		q := &stubQueryRower{
			row: stubRow{scanFn: func(dest ...any) error { return errors.New("db error") }},
		}
		if _, err := geo.ProjectPointOnRoute(ctx, q, "", pt); err == nil {
			t.Fatal("expected error for empty route EWKT")
		}
		if _, err := geo.ProjectPointOnRoute(ctx, q, routeEWKT, geo.Point{Lat: 95, Lng: -77}); err == nil {
			t.Fatal("expected error for out-of-range latitude")
		}
		if _, err := geo.ProjectPointOnRoute(ctx, q, routeEWKT, pt); err == nil {
			t.Fatal("expected error when DB scan fails")
		}
	})
}

func TestSegmentDistanceKm_Unit(t *testing.T) {
	ctx := context.Background()
	routeEWKT := "SRID=4326;LINESTRING(-77.042793 -12.046374, -77.0297 -12.1219)"

	t.Run("Executes PostGIS ST_Length and ST_LineSubstring query", func(t *testing.T) {
		q := &stubQueryRower{
			row: stubRow{
				scanFn: func(dest ...any) error {
					*(dest[0].(*float64)) = 4.25
					return nil
				},
			},
		}

		dist, err := geo.SegmentDistanceKm(ctx, q, routeEWKT, 0.2, 0.7)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if dist != 4.25 {
			t.Fatalf("got %v, want 4.25", dist)
		}
		if !strings.Contains(q.lastSQL, "ST_Length") || !strings.Contains(q.lastSQL, "ST_LineSubstring") {
			t.Fatalf("expected PostGIS functions in SQL, got: %s", q.lastSQL)
		}
		if len(q.lastArgs) != 3 || q.lastArgs[0] != routeEWKT || q.lastArgs[1] != 0.2 || q.lastArgs[2] != 0.7 {
			t.Fatalf("unexpected query args: %#v", q.lastArgs)
		}
	})

	t.Run("Validates fraction bounds", func(t *testing.T) {
		q := &stubQueryRower{row: stubRow{scanFn: func(dest ...any) error { return nil }}}
		cases := [][2]float64{
			{-0.1, 0.5},
			{0.2, 1.2},
			{0.8, 0.3},
			{math.NaN(), 0.5},
		}
		for _, c := range cases {
			if _, err := geo.SegmentDistanceKm(ctx, q, routeEWKT, c[0], c[1]); err == nil {
				t.Fatalf("expected error for fractions (%v, %v)", c[0], c[1])
			}
		}
		if _, err := geo.SegmentDistanceKm(ctx, nil, routeEWKT, 0.1, 0.5); err == nil {
			t.Fatal("expected error for nil QueryRower")
		}
		if _, err := geo.SegmentDistanceKm(ctx, q, " ", 0.1, 0.5); err == nil {
			t.Fatal("expected error for empty route EWKT")
		}
	})
}

func TestJSONTagsSnakeCase(t *testing.T) {
	rg := geo.RouteGeometry{
		LineStringWKT: "SRID=4326;LINESTRING(-77.04 -12.04, -77.03 -12.12)",
		GeoJSON:       json.RawMessage(`{"type":"LineString","coordinates":[[-77.04,-12.04],[-77.03,-12.12]]}`),
		DistanceKm:    8.5,
		DurationMin:   20,
		Waypoints:     []geo.Point{{Lat: -12.04, Lng: -77.04}, {Lat: -12.12, Lng: -77.03}},
	}
	raw, err := json.Marshal(rg)
	if err != nil {
		t.Fatalf("marshal RouteGeometry: %v", err)
	}
	var m map[string]any
	if err := json.Unmarshal(raw, &m); err != nil {
		t.Fatalf("unmarshal map: %v", err)
	}
	for _, key := range []string{"line_string_wkt", "geojson", "distance_km", "duration_min", "waypoints"} {
		if _, ok := m[key]; !ok {
			t.Fatalf("expected snake_case key %q in RouteGeometry JSON: %s", key, string(raw))
		}
	}

	proj := geo.Projection{Fraction: 0.4, DetourKm: 0.8}
	rawProj, err := json.Marshal(proj)
	if err != nil {
		t.Fatalf("marshal Projection: %v", err)
	}
	var mp map[string]any
	if err := json.Unmarshal(rawProj, &mp); err != nil {
		t.Fatalf("unmarshal proj map: %v", err)
	}
	for _, key := range []string{"fraction", "detour_km"} {
		if _, ok := mp[key]; !ok {
			t.Fatalf("expected snake_case key %q in Projection JSON: %s", key, string(rawProj))
		}
	}
}

func TestOSRMProvider_ComputeRoute(t *testing.T) {
	origin := geo.Point{Lat: -12.046374, Lng: -77.042793}
	wp := geo.Point{Lat: -12.093100, Lng: -77.046500}
	dest := geo.Point{Lat: -12.121900, Lng: -77.029700}

	t.Run("Calls OSRM driving endpoint with lng,lat order and decodes GeoJSON LineString", func(t *testing.T) {
		var capturedPath string
		var capturedQuery string

		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			capturedPath = r.URL.Path
			capturedQuery = r.URL.RawQuery
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{
				"code": "Ok",
				"routes": [{
					"geometry": {
						"type": "LineString",
						"coordinates": [
							[-77.042793, -12.046374],
							[-77.0465, -12.0931],
							[-77.0297, -12.1219]
						]
					},
					"distance": 11450.5,
					"duration": 1520.0
				}]
			}`))
		}))
		defer srv.Close()

		provider, err := geo.NewRoutingProvider("osrm_public", srv.URL, "", 3*time.Second)
		if err != nil {
			t.Fatalf("NewRoutingProvider: %v", err)
		}

		res, err := provider.ComputeRoute(context.Background(), origin, dest, []geo.Point{wp})
		if err != nil {
			t.Fatalf("ComputeRoute: %v", err)
		}

		wantPath := "/route/v1/driving/-77.042793,-12.046374;-77.0465,-12.0931;-77.0297,-12.1219"
		if capturedPath != wantPath {
			t.Fatalf("request path = %q, want %q", capturedPath, wantPath)
		}
		if !strings.Contains(capturedQuery, "overview=full") ||
			!strings.Contains(capturedQuery, "geometries=geojson") ||
			!strings.Contains(capturedQuery, "steps=false") {
			t.Fatalf("unexpected query params: %q", capturedQuery)
		}

		wantWKT := "SRID=4326;LINESTRING(-77.042793 -12.046374, -77.0465 -12.0931, -77.0297 -12.1219)"
		if res.LineStringWKT != wantWKT {
			t.Fatalf("LineStringWKT = %q, want %q", res.LineStringWKT, wantWKT)
		}
		if math.Abs(res.DistanceKm-11.4505) > 1e-6 {
			t.Fatalf("DistanceKm = %v, want 11.4505", res.DistanceKm)
		}
		if res.DurationMin != 25 {
			t.Fatalf("DurationMin = %d, want 25", res.DurationMin)
		}
		if len(res.Waypoints) != 3 || res.Waypoints[1] != wp {
			t.Fatalf("unexpected waypoints: %+v", res.Waypoints)
		}

		var gj map[string]any
		if err := json.Unmarshal(res.GeoJSON, &gj); err != nil {
			t.Fatalf("GeoJSON is not valid JSON: %v", err)
		}
		if gj["type"] != "LineString" {
			t.Fatalf("expected GeoJSON type LineString, got %v", gj["type"])
		}
	})

	t.Run("Returns error on non-200 HTTP response or OSRM error code", func(t *testing.T) {
		srv500 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Error(w, "upstream failure", http.StatusBadGateway)
		}))
		defer srv500.Close()

		p1, err := geo.NewRoutingProvider("osrm_local", "", srv500.URL, 2*time.Second)
		if err != nil {
			t.Fatalf("NewRoutingProvider: %v", err)
		}
		if _, err := p1.ComputeRoute(context.Background(), origin, dest, nil); err == nil {
			t.Fatal("expected error for HTTP 502")
		}

		srvNoRoute := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"code":"NoRoute","message":"Impossible route","routes":[]}`))
		}))
		defer srvNoRoute.Close()

		p2, err := geo.NewRoutingProvider("osrm_local", "", srvNoRoute.URL, 2*time.Second)
		if err != nil {
			t.Fatalf("NewRoutingProvider: %v", err)
		}
		if _, err := p2.ComputeRoute(context.Background(), origin, dest, nil); err == nil {
			t.Fatal("expected error for NoRoute")
		}
	})

	t.Run("Respects context cancellation and timeout", func(t *testing.T) {
		srvSlow := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			time.Sleep(150 * time.Millisecond)
			w.WriteHeader(http.StatusOK)
		}))
		defer srvSlow.Close()

		p, err := geo.NewRoutingProvider("osrm_public", srvSlow.URL, "", 40*time.Millisecond)
		if err != nil {
			t.Fatalf("NewRoutingProvider: %v", err)
		}
		if _, err := p.ComputeRoute(context.Background(), origin, dest, nil); err == nil {
			t.Fatal("expected timeout error")
		}
	})
}

func TestMockRoutingProvider_AndFactory(t *testing.T) {
	origin := geo.Point{Lat: -12.046374, Lng: -77.042793}
	wp := geo.Point{Lat: -12.093100, Lng: -77.046500}
	dest := geo.Point{Lat: -12.121900, Lng: -77.029700}

	t.Run("Computes geodesic path with waypoints, 1.25x distance, and 25 km/h duration", func(t *testing.T) {
		provider, err := geo.NewRoutingProvider("mock", "", "", 5*time.Second)
		if err != nil {
			t.Fatalf("NewRoutingProvider(mock): %v", err)
		}

		res, err := provider.ComputeRoute(context.Background(), origin, dest, []geo.Point{wp})
		if err != nil {
			t.Fatalf("ComputeRoute: %v", err)
		}

		expectedDist := (geo.HaversineKm(origin, wp) + geo.HaversineKm(wp, dest)) * 1.25
		if math.Abs(res.DistanceKm-expectedDist) > 1e-9 {
			t.Fatalf("DistanceKm = %v, want %v", res.DistanceKm, expectedDist)
		}

		expectedDuration := int(math.Round((expectedDist / 25.0) * 60.0))
		if res.DurationMin != expectedDuration {
			t.Fatalf("DurationMin = %d, want %d", res.DurationMin, expectedDuration)
		}

		wantWKT := "SRID=4326;LINESTRING(-77.042793 -12.046374, -77.0465 -12.0931, -77.0297 -12.1219)"
		if res.LineStringWKT != wantWKT {
			t.Fatalf("LineStringWKT = %q, want %q", res.LineStringWKT, wantWKT)
		}
		if len(res.Waypoints) != 3 {
			t.Fatalf("expected 3 waypoints, got %d", len(res.Waypoints))
		}

		var gj struct {
			Type        string      `json:"type"`
			Coordinates [][]float64 `json:"coordinates"`
		}
		if err := json.Unmarshal(res.GeoJSON, &gj); err != nil {
			t.Fatalf("unmarshal GeoJSON: %v", err)
		}
		if gj.Type != "LineString" || len(gj.Coordinates) != 3 {
			t.Fatalf("unexpected GeoJSON: %+v", gj)
		}
		if gj.Coordinates[0][0] != origin.Lng || gj.Coordinates[0][1] != origin.Lat {
			t.Fatalf("GeoJSON coordinates must be [lng, lat], got %v", gj.Coordinates[0])
		}
	})

	t.Run("Enforces minimum 0.1 km and 1 min for distinct nearby points", func(t *testing.T) {
		p := geo.NewMockRoutingProvider()
		a := geo.Point{Lat: -12.046374, Lng: -77.042793}
		b := geo.Point{Lat: -12.046380, Lng: -77.042800}

		res, err := p.ComputeRoute(context.Background(), a, b, nil)
		if err != nil {
			t.Fatalf("ComputeRoute: %v", err)
		}
		if res.DistanceKm != 0.1 {
			t.Fatalf("expected minimum 0.1 km for nearby distinct points, got %v", res.DistanceKm)
		}
		if res.DurationMin != 1 {
			t.Fatalf("expected minimum 1 min, got %d", res.DurationMin)
		}
	})

	t.Run("Factory rejects invalid providerType or missing URL", func(t *testing.T) {
		if _, err := geo.NewRoutingProvider("google", "", "", time.Second); err == nil {
			t.Fatal("expected error for unknown provider type")
		}
		if _, err := geo.NewRoutingProvider("osrm_public", "", "http://localhost:5000", time.Second); err == nil {
			t.Fatal("expected error when osrm_public URL is empty")
		}
		if _, err := geo.NewRoutingProvider("osrm_local", "https://router.project-osrm.org", "", time.Second); err == nil {
			t.Fatal("expected error when osrm_local URL is empty")
		}
	})
}
