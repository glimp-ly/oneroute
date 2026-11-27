# Instrucciones para Claude Code

Sigue estrictamente las reglas definidas en:
- `AGENTS.md` (raiz del monorepo)
- `android/AGENTS.md` (si trabajas en `android/`)
- `backend/AGENTS.md` (si trabajas en `backend/`)
- Los skills bajo `.agents/skills/*/SKILL.md`

**Reglas criticas:**
1. Si el usuario no indica que modulo (`auth_identity`, `routes_trips`, `search_bookings`, `active_trip_gps`, `payments_wallets`, `messaging_safety_support` o `shared/`) esta desarrollando, pregunta antes de escribir o modificar codigo.
2. Nunca uses emojis en ningun archivo, comentario, log o texto de UI.
3. En `android/`, respeta estrictamente el estilo visual, colores (`Color.kt`), tipografia (`Type.kt`) y componentes existentes (`core/components/`).
