#!/usr/bin/env node
// Wiederholbarer End-to-End-Test des NEBULA-Backends – AUSSCHLIESSLICH über
// echte WebSocket-Verbindungen zum "/game"-Endpunkt, exakt wie ein echtes
// Frontend es täte (kein direktes Anfassen von GameState/Java-Services).
//
// Deckt den kompletten Spielablauf ab (Umsetzungskonzept/
// 13_Client_Server_Migration_Quarkus_Backend.md, Abschnitt
// "Vollständigkeitsprüfung"):
//   1. Zwei Kommandanten registrieren (zwei unabhängige WS-Verbindungen).
//   2. Kommandant A: Werft ausbauen (Bebauung), tickgetriebene Fertigstellung
//      abwarten; Produktionskette (Rohstoff) einreihen und deren
//      tickgetriebene Fertigstellung abwarten, Lagerbestand verifizieren.
//   3. Werft-Warteschlange anstoßen (Kriegsschiff bauen) – NUR die
//      Befehlsmechanik wird geprüft (Warteschlangeneintrag korrekt
//      angelegt/gestartet), NICHT die volle Fertigstellung abgewartet: echte
//      Kriegsschiffe liegen auf Produktionsbaum-Ebene 6/7 mit
//      Gesamtbauzeiten im drei- bis vierstelligen Spielstunden-Bereich
//      (siehe Nebula_Flache_Produktliste_..., bewusst so bemessen) – ein
//      Abwarten würde den Test unpraktikabel lang machen, ohne zusätzliche
//      Aussagekraft gegenüber der bereits bewiesenen Warteschlangen-Logik zu
//      liefern (identischer Mechanismus wie die bereits vollständig
//      abgewartete Rohstoff-Produktionskette in Schritt 2). Für die
//      eigentliche Bewegung/den Kampf wird stattdessen die von Anfang an
//      kampffähige Startflotte verwendet (siehe world-seed.ts,
//      `combatFleet`).
//   4. Die tatsächlich vorhandene Kampfflotte per echtem Gateway-Sprung
//      (hop-für-hop, ereignisbasiert) von A zum Heimatsystem von B bewegen.
//      Gateway-Routen sind ungerichtet (Graph.java/graph.ts), die
//      Hop-Distanz A→B ist also immer identisch zu B→A – das Skript
//      verifiziert das per eigener BFS-Nachrechnung, statt naiv anzunehmen.
//      Die absolute Hop-Zahl selbst hängt von der zufälligen Galaxie-Topologie
//      ab (in Testläufen bislang 1-6 Sprünge beobachtet); die Wartezeit
//      richtet sich exakt danach.
//   5. Krieg erklären, Verteidiger blockiert seine Heimatkolonie, Angreifer
//      löst ein Gefecht aus, EIN echter Kampf-Tick wird tickgetrieben
//      abgewartet. Das Ergebnis wird NICHT nur auf "irgendeine Änderung"
//      geprüft, sondern gegen eine lokale Nachbildung von
//      `computeSideDamage`/`applyDamage` aus `BattleCommands.java`
//      (Mechanik/04_..., §2-5) exakt nachgerechnet – siehe `expectedTick()`
//      unten, 1:1 aus Formulas.java/formulas.ts übernommene Konstanten.
//
// Nutzung:
//   1. Backend separat starten: cd backend && ./mvnw quarkus:dev
//   2. node backend/e2e/full-playthrough.mjs [ws://localhost:8080/game]
//
// Voraussetzung: Node.js ≥ 22 (globales `WebSocket`). Beendet sich mit
// Exit-Code 0 bei Erfolg, wirft (Exit-Code ≠ 0) bei der ersten fehlgeschlagenen
// Prüfung mit einer möglichst präzisen Fehlermeldung.

import assert from 'node:assert/strict';

const WS_URL = process.argv[2] ?? 'ws://localhost:8080/game';

// --- Formeln aus Formulas.java/formulas.ts, für die harten Kampf-Assertions ---
const COMBAT_DAMAGE_FACTOR = 0.2;
const COMBAT_DURABILITY_FACTOR = 1;
const HOURS_PER_GATEWAY_HOP = 4;
const REAL_MS_PER_GAME_HOUR = 2500;

function hoursToMs(h) { return h * REAL_MS_PER_GAME_HOUR; }

function shipMilitaryValue(product) {
  // Entspricht Formulas.productionAspect(workHoursPerUnit, baseProductionHours).
  // Harte Prüfung, weil ein fehlendes Feld sonst als NaN durch computeSideDamage
  // laufen und dort zu leeren (statt falschen) Erwartungswerten führen würde.
  const value = product?.workHoursPerUnit * product?.baseProductionHours;
  assert.ok(Number.isFinite(value),
    `shipMilitaryValue: workHoursPerUnit/baseProductionHours fehlen oder sind keine Zahl für ${product?.id} – Katalogfeld umbenannt?`);
  return value;
}

function counterMultiplier(attackerCountersDefender, defenderCountersAttacker) {
  if (attackerCountersDefender) return 2;
  if (defenderCountersAttacker) return 0.5;
  return 1;
}

/** 1:1-Nachbildung von BattleCommands.computeSideDamage. */
function computeSideDamage(attackerShips, defenderShips, productById, shipDefById) {
  const defenderCostByType = new Map();
  let totalDefenderCost = 0;
  for (const d of defenderShips) {
    if (d.quantity <= 0) continue;
    const cost = shipMilitaryValue(productById.get(d.shipProductTypeId)) * d.quantity;
    defenderCostByType.set(d.shipProductTypeId, cost);
    totalDefenderCost += cost;
  }
  const damageByType = new Map();
  if (totalDefenderCost <= 0) return damageByType;
  for (const atk of attackerShips) {
    if (atk.quantity <= 0) continue;
    const atkDef = shipDefById.get(atk.shipProductTypeId);
    const rawGroupDamage = atk.quantity * shipMilitaryValue(productById.get(atk.shipProductTypeId)) * COMBAT_DAMAGE_FACTOR;
    for (const [defTypeId, cost] of defenderCostByType) {
      const defDef = shipDefById.get(defTypeId);
      const share = cost / totalDefenderCost;
      const mult = counterMultiplier(atkDef.countersClass === defDef.class, defDef.countersClass === atkDef.class);
      damageByType.set(defTypeId, (damageByType.get(defTypeId) ?? 0) + rawGroupDamage * share * mult);
    }
  }
  return damageByType;
}

