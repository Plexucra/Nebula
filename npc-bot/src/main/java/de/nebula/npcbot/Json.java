package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Kleine, nullsichere Zugriffe auf die JSON-Antworten des Servers – die Bots kennen keine Modellklassen (siehe pom.xml). */
final class Json {
  private Json() {
  }

  static boolean isNull(JsonNode n) {
    return n == null || n.isNull() || n.isMissingNode();
  }

  static String text(JsonNode node, String field) {
    if (isNull(node)) return null;
    JsonNode v = node.path(field);
    return isNull(v) ? null : v.asText();
  }

  static double dbl(JsonNode node, String field) {
    return dbl(node, field, 0);
  }

  static double dbl(JsonNode node, String field, double def) {
    if (isNull(node)) return def;
    JsonNode v = node.path(field);
    return isNull(v) ? def : v.asDouble(def);
  }

  static int integer(JsonNode node, String field) {
    return (int) Math.round(dbl(node, field, 0));
  }

  static boolean bool(JsonNode node, String field) {
    return !isNull(node) && node.path("" + field).asBoolean(false);
  }

  static List<JsonNode> list(JsonNode node) {
    List<JsonNode> out = new ArrayList<>();
    if (!isNull(node) && node.isArray()) node.forEach(out::add);
    return out;
  }

  static boolean eq(String a, String b) {
    return a != null && a.equals(b);
  }
}
