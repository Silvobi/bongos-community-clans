# Bongo's Community Clans — 1.0.3


## Autorstwo AI / AI disclosure

Kod tego moda został napisany przez AI — OpenAI Codex — na podstawie wymagań i wskazówek Bongo.

This mod's code was written by AI — OpenAI Codex — based on Bongo's requirements and guidance.

**Prosty plugin na klany bez tej "toksycznej" części.**

Mod serwerowy Fabric dla **Minecraft Java 26.3**. Działa z klientem vanilla; gracze nie instalują tego moda. Nie dodaje terenów, wojen, ekonomii ani systemu walk klanowych.

## Wymagania i instalacja

- Minecraft Java 26.3, Java 25, Fabric Loader 0.19.5 lub nowszy zgodny z 26.3.
- Fabric API 0.161.0+26.3.
- Umieść `BongosCommunityClans-1.0.3.jar` w `mods/`, usuń poprzedni JAR tego moda i uruchom ponownie serwer. Zwykłe `/reload` nie ładuje modów.
- Na wskazanym serwerze testowym Fabric API jest już zainstalowane.

## Kot i resourcepack

Zaakceptuj opcjonalną paczkę zasobów (około 4,76 MB) podczas łączenia z serwerem, przed wejściem do świata. Etap konfiguracji czeka na pełne załadowanie paczki, a następnie wpuszcza gracza do świata. Zależnie od ustawień klienta paczka może zostać zaakceptowana automatycznie. Samo dołączenie nie uruchamia kota.

W świecie wpisz `/clan cat`. Nie trzeba należeć do klanu ani mieć uprawnień operatora. Komenda korzysta z już załadowanej paczki i nie otwiera pytania o jej pobranie. Odmowa lub błąd pobrania nadal pozwala wejść do świata; komenda wtedy wyświetla informację o ponownym dołączeniu i zaakceptowaniu paczki. Stan załadowania jest związany z konkretnym połączeniem, nie przechodzi na kolejną sesję gracza.

Kot pojawia się 1,35 bloku przed graczem, na wysokości nóg, w miejscu wywołania. Płaski obraz obraca się pionowo w stronę kamery. Animacja ma 256 × 320 px, 137 klatek, 20 FPS i trwa 6,85 s. Audio rozpoczyna się razem z pierwszą klatką. Kot i audio trafiają tylko do osoby wywołującej komendę. Ponowne wywołanie zastępuje poprzednie. Efekt znika po zakończeniu, śmierci lub zmianie wymiaru; nie powstaje żaden przedmiot w świecie ani w ekwipunku. Przy opóźnieniach serwera pomijane są spóźnione klatki, aby nie rozciągać obrazu względem audio.

Paczka jest zawarta w JAR. Mod uruchamia jej mały host HTTP podczas konfiguracji pierwszego połączenia; nie trzeba instalować dodatkowego moda. Ustawienia powstają w `config/bongo-community-clans-cat.properties`. Domyślnie HTTP działa na `127.0.0.1`, na porcie serwera + 1 (testowy serwer: 25566). Ten adres obsługuje klienta uruchomionego na tym samym komputerze. Dla innych komputerów ustaw `http.bind` na odpowiedni adres nasłuchiwania i `pack.url` na osiągalny dla nich bezpośredni URL paczki. Można też hostować dołączony ZIP zewnętrznie i ustawić `http.enabled=false`. Zmiany konfiguracji wymagają restartu.

## Automatyczny kot w 1.0.3

W survivalu i adventure mod wykrywa Shift+kliknięcie zajętego slotu w GUI informacji klanu oraz zamknięcie tego samego okna pakietem klienta w ciągu 500 ms. Dotyczy zarówno próby zabrania dekoracji z GUI, jak i wrzucenia własnego przedmiotu do GUI. Kot uruchamia się po zamknięciu, wyłącznie dla tej osoby, z audio. W sesji gracza obowiązuje 30-sekundowy cooldown automatycznych wywołań. Aktywna animacja nie jest przerywana kolejnym gestem. Ręczne `/clan cat` pozostaje dostępne niezależnie od cooldownu.

