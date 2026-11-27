# Integraciones Externas: OSRM, Mocks de Desarrollo y Paso a Producción

Para evitar bloqueos por costos, API keys o trámites bancarios/RENIEC mientras los 6 integrantes desarrollan el MVP en paralelo, todas las integraciones externas están abstraídas detrás de **interfaces en `internal/shared/`** (`geo.RoutingProvider`, `providers.PaymentGateway`, `providers.IdentityProvider`, `providers.OTPProvider`, `realtime.PushProvider`).

---

## 1. Ruteo y Polilíneas: OSRM + PostGIS (Elegido para MVP)

### ¿Por qué OSRM?
- **Gratis y Open Source** (sin tarjeta de crédito ni cuotas por petición como Google Routes API).
- **Cero fricción inicial:** el servidor público `https://router.project-osrm.org` funciona de inmediato con `ROUTING_PROVIDER=osrm_public`.
- **Escalable sin cambiar código:** si el servidor público tiene latencia o límites, se activa el contenedor local incluido en `docker-compose.yml` (`--profile osrm-local`) y se cambia `ROUTING_PROVIDER=osrm_local`.

### Flujo técnico OSRM + PostGIS
1. Al previsualizar o crear una ruta, Go llama a:
   `GET {OSRM_URL}/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson&steps=false`
2. OSRM devuelve `routes[0].geometry` (GeoJSON `LineString`), `routes[0].distance` (metros) y `routes[0].duration` (segundos).
3. PostGIS convierte esa polilínea y proyecta puntos sobre ella:
   ```sql
   -- Guardar polilínea en ruta.geometria
   ST_GeogFromGeoJSON($1)

   -- Calcular fracción (0..1) y desvío (km) de cualquier punto de recogida/destino
   SELECT
       ST_LineLocatePoint(r.geometria::geometry, ST_SetSRID(ST_MakePoint($lng, $lat), 4326)) AS fraccion,
       ST_Distance(r.geometria, ST_SetSRID(ST_MakePoint($lng, $lat), 4326)::geography) / 1000.0 AS desvio_km
   FROM ruta r WHERE r.id = $ruta_id;
   ```
4. En `ROUTING_PROVIDER=mock` (para tests unitarios offline sin internet), el proveedor genera una línea recta geodésica entre origen y destino y calcula distancia Haversine × 1.25.

---

## 2. Pasarela de Pagos Mock (`PaymentGateway`)

Definida en `internal/shared/providers/payment_gateway.go` y consumida por **`modules/payments_wallets`**:

```go
type ChargeRequest struct {
    PagoID        uuid.UUID
    UsuarioID     uuid.UUID
    Amount        float64
    Currency      string // "PEN"
    IdempotencyID uuid.UUID
    SimulatedOutcome string // leido del header X-Mock-Payment-Result en dev
}

type PaymentGateway interface {
    ChargeDigital(ctx context.Context, req ChargeRequest) (*ChargeResult, error)
    RefundDigital(ctx context.Context, externalRef string, amount float64) (*RefundResult, error)
    DisburseWithdrawal(ctx context.Context, req WithdrawalRequest) (*WithdrawalResult, error)
}
```

### Reglas determinísticas de simulación en `dev` (`PAYMENT_PROVIDER=mock`)
Permite probar tanto cobros exitosos como el flujo crítico del Word (**fallo al abordar → reintento digital → cambio a efectivo con autorización del conductor**):
1. Si el request trae header `X-Mock-Payment-Result: failed` (o `PAYMENT_MOCK_DEFAULT_RESULT=failed`), `ChargeDigital` devuelve `Status = "failed"`, `FailureReason = "MOCK_CARD_DECLINED"`.
2. Si el monto termina en `.99` (ej. `24.99`), simula fallo automático (`MOCK_INSUFFICIENT_FUNDS`) incluso sin header (muy útil para probar directo desde Android).
3. En cualquier otro caso, devuelve `Status = "succeeded"` con `ExternalRef = "MOCK-PAY-<uuid>"`.
4. **Paso a producción:** implementar `CulqiGateway` o `MercadoPagoGateway` satisfaciendo la misma interfaz `PaymentGateway` y activarlo por `PAYMENT_PROVIDER=culqi`.

---

## 3. Verificación DNI y Envío de OTP Mock

> **Nota clara de responsabilidad:**
> Las interfaces `IdentityProvider` y `OTPProvider` y sus implementaciones mock viven en `internal/shared/providers/` (administrado por el líder) para que todo el equipo pueda arrancar sin bloqueos. **Reemplazar estos mocks por integraciones reales (KYC/RENIEC y SMTP/SMS real) corresponde al desarrollo del módulo `auth_identity`** (o coordinado en `shared/providers` con el dueño de `auth_identity`).

### 3.1 `OTPProvider` (`OTP_PROVIDER=mailpit_console`)
- **En `dev`:**
  1. Genera código numérico de 6 dígitos y guarda su hash `bcrypt` en `codigo_verificacion` (`expira_at = NOW() + 10 min`).
  2. Imprime el código claramente en logs estructurados (`slog.Info("DEV_OTP_GENERATED", "destino", ..., "code", code)`).
  3. Envía además un correo vía SMTP a **Mailpit** (`localhost:1025`) para verlo en `http://localhost:8025`.
- **En producción:** cambiar a `OTP_PROVIDER=smtp` (Resend/SES/SendGrid) para correo y/o Twilio/AWS SNS para SMS.

### 3.2 `IdentityProvider` (`IDENTITY_PROVIDER=mock`)
- **En `dev`:**
  - Si `IDENTITY_MOCK_AUTO_APPROVE=true`: al enviar `POST /identity/verification`, el mock valida formato básico (8 dígitos para DNI), aprueba inmediatamente la verificación y marca `usuario.identidad_verificada = TRUE`.
  - Si `IDENTITY_MOCK_AUTO_APPROVE=false` (por defecto): la solicitud queda en `'pendiente'` / `'en_revision'` y se aprueba o rechaza desde `POST /admin/identity/{id}/review` (usando el usuario `admin@rutacompartida.pe` de `seed_dev.sql`).
  - Convención de prueba negativa: DNI `'00000000'` siempre devuelve rechazo mock (`"Documento inválido de prueba"`).
- **En producción (`auth_identity`):** reemplazar el adaptador mock por consulta API RENIEC / proveedor KYC biométrico (Truora, Veriff, Metamap o servicio autorizado), manteniendo la misma interfaz `IdentityProvider` sin tocar el resto del módulo.

---

## 4. Notificaciones Push (`PushProvider`)

- **En `dev` (`PUSH_PROVIDER=mock`):** registra en logs la entrega push y publica igualmente el evento por **WebSocket** (`realtime.Hub`), por lo que la app Android en tiempo real recibe las alertas estando conectada.
- **En producción (`PUSH_PROVIDER=fcm`):** usar Firebase Admin SDK (`FCM_CREDENTIALS_JSON`) leyendo los `token_push` activos en la tabla `dispositivo`.
