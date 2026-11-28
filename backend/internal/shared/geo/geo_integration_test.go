//go:build integration

package geo_test

import (
	"context"
	"math"
	"os"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/glimp-ly/oneroute/backend/internal/shared/geo"
)

const defaultTestDatabaseURL = "postgres://rutacompartida:rutacompartida_dev_pass@127.0.0.1:5432/rutacompartida?sslmode=disable"

func openTestConn(t *testing.T) *pgx.Conn {
	t.Helper()

	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		dsn = defaultTestDatabaseURL
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	conn, err := pgx.Connect(ctx, dsn)
	if err != nil {
		t.Fatalf("pgx.Connect: %v", err)
	}
	t.Cleanup(func() {
		closeCtx, closeCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer closeCancel()
		_ = conn.Close(closeCtx)
	})

	if err := conn.Ping(ctx); err != nil {
		t.Fatalf("conn.Ping: %v", err)
	}
	return conn
}

func TestPostGIS_ProjectPointOnRoute_And_SegmentDistanceKm(t *testing.T) {
	conn := openTestConn(t)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Lima corridor: Plaza Mayor -> Av. Arequipa / Javier Prado (San Isidro) -> Miraflores -> Barranco
	routePts := []geo.Point{
		{Lat: -12.046374, Lng: -77.042793}, // Plaza Mayor (frac ~0.0)
		{Lat: -12.093100, Lng: -77.036500}, // San Isidro
		{Lat: -12.121900, Lng: -77.029700}, // Miraflores
		{Lat: -12.149000, Lng: -77.021800}, // Barranco (frac ~1.0)
	}
	routeEWKT, err := geo.PointsToLineStringEWKT(routePts)
	if err != nil {
		t.Fatalf("PointsToLineStringEWKT: %v", err)
	}

	t.Run("Project points along Lima route and verify order ahead + segment distance", func(t *testing.T) {
		// Point slightly offset from San Isidro (~450 m east)
		pickupNearSanIsidro := geo.Point{Lat: -12.093100, Lng: -77.032000}
		// Point near Miraflores (~300 m west)
		dropoffNearMiraflores := geo.Point{Lat: -12.121900, Lng: -77.032500}

		pickupProj, err := geo.ProjectPointOnRoute(ctx, conn, routeEWKT, pickupNearSanIsidro)
		if err != nil {
			t.Fatalf("ProjectPointOnRoute(pickup): %v", err)
		}
		dropoffProj, err := geo.ProjectPointOnRoute(ctx, conn, routeEWKT, dropoffNearMiraflores)
		if err != nil {
			t.Fatalf("ProjectPointOnRoute(dropoff): %v", err)
		}

		if pickupProj.Fraction <= 0.1 || pickupProj.Fraction >= 0.8 {
			t.Fatalf("unexpected pickup fraction near San Isidro: %v", pickupProj.Fraction)
		}
		if dropoffProj.Fraction <= pickupProj.Fraction || dropoffProj.Fraction >= 1.0 {
			t.Fatalf("expected dropoff fraction > pickup fraction: pickup=%v dropoff=%v", pickupProj.Fraction, dropoffProj.Fraction)
		}
		if pickupProj.DetourKm <= 0.1 || pickupProj.DetourKm >= 1.5 {
			t.Fatalf("expected pickup detour between 0.1 and 1.5 km, got %v", pickupProj.DetourKm)
		}
		if dropoffProj.DetourKm <= 0.05 || dropoffProj.DetourKm >= 1.5 {
			t.Fatalf("expected dropoff detour between 0.05 and 1.5 km, got %v", dropoffProj.DetourKm)
		}

		if err := geo.ValidateOrderAhead(0.0, pickupProj.Fraction, dropoffProj.Fraction); err != nil {
			t.Fatalf("ValidateOrderAhead failed: %v", err)
		}

		totalKm, err := geo.SegmentDistanceKm(ctx, conn, routeEWKT, 0.0, 1.0)
		if err != nil {
			t.Fatalf("SegmentDistanceKm(0, 1): %v", err)
		}
		segKm, err := geo.SegmentDistanceKm(ctx, conn, routeEWKT, pickupProj.Fraction, dropoffProj.Fraction)
		if err != nil {
			t.Fatalf("SegmentDistanceKm(pickup, dropoff): %v", err)
		}

		if totalKm < 10.0 || totalKm > 15.0 {
			t.Fatalf("expected total Lima corridor length between 10 and 15 km, got %v", totalKm)
		}
		if segKm <= 1.0 || segKm >= totalKm {
			t.Fatalf("expected segment length between 1 km and total (%v), got %v", totalKm, segKm)
		}

		zeroKm, err := geo.SegmentDistanceKm(ctx, conn, routeEWKT, 0.5, 0.5)
		if err != nil {
			t.Fatalf("SegmentDistanceKm(0.5, 0.5): %v", err)
		}
		if math.Abs(zeroKm) > 1e-9 {
			t.Fatalf("expected zero length for equal fractions, got %v", zeroKm)
		}
	})

	t.Run("Works inside a rolled-back pgx transaction", func(t *testing.T) {
		tx, err := conn.Begin(ctx)
		if err != nil {
			t.Fatalf("pool.Begin: %v", err)
		}
		defer func() {
			_ = tx.Rollback(ctx)
		}()

		proj, err := geo.ProjectPointOnRoute(ctx, tx, routeEWKT, routePts[0])
		if err != nil {
			t.Fatalf("ProjectPointOnRoute in tx: %v", err)
		}
		if math.Abs(proj.Fraction) > 1e-6 || proj.DetourKm > 1e-4 {
			t.Fatalf("expected origin projection near (0, 0), got %+v", proj)
		}
	})
}