Zwykłe kliknięcie, Shift+kliknięcie pustego slotu, powolne zamknięcie, kliknięcie zwykłe pomiędzy Shift+kliknięciem a zamknięciem, niezgodny identyfikator okna i inne rodzaje menu nie uruchamiają efektu. Zamknięcia wywołane przez serwer, nawigacja stron i ponowne otwieranie GUI nie są traktowane jak pakiet zamknięcia klienta. Creative i spectator nie wywołują automatycznego kota. Bez załadowanej paczki automatyczny efekt jest pomijany bez wiadomości i bez nowego pytania o zasoby.

To reakcja na konkretną sekwencję wejścia, bez kar i ingerencji w przedmioty. Obsługa ekwipunku pozostaje taka jak w 1.0.0. Odstęp jest liczony między obsługą kliknięcia i zamknięcia na serwerze; opóźnienia sieci/serwera mogą wpływać na rozpoznanie gestu.

Testy: 10 testów jednostkowych oraz klienci vanilla na osobnym serwerze z tym samym zestawem modów. Sprawdzono ofertę paczki w protokole konfiguracji przed wejściem do świata, tekst PL/EN, oczekiwanie na SUCCESSFULLY_LOADED (sama akceptacja i pobranie nie wpuszcza do świata), SHA-1 ZIP, brak automatycznej animacji przy dołączeniu, odmowę/błąd pobrania, ponowne połączenie, uruchomienie komendą bez nowego pytania, klatki 0–136, audio, zakończenie, prywatność i zastępowanie animacji. Wygląd kota został oceniony przez użytkownika w wersji 1.0.1; zasoby, geometria i odtwarzanie w 1.0.2 pozostają takie same.

Testy 1.0.3: rzeczywiste pakiety survivalowego GUI, oba kierunki Shift+kliknięcia, zachowanie dekoracji i własnych przedmiotów/liczby sztuk, negatywne gesty, prywatność audio/obrazu, cały 30-sekundowy cooldown i jego wygaśnięcie, ręczna komenda w trakcie cooldownu oraz klient bez paczki. Uruchomienie: `gradlew.bat guiPrankIntegrationTest` na izolowanym serwerze. Osobny test własnego przedmiotu: `gradlew.bat guiInventoryIntegrationTest -PtestPlayer=<nick>`; po komunikacie READY użyj `give <nick> minecraft:stone 8` w konsoli izolowanego serwera.

Paczka jest budowana z przetworzonych zasobów przez `tools/build-cat-pack.py`; nie używa animowanej tekstury z globalną fazą. Każda klatka ma osobny model wybierany przez serwer.

## Pierwszy klan

```text
/clan create MasloTeamX MasloTeamX
/clan change color #a1b2c3
/clan invite Kolega
```

Zaproszony gracz wpisuje `/clan accept`. Przed nickiem pojawi się `[MasloTeamX] Nick` na czacie, w TAB i w nametagu nad głową. Kolor dotyczy prefixu, a nie nicku; domyślnie jest biały.

## Komendy

| Komenda | Działanie |
|---|---|
| `/clan` lub `/clan help` | Pomoc |
| `/clan create <name> <tag>` | Tworzy klan i nadaje rangę założyciela |
| `/clan invite <username>` | Zaprasza gracza online; ważność 5 minut |
| `/clan accept` | Przyjmuje aktualne zaproszenie |
| `/clan decline` | Odrzuca aktualne zaproszenie |
| `/clan ignore` | Włącza/wyłącza blokowanie zaproszeń; włączenie odrzuca aktualne zaproszenie |
| `/clan kick <username>` | Usuwa członka, także offline |
| `/clan leave` | Opuszcza klan |
| `/clan promote <username> <class>` | Awansuje wskazanego członka |
| `/clan demote <username> <class>` | Degraduje wskazanego członka |
| `/clan transfer <username>` | Przekazuje założyciela; poprzedni staje się współzałożycielem |
| `/clan change name <name>` | Zmienia pełną nazwę |
| `/clan change tag <tag>` | Zmienia tag |
| `/clan change color <color>` | Zmienia kolor prefixu |
| `/clan chat <message>` | Wysyła wiadomość do osób online w klanie |
| `/clan quickchat` | Włącza/wyłącza wysyłanie wiadomości `#...` do klanu |
| `/clan info [page]` | Otwiera GUI skrzyni z informacjami i członkami |
| `/clan delete` | Otwiera okno usunięcia; zatwierdzenie dostępne po 10 sekundach |

