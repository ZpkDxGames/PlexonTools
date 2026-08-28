package com.plexon.tools.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemFactory {
    private ItemFactory() {
    }

    public static ItemStack create(Material material, Component name, List<Component> lore) {
        return create(material, name, lore, false);
    }

    public static ItemStack create(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(line -> line.decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(glow);
        item.setItemMeta(meta);
        return item;
    }
}
