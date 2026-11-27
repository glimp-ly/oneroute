# Reglas para Agentes en `android/` (OneRoute Kotlin + Compose)

Consulta primero `AGENTS.md` en la raiz y `contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md` para confirmar a que modulo pertenece la pantalla.

## 1. Regla obligatoria antes de avanzar

Si no sabes que modulo del proyecto o que pantalla/feature esta desarrollando el integrante, **pregunta antes de crear o modificar archivos en `android/`**.

## 2. Configuracion del subproyecto Android

- Paquete base: `com.oneroute` (`minSdk 29`, `compileSdk 37`, AGP `9.4.1`, Kotlin `2.2.10`).
- En Android Studio se debe abrir la carpeta `oneroute/android/`, no la raiz del monorepo.
- Antes de agregar pantallas nuevas, revisa el mapa de asignacion en `contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md`.

## 3. Sistema visual y consistencia de frontend (obligatorio)

Toda pantalla, modal o componente nuevo o modificado en `android/` debe integrarse visualmente sin alterar el diseno actual de la aplicacion:

- **Tema y paleta (`core/theme/Theme.kt`, `Color.kt`):** usa exclusivamente `OneRouteTheme` y los tokens existentes (`PrimaryBlue`, `PrimaryBlueDark`, `PrimaryBlueLight`, `PrimaryContainer`, `SoftContainer`, `CanvasBackground`, `CardSurface`, `SurfaceTint`, `OutlineSoft`, `TextPrimary`, `TextSecondary`, `TextMuted`, `SuccessGreen`/`SuccessContainer`, `EcoTeal`/`EcoContainer`, `WarningAmber`/`WarningContainer`, `DangerRed`/`DangerContainer`). Prohibido inventar colores hex arbitrarios, modo oscuro improvisado, colores neon o gradientes ajenos al sistema.
- **Tipografia (`core/theme/Type.kt`):** usa siempre `MaterialTheme.typography.*` sin sobrescribir familias tipograficas externas.
- **Componentes reutilizables (`core/components/`):** construye las vistas sobre `Scaffold(containerColor = CanvasBackground)` y reutiliza `AppTopBar`, `BrandLogo`, `AppMenuDrawerContent`, `AppBottomBar`, `AppCard`, `ScreenList`, `SectionHeader`, `StatusPill`, `AvatarBadge`, `IconBubble`, `RatingLabel`, `PrimaryPillButton`, `TonalPillButton`, `DangerPillButton`, `AppTextField`, `SelectChip` y `SkeletonContentHost`.
- **Geometria y espaciado:** conserva tarjetas redondeadas (`16.dp` a `24.dp`), jerarquia visual limpia sobre `CanvasBackground` (`#FFF7F9FF`) con tarjetas blancas (`CardSurface`) y contenedores suaves (`SoftContainer`).
- **Iconografia y cero emojis:** nunca uses emojis en textos de pantalla, toasts, dialogos ni comentarios; utiliza `Icons.Rounded.*` o `Icons.AutoMirrored.Rounded.*`.

## 4. Convenciones de recursos, navegacion y dominio

- **Nombres de recursos Android:** todo archivo en `res/drawable/` o `res/mipmap*/` debe estar estrictamente en minusculas (`[a-z0-9_.]`) para evitar fallos de AAPT2.
- **Moneda y localizacion (Peru):** usa siempre `S/` y `PEN` (`S/ 24.00 PEN`, `PEN / lugar`), `DNI`, `CCI`, `Yape`, `Plin` y `comprobante electronico`. Prohibido `$` como moneda, `MXN`, `INE`, `CLABE`, `SPEI` o `CFDI`.
- **Barras y menu lateral:** en pantallas autenticadas con barra superior principal usa `AppTopBar(..., showBrandLogo = true)` para mantener el logo circular y acceso al menu lateral (`LocalOpenAppMenu`).
- **Navegacion y estado compartidos (coordinar cambios):** toda modificacion a `core/navigation/Route.kt`, `AppNavHost.kt` o `AppState.kt` debe mantenerse compatible con las 6 areas funcionales y con `AppStateTest.kt`.
- **Reglas del dominio:** tarifa fijada por el conductor, solicitud + hold de 60 s con maximo 3 intentos, 1 cambio de recogida/destino y 3 billeteras (`Pasajero`, `Ganancias`, `Comisiones`).

## 5. Pruebas y verificacion obligatoria

- Para cambios de estado o navegacion, aplica primero los skills `tdd-bdd-workflow` y `build-tests` en `android/app/src/test/java/com/oneroute/`.
- Despues de cualquier cambio en `android/`, ejecuta desde `oneroute/android/`:

```bash
./gradlew testDebugUnitTest assembleDebug --no-daemon
```
