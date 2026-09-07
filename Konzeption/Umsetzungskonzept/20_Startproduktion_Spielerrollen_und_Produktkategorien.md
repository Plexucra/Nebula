# 20. Startproduktion, Arbeitskräfte-Aggregat, Spielerrollen und Produktkategorien

Vier voneinander unabhängige Änderungen, in einem Zug umgesetzt: die Startproduktion
bootstrappt jetzt nur noch Grundnahrung statt zweier Konsumgüter, der aufklappbare
Auftrags-Detailbereich zeigt den aggregierten Arbeitskräftebedarf eines ganzen Auftrags
(nicht mehr nur je Kettenschritt), die Registrierung unterscheidet drei Spielerrollen, und
zwei Produktkategorien wurden neu zugeschnitten.

## A. Startproduktion nur noch Grundnahrung, auf zwei Aufträge verteilt

### Ausgangslage

`WorldSeed.STARTER_CONSUMER_GOODS` enthielt bisher Grundnahrung UND Grundmedizin: beide
liefen als Dauerauftrag (`requeueOnComplete`) und hatten je eine wiederkehrende
Verkaufsorder am Heimatdepot. Die sequentielle Produktionswarteschlange der Kolonie
(höchstens ein Eintrag `running`, siehe Umsetzungskonzept/10_...md) war damit dauerhaft
mit zwei Dauteraufträgen belegt – für eigene Spezialisierungsaufbauten bei Grundnahrung
blieb praktisch nie Freiraum.

### Änderung

`STARTER_CONSUMER_GOODS` enthält jetzt nur noch `p_grundnahrung`. Grundmedizin baut der
Kommandant komplett selbst auf, sobald er will. `starterProductionQueue` und
`starterSellOrders` iterieren beide bereits über diese Liste, die Reduktion auf ein
Gut fällt also ohne Strukturänderung mit an.

Zusätzlich wird die Startmenge (`STARTER_CONSUMER_GOODS_QUANTITY = 5`) nicht mehr als EIN
Auftrag eingereiht, sondern als `STARTER_CONSUMER_GOODS_ORDER_SPLIT = 2` Aufträge zu je der
halben Menge. Der Gesamtdurchsatz der Warteschlange ändert sich dadurch nicht (beide
Aufträge laufen wegen der Sequentialität ohnehin abwechselnd, nicht parallel), aber die
erste Charge liegt bereits nach der halben Zeit im Lager statt erst nach dem kompletten
Auftrag.

### Bekannte Folgewirkungen (bewusst, nicht separat behoben)

- **Gleichgewichtspreis**: `EconomyTick.runConsumption` speist den Konsum jetzt nur noch
  aus Grundnahrung. `P* = (0,008·Bev + 3,0 + 0,2·Schiffe) / (0,00008·Bev)` ergibt bei der
  eingeschwungenen Bevölkerung (200 Einwohner, 13 Schiffe) **450 statt vorher 300
  Credits/Stück** (mit Grundmedizin-Erlös). `STARTER_SELL_ORDER_PRICE` wurde entsprechend
  angepasst.
- **Lebensstandard**: `runConsumption` gewichtet Grundnahrung doppelt so hoch wie die
  übrigen Konsumgüter. Ohne Grundmedizin-Versorgung startet der Lebensstandard einer
  frischen Kolonie rechnerisch bei rund 50 % statt vorher 75 % – das ist der gewünschte
  Anreiz, die Grundmedizin-Kette selbst aufzubauen.
- **Elerium-Puffer**: ein Warteschlangen-Umlauf ist ohne den Grundmedizin-Block kürzer;
  der bestehende Puffer von `STARTER_ELERIUM_QUANTITY = 3` je Umlauf reicht damit mit noch
  mehr Reserve als vorher.

## B. Aggregierter Arbeitskräftebedarf je Auftrag

### Ausgangslage

Umsetzungskonzept/19_...md machte die Arbeitsstunden-Bremse je Kettenschritt sichtbar
(`ChainPlanStep.workersBoundPerHour`/`.workforceLimited`). Bei einem mehrstufigen Produkt
wie Grundnahrung (mehrere Vorprodukte, jedes mit eigenem Arbeitsstunden-Bedarf) fehlte
aber die Aussage auf Auftragsebene: "dieser Auftrag bindet insgesamt X Arbeitskräfte je
Stunde" – nur die Gesamtdauer war sichtbar.

### Änderung

`ChainPlan` bekommt zwei neue Felder: `totalWorkHours` (Summe aus
`ProductType.workHoursPerUnit × step.quantityToProduce` über alle Kettenschritte) und
`workersBoundPerHour` (= `totalWorkHours / totalHours`). `ChainPlanner.planChain`
berechnet beide in derselben Schleife, in der auch `step.hours` entsteht.

`colony-detail.component.html` zeigt den Wert direkt neben der Gesamtdauer, sowohl in der
Auftragsvorschau als auch beim laufenden/wartenden Auftrag. Beim laufenden Auftrag ergänzt
ein Hinweistext, dass der Fertigstellungstermin seit Auftragsstart feststeht und spätere
Änderungen an Bevölkerung, Spezialisierung oder Anlagenausbau ihn nicht mehr verschieben –
das war zwar schon immer so (der Auftrag speichert `endsAt` beim Start), stand aber
nirgends in der UI.

## C. Spielerrollen bei der Registrierung

### Ausgangslage

