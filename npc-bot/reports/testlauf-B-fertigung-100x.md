# Testlauf-Bericht (20 Bots, 76 Realminuten)

## 1. Ereignisse je Mechanik (Anzahl über alle Bots, erstes Auftreten)

| Mechanik | Ereignis | Anzahl | erstes Auftreten | Beispiel |
|---|---|---:|---|---|
| Wirtschaft | STRATEGY | 261 |    0.0 min | NPC-Nord-02: BUILD_UP -> RAID (Blackout=false, Elerium 22,2 Tage, Nahrung 100%, Lebensstandard 100, Flotte 117/117, Angriff |
| Wirtschaft | QUEUE_REORDERED | 3 |   38.1 min | NPC-Nord-06: NPC-Nord-06-Heimat: Elerium vorgezogen (Reichweite 0 h, Charge x25), 4 Aufträge nach hinten |
| Wirtschaft | BUILD_ORDERED | 162 |    0.3 min | NPC-Nord-02: NPC-Nord-02-Heimat: Infrastruktur (kein Bebauungsplatz für b_shipyard) |
| Wirtschaft | COLONY_LOST | 2 |   64.4 min | NPC-Nord-02: Kolonie NPC-Nord-02-Heimat ist nicht mehr in eigener Hand |
| Wirtschaft | COLONY_GAINED | 2 |   64.5 min | NPC-Sued-01: Neue eigene Kolonie NPC-Nord-02-Heimat |
| Koordination | COORDINATOR | 10 |    0.0 min | NPC-Nord-01: Dieser Bot koordiniert das Lager NORD |
| Koordination | ASSIGN_ROUND | 308 |    0.0 min | NPC-Nord-01: Zuteilung Nr. 1 an 2 Mitglieder (1 Invasoren, 1 Raider, Bedrohung=false, 0 Feindkolonien bekannt) |
| Koordination | ASSIGNMENT | 1344 |    0.0 min | NPC-Nord-01: Zuteilung von NPC-Nord-01: INVADER/p_grundnahrung |
| Koordination | ROLE_RENEGOTIATED | 3 |   65.6 min | NPC-Nord-01: NPC-Nord-02 kann p_grundmedizin nicht mehr liefern (Blackout/Verlust) – NPC-Nord-05 übernimmt, NPC-Nord-02 wec |
| Koordination | MEMBER_LOST | 0 | – | – |
| Koordination | COORDINATOR_TAKEOVER | 0 | – | – |
| Koordination | COORDINATOR_STEPDOWN | 0 | – | – |
| Koordination | TARGET_REASSIGNED | 3 |   64.6 min | NPC-Sued-01: NPC-Sued-01: Ziel col_3wf -> NPC-Nord-10-Heimat |
| Koordination | REPORT_RECEIVED | 1 |   69.2 min | NPC-Nord-01: NPC-Nord-09 meldet: {systemId=sys_4wo, colonyId=col_4x1, kind=conquered} |
| Diplomatie | WAR_DECLARED | 100 |    0.1 min | NPC-Sued-01: Krieg erklärt an NPC-Nord-01 |
| Diplomatie | TREATY_OFFERED | 360 |    0.0 min | NPC-Nord-02: Peace-Vertrag angeboten an NPC-Nord-01 |
| Diplomatie | TREATY_ACCEPTED | 360 |    0.0 min | NPC-Nord-01: Peace-Vertrag von NPC-Nord-02 angenommen |
| Diplomatie | TREATY_REJECTED | 0 | – | – |
| Diplomatie | PEACE_REJECTED | 0 | – | – |
| Raumkampf | RAID_LAUNCHED | 23 |    0.5 min | NPC-Nord-06: Angriff auf Vey Corva 9 (Stärke 354 vs. 132) |
| Raumkampf | BATTLE_STARTED | 22 |    0.8 min | NPC-Sued-02: Gefecht btl_9oo in Kestrel-Feld 8 gegen Kampfflotte NPC-Nord-02-Heimat (Stärke 348 vs. 117) |
| Raumkampf | BATTLE_ENDED | 21 |    1.2 min | NPC-Sued-02: Gefecht btl_9oo beendet – Flotte hat 15/15 Schiffe |
| Raumkampf | BATTLE_RETREAT | 0 | – | – |
| Raumkampf | FLEET_REBUILT | 19 |   20.4 min | NPC-Nord-07: 1x p_corvette in die Kampfflotte gestellt |
| Raumkampf | FLEET_REINFORCED | 0 | – | – |
| Raumkampf | WARSHIP_ORDERED | 62 |   18.4 min | NPC-Nord-04: NPC-Nord-04-Heimat: Werftauftrag p_corvette (Kettenvorschau 0 h) |
| Bodenkrieg | TRANSPORT_ORDERED | 6 |   37.3 min | NPC-Nord-05: NPC-Nord-05-Heimat: Werftauftrag p_trooptransport (Kettenvorschau 1 h) |
| Bodenkrieg | TRANSPORT_READY | 6 |   37.4 min | NPC-Nord-05: Mannschaftstransporter in Dienst gestellt (Flotte flt_5pfp) |
| Bodenkrieg | RECRUIT_ORDERED | 565 |    1.2 min | NPC-Nord-04: NPC-Nord-04-Heimat: 6x p_soldier im Ausbildungszentrum |
| Bodenkrieg | INVASION_LAUNCHED | 5 |   48.4 min | NPC-Nord-09: Landungsoperation gegen NPC-Sued-07-Heimat: 1000 Soldaten, 15 p_drone_medium, Eskorte=true, 14 Sprünge |
| Bodenkrieg | LANDED | 2 |   62.7 min | NPC-Sued-01: Gelandet auf NPC-Nord-02-Heimat: 1000 Soldaten, 15 Drohnen (eingeschifft: 1000 / 15 – Differenz = Landungsabwe |
| Bodenkrieg | LANDING_WIPED | 0 | – | – |
| Bodenkrieg | GROUND_BATTLE_STARTED | 2 |   62.8 min | NPC-Sued-01: Bodengefecht gbt_93b3 gegen NPC-Nord-02-Heimat eröffnet (Phase Combat) |
| Bodenkrieg | GROUND_BATTLE_REFUSED | 0 | – | – |
| Bodenkrieg | GROUND_RETREAT | 0 | – | – |
| Bodenkrieg | GROUND_BATTLE_LOST | 0 | – | – |
| Bodenkrieg | CONQUERED | 1 |   69.1 min | NPC-Nord-09: Kolonie NPC-Sued-07-Heimat erobert! |
| Bodenkrieg | INVASION_ENDED | 0 | – | – |
| Bodenkrieg | INVASION_ABORTED | 2 |   64.5 min | NPC-Sued-01: Ziel NPC-Nord-02-Heimat ist keine gegnerische Kolonie mehr |
| Expansion | COLONY_SHIP_ORDERED | 0 | – | – |
| Expansion | COLONY_SHIP_READY | 0 | – | – |
| Expansion | COLONY_SHIP_LOST | 0 | – | – |
| Expansion | COLONIZATION_STARTED | 0 | – | – |
| Expansion | COLONY_FOUNDED | 0 | – | – |
| Expansion | COLONIZATION_FAILED | 0 | – | – |
| Expansion | DELIVERY_REQUESTED | 1 |   69.1 min | NPC-Nord-09: Versorgungsfahrt geplant für Kolonie col_4x1: {p_grundmedizin=40.0, p_elerium_stabil=25.0, p_grundnahrung=150. |
| Expansion | DELIVERY_STARTED | 0 | – | – |
| Expansion | DELIVERY_DONE | 0 | – | – |

## 2. Endzustand je Bot (letzter Takt)

| Bot | Strategie | Rolle | Spezialität | Kolonien | Bev. | Loy. | Elerium h | Blackout | Credits | Flotte | Transp. | Sold./Drohnen | Werft/Akad. | Handelsfahrten | Erlös | Invasion | Expansion |
|---|---|---|---|---:|---:|---:|---:|---|---:|---:|---:|---|---|---:|---:|---|---|
| NPC-Nord-01 | INVADE | INVADER | p_grundnahrung | 1 | 20000 | 100 | 1732 | - | 288 | 347 | 1 | 302/10 | 3/1 | 0 | 0 | TRAVELING | NONE |
| NPC-Nord-02 | BUILD_UP | RAIDER | p_grundmedizin | 0 | 0 | 0 | ∞ | - | 42466 | 0 | 0 | 0/0 | 0/0 | 0 | 0 | NONE | NONE |
| NPC-Nord-03 | SETTLE | SETTLER | p_grundnahrung | 1 | 40000 | 100 | 128439 | - | 312 | 215 | 0 | 2/10 | 2/0 | 3 | 1 | NONE | WAITING [Credits 312 < 16500; ] |
| NPC-Nord-04 | BUILD_UP | DEFENDER | p_grundmedizin | 1 | 40000 | 100 | 3832 | - | 468 | 122 | 0 | 8/40 | 2/2 | 0 | 0 | NONE | NONE |
| NPC-Nord-05 | PREPARE_INVASION | INVADER | p_stahl | 1 | 20000 | 100 | 2309 | - | 396 | 215 | 1 | 1142/25 | 2/1 | 0 | 0 | BUILDING [Truppen: 1142/2002 Soldaten, 15/15 p_drone_medium] | NONE |
| NPC-Nord-06 | RAID | RAIDER | p_grundmedizin | 1 | 20000 | 100 | 1203 | - | 3214 | 344 | 0 | 2/10 | 3/0 | 1 | 0 | NONE [kein blockierendes Feindziel in Aurelia 10] | NONE |
| NPC-Nord-07 | SETTLE | SETTLER | p_grundnahrung | 1 | 20000 | 100 | 10527 | - | 843 | 0 | 0 | 2/10 | 3/1 | 1 | 0 | NONE | WAITING [Credits 843 < 16500; ] |
| NPC-Nord-08 | FAMINE | DEFENDER | p_grundmedizin | 1 | 20000 | 100 | 472 | - | 104 | 1 | 0 | 20/40 | 3/1 | 1 | 0 | NONE | NONE |
| NPC-Nord-09 | FAMINE | INVADER | p_grundnahrung | 2 | 20000 | 100 | 382 | - | 105 | 355 | 1 | 362/10 | 3/1 | 0 | 0 | BUILDING [Truppen: 362/2002 Soldaten, 0/15 p_drone_medium] | NONE |
| NPC-Nord-10 | RAID | RAIDER | p_leitermetall | 1 | 20000 | 100 | 773 | - | 201 | 115 | 0 | 2/10 | 2/0 | 0 | 0 | NONE | NONE |
| NPC-Sued-01 | FAMINE | INVADER | p_grundnahrung | 2 | 39690 | 100 | 9704 | - | 1322 | 312 | 1 | 652/10 | 3/1 | 0 | 0 | BUILDING [Truppen: 652/1002 Soldaten, 0/15 p_drone_medium] | NONE |
| NPC-Sued-02 | RAID | RAIDER | p_grundmedizin | 1 | 20000 | 100 | 885 | - | 168 | 348 | 0 | 2/10 | 2/0 | 1 | 0 | NONE [kein blockierendes Feindziel in Nashira 9] | NONE |
| NPC-Sued-03 | SETTLE | SETTLER | p_grundnahrung | 1 | 40000 | 100 | 99534 | - | 180 | 0 | 0 | 2/10 | 2/0 | 2 | 0 | NONE | WAITING [Credits 180 < 16500; ] |
| NPC-Sued-04 | FAMINE | DEFENDER | p_grundmedizin | 1 | 40000 | 100 | 3435 | - | 156 | 233 | 0 | 8/40 | 2/2 | 0 | 0 | NONE | NONE |
| NPC-Sued-05 | PREPARE_INVASION | INVADER | p_stahl | 1 | 20000 | 100 | 1257 | - | 12928 | 327 | 1 | 2/10 | 3/0 | 1 | 1 | BUILDING [Truppen: 2/1002 Soldaten, 0/15 p_drone_medium] | NONE |
| NPC-Sued-06 | RAID | RAIDER | p_grundmedizin | 1 | 40000 | 100 | 540 | - | 1063 | 112 | 0 | 2/10 | 3/0 | 1 | 0 | NONE [kein blockierendes Feindziel in Nashira 8] | NONE |
| NPC-Sued-07 | BUILD_UP | DEFENDER | p_grundnahrung | 0 | 0 | 0 | ∞ | - | 33389 | 1 | 0 | 0/0 | 0/0 | 0 | 0 | NONE | NONE |
| NPC-Sued-08 | FAMINE | DEFENDER | p_grundmedizin | 1 | 40000 | 100 | 3119 | - | 108 | 357 | 0 | 8/40 | 2/2 | 1 | 0 | NONE | NONE |
| NPC-Sued-09 | PREPARE_INVASION | INVADER | p_grundnahrung | 1 | 20000 | 100 | 2328 | - | 3102 | 1 | 1 | 2/10 | 3/0 | 1 | 0 | BUILDING [Truppen: 2/1002 Soldaten, 0/15 p_drone_medium] | NONE |
| NPC-Sued-10 | RAID | RAIDER | p_leitermetall | 1 | 20000 | 100 | 1809 | - | 253 | 216 | 0 | 2/10 | 2/0 | 0 | 0 | NONE [kein blockierendes Feindziel in Aurelia-System] | NONE |

## 3. Verlauf (alle Bots, Mittelwerte je 10-Minuten-Fenster)

| Fenster | Bev. Ø | Loy. Ø | Elerium h Ø | Blackouts | Credits Ø | Flotte Ø | Soldaten Σ | Drohnen Σ | Transporter Σ | Strategien |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
|    0.0 min | 18979 | 100 | 5954 | 0 | 121023 | 187 | 68 | 290 | 0 | PREPARE_INVASION×5, RECOVER×5, SETTLE×2, BUILD_UP×3, RAID×5 |
|   10.0 min | 24641 | 100 | 5587 | 0 | 37875 | 187 | 1208 | 290 | 0 | PREPARE_INVASION×5, RECOVER×5, SETTLE×2, BUILD_UP×3, RAID×5 |
|   20.0 min | 25796 | 100 | 7081 | 0 | 2137 | 187 | 2278 | 290 | 0 | PREPARE_INVASION×5, RECOVER×5, SETTLE×2, BUILD_UP×3, RAID×5 |
|   30.0 min | 25994 | 100 | 9323 | 0 | 332 | 187 | 3196 | 290 | 3 | PREPARE_INVASION×5, RECOVER×5, SETTLE×2, BUILD_UP×3, RAID×5 |
|   40.0 min | 26000 | 100 | 10938 | 0 | 343 | 187 | 1746 | 290 | 6 | PREPARE_INVASION×3, RECOVER×4, SETTLE×2, BUILD_UP×3, RAID×5, INVADE×2, UNDER_ATTACK×1 |
|   50.0 min | 26000 | 100 | 12107 | 0 | 346 | 181 | 2076 | 290 | 6 | PREPARE_INVASION×3, RECOVER×5, SETTLE×1, BUILD_UP×3, RAID×5, INVADE×2, UNDER_ATTACK×1 |
|   60.0 min | 24856 | 90 | 13792 | 0 | 2711 | 181 | 3172 | 315 | 6 | PREPARE_INVASION×5, BUILD_UP×6, SETTLE×3, RAID×5, FAMINE×1 |
|   70.0 min | 24985 | 90 | 13614 | 0 | 5053 | 181 | 2522 | 315 | 6 | INVADE×1, BUILD_UP×3, SETTLE×3, PREPARE_INVASION×3, RAID×5, FAMINE×5 |

## 4. Koordination über das Nachrichtensystem

- **NPC-Nord-01** (Koordinator): 20 gesendet, 33 empfangen, 6 Zuteilungen erhalten, 2 Neuverhandlungen: NPC-Nord-02 kann p_grundmedizin nicht mehr liefern (Blackout/Verlust) – NPC-Nord-05 übernimmt, NPC-Nord-02 wechselt auf p_stahl; NPC-Nord-02 kann p_grundmedizin nicht mehr liefern (Blackout/Verlust) – NPC-Nord-05 übernimmt, NPC-Nord-02 wechselt auf p_stahl
- **NPC-Nord-02** : 3 gesendet, 2 empfangen, 33 Zuteilungen erhalten
- **NPC-Nord-03** : 4 gesendet, 3 empfangen, 153 Zuteilungen erhalten
- **NPC-Nord-04** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Nord-05** : 3 gesendet, 2 empfangen, 7 Zuteilungen erhalten
- **NPC-Nord-06** : 3 gesendet, 2 empfangen, 12 Zuteilungen erhalten
- **NPC-Nord-07** : 4 gesendet, 3 empfangen, 153 Zuteilungen erhalten
- **NPC-Nord-08** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Nord-09** : 3 gesendet, 2 empfangen, 6 Zuteilungen erhalten
- **NPC-Nord-10** : 3 gesendet, 2 empfangen, 13 Zuteilungen erhalten
- **NPC-Sued-01** (Koordinator): 19 gesendet, 28 empfangen, 6 Zuteilungen erhalten, 1 Neuverhandlungen: NPC-Sued-07 kann p_grundnahrung nicht mehr liefern (Blackout/Verlust) – NPC-Sued-05 übernimmt, NPC-Sued-07 wechselt auf p_stahl
- **NPC-Sued-02** : 3 gesendet, 2 empfangen, 6 Zuteilungen erhalten
- **NPC-Sued-03** : 4 gesendet, 3 empfangen, 153 Zuteilungen erhalten
- **NPC-Sued-04** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Sued-05** : 3 gesendet, 2 empfangen, 6 Zuteilungen erhalten
- **NPC-Sued-06** : 3 gesendet, 2 empfangen, 5 Zuteilungen erhalten
- **NPC-Sued-07** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Sued-08** : 3 gesendet, 2 empfangen, 153 Zuteilungen erhalten
- **NPC-Sued-09** : 3 gesendet, 2 empfangen, 10 Zuteilungen erhalten
- **NPC-Sued-10** : 3 gesendet, 2 empfangen, 10 Zuteilungen erhalten

## 5. Aktuelle Blocker

- Truppen: 2/1002 Soldaten, 0/15 p_drone_medium — 2× (NPC-Sued-05, NPC-Sued-09)
- Credits 312 < 16500;  — 1× (NPC-Nord-03)
- Truppen: 1142/2002 Soldaten, 15/15 p_drone_medium — 1× (NPC-Nord-05)
- kein blockierendes Feindziel in Aurelia 10 — 1× (NPC-Nord-06)
- Credits 843 < 16500;  — 1× (NPC-Nord-07)
- Truppen: 362/2002 Soldaten, 0/15 p_drone_medium — 1× (NPC-Nord-09)
- Truppen: 652/1002 Soldaten, 0/15 p_drone_medium — 1× (NPC-Sued-01)
- kein blockierendes Feindziel in Nashira 9 — 1× (NPC-Sued-02)
- Credits 180 < 16500;  — 1× (NPC-Sued-03)
- kein blockierendes Feindziel in Nashira 8 — 1× (NPC-Sued-06)
- kein blockierendes Feindziel in Aurelia-System — 1× (NPC-Sued-10)

## 6. Beobachter (unabhängige Serversicht)

Letzte Momentaufnahme   75.4 min: 20 Kolonien (gegründet 0, erobert 2), Bevölkerung 500763, Blackouts 0, Verträge 180, Kriege 100, NPC-Nachrichten in Postfächern 189.

-    1.3 min: Raumgefecht btl_9oo von NPC-Sued-02: AttackerVictory nach 3 Ticks
-    2.3 min: Raumgefecht btl_cr7 von NPC-Nord-06: AttackerVictory nach 3 Ticks
-    2.3 min: Raumgefecht btl_by0 von NPC-Nord-10: AttackerVictory nach 6 Ticks
-    2.3 min: Raumgefecht btl_c4t von NPC-Sued-06: AttackerVictory nach 6 Ticks
-    2.3 min: Raumgefecht btl_axu von NPC-Sued-10: AttackerVictory nach 4 Ticks
-   21.4 min: Raumgefecht btl_3hzu von NPC-Sued-02: AttackerVictory nach 1 Ticks
-   21.4 min: Raumgefecht btl_3hqo von NPC-Sued-06: AttackerVictory nach 1 Ticks
-   23.4 min: Raumgefecht btl_3r9r von NPC-Sued-10: AttackerVictory nach 1 Ticks
-   29.4 min: Raumgefecht btl_4kft von NPC-Nord-10: AttackerVictory nach 1 Ticks
-   33.4 min: Raumgefecht btl_54ns von NPC-Sued-10: AttackerVictory nach 1 Ticks
-   37.4 min: Raumgefecht btl_5ovm von NPC-Sued-02: AttackerVictory nach 1 Ticks
-   43.4 min: Raumgefecht btl_6db8 von NPC-Nord-10: AttackerVictory nach 1 Ticks
-   44.4 min: Raumgefecht btl_6j14 von NPC-Sued-02: AttackerVictory nach 1 Ticks
-   46.4 min: Raumgefecht btl_6va5 von NPC-Sued-10: AttackerVictory nach 1 Ticks
-   51.4 min: Raumgefecht btl_7if7 von NPC-Nord-06: AttackerVictory nach 3 Ticks
-   53.4 min: Raumgefecht btl_7t4z von NPC-Sued-06: AttackerVictory nach 1 Ticks
-   58.4 min: Raumgefecht btl_8gl4 von NPC-Sued-06: AttackerVictory nach 1 Ticks
-   59.4 min: Raumgefecht btl_8j58 von NPC-Nord-09: AttackerVictory nach 1 Ticks
-   59.4 min: Raumgefecht btl_8kv6 von NPC-Sued-01: AttackerVictory nach 1 Ticks
-   61.4 min: Raumgefecht btl_8rrv von NPC-Nord-06: AttackerVictory nach 1 Ticks
-   62.4 min: Raumgefecht btl_8xs4 von NPC-Sued-10: AttackerVictory nach 1 Ticks
-   63.4 min: Raumgefecht btl_926z von NPC-Sued-02: AttackerVictory nach 1 Ticks
-   65.4 min: Bodengefecht gbt_93b3 von NPC-Sued-01: AttackerVictory nach 19 Ticks
-   65.4 min: NPC-Sued-01 besitzt NPC-Nord-02-Heimat (Kestrel-Feld 8) – erobert
-   69.4 min: Bodengefecht gbt_9pjq von NPC-Nord-09: AttackerVictory nach 19 Ticks
-   69.4 min: NPC-Nord-09 besitzt NPC-Sued-07-Heimat (Amaris 7) – erobert
