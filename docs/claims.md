# Menu działek — 1.3.0

Integracja serwerowa dla Minecraft Fabric 26.3, GP3D **19.0.0 universal** i BongoUtils **1.7.0–1.8.0**. Klient vanilla wystarcza. BongoUtils nadal rejestruje `/manageclaim` i zapisuje flagi; BongoCC podłącza menu przez opcjonalne mixiny. Fork GP3D i aktualizacja wspieranego BongoUtils nie są potrzebne.

## Wybór i flagi

`/manageclaim` wybiera teren w aktualnej pozycji gracza, również według Y, z uwzględnieniem poddziałek. Nie używa poprzedniego zaznaczenia łopatą. Menu pozostaje przypięte do tego celu mimo przemieszczania się.

Jeśli działka główna ma podziały, ostrzeżenie pokazuje wybrany teren i działkę główną (ID, świat, współrzędne). `Kontynuuj` otwiera menu. `Chcę zmienić wybór` zamyka okno, podświetla całe drzewo i wysyła: „Stań w miejscu działki lub podpodziału, który chcesz edytować, powtórz wybór i zatwierdź operację ponownie.” Ponowne `/manageclaim` dokonuje nowego wyboru.

Menu główne: `Flagi`, `Dodawanie i usuwanie graczy`, czerwone `Usuń działkę` dla właściciela. `Flagi` otwierają dotychczasowy panel BongoUtils, przypięty do tego samego celu, nawet po przemieszczeniu się. `/manageclaim global` nadal otwiera wyłącznie stare globalne flagi, z zachowaniem ich dziedziczenia i lokalnych wyjątków.

GP3D 19.0.0 ma cztery natywne flagi: eksplozje, eksplozje Withera, PvP i alerty. Wszystkie są już wspierane przez BongoUtils; nie dodano fikcyjnych flag dla ustawień niewspieranych per działka. Zachowane są flagi klanów, teleportów i innych modów rejestrowane we wspólnym panelu.

## Gracze i trusty

`Dodaj gracza`: do 20 osób online na stronie, checkboxy wielokrotnego wyboru i opcjonalny nick offline. Zaznaczenia zachowują się między stronami. Nazwa offline jest rozwiązywana przez lokalną historię GP3D, bez zgadywania UUID i zapytań do Mojanga.

Po wyborze osób wybiera się wspólny poziom: brak podstawowego dostępu, korzystanie (Access Trust), pojemniki (Container Trust), budowanie (Build Trust). Przyciski mają tooltipy. Osobne checkboxy kontrolują Manage Trust i Neighbor Trust; GP3D przechowuje je niezależnie. Manage Trust pozwala również budować. Edycja nie usuwa odmów GP3D ani niezwiązanych trustów PvP/PvE.

`Usuń gracza` i `Zarządzaj permisjami gracza` pokazują wpisy bezpośrednie, odziedziczone i klanowe, także offline. Wpisy zapisane w poddziałce i jednocześnie obecne u rodzica mogą mieć kilka źródeł. Usunięcie bezpośredniego wpisu, po potwierdzeniu, korzysta z natywnego revokeTrust i obejmuje podziały zgodnie z GP3D. Dostęp odziedziczony, publiczny lub klanowy może pozostać. Wpis wyłącznie odziedziczony/klanowy nie ma przycisku usunięcia lokalnego. Manage Trust może odebrać wyłącznie właściciel. Nadawanie wpisów `[permission.node]` wymaga natywnego permissiontrust.

## `/claims`

- `Usuń działkę`: wybór własnej działki głównej, potwierdzenie z ID i informacją o usunięciu podziałów. Budynki zostają, znika ochrona.
- `Usuń wszystkie działki`: potwierdzenie zapamiętanej listy, odblokowywane po 10 rzeczywistych sekundach. Zmiana drzewa działek unieważnia operację.
- `Oddaj działkę`: własna działka główna, odbiorca online i potwierdzenie. Podziały przechodzą razem z działką. Ponownie sprawdzane są właściciel, odbiorca, natywne uprawnienie, jego saldo i limit liczby działek. Respektowane są `Claims.TransferClaim.Enabled` i cena. Płatne przekazywanie bez obsługiwanej ekonomii jest odrzucane jak w GP3D.
- `Statystyki`: natywne saldo GP3D — całość, zajęta i dostępna powierzchnia oraz liczba głównych działek. Podziały nie naliczają powierzchni ponownie.

Trust/untrust/trustlist, porzucanie, flagi i przekazywanie własnej działki przez stare komendy pokazują wskazówkę o menu. Dotyczy to aliasów i `/claim …`, bo przechwytywane są wspólne metody obsługi GP3D. Tworzenie, tryby łopaty, podziały, geometria i administracyjne operacje na cudzych działkach pozostają dostępne. Administracyjne komendy permission-node pozostają działające.

## Glow bez ghost-blocków

Przy `GriefPrevention.VisualizationGlow: true` i aktywnym glow gracza zatrzymywane są wyłącznie wysyłki fake blocków z renderera GP3D. Jego świecące display entities, kolory, geometria, odświeżanie i sprzątanie pozostają natywne. Atak w prawdziwy blok pod znacznikiem nie jest pochłaniany przez ghost-blocki. Gdy glow jest wyłączony, pozostaje natywna wizualizacja blokami.

W wydanym JAR-ze GP3D 19.0.0 `/gpglow` jest zaślepką; zmień konfigurację w pliku i zrestartuj serwer. Integracja dotyka wewnętrznych metod wskazanego adaptera Fabric — przy aktualizacji GP3D wymaga ponownego sprawdzenia mixinów.

## Walidacja i testy

Akcje mają losowe jednorazowe tokeny, przypisane do gracza i ważne 90 sekund. Cel, właściciel, geometria i podziały są zapamiętane na serwerze. Każda operacja ponownie sprawdza cel i uprawnienia. Otwarcie nowego menu lub rozłączenie unieważnia poprzednie akcje.

`gradlew.bat build` obejmuje testy jednostkowe. `claimMenuIntegrationTest` używa klientów protokołu vanilla i prawdziwego GP3D/BongoUtils na osobnym fixture: `127.0.0.1:25649`, RCON 25650, paczka klanów na osobnym porcie (np. 25651), glow i bezpłatne przekazywanie włączone w konfiguracji. Harness resetuje działki testowych kont MenuOwner/MenuPeer i wyłącza whitelistę — nie uruchamiać na produkcji. Test pakietów nie jest oceną wizualną renderowania klienta.
