package dev.crystalclient.core;

public enum Category {
    COMBAT("Combat"), HUD("HUD"), CLIENT("Client");
    public final String label;
    Category(String l) { label = l; }
}
