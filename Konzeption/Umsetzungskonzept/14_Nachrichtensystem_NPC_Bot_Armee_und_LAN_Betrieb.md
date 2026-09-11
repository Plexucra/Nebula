# 14. Ingame-Nachrichtensystem, NPC-Bot-Armee und LAN-Betrieb

## Ausgangslage und Ziel

Drittes großes Feature nach der Client-Server-Migration (Umsetzungskonzept/13_...md):
ein Ingame-Nachrichtensystem zwischen Kommandanten, eine komplette Neuausrichtung
der NPCs als eigenständige Java-Bot-Armee (ursprünglich 20 Prozesse, seit 11.9.2026 40 Bots in einem Prozess, zwei verfeindete Lager),
und die Möglichkeit, die Anwendung im LAN von einem Smartphone oder einem
anderen Rechner aus zu erreichen. Die drei Teile hängen zusammen: die
Bot-Armee koordiniert ihre Spezialisierung über genau das neue
Nachrichtensystem.

## Teil 1: Ingame-Nachrichtensystem

Vollständig neues Feature ohne TS-Vorlage – player-zu-player, AUSDRÜCKLICH
keine Gruppen-/Broadcast-Nachrichten ("E-Mail"-Charakter). Aus der
`GameApi`-Paritätsanforderung (`SimulatedGameApiService` UND
`WebSocketGameApiService` müssen JEDE Methode implementieren, sonst bricht
die Garantie, die die gesamte Migration absichert) folgt: das Modell
(`Message`: `id`, `fromPlayerId`, `toPlayerId`, `subject`, `body`, `sentAt`,
`read`) und die fünf `GameApi`-Methoden (`sendMessage`, `inbox()`,
`sentMessages()`, `unreadMessageCount()`, `markMessageRead`) existieren
gleichermaßen im Backend (`Message.java`, `MessageCommands.java`,
`GameState.messages`, Dispatch-Fälle in `GameSocket.java`) und in beiden
Frontend-Implementierungen.

**Push statt Polling für die "E-Mail"-Erfahrung**: `GameSocket.pushMessages`
schickt nach jedem `sendMessage`/`markMessageRead` sofort den neuen Stand von
`inbox`/`sentMessages`/`unreadMessageCount` an ALLE offenen Verbindungen des
betroffenen Kommandanten (`ConnectionRegistry.connectionsOf`, ursprünglich für
Mehrgeräte-Logins gebaut). Damit das generisch funktioniert, wurde
`WebSocketGameApiService` um einen generischen Push-Cache erweitert
(`pollSetters`), der jeden nicht-`players`-Kanal, für den gerade ein Signal
existiert, sofort mit dem gepushten Wert aktualisiert – vorher war nur der
`players`-Kanal live, alles andere pollte auf sein Intervall.

**Frontend**: neue `messages`-Feature-Komponente (Posteingang/Postausgang-Tabs,
Verfassen-Formular mit Empfänger-Auswahl aus `players()`), neuer
Menüpunkt "Nachrichten" mit Ungelesen-Badge im App-Shell (analog zum
bestehenden Benachrichtigungsglocken-Muster).

**Verifiziert**: dauerhaft im e2e-Test `backend/e2e/full-playthrough.mjs`
(Schritt 6) – zwei echte Kommandanten über echte WebSocket-Verbindungen,
Nachricht in beide Richtungen, `markMessageRead`, und die explizite Ablehnung
einer Nachricht an sich selbst (`assert.rejects`). Letzter beobachteter Lauf:
Nachricht `msg_1a3` (B→A) im Posteingang, gelesen markiert; Antwort `msg_1a4`
(A→B) angekommen; Selbstnachricht korrekt mit Fehlermeldung abgelehnt.

## Teil 2: NPC-Bot-Armee als eigenständige Java-Anwendung

### Architekturentscheidung: vollständige Ablösung des alten NPC-Systems

