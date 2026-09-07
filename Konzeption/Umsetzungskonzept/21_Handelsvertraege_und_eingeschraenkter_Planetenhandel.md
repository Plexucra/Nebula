# 21. Handelsverträge und eingeschränkter Planetenhandel

Design-Vorgabe: kein Systemhandel mehr in normalen Sonnensystemen, planetarer
Handel nur noch zwischen Kommandanten mit gültigem Handelsvertrag, Handel an
neutralen Handelsgilde-Stationen bleibt uneingeschränkt. Zusätzlich neu:
**Handelsvertrag** und **Friedensvertrag** als förmliche, beidseitig
anzunehmende Verträge mit Kündigungsfrist – ergänzend zum bestehenden
einfachen Kriegs-/Friedenszustand aus `DiplomacyCommands`.

## A. Ausgangslage

Vor dieser Änderung kannte `MarketCommands` nur die Unterscheidung
`TradeLocationType.Depot` (Planetarer Handelsposten einer konkreten Kolonie)
und `TradeLocationType.Station` (Systemhandelsposten, ohne Kolonie-Landung
erreichbar – siehe `createSellOrderFromFleet`). Käufer und Verkäufer
mussten in KEINER diplomatischen Beziehung zueinander stehen; ein
Kriegszustand hinderte niemanden am Handel. `StarSystem.isTradeHub` markiert
zwar bereits neutrale Handelsgilde-Stationen (Konzeption/Spieldesign/
05_...md, §5-6), war aber an keiner Stelle der Markt- oder
Blockade-Logik tatsächlich verdrahtet – Blockaden ließen sich formal auch
dort bilden, Handel dort war technisch identisch zu jedem anderen System.

Diplomatie kannte nur `DiplomaticStatus` (`Peace`/`War`, implizit `Peace`
ohne Eintrag) und einseitige `PeaceOffer`s zur Kriegsbeendigung – kein
Konstrukt, das eine Kriegserklärung selbst verhindern oder Handel an eine
Bedingung knüpfen konnte.

## B. Neues Modell: `Treaty` / `TreatyOffer` / `TreatyType`

`TreatyType` (`Peace`, `Trade`) unterscheidet die zwei unabhängig
voneinander abschließbaren Vertragsarten. `Treaty` (analog
`DiplomaticRelation`: genau ein Eintrag je `TreatyType` und ungeordnetem
Kommandanten-Paar, kanonisch sortiert) trägt zusätzlich
`terminationEffectiveAt` (`Long`, `null` = ungekündigt) – eine Kündigung
löscht den Vertrag NICHT sofort, sondern setzt diesen Zeitpunkt; bis dahin
gilt er unverändert weiter (siehe §D). `TreatyOffer` (analog `PeaceOffer`)
ist das einseitige, noch unbeantwortete Angebot für einen neuen Vertrag.

Beide Listen leben in `GameState.treaties`/`treatyOffers`, Logik in der
neuen Klasse `TreatyCommands` (`de.nebula.state`).

## C. Bedeutung der beiden Vertragsarten

- **Friedensvertrag** (`Peace`): blockiert `DiplomacyCommands.declareWar`
  zwischen den Parteien vollständig, solange er (inkl. laufender
  Kündigungsfrist) gültig ist. Er gewährt KEINEN Handelszugang – Sinn ist
  ausschließlich, einen Angriff auszuschließen, ohne gleichzeitig die
  eigenen lokalen Märkte öffnen zu müssen. Ohne Friedensvertrag bleibt
  `declareWar` unverändert einseitig und ohne Vorwarnzeit möglich, wie vor
  dieser Änderung.
- **Handelsvertrag** (`Trade`): Voraussetzung für planetaren Handel
  zwischen den Parteien, siehe §E. Er hat KEINEN Einfluss auf Krieg/Frieden.

Beide können unabhängig voneinander bestehen (parallel, einzeln oder gar
nicht), aber jeweils nur geschlossen werden, solange die Parteien nicht im
Krieg stehen (`offerTreaty`/`respondToTreatyOffer` prüfen
`DiplomacyCommands.diplomaticStatus`). Eine Kriegserklärung ist nur möglich,
wenn kein aktiver Friedensvertrag (auch kein gekündigter, noch laufender)
besteht; sie beendet dabei automatisch und OHNE Kündigungsfrist einen
eventuell noch bestehenden Handelsvertrag samt offener Vertragsangebote
(`TreatyCommands.endAllImmediately`, aufgerufen aus `declareWar`).

