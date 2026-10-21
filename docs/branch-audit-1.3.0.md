# Audyt gałęzi przed 1.3.0

Stan sprawdzony 21 października 2026.

| Gałąź | Commit | Relacja do podstawy 1.3.0 |
|---|---|---|
| `main` | `cf9c5bc` | W całości zawarta |
| `codex/pl-en-messages-2026-10-11` | `4a0cb93` | W całości zawarta przez merge `c378bfe` |
| `codex/inline-tag-colors` | `c378bfe` | W całości zawarta: GP3D, tagi, tłumaczenia, odliczanie |
| `codex/clan-browser-mail-ui` | `7b76486` | Najnowsza wspólna podstawa: katalog i panel klanów |

Sprawdzono graf historii i `git log origin/codex/clan-browser-mail-ui..origin/<gałąź>` dla każdej pozostałej gałęzi. Wszystkie zakresy są puste — inne gałęzie nie zawierają pominiętych commitów. `codex/claim-management-ui` zaczyna się od `7b76486` i zachowuje cały ten zakres. Obecność zmian w historii uzupełniają testy kompilacji i regresji. Podczas przygotowania wersji nie usunięto starych gałęzi ani PR-ów.
