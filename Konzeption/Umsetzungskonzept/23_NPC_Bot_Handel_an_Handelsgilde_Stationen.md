# 23. NPC-Bot-Handel an Handelsgilde-Stationen

Nachreichung der in Umsetzungskonzept/22_...md, §H bewusst zurückgestellten
Aufgabe ("Echte NPC-Bot-Handelsteilnahme ... das mit den NPC machen wir
später"): die 20 laufenden `npc-bot`-Prozesse (Umsetzungskonzept/14_...md,
Teil 2) handeln jetzt eigenständig an Handelsgilde-Stationen, koordinieren
ihre Warenspezialisierung weiterhin über das Nachrichtensystem (Teil 1
desselben Dokuments) und bauen ihre Kolonie – wie schon zuvor – selbstständig
weiter aus, sobald der Handel dafür Baustoffe liefert.

## A. Handelsrollen statt reiner Baustoff-Rotation

`Bot#coordinateSpecialization` verteilte bislang ausschließlich zehn
Tier-2-Baustoffe (`Catalog.SPECIALTY_PRODUCTS`) über den Bot-Index. Das war
für die reine Militär-/Ausbau-Heuristik ausreichend, deckt aber nicht die
Nutzervorgabe für den Handel: **die meisten Bots sollen sich zunächst auf
Nahrungs- und Medizin-Grundbedarfe spezialisieren, nur sehr wenige auf
Baustoffe**. `Catalog#specialtyForIndex` löst das deterministisch:

- Jeder `MATERIALS_SPECIALIST_EVERY`-te Bot (Standard 5, also 2 von 10 je
  Lager) bekommt eines der zehn Tier-2-Baustoffe aus `SPECIALTY_PRODUCTS`
  zugeteilt (rotierend über die Baustoff-Spezialisten hinweg).
- Alle anderen wechseln sich zwischen `p_grundnahrung` (Nahrung) und
  `p_grundmedizin` (Medizin) ab – beide Tier-1-`ConsumerGood`, siehe
  `products.json`.

Die Zuteilung läuft unverändert über den bestehenden Koordinator-Mechanismus
(`sendMessage`/`inbox`, Betreff "Spezialisierung") – nur die Berechnungsformel
wurde ausgetauscht, das Protokoll blieb identisch.

## B. Warum Nahrung/Medizin lokal weiterhin produziert werden muss

`WorldSeed.starterSellOrders` legt für JEDE Kolonie feste, auto-relistende
Verkaufsorders für alle drei Grundkonsumgüter (`GameConstants.
CONSUMER_GOODS_ORDER` = Nahrung, Medizin, Unterhaltungselektronik) an –
`EconomyTick.runConsumption` kauft AUSSCHLIESSLICH aus diesen lokalen,
kolonie-gebundenen `state.sellOrders` (nicht aus dem Hub-Orderbuch!) und
speist damit Lebensstandard und Loyalität. Eine Kolonie, die z. B. nur Medizin
produziert, hätte ohne Gegenmaßnahme dauerhaft eine "schlafende" (leere)
Nahrungs-Verkaufsorder – Versorgung 0, Lebensstandard einbricht. Der
Handelskreislauf in §D schließt genau diese Lücke: importierte Ware landet im
eigenen Kolonielager und speist dort automatisch dieselbe Auto-Relist-Order
(`MarketCommands.replenishDormantSellOrders`, jeden Tick), ganz ohne
zusätzlichen Bot-Code für die lokale Order selbst.

## C. Frachter

Jede Kolonie startet ohnehin mit einer "Handelsflotte" (`WorldSeed.
freighterFleet`, ein `p_freighter`) – zusätzlich zur Kampfflotte. Der Bot
sucht sie beim Bootstrap heraus (`hasFreighter`, Kriterium: einziger
Schiffstyp mit `cargoMassKg > 0`, siehe Umsetzungskonzept/22_...md, §B) und
verwendet sie für den gesamten Handelskreislauf. Keine eigene
Frachterbeschaffung über die Werft – ein Frachter pro Bot ist für die
angestrebten Losgrößen ausreichend.

## D. Handelskreislauf (`Bot#maintainTrade`, Zustandsautomat)

Analog zum bestehenden Angriffs-Zustandsautomaten (`AttackState`) ein neuer,
unabhängiger `TradeState` (`IDLE` / `TRAVELING_TO_HUB` / `TRAVELING_HOME`),
weil Flottenreisen über mehrere echte Zeit-Ticks laufen:

1. **IDLE, zu Hause gelandet**: sobald der Lagerbestand der eigenen
   Spezialware `TRADE_RESERVE_QTY` (Puffer für die eigene Bevölkerungs-Order,
   siehe §B) plus `TRADE_MIN_EXPORT_BATCH` übersteigt, wird die
   nächstgelegene Handelsgilde-Station per BFS über das öffentlich bekannte
   Gateway-Netz ermittelt (`findNearestTradeHub`, einmalig gecacht – die
   Topologie ist statisch), die exportierbare Menge geladen (`loadCargo`) und
   die Reise gestartet (`moveFleet`, löst intern selbstständig in
   Gateway-Sprünge auf, siehe `FleetCommands.moveFleet`).
