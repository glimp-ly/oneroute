---
name: android-screen
description: >-
  Use this skill when adding or modifying Jetpack Compose screens, navigation
  routes, components, or state transitions in the Android app.
---

# Android Compose Screen Workflow (OneRoute)

Sigue este procedimiento para agregar o modificar pantallas en `android/app/src/main/java/com/oneroute/`.

## Steps

1. **Confirmar modulo/feature del integrante:**
   - Consulta la asignacion de pantallas en [03_MODULOS_Y_DIVISION_EQUIPO.md](../../../contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md).
   - Si no sabes que modulo esta desarrollando el integrante, **pregunta primero antes de editar**.

2. **Mantener el estilo visual, colores y componentes existentes:**
   - Usa exclusivamente `OneRouteTheme` (`core/theme/Theme.kt`), los tokens de [Color.kt](../../../android/app/src/main/java/com/oneroute/core/theme/Color.kt) (`PrimaryBlue`, `PrimaryBlueDark`, `PrimaryContainer`, `SoftContainer`, `CanvasBackground`, `CardSurface`, `OutlineSoft`, `TextPrimary`, `TextSecondary`, `TextMuted`, `SuccessGreen`, `EcoTeal`, `WarningAmber`, `DangerRed`) y `MaterialTheme.typography.*` de [Type.kt](../../../android/app/src/main/java/com/oneroute/core/theme/Type.kt). No agregues hex arbitrarios ni estilos ajenos al diseno actual.
   - Usa `Scaffold(containerColor = CanvasBackground)` y construye la interfaz reutilizando `core/components/` (`AppTopBar`, `BrandLogo`, `AppBottomBar`, `AppCard`, `ScreenList`, `SectionHeader`, `StatusPill`, `AvatarBadge`, `IconBubble`, `RatingLabel`, `PrimaryPillButton`, `TonalPillButton`, `DangerPillButton`, `AppTextField`, `SelectChip`).
   - En pantallas autenticadas con barra superior principal, habilita `showBrandLogo = true` en `AppTopBar` para conservar el logo circular y acceso al menu lateral (`LocalOpenAppMenu` -> `AppMenuDrawerContent`).
   - Nunca uses emojis en la interfaz ni comentarios; usa siempre `Icons.Rounded.*` o `Icons.AutoMirrored.Rounded.*`.

3. **Mantener navegacion y estado sincronizados (con TDD/BDD):**
   - Si agregas una ruta nueva en `core/navigation/Route.kt`, actualizala tambien en `RouteContent`, `skeletonVariant()` e `isAuthRoute()` dentro de `core/navigation/AppNavHost.kt`.
   - Toda regla de negocio de estado (hold 60 s, 3 intentos, 3 billeteras, tarifa del conductor) debe vivir en `AppState.kt` y seguir el skill `tdd-bdd-workflow` cubriéndola en `AppStateTest.kt` o `AppNavigatorTest.kt`.

4. **Cumplir reglas de recursos y localizacion (Peru):**
   - Todo recurso nuevo en `android/app/src/main/res/drawable/` debe tener nombre en minusculas (`[a-z0-9_.]`).
   - Usa exclusivamente moneda `S/` y `PEN`, documento `DNI`, cuentas `CCI` y bancos locales; nunca `$` como moneda, `MXN`, `INE`, `CLABE`, `SPEI` o `CFDI`.

5. **Verificar compilacion y pruebas:**
   ```bash
   cd android && ./gradlew testDebugUnitTest assembleDebug --no-daemon
   ```
   Confirma `BUILD SUCCESSFUL` y que todas las pruebas unitarias pasen.
