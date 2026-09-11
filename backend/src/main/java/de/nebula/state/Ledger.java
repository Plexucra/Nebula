package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Transaction;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;

/**
 * 1:1-Portierung von {@code recordTx} aus {@code simulated-game-api.service.ts}.
 * {@code fromWalletId}/{@code toWalletId} sind absichtlich nullable (Geldschöpfung
 * bzw. Geldvernichtung ohne Gegenkonto, siehe TS-Original) – nur Beträge {@code > 0}
 * werden gebucht.
 */
public final class Ledger {
  private Ledger() {
  }

  /** Historie wird wie im TS-Original auf die letzten 800 Einträge gedeckelt. */
  private static final int MAX_TRANSACTIONS = 800;

  public static void recordTx(GameState state, IdGenerator ids, String fromWalletId, String toWalletId,
                               double amount, TransactionReason reason, String note) {
    if (amount <= 0) return;
    for (Wallet w : state.wallets) {
      if (fromWalletId != null && w.id.equals(fromWalletId)) w.balance -= amount;
      if (toWalletId != null && w.id.equals(toWalletId)) {
        w.balance += amount;
        // Einkommen der Bevölkerung (Umsetzungskonzept/38, Teil C): jeder Zufluss ins
        // Bevölkerungs-Wallet – Löhne, Unterhalt, Ausbau, Wachstumsgeld, Ausgleichsfonds –
        // zählt für das Tagesbudget ihrer Gebote. Escrow-Erstattungen laufen nicht
        // über den Ledger und zählen deshalb nicht doppelt.
        if (w.ownerType == de.nebula.model.WalletOwnerType.Population) {
          state.populationInflowSinceLastDay.merge(w.ownerId, amount, Double::sum);
        }
      }
    }
    Transaction tx = new Transaction();
    tx.id = ids.next("tx");
    tx.fromWalletId = fromWalletId;
    tx.toWalletId = toWalletId;
    tx.amount = Math.round(amount * 100) / 100.0;
    tx.reason = reason;
    tx.at = Clock.now();
    tx.note = note;
    state.transactions.add(tx);
    while (state.transactions.size() > MAX_TRANSACTIONS) state.transactions.remove(0);
  }
}
