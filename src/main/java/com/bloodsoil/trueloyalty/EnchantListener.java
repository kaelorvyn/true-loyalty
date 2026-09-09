package com.bloodsoil.trueloyalty;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;

public class EnchantListener implements Listener {

    private final TrueLoyaltyPlugin plugin;
    private final Map<UUID, Boolean> pendingSwap = new ConcurrentHashMap<>();

    public EnchantListener(TrueLoyaltyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepare(PrepareItemEnchantEvent event) {
        if (event.getItem().getType() != Material.TRIDENT || plugin.trueLoyalty() == null) {
            return;
        }
        boolean swap = plugin.random().nextDouble() < plugin.cfg("enchantment.chance-to-replace-loyalty", 0.5);
        pendingSwap.put(event.getEnchanter().getUniqueId(), swap);
        if (!swap) {
            return;
        }
        for (EnchantmentOffer offer : event.getOffers()) {
            if (offer != null && offer.getEnchantment() == Enchantment.LOYALTY) {
                offer.setEnchantment(plugin.trueLoyalty());
                offer.setEnchantmentLevel(1);
            }
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (event.getItem().getType() != Material.TRIDENT || plugin.trueLoyalty() == null) {
            return;
        }
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        if (!toAdd.containsKey(Enchantment.LOYALTY)) {
            return;
        }
        Boolean pending = pendingSwap.remove(event.getEnchanter().getUniqueId());
        boolean swap = pending != null ? pending
                : plugin.random().nextDouble() < plugin.cfg("enchantment.chance-to-replace-loyalty", 0.5);
        if (!swap) {
            return;
        }
        toAdd.remove(Enchantment.LOYALTY);
        toAdd.put(plugin.trueLoyalty(), 1);
        plugin.grantAdvancement(event.getEnchanter(), "sea_god_trident", "enchanted");
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        if (plugin.isTrueLoyaltyTrident(event.getResult())
                && event.getView().getPlayer() instanceof Player player) {
            plugin.grantAdvancement(player, "sea_god_trident", "enchanted");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            pendingSwap.remove(player.getUniqueId());
        }
    }
}