## D. Angebot, Annahme und Kündigung

- `offerTreaty(otherPlayerId, type)`: einseitiges Angebot, wirksam erst nach
  `respondToTreatyOffer(offerId, accept: true)` durch den Empfänger.
  Gesperrt im Krieg, bei bereits bestehendem aktivem Vertrag desselben Typs
  oder bereits offenem Angebot. Benachrichtigung (Code 111) an den Empfänger,
  verlinkt auf `/diplomatie`.
- `respondToTreatyOffer(offerId, accept)`: nur der Empfänger darf antworten.
  Ablehnen löscht das Angebot ersatzlos (Code 113 an den Absender). Annehmen
  prüft den Kriegszustand erneut (könnte sich seit dem Angebot geändert
  haben) und legt den Vertrag an bzw. – falls er wegen einer vorherigen
  Kündigung noch (innerhalb der Frist) bestand – hebt die laufende Kündigung
  wieder auf (Code 112 an den Absender).
- `terminateTreaty(otherPlayerId, type)`: setzt
  `terminationEffectiveAt = jetzt + Kündigungsfrist`. Der Vertrag bleibt bis
  dahin UNVERÄNDERT gültig (Friedensvertrag blockiert weiter `declareWar`,
  Handelsvertrag erlaubt weiter Handel) – Warnung (Code 114) an die
  Gegenseite. Ein bereits gekündigter Vertrag kann nicht erneut gekündigt
  werden; ein Zurückziehen der Kündigung ist bewusst NICHT vorgesehen (die
  Kündigungsfrist ist bindend).
- Tick-Hook `TreatyCommands.processExpiredTerminations` (in `GameTick`,
  gleiche Stelle wie `MarketCommands.replenishDormantSellOrders`): entfernt
  Verträge, deren Kündigungsfrist abgelaufen ist, und benachrichtigt beide
  Seiten (Code 115).

**Kündigungsfristen** (`shared/game-constants.json`, wie
`NOTIFICATION_RETENTION_GAME_HOURS`/`MESSAGE_RETENTION_GAME_HOURS` EINE
gemeinsame Quelle für Backend UND Frontend):
`peaceTreatyTerminationNoticeGameHours = 168` (7 Spieltage),
`tradeAgreementTerminationNoticeGameHours = 48` (2 Spieltage) – exponiert als
`GameConstants.PEACE_TREATY_TERMINATION_NOTICE_GAME_HOURS`/
`TRADE_AGREEMENT_TERMINATION_NOTICE_GAME_HOURS` bzw.
`PEACE_TREATY_TERMINATION_NOTICE_GAME_HOURS`/
`TRADE_AGREEMENT_TERMINATION_NOTICE_GAME_HOURS` in
`core/shared-constants.ts`.

## E. Eingeschränkter planetarer Handel (`MarketCommands`)

Neue private Prüfung `requireTradePermission(state, systemId, playerAId,
playerBId)`: erlaubt IMMER, wenn beide Parteien identisch sind oder das
System `isTradeHub` ist; sonst nur, wenn
`TreatyCommands.hasTradeAgreement(state, a, b)` gilt – sonst
`CommandException`.

- `buyFromOrder`: prüft `requireTradePermission` zwischen Käufer und
  `order.sellerId` VOR der Abwicklung – unabhängig vom `TradeLocationType`
  der Order (schließt auch eine eventuell noch vorhandene `Station`-Order
  in einem normalen System mit ein, siehe unten).
- `createSellOrderFromFleet`: eine Order OHNE Kolonie-Landung
  (`fleet.locationColonyId == null`, bisher `Station`-Order am
  Systemhandelsposten) war zum Zeitpunkt dieses Dokuments nur noch möglich,
  wenn das System `isTradeHub` ist – sonst `CommandException` mit dem
  Hinweis, zuerst bei einer Kolonie zu landen. **Seit
  Umsetzungskonzept/22_...md wirft dieser Zweig auch an einer
  Handelsgilde-Station einen Fehler**: der dortige Handel läuft jetzt über
  ein echtes Depot samt zweiseitigem Orderbuch statt direkt aus der
  Flottenfracht. Eine Order MIT Landung bei einer fremden Kolonie prüft
  weiterhin zusätzlich `requireTradePermission` zwischen Flottenbesitzer und
  Kolonie-Besitzer (man "handelt" an deren Handelsposten) – dieser Teil ist
  von Konzept 22 nicht betroffen.
