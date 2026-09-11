package de.nebula.state;

import de.nebula.model.ChainPlan;
import de.nebula.model.Colony;
import de.nebula.model.NotificationType;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

/**
 * Löhne je Arbeitsstunde (Umsetzungskonzept/38_...md, Teil B) – EINE Fassung
 * für die drei Warteschlangen (Industrie, Werft, Ausbildungszentrum). Der
 * Lohn eines Auftrags steht im Plan ({@code ChainPlan.wageCredits}) und wird
 * beim START vom Kommandanten ins Bevölkerungs-Wallet der Kolonie gebucht;
 * reicht das Guthaben nicht, startet der Auftrag nicht (Code
 * {@code Notifications.CODE_WAGES_UNPAID}). Ein Abbruch erstattet nichts –
 * die Arbeit ist geleistet.
 */
final class Wages {
  private Wages() {
  }

  /** Ob der Kommandant der Kolonie die Löhne dieses Plans aufbringen kann. */
  static boolean affordable(GameState state, String colonyId, ChainPlan plan) {
    if (plan.wageCredits <= 0.005) return true;
    Wallet wallet = ownerWallet(state, colonyId);
    return wallet != null && wallet.balance + 1e-9 >= plan.wageCredits;
  }

  /** Bucht die Löhne des Plans; Aufrufer hat {@link #affordable} geprüft. */
  static void pay(GameState state, IdGenerator ids, String colonyId, ChainPlan plan, String label) {
    if (plan.wageCredits <= 0.005) return;
    Wallet wallet = ownerWallet(state, colonyId);
    String popWalletId = GameQueries.popWalletIdForColony(state, colonyId);
    if (wallet == null || popWalletId == null) return;
    Ledger.recordTx(state, ids, wallet.id, popWalletId, plan.wageCredits, TransactionReason.Wage,
        "Löhne: " + label + " (" + Math.round(plan.totalWorkHours) + " Arbeitsstunden)");
  }

  /** Meldung an die Kolonie, dass ein Auftrag an den Löhnen hängt. */
  static void notifyUnpaid(GameState state, IdGenerator ids, String colonyId, ChainPlan plan, String label, String queueName) {
    Wallet wallet = ownerWallet(state, colonyId);
    double balance = wallet != null ? wallet.balance : 0;
    Notifications.notify(state, ids, NotificationType.Problem, Notifications.CODE_WAGES_UNPAID,
        queueName + " angehalten: die Löhne für \"" + label + "\" (" + Math.round(plan.wageCredits) + " Cr) übersteigen das Guthaben ("
            + Math.round(balance) + " Cr). Der Auftrag wartet – Fortsetzen prüft erneut, Einnahmen bringen Verkäufe an die Bevölkerung.",
        colonyId, "/konto");
  }

  private static Wallet ownerWallet(GameState state, String colonyId) {
    Colony colony = ColonyCommands.colony(state, colonyId);
    return colony == null ? null : GameQueries.findWallet(state, WalletOwnerType.Player, colony.ownerId);
  }
}