Der Auftrag lautete wörtlich, "nur die aktive KI-Steuerungslogik" im Backend
zu ersetzen, mit dem ausdrücklichen Vorbehalt, vorher zu prüfen, ob `Npc`
daneben noch eine unabhängige Rolle hat (z. B. reine Marktgegenparteien ohne
KI-Verhalten). Prüfung im Code (`Npc.java`-Javadoc, `WorldSeed.spawnNpcs`,
alle Verwendungsstellen in `state`/`ws`): es gibt KEINE solche unabhängige
Rolle – ein `Npc` ist gleichzeitig und ausschließlich die KI-gesteuerte
Kolonie UND das einzige "NPC-Marktteilnehmer"-Konzept im Spiel. Da
`GameApi`-Parität verlangt, dass UI-Komponenten (Statistik-Tabelle,
Besitzeranzeige in Galaxiekarte/Systemansicht/Kolonie-Detail), die auf
`npcs()`/`npcColony()`/`ownerWallet()` zugreifen, in BEIDEN
`GameApi`-Implementierungen identisch funktionieren, wurde entschieden, das
gesamte `Npc`-Konzept ersatzlos aus Frontend UND Backend zu entfernen – eine
bewusst weitergehende Konsequenz als die wörtliche "nur Backend"-Formulierung
des Auftrags, aber notwendig, um keine tote/inkonsistente API-Fläche zurückzulassen.

Entfernt: `Npc.java`/`npc.model.ts`, `NpcCommands.java`,
`WorldSeed.spawnNpcs`/`selectNpcClusterIndices`/`specialtyProductFor` und
alle zugehörigen Konstanten (`NPC_COUNT`, `NPC_PLANET_NAMES`,
`NPC_NAME_POOL`, `SEALED_ELERIUM_RESERVE_NPC`), die NPC-Statistiktabelle im
Frontend, die NPC-KI-Tick-Sektion in `SimulatedGameApiService`
(`runNpcAiTick` und alle `npcMaybe*`-Helfer). `universeStats` (Zeitreihe der
Stabilitätskennzahlen) blieb als eigenständiges Feature erhalten, nur die
Implementierung wanderte von `NpcCommands` nach `EconomyTick`, wo sie
inhaltlich hingehört.

### Warum eine eigenständige Java-Anwendung statt privilegierter NPC-Logik

Die neuen NPCs verhalten sich wie ECHTE Spieler: eigener Prozess pro Bot,
Anmeldung über `registerPlayer`, danach ausschließlich reguläre
`GameApi`-Befehle über dieselbe `/game`-WebSocket-Schnittstelle wie ein
Browser-Client – kein direkter `GameState`-Zugriff, keine Sonderrechte. Das
ist konzeptionell sauberer als das alte In-Prozess-NPC-System und beweist
nebenbei, dass das Server-Protokoll vollständig genug ist, um einen
kompletten, autonomen Spieler ausschließlich darüber zu steuern.

**`npc-bot/`** (Repo-Wurzel, Geschwister von `backend/`/`frontend/`): reines
Java 21, KEIN Quarkus – `java.net.http.HttpClient.newWebSocketBuilder()` für
die WebSocket-Verbindung, Jackson (`jackson-databind`) für JSON. Bewusst
KEINE Maven-Modulabhängigkeit zu `backend`: `ClientMessage`/`ServerMessage`
sind als eigene, kleine Klassen in `npc-bot/.../ws/` dupliziert (zwei
komplett unabhängig deploybare Einheiten, siehe Auftrag). Fat-Jar über
`maven-shade-plugin` (`target/npc-bot.jar`, `Main-Class: de.nebula.npcbot.Bot`).

### Prozess-Design

Ein Prozess = ein Kommandant. Start:
```
java -jar npc-bot.jar --index=3 --camp=NORD --server=ws://localhost:8080/game
```
Name folgt `NPC-<Nord|Sued>-<Index zweistellig>` (z. B. `NPC-Nord-03`).
Lagerzugehörigkeit und Freund-/Feinderkennung laufen ausschließlich über
diesen Namenspräfix, ausgewertet zur Laufzeit gegen `players()` – bewusst
KEIN neues Fraktions-/Bündnis-Datenmodell im Backend, wie im Auftrag
vorgegeben. Eine Entscheidungsschleife (`Bot.tick`, alle 8 Sekunden
Realzeit) durchläuft bei jedem Tick: Spielerliste aktualisieren,
Spezialisierungs-Koordination, Wirtschaft (Gebäude/Produktion/Werft),
Diplomatie (Kriegserklärungen), Verteidigung (Heimat-Blockade), Offensive
(Zielwahl/Anreise/Gefecht/Rückzug).

