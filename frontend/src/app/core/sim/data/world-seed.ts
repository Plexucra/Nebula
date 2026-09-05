import {
  Building, ChainPlan, Colony, Fleet, Gateway, GroundForceGroup, Id, Npc, Planet, PlanetStats, PlanetType, Player,
  Population, PopulationMoneySupplyState, ProductionQueueEntry, System, Wallet, WarehouseEntry,
} from '../../models';
import { nextId } from '../id';
import { now } from '../clock';
import { RAW_RESOURCES } from './product-catalog';
import { findBuildingType } from './building-catalog';
import { generateGalaxy } from './galaxy-generator';
import { seededRandom, shuffle, Rng } from '../rng';
import { PLANET_TYPES, rangesForPlanetType } from './planet-type-profiles';

export interface WorldSeed {
  player: Player;
  systems: System[];
  planets: Planet[];
  colonies: Colony[];
  planetStats: PlanetStats[];
  populations: Population[];
  moneySupplyStates: PopulationMoneySupplyState[];
  wallets: Wallet[];
  buildings: Building[];
  warehouse: WarehouseEntry[];
  productionQueue: ProductionQueueEntry[];
  gateways: Gateway[];
  npcs: Npc[];
  fleets: Fleet[];
  groundForceGroups: GroundForceGroup[];
}

/** Platzhalter für einen noch nicht berechneten `ChainPlan`, siehe `SimulatedGameApiService.planChain`. */
const EMPTY_CHAIN_PLAN: ChainPlan = { totalHours: 0, steps: [], feasible: true };

/**
 * Grundnahrung, das ohne manuelles Zutun laufend benötigte Grundkonsumgut
 * (Grundmedizin/Unterhaltungselektronik bewusst NICHT dabei – bleiben Sache
 * des Spielers, siehe unten). Die Heimatkolonie startet mit einem
 * sequentiellen Produktionsauftrag dafür (automatisch mitproduzierte
 * Vorprodukte, nach Fertigstellung erneut eingereiht – siehe
 * Konzeption/Umsetzungskonzept/10_...md, §8), damit von Anfang an eine
 * Grundversorgung läuft statt dass die Bevölkerung erst auf manuell
 * gestartete Produktion wartet. Der lokale Verkauf ist NICHT Teil des Seeds
 * (bewusste Verhaltensänderung) – der Spieler richtet ihn selbst über
 * "Anbieten" im Lagerbestand ein.
 *
 * `CONSUMER_GOODS_ORDER` in `simulated-game-api.service.ts` (Bevölkerungs-
 * Konsum) führt zusätzlich Grundmedizin/Unterhaltungselektronik – das ist
 * bewusst KEIN Widerspruch: die Bevölkerung fragt beide weiterhin nach, nur
 * startet die Kolonie sie nicht automatisch mit, der Spieler richtet sie
 * bei Bedarf selbst ein (Tab „Produktion").
 */
const STARTER_CONSUMER_GOODS: Id[] = ['p_grundnahrung'];
const STARTER_CONSUMER_GOODS_QUANTITY = 5;
/**
 * Hält das Energienetz nach Aufbrauchen der versiegelten Startreserve
 * (`SEALED_ELERIUM_RESERVE_HOME`) laufend nachversorgt, damit kein
 * Blackout entsteht, sobald sie zur Neige geht – 1 reicht, weil der
 * laufende Verbrauch selbst sehr gering ist (siehe
 * `ELERIUM_UPKEEP_PER_POWERGRID_LEVEL` in `simulated-game-api.service.ts`).
 */
const STARTER_ELERIUM_QUANTITY = 1;
/** Anfangsbestand im Lager der Heimatkolonie – überbrückt die Zeit, bis die Start-Auftragsliste (s. o.) das erste Mal nachliefert. */
const STARTER_WAREHOUSE_STOCK: Partial<Record<Id, number>> = { p_grundnahrung: 50 };

function starterProductionQueue(colonyId: Id): ProductionQueueEntry[] {
  const entries: { productTypeId: Id; quantity: number }[] = [
    ...STARTER_CONSUMER_GOODS.map(productTypeId => ({ productTypeId, quantity: STARTER_CONSUMER_GOODS_QUANTITY })),
    { productTypeId: 'p_elerium_stabil', quantity: STARTER_ELERIUM_QUANTITY },
  ];
  return entries.map(({ productTypeId, quantity }) => ({
    id: nextId('pq'), colonyId, productTypeId, quantity,
    autoProduceMissing: true, requeueOnComplete: true,
    status: 'queued', stoppedReasonCode: null, plan: EMPTY_CHAIN_PLAN, startedAt: null, endsAt: null,
  }));
}

