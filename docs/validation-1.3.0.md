# Walidacja 1.3.0

21 października 2026, Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3. Oddzielny serwer lokalny i świat fixture; serwera produkcyjnego nie modyfikowano.

| Sprawdzenie | Wynik |
|---|---|
| `gradlew.bat build` | PASS, 31 testów JUnit, 0 błędów i niepowodzeń |
| `claimMenuIntegrationTest` z GP3D 19.0.0 i BongoUtils 1.8.0 | PASS |
| Regresja `integrationTest -PtestPort=25649` | PASS |
| Uruchomienie bez GP3D i BongoUtils | PASS, klany wczytane, serwer osiągnął stan Done |
| Audyt wszystkich czterech zdalnych gałęzi | Brak pominiętych commitów; ponownie sprawdzono przed publikacją |

Scenariusze nowego menu: natywne świecące display entities bez markerów ghost-blocków; czerwony przycisk usuwania; istniejące flagi i globalne ustawienia; wybór wielu graczy i tooltipy; odrzucenie cudzych i zużytych tokenów; zapisy poziomów dostępu; aliasy i `/claim trust`; przypięcie celu po przemieszczeniu; natywne saldo bloków; odliczanie 10 sekund i anulowanie; potwierdzone usuwanie gracza; nick offline; utworzenie rzeczywistej poddziałki 3D złotą łopatą przez pakiety vanilla; ostrzeżenie i ponowny wybór pozycji; oznaczenie dziedziczenia; przekazanie działki z podziałami; usuwanie całego drzewa oraz potwierdzone usuwanie dwóch działek jednocześnie po nowym odliczaniu.

Regresja klanów: Unicode, legacy/HEX kolory, TAB/nametagi, zaproszenia, prywatny i publiczny czat, quickchat, GUI, blokada wyciągania przedmiotów, lokalizacja, role, odliczanie usuwania, anulowanie, za wczesne potwierdzenie i replay tokenu, ignorowanie zaproszeń i opuszczenie klanu.

Panel flag sprawdzono też wcześniej z BongoUtils 1.7.0. Pliki API flag i panelu nie różnią się między sprawdzonymi gałęziami BongoUtils 1.7.0 i 1.8.0. Nie zmieniano źródeł BongoUtils ani GP3D.

Są to testy modelu, serwera i protokołu. Nie wykonano wizualnej oceny okien przez renderujący klient Minecrafta. Mody i źródła nie zostały wdrożone na serwer produkcyjny.
