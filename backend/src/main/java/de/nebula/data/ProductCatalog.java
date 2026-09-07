package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;

import java.util.List;

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

  public static ProductType find(String id) {
    return CATALOG.stream().filter(pt -> pt.id.equals(id)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannter ProductType: " + id));
  }

  private static List<ProductType> byCategory(ProductCategory category) {
    return CATALOG.stream().filter(pt -> pt.category == category).toList();
  }

  public static final List<ProductType> CONSUMER_GOODS = byCategory(ProductCategory.ConsumerGood);
  public static final List<ProductType> RAW_RESOURCES = byCategory(ProductCategory.RawResource);
  public static final List<ProductType> SHIP_PRODUCTS = byCategory(ProductCategory.Ship);
  public static final List<ProductType> GROUND_UNIT_PRODUCTS = byCategory(ProductCategory.GroundUnit);
}
