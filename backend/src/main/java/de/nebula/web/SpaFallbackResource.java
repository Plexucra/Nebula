package de.nebula.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;

/**
 * Deep-Link-Fallback für die eingebettete Angular-SPA (Umsetzungskonzept/14_...md,
 * Teil 3 – "Backend liefert Frontend mit aus"). Statische Dateien unter
 * {@code META-INF/resources} (das per {@code build-and-run-lan.sh} kopierte
 * Angular-Production-Build) werden von Quarkus/Vert.x bereits automatisch und
 * mit Vorrang vor JAX-RS-Routen ausgeliefert – dieser Handler greift NUR,
 * wenn kein passendes Static Asset gefunden wurde (z. B. beim direkten
 * Aufruf/Neuladen von {@code /galaxie} oder {@code /nachrichten}: Angulars
 * eigener Client-Router übernimmt dort erst NACH dem Laden von
 * {@code index.html}). Der WebSocket-Endpunkt {@code /game} kollidiert nicht,
 * da er nur auf Upgrade-Requests reagiert.
 */
@Path("/{path: (?!game).*}")
public class SpaFallbackResource {

  @GET
  public Response indexHtml() {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/resources/index.html")) {
      if (in == null) {
        return Response.status(Response.Status.NOT_FOUND).entity("Frontend nicht eingebettet – siehe build-and-run-lan.sh").build();
      }
      return Response.ok(in.readAllBytes(), MediaType.TEXT_HTML).build();
    } catch (IOException e) {
      return Response.serverError().build();
    }
  }
}
