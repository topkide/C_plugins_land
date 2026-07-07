package com.dotorimaru.land;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * config.yml 값을 타입 안전하게 노출하는 홀더.
 */
@Getter
public class LandConfig {

    public enum RefundMode {BRIDGE, VOUCHER, NONE}

    public enum EntryType {TITLE, ACTIONBAR}

    private boolean gridEnabled;
    private int gridSize;
    private List<String> allowedWorlds;

    private Material previewMaterial;
    private Material blockedMaterial;
    private int previewYOffset;
    private int targetDistance;
    private int updateIntervalTicks;
    private long confirmDelayMs;

    private boolean protectionDefaultEnabled;
    private boolean protectBlockBreak;
    private boolean protectBlockPlace;
    private boolean protectContainer;
    private boolean protectInteract;
    private boolean protectExplosion;
    private boolean protectMobGriefing;
    private boolean protectFluidFlow;
    private boolean protectPiston;

    private boolean entryEnabled;
    private EntryType entryType;
    private boolean notifyOwnLand;

    private int refundPercent;
    private RefundMode refundMode;

    private String giveCommand;

    private int maxPlotsPerPlayer;
    private int autosaveMinutes;

    public void load(FileConfiguration c) {
        gridEnabled = c.getBoolean("grid.enabled", true);
        gridSize = Math.max(1, c.getInt("grid.size", 10));
        allowedWorlds = c.getStringList("grid.allowed-worlds");

        previewMaterial = material(c.getString("preview.material"), Material.LIME_STAINED_GLASS);
        blockedMaterial = material(c.getString("preview.blocked-material"), Material.RED_STAINED_GLASS);
        previewYOffset = c.getInt("preview.y-offset", 1);
        targetDistance = Math.max(4, c.getInt("preview.target-distance", 40));
        updateIntervalTicks = Math.max(1, c.getInt("preview.update-interval-ticks", 4));
        confirmDelayMs = Math.max(0, c.getLong("preview.confirm-delay-ms", 400));

        protectionDefaultEnabled = c.getBoolean("protection.default-enabled", true);
        protectBlockBreak = c.getBoolean("protection.block-break", true);
        protectBlockPlace = c.getBoolean("protection.block-place", true);
        protectContainer = c.getBoolean("protection.container", true);
        protectInteract = c.getBoolean("protection.interact", true);
        protectExplosion = c.getBoolean("protection.explosion", true);
        protectMobGriefing = c.getBoolean("protection.mob-griefing", true);
        protectFluidFlow = c.getBoolean("protection.fluid-flow", true);
        protectPiston = c.getBoolean("protection.piston", true);

        entryEnabled = c.getBoolean("entry.enabled", true);
        entryType = enumOf(EntryType.class, c.getString("entry.type"), EntryType.TITLE);
        notifyOwnLand = c.getBoolean("entry.notify-own-land", true);

        refundPercent = Math.max(0, Math.min(100, c.getInt("sell.refund-percent", 80)));
        refundMode = enumOf(RefundMode.class, c.getString("sell.refund-mode"), RefundMode.BRIDGE);

        giveCommand = c.getString("economy.give-command", "land-eco-give %player% %currency% %amount%");

        maxPlotsPerPlayer = Math.max(0, c.getInt("limits.max-plots-per-player", 0));
        autosaveMinutes = Math.max(0, c.getInt("misc.autosave-minutes", 5));
    }

    public boolean isWorldAllowed(String world) {
        return allowedWorlds == null || allowedWorlds.isEmpty() || allowedWorlds.contains(world);
    }

    private static Material material(String name, Material def) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return m != null ? m : def;
    }

    private static <T extends Enum<T>> T enumOf(Class<T> type, String name, T def) {
        if (name == null) return def;
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
