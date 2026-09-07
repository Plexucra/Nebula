package de.nebula.data;

import de.nebula.model.Planet;
import de.nebula.model.PlanetResourceConcentration;
import de.nebula.model.PlanetType;
import de.nebula.model.StarSystem;
import de.nebula.state.IdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ausführliche Verifikation der Rohstoff-Rebalancierung (Nutzervorgabe, siehe
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §6-§8): Heimatplanet-Regel,
 * Planetentyp-Fördergütebereiche, Anzahl besiedelbarer/unbesiedelbarer Himmelskörper je
 * System und die Gasriesenmond-Kopplung an einen Gasriesen/Eisriesen im selben System.
 *
 * <p>{@code WorldSeed.createWorldSeed} ist mit festem Seed (1337) deterministisch – die
 * Tests prüfen deshalb die GESAMTE generierte Galaxie (200 Systeme), nicht nur Stichproben.</p>
 */
class WorldSeedResourceProfileTest {

  private static final List<String> HOMEWORLD_FOOD_AND_ELERIUM =
      List.of("res_eis", "res_atmosphaere", "res_salz", "res_kohlenstoff", "res_elerium");

  private static WorldSeed.Seed seed() {
    return WorldSeed.createWorldSeed("Testkommandant", "Testheim", new IdGenerator());
  }

  @Test
  void createWorldSeedIsDeterministicForFixedSeed() {
    WorldSeed.Seed a = seed();
    WorldSeed.Seed b = seed();

    assertEquals(a.planets.size(), b.planets.size());
    Map<String, Planet> byIdA = a.planets.stream().collect(java.util.stream.Collectors.toMap(p -> p.id, p -> p));
    for (Planet pb : b.planets) {
      Planet pa = byIdA.get(pb.id);
      assertEquals(pa.type, pb.type, "gleicher Planet (gleiche Id) sollte bei gleichem Seed denselben Typ haben");
      assertEquals(pa.usable, pb.usable);
      assertEquals(pa.resourceConcentration.size(), pb.resourceConcentration.size());
      for (int i = 0; i < pa.resourceConcentration.size(); i++) {
        assertEquals(pa.resourceConcentration.get(i).resourceTypeId, pb.resourceConcentration.get(i).resourceTypeId);
        assertEquals(pa.resourceConcentration.get(i).concentration, pb.resourceConcentration.get(i).concentration, 0.0001);
      }
    }
  }

  @Test
  void homeworldFoodAndEleriumResourcesAreAlwaysBetween50And60() {
    WorldSeed.Seed seed = seed();
    StarSystem home = seed.systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();
    Planet homeworld = seed.planets.stream()
        .filter(p -> p.systemId.equals(home.id) && p.orbitIndex == 0).findFirst().orElseThrow();
    assertEquals(PlanetType.TemperierterBiosphaerenplanet, homeworld.type);
    assertTrue(homeworld.usable);

    Map<String, Double> byResource = homeworld.resourceConcentration.stream()
        .collect(java.util.stream.Collectors.toMap(c -> c.resourceTypeId, c -> c.concentration));
    assertEquals(PlanetTypeProfiles.RESOURCE_ORDER.size(), byResource.size(), "alle 17 Rohstoffe müssen vorhanden sein");

    for (String resourceTypeId : HOMEWORLD_FOOD_AND_ELERIUM) {
      double c = byResource.get(resourceTypeId);
      assertTrue(c >= 50 && c <= 60, resourceTypeId + " sollte zwischen 50 und 60 liegen, war " + c);
    }

    long goodOutsideFoodAndElerium = byResource.entrySet().stream()
        .filter(e -> !HOMEWORLD_FOOD_AND_ELERIUM.contains(e.getKey()) && e.getValue() >= 50)
        .count();
    assertEquals(1, goodOutsideFoodAndElerium, "genau eine Zufalls-Ausnahme außerhalb Nahrung/Elerium sollte gut (>=50) sein");

    long poorOutsideFoodAndElerium = byResource.entrySet().stream()
        .filter(e -> !HOMEWORLD_FOOD_AND_ELERIUM.contains(e.getKey()) && e.getValue() < 50)
        .count();
    assertEquals(PlanetTypeProfiles.RESOURCE_ORDER.size() - HOMEWORLD_FOOD_AND_ELERIUM.size() - 1, poorOutsideFoodAndElerium);
    for (Map.Entry<String, Double> e : byResource.entrySet()) {
      if (HOMEWORLD_FOOD_AND_ELERIUM.contains(e.getKey()) || e.getValue() >= 50) continue;
      assertTrue(e.getValue() >= 1 && e.getValue() <= 9,
          e.getKey() + " (nicht Nahrung/Elerium/Ausnahme) sollte zwischen 1 und 9 liegen, war " + e.getValue());
    }
  }