/**
 * Versiegelte Eleriumreserve, mit der jede neu gegründete Kolonie startet
 * (Nebula_Planetentypen_..., §8): "verhindert einen Startstillstand,
 * ersetzt aber keine laufende Förderung" – ohne sie wäre das Energienetz
 * ab dem allerersten Tick im Blackout, bis die Kolonie die erste Charge
 * Stabilisiertes Elerium (Ebene 1) produziert hat. Betriebsstoff ist
 * Stabilisiertes Elerium, nicht die tiefer in der Kette liegende
 * Eleriumenergiezelle – siehe `POWERGRID_FUEL_PRODUCT_ID` in
 * `simulated-game-api.service.ts`.
 */
const SEALED_ELERIUM_RESERVE_HOME = 25;
const SEALED_ELERIUM_RESERVE_NPC = 10;

function eleriumReserveEntry(colonyId: string, quantity: number): WarehouseEntry {
  return { colonyId, productTypeId: 'p_elerium_stabil', quantity };
}

const PLANET_NAMES_HOME = ['Aurelia Prime', 'Kessar', 'Vantis', 'Thal Minor', 'Rho Cindra'];

const GALAXY_SYSTEM_COUNT = 200;
const NPC_COUNT = 10;

const SYSTEM_NAME_POOL = [
  'Aurelia', "Kepler's Reach", 'Thessaly', 'Drakon-Weite', 'Vey Corva', 'Halcyon Rand',
  'Praxis Gate', 'Corvin Öde', 'Nashira', 'Talvex', 'Ophir Rand', 'Sirenum',
  'Kestrel-Feld', 'Meridian Tor', 'Borea Vor', 'Xantha', 'Rigel Außenposten',
  'Vantor Bogen', 'Elyra Senke', 'Cassiel', 'Drift von Ilun', 'Perath',
  'Solace Rand', 'Nocturn Bucht', 'Amaris', 'Kroven Riff', 'Vela Passage',
  'Tessark', 'Orinth', 'Fahrun Weite',
];

/** Bei mehr Systemen als Namen im Pool (z. B. `GALAXY_SYSTEM_COUNT` > `SYSTEM_NAME_POOL.length`) hängt ein Zähler an, statt exakte Namensdopplungen zu erzeugen. */
function systemNameAt(names: string[], i: number): string {
  const base = names[i % names.length];
  const cycle = Math.floor(i / names.length);
  return cycle === 0 ? base : `${base} ${cycle + 1}`;
}

/**
 * Wie `systemNameAt`, nur für den Einzelfall bei `createAdditionalPlayerSeed`
 * (ein neues System zur Laufzeit statt der gesamten Galaxie auf einmal):
 * bevorzugt einen im GESAMTEN aktuellen Bestand noch unbenutzten Poolnamen,
 * sonst denselben Zähler-Mechanismus wie `systemNameAt` statt der früheren
 * Notlösung `Heimatsystem ${n}` – bei `GALAXY_SYSTEM_COUNT` = 200 ist der
 * Pool (30 Namen) schon durch die Startgalaxie mehrfach durchzyklt, sodass
 * "noch unbenutzt" für neu registrierte Kommandanten praktisch nie mehr
 * zutrifft und sonst JEDES weitere Heimatsystem diese unschöne Notlösung
 * bekäme statt eines echten Namens.
 */
function pickAdditionalSystemName(usedNames: Set<string>, rnd: Rng): string {
  const strictlyAvailable = SYSTEM_NAME_POOL.filter(n => !usedNames.has(n));
  if (strictlyAvailable.length > 0) return strictlyAvailable[Math.floor(rnd() * strictlyAvailable.length)];
  const base = SYSTEM_NAME_POOL[Math.floor(rnd() * SYSTEM_NAME_POOL.length)];
  let cycle = 2;
  while (usedNames.has(`${base} ${cycle}`)) cycle++;
  return `${base} ${cycle}`;
}

const FACTION_FLAVORS = [
  'unabhängige Kolonisten', 'Grenzsiedlung', 'unerforscht', 'kleine Kolonie',
  'verlassenes System', 'lokale Miliz',
];

const NPC_PLANET_NAMES = [
  'Varek', 'Ilyra', 'Sohrat', 'Kellin', 'Draveth', 'Anthys', 'Woronok', 'Petriv',
  'Cardessa', 'Halvorn', 'Junai', 'Merrek', 'Osvalt', 'Quinnara',
];

const NPC_NAME_POOL = [
  'Direktorin Assan Korr', 'Verwalter Beno Yatt', 'Vorsitzende Ilse Marren',
  'Kommissar Dov Rehn', 'Ratsherrin Priya Thessin', 'Verwalter Okonkwo Bass',
  'Direktor Lian Fessu', 'Vorsteherin Marta Ödberg', 'Kommissarin Ayen Solvik',
  'Verwalter Timo Achra',
];

function buildInstance(colonyId: string, typeId: string, level: number): Building {
  return { id: nextId('bld'), colonyId, typeId, level, pendingOrder: null, activationState: null, activationCompletesAt: null };
}