### Spezialisierungs-Koordination über das neue Nachrichtensystem

Bewusst einfache, DETERMINISTISCHE Koordination statt einer Verhandlung
(explizit als Vereinfachung dokumentiert, wie im Auftrag verlangt): der Bot
mit `--index=1` je Lager gilt als Koordinator. Eine feste Liste von zehn
Tier-2-Zwischenprodukten (`Catalog.SPECIALTY_PRODUCTS`: `p_stahl`,
`p_leichtmetalllegierung`, `p_hochtemplegierung`, `p_leitermetall`,
`p_katalysatormetall`, `p_magnetwerkstoff`, `p_halbleiterrohstoff`,
`p_keramikwerkstoff`, `p_glaswerkstoff`, `p_verbundwerkstoff`) wird 1:1 nach
Bot-Index verteilt: Produkt `N-1` an Bot mit Index `N`. Der Koordinator
setzt sein eigenes Produkt lokal, verschickt die übrigen neun Zuteilungen
per `sendMessage` (Betreff `"Spezialisierung"`, Text = Produkt-ID), sobald
der jeweilige Kollege in `players()` auftaucht – mit Retry über mehrere
Ticks, da die Startreihenfolge der 20 Prozesse nicht garantiert ist. Die
neun anderen Bots lesen passiv ihre `inbox()`, übernehmen das erste
passende `"Spezialisierung"`-Nachricht vom bekannten Koordinator und
markieren sie gelesen.

### Wirtschafts-/Militär-Heuristik (bewusst nicht optimal, aber real)

- **Produktion**: sobald die eigene Spezialisierung bekannt ist, einmalig
  `queueProduction` mit `autoProduceMissing=true, requeueOnComplete=true` –
  läuft danach dauerhaft von selbst weiter (wie auch die beiden
  Start-Aufträge Grundnahrung/Elerium aus `WorldSeed`, die ebenfalls
  `requeueOnComplete=true` tragen). Damit ist "Produktionswarteschlange
  läuft nie leer" strukturell erfüllt, ohne dass der Bot sie aktiv
  überwachen müsste.
- **Bebauung**: feste Prioritätsliste `b_industry → b_shipyard → b_habitat →
  b_powergrid` mit Obergrenzen (10/7/12/8, bewusst unter den
  Katalog-Maxima 20/15/20/20 – "sinnvolle Obergrenzen" statt Maxstufe). Bei
  jedem Tick wird die höchstpriore, noch nicht am Cap befindliche und nicht
  bereits im Ausbau befindliche Stufe versucht; schlägt ein Ausbau mangels
  Guthaben fehl, versucht der Bot im SELBEN Tick die nächstgünstigere
  Priorität statt denselben (zu teuren) Ausbau jeden Tick erneut anzustoßen
  (das war ein im ersten 20-Bot-Testlauf beobachteter Bug, siehe
  Testergebnisse unten).
- **Werft**: sobald `b_shipyard`-Stufe ≥ 1 und die eigene Werft-Warteschlange
  leer ist, wird abwechselnd Korvette/Zerstörer/Kreuzer (deckt den vollen
  Konterkreis ab) in Zweiergruppen nachbestellt, bis eine Gesamtobergrenze
  von 40 Kampfschiffen erreicht ist.
- **Verteidigung**: jeder Bot blockiert reflexartig die eigene Heimatwelt mit
  der Kampfflotte (`formBlockade`, Anker `PlanetOrbit` auf den Heimatplaneten)
  – laut `BlockadeCommands`/`BattleCommands` ist nur eine blockierende Flotte
  überhaupt ein gültiges Angriffsziel; symmetrisch für beide Lager, damit ein
  später ankommender Angreifer immer ein gültiges Ziel vorfindet.
- **Diplomatie**: jeder Bot erklärt jedem aktuell bekannten Mitglied des
  gegnerischen Lagers einzeln den Krieg (10×10 = 100 paarweise Beziehungen
  insgesamt), mit Retry über mehrere Ticks und stillem Ignorieren des
  "bereits im Krieg"-Fehlers ab dem zweiten Versuch.
