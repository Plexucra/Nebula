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
  return product.baseWorkforceRequired * product.baseProductionHours;
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
class GameClient {
  constructor(label) {
    this.label = label;
    this.counter = 0;
    this.pending = new Map();
    this.pushes = [];
    this.ws = new WebSocket(WS_URL);
    this.ws.addEventListener('message', (ev) => this.onMessage(JSON.parse(ev.data)));
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
    return new Promise((resolve, reject) => {
      const requestId = `${this.label}-${++this.counter}`;
      this.pending.set(requestId, { resolve, reject });
      this.ws.send(JSON.stringify({ type, requestId, payload }));
    });
  }

  close() {
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

  const coloniesA = await a.call('colonies');
  const colonyA = coloniesA.find(c => c.id === playerA.homeworldColonyId);
  assert.ok(colonyA, 'Heimatkolonie von A muss in colonies() auftauchen');
  const coloniesB = await b.call('colonies');
  const colonyB = coloniesB.find(c => c.id === playerB.homeworldColonyId);
  assert.ok(colonyB, 'Heimatkolonie von B muss in colonies() auftauchen');

  // --- Schritt 2a: Bebauung – Werft ausbauen, tickgetriebene Fertigstellung ---
  const buildingsBefore = await a.call('buildings', { colonyId: colonyA.id });
  const shipyardBefore = buildingsBefore.find(x => x.typeId === 'b_shipyard');
  assert.ok(shipyardBefore, 'Startkolonie muss eine Werft besitzen (world-seed: Stufe 3)');
  const shipyardLevelBefore = shipyardBefore.level;
  log(`Werft vor Ausbau: Stufe ${shipyardLevelBefore}`);

  await a.call('queueBuilding', { colonyId: colonyA.id, buildingTypeId: 'b_shipyard' });
  const upgraded = await waitUntil(async () => {
    const list = await a.call('buildings', { colonyId: colonyA.id });
    const sy = list.find(x => x.typeId === 'b_shipyard');
    return sy.level > shipyardLevelBefore ? sy : undefined;
  }, { timeoutMs: 60_000, description: 'Werft-Ausbau tickgetrieben fertiggestellt' });
  assert.equal(upgraded.level, shipyardLevelBefore + 1, 'Werft muss nach Fertigstellung genau eine Stufe höher stehen');
  assert.equal(upgraded.pendingOrder, null, 'Nach Fertigstellung darf kein pendingOrder mehr offen sein');
  log(`Werft-Ausbau tickgetrieben fertiggestellt: Stufe ${shipyardLevelBefore} → ${upgraded.level}`);

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

  // --- Schritt 3: Werft-Warteschlange anstoßen (nur Befehlsmechanik) ---------
  const shipyardQueueBefore = await a.call('shipyardQueue', { colonyId: colonyA.id });
  assert.equal(shipyardQueueBefore.length, 0, 'Werft-Warteschlange sollte zu diesem Zeitpunkt leer sein');
  await a.call('queueShip', {
    colonyId: colonyA.id, shipProductTypeId: 'p_corvette', quantity: 1,
    autoProduceMissing: true, requeueOnComplete: false,
  });
  const shipEntry = await waitUntil(async () => {
    const q = await a.call('shipyardQueue', { colonyId: colonyA.id });
    const e = q.find(x => x.shipProductTypeId === 'p_corvette');
    return e && e.status === 'running' ? e : undefined;
  }, { timeoutMs: 10_000, intervalMs: 200, description: 'Werft-Auftrag p_corvette startet' });
  assert.ok(shipEntry.plan.totalHours > 0, 'Werft-Auftrag muss einen berechneten ChainPlan haben');
  log(`Werft-Auftrag p_corvette×1 läuft (Befehlsmechanik verifiziert), ChainPlan.totalHours=${shipEntry.plan.totalHours.toFixed(1)} `
    + '– Fertigstellung wird NICHT abgewartet (siehe Kopfkommentar); Kampf nutzt die bereits vorhandene Startflotte.');

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

  a.close();
  b.close();
  log('ALLE PRÜFUNGEN BESTANDEN.');
}

main().catch(err => {
  console.error(`[e2e] FEHLGESCHLAGEN: ${err.stack ?? err}`);
  process.exit(1);
});
