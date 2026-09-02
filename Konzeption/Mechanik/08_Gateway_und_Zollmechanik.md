# Mechanik: Gateway-Gewicht, Zölle und Reichweiten

*Grundprinzipien: `Konzeption/04_Gateways_und_politische_Ordnung.md`,
`Konzeption/07_Planeten_und_Bevoelkerung.md`. Quelle:
`02/04_Gateways_und_interstellare_Reisen.md`,
`02/07_Planeten_Bevoelkerung_und_politische_Integration.md` §6–7.*

## 1. Gateway-Reichweite

```text
Erste Größenordnung: ca. 5–10 erreichbare Systeme pro Gateway.
Kein Balancewert.
```

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

Beispielstruktur:

```text
Standardzoll (kein Vertrag):  8 % pro Frachter
Spieler X (Vertrag):          0 %
Spieler Y (Vertrag):          2 %
Spieler Z (Vertrag):          5 %
```

- Verträge sind **nicht transitiv**: A–B 0 % und B–C 0 % erzeugt
  **keinen** Vertrag A–C.
- Erwartetes Muster: niedriger/kein Zoll für direkte Nachbarn
  (gegenseitige Abhängigkeit von der jeweils anderen Route), höherer
  Standardzoll für entfernte Transitspieler.

## 4. Trägerschiff-Reisezeit (Referenz)

```text
Reisezeit per Trägerschiff ≈ 10 × Reisezeit per Gateway
```

(identisch zu `03_Schiffsklassen_und_Kontersystem.md` §3, hier als
Referenz für Gateway-bezogene Zeitvergleiche.)

## Offene Zahlenfragen

- Wie wird die Alien-KI-„Sympathie"/Legitimität eines Spielers exakt
  bewertet – reicht das reine Gateway-Gewicht, oder fließen weitere
  Faktoren ein?
- Wie groß ist die tatsächliche Reichweite eines Gateways, wie viele
  direkte Verbindungen besitzt ein typisches System (finaler Wert)?
- Wie groß ist der tatsächliche Zeit- und Kostenunterschied zwischen
  Gateway- und Trägerreise (finaler Faktor, aktuell nur Größenordnung
  10×)?
- Wie viele Schiffe kann ein Trägerschiff aufnehmen?
- Was geschieht bei exakt gleichem Gateway-Gewicht mehrerer Spieler im
  selben System?
- Sind bilaterale Zoll-/Handelsvereinbarungen technisch bindend
  (systemseitig durchgesetzt) oder rein sozial brechbar? Wie werden
  Abschluss, Dauer und Bruch technisch abgebildet?