Komendy zarządzające pojawiają się w podpowiedziach tylko dla uprawnionych osób. Nie wymagają OP. Uprawnienia są również sprawdzane przez serwer przy wykonaniu.

## Rangi

| Ranga | Zaprasza i usuwa niższe rangi | Zmienia nazwę, tag, kolor | Awansuje | Usuwa klan |
|---|---|---|---|---|
| `founder` — Założyciel | Tak | Tak | Do współzałożyciela lub zarządcy | Tak |
| `cofounder` — Współzałożyciel | Tak | Tak | Do zarządcy | Nie |
| `manager` — Zarządca | Zwykłych członków | Nie | Nie | Nie |
| `member` — Członek | Nie | Nie | Nie | Nie |

Obsługiwane są też polskie nazwy rang: `założyciel`, `współzałożyciel`, `zarządca`, `członek`, oraz ich wersje bez znaków diakrytycznych. Nie można usuwać, awansować ani degradować siebie lub osoby o równej/wyższej randze. Do zmiany założyciela służy wyłącznie `transfer`. Założyciel przed wyjściem przekazuje tę rangę albo usuwa klan.

## Nazwy, Unicode i kolory

- Nazwa: 1–32 znaki; litery (także polskie), cyfry dziesiętne i `-_.`; bez spacji.
- Tag: 1–12 punktów kodowych Unicode, po normalizacji NFC. Emoji spoza podstawowej płaszczyzny liczy się jako jeden znak; sekwencje złożonych emoji mogą liczyć się jako kilka. Tag może zawierać spacje i symbole. Odrzucane są puste/białe tagi, nowe linie, znaki sterujące i `§`.
- Przykłady: `/clan create Zażółć ★🧈★`, `/clan change tag "Masło Team"`. Cudzysłowy wokół tagu są opcjonalne; pomagają oznaczyć spacje na początku/końcu. Dosłowne cudzysłowy można wpisać w cytowanym tagu jako `\"`.
- Nazwy i tagi są unikalne, bez rozróżniania wielkości liter.
- Wygląd glifów zależy od czcionki/zasobów klienta. Serwer nie zna listy glifów obsługiwanych przez każdy klient.
- Kolory: `#ffffff`, `#a1b2c3`, `&a`, `a`, `white`, `dark_red` i pozostałe standardowe nazwy/kody Minecrafta. Kody stylu, takie jak `&k`, nie są kolorami. Parser obsługuje również `§a`, ale klient vanilla nie pozwala wysłać `§` w komendzie — użyj `&a`.

## Czat, zaproszenia i język

Quickchat jest ustawieniem gracza, zapisywanym między sesjami. Po włączeniu `#siema` wysyła `siema` tylko do klanu; wiadomości bez `#` pozostają publiczne. `##tekst` pozostaje publiczny z oboma znakami `#`. Wyjście, wyrzucenie i usunięcie klanu wyłączają quickchat. Czat klanu nie jest wysyłany do graczy spoza klanu; operatorzy nie dostają osobnego podsłuchu. Administrator serwera nadal może widzieć wpisywane komendy w logach lub przez inne mody.

