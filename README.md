# Bongo's Community Clans — 1.0.4


## Autorstwo AI / AI disclosure

Kod tego moda został napisany przez AI — OpenAI Codex — na podstawie wymagań i wskazówek Bongo.

This mod's code was written by AI — OpenAI Codex — based on Bongo's requirements and guidance.

**Prosty plugin na klany bez tej "toksycznej" części.**

Mod serwerowy Fabric dla **Minecraft Java 26.3**. Działa z klientem vanilla; gracze nie instalują tego moda. Nie dodaje terenów, wojen, ekonomii ani systemu walk klanowych.

## Wymagania i instalacja

- Minecraft Java 26.3, Java 25, Fabric Loader 0.19.5 lub nowszy zgodny z 26.3.
- Fabric API 0.161.0+26.3.
- Umieść `BongosCommunityClans-1.0.4.jar` w `mods/`, usuń poprzedni JAR tego moda i uruchom ponownie serwer. Zwykłe `/reload` nie ładuje modów.
- Na wskazanym serwerze testowym Fabric API jest już zainstalowane.

Paczka jest budowana z przetworzonych zasobów przez `tools/build-cat-pack.py`; nie używa animowanej tekstury z globalną fazą. Każda klatka ma osobny model wybierany przez serwer.

## Pierwszy klan

```text
/clan create MasloTeamX MasloTeamX
/clan change tag &#a1b2c3Maslo&4TeamX
/clan invite Kolega
```

Zaproszony gracz wpisuje `/clan accept`. Przed nickiem pojawi się `[MasloTeamX] Nick` na czacie, w TAB i w nametagu nad głową. Kody kolorów wpisuje się bezpośrednio w tagu; każdy fragment może mieć inny kolor. Nawiasy kwadratowe wokół tagu mają zawsze kolor `&8` (ciemnoszary); nick pozostaje biały.

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
| `/clan change tag <tag>` | Zmienia tag i jego kolory, np. `&1Super&4Clan` lub `&#000000Super&#ff0000Clan` |
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

- Nazwa: 1–32 znaki; litery (także polskie), cyfry dziesiętne i `-_.`; bez spacji. Kody kolorów są zabronione zarówno przy tworzeniu klanu, jak i zmianie nazwy.
- Tag: 1–12 punktów kodowych Unicode, po normalizacji NFC i pominięciu poprawnych kodów kolorów. Emoji spoza podstawowej płaszczyzny liczy się jako jeden znak; sekwencje złożonych emoji mogą liczyć się jako kilka. Tag może zawierać spacje i symbole. Odrzucane są tagi puste/białe (także po usunięciu kodów kolorów), nowe linie, znaki sterujące i `§`.
- Przykłady: `/clan create Zażółć &a★🧈★`, `/clan change tag &1Super&4Clan`, `/clan change tag &#000000Super&#ff0000Clan`, `/clan change tag "&aMasło &bTeam"`. Cudzysłowy wokół tagu są opcjonalne; pomagają oznaczyć spacje na początku/końcu. Dosłowne cudzysłowy można wpisać w cytowanym tagu jako `\"`.
- Nazwy i tagi są unikalne, bez rozróżniania wielkości liter. Przy sprawdzaniu unikalności tagu kody kolorów są pomijane: `&aClan` i `&bCLAN` to ten sam tag.
- Wygląd glifów zależy od czcionki/zasobów klienta. Serwer nie zna listy glifów obsługiwanych przez każdy klient.
- Kolory w tagu: `&0`–`&f` oraz `&#RRGGBB`, bez rozróżniania wielkości liter. Można je dowolnie mieszać; kod działa do kolejnego kodu lub końca tagu. Kody nie zajmują limitu 12 znaków. Tekst przed pierwszym kodem jest biały. Inne sekwencje z `&`, w tym kody stylu takie jak `&k` oraz niepoprawne kody HEX, pozostają dosłownym tekstem i liczą się do limitu.
- Komenda `/clan change color` została usunięta. Istniejące klany zachowują poprzedni kolor jako domyślny do czasu zmiany tagu. Wpisanie nowego tagu bez kodów przywraca biały kolor.

## Czat, zaproszenia i język