function freighterFleet(ownerId: string, colonyId: string, planetId: string, systemId: string, name: string): Fleet {
  return {
    id: nextId('flt'), ownerId, name, locationType: 'ColonyOrbit', locationColonyId: colonyId, locationPlanetId: planetId,
    systemId, status: 'Stationed', ships: [{ shipProductTypeId: 'p_freighter', quantity: 1 }],
    cargo: [], destinationSystemId: null, pendingHops: [], departedAt: null, arrivesAt: null,
  };
}

function randInt(rnd: Rng, min: number, max: number): number {
  return min + Math.floor(rnd() * (max - min + 1));
}

/**
 * Startflotte mit ALLEN drei Kampfklassen (Korvette/Zerstörer/Kreuzer,
 * siehe Mechanik/03_..., §2) – deckt den vollen Konterkreis
 * (Korvette>Kreuzer>Zerstörer>Korvette) ab, damit sich ein Raumgefecht von
 * Anfang an ausprobieren lässt, ohne erst eine Werft hochziehen zu müssen.
 * Stückzahlen je Klasse bewusst zufällig und deutlich unterschiedlich
 * (Korvette 2-8, Zerstörer 1-5, Kreuzer 1-3 – grob umgekehrt proportional
 * zu ihrem Produktionsaufwand/militärischen Wert, siehe `ship-catalog.ts`)
 * statt symmetrisch 1:1:1: erst bei asymmetrischen Flottenzusammen-
 * setzungen lässt sich der Kontermultiplikator (Mechanik/04_..., §4) beim
 * Testen eines Gefechts wirklich beobachten – bei zwei spiegelgleichen
 * 1:1:1-Flotten heben sich Vor-/Nachteile pro Tick gegenseitig auf.
 */
function combatFleet(ownerId: string, colonyId: string, planetId: string, systemId: string, name: string, rnd: Rng): Fleet {
  return {
    id: nextId('flt'), ownerId, name, locationType: 'ColonyOrbit', locationColonyId: colonyId, locationPlanetId: planetId,
    systemId, status: 'Stationed',
    ships: [
      { shipProductTypeId: 'p_corvette', quantity: randInt(rnd, 2, 8) },
      { shipProductTypeId: 'p_destroyer', quantity: randInt(rnd, 1, 5) },
      { shipProductTypeId: 'p_cruiser', quantity: randInt(rnd, 1, 3) },
    ],
    cargo: [], destinationSystemId: null, pendingHops: [], departedAt: null, arrivesAt: null,
  };
}

/**
 * Kleine Boden-Garnison mit Soldaten und je einem Bestand aller drei
 * Waffenträgerklassen (Mechanik/05_..., §3) – deckt ebenfalls den vollen
 * Konterkreis ab. 3 aktive Soldaten kommandieren bei `DRONES_PER_SOLDIER = 5`
 * (`simulated-game-api.service.ts`) genau 15 Drohnen; die 15 gesäten
 * Drohnen (5 je Klasse) sind damit von Anfang an vollständig aktiv/
 * kampffähig, ohne dass erst `recalcCrewing` nachjustieren müsste.
 */
function starterGroundForceGroup(ownerId: Id, colonyId: Id): GroundForceGroup {
  return {
    id: nextId('gfg'), ownerId, colonyId,
    units: [
      { unitProductTypeId: 'p_soldier', activeCount: 3, reserveCount: 2 },
      { unitProductTypeId: 'p_drone_light', activeCount: 5, reserveCount: 0 },
      { unitProductTypeId: 'p_drone_medium', activeCount: 5, reserveCount: 0 },
      { unitProductTypeId: 'p_drone_heavy', activeCount: 5, reserveCount: 0 },
    ],
  };
}

function randomPlanetType(rnd: Rng): PlanetType {
  return PLANET_TYPES[Math.floor(rnd() * PLANET_TYPES.length)];
}

/**
 * Fördergüte-Profil (0-100 je Rohstoff) aus dem Fördergüte-Bereich des
 * Planetentyps, siehe Nebula_Planetentypen_Rohstoffprofile_
 * Produktionsbaum.md, §7.1. Vereinfachung ggü. Vorlage: der "Clusterwert"
 * wird hier je Planet statt je Sternsystem gezogen – die dort beschriebene
 * Zwei-Phasen-Erzeugung mit regionalem Cluster, Nachbarschaftsvalidierung
 * und Signatur-/Mangelrohstoffen (§7.2-7.3) ist noch nicht umgesetzt.
 */
function concentrationProfileForType(type: PlanetType, rnd: Rng): Planet['resourceConcentration'] {
  return rangesForPlanetType(type).map(({ resourceTypeId, range: [min, max] }) => {
    const clusterValue = rnd() * 100;
    const localRandom = rnd();
    const localDeviation = rnd() * 10 - 5;
    const typwert = min + (max - min) * (0.65 * (clusterValue / 100) + 0.35 * localRandom);
    const foerdergute = Math.round(Math.max(min, Math.min(max, typwert + localDeviation)));
    return { resourceTypeId, concentration: foerdergute };
  });
}

