# División de Desarrollo en 6 Módulos (Equipo en Paralelo)

Tú (Líder Técnico) te encargas de **`internal/shared/`** y eliges **1 de los 6 módulos**; los otros **5 módulos** se asignan uno a cada compañero. Cada persona es dueña exclusiva de su carpeta en `internal/modules/<modulo>/` y de las mutaciones en sus tablas.

---

## Módulo 1: `auth_identity` — Autenticación, Identidad y Habilitación de Conductor
- **Rama base:** `feat/auth-identity`
- **Pantallas Android (`OneRouteAndroid`):** `SplashScreen`, `OnboardingScreen`, `LoginScreen`, `RegisterScreen`, `ForgotPasswordScreen`, `OtpNewPasswordScreen`, `ProfileScreen`, `DocumentVerificationScreen`, `VehicleRegistrationScreen`, `EmergencyContactsScreen`.
- **Tablas propias (escritura):** `rol`, `usuario`, `usuario_rol`, `dispositivo`, `usuario_sesion`, `codigo_verificacion`, `usuario_direccion`, `verificacion_identidad`, `documento_privado`, `contacto_emergencia`, `perfil_conductor`, `vehiculo`, `solicitud_conductor`, `documento_conductor`, `consentimiento_usuario`.
- **Puertos que implementa:** `contracts.IdentityPort`.
- **Puertos que consume:** `contracts.WalletPort` (crear billeteras al registrar/aprobar conductor), `shared/auth`, `shared/storage`, `shared/providers` (`OTPProvider`, `IdentityProvider`).
- **Lógica crítica y reglas:**
  1. Al registrar usuario: crear usuario, asignar rol `'pasajero'`, registrar consentimientos, crear billetera `'pasajero'` (vía `WalletPort`), enviar OTP de verificación de correo.
  2. Login: verificar `bcrypt`, emitir Access JWT (15 min) + Refresh Token guardado como SHA-256 en `usuario_sesion`, registrar/actualizar `dispositivo` y `token_push`.
  3. Verificación de identidad (DNI): independiente del rol conductor; solo actualiza `usuario.identidad_verificada = TRUE` cuando `verificacion_identidad.estado = 'aprobado'`.
  4. Habilitación conductor: crear `perfil_conductor` (`pendiente`), `vehiculo`, subir `licencia`, `soat` (con `vehiculo_id`), `tarjeta_propiedad` (con `vehiculo_id`) al bucket **privado**; al aprobar, asignar rol `'conductor'` y crear billeteras `'ganancias'` y `'comisiones'`.
  5. Contactos de emergencia: máximo 1 contacto `es_principal = TRUE` por usuario (respetar índice único parcial).
- **Mocks vs Real (IMPORTANTE):**
  - En `dev` usa `OTPProvider` (Mailpit + consola) e `IdentityProvider` (mock manual/auto).
  - **Responsabilidad de reemplazo:** quien tome este módulo (o el líder si coordina `shared/providers`) debe reemplazar los mocks por proveedor SMTP/SMS real (Resend/Twilio) y proveedor KYC/RENIEC real antes de salir a producción (ver `05_INTEGRACIONES_MOCK_Y_PRODUCCION.md`).
- **DoD:** registro/login/refresh funcionando con Postman y Android, OTP visible en Mailpit, flujo de aprobación de DNI y conductor probados, URLs firmadas para documentos privados.

---

## Módulo 2: `routes_trips` — Rutas, Polilíneas OSRM y Gestión de Viajes (Conductor)
- **Rama base:** `feat/routes-trips`
- **Pantallas Android (`OneRouteAndroid`):** `CreateTripScreen`, `MapPointPickerScreen`, `PublishPreviewScreen`, `PublishConfirmationScreen`, `MyTripsScreen` (vista conductor + `IncomingRequestsDialog`).
- **Tablas propias (escritura):** `ruta`, `ruta_punto`, `viaje`, `viaje_parada`, `viaje_segmento`.
- **Puertos que implementa:** `contracts.TripPort`.
- **Puertos que consume:** `contracts.WalletPort` (validar `HasSufficientCommissionBalance`), `contracts.BookingPort` (`CancelReservationsByTrip` si el conductor cancela el viaje completo), `contracts.NotificationPort`, `shared/geo` (`RoutingProvider` + PostGIS).
- **Lógica crítica y reglas:**
  1. Crear ruta base invocando `RoutingProvider.ComputeRoute(origen, destino, waypoints)` → guarda `ruta.geometria` (`LINESTRING`) y `ruta_punto` con `fraccion_ruta` calculada por PostGIS (`ST_LineLocatePoint`).
  2. Publicar viaje (`WithTx`):
     - Validar `perfil_conductor.estado = 'aprobado'`, vehículo aprobado y saldo en billetera de comisiones `>= comision.saldo_minimo_publicar_pen`.
     - **El conductor define el precio** (`tipo_tarifa`: `'por_asiento'` o `'por_km'`; `precio_asiento_completo` / `tarifa_km`).
     - La BD ya bloquea >1 viaje activo con `uq_conductor_viaje_activo` y valida vehículo/capacidad con `trg_validar_viaje`.
  3. Toggle `acepta_reservas` y `aceptacion_automatica` sin afectar reservas confirmadas.
  4. **Aceptar/Rechazar solicitudes (60 s):**
     - Verificar que la solicitud esté `pendiente` y no haya expirado (`expira_at > NOW()`).
     - Al aceptar: proyectar recogida/destino en la ruta, validar desvío `<= viaje.desvio_maximo_km` y que recogida esté por delante de `viaje.fraccion_avance_ruta`.
     - Insertar/actualizar `reserva` en estado `'esperando_pasajero'` con `hold_expira_at = NOW() + 60s` (el trigger `trg_validar_capacidad_reserva` valida capacidad por superposición) y notificar por WS + Push.
  5. Cancelar viaje completo: transacción que cancela `viaje` y llama a `BookingPort.CancelReservationsByTrip` + reembolso digital al 100%.