Quickchat jest ustawieniem gracza, zapisywanym między sesjami. Po włączeniu `#siema` wysyła `siema` tylko do klanu; wiadomości bez `#` pozostają publiczne. `##tekst` pozostaje publiczny z oboma znakami `#`. Wyjście, wyrzucenie i usunięcie klanu wyłączają quickchat. Czat klanu nie jest wysyłany do graczy spoza klanu; operatorzy nie dostają osobnego podsłuchu. Administrator serwera nadal może widzieć wpisywane komendy w logach lub przez inne mody.

Gracz może należeć do jednego klanu i mieć jedno aktywne zaproszenie. Zaproszenie wygasa po 5 minutach, także podczas wyłączenia serwera. Utrata uprawnień lub odejście zapraszającego unieważnia jego zaproszenia. Ustawienie ignorowania zaproszeń jest trwałe.

Komunikaty, okno usunięcia i GUI są renderowane przez serwer w języku danego klienta: `pl_*` → polski, pozostałe → angielski. Zmiana języka w ustawieniach klienta dotyczy kolejnych komunikatów i ponownie otwartego GUI. Teksty użytkowników i nazwy klanów nie są tłumaczone.

## Okna i zapis

GUI informacji pokazuje nazwę, kolorowy tag, datę utworzenia w UTC, założyciela i główki członków z rangą oraz statusem online/offline. Strona mieści 36 członków; na dole znajdują się strzałki stron i zamknięcie. Wszystkie metody przenoszenia/klonowania przedmiotów z GUI są zablokowane. Główki osób online korzystają z ich aktualnego profilu.

Okno usunięcia odlicza 10 rzeczywistych sekund i pozwala anulować. Potwierdzenie ma losowy token przypisany do konkretnego gracza i klanu; wygasa po 90 sekundach. Serwer sprawdza czas, członkostwo i rangę, także przy ręcznie spreparowanym pakiecie. Rozłączenie usuwa oczekujące potwierdzenie. Niewykorzystane potwierdzenia nie przetrwają restartu.

Dane znajdują się w `<katalog świata>/bongo-community-clans.json`. Każda zmiana zapisuje zweryfikowaną kopię przez plik tymczasowy i zachowuje poprzednią wersję w `.bak`. Błąd zapisu nie zatwierdza zmiany w pamięci. Uszkodzony plik zatrzymuje uruchamianie obsługi klanów z błędem, zamiast zastępować go pustą bazą. Przy ręcznej edycji zatrzymaj serwer; zachowaj kopię JSON oraz dane świata.

Nametagi używają standardowych zespołów scoreboard o zarezerwowanych nazwach `bcc_*`. Członkostwo klanowe zajmuje slot zespołu gracza; poprzedni zespół jest zapamiętywany i przywracany przy wyjściu, jeżeli nadal istnieje. Mod nie wyłącza friendly fire ani kolizji i nie ujawnia niewidzialnych kolegów. Mody lub komendy, które niezależnie nadpisują zespoły/nazwy TAB, mogą wymagać integracji. Testowano pakiet modów z tego serwera, w tym BongoUtils 1.3.0 i NoChatReports 26.3-v2.21.0.

## Budowanie i testy

Zainstaluj JDK 25, ustaw `JAVA_HOME`, a następnie:

```text
gradlew.bat build
```

Gotowy mod znajduje się w `build/libs/BongosCommunityClans-1.0.4.jar`. Wrapper i zweryfikowany build korzystają z Gradle 9.6.0. Loom: 1.17.12. Źródła i testy znajdują się w tej paczce.

Testy JUnit obejmują role/hierarchię, zaproszenia i ich unieważnianie, przekazanie założyciela, zapis/restart, awarie zapisu, uszkodzone dane, Unicode, kolory i kompletność tłumaczeń. Testy protokołu uruchamia się wyłącznie na osobnym serwerze offline dostępnym lokalnie (domyślnie port 25581):

```text
gradlew.bat integrationTest -PtestPort=25581
```

Harness używa trzech klientów protokołu vanilla i nie wymaga OP. Sprawdza tagi w pakietach czatu/TAB/nametagów, prywatność czatu, GUI i próby wyciągania przedmiotów, polski/angielski, odliczanie, anulowanie, wcześniejsze zatwierdzenie i replay tokenu. Nie jest to test wizualny renderowania czcionek przez klienta Minecrafta.

Referencje wersji: [Fabric dla Minecraft 26.3](https://www.fabricmc.net/2026/09/15/263.html), [oficjalny przykład Fabric 26.3](https://github.com/FabricMC/fabric-example-mod/tree/26.3).
