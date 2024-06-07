package io.github.minus1over12.mcjctf;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.PortalType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A class that tracks the flag item in the player's inventory.
 */
public class FlagTracker implements Listener {
    /**
     * The key used to identify the flag item.
     */
    private final NamespacedKey isFlagKey;
    private final Logger logger;
    private final boolean allowEnd = false;
    private final QuitMode quitMode = QuitMode.DROP;
    Component name = Component.text("Flag");
    
    public FlagTracker(JavaPlugin plugin) {
        this.isFlagKey = new NamespacedKey(plugin, "flag");
        logger = plugin.getLogger();
    }
    
    /**
     * Check if the item is the flag item.
     *
     * @param item The item to check
     * @return True if the item is the flag item, false otherwise
     */
    private boolean isFlag(PersistentDataHolder item) {
        if (item == null) {
            return false;
        }
        PersistentDataContainer container = item.getPersistentDataContainer();
        return container.getOrDefault(isFlagKey, PersistentDataType.BOOLEAN, false);
    }
    
    @EventHandler
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (isFlag(event.getItem().getItemStack().getItemMeta())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void stopDespawn(ItemDespawnEvent event) {
        if (isFlag(event.getEntity().getItemStack().getItemMeta())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item pickedUpItem = event.getItem();
        if (isFlag(pickedUpItem)) {
            LivingEntity entity = event.getEntity();
            if (entity instanceof InventoryHolder holder &&
                    holder.getInventory() instanceof PlayerInventory inventory) {
                ItemStack oldHelmet = inventory.getHelmet();
                inventory.setHelmet(pickedUpItem.getItemStack());
                if (oldHelmet != null) {
                    inventory.addItem(oldHelmet);
                }
                entity.setGlowing(true);
                pickedUpItem.remove();
                logger.info("Flag picked up by " + entity.getName());
            }
            event.setCancelled(true); // Item was manually added, this prevents a dupe
        }
    }
    
    public void trackItem(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(isFlagKey, PersistentDataType.BOOLEAN, true);
        meta.itemName(name);
        meta.setEnchantmentGlintOverride(true);
        meta.setUnbreakable(true);
        meta.setFireResistant(true);
        meta.setMaxStackSize(1);
        meta.setRarity(ItemRarity.EPIC);
        item.setItemMeta(meta);
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (event.getDrops().stream().anyMatch(itemStack -> isFlag(itemStack.getItemMeta()))) {
            entity.setGlowing(false);
        } else if (isFlag(entity)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Item item && isFlag(item)) {
            item.setUnlimitedLifetime(true);
            item.setWillAge(false);
            item.customName(name);
            item.setCustomNameVisible(true);
            item.setInvulnerable(true);
            item.setPersistent(true);
            item.setGlowing(true);
            item.getPersistentDataContainer().set(isFlagKey, PersistentDataType.BOOLEAN, true);
            logger.info("Flag item on ground");
        }
    }
    
    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        // Prevent the flag from being teleported to the end, if not allowed
        Entity entity = event.getEntity();
        if ((!allowEnd && event.getPortalType().equals(PortalType.ENDER)) &&
                ((entity instanceof Item item && isFlag(item.getItemStack().getItemMeta())) ||
                        entity instanceof InventoryHolder holder &&
                                Arrays.stream(holder.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .anyMatch(itemStack -> isFlag(itemStack.getItemMeta())) ||
                        (isFlag(entity)))) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (Arrays.stream(event.getPlayer().getInventory().getContents()).filter(Objects::nonNull)
                .anyMatch(itemStack -> isFlag(itemStack.getItemMeta()))) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();
        ItemStack helmet = quitter.getInventory().getHelmet();
        if (helmet != null && isFlag(helmet.getItemMeta())) {
            // Acts on the player if they have the flag on logout to prevent it from being
            // un-stealable
            switch (quitMode) {
                case KILL -> quitter.setHealth(0);
                case DROP -> {
                    quitter.getWorld().dropItemNaturally(quitter.getLocation(),
                            quitter.getInventory().getHelmet());
                    quitter.getInventory().setHelmet(null);
                }
            }
            quitter.setGlowing(false);
            
        }
        Entity vehicle = quitter.getVehicle();
        if (vehicle != null) {
            vehicle.getPassengers().stream().filter(this::isFlag).forEach(Entity::leaveVehicle);
        }
    }
    
    public void trackEntity(Entity entity) {
        entity.getPersistentDataContainer().set(isFlagKey, PersistentDataType.BOOLEAN, true);
        entity.setGlowing(true);
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setPersistent(true);
    }
    
    public void trackEntity(UUID entityUUID) {
        Entity entity = Bukkit.getEntity(entityUUID);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found");
        }
        trackEntity(entity);
    }
    
    private enum QuitMode {
        KILL, DROP
    }
    
}