- **DoD:** creación de ruta con polilínea OSRM real, publicación de viaje, pausa de reservas, aceptación/rechazo de solicitudes dentro de 60 s y cancelación total.

---

## Módulo 3: `search_bookings` — Búsqueda Espacial y Reservas (Pasajero)
- **Rama base:** `feat/search-bookings`
- **Pantallas Android (`OneRouteAndroid`):** `HomeScreen`, `SearchTripsScreen`, `TripDetailScreen`, `MyTripsScreen` (vista pasajero + `PassengerHoldConfirmationDialog` + `ModifyReservationDialog`).
- **Tablas propias (escritura):** `solicitud_reserva`, `solicitud_intento`, `reserva`, `reserva_segmento`, `reserva_evento`, `reserva_modificacion`.
- **Puertos que implementa:** `contracts.BookingPort`.
- **Puertos que consume:** `contracts.TripPort`, `contracts.WalletPort` (`ScheduleDelayedCredit`, `CreditPassengerRefund`), `contracts.NotificationPort` (`Notify`, `CreateReservationChat`), `shared/geo`.
- **Lógica crítica y reglas:**
  1. **Búsqueda espacial PostGIS:** filtrar viajes `'publicado'`/`'en_curso'` con `acepta_reservas = TRUE` donde `ST_DWithin(ruta_operativa, punto_recogida, desvio_maximo_km * 1000)` y `ST_DWithin(ruta_operativa, punto_destino, desvio_maximo_km * 1000)`, garantizando `fraccion_recogida >= viaje.fraccion_avance_ruta` y `fraccion_destino > fraccion_recogida`, y verificando disponibilidad en el intervalo `[fraccion_recogida, fraccion_destino)`.
  2. **Cotización de tramo:** calcular distancia del segmento sobre la polilínea y tarifa según `viaje.tipo_tarifa` (`tarifa_km * dist` o `precio_asiento_completo * fraccion`).
  3. **Solicitar viaje (`WithTx`):**
     - Exigir `RequireVerifiedIdentity`.
     - Validar `intentos_consumidos < 3` en `solicitud_reserva` para ese `(viaje_id, pasajero_id)`.
     - Si `viaje.aceptacion_automatica AND viaje.gps_confiable`: crear directamente `reserva` en `'esperando_pasajero'` (`hold_expira_at = NOW() + 60s`). Si no: dejar `solicitud_reserva` en `'pendiente'` (`expira_at = NOW() + 60s`).
  4. **Cancelar solicitud previa:** si `solicitud_reserva.estado = 'pendiente'`, cancelar marcando `cancelacion_voluntaria_previa = TRUE, cuenta_para_limite = FALSE` (**no incrementa `intentos_consumidos`**).
  5. **Confirmar Hold 60 s (`WithTx`):**
     - `SELECT ... FOR UPDATE` sobre `reserva`; verificar `estado = 'esperando_pasajero'` y `hold_expira_at > NOW()`.
     - Cambiar a `'confirmada'`, cancelar otras solicitudes/holds pendientes del mismo pasajero en otros viajes (liberando retenciones), registrar `reserva_evento` y llamar `NotificationPort.CreateReservationChat(tx, reservaID)`.
  6. **Modificaciones:** recogida (máx. 1), destino (máx. 1) y asientos (aumento verifica capacidad; reducción tras pagar crea `reembolso` diferido 24 h vía `WalletPort.ScheduleDelayedCredit`).