- `createSellOrder` (Verkauf ab eigenem Kolonielager, immer die eigene
  Kolonie) bleibt UNGEPRÜFT – das Einstellen einer Ware ist keine
  abgeschlossene Transaktion mit einer konkreten Gegenpartei; die Prüfung
  greift beim tatsächlichen Kauf.

Neutrale Handelsgilde-Stationen bleiben von alledem unberührt: dort bleibt
`Station`-Handel möglich und JEDER kann mit JEDEM handeln, unabhängig von
Krieg, Frieden oder Handelsvertrag.

## F. Keine Blockaden an Handelsgilde-Stationen (`BlockadeCommands`)

`formBlockade` lehnt jeden Versuch ab, wenn `fleet.systemId` zu einem
`isTradeHub`-System gehört (`CommandException`). Da `engageBattle`
zwingend eine aktive Blockade der verteidigenden Flotte voraussetzt (siehe
Umsetzungskonzept/11_...md, §3.1), sind Handelsgilde-Stationen dadurch
transitiv auch vor Raumgefechten geschützt – konsistent mit Konzeption/
Spieldesign/05_...md, §6 ("garantierter physischer Zugang").

## G. Frontend

- `diplomacy.model.ts`: `TreatyType`, `Treaty`, `TreatyOffer` – 1:1 zu den
  Backend-Modellen.
- `game-api.ts`/`websocket-game-api.service.ts`: `treaties()`,
  `incomingTreatyOffers()`, `outgoingTreatyOffers()`, `hasPeaceTreaty()`,
  `hasTradeAgreement()`, `offerTreaty()`, `respondToTreatyOffer()`,
  `terminateTreaty()` – analog den bestehenden Friedensangebot-Methoden.
- `DiplomacyComponent`: Zeile je anderem Kommandanten zeigt jetzt zusätzlich
  zu Krieg/Frieden je Vertragsart ein Tag (aktiv/gekündigt mit Countdown)
  und den passenden Aktionsbutton (Anbieten/Kündigen); neues Panel
  „Vertragsangebote" (eingehend/ausgehend, Annehmen/Ablehnen) analog dem
  bestehenden „Friedensangebote"-Panel.
- `ColonyDetailComponent`/`colony-detail.component.html` (Tab „Handel"):
  neue Methode `canBuyFrom(sellerId)` (eigene Order, Handelsgilde-Station
  oder gültiger Handelsvertrag) gate't den Kaufen-Button in BEIDEN Order-
  Listen; ohne Berechtigung erscheint stattdessen ein Link „Handelsvertrag
  erforderlich" zur Diplomatie-Seite. Das bisherige Panel „System Handel"
  heißt jetzt „Handel mit anderen Kolonien im System" (Systemhandelsposten-
  Orders sind darin außerhalb einer Handelsgilde-Station nicht mehr
  enthalten).
- `TradeOverviewComponent`: Hinweistexte an die neue Regel angepasst,
  „Sektorale Handelsstation" in „Handelsgilde-Station" umbenannt (deckungs-
  gleich mit dem in §A/§E durchgängig verwendeten Begriff) mit Hinweis auf
  fehlende Blockierbarkeit und Handel ohne Handelsvertrag-Pflicht.

## H. Bewusst nicht umgesetzt

- Kein Zurückziehen einer laufenden Kündigung (siehe §D) – wer kündigt,
  muss die Frist abwarten; ein neues Angebot ist erst nach deren Ablauf
  wieder möglich.
- ~~Keine eigene Handelsoberfläche für Handelsgilde-Stationen~~ – überholt,
  siehe Umsetzungskonzept/22_...md: Handelsgilde-Stationen haben jetzt ein
  echtes Depot je Kommandant und ein zweiseitiges Orderbuch (Kauf- UND
  Verkaufs-Orders) mit eigener `TradeOverviewComponent`-Oberfläche. Der
  hier noch beschriebene "ohne Landung verkaufen"-Zweig von
  `createSellOrderFromFleet` ist dadurch abgelöst und wirft an einer
  Handelsgilde-Station seitdem einen Fehler, der auf den neuen Weg
  verweist.
- Keine automatische Bereinigung inkonsistenter Altbestände (z. B. eine vor
  dieser Änderung erzeugte `Station`-Order in einem normalen System): der
  Prototyp hält seinen Zustand ausschließlich im Arbeitsspeicher ohne
  Persistenz (Umsetzungskonzept/13_...md, Phase 1) – ein Serverneustart
  räumt das ohnehin auf.