/**
 * Heimatplanet-Mindestfördergüten (Nebula_Planetentypen_..., §8) – ein
 * Spieler soll nie an fehlender Grundversorgung scheitern, unabhängig
 * davon, was die Typ-Zufallsstreuung sonst ergeben hätte.
 */
const HOMEWORLD_MINIMUMS: Record<string, number> = {
  res_eis: 80, res_atmosphaere: 80, res_salz: 60, res_kohlenstoff: 50, res_silikat: 45,
  res_leichtmetall: 30, res_kohlenwasserstoff: 30, res_ferrometall: 25, res_leitmetall: 15,
  res_technometall: 12, res_elerium: 15,
};

function applyHomeworldMinimums(conc: Planet['resourceConcentration']): Planet['resourceConcentration'] {
  return conc.map(c => ({
    ...c,
    concentration: Math.max(c.concentration, HOMEWORLD_MINIMUMS[c.resourceTypeId] ?? 0),
  }));
}

function specialtyProductFor(resourceTypeId: string): string {
  return RAW_RESOURCES.find(p => p.resourceProfile[0]?.resourceTypeId === resourceTypeId)?.id ?? RAW_RESOURCES[0].id;
}

/**
 * Wählt eine Wohnkomplex-Stufe, die zusammen mit `powergridLevel` die
 * Startbevölkerung komfortabel deckt (Ziel ~110 % Infrastruktur, siehe
 * Konzeption/07_..., §4: Bevölkerung ist Ergebnis realer Entwicklung –
 * eine Startwelt, die von Anfang an über ihrer eigenen
 * Infrastrukturkapazität liegt, widerspricht diesem Prinzip und erzeugt
 * chronischen Bevölkerungsschwund, siehe NPC-Stresstest-Befund).
 */
function habitatLevelFor(population: number, powergridLevel: number, targetPct = 1.1): number {
  const habitatCap = findBuildingType('b_habitat').populationCapacityPerLevel ?? 60;
  const powergridCap = findBuildingType('b_powergrid').populationCapacityPerLevel ?? 25;
  const remaining = population * targetPct - powergridLevel * powergridCap;
  return Math.max(1, Math.ceil(remaining / habitatCap));
}

/**
 * Wählt `NPC_COUNT` Systeme als zusammenhängenden Nachbarschafts-Cluster
 * statt verstreut über die ganze Galaxie: Breitensuche (BFS) über den
 * Gateway-Graphen ab einem zufälligen Startsystem, wodurch die nächsten
 * erreichbaren Systeme zuerst gewählt werden. Nur so können NPCs
 * überhaupt direkte Gateway-Nachbarn sein und eine lokale Wirtschaft
 * bilden (siehe Auftrag).
 */
function selectNpcClusterIndices(neighborsByIndex: number[][], eligible: (i: number) => boolean, rnd: Rng, count: number): number[] {
  const n = neighborsByIndex.length;
  const eligibleIndices = Array.from({ length: n }, (_, i) => i).filter(eligible);
  const seed = eligibleIndices[Math.floor(rnd() * eligibleIndices.length)];

  const visited = new Set<number>([seed]);
  const order: number[] = [];
  const queue = [seed];
  let head = 0;
  while (head < queue.length && order.length < count) {
    const cur = queue[head++];
    if (eligible(cur)) order.push(cur);
    for (const nb of neighborsByIndex[cur]) {
      if (!visited.has(nb)) { visited.add(nb); queue.push(nb); }
    }
  }
  // Fallback, falls das Cluster um den Zufalls-Seed (z. B. am Rand der
  // Galaxie) nicht genug erreichbare Systeme liefert.
  if (order.length < count) {
    for (const i of shuffle(eligibleIndices, rnd)) {
      if (!order.includes(i)) order.push(i);
      if (order.length >= count) break;
    }
  }
  return order.slice(0, count);
}

/**
 * Erzeugt `NPC_COUNT` nicht-kriegerische NPC-Kolonien (siehe Auftrag:
 * "verhalten sich wie Spieler, bauen aber keine Angriffsflotten/
 * Bodentruppen") als zusammenhängenden Nachbarschafts-Cluster, damit
 * eine stabile lokale Wirtschaft überhaupt möglich ist. Innerhalb des
 * Clusters stimmen benachbarte NPCs ihre Spezialisierung direkt
 * miteinander ab (kein doppeltes Angebot unter direkten Nachbarn),
 * bewusst kleiner gestartet als die Heimatwelt des Spielers.
 */
