package com.avery.atrain.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class GuiHolder implements InventoryHolder {

    public enum Type {
        MAIN, TUTORIAL, TUTORIAL_CATEGORY,
        LINE_LIST, LINE_DETAIL, LINE_ADD_STOP, LINE_MANAGE_STOPS, LINE_PLATFORMS,
        STOP_LIST, STOP_DETAIL,
        LINE_CHOICE, CONFIRM, LANGUAGE, RECORD_SELECT, QUICK_SETUP
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
