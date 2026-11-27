# Contrato de API REST (`/api/v1`) y WebSocket (`/api/v1/ws`)

Todos los endpoints siguen el prefijo `/api/v1`, formato JSON `camelCase` o `snake_case` consistente (**acordado: `snake_case`** igual que las columnas SQL para reducir fricción entre Go y DTOs Kotlin `@SerializedName`), y responden con el envelope estándar:

```json
{ "success": true, "data": { ... }, "meta": null, "error": null }
```

## 1. Headers estándar y códigos de error

- `Authorization: Bearer <access_token>`
- `Idempotency-Key: <uuid>` (o `client_event_id` en el body para operaciones offline/reintentos: GPS, chat, pagos, recargas, SOS, incidentes).
- `X-Mock-Payment-Result: success | failed` (solo en `APP_ENV=development` para simular fallo de pasarela).

| Código HTTP | `error.code` | Cuándo se devuelve |
|---|---|---|
| `401` | `UNAUTHORIZED` / `TOKEN_EXPIRED` | Token faltante, inválido o sesión revocada. |
| `403` | `IDENTITY_NOT_VERIFIED` | Usuario intenta solicitar/confirmar reserva sin DNI aprobado. |
| `403` | `DRIVER_NOT_APPROVED` | Usuario intenta publicar viaje sin perfil/vehículo aprobado. |
| `403` | `USER_RESTRICTED` | Existe `restriccion_preventiva` o `sancion` activa aplicable. |
| `409` | `ACTIVE_TRIP_LIMIT` | Conductor ya tiene un viaje `publicado`/`en_curso`. |
| `409` | `COMMISSION_BALANCE_LOW` | Saldo en billetera `comisiones` insuficiente para publicar. |
| `409` | `INSUFFICIENT_CAPACITY` | Sin asientos disponibles en alguno de los tramos del intervalo. |
| `409` | `HOLD_EXPIRED` | Pasaron los 60 segundos para responder o confirmar. |
| `409` | `MAX_ATTEMPTS_REACHED` | Pasajero alcanzó 3 intentos en ese viaje. |
| `409` | `MODIFICATION_LIMIT_REACHED` | Ya se usó el único cambio de recogida o destino permitido. |
| `422` | `CALENDAR_DEADLINE_EXPIRED` | Plazo de 7 días calendario vencido (descargo o apelación). |

---

## 2. Endpoints REST por módulo

### Módulo 1: `auth_identity`
| Método | Ruta | Auth / Guard | Propósito |
|---|---|---|---|
| `POST` | `/auth/register` | Público | Registro + envío OTP correo + creación billetera `pasajero`. |
| `POST` | `/auth/login` | Público | Login → `access_token`, `refresh_token`, `user`, `roles`. |
| `POST` | `/auth/refresh` | Público | Rotación de refresh token (`usuario_sesion`). |
| `POST` | `/auth/logout` | `Auth` | Revoca sesión actual. |
| `POST` | `/auth/otp/send` | `Auth` / Público | Envía OTP (`verificar_correo`, `verificar_telefono`, `recuperar_password`). |
| `POST` | `/auth/otp/verify` | `Auth` / Público | Valida OTP y marca verificado / cambia password. |
| `GET` / `PATCH` | `/me` | `Auth` | Ver / editar perfil y foto (`StoragePublic`). |
| `POST` | `/me/device` | `Auth` | Registra `dispositivo` y `token_push`. |
| `POST` | `/identity/verification` | `Auth` | Enviar solicitud de DNI + subir documentos (`StoragePrivate`). |
| `GET` | `/identity/verification` | `Auth` | Estado actual de verificación de identidad. |
| `POST` | `/admin/identity/{id}/review` | `Roles(admin,moderador)` | Aprobar/rechazar DNI en dev o backoffice. |
| `POST` | `/driver/apply` | `Auth + VerifiedDNI` | Postular como conductor + vehículo + licencia/SOAT/tarjeta propiedad. |
| `GET` | `/driver/status` | `Auth` | Estado del perfil conductor, documentos y vehículos. |
| `POST` | `/admin/driver/{solicitud_id}/review` | `Roles(admin,moderador)` | Aprobar/rechazar solicitud conductor y vehículo. |
| `GET` / `POST` / `PATCH` / `DELETE` | `/me/emergency-contacts` | `Auth` | CRUD de `contacto_emergencia` + marcar principal. |