/** 1:1-Nachbildung von BattleCommands.applyDamage (residual startet leer, siehe engageBattle). */
function applyDamage(ships, damageByType, productById) {
  const losses = {};
  for (const s of ships) {
    const dmg = damageByType.get(s.shipProductTypeId);
    if (!dmg || s.quantity <= 0) continue;
    const durability = shipMilitaryValue(productById.get(s.shipProductTypeId)) * COMBAT_DURABILITY_FACTOR;
    const lostCount = Math.min(Math.floor(dmg / durability), s.quantity);
    if (lostCount > 0) losses[s.shipProductTypeId] = lostCount;
  }
  return losses;
}

// --- Minimaler WS-Client: {type,requestId,payload} -> Promise<Ack.payload> ---
/** Frist für EINEN Befehl. Ohne sie hinge ein verlorener Ack den Lauf unbegrenzt auf. */
const CALL_TIMEOUT_MS = 30_000;

class GameClient {
  constructor(label) {
    this.label = label;
    this.counter = 0;
    this.pending = new Map();
    this.pushes = [];
    this.deadReason = null;
    this.ws = new WebSocket(WS_URL);
    this.ws.addEventListener('message', (ev) => this.onMessage(JSON.parse(ev.data)));
    // Ohne diese beiden Handler bliebe ein Verbindungsabbruch unbemerkt: die
    // offenen call()-Promises würden nie erfüllt, der Lauf bliebe still stehen
    // statt mit einer Diagnose abzubrechen.
    this.ws.addEventListener('close', (ev) => this.die(`Verbindung geschlossen (code=${ev.code})`));
    this.ws.addEventListener('error', () => this.die('Verbindungsfehler'));
  }

  die(reason) {
    if (this.deadReason) return;
    this.deadReason = reason;
    for (const [, entry] of this.pending) entry.reject(new Error(`[${this.label}] ${reason}`));
    this.pending.clear();
  }

  ready() {
    return new Promise((resolve, reject) => {
      this.ws.addEventListener('open', () => resolve(), { once: true });
      this.ws.addEventListener('error', (e) => reject(e), { once: true });
    });
  }

  onMessage(msg) {
    if (msg.requestId) {
      const entry = this.pending.get(msg.requestId);
      if (!entry) return;
      this.pending.delete(msg.requestId);
      if (msg.type === 'Error') entry.reject(new Error(`[${this.label}] ${msg.payload?.message ?? 'Unbekannter Fehler'}`));
      else entry.resolve(msg.payload);
    } else {
      this.pushes.push(msg);
    }
  }

  call(type, payload = {}) {
    if (this.deadReason) return Promise.reject(new Error(`[${this.label}] ${this.deadReason}`));
    return new Promise((resolve, reject) => {
      const requestId = `${this.label}-${++this.counter}`;
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new Error(`[${this.label}] Zeitüberschreitung nach ${CALL_TIMEOUT_MS} ms bei Befehl "${type}"`));
      }, CALL_TIMEOUT_MS);
      const settle = (fn) => (value) => { clearTimeout(timer); fn(value); };
      this.pending.set(requestId, { resolve: settle(resolve), reject: settle(reject) });
      this.ws.send(JSON.stringify({ type, requestId, payload }));
    });
  }

  close() {
    this.deadReason = 'Verbindung regulär geschlossen';
    this.ws.close();
  }
}

function log(msg) {
  console.log(`[e2e] ${msg}`);
}

async function waitUntil(predicateAsync, { timeoutMs, intervalMs = 500, description }) {
  const start = Date.now();
  for (;;) {
    const result = await predicateAsync();
    if (result !== undefined) return result;
    if (Date.now() - start > timeoutMs) throw new Error(`Timeout beim Warten auf: ${description}`);
    await new Promise(r => setTimeout(r, intervalMs));
  }
}

/** BFS über die per API abgefragten Gateway-Routen (Nachbildung von core/util/graph.ts bfsHops/bfsPath). */
function bfsHops(routes, from) {
  const adj = new Map();
  for (const r of routes) {
    (adj.get(r.a) ?? adj.set(r.a, []).get(r.a)).push(r.b);
    (adj.get(r.b) ?? adj.set(r.b, []).get(r.b)).push(r.a);
  }
  const dist = new Map([[from, 0]]);
  const queue = [from];
  let head = 0;
  while (head < queue.length) {
    const cur = queue[head++];
    for (const nb of adj.get(cur) ?? []) {
      if (!dist.has(nb)) { dist.set(nb, dist.get(cur) + 1); queue.push(nb); }
    }
  }
  return dist;
}

