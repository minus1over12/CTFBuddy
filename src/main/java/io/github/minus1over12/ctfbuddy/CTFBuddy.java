package io.github.minus1over12.ctfbuddy;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Main class for the plugin.
 *
 * @author War Pigeon
 */
public final class CTFBuddy extends JavaPlugin {
    
    /**
     * Prefix of the warning to show when the default tracking range is being used for mobs.
     */
    private static final String TRACKING_RANGE_WARNING_PREFIX =
            "You may want to increase the default tracking range for ";
    /**
     * Suffix of the warning to show when the default tracking range is being used for mobs.
     */
    private static final String TRACKING_RANGE_WARNING_SUFFIX =
            " in spigot.yml to make the flag easier to find.";
    /**
     * The default tracking range Spigot uses for mobs.
     */
    private static final int DEFAULT_MOB_TRACKING_RANGE = 48;
    /**
     * String used to indicate the make flag command.
     */
    private static final String MAKEFLAG = "makeflag";
    /**
     * String used to indicate the plugin info command.
     */
    private static final String CTFBUDDY = "ctfbuddy";
    /**
     * String used to indicate making an entity flag.
     */
    private static final String ENTITY = "entity";
    /**
     * String used to indicate making an item flag.
     */
    private static final String ITEM = "item";
    /**
     * Object that listens for changes to the flag state.
     */
    private FlagTracker flagTracker;
    
    @Override
    public void onLoad() {
        // Plugin loading logic
        saveDefaultConfig();
    }
    
    @Override
    public void onEnable() {
        // Plugin startup logic
        flagTracker = new FlagTracker(this);
        getServer().getPluginManager().registerEvents(flagTracker, this);
        metrics();
    }
    
    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        switch (command.getName().toLowerCase()) {
            case MAKEFLAG -> {
                if (args.length < 1) {
                    return false;
                }
                switch (args[0]) {
                    case ITEM -> {
                        checkTrackingConfig(sender, "players");
                        // Checking misc is not needed since items get untracked on the client side
                        // when far away.
                        if (args.length != 1) {
                            return false;
                        }
                        if (sender instanceof HumanEntity player) {
                            try {
                                flagTracker.trackItem(player.getInventory().getItemInMainHand());
                                sender.sendMessage(Component.text("Item in hand set as flag"));
                            } catch (IllegalArgumentException e) {
                                sender.sendMessage(
                                        Component.text("Invalid item in hand", NamedTextColor.RED));
                            }
                        } else {
                            sender.sendMessage(
                                    Component.text("You must be a human entity to use this command",
                                            NamedTextColor.RED));
                        }
                        return true;
                    }
                    case ENTITY -> {
                        switch (args.length) {
                            case 1 -> {
                                if (sender instanceof LivingEntity senderEntity) {
                                    Entity target = senderEntity.getTargetEntity(10);
                                    if (target != null) {
                                        flagTracker.trackEntity(target);
                                        sender.sendMessage(Component.text("Entity setup as flag"));
                                        if (target instanceof Monster) {
                                            checkTrackingConfig(sender, "monsters");
                                        } else if (target instanceof Animals) {
                                            checkTrackingConfig(sender, "animals");
                                        }
                                    } else {
                                        sender.sendMessage(Component.text("Not looking at a target",
                                                NamedTextColor.RED));
                                    }
                                    return true;
                                } else {
                                    sender.sendMessage(Component.text(
                                            "You must be a living entity to use this command",
                                            NamedTextColor.RED));
                                }
                                return true;
                            }
                            case 2 -> {
                                try {
                                    flagTracker.trackEntity(UUID.fromString(args[1]));
                                } catch (IllegalArgumentException e) {
                                    sender.sendMessage(
                                            Component.text("Invalid UUID", NamedTextColor.RED));
                                }
                                return true;
                            }
                            default -> {
                                return false;
                            }
                        }
                    }
                    default -> {
                        return false;
                    }
                }
                
            }
            case CTFBUDDY -> {
                sender.sendMessage(Component.text(this + " made by War Pigeon"));
                return true;
            }
            default -> throw new UnsupportedOperationException(
                    "Unexpected command: " + command.getName());
        }
    }
    
    /**
     * Checks the tracking config for default settings.
     *
     * @param sender         the sender running a command.
     * @param entityTypeName The entity type to check.
     */
    private void checkTrackingConfig(@NotNull Audience sender, String entityTypeName) {
        if (getServer().spigot().getSpigotConfig()
                .getInt("world-settings.default.entity-tracking-range." + entityTypeName) <=
                CTFBuddy.DEFAULT_MOB_TRACKING_RANGE) {
            String warningMessage =
                    TRACKING_RANGE_WARNING_PREFIX + entityTypeName + TRACKING_RANGE_WARNING_SUFFIX;
            getLogger().warning(warningMessage);
            sender.sendMessage(Component.text(warningMessage, NamedTextColor.YELLOW));
        }
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command, @NotNull String alias,
                                                @NotNull String @NotNull [] args) {
        switch (command.getName().toLowerCase()) {
            case MAKEFLAG -> {
                if (args.length == 1) {
                    return List.of(ITEM, ENTITY);
                } else {
                    return List.of();
                }
            }
            case CTFBUDDY -> {
                return List.of();
            }
        }
        return super.onTabComplete(sender, command, alias, args);
    }
    
    /**
     * Sends plugin metrics to bStats.
     */
    private void metrics() {
        // All you have to do is adding the following two lines in your onEnable method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 22225; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);
    }
}
