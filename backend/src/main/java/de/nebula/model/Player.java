package de.nebula.model;

public class Player {
  public String id;
  public String name;
  public String homeworldColonyId;
  public String homeSystemId;
  public long createdAt;
  public PlayerRole role;
  /** Lager-Kennzeichen für {@code role == Npc} (z. B. "NORD"/"SUED"), sonst null. */
  public String campId;
}