2. **TRAVELING_TO_HUB**: sobald die Flotte an der Station steht
   (`status == Stationed`, `systemId == hubSystemId`), wird
   - die Fracht ins unbegrenzte Stationsdepot entladen
     (`unloadCargoToHubDepot`) und SOFORT als Verkaufs-Order eingestellt,
     mit Preis = bestem aktuell im Orderbuch stehenden Kaufgebot
     (`bestPrice(..., "Buy")`) – das garantiert sofortige (Teil-)Ausführung
     gegen die Handelsgilde oder einen anderen Kommandanten statt einer
     Order, die tage-/spielzyklenlang ungematcht im Buch liegt;
   - mit dem – durch den Verkauf gerade gewachsenen – Wallet-Guthaben werden
     die Grundbedarfe/Baustoffe nachgekauft, die die eigene Kolonie NICHT
     selbst herstellt (`buyImportsAtHub`, siehe §E), ebenfalls zum besten
     verfügbaren Briefkurs (`bestPrice(..., "Sell")`) für sofortige
     Ausführung;
   - die Rückreise gestartet.
3. **TRAVELING_HOME**: nach Ankunft wird die GESAMTE verbliebene Fracht
   (Ausgangspunkt: leere Ladung nach dem Verkauf, plus alles frisch
   Eingekaufte) ins Kolonielager entladen (`unloadCargo`) – von dort
   übernehmen die bestehenden Mechanismen (Auto-Relist-Orders für die
   Bevölkerung, `queueMissingMaterials` für den Gebäudeausbau) den Rest.

## E. Wer kauft was zurück (`buyImportsAtHub`)

- Jeder, der NICHT Nahrungs-Spezialist ist, kauft `p_grundnahrung` nach,
  sobald der Lagerbestand unter `TRADE_IMPORT_LOW_WATERMARK` fällt (und
  symmetrisch für `p_grundmedizin`) – siehe §B.
- Jeder, der NICHT Baustoff-Spezialist ist, kauft zusätzlich
  `TRADE_IMPORT_MATERIAL` (`p_stahl`) nach. `p_stahl` ist laut
  `buildings.json` das einzige Material, das JEDER der drei
  Ausbaupfade aus `Catalog.BUILD_PRIORITY` (Industriekomplex, Werft,
  Wohnkomplex) UND die Infrastruktur selbst bereits ab Stufe 1 benötigt –
  ein einzelnes importiertes Gut deckt damit den größten gemeinsamen Nenner
  aller Ausbauten ab. Seltenere Baustoffe (z. B. `p_glaswerkstoff` für
  höhere Wohnkomplex-Stufen) bleiben unverändert Sache der bestehenden
  Selbstproduktion über `queueMissingMaterials`, sobald `queueBuilding`
  konkret danach verlangt.
- Vor jedem Kauf werden eigene, von einem früheren Besuch übrig gebliebene
  Kauf-Orders für dasselbe Produkt storniert (`cancelOwnOrders`) – sonst
  würden bei dünner Marktliquidität über viele Besuche hinweg immer mehr
  Credits in nie vollständig ausgeführten Kauf-Orders gebunden bleiben
  (`HubOrder.escrowedCredits`, siehe Umsetzungskonzept/22_...md, §C).

**Ergebnis für den Baustoff-Ausbau**: der Effizienzgewinn aus der
Nutzervorgabe ("damit sie Kapazität haben, auch andere Dinge herzustellen")
entsteht dadurch, dass Nahrungs-/Medizin-Kolonien Stahl importieren statt
selbst zu produzieren – ihre Industriekomplex-Kapazität bleibt für die
eigene Spezialware frei. Der bereits bestehende Ausbau-Mechanismus in
`Bot#maintainEconomy` (`Catalog.BUILD_PRIORITY`, Infrastruktur-Fallback bei
fehlendem Bebauungsplatz) bleibt unverändert – er bekommt durch den Handel
lediglich häufiger schon ausreichend Baustoffe vor, statt jedes Mal selbst
eine neue Fertigungskette anzustoßen.

## F. Bewusste Vereinfachungen

- **Kein direkter Bot-zu-Bot-Handel**: die Nutzervorgabe verlangt
  Koordination über Nachrichten (weiterhin nur für die Spezialisierungs-
  Zuteilung, siehe §A) UND Handel an Handelsgilde-Stationen – beides ist
  damit erfüllt, ohne ein zusätzliches, in der Vorgabe nicht verlangtes
  Peer-to-Peer-Verhandlungsprotokoll zu erfinden. Die eigentliche
  Gegenpartei ist immer das anonyme Orderbuch (Market-Maker oder ein
  beliebiger anderer Kommandant).
- **Ein Frachter, ein Handelsziel je Fahrt**: kein Multi-Hub-Preisvergleich,
  keine eigene Frachterflotten-Beschaffung – die nächstgelegene Station
  reicht für den angestrebten Kreislauf.
- **Heimatsystem == Handelsgilde-Station** (seltene, in Umsetzungskonzept/
  22_...md, §G bereits dokumentierte Kollision der Galaxiegenerierung):
  eine solche Kolonie handelt schlicht nicht am eigenen Handelsposten
  (`tryStartExport` bricht ab, `moveFleet` würde ohnehin "Ziel ==
  Ausgangssystem" ablehnen).