`registerPlayer` kannte nur eine Spielerart. Die NPC-Bot-Armee registrierte sich genauso
wie ein menschlicher Kommandant und wurde ausschließlich über ihr Namensschema
(`NPC-Nord-01`) als NPC erkannt – rein clientseitig im Bot selbst. Das Heimatsystem eines
neuen Kommandanten wird bei `createAdditionalPlayerSeed` bisher IMMER maximal isoliert
platziert (`pickIsolatedPosition`, größter Mindestabstand zu allen bestehenden Systemen) –
für NPCs desselben Lagers unpraktisch, die sich dadurch über die ganze Galaxie verteilen.

### Änderung

Neues Enum `PlayerRole` (`Normal`, `Npc`, `Test`) plus `Player.campId` (nullable). Die
Registrierungsoberfläche (`new-game.component`) bekommt eine Auswahl "Normaler Spieler /
NPC / Test Spieler"; bei NPC zusätzlich ein Freitextfeld für das Lager (z. B. "NORD").
`GameSocket.handleRegisterPlayer` liest beides aus dem Payload (Default `Normal`/`null`)
und reicht es an `WorldSeed.createWorldSeed`/`createAdditionalPlayerSeed` durch.

**Platzierung**: `createAdditionalPlayerSeed` bekommt zusätzlich die Liste der bereits
registrierten Spieler. Ist die Rolle `Npc` und existieren bereits Spieler mit demselben
`campId`, wird das neue Heimatsystem beim Schwerpunkt von deren Heimatsystemen platziert
(`pickCampPosition`: zufällige Kandidaten, die einen Mindestabstand von
`CAMP_MIN_SEPARATION = 0,05` zu jedem bestehenden System einhalten, wähle den mit dem
kleinsten Abstand zum Lager-Schwerpunkt). Ohne Lagerkollegen (der erste NPC eines Lagers)
bleibt es bei `pickIsolatedPosition` – dadurch siedeln sich verschiedene Lager im Schnitt
weit auseinander an, aber Kollegen desselben Lagers rücken danach zusammen.

`npc-bot/Bot.java` sendet jetzt `role: "Npc"` und `campId: <NORD|SUED>` bei der
Registrierung; die Namenskonvention `NPC-Nord-01` bleibt für die botinterne Team- und
Gegnererkennung unverändert bestehen.

`Test` bekommt aktuell **dieselben** Startbedingungen wie `Normal` – die Rolle wird nur
gespeichert und ist der Aufhänger für spätere gezielte Sonderausstattung (z. B. eine große
Kampfflotte, um das Kampfsystem ohne Aufbauphase zu testen), bewusst noch nicht
umgesetzt, um keine willkürlichen Zahlen festzulegen.

## D. Produktkategorien: Schiffsmodule und Energiemodule

### Ausgangslage

`ProductCategory.BuildingMaterial` sammelte 123 sehr unterschiedliche Produkte: sowohl
Baustoffe für Gebäude (Stahl, Legierungen, Baugruppen, siehe `buildings.json` →
`materials`) als auch die 36 Baugruppen, aus denen Schiffe gebaut werden
(`p_<schiffstyp>_<modul>`, je Schiffstyp Rumpf/Antrieb/Energie/Elektronik/Versorgung/
Waffen/Ladung/Hangar/Truppen – Träger und Zerstörer/Kreuzer/Korvette/Frachter/Transport).
`ProductCategory.Fuel` hieß im UI "Treibstoff", meinte aber inhaltlich die
Elerium-Antriebs-/Energieversorgungskette (Umsetzungskonzept/01_...md, §3).

### Änderung

Neue Kategorie `ShipModule` ("Schiffsmodule"): alle 36 `p_<schiffstyp>_<modul>`-Produkte
wandern dorthin, unabhängig davon, ob sie vorher `BuildingMaterial` (31 Stück) oder
`Fuel` (5 "…_energie"-Module) waren – verifiziert, dass diese 36 Produkte AUSSCHLIESSLICH
als Eingang von `Ship`-Rezepten verbaut werden, nirgends sonst. Die verbleibenden 92
Baumaterialien bleiben `BuildingMaterial`, die übrigen 6 Kraftstoff-Produkte werden zu
`EnergyModule` ("Energiemodule", vormals `Fuel`/"Treibstoff") umbenannt.

Geändert: `ProductCategory` (Backend-Enum), `ProductCategory`-Union (Frontend-Typ),
`PRODUCT_CATEGORY_LABELS`, die Icon-Zuordnung in `production-overview.component.ts` sowie
`shared/catalog/products.json` (einzige Datenquelle). Keine Stelle im Code hatte einen
`switch` über das Enum – alle Verwendungen sind Gleichheitsprüfungen bzw.
`ProductCatalog.byCategory`, die Umbenennung/Erweiterung ist damit compilergeprüft
vollständig.

## Verifikation

- `mvn -o compile` und `mvn -o test` (Backend, `WorldSeedSmokeTest` angepasst) sowie
  `npx tsc --noEmit -p tsconfig.app.json` (Frontend) fehlerfrei.
- `python3 -c "import json; json.load(open('shared/catalog/products.json'))"` – Katalog
  bleibt gültiges JSON, Kategorie-Summen ergeben wieder 187 Produkte
  (92 BuildingMaterial + 36 ShipModule + 26 ConsumerGood + 17 RawResource +
  6 EnergyModule + 6 Ship + 4 GroundUnit).
- Manuell: Registrierung mit allen drei Rollen; zwei NPC-Bots je Lager registrieren und
  auf der Galaxiekarte prüfen, dass ihre Heimatsysteme beieinanderliegen; Kolonie-Detail →
  Produktion → Grundnahrung-Auftrag zeigt Gesamtdauer, benötigte Arbeitskräfte und den
  Hinweis zum fixen Fertigstellungstermin; Produktauswahl-Dialog zeigt "Schiffsmodule" und
  "Energiemodule" als eigene Kategorien.
