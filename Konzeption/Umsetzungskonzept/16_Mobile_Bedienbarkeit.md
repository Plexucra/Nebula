# 16. Mobile Bedienbarkeit (ohne Abstriche auf dem Desktop)

Der Nutzer ruft die Anwendung inzwischen per LAN vom Smartphone auf
(`http://<LAN-IP>:8080/`, siehe Dokument 14). Die Oberfläche war
desktop-first gebaut. Vorgabe: **additiv statt umbauend** – der
Desktop-Zustand ist der Referenzzustand und darf sich nicht verschlechtern.

## Grundsatz der Umsetzung

Alle Änderungen liegen **ausschließlich in Media-Queries unterhalb von
720 px**. Kein einziges Desktop-Layout wurde umgestellt; oberhalb des
Breakpoints ist jede Regel identisch zu vorher. Einzige Ausnahmen sind zwei
neutrale Ergänzungen, die auf beiden Größen gelten und dort nichts
verändern: der ☰-Schalter (auf dem Desktop `display: none`) und die
Hilfsklasse `.scroll-x`.

**Breakpoints**: 720 px als Hauptgrenze (Handy/schmale Tablets). Der bereits
vorhandene 860-px-Breakpoint im App-Shell (Icon-only-Navigation) bleibt
unverändert bestehen und greift weiterhin für den Bereich dazwischen.

## Angepasste Stellen

| Bereich | Problem am Handy | Ergänzung (nur < 720 px) |
|---|---|---|
| Hauptnavigation (10 Menüpunkte) | selbst als Icon-Spalte 74 px Breite dauerhaft belegt | ☰-Schalter in der Kopfzeile; die Seitenleiste wird zur eingeblendeten **Schublade** (`position: fixed`, 232 px, `translateX(-100%)` → `0`) mit abdunkelndem Hintergrund; schließt automatisch beim Navigieren und beim Tippen auf den Hintergrund. In der Schublade sind die Beschriftungen wieder sichtbar. |
| Statusleiste (Credits, Gateway, Glocke, Reset, Abmelden) | überläuft in einer Zeile | Kopfzeile darf umbrechen (`flex-wrap`), Höhe wird flexibel, Schalter mindestens 40 px hoch |
| Benachrichtigungs-Panel | breiter als der Bildschirm | auf `left: 8px / right: 8px`, `max-height: 70vh` festgesetzt |
| Kolonie-Detail (7 Tabs) | Tabs zu klein zum Tippen | Tab-Höhe 37 → **44 px**; der Tab-Streifen scrollte schon vorher horizontal in sich selbst und tut das weiter |
| Kolonie-Detail Detailraster | zwei Spalten zu schmal | `.grid-2` auf eine Spalte |
| Konto: Transaktionsliste (130px \| 1fr \| auto \| 90px) | vier Spalten passen nicht | gestapelt auf `1fr auto`, Notiz über die volle Breite, Betrag bleibt rechtsbündig |
| Handel: Order-Zeile (5 Spalten) | passt nicht | gestapelt auf `1fr auto`, Verkäufer über die volle Breite |
| Statistiken | Kachelraster zu grob | Kacheln ab 140 px; die breiten Kolonie-Tabellen behalten ihre `min-width` und scrollen weiter **in ihrem eigenen Container** (`.npc-table { overflow-x: auto }`, war bereits vorhanden) |
| Kolonienliste / Produktionsübersicht | Karten zu schmal | einspaltig bzw. Kacheln ab 150 px; Zusammenfassungszeilen gestapelt |
| Formulare (Verfassen, Auftragsformulare) | Felder nebeneinander gequetscht | `flex-direction: column`, Schaltflächen über die volle Breite |
| Eingabefelder allgemein | iOS zoomt bei < 16 px Schriftgröße automatisch hinein | `font-size: 16px`, `min-height: 40px` |
| Schaltflächen allgemein | 11-px-Buttons schwer zu treffen | `min-height: 40px`, kleine Varianten 36 px |
| Lange Produktnamen | sprengten ihre Karte um wenige Pixel und wurden abgeschnitten | `overflow-wrap: anywhere` auf Karten-/Zeilenklassen, `min-width: 0` auf den Rastern |
| Seite insgesamt | horizontales Scrollen der ganzen Seite | `overflow-x: hidden` auf `html, body`; breite Inhalte scrollen ausschließlich in ihrem eigenen Container |