- **Ziel- und Angriffslogik**: deterministische 1:1-Zuordnung – Bot Nr. N
  eines Lagers zielt auf Bot Nr. N des gegnerischen Lagers, statt einer
  aufwendigen Zielsuche. Angriffsschwelle: (a) eine Mindest-Aufbauzeit von
  90 Sekunden Realzeit seit Bot-Start ist verstrichen, (b) ein grober
  Flottenstärkevergleich (`Catalog.SHIP_MILITARY_WEIGHT`, 1:1 aus
  `ShipTypeDef.carrierSlotUsage` übernommen: Korvette 1, Zerstörer 2, Kreuzer
  4) über die tatsächlichen, live vom Server abgefragten Flotten beider
  Seiten (`allFleets`, bewusst ungefiltert – siehe `FleetCommands.allFleets`)
  fällt zugunsten des eigenen Bots aus (Marge ×1,1). Erst dann `moveFleet`
  zum gegnerischen Heimatsystem, danach `attackableFleetsInSystem` +
  `engageBattle`, sobald angekommen und das Ziel tatsächlich blockiert.
  Rückzug (`retreatFromBattle`), sobald die eigene Flotte während des
  laufenden Gefechts unter 35 % ihrer Stärke bei Gefechtsbeginn gefallen ist.

**Dokumentierte Vereinfachung – warum die Angriffsschwelle NICHT auf
tatsächlichem Flottenzuwachs wartet**: ein einzelnes Kampfschiff hat in
diesem Prototyp trotz Zeitkompression (`Clock.REAL_MS_PER_GAME_HOUR = 2500`)
eine mehrstufige Fertigungskette (Rumpf/Antrieb/Energie/Elektronik/
Waffen/Versorgung, je wieder aus rohstoffnahen Vorprodukten) – im e2e-Test
wurde für eine erste Korvette ganz ohne Lagerbestand `ChainPlan.totalHours
=752,3` beobachtet, macht bei 2500 ms/Spielstunde ca. 31 Realzeit-Minuten für
EIN Schiff. Eine Angriffsschwelle, die echten Flottenzuwachs voraussetzt,
hätte die Verifikation auf weit über eine Stunde gestreckt. Der
Stärkevergleich selbst bleibt aber vollständig real und serverseitig
(keine Fiktion) – er vergleicht nur meist noch die zufällig unterschiedlich
starken Startflotten beider Seiten, was für einen ersten Verifikationslauf
plausible, echte Gefechte erzeugt (siehe Testergebnisse).

Ein zweiter, während des ersten 20-Bot-Testlaufs entdeckter und korrigierter
Effekt: der Startgeldbeutel (6500 Credits, identisch zur menschlichen
Heimatwelt aus `WorldSeed`) ist die EINZIGE Einnahmequelle eines
Bot-Kommandanten – Spielerwallets erhalten in diesem Prototyp kein passives
Einkommen (`EconomyTick.payUpkeepAndWages` zieht nur ab,
`runWealthRedistributionIfDue` verteilt nur zwischen Bevölkerungs-, nicht
Spielerwallets um). Ohne aktiven Warenverkauf über den Markt ist die
Bau-Wirtschaft eines Bots also von Natur aus endlich – die Obergrenzen aus
der Bebauungs-Priorität wirken dadurch in der Praxis eher als "Guthaben
reicht nicht mehr" denn als "Stufen-Cap erreicht". Das ist eine plausible,
unveränderte Eigenschaft der bestehenden Spielwirtschaft (nicht Teil dieses
Auftrags, ein aktiver Verkaufs-/Einkaufs-Kreislauf zwischen Bots wäre ein
eigenständiges, größeres Feature) und wird hier nur dokumentiert, nicht
behoben.

### Start-/Stop-Skripte

`npc-bot/run-army.sh [server-url]` startete ursprünglich alle 20 Prozesse (10×
`--camp=NORD`, 10× `--camp=SUED`, `--index=1..10` je Lager). **Seit 11.9.2026**
startet es EINEN `BotArmy`-Prozess mit 40 Bots (20 je Lager, `BOTS_PER_CAMP`
überschreibbar) – 40 einzelne JVMs à 600 MB passten nicht mehr in den
Speicher. PID nach `npc-bot/army.pids`, Logs nach `npc-bot/logs/`.
`npc-bot/stop-army.sh` beendet alle in `army.pids` gelisteten Prozesse
sauber (SIGTERM).

