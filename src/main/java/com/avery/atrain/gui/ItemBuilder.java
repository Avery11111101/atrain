package com.avery.atrain.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {
    private final Material material;
    private String name;
    private final List<String> lore = new ArrayList<>();
    private int customModelData = -1;

    public ItemBuilder(Material material) {
        this.material = material;
    }

    public ItemBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ItemBuilder lore(String... lines) {
        lore.addAll(Arrays.asList(lines));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    public ItemBuilder model(int data) {
        this.customModelData = data;
        return this;
    }

    public ItemStack build() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            if (customModelData >= 0) meta.setCustomModelData(customModelData);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