function spawnNpcs(systems: System[], neighborsByIndex: number[][], rnd: Rng, t: number): {
  npcs: Npc[]; colonies: Colony[]; planets: Planet[]; planetStats: PlanetStats[];
  populations: Population[]; moneySupplyStates: PopulationMoneySupplyState[];
  wallets: Wallet[]; buildings: Building[]; warehouse: WarehouseEntry[]; fleets: Fleet[];
} {
  const eligible = (i: number) => !systems[i].isHomeSystem && !systems[i].isTradeHub;
  const clusterIndices = selectNpcClusterIndices(neighborsByIndex, eligible, rnd, NPC_COUNT);
  const clusterSet = new Set(clusterIndices);
  const planetNames = shuffle(NPC_PLANET_NAMES, rnd);
  const npcNames = shuffle(NPC_NAME_POOL, rnd);

  const npcs: Npc[] = [];
  const colonies: Colony[] = [];
  const planets: Planet[] = [];
  const planetStats: PlanetStats[] = [];
  const populations: Population[] = [];
  const moneySupplyStates: PopulationMoneySupplyState[] = [];
  const wallets: Wallet[] = [];
  const buildings: Building[] = [];
  const warehouse: WarehouseEntry[] = [];
  const fleets: Fleet[] = [];

  // Planetentyp + Konzentrationsprofile vorab erzeugen, damit die
  // Spezialisierungswahl unten bereits alle Nachbarprofile im Cluster kennt.
  const typeByIndex = new Map(clusterIndices.map(i => [i, randomPlanetType(rnd)]));
  const concByIndex = new Map(clusterIndices.map(i => [i, concentrationProfileForType(typeByIndex.get(i)!, rnd)]));
  const specialtyByIndex = new Map<number, string>();

  clusterIndices.forEach((sysIndex, i) => {
    const system = systems[sysIndex];
    const conc = concByIndex.get(sysIndex)!;
    const planetType = typeByIndex.get(sysIndex)!;

    // Direkte Gateway-Nachbarn dieses Systems, die ebenfalls zum
    // NPC-Cluster gehören und bereits eine Spezialisierung gewählt haben.
    const neighborSpecialties = new Set(
      neighborsByIndex[sysIndex].filter(nb => clusterSet.has(nb) && specialtyByIndex.has(nb))
        .map(nb => specialtyByIndex.get(nb)!),
    );
    const sortedByConcentration = [...conc].sort((a, b) => b.concentration - a.concentration);
    const chosen = sortedByConcentration.find(c => !neighborSpecialties.has(specialtyProductFor(c.resourceTypeId)));
    const specialtyProductId = specialtyProductFor((chosen ?? sortedByConcentration[0]).resourceTypeId);
    specialtyByIndex.set(sysIndex, specialtyProductId);

    const planet: Planet = {
      id: nextId('pla'),
      systemId: system.id,
      name: planetNames[i % planetNames.length],
      size: (['Klein', 'Mittel', 'Groß', 'Riesig'] as const)[Math.floor(rnd() * 4)],
      type: planetType,
      buildCapacity: 45 + Math.floor(rnd() * 30),
      resourceConcentration: conc,
      orbitIndex: 0,
    };
    system.planetIds = [planet.id];

    const npc: Npc = {
      id: nextId('npc'),
      name: npcNames[i % npcNames.length],
      homeColonyId: '',
      homeSystemId: system.id,
      specialtyProductId,
    };
    const colony: Colony = {
      id: nextId('col'), planetId: planet.id, systemId: system.id, ownerId: npc.id,
      name: `${planet.name}-Kolonie`, foundedAt: t, isHomeworld: true,
    };
    npc.homeColonyId = colony.id;

    const population = 150 + Math.floor(rnd() * 200);
    const powergridLevel = 1;
    const habitatLevel = habitatLevelFor(population, powergridLevel);
    planets.push(planet);
    npcs.push(npc);
    colonies.push(colony);
    planetStats.push({
      colonyId: colony.id, infrastructurePct: 105 + rnd() * 10, securityPct: 0,
      standardOfLivingPct: 60 + rnd() * 20, loyaltyPct: 55 + rnd() * 15, lastRecalculatedAt: t,
    });
    populations.push({ colonyId: colony.id, currentCount: population, growthRatePerInterval: 0 });
    moneySupplyStates.push({ planetId: planet.id, historicalPeakPopulation: population, lastPopulation: population });
    wallets.push({ id: nextId('wal'), ownerType: 'Player', ownerId: npc.id, balance: 2500 + Math.floor(rnd() * 2000) });
    wallets.push({ id: nextId('wal'), ownerType: 'Population', ownerId: colony.id, balance: 300 + Math.floor(rnd() * 300) });
    buildings.push(
      buildInstance(colony.id, 'b_habitat', habitatLevel),
      buildInstance(colony.id, 'b_powergrid', powergridLevel),
      buildInstance(colony.id, 'b_industry', 1),
    );
    warehouse.push(eleriumReserveEntry(colony.id, SEALED_ELERIUM_RESERVE_NPC));
    // Jeder NPC startet mit einem Frachter – Grundvoraussetzung für Handel
    // über die eigene Kolonie hinaus (Konzeption/05_..., §9).
    fleets.push(freighterFleet(npc.id, colony.id, planet.id, system.id, `Handelsflotte ${colony.name}`));
  });

  return { npcs, colonies, planets, planetStats, populations, moneySupplyStates, wallets, buildings, warehouse, fleets };
}

