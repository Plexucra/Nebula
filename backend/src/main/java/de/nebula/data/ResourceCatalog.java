package de.nebula.data;

import de.nebula.model.ResourceCategory;
import de.nebula.model.ResourceType;

import java.util.List;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/data/resource-catalog.ts}.
 * Ebene-1-Rohstoffkatalog nach Konzeption/Umsetzungskonzept/
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §4: bewusste wirtschaftliche
 * Sammelgruppen statt einzelner realer Elemente (siehe §4.1 dort für die Zusammenfassungsregeln,
 * z. B. Aluminium+Magnesium als Leichtmetallerz).
 */
public final class ResourceCatalog {
  private ResourceCatalog() {
  }

  private static ResourceType r(String id, String name, ResourceCategory category, String description) {
    ResourceType t = new ResourceType();
    t.id = id;
    t.name = name;
    t.category = category;
    t.description = description;
    return t;
  }

  public static final List<ResourceType> CATALOG = List.of(
      r("res_ferrometall", "Ferrometallerz", ResourceCategory.Metalle, "Eisen-, nickel- und kobalthaltige Erze für Stahl, Struktur, Panzerung und Maschinen."),
      r("res_leichtmetall", "Leichtmetallerz", ResourceCategory.Metalle, "Aluminium- und magnesiumhaltige Erze für Leichtbau, Rahmen und schnelle Schiffe."),
      r("res_refraktaer", "Refraktärmetallerz", ResourceCategory.Metalle, "Titan-, wolfram-, molybdän- und niobhaltige Erze für Hitze, Reaktoren, Düsen und schwere Panzerung."),
      r("res_leitmetall", "Leitmetallerz", ResourceCategory.Metalle, "Vor allem kupfer-, zinn- und zinkhaltige Erze für Leitungen, Kontakte und Elektromotoren."),
      r("res_edelmetall", "Edelmetallerz", ResourceCategory.Metalle, "Gold, Silber und Platingruppenmetalle für Kontakte, Katalysatoren und Sensorik."),
      r("res_seltenerden", "Seltenerdenerz", ResourceCategory.Metalle, "Lanthanoide, Yttrium und Scandium für Magnete, Optik, Sensoren und Hochleistungselektronik."),
      r("res_technometall", "Technologiemetallerz", ResourceCategory.Metalle, "Lithium, Gallium, Germanium, Indium und verwandte Spurenmetalle für Halbleiter, Batterien und Spezialelektronik."),
      r("res_silikat", "Silikatmineral", ResourceCategory.Mineralien, "Quarz, Feldspäte und silikatische Gesteine für Glas, Keramik, Silizium und Bauwerkstoffe."),
      r("res_kohlenstoff", "Kohlenstoffmineral", ResourceCategory.Mineralien, "Graphit, Karbonate und kohlenstoffreiche Minerale für Verbundstoffe, Elektroden, Chemie und Biologie."),
      r("res_salz", "Salzmineral", ResourceCategory.Mineralien, "Chloride, Phosphate, Nitrate, Sulfate und Spurennährstoffe für Chemie, Dünger, Medizin und Lebenserhaltung."),
      r("res_radionuklid", "Radionukliderz", ResourceCategory.Mineralien, "Uran-, Thorium- und andere radioaktive Minerale für Strahlenquellen, Spezialenergie und Sensorik."),
      r("res_eis", "Wassereis", ResourceCategory.Fluide, "Eis, Grundwasser und wasserhaltige Minerale für Trinkwasser, Sauerstoff, Chemie und Reaktionsmasse."),
      r("res_atmosphaere", "Atmosphärenfluid", ResourceCategory.Fluide, "Stickstoff, Sauerstoff, Kohlendioxid, Wasserstoff und Prozessgase für Atemluft, Chemie und Treibstoffe."),
      r("res_edelgas", "Edelgaskonzentrat", ResourceCategory.Fluide, "Helium, Neon, Argon, Krypton und Xenon für Kühlung, Ionentriebwerke und Fertigungsatmosphäre."),
      r("res_kohlenwasserstoff", "Kohlenwasserstofflager", ResourceCategory.Fluide, "Methan, höhere Kohlenwasserstoffe und organische Sedimente für Polymere, Chemie, Textilien und Treibstoffe."),
      r("res_isotopentraeger", "Isotopenträger", ResourceCategory.Elerium, "Deuterium-, helium-3- und lithiumreiche Trägerstoffe für Fusionsisotope und Hochenergieantriebe."),
      r("res_elerium", "Eleriumspuren", ResourceCategory.Elerium, "Fiktives Elerium-115 in stabilen Mineral- oder Fluidmatrizen – Grundlage für Energieversorgung, Schiffsantrieb und Waffeninitiatoren. Nur in Spuren vorhanden.")
  );
}
