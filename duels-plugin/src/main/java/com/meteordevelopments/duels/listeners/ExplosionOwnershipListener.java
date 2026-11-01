package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks ownership of EnderCrystals by tagging spawned crystals with the placing player's UUID.
 */
public class ExplosionOwnershipListener implements Listener {

    // Stored in the entity's PersistentDataContainer rather than Bukkit metadata, which is never cleared when the entity is removed
    private static final NamespacedKey OWNER_KEY = new NamespacedKey(DuelsPlugin.getInstance(), "explosion_owner");
    private final Map<UUID, Long> recentCrystalUse = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentTntMinecartUse = new ConcurrentHashMap<>();
    // Keyed by world UID so unloaded worlds are not kept in memory
    private final Map<UUID, UUID> recentBlockExplosionTrigger = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentBlockExplosionTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> recentMinecartExplosionOwner = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentMinecartExplosionTime = new ConcurrentHashMap<>();

    public ExplosionOwnershipListener(final DuelsPlugin plugin) {
        plugin.registerPreListener(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        // Only consider main hand interactions
        if (event.getHand() != EquipmentSlot.HAND) return;

        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();

        // Record placing of crystal / TNT minecart via item in hand
        if (item != null) {
            if (item.getType() == Material.END_CRYSTAL) {
                recentCrystalUse.put(player.getUniqueId(), System.currentTimeMillis());
            } else if (item.getType() == Material.TNT_MINECART) {
                recentTntMinecartUse.put(player.getUniqueId(), System.currentTimeMillis());
            }
        }

        // Bed / respawn anchor explosions are triggered by interacting with the placed block
        if (event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();
            if (type == Material.RESPAWN_ANCHOR || type.name().endsWith("_BED")) {
                recentBlockExplosionTrigger.put(player.getWorld().getUID(), player.getUniqueId());
                recentBlockExplosionTime.put(player.getWorld().getUID(), System.currentTimeMillis());
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        recentCrystalUse.remove(uuid);
        recentTntMinecartUse.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(final EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            // Not a crystal; check TNT minecart
            if (event.getEntity() instanceof ExplosiveMinecart minecart) {
                Player owner = findRecentUser(minecart.getWorld(), recentTntMinecartUse);
                if (owner != null) {
                    setOwner(minecart, owner.getUniqueId());
                }
            }
            return;
        }
        // Find a player who very recently used an end crystal
        Player owner = findRecentUser(crystal.getWorld(), recentCrystalUse);
        if (owner != null) {
            setOwner(crystal, owner.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleCreate(final VehicleCreateEvent event) {
        // Some servers fire VehicleCreateEvent for minecart placement; tag owner here too
        if (event.getVehicle() instanceof ExplosiveMinecart minecart) {
            Player owner = findRecentUser(minecart.getWorld(), recentTntMinecartUse);
            if (owner != null) {
                setOwner(minecart, owner.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart minecart)) {
            return;
        }
        // Try to get owner from metadata first
        UUID ownerUuid = getTntMinecartOwner(minecart);
        
        // If no metadata, try to find recent user as fallback
        if (ownerUuid == null) {
            Player recentUser = findRecentUser(minecart.getWorld(), recentTntMinecartUse);
            if (recentUser != null) {
                ownerUuid = recentUser.getUniqueId();
            }
        }
        
        if (ownerUuid != null) {
            UUID worldId = minecart.getWorld().getUID();
            recentMinecartExplosionOwner.put(worldId, ownerUuid);
            recentMinecartExplosionTime.put(worldId, System.currentTimeMillis());
        }
    }

    private Player findRecentUser(final World world, final Map<UUID, Long> activityMap) {
        long now = System.currentTimeMillis();
        for (Player p : world.getPlayers()) {
            Long lastUse = activityMap.get(p.getUniqueId());
            if (lastUse != null && (now - lastUse) <= 1500) {
                return p;
            }
        }
        return null;
    }

    private static void setOwner(final PersistentDataHolder entity, final UUID owner) {
        entity.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, owner.toString());
    }

    private static UUID getOwner(final PersistentDataHolder entity) {
        final String uuidStr = entity.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (uuidStr != null) {
            try {
                return UUID.fromString(uuidStr);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static UUID getCrystalOwner(final EnderCrystal crystal) {
        return getOwner(crystal);
    }

    public static UUID getTntMinecartOwner(final ExplosiveMinecart minecart) {
        return getOwner(minecart);
    }

    public static UUID getRecentBlockExplosionTrigger(final World world) {
        return world == null ? null : INSTANCE.recentBlockExplosionTrigger.get(world.getUID());
    }

    public static boolean isRecentBlockExplosionInWorld(final World world, long windowMs) {
        if (world == null) return false;
        Long time = INSTANCE.recentBlockExplosionTime.get(world.getUID());
        return time != null && (System.currentTimeMillis() - time) <= windowMs;
    }

    public static UUID getRecentMinecartExplosionOwner(final World world) {
        return world == null ? null : INSTANCE.recentMinecartExplosionOwner.get(world.getUID());
    }

    public static boolean isRecentMinecartExplosionInWorld(final World world, long windowMs) {
        if (world == null) return false;
        Long time = INSTANCE.recentMinecartExplosionTime.get(world.getUID());
        return time != null && (System.currentTimeMillis() - time) <= windowMs;
    }

    // Simple instance holder for static access to maps
    private static ExplosionOwnershipListener INSTANCE;

    {
        INSTANCE = this;
    }
}