interface HomeworldBundle {
  player: Player;
  /** Der Heimatplaneten-Cluster (`PLANET_NAMES_HOME.length` Himmelskörper, nur der erste ist besiedelt). */
  planets: Planet[];
  colony: Colony;
  planetStats: PlanetStats;
  population: Population;
  moneySupplyState: PopulationMoneySupplyState;
  wallets: Wallet[];
  buildings: Building[];
  warehouse: WarehouseEntry[];
  productionQueue: ProductionQueueEntry[];
  /** Handelsflotte (1 Frachter) + Kampfflotte (je 1 Schiff der drei Klassen, siehe `combatFleet`). */
  fleets: Fleet[];
  groundForceGroup: GroundForceGroup;
}

/**
 * Baut EINEN Kommandanten samt Heimatplaneten-Cluster, Startkolonie,
 * Gebäuden, Wallets und Start-Auftragsliste – unabhängig davon, ob das
 * zugehörige Heimatsystem Teil einer brandneuen Galaxie ist
 * (`createWorldSeed`) oder nachträglich in eine bestehende eingefügt wird
 * (`createAdditionalPlayerSeed`). `homeSystemId` muss vom Aufrufer bereits
 * feststehen, da sowohl die Heimatplaneten als auch die Kolonie darauf
 * verweisen.
 */
function buildHomeworldBundle(commanderName: string, homeworldName: string, homeSystemId: Id, rnd: Rng, t: number): HomeworldBundle {
  const player: Player = {
    id: nextId('ply'),
    name: commanderName,
    homeSystemId,
    homeworldColonyId: '', // wird unten gesetzt
    createdAt: t,
  };

  const planets: Planet[] = PLANET_NAMES_HOME.map((name, i) => {
    // Der Spieler startet auf einem temperierten Biosphärenplaneten
    // (Nebula_Planetentypen_..., §8) – die übrigen Himmelskörper im
    // Heimatsystem sind zunächst unbesiedelt und dürfen beliebige Typen sein.
    const planetType: PlanetType = i === 0 ? 'TemperierterBiosphaerenplanet' : randomPlanetType(rnd);
    let conc = concentrationProfileForType(planetType, rnd);
    if (i === 0) conc = applyHomeworldMinimums(conc);
    return {
      id: nextId('pla'),
      systemId: homeSystemId,
      name,
      size: (['Klein', 'Mittel', 'Groß', 'Riesig'] as const)[Math.floor(rnd() * 4)],
      type: planetType,
      buildCapacity: i === 0 ? 90 : 55 + Math.floor(rnd() * 30),
      resourceConcentration: conc,
      orbitIndex: i,
    };
  });

  const colony: Colony = {
    id: nextId('col'),
    planetId: planets[0].id,
    systemId: homeSystemId,
    ownerId: player.id,
    name: homeworldName,
    foundedAt: t,
    isHomeworld: true,
  };
  player.homeworldColonyId = colony.id;

  const homePopulationCount = 420;
  const homePowergridLevel = 4;
  const homeHabitatLevel = habitatLevelFor(homePopulationCount, homePowergridLevel);

  const planetStats: PlanetStats = {
    colonyId: colony.id,
    infrastructurePct: 110,
    securityPct: 100,
    standardOfLivingPct: 100,
    loyaltyPct: 78,
    lastRecalculatedAt: t,
  };

  const population: Population = { colonyId: colony.id, currentCount: homePopulationCount, growthRatePerInterval: 0 };
  const moneySupplyState: PopulationMoneySupplyState = { planetId: planets[0].id, historicalPeakPopulation: homePopulationCount, lastPopulation: homePopulationCount };

  const playerWallet: Wallet = { id: nextId('wal'), ownerType: 'Player', ownerId: player.id, balance: 6500 };
  const popWallet: Wallet = { id: nextId('wal'), ownerType: 'Population', ownerId: colony.id, balance: 900 };

  const buildings: Building[] = [
    buildInstance(colony.id, 'b_habitat', homeHabitatLevel),
    buildInstance(colony.id, 'b_powergrid', homePowergridLevel),
    // Industriekomplex/Werft bewusst höher als ein absolutes Minimum (siehe
    // Umsetzungskonzept/12_...md, "10-Spieler-Arbeitsteilungs-Meilenstein"):
    // erst ab hier ist der Bau eines ersten Frachters in Arbeitsteilung
    // innerhalb einer Spielwoche überhaupt in Reichweite, ohne dass jede
    // beteiligte Kolonie zusätzlich noch selbst erst mehrere Gebäudestufen
    // ausbauen müsste, bevor die eigentliche Produktion beginnen kann.
    buildInstance(colony.id, 'b_industry', 4),
    buildInstance(colony.id, 'b_shipyard', 3),
    buildInstance(colony.id, 'b_academy', 1),
  ];
  const warehouse: WarehouseEntry[] = [
    eleriumReserveEntry(colony.id, SEALED_ELERIUM_RESERVE_HOME),
    ...Object.entries(STARTER_WAREHOUSE_STOCK).map(([productTypeId, quantity]) => ({ colonyId: colony.id, productTypeId, quantity: quantity! })),
  ];

  // Frachter ab Spielbeginn – ohne eigene Transportkapazität ist kein
  // Handel über die eigene Kolonie hinaus möglich (Konzeption/05_..., §9).
  const freighter = freighterFleet(player.id, colony.id, colony.planetId, homeSystemId, `Handelsflotte ${colony.name}`);
  // Kampfflotte mit allen drei Klassen (Korvette/Zerstörer/Kreuzer), stark
  // unterschiedliche Stückzahlen je Klasse (siehe `combatFleet`) – ermöglicht
  // sofortiges Ausprobieren des Kampfsystems inkl. Kontermultiplikator ohne
  // erst eine Werft hochziehen zu müssen (Mechanik/04_..., Konterzyklus).
  const combat = combatFleet(player.id, colony.id, colony.planetId, homeSystemId, `Kampfflotte ${colony.name}`, rnd);
  const groundForceGroup = starterGroundForceGroup(player.id, colony.id);

  return {
    player, planets, colony, planetStats, population, moneySupplyState,
    wallets: [playerWallet, popWallet], buildings, warehouse,
    productionQueue: starterProductionQueue(colony.id), fleets: [freighter, combat], groundForceGroup,
  };
}

