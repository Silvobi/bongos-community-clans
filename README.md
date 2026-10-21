# Bongo's Community Clans 1.2.0

Serwerowy mod Fabric dla **Minecraft Java 26.3**: klany z rangami, kolorowymi tagami, prywatnym czatem, GUI oraz opcjonalnym współdzieleniem działek i rankingiem GP3D. Klient vanilla nie potrzebuje dodatkowego moda.

## Wymagania i instalacja

- **Java 25**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.3**.
- Opcjonalnie: **GriefPrevention3D 19.0.0** (uniwersalny JAR) i [BongoUtils 1.7.0](https://github.com/Silvobi/bongo-utils) do panelu flag.

Zatrzymaj serwer, wykonaj kopię świata, usuń poprzedni JAR klanów i umieść `BongosCommunityClans-1.2.0.jar` w `mods/`. Zachowaj `<świat>/bongo-community-clans.json` i `.bak`. Uruchom ponownie serwer; `/reload` nie podmienia modów.

**Aktualizacja po błędzie `bad_tag` z 1.1.0:** ta wersja łączy kolorowe tagi z GP3D. Limit 12 znaków dotyczy widocznego tagu, pomijając poprawne kody kolorów. Długi zapis gradientu nie blokuje startu. Nie skracaj tagów ani nie kasuj bazy klanów; wystarczy wymiana JAR-a. Walidacja uszkodzonych danych pozostaje aktywna.

Wersja 1.2.0 usuwa też przejście do „Oczekiwania na serwer” po kliknięciu nieaktywnego odliczania usunięcia klanu. Odliczanie pozostaje widoczne; zatwierdzenie i anulowanie nadal sprawdza serwer.

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
| `/clan info [klan]` | Informacje o własnym lub wskazanym klanie; podpowiadanie nazw |
| `/clans` | Lista klanów w dużej skrzyni, z sortowaniem i stronami |
| `/clan top [area\|claims] [page]` | Ranking powierzchni lub liczby działek GP3D; domyślnie powierzchnia |
| `/clan delete` | Otwiera okno usunięcia; zatwierdzenie dostępne po 10 sekundach |

Komendy zarządzające pojawiają się w podpowiedziach tylko dla uprawnionych osób. Nie wymagają OP. Uprawnienia są również sprawdzane przez serwer przy wykonaniu.

`/clans` jest dostępne także bez członkostwa. Sortowanie: najnowsze/najstarsze, A–Z/Z–A, najwięcej/najmniej członków oraz, z GP3D, największa/najmniejsza suma m². Każda opcja ma własną ikonę w górnym wierszu; wybrana świeci. Kliknięcie klanu otwiera jego informacje. Katalog mieści 27 klanów na stronie. `/clan info Żółwie` obsługuje bezpośrednio nazwy Unicode.

## Rangi

| Ranga | Zaprasza i usuwa niższe rangi | Zmienia nazwę i tag z kolorami | Awansuje | Usuwa klan |
|---|---|---|---|---|
| `founder` — Założyciel | Tak | Tak | Do współzałożyciela lub zarządcy | Tak |
| `cofounder` — Współzałożyciel | Tak | Tak | Do zarządcy | Nie |
| `manager` — Zarządca | Zwykłych członków | Nie | Nie | Nie |
| `member` — Członek | Nie | Nie | Nie | Nie |

Obsługiwane są też polskie nazwy rang: `założyciel`, `współzałożyciel`, `zarządca`, `członek`, oraz ich wersje bez znaków diakrytycznych. Nie można usuwać, awansować ani degradować siebie lub osoby o równej/wyższej randze. Do zmiany założyciela służy wyłącznie `transfer`. Założyciel przed wyjściem przekazuje tę rangę albo usuwa klan.

## Działki i ranking GP3D

Opcjonalna integracja z uniwersalnym **GriefPrevention3D 19.0.0**, Fabric **26.3**. Domyślnie aktualni członkowie tego samego klanu uzyskują na swoich własnych claimach dodatkowy **Build Trust**: budowanie, niszczenie, skrzynie oraz podstawowy dostęp. Dotyczy także działek członków offline i nowych claimów. Nie trzeba wzajemnie dopisywać nowych członków do list trustów.

Dodatkowe prawo jest sprawdzane przez silnik GP3D przy każdej operacji. Dołączenie daje je od razu; wyjście, wyrzucenie lub usunięcie klanu od razu je odbiera. Mod nie modyfikuje natywnych list trustów. Ręcznie nadane prawa pozostają niezależne od członkostwa. Założyciel klanu nie otrzymuje Manage Trust, prawa usunięcia/przejęcia działki ani zmiany właściciela. Jawne odmowy GP3D oraz działki i poddziałki administracyjne nie są omijane.

Ograniczone i zagnieżdżone poddziałki nie otrzymują domyślnie tego dodatkowego prawa. Z **BongoUtils 1.7.0** właściciel lub osoba z Manage Trust może użyć `/manageclaim` i ustawić w nich lokalną flagę **Dostęp członków klanu**. W zwykłych poddziałkach lokalne `off` również działa niezależnie od dostępu w działce nadrzędnej. Dostępne poziomy: `off`, `access`, `container`, `build`.

`/manageclaim global` ustawia domyślny poziom na działkach właściciela; lokalne wyjątki mają pierwszeństwo dla każdej flagi osobno. Domyślny poziom to `build`. Bez BongoUtils integracja klanowa nadal działa z domyślnym Build Trust, z zachowaniem odmów i ograniczeń poddziałek. **BongosTeleports 1.6.0** rozpoznaje ten sam dodatkowy trust dla TPA, padów i ich zarządzania (z zachowaniem dotychczasowej własności pada).

### Ranking działek

- `/clan top` lub `/clan top area [page]` — ranking powierzchni w **m²**.
- `/clan top claims [page]` — ranking liczby głównych działek.
- `/clan info` — liczba działek, suma m² i podział metrażu na wymiary w lore informacji o klanie.

Rankingi pokazują po 10 klanów na stronę. Liczą własne główne claimy wszystkich **aktualnych** członków, także offline. Poddziałki i claimy administracyjne nie dodają punktów. Metraż dotyczy X/Z; nieregularne claimy używają rzeczywistej powierzchni GP3D, a prostokąty szerokości i długości z wliczonymi skrajnymi blokami. Działka 10 × 10 bloków daje 100 m². Wymiary sumują się bez przelicznika Netheru. Ranking jest obliczany z bieżących claimów i członkostwa; przeniesienie własności, opuszczenie klanu czy zmiana rozmiaru wpływa na kolejne wyświetlenie. Suma używa 64-bitowych liczb. Przy remisie rozstrzyga drugi wynik, nazwa i identyfikator klanu.

Ranking nie przejmuje własności terenu, nie dodaje wojen ani nagród. Bez GP3D wyświetla komunikat o wymaganej integracji; pozostałe funkcje klanów działają normalnie.

Testy jednostkowe obejmują poziomy trustu, brak uprawnień zarządzania, sumowanie claimów offline, pomijanie poddziałek i adminów, rozdzielenie wymiarów oraz wartości powyżej 32 bitów. Wspólny test serwerowy w projekcie Teleports sprawdza rzeczywiste stawianie bloków, otwieranie skrzyń, wyrzucenie członka, ręczne trusty, odmowy i poddziałki 3D z lokalnym wyjątkiem, ranking oraz restart.

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

Komunikaty, okno usunięcia i GUI są renderowane przez serwer w języku danego klienta: `pl_*` → polski, pozostałe → angielski. Zmiana języka w ustawieniach klienta dotyczy kolejnych komunikatów i ponownie otwartego GUI. Teksty użytkowników i nazwy klanów nie są tłumaczone. Konsola/RCON otrzymują angielski komunikat, gdy komenda wymaga gracza.

## Okna i zapis

GUI informacji ma czarne szyby jako wypełnienie. W górnym wierszu są: tag (nametag), data UTC i założyciel (zegar), liczba członków (miecz), a z GP3D liczba działek, suma m² oraz podział world/nether/the end i dodatkowe wymiary (spawner). Miecz ulepsza się dla 1–6 członków: drewno, miedź, żelazo, złoto, diament, netheryt; większe klany zachowują netheryt. Trzy wiersze mieszczą 27 główek z rangą i statusem online/offline. Na dole: powrót do `/clans`, kartki poprzedniej/następnej strony i bariera zamknięcia. Powrót zachowuje sortowanie i stronę katalogu. Bez GP3D spawner pozostaje wypełnieniem. Wszystkie metody przenoszenia/klonowania przedmiotów z GUI są zablokowane. Główki osób online korzystają z ich aktualnego profilu.

Okno usunięcia odlicza 10 rzeczywistych sekund i pozwala anulować. Potwierdzenie ma losowy token przypisany do konkretnego gracza i klanu; wygasa po 90 sekundach. Serwer sprawdza czas, członkostwo i rangę, także przy ręcznie spreparowanym pakiecie. Rozłączenie usuwa oczekujące potwierdzenie. Niewykorzystane potwierdzenia nie przetrwają restartu.

Dane znajdują się w `<katalog świata>/bongo-community-clans.json`. Każda zmiana zapisuje zweryfikowaną kopię przez plik tymczasowy i zachowuje poprzednią wersję w `.bak`. Błąd zapisu nie zatwierdza zmiany w pamięci. Uszkodzony plik zatrzymuje uruchamianie obsługi klanów z błędem, zamiast zastępować go pustą bazą. Przy ręcznej edycji zatrzymaj serwer; zachowaj kopię JSON oraz dane świata.

Nametagi używają standardowych zespołów scoreboard o zarezerwowanych nazwach `bcc_*`. Członkostwo klanowe zajmuje slot zespołu gracza; poprzedni zespół jest zapamiętywany i przywracany przy wyjściu, jeżeli nadal istnieje. Mod nie wyłącza friendly fire ani kolizji i nie ujawnia niewidzialnych kolegów. Mody lub komendy, które niezależnie nadpisują zespoły/nazwy TAB, mogą wymagać integracji. Wspólne testy aktualizacji obejmują BongoUtils 1.7.0 i BongosTeleports 1.6.0 z GP3D 19.0.0.

## Budowanie i testy

Z JDK 25: `bash gradlew build` na Linuxie lub `gradlew.bat build` na Windowsie. Wynik: `build/libs/BongosCommunityClans-1.2.0.jar`; `-sources.jar` jest archiwum źródeł.

Testy jednostkowe obejmują role, zapis, zaproszenia, Unicode, kolory, tłumaczenia i reguły GP3D. Testy protokołu obejmują również dialogi, prywatność, TAB/nametagi, działki i odtworzenie błędu startu z kolorowym tagiem. [Instrukcje i ograniczenia testów](docs/testing.md).

## Dodatkowy efekt

Kod zawiera również opcjonalny efekt `/clan cat`, wymagający pobrania paczki zasobów. [Konfiguracja paczki i zachowanie efektu](docs/optional-effects.md).

## Autorstwo AI / AI disclosure

Kod tego moda został napisany przez AI — OpenAI Codex — na podstawie wymagań i wskazówek Bongo.

This mod's code was written by AI — OpenAI Codex — based on Bongo's requirements and guidance.
