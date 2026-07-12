package com.avery.atrain.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class GuiHolder implements InventoryHolder {

    public enum Type {
        MAIN, TUTORIAL, TUTORIAL_CATEGORY,
        STOP_LIST, STATION_EDIT,
        LINE_LIST, LINE_DETAIL, LINE_ADD_STOP,
        RECORD_SELECT, RECORD_SEGMENT,
        SPEED_BLOCK_EDIT,
        CONFIRM, LANGUAGE
    }

    private final Type type;
    private Inventory inventory;
    private final Map<String, String> data = new HashMap<>();
    private final Stack<Type> history = new Stack<>();

    public GuiHolder(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public Map<String, String> getData() { return data; }
    public Stack<Type> getHistory() { return history; }

    public void set(String key, String value) { data.put(key, value); }
    public String get(String key) { return data.get(key); }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
