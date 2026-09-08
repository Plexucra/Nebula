# 29. Ein Regler für das Spieltempo

## Ausgangslage

Die Zeitkompression stand als **`realMsPerGameHour: 2500`** in
`shared/game-constants.json` und wurde von Backend (`Clock.java` über
`SharedConstants`) wie Frontend (`core/shared-constants.ts`) gelesen. Für
längere Testläufe sollte das Tempo erhöht werden – die Prüfung, ob dabei
wirklich *alles* mitzieht, förderte fünf Größen zutage, die sich dem Regler
entzogen hätten.

## A. Der Regler

Statt den Ausgangswert zu verbiegen, gibt es jetzt einen **eigenen
Multiplikator**:

```json
"baseRealMsPerGameHour": 2500,
"gameSpeedMultiplier": 4
```

```text
wirksame Zeitkompression = baseRealMsPerGameHour / gameSpeedMultiplier
```

Beide Anwendungen rechnen identisch (`Clock.REAL_MS_PER_GAME_HOUR`,
`shared-constants.ts`). Der Ausgangswert bleibt als Bezugsgröße stehen und
sagt weiterhin, was „Tempo 1" bedeutet; gedreht wird ausschließlich am
Multiplikator. Bei 4 gilt: 1 Spielstunde = 625 ms, 1 Spieltag = 15 s
Realzeit, ein Kampf-Tick (8 Spielstunden) = 5 s.

**Der Realzeit-Tick bleibt bei 1 s.** Schneller heißt nicht mehr Ticks je
Sekunde, sondern mehr Spielstunden je Tick (`GameConstants.TICK_GAME_HOURS`,
bei Tempo 4: 1,6 statt 0,4). Daraus folgt die Regel, an der sich alles
Weitere entscheidet:

> Was je Tick verbucht wird, muss eine **Rate je Spielstunde** sein,
> multipliziert mit `TICK_GAME_HOURS` – nie ein fester Betrag je Tick.

## B. Was dem Regler entkommen wäre

### 1. Bevölkerungs-Konsum (der schwerwiegendste)

`CONSUMER_NEED_PER_CAPITA` war laut eigenem Kommentar „je Kopf **und Tick**"
definiert und wurde in `EconomyTick.runConsumption` ohne `TICK_GAME_HOURS`
verwendet. Bei vierfachem Tempo hätte die Bevölkerung je Spieltag nur noch ein
Viertel gegessen, während Produktion, Löhne, Energieverbrauch und Wachstum
mitskalieren – die gesamte Versorgungsbilanz wäre gekippt.

Jetzt `CONSUMER_NEED_PER_CAPITA_PER_HOUR`, Werte um den Faktor 2,5 (= 1/0,4)
angehoben, in `runConsumption` mit `TICK_GAME_HOURS` multipliziert. Bei Tempo 1
identisches Verhalten. `ColonyCommands.supplyInventory` braucht seine
Rückrechnung `× ticksPerGameHour` dadurch nicht mehr.

### 2. Tickweise Glättung (EMA)

Drei Stellen glätteten mit festen Gewichten je Tick: Energiedeckung
(`0,8·alt + 0,2·neu`), Konsumbudget (`0,9/0,1`), Lebensstandard (`0,7/0,3`).
Feste Gewichte schreiben eine Reaktionszeit in **Realsekunden** fest – bei
vierfachem Tempo hätte eine Kolonie viermal so viele *Spiel*stunden gebraucht,
um sich von einem Versorgungsloch zu erholen. Beim Blackout ist das kein
Detail: laut `TODO.md` kostet ein einziger ungedeckter Tick rund 24 Ticks
Blackout, und im Blackout läuft die Produktion auf 10 %.

Jetzt über eine **Zeitkonstante in Spielstunden**:

```text
alpha = 1 - e^(-TICK_GAME_HOURS / tau)
```

`Formulas.smoothingAlpha(tau)` mit den drei Tau-Werten in `Formulas`. Sie sind
so gewählt, dass bei Tempo 1 exakt 0,2 / 0,1 / 0,3 herauskommen – kein
Verhaltensbruch, nur eine tempo-unabhängige Formulierung derselben Kurve.

### 3. Zwei Realzeit-Fristen

| vorher | jetzt |
| --- | --- |
| `SPECIALIZATION_DECAY_GRACE_MS = 16000` | `..._GAME_HOURS = 6,4` |
| `STATS_SNAPSHOT_INTERVAL_MS = 10000` | `..._GAME_HOURS = 4` |

Beides sind Aussagen über die Spielwelt („nach dieser Zeit ohne Produktion
verlernt eine Kolonie ihr Können", „so fein wird der Bevölkerungsverlauf
aufgelöst") und gehören an die Spieluhr. Die Zahlen entsprechen bei Tempo 1
den bisherigen Werten.

### 4. Der NPC-Bot

`Bot.TICK_INTERVAL_MS = 8000` und `ATTACK_READY_DELAY_MS = 90_000` waren feste
Realzeiten. Bei vierfachem Tempo hätte der Bot in einer viermal schneller
wachsenden Welt weiter im alten Takt entschieden und weiter dieselbe Realzeit
auf seine Aufbauphase gewartet.

Jetzt in Spielstunden (8 bzw. 36), umgerechnet über `GameSpeed.java` – ein
kleiner Leser für dieselbe `shared/game-constants.json`, die Maven jetzt auch
in den Bot-Klassenpfad kopiert. Das ist eine **Datei-, keine
Modulabhängigkeit**; die bewusste Unabhängigkeit des Bots vom Backend
(`npc-bot/pom.xml`) bleibt unberührt.

### 5. Der e2e-Test

`backend/e2e/full-playthrough.mjs` hatte `REAL_MS_PER_GAME_HOUR = 2500`
dupliziert – alle Wartezeiten des Skripts wären beim ersten Dreh am Regler aus
dem Ruder gelaufen. Liest die Datei jetzt selbst.

## C. Was bewusst NICHT am Regler hängt

- **Der Tick selbst** (`@Scheduled(every = "1s")`), siehe oben.
- **UI-Polling** (Frontend, 1 s) und die **Bildschirmuhr** (500 ms). Das sind
  Bildwiederholraten, keine Spielzeiten.

## D. Absicherung

`GameSpeedTest` hält die Eigenschaften fest, und zwar so, dass sie bei **jedem**
eingestellten Multiplikator gelten – nicht nur beim gerade konfigurierten:

- wirksame Kompression = Ausgangswert / Regler;
- `TICK_MS = TICK_GAME_HOURS × REAL_MS_PER_GAME_HOUR`;
- ein Verbrauch je Spielstunde bleibt über einen Umweg durch die Ticks
  derselbe;
- die je Spielstunde verbleibende Restgewichtung der Glättung hängt nur von
  `tau` ab, nicht vom Tempo;
- die drei Tau-Werte reproduzieren bei Tempo 1 die historischen Alpha-Werte.

Wer künftig wieder eine Größe in Realzeit festschreibt („alle 10 Sekunden",
„so viel je Tick"), bricht dort.
