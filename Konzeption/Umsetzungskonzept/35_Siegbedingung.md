# 35 — Siegbedingung: wann ein Krieg entschieden ist

**Vorgabe (Nutzer, 9.9.2026):** „Gewonnen hat eine Partei, wenn sie alle
feindlichen Kolonien vernichtet hat."

Bis dahin kannte das Spiel keinen Ausgang: Kolonien wechselten per Eroberung den
Besitzer (`ColonyConquest`), ein Kommandant ohne Kolonie blieb angemeldet, und
niemand hat je gewonnen. Weder Server noch Oberfläche hatten dafür einen
Begriff.

## A. Was eine Partei ist

| Fall | Partei |
|---|---|
| NPC-Bot mit `campId` (`NORD`, `SUED`) | das **Lager** – alle Mitglieder gewinnen gemeinsam |
| jeder andere Kommandant | er **selbst** |

Technisch: `VictoryCommands.partyOf(player)` → `"camp:<campId>"` bzw.
`"player:<id>"`. Damit ist die Regel für einen LAN-Abend mit mehreren Menschen
und zwei NPC-Lagern ohne Sonderfall anwendbar.

## B. Die Regel

In jedem Tick, **nach** allen Eroberungen des Ticks (`GameTick`, direkt vor dem
Aufräumen), prüft `VictoryCommands.evaluate`:

1. Welche Parteien besitzen aktuell mindestens eine Kolonie?
2. Sind es **genau eine** – und gab es im Lauf dieser Galaxie schon einmal
   **mindestens zwei** Parteien mit Kolonien (`GameState.partiesEverWithColonies`)?
3. Dann ist der Krieg entschieden.

Der zweite Punkt ist keine Feinheit, sondern nötig: Ohne ihn gewänne der erste
Kommandant einer frischen Galaxie im ersten Tick gegen niemanden.

Der Ausgang wird EINMAL festgeschrieben (`GameState.victory`, Typ
`GameVictory`) und danach nicht mehr verändert – auch nicht, wenn ein
Unterlegener später neu gründet. `resetGame` löscht ihn mit dem Rest der Welt.

## C. Was der Sieg NICHT tut

Das Spiel hält nicht an. Der Weltzustand lebt weiter, solange der Server läuft
(Umsetzungskonzept/13), und ein Kommandant ohne Kolonie bleibt im Spiel: Er
behält seine Flotten und kann mit einem Kolonisationsschiff neu anfangen
(Umsetzungskonzept/34 §J 9). Der Sieg ist eine **Feststellung**, kein
Endbildschirm.

## D. Wie er sichtbar wird

* **Benachrichtigung** an JEDEN Kommandanten, Code 121
  (`Notifications.CODE_VICTORY`), Linkziel „Statistiken öffnen".
* **Band über der gesamten Oberfläche** (`app-shell`): golden für die eigene
  Partei, neutral sonst.
* **Tafel auf der Statistikseite** mit Siegerpartei, Mitgliedern, Zahl ihrer
  Kolonien und den unterlegenen Kommandanten.
* **WS-Befehl** `victory` (liefert `null`, solange der Krieg offen ist) – auch
  vom Beobachter (`npc-bot/observer.mjs`) und im Laufbericht
  (`npc-bot/report.mjs`) ausgewertet.

## E. Was die NPCs davon haben

Das Lagerziel ist damit messbar, und die Rollenverteilung richtet sich danach:
Bei zehn Mitgliedern stellt ein Lager 6 Invasoren, 2 Raider, 1 Siedler und
1 Verteidiger (`Coordination.defaultRole`); die Zielaufklärung sucht
gegnerische Kolonien in den Heimatsystemen **aller** Kommandanten, nicht nur in
denen der Gegner – sonst blieben genau die eroberten und neu gegründeten
Kolonien unsichtbar, die zum Abschluss fehlen.

## F. Absicherung

`backend/src/test/java/de/nebula/state/VictoryTest.java`:

| Fall | Erwartung |
|---|---|
| zwei Parteien mit Kolonien | kein Sieg |
| einzelner Kommandant in frischer Galaxie | kein Sieg |
| letzte fremde Kolonie erobert | Sieg, beide Seiten werden benachrichtigt |
| zwei NPCs eines Lagers, dritter verliert alles | Lagersieg mit beiden Mitgliedern |
| Unterlegener gründet neu | der Sieg bleibt festgeschrieben |
