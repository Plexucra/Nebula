import {
  Building, Colony, Fleet, Gateway, Npc, Planet, PlanetStats, PlanetType, Player, Population,
  PopulationMoneySupplyState, System, Wallet, WarehouseEntry,
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
  gateways: Gateway[];
  npcs: Npc[];
  fleets: Fleet[];
}

/**
 * Versiegelte Eleriumreserve, mit der jede neu gegründete Kolonie startet
 * (Nebula_Planetentypen_..., §8): "verhindert einen Startstillstand,
 * ersetzt aber keine laufende Förderung" – ohne sie wäre das Energienetz
 * ab dem allerersten Tick im Blackout, da die vierstufige
 * Eleriumenergiezelle-Kette (§9.2) niemand so schnell hochziehen kann.
 */
const SEALED_ELERIUM_RESERVE_HOME = 25;
const SEALED_ELERIUM_RESERVE_NPC = 10;

function eleriumReserveEntry(colonyId: string, quantity: number): WarehouseEntry {
  return { colonyId, productTypeId: 'p_elerium_energiezelle', quantity };
}

const PLANET_NAMES_HOME = ['Aurelia Prime', 'Kessar', 'Vantis', 'Thal Minor', 'Rho Cindra'];

const GALAXY_SYSTEM_COUNT = 24;
const NPC_COUNT = 10;

const SYSTEM_NAME_POOL = [
  'Aurelia', "Kepler's Reach", 'Thessaly', 'Drakon-Weite', 'Vey Corva', 'Halcyon Rand',
  'Praxis Gate', 'Corvin Öde', 'Nashira', 'Talvex', 'Ophir Rand', 'Sirenum',
  'Kestrel-Feld', 'Meridian Tor', 'Borea Vor', 'Xantha', 'Rigel Außenposten',
  'Vantor Bogen', 'Elyra Senke', 'Cassiel', 'Drift von Ilun', 'Perath',
  'Solace Rand', 'Nocturn Bucht', 'Amaris', 'Kroven Riff', 'Vela Passage',
  'Tessark', 'Orinth', 'Fahrun Weite',
];

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