export function createWorldSeed(commanderName: string, homeworldName: string): WorldSeed {
  const rnd = seededRandom(1337);
  const t = now();

  // --- Galaxie-Topologie ---------------------------------------------------
  const galaxy = generateGalaxy(GALAXY_SYSTEM_COUNT, rnd);
  const names = shuffle(SYSTEM_NAME_POOL, rnd);
  const systemIds = galaxy.positions.map(() => nextId('sys'));
  const gatewayIds = galaxy.positions.map(() => nextId('gw'));
  const tradeHubSet = new Set(galaxy.tradeHubIndices);
  const homeIndex = galaxy.centralIndex;

  const home = buildHomeworldBundle(commanderName, homeworldName, systemIds[homeIndex], rnd, t);

  const systems: System[] = galaxy.positions.map((pos, i) => {
    const isHome = i === homeIndex;
    const isHub = tradeHubSet.has(i);
    return {
      id: systemIds[i],
      name: isHome ? 'Aurelia-System' : systemNameAt(names, i),
      x: pos.x,
      y: pos.y,
      planetIds: isHome ? home.planets.map(p => p.id) : [],
      gatewayId: gatewayIds[i],
      isHomeSystem: isHome,
      isTradeHub: isHub,
      factionFlavor: isHome
        ? 'Heimatsystem'
        : isHub
          ? 'Sektorale Handelsstation (Handelsgilde)'
          : FACTION_FLAVORS[Math.floor(rnd() * FACTION_FLAVORS.length)],
    };
  });

  // --- Gateways --------------------------------------------------------------
  // ALLE Gateways starten von Anfang an uneingeschränkt aktiv – kein
  // Erforschen/Entdecken/Aktivieren mehr nötig (bewusste Vereinfachung
  // gegenüber der früher hier dokumentierten Konzeption/08_..., §7: das
  // frühe Spiel soll nicht durch Warten aufs eigene Gateway ausgebremst
  // werden, sondern Kommandanten sollen von Beginn an frei mit ihren
  // Flotten durchs gesamte bekannte Netz reisen können).
  const gateways: Gateway[] = galaxy.positions.map((_, i) => ({
    id: gatewayIds[i],
    systemId: systemIds[i],
    state: 'Active',
    discoveredAt: t,
    activatedAt: t,
    activatingCompletesAt: null,
    reachableSystemIds: galaxy.neighbors[i].map(j => systemIds[j]),
  }));

  // --- NPCs ------------------------------------------------------------------
  const npcData = spawnNpcs(systems, galaxy.neighbors, rnd, t);

  return {
    player: home.player,
    systems,
    planets: [...home.planets, ...npcData.planets],
    colonies: [home.colony, ...npcData.colonies],
    planetStats: [home.planetStats, ...npcData.planetStats],
    populations: [home.population, ...npcData.populations],
    moneySupplyStates: [home.moneySupplyState, ...npcData.moneySupplyStates],
    wallets: [...home.wallets, ...npcData.wallets],
    buildings: [...home.buildings, ...npcData.buildings],
    warehouse: [...home.warehouse, ...npcData.warehouse],
    productionQueue: home.productionQueue,
    npcs: npcData.npcs,
    fleets: [...home.fleets, ...npcData.fleets],
    groundForceGroups: [home.groundForceGroup],
    gateways,
  };
}

