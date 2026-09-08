# TODO

## Offen

- [ ] **Balancing: Elerium-Startreserve passt nicht mehr zur Startbebauung.**
  `WorldSeed.STARTER_ELERIUM_QUANTITY` (3 je Warteschlangen-Umlauf) und die
  25er-Startreserve sind laut ihrem eigenen Kommentar für „bis Infrastruktur 3"
  (0,0198 Elerium/Spielstunde) bemessen. Der Start liegt inzwischen aber bei
  **Infrastruktur 6** = 0,047/h, also dem 2,4-fachen Verbrauch. Gemessen an einer
  frischen Kolonie: der Elerium-Dauerauftrag kam in 10 Realminuten genau EINMAL an
  die Reihe (die Kolonie hat nur EINE sequentielle Warteschlange, lange Bauaufträge
  verdrängen ihn), der Bestand fiel monoton 25,0 → 16,9. Ein längerer Ausbau führt
  damit in den Blackout. Verschärfend: `EconomyTick.consumePowerUpkeep` glättet mit
  `0,8·alt + 0,2·neu` gegen die Blackout-Schwelle 0,999 – ein einziger ungedeckter
  Tick kostet rund 24 Ticks Blackout, und im Blackout (Produktion ×0,1,
  Kernwerte ×0,5) kann die Kolonie das Elerium kaum noch selbst nachliefern.
  Beobachtet: Bevölkerung 120 → 4, Lebensstandard 0, Warteschlange leer.
  Offene Balancing-Entscheidung (Reserve/Menge anheben, oder die
  Energieversorgung aus der sequentiellen Warteschlange herausnehmen) – bewusst
  nicht selbständig geändert.

- [ ] **Baustoffe verbrauchen einander als Vorprodukt – Reihenfolge ist eine Falle.**
  `p_leiterbuendel` enthält `p_leitermetall` (1:1). Wer die vom Server gemeldete
  Fehlliste („Fehlende Baustoffe: p_stahl …, p_leitermetall …, p_leiterbuendel …")
  in genau dieser Reihenfolge abarbeitet, produziert erst 13 Leitermetall und
  lässt sie dann vom Leiterbündel-Auftrag wieder aufzehren (`autoProduceMissing`
  bedient sich aus dem Lager) – die geforderten Mengen liegen nie GLEICHZEITIG im
  Lager, der Ausbau bleibt dauerhaft abgelehnt. Direkt gemessen: Leitermetall
  13 → 0 in dem Moment, in dem der Leiterbündel-Auftrag startet.
  Betroffen ist auch `Bot.queueMissingMaterials` (npc-bot), das die Fehlliste in
  Server-Reihenfolge abarbeitet; die Bot-Logs zeigen passend dazu wiederholtes
  Nachbestellen derselben Menge (13, dann wieder 13; 15, dann wieder 15).
  Der e2e-Test umgeht das inzwischen, indem er nach Tier ABSTEIGEND einreiht –
  Bot und UI-Hinweis sollten nachziehen.

## Erledigt

- [x] ~~Kolonisieren war in der Oberfläche auf das Heimatsystem beschränkt~~ –
  behoben. Der „Kolonisieren"-Knopf sitzt jetzt zusätzlich an jedem
  unbesiedelten Planeten der **Systemansicht** (`system-view.component`), also
  in JEDEM erreichten System. Er erscheint genau dann, wenn eine eigene,
  stationierte Flotte mit Kolonisationsschiff im Orbit dieses Planeten liegt –
  dieselbe Bedingung wie `ColonyCommands.fleetWithColonyShipAt`; welche
  Produkte Kolonisationsschiffe sind, kommt aus `ShipTypeDef.class`
  (`'ColonyShip'` fehlte im TS-Typ und ist ergänzt), nicht aus einer zweiten
  Produktliste im Client. Liegt kein Schiff im Orbit, steht dort der Grund;
  läuft bereits eine Gründung, deren Restzeit. Die Kolonienliste behält ihre
  Heimatsystem-Sicht, nennt das System aber jetzt beim Namen (statt hart
  „Aurelia-System") und verweist für alles Weitere auf die Galaxiekarte.
  Der Weg dorthin (Werft → Lager → Flotte → Betanken → Gateway-Sprung → Orbit →
  Gründung) ist über die echten Befehle abgesichert: `ColonizationJourneyTest`.

- [x] ~~Nach der Landung blieb eine leere Geisterflotte stehen~~ – behoben.
  `colonizePlanet` verbucht das Schiff jetzt über
  `FleetCommands.consumeShips`: leergelaufene Schiffsgruppen verschwinden, und
  mit dem letzten Schiff verschwindet die Flotte selbst (samt ihrer Blockade,
  wie beim Ortswechsel). Eine gemischte Flotte verliert dagegen nur das
  Kolonisationsschiff und behält Schiffe wie Tankinhalt – beides ist in
  `ColonizationJourneyTest` und `ColonyCommandsUsabilityTest` festgehalten.

- [x] ~~Mehrere Kommandanten auf demselben Planeten~~ – **so gewollt**
  (Nutzerentscheidung). `colonizePlanet` prüft `alreadyOwned`/`alreadyRunning`
  bewusst nur gegen die eigenen Kolonien; zwei Kommandanten dürfen denselben
  Planeten besiedeln. Nicht ändern.

- [x] ~~Frisch gegründete Kolonie startet ohne Vorräte~~ – **kein Fehler**
  (Nutzerentscheidung): die Rohstoffe bringt der Kommandant selbst mit. Zu
  beachten ist dabei nur, dass das Kolonisationsschiff selbst keinen Frachtraum
  hat (`cargoMassKg = 0`, Umsetzungskonzept/24_...md, §F) – die Erstversorgung
  (vor allem Elerium für die Infrastruktur 2, sonst Blackout ab dem ersten
  Tick) muss also ein Frachter mitfliegen oder unmittelbar nachliefern.

- [x] ~~e2e (`backend/e2e/full-playthrough.mjs`) schlägt beim ersten Kampf-Tick fehl~~ –
  behoben. Die ursprüngliche Vermutung („Determinismus-Mismatch in
  `BattleCommands.java`") war falsch: die Datei war in Ordnung. Das Testskript
  rechnete mit `product.baseWorkforceRequired`, einem beim Workforce-Redesign in
  `workHoursPerUnit` umbenannten Feld. Ergebnis war `NaN`, das in
  `computeSideDamage`/`applyDamage` still als „keine Verluste" (`{}`) durchfiel.
  `shipMilitaryValue` prüft den Wert jetzt hart, damit ein künftiger Feldumbau
  laut scheitert statt still falsche Erwartungswerte zu liefern.
  Zusätzlich waren weitere Erwartungen des Skripts veraltet (Grundmedizin-
  Startorder, Minimalstart Industrie 1/Infrastruktur 2, `workforceFactor`), und
  der WS-Client hatte weder Befehls-Timeout noch Abbruchbehandlung – ein
  verlorener Ack ließ den Lauf unbegrenzt still stehen.
