package com.bloodsoil.trueloyalty;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Trident;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class TrueLoyaltySequence extends BukkitRunnable {

    private enum Phase {
        RING, FLY, CONVERGE, HOLD, HOMING
    }

    private static final Vector UP = new Vector(0, 1, 0);
    private static final double RING_Y = 1.0;

    private final TrueLoyaltyPlugin plugin;
    private final Player owner;
    private final Player revenge;
    private final World world;
    private final Location origin;
    private final Runnable onEnd;
    private final List<Phantom> phantoms = new ArrayList<>();

    private final int count;
    private final double startRadius;
    private final double sphereTopHeight;
    private final int arcTicks;
    private final int convergeTicks;
    private final int holdTicks;
    private final int ringHoldTicks;
    private final double ringRotationTurns;
    private final double ringDamagePerTick;
    private final int ringDamageEveryTicks;
    private final double ringDamageBand;
    private final double ringKnockback;
    private final double sphereSpiralTurns;
    private final double homingSpeed;
    private final double homingHitDistance;
    private final double damagePerTrident;
    private final int strikeWindowTicks;
    private final int piercingLevel;
    private final boolean lightning;
    private final double targetRadius;
    private final double targetVerticalRange;
    private final double bossSearchRadiusY;
    private final List<Phantom> pendingStrikes = new ArrayList<>();

    private Phase phase = Phase.RING;
    private Location flyOrigin;
    private int tick;
    private int strikeWindowStart = -1;
    private boolean homingInitialized;
    private boolean targetsPending = true;
    private int homingRetryTicks;
    private boolean ended;
    private boolean cancelled;

    public TrueLoyaltySequence(TrueLoyaltyPlugin plugin, Player owner, Player revenge, Runnable onEnd) {
        this.plugin = plugin;
        this.owner = owner;
        this.revenge = revenge;
        this.world = owner.getWorld();
        this.origin = owner.getLocation().clone();
        this.onEnd = onEnd;
        this.count = Math.max(1, plugin.cfg("phantoms.count", 128));
        this.startRadius = Math.max(1.0, plugin.cfg("phantoms.start-radius", 4.0));
        this.sphereTopHeight = Math.max(4.0, plugin.cfg("phantoms.sphere-top-height", 15.0));
        this.arcTicks = Math.max(5, plugin.cfg("phantoms.arc-duration-ticks", 44));
        this.convergeTicks = Math.max(5, plugin.cfg("phantoms.converge-duration-ticks", 18));
        this.holdTicks = Math.max(0, plugin.cfg("phantoms.hold-ticks", 8));
        this.ringHoldTicks = Math.max(1, plugin.cfg("phantoms.ring-hold-ticks", 100));
        this.ringRotationTurns = Math.max(0, plugin.cfg("phantoms.ring-rotation-turns", 2.5));
        this.ringDamagePerTick = Math.max(0, plugin.cfg("phantoms.ring-damage-per-tick", 1.5));
        this.ringDamageEveryTicks = Math.max(1, plugin.cfg("phantoms.ring-damage-every-ticks", 4));
        this.ringDamageBand = Math.max(0.5, plugin.cfg("phantoms.ring-damage-band", 1.5));
        this.ringKnockback = Math.max(0, plugin.cfg("phantoms.ring-knockback", 0.8));
        this.sphereSpiralTurns = Math.max(0, plugin.cfg("phantoms.sphere-spiral-turns", 0.6));
        this.homingSpeed = Math.max(0.3, plugin.cfg("phantoms.homing-speed", 3.0));
        this.homingHitDistance = Math.max(0.3, plugin.cfg("phantoms.homing-hit-distance", 5.0));
        this.damagePerTrident = Math.max(0, plugin.cfg("phantoms.damage-per-trident", 15.0));
        this.strikeWindowTicks = Math.max(10, plugin.cfg("phantoms.strike-window-ticks", 100));
        this.piercingLevel = Math.max(0, plugin.cfg("phantoms.piercing-level", 3));
        this.lightning = plugin.getConfig().getBoolean("phantoms.lightning", true);
        this.targetRadius = Math.max(1.0, plugin.cfg("phantoms.target-radius", 64.0));
        this.targetVerticalRange = Math.max(1.0, plugin.cfg("phantoms.target-vertical-range", 8.0));
        this.bossSearchRadiusY = Math.max(16.0, plugin.cfg("phantoms.boss-search-radius-y", 256.0));
    }

    public World world() {
        return world;
    }

    public void start() {
        plugin.track(this);
        spawnPhantoms();
        runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnPhantoms() {
        List<Vector> ring = Geometry.ring(count);
        for (int i = 0; i < count; i++) {
            Vector dir = ring.get(i);
            Vector start = origin.toVector().clone().add(new Vector(0, RING_Y, 0))
                    .add(dir.clone().multiply(startRadius));
            double azimuth = Math.atan2(dir.getZ(), dir.getX());

            Trident trident = world.spawnArrow(toLocation(start), dir.clone(), 0.6f, 0f, Trident.class);
            if (trident == null) {
                continue;
            }
            if (piercingLevel > 0) {
                ItemStack tridentItem = new ItemStack(org.bukkit.Material.TRIDENT);
                tridentItem.addUnsafeEnchantment(Enchantment.PIERCING, piercingLevel);
                trident.setItem(tridentItem);
                trident.setPierceLevel(piercingLevel);
            }
            trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            trident.setGravity(false);
            trident.setNoPhysics(true);
            trident.setSilent(true);
            trident.setInvulnerable(true);
            trident.setPersistent(false);
            trident.setGlowing(true);
            trident.setDamage(0);
            trident.setCritical(false);
            trident.setKnockbackStrength(0);
            trident.setPierceLevel(0);
            trident.setShooter(owner, false);
            trident.setVelocity(dir.clone().multiply(1.0));

            Phantom phantom = new Phantom(trident, start, convergePoint(), azimuth);
            phantoms.add(phantom);

            plugin.spawnParticle(Particle.END_ROD, toLocation(start),
                    plugin.cfg("particles.spawn-per-trident-endrod", 8), 0.3, 0.3, 0.3, 0.05);
            plugin.spawnParticle(Particle.ELECTRIC_SPARK, toLocation(start),
                    plugin.cfg("particles.spawn-per-trident-spark", 3), 0.2, 0.2, 0.2, 0.05);
        }
        plugin.spawnParticle(Particle.END_ROD, origin,
                plugin.cfg("particles.sphere-burst-endrod", 120), 3.0, 3.0, 3.0, 0.2);
        plugin.spawnParticle(Particle.CRIT, origin,
                plugin.cfg("particles.sphere-burst-crit", 60), 2.5, 2.5, 2.5, 0.25);
        world.playSound(origin, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.8f);
    }

    @Override
    public void run() {
        if (ended) {
            return;
        }
        if (!owner.isOnline() || owner.isDead()) {
            cancel();
            return;
        }
        tick++;
        try {
            switch (phase) {
                case RING -> {
                    ringHold();
                    if (tick >= ringHoldTicks) {
                        flyOrigin = owner.getLocation().clone();
                        phase = Phase.FLY;
                        tick = 0;
                        debug("阶段 FLY: 幻影=" + phantoms.size());
                    }
                }
                case FLY -> {
                    fly();
                    if (tick >= arcTicks) {
                        phase = Phase.CONVERGE;
                        tick = 0;
                        debug("阶段 CONVERGE: 幻影=" + phantoms.size());
                    }
                }
                case CONVERGE -> {
                    converge();
                    if (tick >= convergeTicks) {
                        phase = Phase.HOLD;
                        tick = 0;
                        debug("阶段 HOLD: 幻影=" + phantoms.size());
                    }
                }
                case HOLD -> {
                    if (tick == 1) {
                        convergeBurst();
                    }
                    if (tick >= holdTicks) {
                        phase = Phase.HOMING;
                        tick = 0;
                        debug("阶段 HOMING: 幻影=" + phantoms.size());
                    }
                }
                case HOMING -> {
                    if (!homingInitialized) {
                        homingInitialized = true;
                        homingRetryTicks = 0;
                        assignTargets();
                        if (ended) {
                            return;
                        }
                    }
                    if (targetsPending) {
                        homingRetryTicks++;
                        assignTargets();
                        if (ended) {
                            return;
                        }
                        if (targetsPending && homingRetryTicks >= 20) {
                            debug("追踪阶段无目标，技能结束");
                            finish(true);
                            return;
                        }
                        return;
                    }
                    homing();
                    if (phantoms.isEmpty()) {
                        finish(true);
                    }
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("真·忠诚技能异常: " + e.getMessage());
            cancel();
        }
    }

    private void ringHold() {
        double angular = ringRotationTurns * 2.0 * Math.PI / ringHoldTicks;
        boolean damageTick = tick % ringDamageEveryTicks == 0;
        List<LivingEntity> ringTargets = damageTick ? ringTargets() : List.of();
        Vector center = owner.getLocation().toVector().clone().add(new Vector(0, RING_Y, 0));
        for (Phantom phantom : phantoms) {
            double angle = phantom.azimuth + angular * tick;
            Vector pos = center.clone()
                    .add(new Vector(Math.cos(angle) * startRadius, 0, Math.sin(angle) * startRadius));
            phantom.position = pos.clone();
            phantom.entity.teleport(toLocation(pos));
            Vector outward = pos.clone().subtract(center);
            outward.setY(0);
            if (outward.lengthSquared() > 1.0E-6) {
                outward.normalize();
            }
            phantom.entity.setVelocity(outward.multiply(0.5));
            if (damageTick) {
                for (LivingEntity target : ringTargets) {
                    ringHit(target);
                }
            }
        }
    }

    private void fly() {
        int trailEndrod = plugin.cfg("particles.trail-endrod", 3);
        int sparkEvery = Math.max(1, plugin.cfg("particles.trail-spark-every-ticks", 3));
        Location base = flyBase();
        double[] geometry = sphereGeometry(base);
        double centerY = base.getY() + geometry[0];
        double radius = geometry[1];
        double ratio = (RING_Y - geometry[0]) / radius;
        double startPhi = Math.acos(Math.max(-1.0, Math.min(1.0, ratio)));
        Vector center = new Vector(base.getX(), centerY, base.getZ());
        for (Phantom phantom : phantoms) {
            double t = Math.min(1.0, tick / (double) arcTicks);
            double t2 = Math.min(1.0, (tick + 1) / (double) arcTicks);
            Vector pos = spherePoint(center, radius, startPhi, phantom.azimuth, t);
            Vector next = spherePoint(center, radius, startPhi, phantom.azimuth, t2);
            phantom.position = pos.clone();
            phantom.entity.setVelocity(next.clone().subtract(pos));
            phantom.entity.teleport(toLocation(phantom.position));
            Location loc = toLocation(phantom.position);
            plugin.spawnParticle(Particle.END_ROD, loc, trailEndrod, 0.08, 0.08, 0.08, 0.02);
            if (tick % sparkEvery == 0) {
                plugin.spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        int glow = Math.max(1, plugin.cfg("particles.sphere-glow", 24));
        for (int i = 0; i < glow; i++) {
            Vector dir = Geometry.fibonacciSphere(glow).get(i);
            double t = 0.2 + 0.6 * (i / (double) glow);
            Vector p = spherePoint(center, radius, startPhi, Math.atan2(dir.getZ(), dir.getX()), t);
            plugin.spawnParticle(Particle.END_ROD, toLocation(p), 1, 0.08, 0.08, 0.08, 0.01);
        }
        if (tick % 8 == 0) {
            world.playSound(base, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.5f, 1.1f);
        }
    }

    private Vector spherePoint(Vector center, double radius, double startPhi, double azimuth, double t) {
        double phi = startPhi * (1 - t);
        double theta = azimuth + sphereSpiralTurns * 2.0 * Math.PI * t;
        double sinPhi = Math.sin(phi);
        return center.clone().add(new Vector(
                radius * sinPhi * Math.cos(theta),
                radius * Math.cos(phi),
                radius * sinPhi * Math.sin(theta)));
    }

    private List<LivingEntity> ringTargets() {
        List<LivingEntity> targets = new ArrayList<>();
        double reach = startRadius + ringDamageBand;
        Location centerLoc = owner.getLocation();
        for (Entity entity : world.getNearbyEntities(centerLoc, reach, 4.0, reach)) {
            if (!(entity instanceof LivingEntity living) || living == owner || living.isDead() || !living.isValid()) {
                continue;
            }
            Vector d = living.getLocation().toVector().clone().subtract(centerLoc.toVector());
            double horizontal = Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
            if (Math.abs(horizontal - startRadius) <= ringDamageBand) {
                targets.add(living);
            }
        }
        return targets;
    }

    private void ringHit(LivingEntity target) {
        if (target.isDead() || !target.isValid()) {
            return;
        }
        if (ringDamagePerTick > 0) {
            target.damage(ringDamagePerTick, owner);
        }
        Vector away = target.getLocation().toVector().clone().subtract(owner.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() > 1.0E-6) {
            away.normalize();
        }
        away.multiply(ringKnockback).setY(0.3);
        target.setVelocity(away);
        plugin.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.05);
    }

    private void converge() {
        double progress = Math.min(1.0, tick / (double) convergeTicks);
        Vector converge = convergePoint();
        for (Phantom phantom : phantoms) {
            Vector delta = converge.clone().subtract(phantom.position).multiply(progress);
            Vector pos = phantom.position.clone().add(delta);
            double angle = progress * Math.PI * 2;
            Vector spiral = Geometry.tangent(phantom.start.clone().subtract(flyBase().toVector()))
                    .multiply(Math.sin(angle) * (1 - progress) * 1.2);
            pos.add(spiral);
            move(phantom, pos);
            plugin.spawnParticle(Particle.END_ROD, toLocation(phantom.position), 2, 0.05, 0.05, 0.05, 0.02);
        }
    }

    private void convergeBurst() {
        Location p = toLocation(convergePoint());
        plugin.spawnParticle(Particle.TOTEM_OF_UNDYING, p,
                plugin.cfg("particles.converge-totem", 40), 0.8, 0.8, 0.8, 0.12);
        plugin.spawnParticle(Particle.END_ROD, p,
                plugin.cfg("particles.converge-endrod", 50), 1.0, 1.0, 1.0, 0.15);
        plugin.spawnParticle(Particle.EXPLOSION, p, 1, 0, 0, 0, 0);
        world.playSound(p, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.9f);
    }

    private void assignTargets() {
        List<LivingEntity> bosses = new ArrayList<>();
        List<LivingEntity> others = new ArrayList<>();
        Location center = owner.getLocation();
        if (revenge != null && revenge.isValid() && !revenge.isDead()) {
            others.add(revenge);
        }
        double bossRadius = Math.max(16.0, plugin.cfg("phantoms.boss-search-radius", 64.0));
        for (Entity entity : world.getNearbyEntities(center, bossRadius, bossSearchRadiusY, bossRadius)) {
            if (entity instanceof Boss && entity instanceof LivingEntity living
                    && !living.isDead() && living.isValid()) {
                bosses.add(living);
            }
        }
        java.util.Collection<Entity> nearby = world.getNearbyEntities(center, targetRadius, targetVerticalRange, targetRadius);
        debug("索敌扫描: 附近实体=" + nearby.size() + " 原点=" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ());
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living) || living == owner || living.isDead() || !living.isValid()) {
                continue;
            }
            if (living instanceof Player) {
                continue;
            }
            if (living instanceof Tameable tame && tame.isTamed()) {
                continue;
            }
            if (isHostile(living) && !(living instanceof Boss)) {
                others.add(living);
            }
        }
        if (bosses.isEmpty() && others.isEmpty()) {
            return;
        }
        targetsPending = false;
        double percent = Math.max(0.0, Math.min(1.0, plugin.cfg("phantoms.boss-priority-percent", 0.8)));
        int bossSlots = bosses.isEmpty() ? 0 : (int) Math.round(phantoms.size() * percent);
        int otherSlots = phantoms.size() - bossSlots;
        debug("锁敌完成: boss=" + bosses.size() + " 其他=" + others.size()
                + " boss槽=" + bossSlots + " 其他槽=" + otherSlots);
        for (int i = 0; i < phantoms.size(); i++) {
            Phantom phantom = phantoms.get(i);
            LivingEntity target;
            if (i < bossSlots) {
                target = bosses.get(i % bosses.size());
            } else if (!others.isEmpty()) {
                target = others.get((i - bossSlots) % others.size());
            } else {
                target = bosses.get(i % bosses.size());
            }
            phantom.target = target;
            phantom.velocity = new Vector(plugin.random().nextDouble() - 0.5,
                    plugin.random().nextDouble() * 0.4, plugin.random().nextDouble() - 0.5)
                    .normalize().multiply(0.3);
        }
    }

    private boolean isHostile(LivingEntity entity) {
        if (entity instanceof Monster) {
            return true;
        }
        EntityType type = entity.getType();
        return type == EntityType.SLIME || type == EntityType.MAGMA_CUBE || type == EntityType.SHULKER
                || type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN;
    }

    private void homing() {
        double hitDistance = homingHitDistance;
        int trailEndrod = plugin.cfg("particles.homing-endrod", 2);
        int trailCrit = plugin.cfg("particles.homing-crit", 1);
        int targetMark = plugin.cfg("particles.target-mark", 2);
        int maxLife = Math.max(20, plugin.cfg("phantoms.homing-max-ticks", 120));
        List<Phantom> done = new ArrayList<>();
        for (Phantom phantom : phantoms) {
            if (phantom.pending) {
                LivingEntity target = phantom.target;
                if (target != null && target.isValid() && !target.isDead() && target.getWorld() != world) {
                    vanish(phantom);
                    done.add(phantom);
                    continue;
                }
                Location eye = target != null && target.isValid() && !target.isDead()
                        ? target.getEyeLocation().clone() : phantom.lastTargetLocation;
                if (eye == null) {
                    vanish(phantom);
                    done.add(phantom);
                    continue;
                }
                phantom.lastTargetLocation = eye.clone();
                homeMove(phantom, eye);
                continue;
            }
            if (phantom.life++ >= maxLife && !(phantom.target instanceof Player)) {
                vanish(phantom);
                done.add(phantom);
                continue;
            }
            LivingEntity target = phantom.target;
            if (target != null && target.isValid() && !target.isDead() && target.getWorld() != world) {
                vanish(phantom);
                done.add(phantom);
                continue;
            }
            Location eye;
            if (target != null && target.isValid() && !target.isDead()) {
                eye = target.getEyeLocation();
                phantom.lastTargetLocation = eye.clone();
            } else {
                eye = phantom.lastTargetLocation;
            }
            if (eye == null) {
                vanish(phantom);
                done.add(phantom);
                continue;
            }
            boolean forceStrike = false;
            if (target != null && target.isValid() && !target.isDead()) {
                if (isBoss(target) && phantom.life > 60) {
                    forceStrike = true;
                } else if (!isBoss(target) && phantom.life > 120
                        && phantom.position.distanceSquared(eye.toVector()) < 16 * 16) {
                    forceStrike = true;
                }
            }
            if (forceStrike || isInHitRange(phantom, target, eye)) {
                queueStrike(phantom, eye);
                continue;
            }
            homeMove(phantom, eye);
            Location loc = toLocation(phantom.position);
            plugin.spawnParticle(Particle.END_ROD, loc, trailEndrod, 0.06, 0.06, 0.06, 0.02);
            plugin.spawnParticle(Particle.CRIT, loc, trailCrit, 0.06, 0.06, 0.06, 0.02);
            plugin.spawnParticle(Particle.TOTEM_OF_UNDYING, eye, targetMark, 0.15, 0.15, 0.15, 0.01);
        }
        drainStrikes(done);
        phantoms.removeAll(done);
    }

    private void homeMove(Phantom phantom, Location eye) {
        Vector desired = eye.toVector().clone().subtract(phantom.position);
        if (desired.lengthSquared() > 1.0E-6) {
            desired.normalize().multiply(homingSpeed);
        }
        phantom.velocity = phantom.velocity.clone().multiply(0.90).add(desired.multiply(0.18));
        if (phantom.velocity.lengthSquared() > homingSpeed * homingSpeed) {
            phantom.velocity.normalize().multiply(homingSpeed);
        }
        Vector pos = phantom.position.clone().add(phantom.velocity);
        move(phantom, pos);
    }

    private void queueStrike(Phantom phantom, Location eye) {
        if (phantom.pending) {
            return;
        }
        phantom.pending = true;
        phantom.lastTargetLocation = eye.clone();
        pendingStrikes.add(phantom);
    }

    private void drainStrikes(List<Phantom> done) {
        if (strikeWindowStart < 0 && !pendingStrikes.isEmpty()) {
            strikeWindowStart = tick;
        }
        if (strikeWindowStart < 0) {
            return;
        }
        int elapsed = tick - strikeWindowStart;
        int remainingTicks = Math.max(1, strikeWindowTicks - elapsed);
        int perTick = Math.max(1, (int) Math.ceil(pendingStrikes.size() / (double) remainingTicks));
        for (int i = 0; i < perTick && !pendingStrikes.isEmpty(); i++) {
            Phantom phantom = pendingStrikes.remove(0);
            LivingEntity target = phantom.target;
            Location eye = phantom.lastTargetLocation != null ? phantom.lastTargetLocation
                    : (target != null && target.isValid() ? target.getEyeLocation() : null);
            if (eye != null) {
                strike(phantom, target, eye);
            } else {
                vanish(phantom);
            }
            done.add(phantom);
        }
    }

    private boolean isInHitRange(Phantom phantom, LivingEntity target, Location eye) {
        double hitDistance = homingHitDistance;
        if (target == null || !target.isValid() || target.isDead()) {
            return phantom.position.distanceSquared(eye.toVector()) < hitDistance * hitDistance;
        }
        BoundingBox box = target.getBoundingBox().expand(hitDistance);
        if (box.contains(phantom.position)) {
            return true;
        }
        if (target instanceof org.bukkit.entity.EnderDragon dragon) {
            for (org.bukkit.entity.ComplexEntityPart part : dragon.getParts()) {
                if (part.getBoundingBox().expand(hitDistance).contains(phantom.position)) {
                    return true;
                }
            }
        }
        return phantom.position.distanceSquared(eye.toVector()) < hitDistance * hitDistance;
    }

    private boolean isBoss(LivingEntity target) {
        return target instanceof Boss || target instanceof org.bukkit.entity.EnderDragon
                || target instanceof org.bukkit.entity.Wither;
    }

    private void strike(Phantom phantom, LivingEntity target, Location eye) {
        if (damagePerTrident > 0 && target != null && target.isValid() && !target.isDead()
                && !target.isInvulnerable()) {
            double before = target.getHealth() + target.getAbsorptionAmount();
            target.setNoDamageTicks(0);
            target.damage(damagePerTrident, owner);
            double after = target.getHealth() + target.getAbsorptionAmount();
            if (after >= before && !target.isDead()) {
                forceDamage(target, damagePerTrident);
            }
        }
        plugin.spawnParticle(Particle.EXPLOSION, eye, 1, 0, 0, 0, 0);
        plugin.spawnParticle(Particle.CRIT, eye,
                plugin.cfg("particles.hit-crit", 40), 0.6, 0.6, 0.6, 0.2);
        plugin.spawnParticle(Particle.END_ROD, eye,
                plugin.cfg("particles.hit-endrod", 30), 0.8, 0.8, 0.8, 0.15);
        plugin.spawnParticle(Particle.TOTEM_OF_UNDYING, eye,
                plugin.cfg("particles.hit-totem", 15), 0.5, 0.5, 0.5, 0.1);
        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        world.playSound(eye, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
        if (lightning) {
            LightningStrike bolt = world.strikeLightning(target.getLocation());
            if (bolt != null) {
                bolt.addScoreboardTag(TrueLoyaltyPlugin.BOLT_TAG_PREFIX + owner.getUniqueId());
            }
        }
        vanish(phantom);
    }

    private void forceDamage(LivingEntity target, double amount) {
        try {
            Object handle = target.getClass().getMethod("getHandle").invoke(target);
            Object ownerHandle = owner.getClass().getMethod("getHandle").invoke(owner);
            Class<?> livingClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> damageSourceClass = Class.forName("net.minecraft.world.damagesource.DamageSource");
            Object level = handle.getClass().getMethod("level").invoke(handle);
            Object damageSources = level.getClass().getMethod("damageSources").invoke(level);
            Object source = damageSources.getClass().getMethod("mobAttack", livingClass)
                    .invoke(damageSources, ownerHandle);
            handle.getClass().getMethod("actuallyHurt", damageSourceClass, float.class)
                    .invoke(handle, source, (float) amount);
        } catch (Exception e) {
            plugin.getLogger().warning("真·忠诚强制结算伤害失败: " + e.getMessage());
        }
    }

    private void vanish(Phantom phantom) {
        Location loc = toLocation(phantom.position);
        debug("幻影消失: 剩余=" + (phantoms.size() - 1));
        plugin.spawnParticle(Particle.END_ROD, loc,
                plugin.cfg("particles.vanish-endrod", 10), 0.4, 0.4, 0.4, 0.08);
        plugin.spawnParticle(Particle.TOTEM_OF_UNDYING, loc,
                plugin.cfg("particles.vanish-totem", 5), 0.3, 0.3, 0.3, 0.06);
        phantom.entity.remove();
    }

    private void finish(boolean completed) {
        if (ended) {
            return;
        }
        ended = true;
        for (Phantom phantom : phantoms) {
            vanish(phantom);
        }
        phantoms.clear();
        if (completed && owner.isOnline()) {
            Location loc = owner.getLocation();
            plugin.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0),
                    plugin.cfg("particles.end-totem", 50), 0.8, 1.0, 0.8, 0.12);
            plugin.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0),
                    plugin.cfg("particles.end-endrod", 60), 1.0, 1.2, 1.0, 0.15);
            plugin.goldenRing(loc, 1.6, 24);
            world.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.1f);
            owner.sendTitle(plugin.cfg("titles.end", "三叉戟为你耗尽力量"), "", 10, 50, 20);
        }
        cancel();
    }

    @Override
    public void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        ended = true;
        for (Phantom phantom : phantoms) {
            phantom.entity.remove();
        }
        phantoms.clear();
        plugin.untrack(this);
        if (onEnd != null) {
            onEnd.run();
        }
        super.cancel();
    }

    private void move(Phantom phantom, Vector position) {
        Vector previous = phantom.position;
        phantom.position = position.clone();
        phantom.entity.setVelocity(position.clone().subtract(previous));
        phantom.entity.teleport(toLocation(phantom.position));
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    private Vector convergePoint() {
        Location base = flyBase();
        return base.toVector().clone().add(new Vector(0, sphereTopHeight, 0));
    }

    private double[] sphereGeometry(Location base) {
        double topY = sphereTopHeight;
        double ringY = RING_Y;
        double centerY = (topY * topY - ringY * ringY - startRadius * startRadius)
                / (2.0 * (topY - ringY));
        return new double[] { centerY, topY - centerY };
    }

    private Location flyBase() {
        return flyOrigin != null ? flyOrigin : origin;
    }

    private Location toLocation(Vector vector) {
        return new Location(world, vector.getX(), vector.getY(), vector.getZ());
    }

    private Location toLocation(Vector vector, float yaw, float pitch) {
        return new Location(world, vector.getX(), vector.getY(), vector.getZ(), yaw, pitch);
    }

    private static final class Phantom {
        final Trident entity;
        final Vector start;
        final Vector end;
        final double azimuth;
        Vector position;
        Vector velocity = new Vector();
        LivingEntity target;
        Location lastTargetLocation;
        boolean pending;
        int life;

        Phantom(Trident entity, Vector start, Vector end, double azimuth) {
            this.entity = entity;
            this.start = start;
            this.end = end;
            this.azimuth = azimuth;
            this.position = start.clone();
        }
    }
}
