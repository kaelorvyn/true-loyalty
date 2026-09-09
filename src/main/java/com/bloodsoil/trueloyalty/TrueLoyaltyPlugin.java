package com.bloodsoil.trueloyalty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class TrueLoyaltyPlugin extends JavaPlugin {

    public static final String BOLT_TAG_PREFIX = "trueloyalty_bolt_";

    private final Random random = new Random();
    private final Set<TrueLoyaltySequence> activeSequences = ConcurrentHashMap.newKeySet();
    private Enchantment trueLoyalty;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        installDatapack();
        String id = getConfig().getString("enchantment.id", "bloodsoil:true_loyalty");
        NamespacedKey key = NamespacedKey.fromString(id);
        trueLoyalty = key == null ? null : Enchantment.getByKey(key);
        if (trueLoyalty == null) {
            getLogger().warning("附魔 " + id + " 未加载，请重启服务器让数据包生效");
        }
        ProtectionListener protection = new ProtectionListener(this);
        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
        getServer().getPluginManager().registerEvents(protection, this);
        getCommand("trueloyalty").setExecutor(new TrueLoyaltyCommand(this, protection));
    }

    @Override
    public void onDisable() {
        for (TrueLoyaltySequence sequence : activeSequences) {
            sequence.cancel();
        }
        activeSequences.clear();
    }

    private void installDatapack() {
        boolean needRestart = false;
        for (World world : Bukkit.getWorlds()) {
            Path root = world.getWorldFolder().toPath().resolve("datapacks/true-loyalty");
            needRestart |= writeResource("datapack/true-loyalty/pack.mcmeta", root.resolve("pack.mcmeta"));
            needRestart |= writeResource("datapack/true-loyalty/data/bloodsoil/enchantment/true_loyalty.json",
                    root.resolve("data/bloodsoil/enchantment/true_loyalty.json"));
            needRestart |= writeResource("datapack/true-loyalty/data/bloodsoil/tags/enchantment/exclusive_set/true_loyalty.json",
                    root.resolve("data/bloodsoil/tags/enchantment/exclusive_set/true_loyalty.json"));
        }
        if (needRestart) {
            getLogger().info("真·忠诚数据包已写入各世界 datapacks/true-loyalty，重启服务器后生效");
        }
    }

    private boolean writeResource(String resource, Path target) {
        try (InputStream in = getResource(resource)) {
            if (in == null) {
                return false;
            }
            boolean existed = Files.exists(target);
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return !existed;
        } catch (IOException e) {
            getLogger().warning("数据包文件写入失败: " + target + " " + e.getMessage());
            return false;
        }
    }

    public Enchantment trueLoyalty() {
        return trueLoyalty;
    }

    public boolean isTrueLoyaltyTrident(ItemStack item) {
        return item != null && item.getType() == Material.TRIDENT && trueLoyalty != null
                && item.containsEnchantment(trueLoyalty);
    }

    public ItemStack findHeldTrident(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isTrueLoyaltyTrident(main)) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isTrueLoyaltyTrident(off)) {
            return off;
        }
        return null;
    }

    public EquipmentSlot consumeHeldTrident(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isTrueLoyaltyTrident(main)) {
            consume(player, main, EquipmentSlot.HAND);
            return EquipmentSlot.HAND;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isTrueLoyaltyTrident(off)) {
            consume(player, off, EquipmentSlot.OFF_HAND);
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    private void consume(Player player, ItemStack item, EquipmentSlot slot) {
        ItemStack rest = item.clone();
        rest.setAmount(item.getAmount() - 1);
        if (rest.getAmount() <= 0) {
            rest = new ItemStack(Material.AIR);
        }
        if (slot == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(rest);
        } else {
            player.getInventory().setItemInOffHand(rest);
        }
    }

    public ItemStack trident() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        if (trueLoyalty != null) {
            item.addUnsafeEnchantment(trueLoyalty, 1);
        }
        return item;
    }

    public ItemStack book() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        if (trueLoyalty != null) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            meta.addStoredEnchant(trueLoyalty, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Random random() {
        return random;
    }

    public void grantAdvancement(Player player, String key, String criterion) {
        NamespacedKey advancementKey = NamespacedKey.fromString("bloodsoil:" + key);
        if (advancementKey == null) {
            return;
        }
        Advancement advancement = Bukkit.getAdvancement(advancementKey);
        if (advancement != null) {
            player.getAdvancementProgress(advancement).awardCriteria(criterion);
        }
    }

    public int cfg(String path, int def) {
        return getConfig().getInt(path, def);
    }

    public double cfg(String path, double def) {
        return getConfig().getDouble(path, def);
    }

    public String cfg(String path, String def) {
        return getConfig().getString(path, def);
    }

    public void spawnParticle(Particle particle, Location location, int count,
                              double dx, double dy, double dz, double speed) {
        if (count <= 0 || location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(particle, location, count, dx, dy, dz, speed);
    }

    public <T> void spawnParticle(Particle particle, Location location, int count, T data) {
        if (count <= 0 || location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(particle, location, count, data);
    }

    public void goldenRing(Location center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = i * 2.0 * Math.PI / points;
            Location p = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1,
                    new Particle.DustTransition(Color.fromRGB(0xFFD700), Color.WHITE, 1.1f));
        }
    }

    public void track(TrueLoyaltySequence sequence) {
        activeSequences.add(sequence);
    }

    public void untrack(TrueLoyaltySequence sequence) {
        activeSequences.remove(sequence);
    }
}
