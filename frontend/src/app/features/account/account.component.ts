import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { GAME_API } from '../../core/sim/game-api.token';
import { UiClockService } from '../../core/ui/ui-clock.service';
import { TransactionReason } from '../../core/models';

const REASON_LABEL: Record<TransactionReason, string> = {
  Wage: 'Lohn', Consumption: 'Konsum', FleetUpkeep: 'Flottenunterhalt', BuildingUpkeep: 'Gebäudeunterhalt',
  GatewayFee: 'Gateway-Gebühr', Trade: 'Handel', MoneyCreation: 'Geldschöpfung', Transfer: 'Überweisung',
  Construction: 'Bau', Production: 'Produktion', Recruitment: 'Rekrutierung',
  Tax: 'Steuer', Subsidy: 'Ausgleichsfonds',
};

/** Wie viele der jüngsten Transaktionen in die Einnahmen/Ausgaben-Übersicht einfließen. */
const RECENT_WINDOW = 50;

interface ReasonBreakdown {
  reason: TransactionReason;
  label: string;
  total: number;
  count: number;
}

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [DecimalPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './account.component.html',
  styleUrl: './account.component.scss',
})
export class AccountComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  protected readonly player = this.api.player;
  protected readonly wallet = this.api.wallet;
  protected readonly transactions = this.api.transactions();

  /**
   * Versatz der Spieluhr des Servers gegen die Wanduhr, in ganzen Minuten
   * (negativ = die Spieluhr geht nach). Nach einem Serverneustart mit
   * geladenem Spielstand läuft die Spieluhr dort weiter, wo sie stand – alle
   * Restzeiten der Oberfläche rechnen damit, hier steht es zur Einordnung.
   */
  protected readonly clockOffsetMinutes = computed(() => Math.round(this.clock.offset() / 60000));

  protected readonly recentTransactions = computed(() => this.transactions().slice(0, RECENT_WINDOW));

  protected readonly expenseBreakdown = computed(() =>
    this.breakdownBy(this.recentTransactions().filter(tx => !this.isIncome(tx.toWalletId))));

  protected readonly incomeBreakdown = computed(() =>
    this.breakdownBy(this.recentTransactions().filter(tx => this.isIncome(tx.toWalletId))));

  protected readonly totalExpense = computed(() => this.expenseBreakdown().reduce((sum, r) => sum + r.total, 0));
  protected readonly totalIncome = computed(() => this.incomeBreakdown().reduce((sum, r) => sum + r.total, 0));

  protected reasonLabel(r: TransactionReason): string { return REASON_LABEL[r]; }

  /**
   * Ersetzt interne Produkt-IDs in der Buchungsnotiz durch den Produktnamen.
   * Im Transaktionsverlauf stand vorher wörtlich "Konsum p_grundnahrung".
   */
  protected noteText(note: string | null | undefined): string {
    if (!note) return '';
    return note.replace(/p_[a-z0-9_]+/gi, id => this.api.productTypes().find(p => p.id === id)?.name ?? id);
  }

  /**
   * Saldo je Spielstunde – die Bezugsgröße, die den Blöcken "Zuletzt
   * ausgegeben/eingenommen" fehlte: "letzte 50 Buchungen" sagt nichts darüber,
   * ob das Konto wächst oder leerläuft.
   */
  protected readonly flowPerHour = this.api.treasuryFlowPerHour();
  protected isIncome(toWalletId: string | null): boolean {
    return toWalletId === this.wallet()?.id;
  }

  protected shareOf(total: number, group: ReasonBreakdown[]): number {
    const sum = group.reduce((s, r) => s + r.total, 0);
    return sum > 0 ? (total / sum) * 100 : 0;
  }

  private breakdownBy(txs: { reason: TransactionReason; amount: number }[]): ReasonBreakdown[] {
    const byReason = new Map<TransactionReason, ReasonBreakdown>();
    for (const tx of txs) {
      const entry = byReason.get(tx.reason) ?? { reason: tx.reason, label: REASON_LABEL[tx.reason], total: 0, count: 0 };
      entry.total += tx.amount;
      entry.count += 1;
      byReason.set(tx.reason, entry);
    }
    return [...byReason.values()].sort((a, b) => b.total - a.total);
  }
}
