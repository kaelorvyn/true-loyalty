package com.bloodsoil.trueloyalty;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Boss;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DeathProtection;

public class ProtectionListener implements Listener {

    private final TrueLoyaltyPlugin plugin;
    private final Map<UUID, Set<TrueLoyaltySequence>> active = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastTriggerTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombatTick = new ConcurrentHashMap<>();

    public ProtectionListener(TrueLoyaltyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.isDead() || player.getHealth() <= 0 || isSameTickTrigger(player)) {
            return;
        }
        recordCombatSource(player, event);
        double effectiveHealth = player.getHealth() + player.getAbsorptionAmount();
        if (event.getFinalDamage() >= player.getHealth()) {
            debug("致命伤害事件: " + player.getName() + " cause=" + event.getCause()
                    + " finalDamage=" + event.getFinalDamage() + " health=" + player.getHealth()
                    + " absorption=" + player.getAbsorptionAmount()
                    + " held=" + (plugin.findHeldTrident(player) != null)
                    + " active=" + activeCount(player));
        }
        if (event.getFinalDamage() < effectiveHealth) {
            return;
        }
        if (!isCombatKill(event, player)) {
            debug("环境死亡不触发: " + player.getName() + " cause=" + event.getCause()
                    + (event instanceof EntityDamageByEntityEvent by ? " damager=" + by.getDamager().getType() : ""));
            return;
        }
        if (trigger(player, revengeFrom(event))) {
            lastTriggerTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isSameTickTrigger(player)) {
            return;
        }
        EntityDamageEvent last = player.getLastDamageCause();
        if (!isCombatKill(last, player)) {
            debug("环境死亡兜底不触发: " + player.getName()
                    + " cause=" + (last == null ? "null" : last.getCause().name())
                    + (last instanceof EntityDamageByEntityEvent by ? " damager=" + by.getDamager().getType() : ""));
            return;
        }
        boolean held = plugin.findHeldTrident(player) != null;
        debug("死亡兜底: " + player.getName() + " cancelled=" + event.isCancelled()
                + " health=" + player.getHealth() + " held=" + held
                + " cause=" + (last == null ? "null" : last.getCause().name()));
        if (!held) {
            return;
        }
        if (player.getHealth() > 0 && !player.isDead()) {
            return;
        }
        if (trigger(player, player.getKiller())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastCombatTick.remove(event.getPlayer().getUniqueId());
        cancelFor(event.getPlayer());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        for (Set<TrueLoyaltySequence> sequences : active.values()) {
            for (TrueLoyaltySequence sequence : sequences) {
                if (sequence.world().equals(event.getWorld())) {
                    sequence.cancel();
                }
            }
        }
    }