### Testergebnisse

**Erster 20-Bot-Lauf** (vor dem Bebauungs-Fix): alle 20 Prozesse liefen
stabil, Registrierung/Koordination/Kriegserklärung/Verteidigung liefen
korrekt, aber jeder Bot verfing sich nach ca. 5 Ausbauten in derselben
"Nicht genug Credits"-Fehlermeldung (1500 Vorkommnisse über alle 20 Logs in
8 Minuten) – Ursache: die Bebauungsschleife brach nach dem ersten
(fehlgeschlagenen) Versuch je Tick ab, statt die nächstgünstigere Priorität
zu probieren. Fix wie oben beschrieben (siehe `Bot.maintainEconomy`).

**Zweiter 20-Bot-Lauf** (nach dem Fix, vollständig protokollgestützt über
`npc-bot/verify-army.mjs` verifiziert – ein Beobachter-Client, der sich
nacheinander testweise als einzelne Bots einloggt, da Kriege/Postfächer/
Gefechte serverseitig strikt auf den eingeloggten Kommandanten gefiltert
sind und es keinen Admin-Zugriff gibt):
- 20/20 Bot-Kommandanten registriert (`NPC-Nord-01`…`10`, `NPC-Sued-01`…`10`).
- `NPC-Nord-05` führte 10 aktive Kriege, ausnahmslos gegen SUED-Kommandanten.
- Koordinationsnachricht verifiziert: `NPC-Nord-01 → NPC-Nord-05`, Betreff
  `"Spezialisierung"`, Text `"p_katalysatormetall"`, bereits gelesen.
- Reales Gefecht verifiziert: `btl_5ye`, Angreifer `ply_ft`, Verteidiger
  `ply_i5`, `outcome=AttackerVictory`, 5 aufgelöste Ticks.
- Über den gesamten Lauf (ca. 6 Minuten): 11 gestartete Angriffe, 10
  gestartete Gefechte, 4 beendete Gefechte, 1 Rückzug, **0 Fehler**
  (`Befehl abgelehnt`/`Tick-Fehler` in keinem der 20 Logs).
- Anschließend sauber über `stop-army.sh` beendet, Backend gestoppt.

## Teil 3: LAN-Betrieb

**Ansatz**: Quarkus liefert das gebaute Angular-Production-Bundle als
statische Ressource selbst aus (`META-INF/resources`, Vert.x-Standardpfad),
statt einer separaten Web-Server-Instanz – ein Prozess, ein Port, kein CORS.

- `frontend/angular.json` nutzt den neuen `@angular-devkit/build-angular
  :application`-Builder; dessen `ng build --configuration production`-Ausgabe
  liegt unter `dist/nebula-frontend/browser/` (NICHT direkt unter
  `dist/nebula-frontend/`, wie bei älteren Angular-Versionen) – geprüft und
  im Kopierskript entsprechend berücksichtigt.
- `application.properties`: `quarkus.http.host=0.0.0.0` (statt nur
  `localhost`) – ausdrücklich nur für den lokalen WLAN-Betrieb, KEINE
  Internet-Veröffentlichung.
- `SpaFallbackResource.java` (neuer JAX-RS-Endpunkt `@Path("/{path:
  (?!game).*}")`): liefert `index.html` für jeden GET, für den kein
  statisches Asset existiert – nötig, damit ein direkter Aufruf/Neuladen
  einer Angular-Route wie `/nachrichten` nicht mit 404 scheitert (Vert.x'
  Static-Handler hat nachweislich Vorrang vor dieser Fallback-Route, siehe
  Testergebnisse: echte Assets wie `main-*.js` werden weiterhin direkt
  ausgeliefert). Kollidiert nicht mit dem `/game`-WebSocket-Endpunkt, der
  nur auf Upgrade-Requests reagiert.