function freighterFleet(ownerId: string, colonyId: string, systemId: string, name: string): Fleet {
  return {
    id: nextId('flt'), ownerId, name, locationType: 'ColonyOrbit', locationColonyId: colonyId,
    systemId, status: 'Stationed', ships: [{ shipProductTypeId: 'p_freighter', quantity: 1 }],
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
    fleets.push(freighterFleet(npc.id, colony.id, system.id, `Handelsflotte ${colony.name}`));
  });

  return { npcs, colonies, planets, planetStats, populations, moneySupplyStates, wallets, buildings, warehouse, fleets };
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

  const player: Player = {
    id: nextId('ply'),
    name: commanderName,
    homeSystemId: systemIds[homeIndex],
    homeworldColonyId: '', // wird unten gesetzt
    createdAt: t,
  };

  const homePlanets: Planet[] = PLANET_NAMES_HOME.map((name, i) => {
    // Der Spieler startet auf einem temperierten Biosphärenplaneten
    // (Nebula_Planetentypen_..., §8) – die übrigen Himmelskörper im
    // Heimatsystem sind zunächst unbesiedelt und dürfen beliebige Typen sein.
    const planetType: PlanetType = i === 0 ? 'TemperierterBiosphaerenplanet' : randomPlanetType(rnd);
    let conc = concentrationProfileForType(planetType, rnd);
    if (i === 0) conc = applyHomeworldMinimums(conc);
    return {
      id: nextId('pla'),
      systemId: player.homeSystemId,
      name,
      size: (['Klein', 'Mittel', 'Groß', 'Riesig'] as const)[Math.floor(rnd() * 4)],
      type: planetType,
      buildCapacity: i === 0 ? 90 : 55 + Math.floor(rnd() * 30),
      resourceConcentration: conc,
      orbitIndex: i,
    };
  });

  const systems: System[] = galaxy.positions.map((pos, i) => {
    const isHome = i === homeIndex;
    const isHub = tradeHubSet.has(i);
    return {
      id: systemIds[i],
      name: isHome ? 'Aurelia-System' : names[i % names.length],
      x: pos.x,
      y: pos.y,
      planetIds: isHome ? homePlanets.map(p => p.id) : [],
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

  const homeworld: Colony = {
    id: nextId('col'),
    planetId: homePlanets[0].id,
    systemId: player.homeSystemId,
    ownerId: player.id,
    name: homeworldName,
    foundedAt: t,
    isHomeworld: true,
  };
  player.homeworldColonyId = homeworld.id;

  const homePopulationCount = 420;
  const homePowergridLevel = 4;
  const homeHabitatLevel = habitatLevelFor(homePopulationCount, homePowergridLevel);

  const homeStats: PlanetStats = {
    colonyId: homeworld.id,
    infrastructurePct: 110,
    securityPct: 100,
    standardOfLivingPct: 100,
    loyaltyPct: 78,
    lastRecalculatedAt: t,
  };

  const homePopulation: Population = { colonyId: homeworld.id, currentCount: homePopulationCount, growthRatePerInterval: 0 };
  const homeMoneyState: PopulationMoneySupplyState = { planetId: homePlanets[0].id, historicalPeakPopulation: homePopulationCount, lastPopulation: homePopulationCount };

  const playerWallet: Wallet = { id: nextId('wal'), ownerType: 'Player', ownerId: player.id, balance: 6500 };
  const popWallet: Wallet = { id: nextId('wal'), ownerType: 'Population', ownerId: homeworld.id, balance: 900 };

  const startBuildings: Building[] = [
    buildInstance(homeworld.id, 'b_habitat', homeHabitatLevel),
    buildInstance(homeworld.id, 'b_powergrid', homePowergridLevel),
    buildInstance(homeworld.id, 'b_industry', 2),
    buildInstance(homeworld.id, 'b_shipyard', 1),
    buildInstance(homeworld.id, 'b_academy', 1),
  ];
  const startWarehouse: WarehouseEntry[] = [eleriumReserveEntry(homeworld.id, SEALED_ELERIUM_RESERVE_HOME)];

  // Frachter ab Spielbeginn – ohne eigene Transportkapazität ist kein
  // Handel über die eigene Kolonie hinaus möglich (Konzeption/05_..., §9).
  const playerFleet = freighterFleet(player.id, homeworld.id, player.homeSystemId, `Handelsflotte ${homeworld.name}`);

  // --- Gateways --------------------------------------------------------------
  // Das Gateway existiert bereits im Heimatsystem (Konzeption/08_..., §1),
  // gilt hier als bereits gefunden ("Discovered") – ein echtes
  // Forschungssystem, das die Entdeckung selbst auslöst, ist noch offen
  // (siehe Konzeption/04_..., §6). Aktivierung bleibt eine bewusste
  // Spielerentscheidung (Konzeption/08_..., §7: "Isolation bietet
  // Sicherheit. Öffnung bietet Wohlstand.") statt an einen künstlichen
  // Fortschrittswert gekoppelt zu sein – das frühe Spiel soll stattdessen
  // durch den lokalen Produktions-/Handelskreislauf ausgefüllt sein
  // (Rohstoffe/Konsumgüter bauen und verkaufen), nicht durch Warten auf
  // eine Zahl. Alle übrigen Systeme gehören zur bereits etablierten
  // galaktischen Gemeinschaft und gelten narrativ als längst aktiviert.
  const gateways: Gateway[] = galaxy.positions.map((_, i) => {
    const isHome = i === homeIndex;
    return {
      id: gatewayIds[i],
      systemId: systemIds[i],
      state: isHome ? 'Discovered' : 'Active',
      discoveredAt: isHome ? t : t - 1000,
      activatedAt: isHome ? null : t - 1000,
      activatingCompletesAt: null,
      reachableSystemIds: galaxy.neighbors[i].map(j => systemIds[j]),
    };
  });

  // --- NPCs ------------------------------------------------------------------
  const npcData = spawnNpcs(systems, galaxy.neighbors, rnd, t);

  return {
    player,
    systems,
    planets: [...homePlanets, ...npcData.planets],
    colonies: [homeworld, ...npcData.colonies],
    planetStats: [homeStats, ...npcData.planetStats],
    populations: [homePopulation, ...npcData.populations],
    moneySupplyStates: [homeMoneyState, ...npcData.moneySupplyStates],
    wallets: [playerWallet, popWallet, ...npcData.wallets],
    buildings: [...startBuildings, ...npcData.buildings],
    warehouse: [...startWarehouse, ...npcData.warehouse],
    npcs: npcData.npcs,
    fleets: [playerFleet, ...npcData.fleets],
    gateways,
  };
}