### Módulo 2: `routes_trips`
| Método | Ruta | Auth / Guard | Propósito |
|---|---|---|---|
| `POST` | `/routes/preview` | `Auth` | Calcula polilínea y distancia vía OSRM sin guardar. |
| `POST` | `/routes` | `ApprovedDriver` | Crea `ruta` + `ruta_punto` con fracciones PostGIS. |
| `GET` | `/routes/mine` | `ApprovedDriver` | Rutas frecuentes del conductor. |
| `POST` | `/trips` | `ApprovedDriver` | Publica `viaje` (precio definido por conductor, valida comisiones y 1 viaje activo). |
| `GET` | `/trips/mine/driver` | `ApprovedDriver` | Viajes del conductor (activos/historial). |
| `PATCH` | `/trips/{id}/accepting` | `ApprovedDriver` | Activa/pausa `acepta_reservas` y `aceptacion_automatica`. |
| `POST` | `/trips/{id}/cancel` | `ApprovedDriver` | Cancela viaje completo y todas sus reservas con reembolso 100%. |
| `GET` | `/trips/{id}/requests` | `ApprovedDriver` | Lista solicitudes pendientes del viaje (con `expira_at`). |
| `POST` | `/requests/{id}/accept` | `ApprovedDriver` | Acepta solicitud manual → crea retención 60 s (`esperando_pasajero`). |
| `POST` | `/requests/{id}/reject` | `ApprovedDriver` | Rechaza solicitud con motivo (consume intento). |

### Módulo 3: `search_bookings`
| Método | Ruta | Auth / Guard | Propósito |
|---|---|---|---|
| `GET` | `/trips/search` | Público / `Auth` | Búsqueda espacial PostGIS (`origin_lat, origin_lng, dest_lat, dest_lng, seats, date`). |
| `GET` | `/trips/{id}` | Público / `Auth` | Detalle de viaje + paradas + ocupación por tramos. |
| `POST` | `/trips/{id}/quote` | `Auth` | Cotiza tramo `(recogida, destino, asientos)` calculando desvío y precio. |
| `POST` | `/trips/{id}/requests` | `VerifiedDNI` | Crea solicitud (`pendiente` o directa `esperando_pasajero` si auto-aceptación). |
| `DELETE` | `/requests/{id}` | `VerifiedDNI` | Cancela solicitud `pendiente` (no consume intento) o retención. |
| `POST` | `/reservations/{id}/confirm` | `VerifiedDNI` | Confirma retención dentro de 60 s → pasa a `confirmada`, cancela otras y abre chat. |
| `GET` | `/reservations/mine` | `Auth` | Reservas activas e historial del pasajero. |
| `POST` | `/reservations/{id}/modify-pickup` | `VerifiedDNI` | Único cambio permitido de recogida + recálculo de tarifa. |
| `POST` | `/reservations/{id}/modify-destination` | `VerifiedDNI` | Único cambio permitido de destino + recálculo/crédito. |
| `POST` | `/reservations/{id}/modify-seats` | `VerifiedDNI` | Aumentar o solicitar reducción de asientos (retención 24 h si ya pagó). |

### Módulo 4: `active_trip_gps`
| Método | Ruta | Auth / Guard | Propósito |
|---|---|---|---|
| `POST` | `/trips/{id}/start` | `ApprovedDriver` | Cambia viaje a `en_curso`. |
| `POST` | `/trips/{id}/gps` | `ApprovedDriver` | Envía 1 o lote de puntos GPS (`client_event_id` idempotente). |
| `POST` | `/trips/{id}/complete` | `ApprovedDriver` | Finaliza el viaje completo. |
| `POST` | `/reservations/{id}/arrived` | `ApprovedDriver` | Marca llegada del conductor al punto de recogida (inicia 5 min espera). |
| `POST` | `/reservations/{id}/extend-wait` | `ApprovedDriver` | Amplía espera por acuerdo con pasajero. |
| `POST` | `/reservations/{id}/no-show` | `ApprovedDriver` | Declara no-show tras vencer espera (libera cupo sin cobro digital). |
| `POST` | `/reservations/{id}/board` | `ApprovedDriver` | Confirma abordaje del pasajero (`en_curso`) y dispara cobro (`WalletPort`). |
| `POST` | `/reservations/{id}/early-finish` | `Auth` | Pasajero confirma *"Finalizar recorrido"* anticipado con GPS y recálculo. |
| `POST` | `/reservations/{id}/cancel` | `Auth` | Cancelación individual (calcula distancia conductor → % reembolso). |
| `POST` | `/trips/{id}/tracking-links` | `Auth` | Crea enlace temporal de seguimiento. |
| `DELETE` | `/tracking-links/{id}` | `Auth` | Revoca enlace activo. |
| `GET` | `/tracking/{token}` | Público | Consulta pública de ubicación básica (sin requerir cuenta). |
| `POST` | `/reservations/{id}/rate` | `Auth` | Califica 1–5 estrellas + etiquetas + anónimo + propina opcional. |