- `frontend/src/app/core/sim/backend-config.ts`, `useWebSocketBackend()`:
  liefert jetzt automatisch `true`, sobald die Seite NICHT vom
  Angular-Dev-Server (Port 4200) geladen wurde – ein Smartphone im WLAN kann
  vorher kein `localStorage`-Flag setzen, und eine lokale
  Browser-Simulation ohne geteilte Galaxie ergäbe dort ohnehin keinen Sinn.
  Das bestehende `localStorage`-Flag bleibt in BEIDE Richtungen explizit
  wirksam (Override sowohl auf `"websocket"` als auch auf `"simulation"`),
  der normale `ng serve`-Workflow auf Port 4200 ist davon unberührt und
  bleibt beim bisherigen Simulation-Standard.
- `build-and-run-lan.sh` (Repo-Wurzel): baut das Frontend production,
  kopiert `dist/nebula-frontend/browser/*` nach
  `backend/src/main/resources/META-INF/resources/` (per `.gitignore`
  ausgeschlossen – generierte Build-Ausgabe, kein Quellcode), paketiert das
  Backend (`mvn clean package`, NICHT `quarkus:dev` – Produktionsmodus statt
  Live-Reload) und startet `target/quarkus-app/quarkus-run.jar`. Ermittelt
  die LAN-IP automatisch über `hostname -I` und gibt die aufzurufende URL
  aus.
- Der bestehende lokale Entwicklungsablauf (separates `ng serve` auf 4200 +
  `quarkus:dev` auf 8080) ist von alldem unberührt: `application.properties`
  bindet zwar jetzt auch dort an `0.0.0.0`, das ändert aber nichts am
  Zugriff über `localhost`, und ohne vorherigen `build-and-run-lan.sh`-Lauf
  existiert unter `META-INF/resources` schlicht keine `index.html`.

### Nutzung (für den Anwender)

1. Rechner und Smartphone im selben WLAN.
2. Im Projektwurzelverzeichnis: `./build-and-run-lan.sh` ausführen und die
   ausgegebene URL abwarten (Format `http://<LAN-IP>:8080/`).
3. Auf dem Smartphone (oder einem anderen Rechner im selben WLAN) genau
   diese URL im Browser öffnen.
4. Beenden mit Strg+C im Terminal, in dem das Skript läuft.

### Testergebnisse (Selbsttest ohne echtes Smartphone, LAN-IP statt localhost)

Ermittelte LAN-IP dieses Rechners: `192.168.178.95` (`hostname -I`).
- `curl http://192.168.178.95:8080/` → `HTTP 200`, liefert das echte
  Angular-`index.html` (`<title>NEBULA</title>` u. a. bestätigt).
- `curl http://192.168.178.95:8080/nachrichten` (simuliert einen direkten
  Aufruf/Reload einer Unterseite) → `HTTP 200`, SPA-Fallback liefert
  ebenfalls `index.html` aus.
- `curl http://192.168.178.95:8080/main-UWTSI35H.js` (aus `index.html`
  extrahiertes echtes Asset) → `HTTP 200` – bestätigt, dass echte Dateien
  weiterhin direkt ausgeliefert werden und nicht vom SPA-Fallback
  überschattet werden.
- WebSocket-Test (Node-Skript, `ws://192.168.178.95:8080/game`, NICHT
  `localhost`): Verbindung erfolgreich, `registerPlayer` über die LAN-IP
  ausgeführt und mit `Ack`/neuem Kommandanten (`ply_b5`, Name
  `LAN-Test`) beantwortet – entspricht exakt dem, was ein Smartphone im
  selben WLAN erleben würde.
- Danach `mvn clean test` (Backend) und der vollständige
  `full-playthrough.mjs`-E2E-Test erneut GRÜN gegen frisch gestartetes
  `quarkus:dev` gelaufen – bestätigt, dass der normale Dev-Workflow durch
  die LAN-Änderungen nicht beeinträchtigt wurde.

**Firewall/Router**: bewusst NICHT Teil dieser Umsetzung geprüft oder
verändert – das kann von hier aus nicht zuverlässig eingesehen werden (hängt
vom konkreten Betriebssystem, der lokalen Firewall-Konfiguration und dem
Router ab). Sollte ein Smartphone die oben genannte URL trotz gleichem WLAN
nicht erreichen, ist typischerweise die lokale Firewall des Rechners
(eingehende Verbindungen auf Port 8080) die Ursache – das muss der Nutzer
selbst prüfen/freigeben.