  @Test
  void additionalPlayerHomeworldFollowsSameProfileRule() {
    WorldSeed.Seed seed = seed();
    IdGenerator ids = new IdGenerator();
    WorldSeed.AdditionalSeed additional = WorldSeed.createAdditionalPlayerSeed(seed.systems, "Zweitkommandant", "Zweitheim", ids);

    Planet homeworld = additional.planets.stream().filter(p -> p.orbitIndex == 0).findFirst().orElseThrow();
    Map<String, Double> byResource = homeworld.resourceConcentration.stream()
        .collect(java.util.stream.Collectors.toMap(c -> c.resourceTypeId, c -> c.concentration));
    for (String resourceTypeId : HOMEWORLD_FOOD_AND_ELERIUM) {
      double c = byResource.get(resourceTypeId);
      assertTrue(c >= 50 && c <= 60, resourceTypeId + " sollte zwischen 50 und 60 liegen, war " + c);
    }
    long good = byResource.entrySet().stream()
        .filter(e -> !HOMEWORLD_FOOD_AND_ELERIUM.contains(e.getKey()) && e.getValue() >= 50).count();
    assertEquals(1, good);
  }

  @Test
  void everyUsablePlanetsConcentrationsRespectItsPlanetTypeRanges() {
    WorldSeed.Seed seed = seed();
    StarSystem home = seed.systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();

    int checked = 0;
    for (Planet p : seed.planets) {
      if (!p.usable) continue;
      // Der Heimatplanet selbst (orbitIndex 0 im Heimatsystem) folgt der Sonderregel aus
      // applyHomeworldProfile, nicht den allgemeinen Planetentyp-Bereichen.
      if (p.systemId.equals(home.id) && p.orbitIndex == 0) continue;

      List<PlanetTypeProfiles.ResourceRange> ranges = PlanetTypeProfiles.rangesForPlanetType(p.type);
      Map<String, PlanetTypeProfiles.Range> rangeByResource = ranges.stream()
          .collect(java.util.stream.Collectors.toMap(PlanetTypeProfiles.ResourceRange::resourceTypeId, PlanetTypeProfiles.ResourceRange::range));
      assertEquals(17, p.resourceConcentration.size(), "besiedelbarer Planet sollte alle 17 Rohstoffe führen: " + p.id);
      for (PlanetResourceConcentration c : p.resourceConcentration) {
        PlanetTypeProfiles.Range r = rangeByResource.get(c.resourceTypeId);
        assertTrue(r != null, "unbekannter Rohstoff " + c.resourceTypeId + " für Typ " + p.type);
        assertTrue(c.concentration >= r.min() && c.concentration <= r.max(),
            p.type + "/" + c.resourceTypeId + " außerhalb des Bereichs [" + r.min() + "," + r.max() + "]: " + c.concentration);
      }
      checked++;
    }
    assertTrue(checked > 500, "es sollten mehrere hundert besiedelbare Planeten geprüft worden sein, waren " + checked);
  }

  @Test
  void unusablePlanetsHaveNoResourceConcentration() {
    WorldSeed.Seed seed = seed();
    long unusableCount = 0;
    for (Planet p : seed.planets) {
      if (p.usable) continue;
      assertTrue(p.resourceConcentration.isEmpty(), "unbesiedelbarer Planet sollte keine Rohstoffkonzentration zeigen: " + p.id);
      unusableCount++;
    }
    assertTrue(unusableCount > 500, "es sollten mehrere hundert unbesiedelbare Himmelskörper existieren, waren " + unusableCount);
  }