export interface AdditionalPlayerSeed {
  player: Player;
  newSystem: System;
  newGateway: Gateway;
  /** Bestehendes System, mit dem das neue Heimatsystem kartografisch verbunden wird (siehe Klassendoku unten). */
  linkedSystemId: Id;
  planets: Planet[];
  colony: Colony;
  planetStats: PlanetStats;
  population: Population;
  moneySupplyState: PopulationMoneySupplyState;
  wallets: Wallet[];
  buildings: Building[];
  warehouse: WarehouseEntry[];
  productionQueue: ProductionQueueEntry[];
  fleets: Fleet[];
  groundForceGroup: GroundForceGroup;
}

/**
 * Fügt EINEN weiteren Kommandanten in eine bereits bestehende Galaxie ein
 * ("Registrieren", siehe `SimulatedGameApiService.registerPlayer`): bringt
 * dabei ein komplett neues Heimatsystem samt Heimatplaneten-Cluster mit,
 * nach demselben Muster wie das allererste Heimatsystem in
 * `createWorldSeed` – nur zusätzlich zur bestehenden Galaxie statt als deren
 * Ursprung. NPCs, andere Kommandanten, Systeme und der Markt bleiben
 * unangetastet.
 *
 * Das neue System startet – wie jedes Heimatsystem – mit uneingeschränkt
 * aktivem Gateway (siehe `createWorldSeed`-Kommentar zu Gateways) und wird
 * kartografisch mit dem nächstgelegenen bestehenden System verbunden. Kein
 * Versuch, es korrekt in das ursprüngliche Voronoi-Nachbarschaftsnetz von
 * `generateGalaxy` einzuflechten – für den Prototyp reicht eine einzelne
 * Verbindung, damit das System auf der Karte nicht als Insel wirkt.
 */
export function createAdditionalPlayerSeed(existingSystems: System[], commanderName: string, homeworldName: string): AdditionalPlayerSeed {
  const rnd: Rng = Math.random;
  const t = now();

  const position = pickIsolatedPosition(existingSystems, rnd);
  const linkedSystem = nearestSystem(existingSystems, position);

  const systemId = nextId('sys');
  const gatewayId = nextId('gw');
  const home = buildHomeworldBundle(commanderName, homeworldName, systemId, rnd, t);

  const usedNames = new Set(existingSystems.map(s => s.name));
  const systemName = pickAdditionalSystemName(usedNames, rnd);

  const newSystem: System = {
    id: systemId,
    name: systemName,
    x: position.x,
    y: position.y,
    planetIds: home.planets.map(p => p.id),
    gatewayId,
    isHomeSystem: true,
    isTradeHub: false,
    factionFlavor: 'Heimatsystem',
  };
  const newGateway: Gateway = {
    id: gatewayId,
    systemId,
    state: 'Active', // siehe createWorldSeed: alle Gateways starten uneingeschränkt aktiv
    discoveredAt: t,
    activatedAt: t,
    activatingCompletesAt: null,
    reachableSystemIds: [linkedSystem.id],
  };

  return {
    player: home.player, newSystem, newGateway, linkedSystemId: linkedSystem.id,
    planets: home.planets, colony: home.colony, planetStats: home.planetStats, population: home.population,
    moneySupplyState: home.moneySupplyState, wallets: home.wallets, buildings: home.buildings,
    warehouse: home.warehouse, productionQueue: home.productionQueue, fleets: home.fleets,
    groundForceGroup: home.groundForceGroup,
  };
}

/**
 * Probiert mehrere zufällige Kandidatenpunkte und behält den mit dem
 * größten Mindestabstand zu allen bestehenden Systemen – einfache
 * räumliche Streuung ohne den Anspruch der Poisson-Disk-Platzierung aus
 * `generateGalaxy` (dort für eine feste Systemanzahl vorab optimiert, hier
 * für einen einzelnen, jederzeit nachträglich eingefügten Punkt unnötig).
 */
function pickIsolatedPosition(existingSystems: System[], rnd: Rng): { x: number; y: number } {
  const margin = 0.08;
  let best = { x: 0.5, y: 0.5 };
  let bestMinDist = -1;
  for (let attempt = 0; attempt < 40; attempt++) {
    const candidate = { x: margin + rnd() * (1 - 2 * margin), y: margin + rnd() * (1 - 2 * margin) };
    const minDist = existingSystems.reduce((min, s) => Math.min(min, Math.hypot(candidate.x - s.x, candidate.y - s.y)), Infinity);
    if (minDist > bestMinDist) { bestMinDist = minDist; best = candidate; }
  }
  return best;
}

function nearestSystem(systems: System[], pos: { x: number; y: number }): System {
  return systems.reduce((nearest, s) =>
    Math.hypot(pos.x - s.x, pos.y - s.y) < Math.hypot(pos.x - nearest.x, pos.y - nearest.y) ? s : nearest);
}
