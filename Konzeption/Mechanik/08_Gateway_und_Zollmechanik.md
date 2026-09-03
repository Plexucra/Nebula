# Mechanik: Gateway-Gewicht, Zölle und Reichweiten

*Grundprinzipien: `Konzeption/04_Gateways_und_politische_Ordnung.md`,
`Konzeption/07_Planeten_und_Bevoelkerung.md`. Quelle:
`02/04_Gateways_und_interstellare_Reisen.md`,
`02/07_Planeten_Bevoelkerung_und_politische_Integration.md` §6–7.*

## 1. Gateway-Reichweite

```text
Jedes Gateway verbindet mindestens 3, höchstens 6 Nachbarsysteme direkt.
Der gesamte Gateway-Graph der Galaxie ist zusammenhängend (kein isoliertes System).
```

Konkretisiert die frühere Größenordnung "ca. 5-10" auf einen festen
Bereich, der sich prozedural erzeugen und prüfen lässt (siehe
Umsetzungskonzept/06_..., Galaxiegenerator). 3-6 statt 5-10, damit die
Anzahl der Nachbarn spürbar variiert (manche Systeme sind
Durchgangsknoten mit vielen Verbindungen, manche liegen abgelegen mit
nur drei) und die Grafenerzeugung auch bei kleineren Galaxien robust
bleibt.

### Sektorale Handelsstationen: Erreichbarkeit

```text
Sektorale Handelsstationen werden so in den Gateway-Graphen platziert,
dass jedes System im Schnitt ca. 2, maximal 3 Gateway-Sprünge von der
nächstgelegenen Handelsstation entfernt liegt.
```

Beantwortet die in `Konzeption/05_Handelsgilde_und_Warenprinzip.md`
("Offene konzeptionelle Fragen") offene Frage nach der maximalen
Entfernung zur nächsten Station. Platzierung erfolgt algorithmisch
(Greedy-k-Center auf dem Gateway-Graphen: iterativ das jeweils am
weitesten von allen bisherigen Stationen entfernte System als neue
Station wählen, bis Durchschnitt und Maximum eingehalten sind).

## 2. Gateway-Gewicht (politische Kontrolle)

```text
Gateway-Gewicht (Spieler, System) =
    Σ über alle Planeten des Spielers im System von
        (Bevölkerung des Planeten × Loyalität des Planeten)
```

Beispiel aus dem Ursprungsdokument:

```text
Welt A: 10 Mrd. Einwohner × 20 % Loyalität ≈ Welt B: 2 Mrd. Einwohner × 100 % Loyalität
```

- Für die Gateway-KI-Kontrolle eines Systems zählt der **höchste** Wert
  dieser Formel unter allen Spielern im System.
- Konsequenz: Ein Eroberer erhält nicht sofort das volle politische
  Gewicht einer großen fremden Bevölkerung – Integration (=
  Loyalitätsaufbau, siehe `11_Planetenwerte_Formeln.md`) ist
  notwendig.

## 3. Standardzoll und individuelle Verträge

```text
Standardzoll (kein Vertrag):  8 % pro Frachter
```

Entschieden als tatsächlicher Startwert (nicht mehr nur Beispiel) –
gilt einheitlich für alle Spieler ohne individuellen Vertrag. Beispiel
für individuelle Verträge (noch nicht implementiert, siehe
Umsetzungskonzept/06_..., §6):

```text
Spieler X (Vertrag):          0 %
Spieler Y (Vertrag):          2 %
Spieler Z (Vertrag):          5 %
```

- Verträge sind **nicht transitiv**: A–B 0 % und B–C 0 % erzeugt
  **keinen** Vertrag A–C.
- Erwartetes Muster: niedriger/kein Zoll für direkte Nachbarn
  (gegenseitige Abhängigkeit von der jeweils anderen Route), höherer
  Standardzoll für entfernte Transitspieler.

## 4. Reisezeit (Basiswert entschieden)

```text
Gateway-Reise:      2 Spielstunden pro durchquertem Gateway-Sprung
Trägerschiff-Reise: 10 × Gateway-Reisezeit ≈ 20 Spielstunden pro Sprung
```

Erster Arbeitswert (kein finales Balancing), aber jetzt ein konkreter
Basiswert statt nur des 10×-Verhältnisses aus
`03_Schiffsklassen_und_Kontersystem.md` §3. Reisezeit ist **rein
sprunganzahl-basiert**, nicht von der geometrischen Distanz auf der
Galaxiekarte abhängig – konsistent mit der bereits sprungbasierten
Gateway-Reichweite (§1) und der Handelsstations-Erreichbarkeit.

## 5. Be- und Entladen von Frachtern

```text
Be-/Entladen an einem Handelsposten/Depot ist instantan
(keine eigene Zeitkomponente) – begrenzt einzig durch die
Ladekapazität des Frachters und den verfügbaren Warenbestand.
```

Konzeption/05_Handelsgilde_und_Warenprinzip.md §2 nennt Verladen als
Teil des Transportvorgangs, aber nicht als eigenen Zeitfaktor – anders
als Reisezeit, Gebühren oder Blockaden. Die Reisezeit (§4) bleibt damit
die einzige Zeitkomponente eines Handelstransports.

## Offene Zahlenfragen

- Wie wird die Alien-KI-„Sympathie"/Legitimität eines Spielers exakt
  bewertet – reicht das reine Gateway-Gewicht, oder fließen weitere
  Faktoren ein?
- Wie viele Schiffe kann ein Trägerschiff aufnehmen?
- Was geschieht bei exakt gleichem Gateway-Gewicht mehrerer Spieler im
  selben System?
- Sind bilaterale Zoll-/Handelsvereinbarungen technisch bindend
  (systemseitig durchgesetzt) oder rein sozial brechbar? Wie werden
  Abschluss, Dauer und Bruch technisch abgebildet?
