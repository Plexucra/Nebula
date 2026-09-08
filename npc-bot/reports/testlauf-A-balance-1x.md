# Testlauf-Bericht (20 Bots, 76 Realminuten)

## 1. Ereignisse je Mechanik (Anzahl über alle Bots, erstes Auftreten)

| Mechanik | Ereignis | Anzahl | erstes Auftreten | Beispiel |
|---|---|---:|---|---|
| Wirtschaft | STRATEGY | 643 |    0.0 min | NPC-Nord-02: BUILD_UP -> RAID (Blackout=false, Elerium 22,2 Tage, Nahrung 100%, Lebensstandard 100, Flotte 148/148, Angriff |
| Wirtschaft | QUEUE_REORDERED | 97 |   15.5 min | NPC-Sued-10: NPC-Sued-10-Heimat: Elerium vorgezogen (Reichweite 21 h, Charge x18), 15 Aufträge nach hinten |
| Wirtschaft | BUILD_ORDERED | 0 | – | – |
| Wirtschaft | COLONY_LOST | 0 | – | – |
| Wirtschaft | COLONY_GAINED | 0 | – | – |
| Koordination | COORDINATOR | 10 |    0.0 min | NPC-Nord-01: Dieser Bot koordiniert das Lager NORD |
| Koordination | ASSIGN_ROUND | 308 |    0.0 min | NPC-Nord-01: Zuteilung Nr. 1 an 2 Mitglieder (1 Invasoren, 1 Raider, Bedrohung=false, 0 Feindkolonien bekannt) |
| Koordination | ASSIGNMENT | 1325 |    0.0 min | NPC-Nord-01: Zuteilung von NPC-Nord-01: INVADER/p_grundnahrung |
| Koordination | ROLE_RENEGOTIATED | 15 |   32.0 min | NPC-Nord-01: NPC-Nord-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Nord-05 übernimmt, NPC-Nord-09 wec |
| Koordination | MEMBER_LOST | 0 | – | – |
| Koordination | COORDINATOR_TAKEOVER | 0 | – | – |
| Koordination | COORDINATOR_STEPDOWN | 0 | – | – |
| Koordination | TARGET_REASSIGNED | 0 | – | – |
| Koordination | REPORT_RECEIVED | 0 | – | – |
| Diplomatie | WAR_DECLARED | 100 |    0.1 min | NPC-Sued-01: Krieg erklärt an NPC-Nord-01 |
| Diplomatie | TREATY_OFFERED | 360 |    0.0 min | NPC-Nord-02: Peace-Vertrag angeboten an NPC-Nord-01 |
| Diplomatie | TREATY_ACCEPTED | 360 |    0.0 min | NPC-Nord-01: Peace-Vertrag von NPC-Nord-02 angenommen |
| Diplomatie | TREATY_REJECTED | 0 | – | – |
| Diplomatie | PEACE_REJECTED | 0 | – | – |
| Raumkampf | RAID_LAUNCHED | 6 |    0.6 min | NPC-Nord-10: Angriff auf Vey Corva 9 (Stärke 357 vs. 152) |
| Raumkampf | BATTLE_STARTED | 6 |    1.2 min | NPC-Sued-10: Gefecht btl_9ud in Kestrel-Feld 8 gegen Kampfflotte NPC-Nord-09-Heimat (Stärke 342 vs. 123) |
| Raumkampf | BATTLE_ENDED | 6 |    1.5 min | NPC-Sued-10: Gefecht btl_9ud beendet – Flotte hat 9/9 Schiffe |
| Raumkampf | BATTLE_RETREAT | 0 | – | – |
| Raumkampf | FLEET_REBUILT | 0 | – | – |
| Raumkampf | FLEET_REINFORCED | 0 | – | – |
| Raumkampf | WARSHIP_ORDERED | 0 | – | – |
| Bodenkrieg | TRANSPORT_ORDERED | 0 | – | – |
| Bodenkrieg | TRANSPORT_READY | 0 | – | – |
| Bodenkrieg | RECRUIT_ORDERED | 0 | – | – |
| Bodenkrieg | INVASION_LAUNCHED | 0 | – | – |
| Bodenkrieg | LANDED | 0 | – | – |
| Bodenkrieg | LANDING_WIPED | 0 | – | – |
| Bodenkrieg | GROUND_BATTLE_STARTED | 0 | – | – |
| Bodenkrieg | GROUND_BATTLE_REFUSED | 0 | – | – |
| Bodenkrieg | GROUND_RETREAT | 0 | – | – |
| Bodenkrieg | GROUND_BATTLE_LOST | 0 | – | – |
| Bodenkrieg | CONQUERED | 0 | – | – |
| Bodenkrieg | INVASION_ENDED | 0 | – | – |
| Bodenkrieg | INVASION_ABORTED | 0 | – | – |
| Expansion | COLONY_SHIP_ORDERED | 0 | – | – |
| Expansion | COLONY_SHIP_READY | 0 | – | – |
| Expansion | COLONY_SHIP_LOST | 0 | – | – |
| Expansion | COLONIZATION_STARTED | 0 | – | – |
| Expansion | COLONY_FOUNDED | 0 | – | – |
| Expansion | COLONIZATION_FAILED | 0 | – | – |
| Expansion | DELIVERY_REQUESTED | 0 | – | – |
| Expansion | DELIVERY_STARTED | 0 | – | – |
| Expansion | DELIVERY_DONE | 0 | – | – |

## 2. Endzustand je Bot (letzter Takt)

| Bot | Strategie | Rolle | Spezialität | Kolonien | Bev. | Loy. | Elerium h | Blackout | Credits | Flotte | Transp. | Sold./Drohnen | Werft/Akad. | Handelsfahrten | Erlös | Invasion | Expansion |
|---|---|---|---|---:|---:|---:|---:|---|---:|---:|---:|---|---|---:|---:|---|---|
| NPC-Nord-01 | EMERGENCY_POWER | INVADER | p_grundnahrung | 1 | 16 | 100 | 0 | JA | 0 | 347 | 0 | 2/10 | 0/0 | 2 | 1 | BUILDING [Aufbau ruht (EMERGENCY_POWER)] | NONE |
| NPC-Nord-02 | RAID | RAIDER | p_grundmedizin | 1 | 4547 | 100 | 1214 | - | 30 | 148 | 0 | 2/10 | 0/0 | 0 | 0 | NONE [kein blockierendes Feindziel in Praxis Gate 7] | NONE |
| NPC-Nord-03 | SETTLE | SETTLER | p_grundnahrung | 1 | 13496 | 100 | 788 | - | 304 | 233 | 0 | 2/10 | 0/0 | 1 | 0 | NONE | WAITING [keine Werft; Credits 304 < 16500; ] |
| NPC-Nord-04 | BUILD_UP | DEFENDER | p_grundmedizin | 1 | 6176 | 100 | 192 | - | 30 | 0 | 0 | 2/10 | 0/0 | 0 | 0 | NONE | NONE |
| NPC-Nord-05 | EMERGENCY_POWER | INVADER | p_stahl | 1 | 5 | 100 | 0 | JA | 0 | 316 | 0 | 2/10 | 0/0 | 0 | 0 | BUILDING [Aufbau ruht (EMERGENCY_POWER)] | NONE |
| NPC-Nord-06 | RAID | RAIDER | p_grundmedizin | 1 | 5299 | 100 | 958 | - | 106 | 0 | 0 | 2/10 | 0/0 | 0 | 0 | NONE | NONE |
| NPC-Nord-07 | SETTLE | SETTLER | p_grundnahrung | 1 | 2284 | 100 | 937 | - | 37 | 212 | 0 | 2/10 | 0/0 | 1 | 0 | NONE | WAITING [keine Werft; Bevölkerung 2284 < 4050; Credits 37 < 16500; ] |
| NPC-Nord-08 | BUILD_UP | DEFENDER | p_grundmedizin | 1 | 7677 | 100 | 490 | - | 111 | 336 | 0 | 2/10 | 0/0 | 0 | 0 | NONE | NONE |
| NPC-Nord-09 | PREPARE_INVASION | INVADER | p_grundnahrung | 1 | 0 | 100 | 8945 | - | 0 | 0 | 0 | 2/10 | 0/0 | 0 | 0 | BUILDING [kein Mannschaftstransporter (nicht bestellt)] | NONE |
| NPC-Nord-10 | RAID | RAIDER | p_leitermetall | 1 | 4523 | 100 | 660 | - | 30 | 215 | 0 | 2/10 | 0/0 | 0 | 0 | NONE [kein blockierendes Feindziel in Vey Corva 9] | NONE |
| NPC-Sued-01 | EMERGENCY_POWER | INVADER | p_grundnahrung | 1 | 1 | 100 | 0 | JA | 0 | 257 | 0 | 2/10 | 0/0 | 0 | 0 | BUILDING [Aufbau ruht (EMERGENCY_POWER)] | NONE |
| NPC-Sued-02 | RAID | RAIDER | p_grundmedizin | 1 | 3353 | 100 | 958 | - | 53 | 215 | 0 | 2/10 | 0/0 | 0 | 0 | NONE [kein blockierendes Feindziel in Vela Passage 7] | NONE |
| NPC-Sued-03 | SETTLE | SETTLER | p_grundnahrung | 1 | 4976 | 100 | 277 | - | 30 | 238 | 0 | 2/10 | 0/0 | 1 | 0 | NONE | WAITING [keine Werft; Credits 30 < 16500; ] |
| NPC-Sued-04 | BUILD_UP | DEFENDER | p_grundmedizin | 1 | 10592 | 100 | 575 | - | 263 | 0 | 0 | 2/10 | 0/0 | 0 | 0 | NONE | NONE |
| NPC-Sued-05 | EMERGENCY_POWER | INVADER | p_stahl | 1 | 2097 | 100 | 0 | JA | 15 | 0 | 0 | 2/10 | 0/0 | 0 | 0 | BUILDING [Aufbau ruht (EMERGENCY_POWER)] | NONE |
| NPC-Sued-06 | RAID | RAIDER | p_grundmedizin | 1 | 4357 | 100 | 447 | - | 15 | 0 | 0 | 2/10 | 0/0 | 0 | 0 | NONE | NONE |
| NPC-Sued-07 | SETTLE | SETTLER | p_grundnahrung | 1 | 4340 | 100 | 1022 | - | 161 | 313 | 0 | 2/10 | 0/0 | 1 | 0 | NONE | WAITING [keine Werft; Credits 161 < 16500; ] |
| NPC-Sued-08 | BUILD_UP | DEFENDER | p_grundmedizin | 1 | 1856 | 100 | 788 | - | 37 | 153 | 0 | 2/10 | 0/0 | 0 | 0 | NONE | NONE |
| NPC-Sued-09 | EMERGENCY_POWER | INVADER | p_grundnahrung | 1 | 0 | 100 | 0 | JA | 0 | 325 | 0 | 2/10 | 0/0 | 0 | 0 | BUILDING [Aufbau ruht (EMERGENCY_POWER)] | NONE |
| NPC-Sued-10 | RAID | RAIDER | p_leitermetall | 1 | 15294 | 100 | 1576 | - | 120 | 342 | 0 | 2/10 | 0/0 | 1 | 41 | NONE [kein blockierendes Feindziel in Xantha 3] | NONE |

## 3. Verlauf (alle Bots, Mittelwerte je 10-Minuten-Fenster)

| Fenster | Bev. Ø | Loy. Ø | Elerium h Ø | Blackouts | Credits Ø | Flotte Ø | Soldaten Σ | Drohnen Σ | Transporter Σ | Strategien |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
|    0.0 min | 8128 | 100 | 362 | 0 | 58782 | 213 | 40 | 200 | 0 | FAMINE×17, SETTLE×2, PREPARE_INVASION×1 |
|   10.0 min | 6414 | 100 | 382 | 0 | 18261 | 213 | 40 | 200 | 0 | PREPARE_INVASION×5, FAMINE×3, SETTLE×4, RECOVER×3, RAID×4, BUILD_UP×1 |
|   20.0 min | 5259 | 100 | 655 | 0 | 5100 | 213 | 40 | 200 | 0 | PREPARE_INVASION×5, RAID×6, SETTLE×4, RECOVER×3, BUILD_UP×2 |
|   30.0 min | 4793 | 100 | 521 | 1 | 586 | 213 | 40 | 200 | 0 | PREPARE_INVASION×5, RAID×6, SETTLE×4, RECOVER×2, BUILD_UP×2, EMERGENCY_POWER×1 |
|   40.0 min | 3952 | 100 | 715 | 3 | 155 | 213 | 40 | 200 | 0 | EMERGENCY_POWER×4, RAID×6, SETTLE×4, RECOVER×2, PREPARE_INVASION×1, BUILD_UP×2, FAMINE×1 |
|   50.0 min | 3011 | 100 | 560 | 3 | 24 | 213 | 40 | 200 | 0 | PREPARE_INVASION×2, RAID×6, SETTLE×4, RECOVER×2, FAMINE×1, BUILD_UP×2, EMERGENCY_POWER×3 |
|   60.0 min | 3240 | 100 | 1048 | 2 | 26 | 183 | 40 | 200 | 0 | PREPARE_INVASION×4, RAID×6, SETTLE×4, BUILD_UP×4, EMERGENCY_POWER×2 |
|   70.0 min | 4544 | 100 | 991 | 5 | 67 | 183 | 40 | 200 | 0 | EMERGENCY_POWER×5, RAID×6, SETTLE×4, BUILD_UP×4, PREPARE_INVASION×1 |

## 4. Koordination über das Nachrichtensystem

- **NPC-Nord-01** (Koordinator): 20 gesendet, 34 empfangen, 7 Zuteilungen erhalten, 4 Neuverhandlungen: NPC-Nord-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Nord-05 übernimmt, NPC-Nord-09 wechselt auf p_stahl; NPC-Nord-01 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Nord-10 übernimmt, NPC-Nord-01 wechselt auf p_leitermetall; NPC-Nord-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Nord-05 übernimmt, NPC-Nord-09 wechselt auf p_stahl; NPC-Nord-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Nord-05 übernimmt, NPC-Nord-09 wechselt auf p_stahl
- **NPC-Nord-02** : 3 gesendet, 2 empfangen, 6 Zuteilungen erhalten
- **NPC-Nord-03** : 4 gesendet, 3 empfangen, 153 Zuteilungen erhalten
- **NPC-Nord-04** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Nord-05** : 3 gesendet, 2 empfangen, 8 Zuteilungen erhalten
- **NPC-Nord-06** : 3 gesendet, 2 empfangen, 7 Zuteilungen erhalten
- **NPC-Nord-07** : 4 gesendet, 3 empfangen, 153 Zuteilungen erhalten
- **NPC-Nord-08** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Nord-09** : 3 gesendet, 2 empfangen, 10 Zuteilungen erhalten
- **NPC-Nord-10** : 3 gesendet, 2 empfangen, 10 Zuteilungen erhalten
- **NPC-Sued-01** (Koordinator): 20 gesendet, 29 empfangen, 8 Zuteilungen erhalten, 11 Neuverhandlungen: NPC-Sued-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-05 übernimmt, NPC-Sued-09 wechselt auf p_stahl; NPC-Sued-01 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-10 übernimmt, NPC-Sued-01 wechselt auf p_leitermetall; NPC-Sued-01 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-05 übernimmt, NPC-Sued-01 wechselt auf p_stahl; NPC-Sued-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-01 übernimmt, NPC-Sued-09 wechselt auf p_stahl; NPC-Sued-01 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-05 übernimmt, NPC-Sued-01 wechselt auf p_stahl; NPC-Sued-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-01 übernimmt, NPC-Sued-09 wechselt auf p_stahl; NPC-Sued-01 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-10 übernimmt, NPC-Sued-01 wechselt auf p_leitermetall; NPC-Sued-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-05 übernimmt, NPC-Sued-09 wechselt auf p_stahl; NPC-Sued-01 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-09 übernimmt, NPC-Sued-01 wechselt auf p_stahl; NPC-Sued-09 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-10 übernimmt, NPC-Sued-09 wechselt auf p_leitermetall; NPC-Sued-05 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-02 übernimmt, NPC-Sued-05 wechselt auf p_grundmedizin
- **NPC-Sued-02** : 3 gesendet, 2 empfangen, 6 Zuteilungen erhalten
- **NPC-Sued-03** : 4 gesendet, 3 empfangen, 153 Zuteilungen erhalten
- **NPC-Sued-04** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Sued-05** : 3 gesendet, 2 empfangen, 11 Zuteilungen erhalten
- **NPC-Sued-06** : 3 gesendet, 2 empfangen, 7 Zuteilungen erhalten
- **NPC-Sued-07** : 4 gesendet, 3 empfangen, 153 Zuteilungen erhalten
- **NPC-Sued-08** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Sued-09** : 3 gesendet, 2 empfangen, 11 Zuteilungen erhalten
- **NPC-Sued-10** : 3 gesendet, 2 empfangen, 10 Zuteilungen erhalten

## 5. Aktuelle Blocker

- Aufbau ruht (EMERGENCY_POWER) — 5× (NPC-Nord-01, NPC-Nord-05, NPC-Sued-01, NPC-Sued-05, NPC-Sued-09)
- kein blockierendes Feindziel in Praxis Gate 7 — 1× (NPC-Nord-02)
- keine Werft; Credits 304 < 16500;  — 1× (NPC-Nord-03)
- keine Werft; Bevölkerung 2284 < 4050; Credits 37 < 16500;  — 1× (NPC-Nord-07)
- kein Mannschaftstransporter (nicht bestellt) — 1× (NPC-Nord-09)
- kein blockierendes Feindziel in Vey Corva 9 — 1× (NPC-Nord-10)
- kein blockierendes Feindziel in Vela Passage 7 — 1× (NPC-Sued-02)
- keine Werft; Credits 30 < 16500;  — 1× (NPC-Sued-03)
- keine Werft; Credits 161 < 16500;  — 1× (NPC-Sued-07)
- kein blockierendes Feindziel in Xantha 3 — 1× (NPC-Sued-10)

## 6. Beobachter (unabhängige Serversicht)

Letzte Momentaufnahme   75.4 min: 20 Kolonien (gegründet 0, erobert 0), Bevölkerung 85892, Blackouts 3, Verträge 180, Kriege 100, NPC-Nachrichten in Postfächern 191.

-    2.3 min: Raumgefecht btl_abv von NPC-Nord-10: AttackerVictory nach 3 Ticks
-    2.3 min: Raumgefecht btl_b7v von NPC-Sued-02: AttackerVictory nach 4 Ticks
-    2.3 min: Raumgefecht btl_9ud von NPC-Sued-10: AttackerVictory nach 3 Ticks
-   30.4 min: NPC-Nord-09: NPC-Nord-09-Heimat im Blackout
-   45.4 min: NPC-Sued-03: NPC-Sued-03-Heimat im Blackout
-   45.4 min: NPC-Sued-09: NPC-Sued-09-Heimat im Blackout
-   49.4 min: NPC-Nord-01: NPC-Nord-01-Heimat im Blackout
-   54.4 min: NPC-Sued-01: NPC-Sued-01-Heimat im Blackout
-   61.4 min: NPC-Nord-10: NPC-Nord-10-Heimat im Blackout
-   63.4 min: Raumgefecht btl_5ini von NPC-Nord-10: AttackerVictory nach 5 Ticks
-   63.4 min: Raumgefecht btl_5isn von NPC-Sued-10: AttackerVictory nach 3 Ticks
-   65.4 min: Raumgefecht btl_5lq0 von NPC-Nord-10: AttackerVictory nach 4 Ticks
-   73.4 min: NPC-Sued-05: NPC-Sued-05-Heimat im Blackout
