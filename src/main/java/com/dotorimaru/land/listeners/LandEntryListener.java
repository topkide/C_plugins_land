package com.dotorimaru.land.listeners;

import com.dotorimaru.land.LandConfig;
import com.dotorimaru.land.manager.LandManager;
import com.dotorimaru.land.manager.MessageManager;
import com.dotorimaru.land.model.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 토지 진입 안내. 다른 부지로 이동하는 순간에만 안내를 띄웁니다(디바운스).
 */
public class LandEntryListener implements Listener {

    private final LandConfig config;
    private final MessageManager messages;
    private final LandManager landManager;

    private final Map<UUID, UUID> lastEstate = new HashMap<>();

    public LandEntryListener(LandConfig config, MessageManager messages, LandManager landManager) {
        this.config = config;
        this.messages = messages;
        this.landManager = landManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!config.isEntryEnabled()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // 블록 X/Z 가 바뀌지 않았으면(제자리/시선/점프) 무시
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ()
                && Objects.equals(from.getWorld(), to.getWorld())) {
            return;
        }

        Player player = event.getPlayer();
        Estate estate = landManager.getEstateAt(to.getWorld().getName(), to.getBlockX(), to.getBlockZ());
        UUID newId = estate == null ? null : estate.getId();
        UUID prevId = lastEstate.get(player.getUniqueId());

        if (Objects.equals(prevId, newId)) return;
        lastEstate.put(player.getUniqueId(), newId);

        if (estate == null) return; // 야생으로 나감 → 안내 없음

        boolean own = estate.getOwner().equals(player.getUniqueId());
        if (own && !config.isNotifyOwnLand()) return;

        boolean actionBar = config.getEntryType() == LandConfig.EntryType.ACTIONBAR;
        if (own) {
            messages.showEntry(player, actionBar, "entry-title-own");
        } else {
            messages.showEntry(player, actionBar, "entry-title", "%owner%", ownerName(estate.getOwner()));
        }
    }

    private String ownerName(UUID owner) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        return op.getName() != null ? op.getName() : "누군가";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastEstate.remove(event.getPlayer().getUniqueId());
    }
}
