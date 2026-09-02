# Mechanik: Ressourcenprofile und Cluster

*Grundprinzipien: `Konzeption/02_Ressourcen_und_Geografie.md`. Quelle:
`02/02_Rohstoffe_und_Ressourcenverteilung.md`.*

## 1. Ressourcenprofil je Schiffstyp (Beispielstruktur)

Beispielhaftes Muster aus dem Ursprungsdokument (noch keine echten
Kategorienamen oder Zahlen):

```text
Schiffstyp A → hoher Bedarf an Ressourcengruppe A
Schiffstyp B → hoher Bedarf an Ressourcengruppe B
Schiffstyp C → hoher Bedarf an Ressourcengruppe C
```

Alle Ressourcengruppen bleiben für alle Produkte relevant, nur mit
unterschiedlicher Gewichtung. Jedes Produkt (Schiffstyp, Modul, etc.)
besitzt damit ein eigenes gewichtetes Ressourcenprofil (Vektor über alle
Ressourcenkategorien).

## 2. Verteilungslogik der Ressourcen

- Jeder Rohstoff existiert grundsätzlich auf jedem Planeten, aber in
  stark unterschiedlicher **Konzentration**.
- Rohstoffe sind **unerschöpflich** – keine Abbau-bis-Erschöpfung-Logik,
  sondern rein konzentrationsbasiert.
- Ressourcencluster sind großräumig: benachbarte Planeten reichen nicht
  aus, um regionale Knappheit auszugleichen.
- Je stärker eine Region auf eine Ressource ausschlägt, desto stärker
  fehlen ihr tendenziell die anderen Ressourcen (implizites
  Nullsummen-/Gegengewichtsprinzip zwischen Ressourcenkategorien).
### Cluster-Erzeugungsregel (konkret festgelegt)

Cluster entstehen nicht durch eine separate Platzierungslogik, sondern
automatisch aus einer lokalen Nachbarschaftsregel: Ein Nachbarsystem
darf pro Ressource höchstens **10 Prozentpunkte mehr** Konzentration
besitzen als das stärkste Vorkommen dieser Ressource im aktuellen
System.

Beispiel: Besitzt der beste Planet für Ressource R1 im aktuellen System
eine Konzentration von 40 %, darf kein Planet im direkten Nachbarsystem
mehr als 50 % R1 besitzen. Lag dort tatsächlich 50 % vor, gilt für
dessen eigenen Nachbarn wiederum höchstens 60 %, und so weiter. Je
größer die Entfernung (Anzahl der Gateway-Sprünge) von der
ursprünglichen Konzentration, desto stärker dürfen sich die Systeme
folglich unterscheiden – die 10-Prozentpunkte-Schranke gilt dabei immer
nur zwischen direkten Nachbarn, nicht kumulativ über die gesamte
Kette hinweg berechnet.

## Offene Zahlenfragen

- Anzahl der Ressourcenkategorien insgesamt.
- Ob und wie die 10-Prozentpunkte-Nachbarschaftsregel nach oben gedeckelt
  wird (verhindert eine harte Obergrenze, dass eine Kette von Sprüngen
  am Ende 100 % erreicht?) und wie sie sich in die Gegenrichtung
  (abnehmende Konzentration) auswirkt.
- Konkrete Ressourcenprofile (Gewichtungsvektoren) der drei
  Kampfschiffstypen und anderer Produkte.