## Bewusst NICHT angepasst

- **Galaxiekarte**: bereits touch-tauglich – sie nutzt Pointer-Events
  (Verschieben, Pinch-Zoom über zwei Zeiger) und hat `touch-action: none`.
  Ihr Inhalt ragt naturgemäß über den Viewport hinaus; das ist der Zweck
  einer pannbaren Karte, kein Layoutfehler.
- **Breite Statistik-Tabellen**: bleiben breit und scrollen horizontal in
  ihrem Container. Ein Umbruch auf Karten würde den direkten Spaltenvergleich
  zerstören, der der einzige Zweck dieser Ansicht ist.
- **Fortschrittsbalken-Füllungen**, die rechnerisch über ihren Track
  hinausragen: liegen in einem Element mit `overflow: hidden` und sind
  optisch korrekt – kein Eingriff nötig.
- **Der 860-px-Breakpoint** wurde nicht ersetzt, sondern ergänzt: zwischen
  720 und 860 px bleibt es bei der bisherigen Icon-Navigation.

## Verifikation

Die Fenstergröße ließ sich über die Browser-Werkzeuge nicht zuverlässig
setzen (`resize_window` meldete Erfolg, `window.outerWidth` blieb aber bei
1920 – das Fenster war maximiert). Ersatzweg: die laufende Anwendung wurde in
einem **gleich-origin-iframe mit exakt gesetzter Größe** geladen. CSS-Media-
Queries werten den iframe-Viewport aus, der Test ist damit echt – bestätigt
durch `matchMedia('(max-width: 720px)').matches` im iframe-Kontext.

**Handy-Durchgang (390 × 844, effektiv 386 × 816 Viewport)** – gegen den
laufenden Server unter `http://192.168.178.95:8080/`, mit echter
Registrierung und Navigation durch alle zehn Hauptansichten:

- `matchMedia('(max-width: 720px)') = true`, ☰-Schalter `display: flex`
- Schublade geschlossen: `transform: matrix(1,0,0,1,-232,0)` (außerhalb des
  Bildschirms); nach Tippen auf ☰: `transform: none`, `left: 0`, Breite
  232 px, Hintergrund-Abdunklung vorhanden, alle **10 Menüpunkte mit
  Beschriftung**, Zeilenhöhe **44 px**
- **Kein horizontales Seiten-Scrollen in keiner der zehn Ansichten**:
  `documentElement.scrollWidth == innerWidth == 386` auf `/planeten`,
  `/produktion`, `/flotten`, `/bodentruppen`, `/diplomatie`, `/nachrichten`,
  `/galaxie`, `/handel`, `/konto`, `/statistiken`
- Kolonie-Detail: alle 7 Tabs bedienbar, Tab-Höhe 44 px, Tab-Streifen scrollt
  in sich selbst (`overflow-x: auto`, `scrollWidth > clientWidth`), kein
  Seiten-Scrollen auf irgendeinem Tab
- Die verbliebenen Überstände liegen ausnahmslos in eigenen Scroll- bzw.
  Clipping-Containern (Statistik-Tabelle in `.npc-table`, Kartengrafik im
  SVG, Balkenfüllung in `overflow: hidden`)

**Desktop-Durchgang (1440 × 900)** – Abnahmekriterium "nichts kaputt":

- `matchMedia('(max-width: 720px)') = false`, ☰-Schalter `display: none`
- Seitenleiste unverändert: `position: static`, `transform: none`,
  **Breite 220 px**, Beschriftungen sichtbar
- Kopfzeile unverändert: **Höhe 60 px**, `flex-wrap: nowrap`
- Alle neun Hauptansichten geprüft: Inhalt gerendert, Seitenleiste 220 px,
  ☰ verborgen, kein horizontales Scrollen
- Kolonie-Detail: alle 7 Tabs mit der **ursprünglichen Höhe von 37 px**

`tsc --noEmit` und `ng build --configuration production` laufen sauber durch.