  @Test
  void everySystemHasThreeToFiveUsableAndFourToSixUnusableBodies() {
    WorldSeed.Seed seed = seed();
    StarSystem home = seed.systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();

    Map<String, List<Planet>> bySystem = seed.planets.stream()
        .collect(java.util.stream.Collectors.groupingBy(p -> p.systemId));
    assertEquals(200, bySystem.size());

    for (StarSystem s : seed.systems) {
      List<Planet> planets = bySystem.get(s.id);
      long usable = planets.stream().filter(p -> p.usable).count();
      long unusable = planets.size() - usable;
      assertTrue(unusable >= 4 && unusable <= 6, s.name + ": unbesiedelbare Körper außerhalb [4,6]: " + unusable);
      if (s.id.equals(home.id)) {
        assertEquals(5, usable, "Heimatsystem sollte genau 5 besiedelbare (benannte) Planeten haben");
      } else {
        assertTrue(usable >= 3 && usable <= 5, s.name + ": besiedelbare Körper außerhalb [3,5]: " + usable);
      }
    }
  }

  @Test
  void gasriesenmondOnlyOccursInSystemsWithAGasGiantAndAtMostOncePerSystem() {
    WorldSeed.Seed seed = seed();
    Map<String, List<Planet>> bySystem = seed.planets.stream()
        .collect(java.util.stream.Collectors.groupingBy(p -> p.systemId));

    long totalMoons = 0;
    for (Map.Entry<String, List<Planet>> e : bySystem.entrySet()) {
      long moons = e.getValue().stream().filter(p -> p.type == PlanetType.Gasriesenmond).count();
      assertTrue(moons <= 1, "höchstens ein Gasriesenmond pro System, System " + e.getKey() + " hat " + moons);
      if (moons == 1) {
        boolean hasGasGiant = e.getValue().stream()
            .anyMatch(p -> p.type == PlanetType.Gasriese || p.type == PlanetType.Eisriese);
        assertTrue(hasGasGiant, "Gasriesenmond ohne Gasriese/Eisriese im selben System: " + e.getKey());
      }
      totalMoons += moons;
    }
    assertTrue(totalMoons > 0, "es sollte mindestens ein Gasriesenmond in der ganzen Galaxie vorkommen");
  }

  @Test
  void gasriesenAndEisriesenAreNeverUsable() {
    WorldSeed.Seed seed = seed();
    long gasGiants = 0;
    for (Planet p : seed.planets) {
      if (p.type == PlanetType.Gasriese || p.type == PlanetType.Eisriese) {
        assertFalse(p.usable, "Gasriese/Eisriese darf nie besiedelbar sein: " + p.id);
        gasGiants++;
      }
    }
    assertTrue(gasGiants > 0, "es sollten Gasriesen/Eisriesen in der Galaxie vorkommen");
  }

  @Test
  void gasriesenmondIsAlwaysUsable() {
    WorldSeed.Seed seed = seed();
    long moons = 0;
    for (Planet p : seed.planets) {
      if (p.type == PlanetType.Gasriesenmond) {
        assertTrue(p.usable, "Gasriesenmond sollte immer besiedelbar sein: " + p.id);
        moons++;
      }
    }
    assertTrue(moons > 0);
  }

  @Test
  void everySystemIsRepresentedAndPlanetIdsMatchPlanetsList() {
    WorldSeed.Seed seed = seed();
    Set<String> planetIds = new HashSet<>();
    for (Planet p : seed.planets) planetIds.add(p.id);

    for (StarSystem s : seed.systems) {
      assertFalse(s.planetIds.isEmpty(), s.name + " sollte Himmelskörper haben");
      for (String pid : s.planetIds) {
        assertTrue(planetIds.contains(pid), "planetId " + pid + " aus system.planetIds fehlt in seed.planets");
      }
    }
  }

  @Test
  void planetTypeProfilesHaveAtMostTwoSignatureResourcesEach() {
    for (PlanetType type : PlanetType.values()) {
      List<PlanetTypeProfiles.ResourceRange> ranges = PlanetTypeProfiles.rangesForPlanetType(type);
      assertEquals(17, ranges.size(), type + " sollte alle 17 Rohstoffe führen");
      long signatureCount = ranges.stream().filter(r -> r.range().max() >= 30).count();
      assertTrue(signatureCount <= 2, type + " hat mehr als 2 Signaturrohstoffe (max >= 30): " + signatureCount);
      long badCount = ranges.stream().filter(r -> r.range().max() < 15).count();
      assertEquals(17 - signatureCount, badCount, type + ": alle Nicht-Signaturrohstoffe sollten unter 15 Fördergüte-Maximum liegen");
    }
  }
}
