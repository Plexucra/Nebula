# TODO

- [ ] e2e (`backend/e2e/full-playthrough.mjs`) schlägt beim ersten Kampf-Tick fehl: lokal vorausberechnete Verteidigerverluste (`computeSideDamage`/`applyDamage`) erwarten `{}`, Server liefert tatsächlich `{"p_destroyer":1,"p_corvette":2}`. Betrifft `backend/src/main/java/de/nebula/state/BattleCommands.java`. Nicht durch die Workforce-Redesign-Änderungen verursacht (Datei war nicht Teil dieser Änderungen) – vermutlich vorbestehender Determinismus-Mismatch bei der Kampfberechnung.