async function main() {
  log(`Verbinde zwei Clients mit ${WS_URL} ...`);
  const a = new GameClient('A');
  const b = new GameClient('B');
  await Promise.all([a.ready(), b.ready()]);
  log('Beide WebSocket-Verbindungen offen.');

  // --- Schritt 1: zwei Kommandanten registrieren -----------------------------
  const playerA = await a.call('registerPlayer', { commanderName: `E2E-Angreifer-${Date.now()}`, homeworldName: 'Angriffswelt' });
  const playerB = await b.call('registerPlayer', { commanderName: `E2E-Verteidiger-${Date.now()}`, homeworldName: 'Bollwerk' });
  assert.ok(playerA.id && playerA.homeworldColonyId && playerA.homeSystemId, 'registerPlayer(A) muss vollständigen Player liefern');
  assert.ok(playerB.id && playerB.homeworldColonyId && playerB.homeSystemId, 'registerPlayer(B) muss vollständigen Player liefern');
  assert.notEqual(playerA.id, playerB.id, 'A und B müssen unterschiedliche Kommandanten sein');
  log(`A registriert: ${playerA.name} (${playerA.id}), Heimatsystem ${playerA.homeSystemId}`);
  log(`B registriert: ${playerB.name} (${playerB.id}), Heimatsystem ${playerB.homeSystemId}`);

  // --- Startverkaufsorders für die Grundkonsumgüter ------------------------
  // Umsetzungskonzept/15_...md, Auftrag 1: ohne sie kann die Bevölkerung gar
  // nichts kaufen (EconomyTick.runConsumption kauft NUR aus sellOrders, nicht
  // aus dem Kolonielager) – Lebensstandard bliebe dauerhaft 0 %.
  // Seit Umsetzungskonzept/20_...md ist Grundnahrung das EINZIGE mitgelieferte
  // Startkonsumgut (WorldSeed.STARTER_CONSUMER_GOODS) – Grundmedizin und
  // Unterhaltungselektronik bleiben bewusst Sache des Kommandanten, damit die
  // sequentielle Produktionswarteschlange Freiraum für Spezialisierungsstufen hat.
  const starterOrders = await a.call('sellOrders', { systemId: playerA.homeSystemId });
  const starterFood = starterOrders.find(o => o.productTypeId === 'p_grundnahrung');
  assert.ok(starterFood, 'Eine frische Heimatkolonie muss eine Verkaufsorder für Grundnahrung haben');
  assert.ok(starterFood.autoRelist, 'Die Start-Verkaufsorder muss wiederkehrend sein (autoRelist)');
  assert.ok(!starterOrders.some(o => o.productTypeId === 'p_grundmedizin'),
    'Grundmedizin darf laut Umsetzungskonzept/20_...md KEINE Start-Verkaufsorder mehr haben');
  log(`Start-Verkaufsorder vorhanden: ${starterFood.productTypeId} ${starterFood.remainingQuantity}× à ${starterFood.pricePerUnit} Cr (autoRelist), `
    + `Grundmedizin bewusst nicht (Konzept 20).`);

  // Der Lebensstandard muss tickgetrieben über 0 steigen – der eigentliche
  // Beweis, dass der Kreislauf greift und nicht nur Orders existieren.
  const earlyStats = await waitUntil(async () => {
    const stats = await a.call('colonyStats', { id: playerA.homeworldColonyId });
    return stats.standardOfLivingPct > 0 ? stats : undefined;
  }, { timeoutMs: 30000, description: 'Lebensstandard > 0 (Bevölkerung kauft aus den Start-Verkaufsorders)' });
  log(`Lebensstandard tickgetrieben auf ${earlyStats.standardOfLivingPct.toFixed(1)}% gestiegen (vor Auftrag 1 dauerhaft 0 %).`);

  // Und der Gegenbeweis auf der Geldseite: die Käufe der Bevölkerung landen
  // als Einnahme im Spieler-Wallet (vorher kannte es ausschließlich Abflüsse).
  const consumptionTx = await waitUntil(async () => {
    const txs = await a.call('transactions');
    const consumption = txs.filter(t => t.reason === 'Consumption');
    return consumption.length > 0 ? consumption : undefined;
  }, { timeoutMs: 30000, description: 'Konsum-Transaktionen im Spieler-Wallet' });
  const income = consumptionTx.reduce((sum, t) => sum + t.amount, 0);
  log(`${consumptionTx.length} Konsum-Transaktionen, ${income.toFixed(0)} Credits Einnahmen – Kreislauf Produktion → Order → Bevölkerung → Spieler-Wallet geschlossen.`);

  const coloniesA = await a.call('colonies');
  const colonyA = coloniesA.find(c => c.id === playerA.homeworldColonyId);
  assert.ok(colonyA, 'Heimatkolonie von A muss in colonies() auftauchen');
  const coloniesB = await b.call('colonies');
  const colonyB = coloniesB.find(c => c.id === playerB.homeworldColonyId);
  assert.ok(colonyB, 'Heimatkolonie von B muss in colonies() auftauchen');

  // --- Schritt 2a: Bebauung – Startbebauung, Bebauungsplätze, Baustoffe ----
  // WorldSeed (Nutzerentscheidung, abweichend von der ursprünglichen
  // Minimalstart-Herleitung in Umsetzungskonzept/17_...md): Heimatkolonie
  // startet mit Wohnkomplex 1 + Industriekomplex 5 + Infrastruktur 6, alle
  // 6 Bebauungsplätze belegt. Jeder Ausbau kostet Baustoffe; das Startlager
  // enthält keine – der erste Zug ist daher Infrastruktur 7, für die der
  // Industriekomplex die Baustoffe erst produzieren muss.
  const buildingsBefore = await a.call('buildings', { colonyId: colonyA.id });
  assert.deepEqual(
    buildingsBefore.map(x => `${x.typeId}:${x.level}`).sort(),
    ['b_habitat:1', 'b_industry:5', 'b_infrastructure:6'],
    'Startbebauung: Wohnkomplex 1 + Industriekomplex 5 + Infrastruktur 6');
  const slots0 = await a.call('buildSlots', { colonyId: colonyA.id });
  assert.deepEqual([slots0.total, slots0.used, slots0.free], [6, 6, 0], 'Start: 6 Plätze, alle belegt');
  log(`Startbebauung bestätigt: ${buildingsBefore.map(x => x.typeId + ' ' + x.level).join(', ')} · Plätze ${slots0.used}/${slots0.total} (${slots0.free} frei), Infrastruktur planetweit ${slots0.planetInfrastructureTotal}/${slots0.planetInfrastructureMax}`);

  const queueAtStart = await a.call('productionQueue', { colonyId: colonyA.id });
  assert.ok(queueAtStart.some(q => q.status === 'running'), 'Startaufträge müssen sofort laufen');
  log(`Startaufträge laufen sofort an: ${queueAtStart.filter(q => q.status === 'running').length} running, ${queueAtStart.filter(q => q.status === 'queued').length} queued.`);

  await assert.rejects(() => a.call('queueShip', { colonyId: colonyA.id, shipProductTypeId: 'p_corvette', quantity: 1, autoProduceMissing: true, requeueOnComplete: false }),
    /Werft/, 'Ohne Werft muss ein Werft-Auftrag abgelehnt werden');
  await assert.rejects(() => a.call('queueBuilding', { colonyId: colonyA.id, buildingTypeId: 'b_shipyard' }),
    /Bebauungsplatz/, 'Ohne freien Platz muss der Werft-Bau abgelehnt werden');
  log('Werft-Auftrag ohne Werft und Werft-Bau ohne freien Bebauungsplatz korrekt abgelehnt.');

  const breakdown0 = await a.call('colonySpeedBreakdown', { colonyId: colonyA.id });
  const infraPreview = breakdown0.buildingUpgrades.find(u => u.typeId === 'b_infrastructure');
  assert.ok(infraPreview.materials.length >= 2, 'Der Infrastruktur-Ausbau muss Baustoffe verlangen');
  assert.ok(infraPreview.materials.every(m => m.available === 0), 'Startlager enthält keine Baustoffe');
  assert.equal(infraPreview.affordable, false);
  assert.equal(infraPreview.needsSlot, false, 'Infrastruktur belegt selbst keinen Platz');
  await assert.rejects(() => a.call('queueBuilding', { colonyId: colonyA.id, buildingTypeId: 'b_infrastructure' }),
    /Fehlende Baustoffe: p_/, 'Ohne Baustoffe muss der Infrastruktur-Ausbau mit Auflistung abgelehnt werden');
  const nextInfraLevel = infraPreview.currentLevel + 1;
  log(`Infrastruktur ${infraPreview.currentLevel}→${nextInfraLevel} ohne Baustoffe korrekt abgelehnt; Vorschau: ${infraPreview.upgradeCost} Cr, ${infraPreview.upgradeHours}h, Baustoffe ${infraPreview.materials.map(m => `${m.productTypeId} ${m.required} (Lager ${m.available})`).join(', ')}.`);

  // Baustoffe für die nächste Infrastruktur-Stufe produzieren (echte Kettenzeiten),
  // dann tatsächlich ausbauen – Baustoffe werden abgezogen, ein Platz wird frei.
  //
  // Die Startaufträge (Daueraufträge Grundnahrung + stabilisiertes Elerium) werden
  // dabei BEWUSST NICHT abgebrochen, obwohl sie die EINE sequentielle Warteschlange
  // mitbelegen und den Lauf dadurch verlängern: der Elerium-Dauerauftrag ist die
  // Energieversorgung der Kolonie. Ohne ihn ist bei der Startbebauung
  // (Infrastruktur 6 ⇒ 0,047 Elerium/Spielstunde) die 25er-Startreserve nach rund
  // 530 Spielstunden aufgebraucht – kürzer als dieser Produktionslauf dauert. Danach
  // greift der Blackout (Produktion ×0,1), der Lebensstandard fällt auf 0, die
  // Bevölkerung schrumpft und die Fertigung kommt endgültig zum Erliegen: eine
  // Todesspirale, aus der die Kolonie sich nicht mehr selbst befreien kann.
  // ALLE Baustoffe als EINEN gebündelten Auftrag einreihen (queueProductionBundle),
  // nicht als separate Einzelaufträge: Baustoffe können einander als Vorprodukt
  // enthalten (p_leiterbuendel = p_leitermetall + p_polymergrundstoff), Infrastruktur
  // braucht ab Stufe 4 direkt BEIDE zugleich. Getrennte Aufträge würden sich
  // gegenseitig den Lagerbestand wegnehmen (der spätere Auftrag bedient sich per
  // `autoProduceMissing` aus dem, was der frühere gerade erst eingelagert hat) –
  // beide Sollmengen lägen dann nie GLEICHZEITIG im Lager und die Wartebedingung
  // unten könnte nie eintreten. Der Kettenplaner rechnet den gemeinsamen Bedarf
  // stattdessen in einem Rutsch (ChainPlanner.planChain mit mehreren Wurzeln).
  const products = Object.fromEntries(infraPreview.materials.map(m => [m.productTypeId, m.required]));
  await a.call('queueProductionBundle', { colonyId: colonyA.id, products, autoProduceMissing: true, requeueOnComplete: false });
  const materialsReady = await waitUntil(async () => {
    const wh = await a.call('warehouse', { colonyId: colonyA.id });
    const ok = infraPreview.materials.every(m => (wh.find(w => w.productTypeId === m.productTypeId)?.quantity ?? 0) >= m.required);
    return ok ? wh : undefined;
  }, { timeoutMs: 2_700_000, intervalMs: 5000, description: `Baustoffe für Infrastruktur ${nextInfraLevel} produziert (echte Kettenzeiten)` });
  const materialsDoneAt = Date.now();
  log(`Baustoffe produziert: ${infraPreview.materials.map(m => `${m.productTypeId} ${materialsReady.find(w => w.productTypeId === m.productTypeId)?.quantity}`).join(', ')}.`);
  await a.call('queueBuilding', { colonyId: colonyA.id, buildingTypeId: 'b_infrastructure' });
  const whAfter = await a.call('warehouse', { colonyId: colonyA.id });
  for (const m of infraPreview.materials) {
    const rest = whAfter.find(w => w.productTypeId === m.productTypeId)?.quantity ?? 0;
    assert.ok(rest < m.required, `Baustoff ${m.productTypeId} muss beim Einreihen abgezogen werden`);
  }
  const infraUpgraded = await waitUntil(async () => {
    const list = await a.call('buildings', { colonyId: colonyA.id });
    const inf = list.find(x => x.typeId === 'b_infrastructure');
    return inf.level >= nextInfraLevel ? inf : undefined;
  }, { timeoutMs: 60_000, description: `Infrastruktur ${nextInfraLevel} tickgetrieben fertiggestellt` });
  const slotsAfterInfra = await a.call('buildSlots', { colonyId: colonyA.id });
  assert.deepEqual([slotsAfterInfra.total, slotsAfterInfra.used, slotsAfterInfra.free],
    [slots0.total + 1, slots0.used, 1],
    `Infrastruktur ${nextInfraLevel} liefert genau einen zusätzlichen, freien Bebauungsplatz`);
  log(`Infrastruktur ${infraUpgraded.level} fertig, Baustoffe abgezogen, Plätze ${slotsAfterInfra.used}/${slotsAfterInfra.total} (${slotsAfterInfra.free} frei).`);
  globalThis.__materialsDoneAt = materialsDoneAt;

  // --- Schritt 2b: Produktionskette (Rohstoff) einreihen und abwarten --------
  // Die Start-Auftragsliste (Grundnahrung/Elerium) belegt den einzigen
  // sequentiellen Warteschlangenplatz der Kolonie – für einen schnellen,
  // deterministischen Testlauf beide Einträge zunächst regulär per
  // cancelProduction abbrechen (übt zugleich die anteilige
  // Abbruch-Gutschrift-Logik aus echten Client-Befehlen aus).
  const startQueue = await a.call('productionQueue', { colonyId: colonyA.id });
  for (const entry of startQueue) {
    await a.call('cancelProduction', { colonyId: colonyA.id, entryId: entry.id });
  }
  const clearedQueue = await a.call('productionQueue', { colonyId: colonyA.id });
  assert.equal(clearedQueue.length, 0, 'Produktionswarteschlange muss nach Abbruch aller Start-Aufträge leer sein');

  const warehouseBefore = await a.call('warehouse', { colonyId: colonyA.id });
  const ferroBefore = warehouseBefore.find(w => w.productTypeId === 'p_ferrometall')?.quantity ?? 0;

  await a.call('queueProduction', {
    colonyId: colonyA.id, productTypeId: 'p_ferrometall', quantity: 3,
    autoProduceMissing: true, requeueOnComplete: false,
  });
  const runningEntry = await waitUntil(async () => {
    const q = await a.call('productionQueue', { colonyId: colonyA.id });
    const e = q.find(x => x.productTypeId === 'p_ferrometall');
    return e && e.status === 'running' ? e : undefined;
  }, { timeoutMs: 10_000, intervalMs: 200, description: 'p_ferrometall-Auftrag startet (status=running)' });
  assert.ok(runningEntry.plan.totalHours > 0, 'Gestarteter Auftrag muss einen berechneten ChainPlan mit totalHours > 0 haben');
  log(`Produktionsauftrag p_ferrometall×3 läuft, ChainPlan.totalHours=${runningEntry.plan.totalHours.toFixed(3)}`);

  await waitUntil(async () => {
    const q = await a.call('productionQueue', { colonyId: colonyA.id });
    return q.find(x => x.productTypeId === 'p_ferrometall') === undefined ? true : undefined;
  }, { timeoutMs: 30_000, intervalMs: 300, description: 'p_ferrometall-Auftrag tickgetrieben abgeschlossen' });

  const warehouseAfter = await a.call('warehouse', { colonyId: colonyA.id });
  const ferroAfter = warehouseAfter.find(w => w.productTypeId === 'p_ferrometall')?.quantity ?? 0;
  assert.equal(ferroAfter, ferroBefore + 3, `Lagerbestand p_ferrometall muss um genau 3 gestiegen sein (${ferroBefore} → erwartet ${ferroBefore + 3}, tatsächlich ${ferroAfter})`);
  log(`Produktionskette tickgetrieben abgeschlossen: Lagerbestand p_ferrometall ${ferroBefore} → ${ferroAfter}`);

  // --- Schritt 4: Kampfflotte per echtem Gateway-Sprung bewegen --------------
  const fleetsA = await a.call('fleets');
  const combatFleetA = fleetsA.find(f => f.name.startsWith('Kampfflotte'));
  assert.ok(combatFleetA && combatFleetA.ships.some(s => s.quantity > 0), 'A muss eine kampffähige Startflotte besitzen');
  const fleetsB = await b.call('fleets');
  const combatFleetB = fleetsB.find(f => f.name.startsWith('Kampfflotte'));
  assert.ok(combatFleetB && combatFleetB.ships.some(s => s.quantity > 0), 'B muss eine kampffähige Startflotte besitzen');
  log(`Kampfflotte A: ${JSON.stringify(combatFleetA.ships)}`);
  log(`Kampfflotte B: ${JSON.stringify(combatFleetB.ships)}`);

  // Gateway-Routen sind ungerichtet (siehe Graph.java/graph.ts) – die
  // Hop-Distanz A→B ist also zwangsläufig identisch zu B→A, unabhängig von
  // der zufälligen Galaxie-Topologie. Es gibt daher keine "kürzere Richtung"
  // zu wählen; A fliegt zu B und greift dort an (die Wahl der Richtung ist
  // beliebig, siehe Assertion unten, die genau diese Symmetrie verifiziert).
  const routes = await a.call('galaxyRoutes');
  const hopsFromA = bfsHops(routes, playerA.homeSystemId);
  const hopsFromB = bfsHops(routes, playerB.homeSystemId);
  const aToB = hopsFromA.get(playerB.homeSystemId);
  const bToA = hopsFromB.get(playerA.homeSystemId);
  assert.ok(aToB !== undefined && bToA !== undefined, 'Beide Heimatsysteme müssen über das Gateway-Netz erreichbar sein');
  assert.equal(aToB, bToA, 'Hop-Distanz muss in beide Richtungen identisch sein (ungerichteter Graph)');

  const attackerClient = a, defenderClient = b;
  const attackerFleet = combatFleetA, defenderFleet = combatFleetB;
  const attackerPlayer = playerA, defenderPlayer = playerB, defenderColony = colonyB;
  const destinationSystemId = playerB.homeSystemId;
  const expectedHops = aToB;

  const preview = await attackerClient.call('routePreview', { fleetId: attackerFleet.id, destinationSystemId });
  assert.ok(preview, 'routePreview muss für ein erreichbares Zielsystem ein Ergebnis liefern');
  assert.equal(preview.hops, expectedHops, 'routePreview-Sprunganzahl muss mit der selbst berechneten BFS-Distanz übereinstimmen');
  assert.equal(preview.ms, expectedHops * hoursToMs(HOURS_PER_GATEWAY_HOP), 'routePreview-Reisezeit muss hops × HOURS_PER_GATEWAY_HOP × REAL_MS_PER_GAME_HOUR entsprechen');
  log(`Reise ${attackerPlayer.name} → ${destinationSystemId}: ${preview.hops} Gateway-Sprünge, ${preview.ms}ms`);

  const hasVisitedBefore = await attackerClient.call('hasVisitedSystem', { systemId: destinationSystemId });
  assert.equal(hasVisitedBefore, false, 'Zielsystem darf vor der Ankunft noch nicht als besucht gelten');

  await attackerClient.call('moveFleet', { fleetId: attackerFleet.id, destinationSystemId });
  const arrived = await waitUntil(async () => {
    const fl = await attackerClient.call('fleets');
    const f = fl.find(x => x.id === attackerFleet.id);
    return f.status === 'Stationed' && f.systemId === destinationSystemId ? f : undefined;
  }, { timeoutMs: preview.ms + 60_000, intervalMs: 1000, description: `Flotte erreicht Zielsystem nach ${preview.hops} Sprüngen` });
  assert.equal(arrived.pendingHops.length, 0, 'Nach Ankunft dürfen keine pendingHops mehr offen sein');
  log(`Flotte von ${attackerPlayer.name} im Zielsystem ${destinationSystemId} angekommen (${expectedHops} Sprünge, hop-für-hop bestätigt).`);

  const hasVisitedAfter = await attackerClient.call('hasVisitedSystem', { systemId: destinationSystemId });
  assert.equal(hasVisitedAfter, true, 'Zielsystem muss nach Ankunft als besucht gelten');

  // --- Schritt 5: Krieg, Blockade, Angriff, echter Kampf-Tick -----------------
  await defenderClient.call('formBlockade', {
    fleetId: defenderFleet.id,
    anchor: { kind: 'PlanetOrbit', planetId: defenderColony.planetId },
  });
  const blockades = await attackerClient.call('blockadesInSystem', { systemId: destinationSystemId });
  assert.ok(blockades.some(bl => bl.fleetId === defenderFleet.id), 'Verteidiger-Flotte muss nach formBlockade in blockadesInSystem auftauchen');
  log(`${defenderPlayer.name} blockiert die eigene Heimatkolonie mit der Verteidigungsflotte.`);

  const statusBeforeWar = await attackerClient.call('diplomaticStatus', { otherPlayerId: defenderPlayer.id });
  assert.equal(statusBeforeWar, 'Peace', 'Zwei frisch registrierte Kommandanten müssen sich zunächst im Frieden befinden');
  await attackerClient.call('declareWar', { otherPlayerId: defenderPlayer.id });
  const statusAfterWar = await attackerClient.call('diplomaticStatus', { otherPlayerId: defenderPlayer.id });
  assert.equal(statusAfterWar, 'War', 'diplomaticStatus muss nach declareWar "War" liefern');
  log(`${attackerPlayer.name} erklärt ${defenderPlayer.name} den Krieg.`);

  await attackerClient.call('engageBattle', { attackerFleetId: attackerFleet.id, defenderFleetId: defenderFleet.id });
  const activeBattles = await attackerClient.call('activeBattles');
  const battle = activeBattles.find(bt => bt.attackerFleetId === attackerFleet.id && bt.defenderFleetId === defenderFleet.id);
  assert.ok(battle, 'engageBattle muss ein aktives Battle mit den korrekten Flotten-IDs erzeugen');
  assert.equal(battle.status, 'Active');
  assert.equal(battle.ticksResolved, 0, 'Frisch gestartetes Gefecht darf noch keinen aufgelösten Tick haben');
  assert.ok(battle.reportToken && battle.reportToken.length > 0, 'Battle muss von Anfang an einen abrufbaren reportToken besitzen');
  log(`Gefecht gestartet: ${battle.id} (reportToken=${battle.reportToken}), nextTickAt in ${battle.nextTickAt - Date.now()}ms`);

  // Erwartete Schadens-/Verlustwerte lokal nachrechnen (1:1 aus BattleCommands.java/
  // simulated-game-api.service.ts), BEVOR der Tick server-seitig auflöst.
  const [productTypes, shipTypes] = await Promise.all([attackerClient.call('productTypes'), attackerClient.call('shipTypes')]);
  const productById = new Map(productTypes.map(p => [p.id, p]));
  const shipDefById = new Map(shipTypes.map(s => [s.productTypeId, s]));
  const damageToDefender = computeSideDamage(attackerFleet.ships, defenderFleet.ships, productById, shipDefById);
  const damageToAttacker = computeSideDamage(defenderFleet.ships, attackerFleet.ships, productById, shipDefById);
  const expectedDefenderLosses = applyDamage(defenderFleet.ships, damageToDefender, productById);
  const expectedAttackerLosses = applyDamage(attackerFleet.ships, damageToAttacker, productById);
  log(`Lokal vorausberechnet – erwartete Verluste Angreifer: ${JSON.stringify(expectedAttackerLosses)}, Verteidiger: ${JSON.stringify(expectedDefenderLosses)}`);

  const resolvedBattle = await waitUntil(async () => {
    const b2 = await attackerClient.call('battle', { id: battle.id });
    return b2.ticksResolved >= 1 ? b2 : undefined;
  }, { timeoutMs: hoursToMs(8) + 30_000, intervalMs: 1000, description: 'erster Kampf-Tick tickgetrieben aufgelöst (COMBAT_TICK_HOURS)' });

  const firstTick = resolvedBattle.ticks[0];
  assert.equal(firstTick.tick, 1);
  assert.deepEqual(firstTick.attackerLosses, expectedAttackerLosses,
    `Angreiferverluste im ersten Kampf-Tick müssen exakt der lokalen Nachrechnung von computeSideDamage/applyDamage entsprechen (erwartet ${JSON.stringify(expectedAttackerLosses)}, tatsächlich ${JSON.stringify(firstTick.attackerLosses)})`);
  assert.deepEqual(firstTick.defenderLosses, expectedDefenderLosses,
    `Verteidigerverluste im ersten Kampf-Tick müssen exakt der lokalen Nachrechnung von computeSideDamage/applyDamage entsprechen (erwartet ${JSON.stringify(expectedDefenderLosses)}, tatsächlich ${JSON.stringify(firstTick.defenderLosses)})`);
  log(`Kampf-Tick 1 aufgelöst – Verluste stimmen exakt mit der Formel-Nachrechnung überein.`);
  log(`  Angreifer-Verluste: ${JSON.stringify(firstTick.attackerLosses)}`);
  log(`  Verteidiger-Verluste: ${JSON.stringify(firstTick.defenderLosses)}`);

  // Flottenbestand muss sich um exakt die verlorenen Schiffe verringert haben.
  const fleetsAfterTick = await attackerClient.call('fleets');
  const attackerFleetAfterTick = fleetsAfterTick.find(f => f.id === attackerFleet.id);
  for (const [shipId, lost] of Object.entries(expectedAttackerLosses)) {
    const before = attackerFleet.ships.find(s => s.shipProductTypeId === shipId)?.quantity ?? 0;
    const after = attackerFleetAfterTick.ships.find(s => s.shipProductTypeId === shipId)?.quantity ?? 0;
    assert.equal(after, before - lost, `Angreiferflotte: ${shipId} muss von ${before} auf ${before - lost} sinken (tatsächlich ${after})`);
  }
  log('Flottenbestand nach dem Tick stimmt mit den gemeldeten Verlusten überein.');

  // Aufräumen: Gefecht sauber per Rückzug beenden (übt gleichzeitig retreatFromBattle aus).
  if (resolvedBattle.status === 'Active') {
    await attackerClient.call('retreatFromBattle', { battleId: battle.id });
    const afterRetreat = await attackerClient.call('battle', { id: battle.id });
    assert.equal(afterRetreat.status, 'Ended');
    assert.equal(afterRetreat.outcome, 'Retreat');
    log('Gefecht per retreatFromBattle sauber beendet (outcome=Retreat).');
  }

  // --- Schritt 6: Ingame-Nachrichtensystem (Umsetzungskonzept/14_...md) -------
  // Ausschließlich Spieler-zu-Spieler ("E-Mail"), kein Gruppen-/Broadcast-Chat.
  const sendAck = await defenderClient.call('sendMessage', {
    toPlayerId: attackerPlayer.id, subject: 'Waffenstillstand?', body: 'Ziehen Sie sich zurück, sonst eskaliert das weiter.',
  });
  assert.equal(sendAck, null, 'sendMessage liefert kein Payload (void-Befehl)');

  const inboxA = await waitUntil(async () => {
    const list = await attackerClient.call('inbox');
    const m = list.find(x => x.fromPlayerId === defenderPlayer.id && x.subject === 'Waffenstillstand?');
    return m ?? undefined;
  }, { timeoutMs: 5000, intervalMs: 200, description: 'Nachricht von B erscheint in As Posteingang' });
  assert.equal(inboxA.read, false, 'Frisch empfangene Nachricht muss ungelesen sein');
  assert.equal(inboxA.body, 'Ziehen Sie sich zurück, sonst eskaliert das weiter.');
  log(`Nachricht ${defenderPlayer.name} → ${attackerPlayer.name} im Posteingang angekommen (id=${inboxA.id}), ungelesen.`);

  const sentB = await defenderClient.call('sentMessages');
  assert.ok(sentB.some(x => x.id === inboxA.id), 'Nachricht muss auch in Bs sentMessages auftauchen');

  const unreadBefore = await attackerClient.call('unreadMessageCount');
  assert.ok(unreadBefore >= 1, 'unreadMessageCount muss die neue Nachricht mitzählen');

  await attackerClient.call('markMessageRead', { id: inboxA.id });
  const inboxAAfterRead = await attackerClient.call('inbox');
  assert.equal(inboxAAfterRead.find(x => x.id === inboxA.id).read, true, 'Nachricht muss nach markMessageRead als gelesen markiert sein');
  log('Nachricht per markMessageRead korrekt als gelesen markiert.');

  // Antwort in die Gegenrichtung, um auch den zweiten Zustellweg zu prüfen.
  await attackerClient.call('sendMessage', { toPlayerId: defenderPlayer.id, subject: 'Re: Waffenstillstand?', body: 'Kommt nicht in Frage.' });
  const inboxB = await waitUntil(async () => {
    const list = await defenderClient.call('inbox');
    const m = list.find(x => x.fromPlayerId === attackerPlayer.id && x.subject === 'Re: Waffenstillstand?');
    return m ?? undefined;
  }, { timeoutMs: 5000, intervalMs: 200, description: 'Antwort von A erscheint in Bs Posteingang' });
  log(`Antwort ${attackerPlayer.name} → ${defenderPlayer.name} im Posteingang angekommen (id=${inboxB.id}).`);

  // Nachricht an sich selbst muss abgelehnt werden.
  await assert.rejects(
    () => attackerClient.call('sendMessage', { toPlayerId: attackerPlayer.id, subject: 'x', body: 'x' }),
    /nicht möglich/,
    'sendMessage an sich selbst muss mit einer Fehlermeldung abgelehnt werden',
  );
  log('sendMessage an sich selbst korrekt abgelehnt.');

  // --- Schritt 7: "Beibehalten" (Aufbewahrungsfristen) --------------------
  // Umsetzungskonzept/15_...md, Auftrag 2: ungemarkierte Einträge räumt der
  // Server nach Ablauf der Frist weg, markierte bleiben. Hier wird die
  // Befehlsmechanik geprüft (die Frist selbst dauert 7 Spieltage und wird
  // separat mit verkürzter Frist live verifiziert).
  // Geprüft aus Sicht des ABSENDERS (Postausgang) – laut MessageCommands.setMessageKeep
  // dürfen sowohl Absender als auch Empfänger das Kennzeichen setzen.
  const keepTarget = (await attackerClient.call('sentMessages')).find(m => m.id === inboxB.id);
  assert.ok(keepTarget, 'Die gesendete Nachricht muss im Postausgang des Absenders liegen');
  assert.strictEqual(keepTarget.keep, false, 'Neue Nachrichten starten ohne "Beibehalten"');
  await attackerClient.call('setMessageKeep', { id: inboxB.id, keep: true });
  const afterKeep = (await attackerClient.call('sentMessages')).find(m => m.id === inboxB.id);
  assert.strictEqual(afterKeep.keep, true, 'setMessageKeep muss das Kennzeichen setzen');
  await attackerClient.call('setMessageKeep', { id: inboxB.id, keep: false });
  const afterUnkeep = (await attackerClient.call('sentMessages')).find(m => m.id === inboxB.id);
  assert.strictEqual(afterUnkeep.keep, false, 'setMessageKeep muss das Kennzeichen auch wieder entfernen können');
  log('Nachrichten-"Beibehalten" (als Absender) gesetzt und wieder entfernt.');

  const notifications = await attackerClient.call('notifications');
  assert.ok(notifications.length > 0, 'Nach Kriegserklärung/Gefecht müssen Benachrichtigungen vorliegen');
  assert.strictEqual(notifications[0].keep, false, 'Neue Benachrichtigungen starten ohne "Beibehalten"');
  await attackerClient.call('setNotificationKeep', { id: notifications[0].id, keep: true });
  const notifAfter = (await attackerClient.call('notifications')).find(n => n.id === notifications[0].id);
  assert.strictEqual(notifAfter.keep, true, 'setNotificationKeep muss das Kennzeichen setzen');
  log(`Benachrichtigungs-"Beibehalten" gesetzt (id=${notifications[0].id}).`);

  // --- Schritt 8: Produktionstempo-Aufschlüsselung aus dem Backend ---------
  // Umsetzungskonzept/15_...md, Auftrag 3: die Transparenz-Anzeige rechnet
  // nicht mehr client-seitig, sondern bekommt alle Faktoren fertig geliefert.
  const breakdown = await attackerClient.call('colonySpeedBreakdown', { colonyId: attackerPlayer.homeworldColonyId });
  assert.ok(breakdown.population > 0, 'Aufschlüsselung muss die Bevölkerung enthalten');
  // Kein workforceFactor mehr: der frühere Tempo-Multiplikator (bis ×5) wurde mit
  // Umsetzungskonzept/19_...md abgeschafft – Bevölkerung kann die Produktion seither
  // nur noch BREMSEN (Formulas.productionHoursWithWorkforce). Übrig bleibt die
  // verfügbare Arbeitskraft als Kennzahl.
  assert.ok(breakdown.availableWorkers > 0, 'Aufschlüsselung muss die verfügbare Arbeitskraft enthalten');
  assert.ok(breakdown.buildingSpeedFactor > 0, 'Aufschlüsselung muss den Gebäude-Tempofaktor enthalten');
  assert.ok(breakdown.buildingUpgrades.length > 0, 'Aufschlüsselung muss Ausbau-Vorschauen enthalten');
  assert.ok(Object.keys(breakdown.concentrationFactorByProduct).length > 0, 'Aufschlüsselung muss Fördergüte-Faktoren enthalten');
  log(`Tempo-Aufschlüsselung vom Backend: Bevölkerung ${breakdown.population.toFixed(0)}, `
    + `verfügbare Arbeitskraft ${breakdown.availableWorkers.toFixed(0)}, Industrie Stufe ${breakdown.industryLevel} `
    + `(×${breakdown.buildingSpeedFactor}), Blackout=${breakdown.blackout}, `
    + `${breakdown.buildingUpgrades.length} Ausbau-Vorschauen, `
    + `${Object.keys(breakdown.concentrationFactorByProduct).length} Fördergüte-Faktoren.`);

  a.close();
  b.close();
  log('ALLE PRÜFUNGEN BESTANDEN.');
}

main().catch(err => {
  console.error(`[e2e] FEHLGESCHLAGEN: ${err.stack ?? err}`);
  process.exit(1);
});
