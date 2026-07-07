package com.dotorimaru.land.command;

import com.dotorimaru.land.LandPlugin;
import com.dotorimaru.land.model.Estate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LandCommand implements CommandExecutor, TabCompleter {

    private final LandPlugin plugin;

    public LandCommand(LandPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "player-only");
            return true;
        }

        if (args.length >= 1 && args[0].equals("보호")) {
            toggleProtection(player);
            return true;
        }

        plugin.getGuiManager().openList(player);
        return true;
    }

    private void toggleProtection(Player player) {
        Estate estate = plugin.getLandManager().getEstateAt(
                player.getWorld().getName(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());

        if (estate == null) {
            plugin.getMessageManager().send(player, "protection-not-standing");
            return;
        }
        if (!estate.getOwner().equals(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection-not-owner");
            return;
        }
        boolean now = plugin.getLandManager().toggleProtection(estate.getId());
        plugin.getMessageManager().send(player, now ? "protection-on" : "protection-off");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("보호");
        }
        return List.of();
    }
}
