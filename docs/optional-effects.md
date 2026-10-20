# Dodatkowy efekt kota

[Powrót do README](../README.md)

Obecny kod 1.1.1 zawiera także `/clan cat` i prywatny efekt po próbie Shift-kliknięcia przedmiotu w `/clan info` i szybkim zamknięciu okna. Podstawowe funkcje klanu i integracja GP3D działają bez pobrania paczki zasobów; sam efekt wymaga zaakceptowania opcjonalnej paczki oferowanej podczas łączenia.

Paczka jest osadzona w JAR-ze jako `src/main/resources/cat/clapping-cat.zip`. Mod udostępnia ją przez mały serwer HTTP. Konfiguracja powstaje w `config/bongo-community-clans-cat.properties`:

| Klucz | Domyślnie | Znaczenie |
| --- | --- | --- |
| `http.enabled` | `true` | Udostępnia osadzony ZIP przez HTTP |
| `http.bind` | `127.0.0.1` | Adres nasłuchiwania |
| `http.port` | Port Minecrafta + 1 | Port HTTP, domyślnie 25566 przy Minecraft 25565 |
| `pack.url` | `http://127.0.0.1:<port>/clapping-cat.zip` | Bezpośredni URL ZIP-a przekazywany klientowi |

Domyślne ustawienia umożliwiają pobranie paczki tylko klientowi na tej samej maszynie. Dla graczy zdalnych potrzebny jest osiągalny adres i port albo zewnętrzny hosting osadzonego ZIP-a (`http.enabled=false` i odpowiedni `pack.url`). Zmiany wymagają restartu. Odrzucenie paczki nie blokuje wejścia na serwer; `/clan cat` informuje wtedy o jej braku.

Animacja i dźwięk są wysyłane wyłącznie do odbiorcy. Efekt nie tworzy trwałej encji świata ani przedmiotu w ekwipunku. Automatyczny efekt nie działa bez paczki, u martwych graczy, w Spectator/Creative ani podczas trwającej animacji; ma 30 sekund przerwy. GUI nadal blokuje wyciąganie przedmiotów.

Testy protokołu: `catIntegrationTest`, `guiPrankIntegrationTest`, `guiInventoryIntegrationTest` — wyłącznie na odrębnym serwerze testowym. Nie są częścią standardowego `build`.
