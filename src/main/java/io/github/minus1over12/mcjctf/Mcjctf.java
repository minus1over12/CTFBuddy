package io.github.minus1over12.mcjctf;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class Mcjctf extends JavaPlugin {
    private FlagTracker flagTracker;
    
    @Override
    public void onEnable() {
        // Plugin startup logic
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
                    if (args.length != 1) {
                        return false;
                    }
                    if (sender instanceof HumanEntity player) {
                        flagTracker.trackItem(player.getInventory().getItemInMainHand());
                    } else {
                        sender.sendMessage("You must be a human entity to use this command");
                    }
                    return true;
                }
                case "entity" -> {
                    switch (args[1]) {
                        case "uuid" -> {
                            if (args.length != 2) {
                                return false;
                            }
                            try {
                                flagTracker.trackEntity(UUID.fromString(args[1]));
                            } catch (IllegalArgumentException e) {
                                sender.sendMessage("Invalid UUID");
                            }
                            return true;
                        }
                        case "target" -> {
                            if (args.length != 2) {
                                return false;
                            }
                            if (sender instanceof LivingEntity senderEntity) {
                                Entity target = senderEntity.getTargetEntity(10);
                                if (target != null) {
                                    flagTracker.trackEntity(target);
                                    sender.sendMessage("Entity setup as flag");
                                } else {
                                    sender.sendMessage("Not looking at a target");
                                }
                                return true;
                            } else {
                                sender.sendMessage(
                                        "You must be a living entity to use this command");
                            }
                        }
                    }
                }
            }
            
        }
        return false;
    }
}