Gracz może należeć do jednego klanu i mieć jedno aktywne zaproszenie. Zaproszenie wygasa po 5 minutach, także podczas wyłączenia serwera. Utrata uprawnień lub odejście zapraszającego unieważnia jego zaproszenia. Ustawienie ignorowania zaproszeń jest trwałe.

Komunikaty, okno usunięcia i GUI są renderowane przez serwer w języku danego klienta: `pl_*` → polski, pozostałe → angielski. Zmiana języka w ustawieniach klienta dotyczy kolejnych komunikatów i ponownie otwartego GUI. Teksty użytkowników i nazwy klanów nie są tłumaczone.

## Okna i zapis

GUI informacji pokazuje nazwę, tag, datę utworzenia w UTC, założyciela, kolor i główki członków z rangą oraz statusem online/offline. Strona mieści 36 członków; na dole znajdują się strzałki stron i zamknięcie. Wszystkie metody przenoszenia/klonowania przedmiotów z GUI są zablokowane. Główki osób online korzystają z ich aktualnego profilu.

Okno usunięcia odlicza 10 rzeczywistych sekund i pozwala anulować. Potwierdzenie ma losowy token przypisany do konkretnego gracza i klanu; wygasa po 90 sekundach. Serwer sprawdza czas, członkostwo i rangę, także przy ręcznie spreparowanym pakiecie. Rozłączenie usuwa oczekujące potwierdzenie. Niewykorzystane potwierdzenia nie przetrwają restartu.

Dane znajdują się w `<katalog świata>/bongo-community-clans.json`. Każda zmiana zapisuje zweryfikowaną kopię przez plik tymczasowy i zachowuje poprzednią wersję w `.bak`. Błąd zapisu nie zatwierdza zmiany w pamięci. Uszkodzony plik zatrzymuje uruchamianie obsługi klanów z błędem, zamiast zastępować go pustą bazą. Przy ręcznej edycji zatrzymaj serwer; zachowaj kopię JSON oraz dane świata.

Nametagi używają standardowych zespołów scoreboard o zarezerwowanych nazwach `bcc_*`. Członkostwo klanowe zajmuje slot zespołu gracza; poprzedni zespół jest zapamiętywany i przywracany przy wyjściu, jeżeli nadal istnieje. Mod nie wyłącza friendly fire ani kolizji i nie ujawnia niewidzialnych kolegów. Mody lub komendy, które niezależnie nadpisują zespoły/nazwy TAB, mogą wymagać integracji. Testowano pakiet modów z tego serwera, w tym BongoUtils 1.3.0 i NoChatReports 26.3-v2.21.0.

## Budowanie i testy

Zainstaluj JDK 25, ustaw `JAVA_HOME`, a następnie:

```text
gradlew.bat build
```

Gotowy mod znajduje się w `build/libs/BongosCommunityClans-1.0.0.jar`. Wrapper i zweryfikowany build korzystają z Gradle 9.6.0. Loom: 1.17.12. Źródła i testy znajdują się w tej paczce.

Testy JUnit obejmują role/hierarchię, zaproszenia i ich unieważnianie, przekazanie założyciela, zapis/restart, awarie zapisu, uszkodzone dane, Unicode, kolory i kompletność tłumaczeń. Testy protokołu uruchamia się wyłącznie na osobnym serwerze offline dostępnym lokalnie (domyślnie port 25581):

```text
gradlew.bat integrationTest -PtestPort=25581
```

Harness używa trzech klientów protokołu vanilla i nie wymaga OP. Sprawdza tagi w pakietach czatu/TAB/nametagów, prywatność czatu, GUI i próby wyciągania przedmiotów, polski/angielski, odliczanie, anulowanie, wcześniejsze zatwierdzenie i replay tokenu. Nie jest to test wizualny renderowania czcionek przez klienta Minecrafta.

Referencje wersji: [Fabric dla Minecraft 26.3](https://www.fabricmc.net/2026/09/15/263.html), [oficjalny przykład Fabric 26.3](https://github.com/FabricMC/fabric-example-mod/tree/26.3).
