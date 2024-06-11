package io.github.minus1over12.ctfbuddy;

import io.papermc.paper.util.Tick;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.PortalType;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
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
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * A class that tracks the flag item in the player's inventory.
 *
 * @author War Pigeon
 */
public class FlagTracker implements Listener {
    /**
     * Noise used for flag getting picked up.
     */
    private static final String ENTITY_ALLAY_ITEM_GIVEN = "entity.allay.item_given";
    /**
     * String used in messages about flag being picked up by an entity.
     */
    private static final String FLAG_PICKED_UP_BY = "Flag picked up by ";
    /**
     * Music played when a flag is picked up.
     */
    private static final String FLAG_MUSIC = "music.dragon";
    /**
     * The key used to identify the flag item.
     */
    private final NamespacedKey isFlagKey;
    /**
     * Logger provided by Bukkit.
     */
    private final Logger logger;
    /**
     * Indicates if flags are allowed to travel to the end. Flags in the end can be destroyed by the
     * void.
     */
    private final boolean allowEnd;
    /**
     * The action to use when a player quits with the flag.
     */
    private final QuitMode quitMode;
    /**
     * Reference to the plugin class. Needed for scheduling tasks.
     */
    private final Plugin plugin;
    
    /**
     * Toggles use of firework "beacons" every minute.
     */
    private final boolean particleBeacon;
    
    /**
     * A set of entities that are known to be flags.
     */
    private final Set<Entity> knownFlagEntities = Collections.synchronizedSet(new HashSet<>());
    
    /**
     * Create a new FlagTracker.
     *
     * @param plugin The plugin that this FlagTracker is associated with.
     */
    protected FlagTracker(JavaPlugin plugin) {
        this.isFlagKey = new NamespacedKey(plugin, "flag");
        logger = plugin.getLogger();
        FileConfiguration config = plugin.getConfig();
        allowEnd = config.getBoolean("allowEnd");
        particleBeacon = config.getBoolean("useParticleBeacon");
        quitMode = QuitMode.valueOf(config.getString("quitMode", "DROP").trim().toUpperCase());
        this.plugin = plugin;
        if (particleBeacon) {
            Bukkit.getWorlds().forEach(world -> processPotentialFlagEntities(world.getEntities()));
        }
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
        return item.getPersistentDataContainer()
                .getOrDefault(isFlagKey, PersistentDataType.BOOLEAN, false);
    }
    
