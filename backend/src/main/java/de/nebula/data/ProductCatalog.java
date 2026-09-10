package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produktkatalog – die Daten selbst liegen in {@code shared/catalog/products.json}
 * und werden beim Start EINMAL geladen (siehe {@link CatalogJson}).
 *
 * <p>Bis Umsetzungskonzept/15_...md, Auftrag 3 standen dieselben 214 Einträge
 * hartkodiert sowohl hier als auch in {@code product-catalog.ts} – zwei
 * Kopien derselben Ausgangsdaten, die zwangsläufig auseinanderlaufen. Die
 * TS-Kopie ist ersatzlos entfallen (das Frontend holt den Katalog über den
 * WebSocket-Befehl {@code productTypes}), die Java-Seite liest jetzt dieselbe
 * JSON-Datei, die auch jedes andere Werkzeug lesen könnte.</p>
 */
public final class ProductCatalog {
  private ProductCatalog() {
  }

  public static final List<ProductType> CATALOG =
      List.copyOf(CatalogJson.load("products.json", new TypeReference<List<ProductType>>() {
      }));

  /**
   * Index nach Id. {@link #find} ist die meistgerufene Methode des Backends –
   * jeder Tick schlägt sie für jedes Gebäude, jede Schiffsgruppe, jeden
   * Kettenschritt und jeden Kampfwurf nach. Vorher ein Stream über alle 214
   * Einträge samt Lambda-Allokation je Aufruf; jetzt ein Hash-Lookup.
   */
  private static final Map<String, ProductType> BY_ID = CATALOG.stream()
      .collect(Collectors.toUnmodifiableMap(pt -> pt.id, pt -> pt));

  public static ProductType find(String id) {
    ProductType product = BY_ID.get(id);
    if (product == null) throw new IllegalArgumentException("Unbekannter ProductType: " + id);
    return product;
  }

  private static List<ProductType> byCategory(ProductCategory category) {
    return CATALOG.stream().filter(pt -> pt.category == category).toList();
  }

  public static final List<ProductType> CONSUMER_GOODS = byCategory(ProductCategory.ConsumerGood);
  public static final List<ProductType> RAW_RESOURCES = byCategory(ProductCategory.RawResource);
  public static final List<ProductType> SHIP_PRODUCTS = byCategory(ProductCategory.Ship);
  public static final List<ProductType> GROUND_UNIT_PRODUCTS = byCategory(ProductCategory.GroundUnit);
}
