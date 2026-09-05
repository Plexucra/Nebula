package de.nebula.data;

import de.nebula.model.BuildingCategory;
import de.nebula.model.BuildingType;

import java.util.List;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/data/building-catalog.ts}.
 * ProductionFacility-Typen sind bewusst grob nach Warenkategorie geschnitten (Rohstoff/
 * Fertigung, Werft, Ausbildung) statt pro Einzelprodukt – siehe Umsetzungskonzept/02_...,
 * §1 (Produktionsanlagen begrenzen, was technisch produziert werden kann).
 *
 * <p>{@code b_habitat.populationCapacityPerLevel} bewusst hoch (6000, nicht 60): bei
 * niedrigem Wert stößt die Bevölkerung schon im vierstelligen Bereich an die Kapazitätsdecke,
 * weit unter den in der Konzeption beispielhaft genannten Millionen-/Milliarden-Größenordnungen
 * (Mechanik/11_..., §7). Restliche Formeln bleiben unverändert (siehe Mechanik/10_...,
 * "Offene Zahlenfragen" zur weiterhin ungelösten Geldschöpfungs-Skalierungsfrage).</p>
 */
public final class BuildingCatalog {
  private BuildingCatalog() {
  }

  private static BuildingType b(String id, String name, BuildingCategory category, String description,
                                int maxLevel, double buildPointsPerLevel, double baseCostPerLevel,
                                double baseHoursPerLevel, double upkeepPerLevel,
                                Integer productionSlotsPerLevel, Integer populationCapacityPerLevel) {
    BuildingType t = new BuildingType();
    t.id = id;
    t.name = name;
    t.category = category;
    t.description = description;
    t.maxLevel = maxLevel;
    t.buildPointsPerLevel = buildPointsPerLevel;
    t.baseCostPerLevel = baseCostPerLevel;
    t.baseHoursPerLevel = baseHoursPerLevel;
    t.upkeepPerLevel = upkeepPerLevel;
    t.productionSlotsPerLevel = productionSlotsPerLevel;
    t.populationCapacityPerLevel = populationCapacityPerLevel;
    return t;
  }

  public static final List<BuildingType> CATALOG = List.of(
      b("b_habitat", "Wohnkomplex", BuildingCategory.Infrastructure, "Lebensraum für die Kolonialbevölkerung. Trägt die Bevölkerung, gibt selbst keinen Produktionsbonus.", 20, 3, 120, 1, 1.5, null, 6000),
      b("b_powergrid", "Energienetz", BuildingCategory.Infrastructure, "Energieversorgung für Kolonie und Industrie. Erhöht die planetare Infrastrukturkapazität – benötigt dafür laufend Stabilisiertes Elerium aus dem Kolonielager, sonst Blackout-Abzug.", 20, 2, 90, 0.8, 1.5, null, 2500),
      b("b_industry", "Industriekomplex", BuildingCategory.ProductionFacility, "Extraktion und Fertigung von Rohstoffen, Bauteilen und Konsumgütern.", 20, 4, 160, 1.2, 3, 1, null),
      b("b_shipyard", "Werft", BuildingCategory.ProductionFacility, "Voraussetzung für den Bau von Schiffen jeder Klasse.", 15, 6, 260, 2, 5, 1, null),
      b("b_academy", "Ausbildungszentrum", BuildingCategory.ProductionFacility, "Voraussetzung für die Rekrutierung und Ausbildung von Bodentruppen.", 15, 3, 140, 1, 2, 1, null),
      b("b_defense", "Planetare Abwehr", BuildingCategory.PlanetaryDefense, "Autonome Abwehrsysteme gegen Landungsversuche. Muss nach Ausbau aktiviert werden (Anlaufzeit).", 10, 6, 300, 2, 6, null, null)
  );

  public static BuildingType find(String id) {
    return CATALOG.stream().filter(bt -> bt.id.equals(id)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannter BuildingType: " + id));
  }
}
