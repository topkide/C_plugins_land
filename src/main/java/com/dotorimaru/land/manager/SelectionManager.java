package com.dotorimaru.land.manager;

import com.dotorimaru.land.LandConfig;
import com.dotorimaru.land.model.CertificateType;
import com.dotorimaru.land.util.Grid;
import com.dotorimaru.land.util.PreviewRenderer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 토지 선택 모드 관리자.
 * 첫 우클릭으로 선택 시작 → 바라보는 위치에 미리보기 표시(주기 갱신) → 두 번째 우클릭으로 확정.
 */
public class SelectionManager {

    /** 계산된 사각형 범위 */
    public record Rect(String world, int minX, int minZ, int maxX, int maxZ, boolean free) {
    }

    private static class Selection {
        final CertificateType cert;
        final long startTime;
        String lastKey = "";
        List<Location> shown;

        Selection(CertificateType cert) {
            this.cert = cert;
            this.startTime = System.currentTimeMillis();
        }
    }

    private final Plugin plugin;
    private final LandConfig config;
    private final LandManager landManager;

    private final Map<UUID, Selection> selections = new HashMap<>();
    private BukkitTask task;

    public SelectionManager(Plugin plugin, LandConfig config, LandManager landManager) {
        this.plugin = plugin;
        this.config = config;
        this.landManager = landManager;
    }

    public void start(Player player, CertificateType cert) {
        cancel(player);
        selections.put(player.getUniqueId(), new Selection(cert));
        ensureTask();
    }

    public boolean isSelecting(Player player) {
        return selections.containsKey(player.getUniqueId());
    }

    public CertificateType getCert(Player player) {
        Selection s = selections.get(player.getUniqueId());
        return s == null ? null : s.cert;
    }

    /** confirm-delay 를 지났는지(즉시 확정 방지) */
    public boolean canConfirm(Player player) {
        Selection s = selections.get(player.getUniqueId());
        return s != null && (System.currentTimeMillis() - s.startTime) >= config.getConfirmDelayMs();
    }

    public void cancel(Player player) {
        Selection s = selections.remove(player.getUniqueId());
        if (s != null && s.shown != null) {
            PreviewRenderer.restore(player, s.shown);
        }
        if (selections.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * 현재 바라보는 위치 기준으로 최종 범위를 계산합니다.
     * 지면을 바라보고 있지 않으면 null.
     */
    public Rect computeRect(Player player, CertificateType cert) {
        Block target = player.getTargetBlockExact(config.getTargetDistance());
        if (target == null) return null;

        int w = cert.getWidth();
        int l = cert.getLength();
        int cx = target.getX();
        int cz = target.getZ();

        int minX, minZ;
        if (config.isGridEnabled()) {
            minX = Grid.snapMin(cx, config.getGridSize());
            minZ = Grid.snapMin(cz, config.getGridSize());
        } else {
            minX = cx - w / 2;
            minZ = cz - l / 2;
        }
        int maxX = minX + w - 1;
        int maxZ = minZ + l - 1;

        String world = target.getWorld().getName();
        boolean free = landManager.isAreaFree(world, minX, minZ, maxX, maxZ);
        return new Rect(world, minX, minZ, maxX, maxZ, free);
    }

    private void ensureTask() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, config.getUpdateIntervalTicks());
    }

    private void tick() {
        for (Map.Entry<UUID, Selection> entry : selections.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Selection s = entry.getValue();
            if (player == null || !player.isOnline()) continue;

            Rect rect = computeRect(player, s.cert);
            if (rect == null) continue;

            String key = rect.minX() + "," + rect.minZ() + "," + rect.maxX() + "," + rect.maxZ() + "," + rect.world() + "," + rect.free();
            if (key.equals(s.lastKey)) continue;

            // 이전 미리보기 복원 후 새로 그림
            if (s.shown != null) PreviewRenderer.restore(player, s.shown);

            BlockData data = (rect.free() ? config.getPreviewMaterial() : config.getBlockedMaterial()).createBlockData();
            s.shown = PreviewRenderer.drawBorder(
                    player, player.getWorld(),
                    rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ(),
                    config.getPreviewYOffset(), data
            );
            s.lastKey = key;
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, Selection> entry : selections.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && entry.getValue().shown != null) {
                PreviewRenderer.restore(player, entry.getValue().shown);
            }
        }
        selections.clear();
    }
}
