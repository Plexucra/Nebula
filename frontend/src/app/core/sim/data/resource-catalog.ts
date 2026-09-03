import { ResourceType } from '../../models';

/**
 * Ebene-1-Rohstoffkatalog nach `Konzeption/Umsetzungskonzept/
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md`, §4: bewusste
 * wirtschaftliche Sammelgruppen statt einzelner realer Elemente (siehe §4.1
 * dort für die Zusammenfassungsregeln, z. B. Aluminium+Magnesium als
 * Leichtmetallerz).
 */
export const RESOURCE_CATALOG: ResourceType[] = [
  { id: 'res_ferrometall', name: 'Ferrometallerz', category: 'Metalle', description: 'Eisen-, nickel- und kobalthaltige Erze für Stahl, Struktur, Panzerung und Maschinen.' },
  { id: 'res_leichtmetall', name: 'Leichtmetallerz', category: 'Metalle', description: 'Aluminium- und magnesiumhaltige Erze für Leichtbau, Rahmen und schnelle Schiffe.' },
  { id: 'res_refraktaer', name: 'Refraktärmetallerz', category: 'Metalle', description: 'Titan-, wolfram-, molybdän- und niobhaltige Erze für Hitze, Reaktoren, Düsen und schwere Panzerung.' },
  { id: 'res_leitmetall', name: 'Leitmetallerz', category: 'Metalle', description: 'Vor allem kupfer-, zinn- und zinkhaltige Erze für Leitungen, Kontakte und Elektromotoren.' },
  { id: 'res_edelmetall', name: 'Edelmetallerz', category: 'Metalle', description: 'Gold, Silber und Platingruppenmetalle für Kontakte, Katalysatoren und Sensorik.' },
  { id: 'res_seltenerden', name: 'Seltenerdenerz', category: 'Metalle', description: 'Lanthanoide, Yttrium und Scandium für Magnete, Optik, Sensoren und Hochleistungselektronik.' },
  { id: 'res_technometall', name: 'Technologiemetallerz', category: 'Metalle', description: 'Lithium, Gallium, Germanium, Indium und verwandte Spurenmetalle für Halbleiter, Batterien und Spezialelektronik.' },
  { id: 'res_silikat', name: 'Silikatmineral', category: 'Mineralien', description: 'Quarz, Feldspäte und silikatische Gesteine für Glas, Keramik, Silizium und Bauwerkstoffe.' },
  { id: 'res_kohlenstoff', name: 'Kohlenstoffmineral', category: 'Mineralien', description: 'Graphit, Karbonate und kohlenstoffreiche Minerale für Verbundstoffe, Elektroden, Chemie und Biologie.' },
  { id: 'res_salz', name: 'Salzmineral', category: 'Mineralien', description: 'Chloride, Phosphate, Nitrate, Sulfate und Spurennährstoffe für Chemie, Dünger, Medizin und Lebenserhaltung.' },
  { id: 'res_radionuklid', name: 'Radionukliderz', category: 'Mineralien', description: 'Uran-, Thorium- und andere radioaktive Minerale für Strahlenquellen, Spezialenergie und Sensorik.' },
  { id: 'res_eis', name: 'Wassereis', category: 'Fluide', description: 'Eis, Grundwasser und wasserhaltige Minerale für Trinkwasser, Sauerstoff, Chemie und Reaktionsmasse.' },
  { id: 'res_atmosphaere', name: 'Atmosphärenfluid', category: 'Fluide', description: 'Stickstoff, Sauerstoff, Kohlendioxid, Wasserstoff und Prozessgase für Atemluft, Chemie und Treibstoffe.' },
  { id: 'res_edelgas', name: 'Edelgaskonzentrat', category: 'Fluide', description: 'Helium, Neon, Argon, Krypton und Xenon für Kühlung, Ionentriebwerke und Fertigungsatmosphäre.' },
  { id: 'res_kohlenwasserstoff', name: 'Kohlenwasserstofflager', category: 'Fluide', description: 'Methan, höhere Kohlenwasserstoffe und organische Sedimente für Polymere, Chemie, Textilien und Treibstoffe.' },
  { id: 'res_isotopentraeger', name: 'Isotopenträger', category: 'Elerium', description: 'Deuterium-, helium-3- und lithiumreiche Trägerstoffe für Fusionsisotope und Hochenergieantriebe.' },
  {
    id: 'res_elerium', name: 'Eleriumspuren', category: 'Elerium',
    description: 'Fiktives Elerium-115 in stabilen Mineral- oder Fluidmatrizen – Grundlage für Energieversorgung, Schiffsantrieb und Waffeninitiatoren. Nur in Spuren vorhanden.',
  },
];