- **DoD:** búsqueda por cercanía en Lima, solicitud manual y automática, hold 60 s con bloqueo ante concurrencia, límite de 3 intentos y modificaciones de reserva.

---

## Módulo 4: `active_trip_gps` — Viaje Activo, GPS, No-Show, Cancelaciones y Calificaciones
- **Rama base:** `feat/active-trip-gps`
- **Pantallas Android (`OneRouteAndroid`):** `MeetingPointScreen`, `ActiveTripScreen`, `LiveNavigationScreen`, `TripHistoryScreen`, `TripSummaryScreen`, `CancelTripScreen`, `RateUserScreen`, `TrackingLinkDialog`, `EarlyFinishDialog`, `NoShowWaitDialog`.
- **Tablas propias (escritura):** `viaje_ubicacion_gps`, `enlace_seguimiento`, `reserva_cancelacion`, `reserva_no_show`, `historial_no_show`, `calificacion`.
- **Puertos que implementa:** ninguno obligatorio (o `TrackingPort` si se requiere).
- **Puertos que consume:** `contracts.BookingPort`, `contracts.WalletPort` (`SettleRidePayment`, `CreditPassengerRefund`, `ProcessTipPayment`), `contracts.NotificationPort`, `contracts.IdentityPort`, `shared/realtime`, `shared/geo`.
- **Lógica crítica y reglas:**
  1. **Ingesta GPS (REST batch offline + WebSocket):** insertar en `viaje_ubicacion_gps` usando `client_event_id` (`ON CONFLICT (client_event_id) DO NOTHING` para idempotencia offline), proyectar posición en la ruta para actualizar `viaje.ultima_ubicacion` y `fraccion_avance_ruta`, y emitir `trip.gps_updated` a la sala `trip:{viaje_id}`.
  2. **Inicio de viaje y abordaje:** al abordar el pasajero (`MarkReservationBoarded`), disparar `WalletPort.SettleRidePayment` si el método es digital/billetera (el efectivo queda pendiente de confirmación del conductor).
  3. **No-Show (5 min):** registrar llegada del conductor (`conductor_llego_recogida_at`), permitir extensión (+3 min), y tras vencimiento marcar `no_show` (`reserva_no_show` + `historial_no_show`) liberando asientos **sin cobro digital**.
  4. **Finalización anticipada (*"Finalizar recorrido"*):** registrar punto GPS real de descenso, recalcular distancia/tarifa, acreditar diferencia a `Saldo Pasajero` si bajó, liberar tramos restantes y marcar `completada`.
  5. **Cancelación individual:** calcular distancia actual del conductor al punto de recogida del pasajero con PostGIS (`ST_Distance`), aplicar regla configurable (`lejos` = 100%, `cerca` = parcial, `en punto` = 0%), registrar `reserva_cancelacion` y reembolsar si aplica.
  6. **Enlace de seguimiento público:** generar token aleatorio (`crypto/rand`), guardar `SHA-256` en `enlace_seguimiento`, exponer endpoint público `GET /api/v1/tracking/{token}` sin auth que muestra ubicación básica hasta que expire o sea revocado.
  7. **Calificaciones:** 1–5 estrellas + etiquetas + comentario + `es_anonima` + propina opcional (`WalletPort.ProcessTipPayment`); si la contraparte ya calificó, publicar ambas inmediatamente; si no, `publicar_at = NOW() + 24h`.
- **DoD:** tracking GPS en vivo con WebSocket, sincronización offline por lote sin duplicados, no-show, fin anticipado, enlace público y calificación ciega 24 h.

---

