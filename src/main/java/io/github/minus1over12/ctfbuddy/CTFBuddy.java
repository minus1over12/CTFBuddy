package io.github.minus1over12.ctfbuddy;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Main class for the plugin.
 *
 * @author War Pigeon
 */
public final class CTFBuddy extends JavaPlugin {
    /**
     * Spigot's default tracking range for misc entities.
     */
    private static final int DEFAULT_MISC_TRACKING_RANGE = 32;
    
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
     * Object that listens for changes to the flag state.
     */
    private FlagTracker flagTracker;
    
    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        flagTracker = new FlagTracker(this);
        getServer().getPluginManager().registerEvents(flagTracker, this);
    }
    
    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if ("makeflag".equalsIgnoreCase(command.getName())) {
            if (args.length < 1) {
                return false;
            }
            switch (args[0]) {
                case "item" -> {
                    checkTrackingConfig(sender, DEFAULT_MISC_TRACKING_RANGE, "misc");
                    checkTrackingConfig(sender, DEFAULT_MOB_TRACKING_RANGE, "players");
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
                case "entity" -> {
                    switch (args.length) {
                        case 1 -> {
                            if (sender instanceof LivingEntity senderEntity) {
                                Entity target = senderEntity.getTargetEntity(10);
                                if (target != null) {
                                    flagTracker.trackEntity(target);
                                    sender.sendMessage(Component.text("Entity setup as flag"));
                                    if (target instanceof Monster) {
                                        checkTrackingConfig(sender, DEFAULT_MOB_TRACKING_RANGE,
                                                "monsters");
                                    } else if (target instanceof Animals) {
                                        checkTrackingConfig(sender, DEFAULT_MOB_TRACKING_RANGE,
                                                "animals");
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
        return false;
    }
    
    /**
     * Checks the tracking config for default settings.
     *
     * @param sender               the sender running a command.
     * @param defaultTrackingRange the default value for the path being checked.
     * @param entityTypeName       The entity type to check.
     */
    private void checkTrackingConfig(@NotNull Audience sender, int defaultTrackingRange,
                                     String entityTypeName) {
        if (getServer().spigot().getSpigotConfig()
                .getInt("world-settings.default.entity-tracking-range." + entityTypeName) <=
                defaultTrackingRange) {
            String warningMessage =
                    TRACKING_RANGE_WARNING_PREFIX + entityTypeName + TRACKING_RANGE_WARNING_SUFFIX;
            getLogger().warning(warningMessage);
            sender.sendMessage(Component.text(warningMessage, NamedTextColor.YELLOW));
        }
    }
    
}
