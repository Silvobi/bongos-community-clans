# Budowanie i testy klanów

[Powrót do README](../README.md)

## Budowanie i testy

Zainstaluj JDK 25, ustaw `JAVA_HOME`, a następnie:

```text
gradlew.bat build
```

Gotowy mod znajduje się w `build/libs/BongosCommunityClans-1.3.0.jar`. Wrapper i zweryfikowany build korzystają z Gradle 9.6.0. Loom: 1.17.12. Źródła i testy znajdują się w tej paczce. Scenariusze nowego menu działek i konfigurację fixture opisuje [menu działek](claims.md).

Testy JUnit obejmują role/hierarchię, zaproszenia i ich unieważnianie, przekazanie założyciela, zapis/restart, awarie zapisu, uszkodzone dane, Unicode, kolory i kompletność tłumaczeń. Testy protokołu uruchamia się wyłącznie na osobnym serwerze offline dostępnym lokalnie (domyślnie port 25581):

```text
gradlew.bat integrationTest -PtestPort=25581
```

Harness używa trzech klientów protokołu vanilla i nie wymaga OP. Sprawdza tagi w pakietach czatu/TAB/nametagów, prywatność czatu, GUI i próby wyciągania przedmiotów, polski/angielski, odliczanie, anulowanie, wcześniejsze zatwierdzenie i replay tokenu. Nie jest to test wizualny renderowania czcionek przez klienta Minecrafta.

Referencje wersji: [Fabric dla Minecraft 26.3](https://www.fabricmc.net/2026/09/15/263.html), [oficjalny przykład Fabric 26.3](https://github.com/FabricMC/fabric-example-mod/tree/26.3).

## GP3D i regresja kolorowych tagów

W [repozytorium BongosTeleports](https://github.com/Silvobi/bongos-teleports) zadanie `sharedClaimsTest` testuje wszystkie trzy mody i GP3D 19.0.0. Sprawdza budowanie, skrzynie, natywne trusty, dołączenie i wyrzucenie z klanu, odmowy, poddziałki 3D, lokalne flagi, ranking i restart.

`clanTagCompatibilityTest -PpreviousClansJar=/ścieżka/BongosCommunityClans-1.1.0.jar -PfixedClansJar=/ścieżka/BongosCommunityClans-1.2.0.jar` odtwarza błąd `bad_tag` starej wersji, a następnie sprawdza poprawny start i restart tej samej bazy z nowym modem, kolory, czat, GUI i trusty. Oba JAR-y muszą znajdować się poza `verification-allmods/mods`, ponieważ test tymczasowo podmienia mod i przywraca nowszy w bloku końcowym.

Te zadania wymagają oddzielnego, przygotowanego `verification-allmods` z markerem fixture, aktualnymi modami i JAR-em testowym. Resetują dane testowego świata. Instrukcje i ograniczenia są opisane w repozytorium Teleports. Nie używaj serwera produkcyjnego ani jego świata.

## Katalog klanów 1.2.0

`gradlew.bat clanBrowserIntegrationTest -PtestPort=25639` wymaga osobnego serwera testowego i fixture 28 klanów, w tym najnowszego `Żółwie` z 28 członkami. Generator `scripts/prepare-clan-browser-fixture.cjs <ścieżka-do-nowego-świata>` odmawia nadpisania istniejącej bazy. Uruchamiaj wyłącznie na świeżym świecie testowym. Test sprawdza podpowiadanie Unicode, dostęp osoby spoza klanu, sortowanie, strony katalogu i członków, układ slotów, powrót do wybranej strony oraz blokadę przenoszenia/klonowania przedmiotów. W wariancie bez GP3D sprawdza ukrycie sortowania metrażu.

Testy jednostkowe `ClanBrowserTest` obejmują wszystkie osiem kierunków sortowania i granice stron. `ClaimLeaderboardTest` sprawdza sumy m² per wymiar, członków offline, poddziałki i duże wartości.