## Módulo 5: `payments_wallets` — Pagos, Triple Billetera, Comisiones y Promociones
- **Rama base:** `feat/payments-wallets`
- **Pantallas Android (`OneRouteAndroid`):** `WalletPaymentsScreen` (3 billeteras: `Pasajero`, `Ganancias`, `Comisiones`; recargas, retiros, créditos retenidos 24 h, métodos de pago digital/efectivo/billetera/mixto).
- **Tablas propias (escritura):** `metodo_pago`, `pago`, `pago_intento`, `reembolso`, `billetera`, `movimiento_billetera`, `recarga_billetera`, `retiro`, `tarifa_comision`, `comision_viaje`, `promocion`, `promocion_aplicacion`.
- **Puertos que implementa:** `contracts.WalletPort`.
- **Puertos que consume:** `contracts.NotificationPort`, `shared/providers` (`PaymentGateway`).
- **Lógica crítica y reglas:**
  1. **Libro mayor inmutable (`movimiento_billetera`):** toda variación de saldo en `billetera` **debe** ocurrir dentro de `WithTx` haciendo `SELECT ... FROM billetera WHERE id = $1 FOR UPDATE`, verificando reglas de tipo y creando el movimiento con `saldo_anterior` y `saldo_nuevo` exactos.
  2. **Liquidación al abordar (`SettleRidePayment`):**
     - Aplicar promoción si existe (`promocion_aplicacion` con `descuento_total = aporte_plataforma + aporte_conductor`).
     - Desglosar `monto_billetera + monto_digital + monto_efectivo = monto_total`.
     - Si falla el intento digital (`pago_intento.estado = 'fallido'`), permitir reintento o cambio a efectivo (autorizado por el conductor en `autorizado_por`).
     - Si es efectivo: no se completa hasta que el conductor confirme recepción (`POST /payments/{id}/confirm-cash`).
     - Calcular `comision_viaje`: descontar comisión antes de acreditar el neto a `ganancias` (si es digital), o descontarla de `comisiones` (si es efectivo).
  3. **Recargas y Retiros:**
     - `recarga_billetera`: solo `comisiones` y `pasajero` (trigger `trg_validar_recarga_billetera`).
     - `retiro`: solo `ganancias` (trigger `trg_validar_retiro_billetera`), soporta `transferencia_bancaria`, `yape`, `plin`, `cci`.
  4. **Retención 24 h:** al reducir asientos pagados, insertar `reembolso` con `estado = 'programado'` y `disponible_at = NOW() + 24h` para que el scheduler lo acredite en `pasajero`.
- **Mocks vs Real:** usa `PaymentGateway` mock (`success`/`failed`) para pruebas; arquitectura lista para enchufar Culqi o MercadoPago.
- **DoD:** 3 billeteras consistentes sin doble abono, pago mixto/efectivo/reintento tras fallo, recarga de comisiones, retiros y acreditación de créditos 24 h.

---

## Módulo 6: `messaging_safety_support` — Chat, Notificaciones, SOS, Reportes y Soporte
- **Rama base:** `feat/messaging-safety-support`
- **Pantallas Android (`OneRouteAndroid`):** `MessagesInboxScreen`, `RideChatScreen`, `NotificationsScreen`, `HelpCenterScreen`, `FaqArticleDetailScreen`, `SupportTicketChatScreen`, `SafetyReportsScreen`, diálogos `SOS` en `ActiveTripScreen` y `LiveNavigationScreen`.
- **Tablas propias (escritura):** `categoria_incidente`, `categoria_reporte`, `categoria_notificacion`, `alerta_sos`, `alerta_sos_destinatario`, `incidente`, `incidente_evidencia`, `reporte`, `reporte_categoria`, `reporte_comunicacion`, `reporte_evidencia`, `investigacion`, `descargo`, `descargo_evidencia`, `restriccion_preventiva`, `sancion`, `apelacion_sancion`, `chat_reserva`, `mensaje_chat`, `mensaje_reporte`, `notificacion`, `preferencia_notificacion`, `ticket_soporte`, `ticket_mensaje`, `ticket_adjunto`.
- **Puertos que implementa:** `contracts.NotificationPort`.
- **Puertos que consume:** `contracts.IdentityPort` (contactos de emergencia para SOS), `shared/realtime`, `shared/storage`, `shared/providers` (`PushProvider`).
- **Lógica crítica y reglas:**
  1. **Chat privado por reserva:** validar que el remitente sea el pasajero o el conductor de esa reserva (o soporte autorizado en solo lectura); emitir por WebSocket a `reservation:{reserva_id}`; idempotencia con `client_event_id`.
  2. **Notificaciones y preferencias:** consultar `preferencia_notificacion` antes de enviar Push; el trigger `trg_validar_preferencia_notificacion` impide apagar categorías críticas (`sos`, `seguridad_critica`, `sancion`).
  3. **SOS (`POST /api/v1/sos`):** solo durante viaje activo; guardar `alerta_sos` (idempotente por `client_event_id`), crear filas en `alerta_sos_destinatario` para plataforma + contactos de emergencia (`IdentityPort`), notificar por WS/SMS/Push y devolver confirmación explícita al cliente (si no hay red, Android ofrece llamada 105).
  4. **Reportes inmutables y descargos:** al crear `reporte` (con múltiples `reporte_categoria`), guardar evidencias iniciales (`comunicacion_id IS NULL`) e `investigacion`; el trigger bloquea edición/borrado. Al notificar al reportado (`notificado_at = NOW()`), inicia ventana de **7 días calendario** (`America/Lima`) validada en BD para presentar `descargo`.
  5. **Restricciones preventivas, sanciones y apelaciones:** `apelacion_sancion` validada a 7 días calendario por trigger; soporte de tickets con adjuntos privados.
- **DoD:** chat en vivo por WebSocket, preferencias de notificación protegidas, flujo SOS completo, reportes inmutables + descargos 7 días, sanciones/apelaciones y tickets de soporte.