    /**
     * Prevents hoppers from picking up a flag.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (isFlag(event.getItem().getItemStack().getItemMeta())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevents the flag from despawning.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void stopDespawn(ItemDespawnEvent event) {
        if (isFlag(event.getEntity().getItemStack().getItemMeta())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Moves the flag to the helmet as a visual indicator, and to give the flag bearer a harder
     * challenge; also makes them glow for visibility.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item pickedUpItem = event.getItem();
        if (isFlag(pickedUpItem.getItemStack().getItemMeta())) {
            LivingEntity entity = event.getEntity();
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                entity.playPickupItemAnimation(pickedUpItem);
                ItemStack oldHelmet = equipment.getHelmet();
                equipment.setHelmet(pickedUpItem.getItemStack());
                World world = entity.getWorld();
                if (oldHelmet != null) {
                    if (entity instanceof InventoryHolder inventoryHolder &&
                            !isFlag(oldHelmet.getItemMeta())) {
                        HashMap<Integer, ItemStack> result =
                                inventoryHolder.getInventory().addItem(oldHelmet);
                        if (!result.isEmpty()) {
                            world.dropItemNaturally(entity.getLocation(), oldHelmet);
                        }
                    } else {
                        world.dropItemNaturally(entity.getLocation(), oldHelmet);
                    }
                }
                entity.setGlowing(true);
                pickedUpItem.remove();
                logger.info(FLAG_PICKED_UP_BY + entity.getName() + " at " + entity.getLocation());
                world.playSound(
                        Sound.sound(Key.key(ENTITY_ALLAY_ITEM_GIVEN), Sound.Source.PLAYER, 1, 0.9f),
                        entity);
                Component customName = pickedUpItem.customName();
                if (entity instanceof Audience audience && customName != null) {
                    audience.sendActionBar(
                            Component.textOfChildren(Component.text("You picked up "), customName));
                    audience.playSound(
                            Sound.sound(Key.key("item.armor.equip_generic"), SoundCategory.PLAYERS,
                                    1, 1));
                    audience.stopSound(SoundStop.source(Sound.Source.MUSIC));
                    audience.playSound(Sound.sound(Key.key(FLAG_MUSIC), Sound.Source.MUSIC, 1, 1));
                }
            }
            event.setCancelled(true); // Item was manually added, this prevents a duplicate
        }
    }
    
    /**
     * Track an item as the flag. This changes some of the item's properties to prevent destruction,
     * and make its specialness more obvious.
     *
     * @param item The item to track.
     */
    protected void trackItem(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("ItemMeta is null");
        }
        meta.getPersistentDataContainer().set(isFlagKey, PersistentDataType.BOOLEAN, true);
        meta.setUnbreakable(true);
        meta.setFireResistant(true);
        meta.setMaxStackSize(1);
        meta.setRarity(ItemRarity.EPIC);
        item.setItemMeta(meta);
    }
    
    /**
     * Check if a dead entity is the flag. This gets rid of the glowing effect for players, and
     * helps ensure the flag gets dropped from mobs.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (event.getDrops().stream().anyMatch(itemStack -> isFlag(itemStack.getItemMeta()))) {
            entity.setGlowing(false);
            entity.stopSound(SoundStop.namedOnSource(Key.key(FLAG_MUSIC), Sound.Source.MUSIC));
        } else if (isFlag(entity)) {
            logger.info("Flag entity " + entity + " died at " + entity.getLocation());
        } else {
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null && isFlag(equipment.getHelmet().getItemMeta())) {
                // Handles keepInventory true case
                entity.getWorld().dropItemNaturally(entity.getLocation(), equipment.getHelmet());
                entity.setGlowing(false);
                equipment.setHelmet(null);
                entity.stopSound(SoundStop.namedOnSource(Key.key(FLAG_MUSIC), Sound.Source.MUSIC));
            }
        }
    }
    
    /**
     * Reacts to the flag being dropped on the ground. This uses EntitySpawnEvent to get every
     * possible case, instead of needing to figure out every event that can lead to an Item entity
     * being created that may be the flag.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Item item && isFlag(item.getItemStack().getItemMeta())) {
            item.setUnlimitedLifetime(true);
            item.setWillAge(false);
            item.setCustomNameVisible(true);
            item.customName(item.getItemStack().displayName());
            item.setInvulnerable(true);
            item.setPersistent(true);
            item.setGlowing(true);
            item.getPersistentDataContainer().set(isFlagKey, PersistentDataType.BOOLEAN, true);
            logger.info("Flag item on ground at " + item.getLocation());
            item.getWorld().playSound(
                    Sound.sound(Key.key("entity.allay.item_thrown"), Sound.Source.AMBIENT, 1, 0.9f),
                    item);
            if (particleBeacon) {
                addIndicatorSchedule(item);
            }
        }
    }
    
    /**
     * Prevent the flag from being teleported to the end, if not allowed.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        // Prevent the flag from being teleported to the end, if not allowed
        Entity entity = event.getEntity();
        if (!allowEnd && event.getPortalType().equals(PortalType.ENDER) &&
                ((entity instanceof Item item && isFlag(item.getItemStack().getItemMeta())) ||
                        entity instanceof InventoryHolder holder &&
                                Arrays.stream(holder.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .anyMatch(itemStack -> isFlag(itemStack.getItemMeta())) ||
                        isFlag(entity))) {
            event.setCancelled(true);
        } else if (entity instanceof LivingEntity livingEntity) {
            EntityEquipment equipment = livingEntity.getEquipment();
            if (equipment != null && isFlag(equipment.getHelmet().getItemMeta())) {
                event.setCancelled(true);
            }
        }
    }
    
    /**
     * Prevent the player from teleporting to the end with the flag, if configured to not be
     * allowed.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!allowEnd &&
                event.getTo().getWorld().getEnvironment().equals(World.Environment.THE_END) &&
                Arrays.stream(event.getPlayer().getInventory().getContents())
                        .filter(Objects::nonNull)
                        .anyMatch(itemStack -> isFlag(itemStack.getItemMeta()))) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Handle the player quitting the game.
     *
     * @param event The event that triggered this method.
     */
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
    
    /**
     * Track an entity as a flag.
     *
     * @param entity The entity to track.
     */
    protected void trackEntity(Entity entity) {
        entity.getPersistentDataContainer().set(isFlagKey, PersistentDataType.BOOLEAN, true);
        entity.setGlowing(true);
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setPersistent(true);
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setRemoveWhenFarAway(false);
        }
        if (particleBeacon) {
            addIndicatorSchedule(entity);
        }
    }
    
    /**
     * Track an entity as a flag, given its UUID.
     *
     * @param entityUUID The UUID of the entity to track.
     */
    protected void trackEntity(UUID entityUUID) {
        Entity entity = Bukkit.getEntity(entityUUID);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found");
        }
        trackEntity(entity);
    }
    
    /**
     * Method used to cancel entity attacks if needed.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Creeper && isFlag(entity)) {
            // Prevent flag Creepers from blowing themselves up
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevents an entity from being destroyed by transformation.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onEntityTransform(EntityTransformEvent event) {
        if (isFlag(event.getEntity())) {
            trackEntity(event.getTransformedEntity());
        }
    }
    
    /**
     * Sets scheduled tasks back up when a world loads.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (particleBeacon) {
            processPotentialFlagEntities(event.getWorld().getEntities());
        }
    }
    
    /**
     * Sets up scheduled tasks for flags that are already in the world as they load in.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (particleBeacon) {
            processPotentialFlagEntities(event.getEntities());
        }
    }
    
    /**
     * Goes through a stream and sets up indicator schedules for any flag entities that do not
     * already have one.
     *
     * @param entities The entities to process.
     */
    private void processPotentialFlagEntities(List<Entity> entities) {
        assert !particleBeacon;
        entities.parallelStream().filter(this::isFlag)
                .filter(entity -> !knownFlagEntities.contains(entity))
                .forEach(this::addIndicatorSchedule);
        
    }
    
    /**
     * Adds an indicator schedule to an entity.
     *
     * @param entity the entity to add the schedule to.
     */
    private void addIndicatorSchedule(Entity entity) {
        assert !particleBeacon;
        entity.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
                    if (!isFlag(entity) && entity instanceof LivingEntity livingEntity) {
                        EntityEquipment equipment = livingEntity.getEquipment();
                        if (equipment != null && !isFlag(equipment.getHelmet().getItemMeta())) {
                            scheduledTask.cancel();
                            return;
                        }
                    }
                    World world = entity.getWorld();
                    int worldHeight = world.getMaxHeight() - world.getMinHeight();
                    world.playSound(
                            Sound.sound(Key.key("entity.firework_rocket.launch"),
                                    Sound.Source.AMBIENT, 512,
                                    1), entity);
                    Particle.FIREWORK.builder().count(worldHeight * 2).location(entity.getLocation())
                            .offset(0, worldHeight, 0).receivers(512).spawn();
                }, () -> knownFlagEntities.remove(entity), Tick.tick()
                        .fromDuration(Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60,
                                120))),
                Tick.tick().fromDuration(Duration.ofMinutes(1)));
        knownFlagEntities.add(entity);
    }
    
    /**
     * The action to take when a player quits with the flag.
     */
    private enum QuitMode {
        /**
         * Kill the player when they quit with the flag.
         */
        KILL,
        /**
         * Drop the flag when the player quits.
         */
        DROP
    }
    
}