### Módulo 5: `payments_wallets`
| Método | Ruta | Auth / Guard | Propósito |
|---|---|---|---|
| `GET` | `/wallets` | `Auth` | Devuelve las 3 billeteras (`pasajero`, `ganancias`, `comisiones`) + créditos retenidos 24 h. |
| `GET` | `/wallets/{tipo}/movements` | `Auth` | Movimientos paginados de la billetera indicada. |
| `POST` | `/wallets/recharges` | `Auth` | Recargar billetera `comisiones` o `pasajero` (`client_event_id`). |
| `POST` | `/wallets/withdrawals` | `ApprovedDriver` | Solicitar retiro desde `ganancias` (CCI, banco, Yape, Plin). |
| `POST` | `/payments/{id}/retry` | `Auth` | Reintentar pago digital tras fallo al abordar. |
| `POST` | `/payments/{id}/switch-cash` | `Auth` | Cambiar a efectivo (requiere confirmación del conductor). |
| `POST` | `/payments/{id}/confirm-cash` | `ApprovedDriver` | Conductor confirma recepción de efectivo y descuenta comisión. |
| `POST` | `/promotions/validate` | `Auth` | Valida cupón promocional sobre una reserva/monto. |

### Módulo 6: `messaging_safety_support`
| Método | Ruta | Auth / Guard | Propósito |
|---|---|---|---|
| `GET` | `/chats` | `Auth` | Lista chats de reservas activas e históricas. |
| `GET` | `/chats/{reserva_id}/messages` | `Auth` | Historial paginado de mensajes. |
| `POST` | `/chats/{reserva_id}/messages` | `Auth` | Enviar mensaje (`texto`, `imagen`, `ubicacion`, `archivo`) con `client_event_id`. |
| `POST` | `/chats/messages/{id}/report` | `Auth` | Reportar mensaje inapropiado. |
| `GET` | `/notifications` | `Auth` | Historial in-app de notificaciones. |
| `PATCH` | `/notifications/read` | `Auth` | Marcar una o todas como leídas. |
| `GET` / `PUT` | `/notifications/preferences` | `Auth` | Configurar notificaciones (bloquea desactivar críticas). |
| `POST` | `/sos` | `Auth` | Activa alerta SOS en viaje activo (`client_event_id`) y notifica plataforma + contactos. |
| `POST` | `/incidents` | `Auth` | Registra incidente ordinario con GPS y adjuntos (`client_event_id`). |
| `POST` | `/reports` | `Auth` | Crea reporte formal inmutable contra usuario (múltiples categorías + evidencias). |
| `GET` | `/reports/received` | `Auth` | Reportes recibidos y plazo restante en días calendario. |
| `POST` | `/reports/{id}/defense` | `Auth` | Presenta `descargo` dentro de los 7 días calendario. |
| `POST` | `/sanctions/{id}/appeal` | `Auth` | Presenta `apelacion_sancion` dentro de los 7 días calendario. |
| `GET` / `POST` | `/support/tickets` | `Auth` | Listar o crear ticket de soporte. |
| `GET` / `POST` | `/support/tickets/{id}/messages` | `Auth` | Mensajes y adjuntos del ticket. |

---

## 3. Contrato WebSocket (`/api/v1/ws?token=<jwt>`)

### Mensajes Cliente → Servidor
- `{"type": "subscribe", "room": "trip:<viaje_id>"}` (el servidor valida que sea conductor o pasajero confirmado del viaje).
- `{"type": "subscribe", "room": "reservation:<reserva_id>"}` (chat privado).
- `{"type": "gps.ping", "viaje_id": "...", "client_event_id": "...", "lat": -12.11, "lng": -77.03, "speed_kmh": 34.5, "heading": 180, "battery": 82, "recorded_at": "..."}`
- `{"type": "chat.send", "reserva_id": "...", "client_event_id": "...", "msg_type": "texto", "content": "..."}`

### Eventos Servidor → Cliente
- `request.incoming` → sala `user:{conductor_id}` (payload: solicitud + `expira_at` 60 s).
- `request.accepted_hold` → sala `user:{pasajero_id}` (payload: reserva en espera + `hold_expira_at` 60 s).
- `request.expired` / `request.rejected` → sala `user:{pasajero_id}`.
- `trip.gps_updated` → sala `trip:{viaje_id}` (lat, lng, velocidad, fracción de avance, `gps_confiable`).
- `trip.status_changed` → sala `trip:{viaje_id}`.
- `chat.message_new` → sala `reservation:{reserva_id}`.
- `sos.alert_ack` → sala `user:{usuario_id}` (confirmación del servidor antes de mostrar enviado).
- `notification.new` → sala `user:{usuario_id}`.