    @EventHandler
    public void onLightningDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LightningStrike bolt
                && event.getEntity() instanceof Player victim
                && isOwnBolt(bolt, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLightningFire(EntityCombustByEntityEvent event) {
        if (event.getCombuster() instanceof LightningStrike bolt
                && event.getEntity() instanceof Player victim
                && isOwnBolt(bolt, victim)) {
            event.setCancelled(true);
        }
    }

    private boolean isOwnBolt(LightningStrike bolt, Player victim) {
        return bolt.getScoreboardTags().contains(TrueLoyaltyPlugin.BOLT_TAG_PREFIX + victim.getUniqueId());
    }

    private Player revengeFrom(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent by) {
            Entity damager = by.getDamager();
            if (damager instanceof Player player) {
                return player;
            }
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean isCombatKill(EntityDamageEvent event, Player player) {
        if (event == null) {
            return false;
        }
        if (event instanceof EntityDamageByEntityEvent by) {
            if (isHostileDamager(by.getDamager())) {
                return true;
            }
            if (by.getDamager() instanceof Projectile projectile) {
                return isHostileDamager(shooterEntity(projectile.getShooter()));
            }
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL) {
            return false;
        }
        for (String indirect : plugin.getConfig().getStringList("trigger.indirect-combat-causes")) {
            if (indirect.equalsIgnoreCase(cause.name())) {
                return true;
            }
        }
        Long last = lastCombatTick.get(player.getUniqueId());
        return last != null && Bukkit.getCurrentTick() - last
                <= plugin.cfg("trigger.indirect-combat-window-ticks", 100);
    }

    private void recordCombatSource(Player player, EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent by) {
            if (isHostileDamager(by.getDamager())) {
                lastCombatTick.put(player.getUniqueId(), (long) Bukkit.getCurrentTick());
            } else if (by.getDamager() instanceof Projectile projectile
                    && isHostileDamager(shooterEntity(projectile.getShooter()))) {
                lastCombatTick.put(player.getUniqueId(), (long) Bukkit.getCurrentTick());
            }
        }
    }

    private boolean isHostileDamager(Entity entity) {
        if (entity instanceof ComplexEntityPart part) {
            return isHostileDamager(part.getParent());
        }
        return entity instanceof Monster || entity instanceof Player || entity instanceof Boss;
    }

    private Entity shooterEntity(ProjectileSource source) {
        return source instanceof Entity entity ? entity : null;
    }

    private boolean trigger(Player player, Player revenge) {
        ItemStack held = plugin.findHeldTrident(player);
        if (held == null) {
            debug("触发失败: 手中没有真·忠诚三叉戟 " + player.getName());
            return false;
        }
        ItemStack visual = held.clone();
        EquipmentSlot consumed = plugin.consumeHeldTrident(player);
        if (consumed == null) {
            return false;
        }
        plugin.grantAdvancement(player, "sea_god_dusk", "triggered");
        player.setHealth(1);
        playTotemEffects(player, visual, consumed);
        debug("真·忠诚已触发: " + player.getName()
                + " 复仇目标=" + (revenge == null ? "无" : revenge.getName()));
        AtomicReference<TrueLoyaltySequence> sequenceRef = new AtomicReference<>();
        TrueLoyaltySequence sequence = new TrueLoyaltySequence(plugin, player, revenge, () -> {
            Set<TrueLoyaltySequence> sequences = active.get(player.getUniqueId());
            TrueLoyaltySequence current = sequenceRef.get();
            if (sequences != null && current != null) {
                sequences.remove(current);
                if (sequences.isEmpty()) {
                    active.remove(player.getUniqueId());
                }
            }
        });
        sequenceRef.set(sequence);
        active.computeIfAbsent(player.getUniqueId(), key -> ConcurrentHashMap.newKeySet()).add(sequence);
        sequence.start();
        return true;
    }

    @SuppressWarnings("deprecation")
    private void playTotemEffects(Player player, ItemStack tridentVisual, EquipmentSlot slot) {
        Location loc = player.getLocation();
        plugin.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0),
                plugin.cfg("particles.trigger-totem", 60), 0.6, 0.8, 0.6, 0.1);
        plugin.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0),
                plugin.cfg("particles.trigger-endrod", 80), 1.0, 1.2, 1.0, 0.15);
        plugin.goldenRing(loc, 1.3, plugin.cfg("particles.trigger-ring", 24));
        player.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        ItemStack oldStack = slot == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand().clone()
                : player.getInventory().getItemInOffHand().clone();
        ItemStack floating = tridentVisual != null ? tridentVisual.clone() : new ItemStack(Material.TRIDENT);
        DeathProtection deathProtection = new ItemStack(Material.TOTEM_OF_UNDYING)
                .getData(DataComponentTypes.DEATH_PROTECTION);
        debug("图腾组件=" + (deathProtection != null));
        if (deathProtection != null) {
            floating.setData(DataComponentTypes.DEATH_PROTECTION, deathProtection);
        }
        debug("三叉戟组件=" + floating.hasData(DataComponentTypes.DEATH_PROTECTION));
        if (slot == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(floating.clone());
        } else {
            player.getInventory().setItemInOffHand(floating.clone());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playEffect(EntityEffect.TOTEM_RESURRECT);
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ItemStack current = slot == EquipmentSlot.HAND
                    ? player.getInventory().getItemInMainHand()
                    : player.getInventory().getItemInOffHand();
            if (current.getType() == Material.TRIDENT
                    && current.hasData(DataComponentTypes.DEATH_PROTECTION)) {
                if (slot == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(oldStack);
                } else {
                    player.getInventory().setItemInOffHand(oldStack);
                }
            }
        }, 3L);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        addEffect(player, PotionEffectType.ABSORPTION,
                plugin.cfg("effects.absorption-amplifier", 1), plugin.cfg("effects.absorption-seconds", 5));
        addEffect(player, PotionEffectType.REGENERATION,
                plugin.cfg("effects.regeneration-amplifier", 1), plugin.cfg("effects.regeneration-seconds", 45));
        addEffect(player, PotionEffectType.FIRE_RESISTANCE,
                plugin.cfg("effects.fire-resistance-amplifier", 0), plugin.cfg("effects.fire-resistance-seconds", 40));
        addEffect(player, PotionEffectType.RESISTANCE,
                plugin.cfg("effects.resistance-amplifier", 1), plugin.cfg("effects.resistance-seconds", 5));
        player.sendTitle("", plugin.cfg("titles.trigger", "休伤吾主！"), 10, 60, 20);
    }

    private void addEffect(Player player, PotionEffectType type, int amplifier, int seconds) {
        if (seconds > 0) {
            player.addPotionEffect(new PotionEffect(type, seconds * 20, Math.max(0, amplifier), false, true, true));
        }
    }

    public void cancelFor(Player player) {
        Set<TrueLoyaltySequence> sequences = active.remove(player.getUniqueId());
        if (sequences != null) {
            for (TrueLoyaltySequence sequence : sequences) {
                sequence.cancel();
            }
        }
    }

    private boolean isSameTickTrigger(Player player) {
        return lastTriggerTick.getOrDefault(player.getUniqueId(), -1) == Bukkit.getCurrentTick();
    }

    private int activeCount(Player player) {
        Set<TrueLoyaltySequence> sequences = active.get(player.getUniqueId());
        return sequences == null ? 0 : sequences.size();
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[debug] " + message);
        }
    }
}
