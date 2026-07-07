package com.dotorimaru.land.manager;

import com.dotorimaru.land.model.Estate;
import com.dotorimaru.land.model.Plot;
import com.dotorimaru.land.storage.Storage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 토지(구획/부지) 관리자.
 * 청크 단위 공간 인덱스로 위치 조회를 빠르게 하고, 구매/중복검사/인접 자동병합/판매/보호를 담당합니다.
 */
public class LandManager {

    public enum Status {SUCCESS, OVERLAP, LIMIT}

    public record BuyResult(Status status, boolean merged, Plot plot) {
    }

    private final Plugin plugin;
    private final Storage storage;

    private final Map<UUID, Plot> plots = new HashMap<>();
    private final Map<UUID, Estate> estates = new HashMap<>();

    /** world → chunkKey → plotIds */
    private final Map<String, Map<Long, Set<UUID>>> chunkIndex = new HashMap<>();
    /** owner → plotIds (병합 후보 조회용) */
    private final Map<UUID, Set<UUID>> ownerPlots = new HashMap<>();

    public LandManager(Plugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load() {
        plots.clear();
        estates.clear();
        chunkIndex.clear();
        ownerPlots.clear();

        plots.putAll(storage.loadPlots());
        estates.putAll(storage.loadEstates());

        // 인덱스 재구축
        for (Plot plot : plots.values()) {
            addToIndex(plot);
            ownerPlots.computeIfAbsent(plot.getOwner(), k -> new HashSet<>()).add(plot.getId());
        }
        plugin.getLogger().info("✅ 구획 %d개 / 부지 %d개를 로드했습니다.".formatted(plots.size(), estates.size()));
    }

    public void save() {
        storage.saveLands(plots.values(), estates.values());
    }

    // ---------------------------------------------------------------
    // 조회
    // ---------------------------------------------------------------

    public Plot getPlot(UUID id) {
        return plots.get(id);
    }

    public Estate getEstate(UUID id) {
        return id == null ? null : estates.get(id);
    }

    public Plot getPlotAt(String world, int x, int z) {
        Map<Long, Set<UUID>> worldIndex = chunkIndex.get(world);
        if (worldIndex == null) return null;
        Set<UUID> candidates = worldIndex.get(chunkKey(x >> 4, z >> 4));
        if (candidates == null) return null;
        for (UUID id : candidates) {
            Plot plot = plots.get(id);
            if (plot != null && plot.contains(world, x, z)) return plot;
        }
        return null;
    }

    public Estate getEstateAt(String world, int x, int z) {
        Plot plot = getPlotAt(world, x, z);
        return plot == null ? null : estates.get(plot.getEstateId());
    }

    public List<Estate> getEstatesOf(UUID owner) {
        List<Estate> result = new ArrayList<>();
        for (Estate estate : estates.values()) {
            if (estate.getOwner().equals(owner)) result.add(estate);
        }
        return result;
    }

    public int countPlots(UUID owner) {
        Set<UUID> set = ownerPlots.get(owner);
        return set == null ? 0 : set.size();
    }

    // ---------------------------------------------------------------
    // 구매
    // ---------------------------------------------------------------

    public BuyResult tryBuy(Player player, String world,
                            int minX, int minZ, int maxX, int maxZ,
                            long price, String currency,
                            boolean protectionDefault, int maxPlots) {

        // 중복 구매 검사
        if (overlapsExisting(world, minX, minZ, maxX, maxZ)) {
            return new BuyResult(Status.OVERLAP, false, null);
        }

        UUID owner = player.getUniqueId();
        if (maxPlots > 0 && countPlots(owner) >= maxPlots) {
            return new BuyResult(Status.LIMIT, false, null);
        }

        Plot plot = new Plot(UUID.randomUUID(), owner, world, minX, minZ, maxX, maxZ, price, currency, null);
        plots.put(plot.getId(), plot);
        addToIndex(plot);
        ownerPlots.computeIfAbsent(owner, k -> new HashSet<>()).add(plot.getId());

        boolean merged = mergeIntoNeighbours(plot, protectionDefault);

        save();
        return new BuyResult(Status.SUCCESS, merged, plot);
    }

    /** 해당 사각형이 기존 구획과 겹치지 않아(구매 가능) 비어있는지 */
    public boolean isAreaFree(String world, int minX, int minZ, int maxX, int maxZ) {
        return !overlapsExisting(world, minX, minZ, maxX, maxZ);
    }

    private boolean overlapsExisting(String world, int minX, int minZ, int maxX, int maxZ) {
        Map<Long, Set<UUID>> worldIndex = chunkIndex.get(world);
        if (worldIndex == null) return false;

        Set<UUID> checked = new HashSet<>();
        for (int cx = minX >> 4; cx <= (maxX >> 4); cx++) {
            for (int cz = minZ >> 4; cz <= (maxZ >> 4); cz++) {
                Set<UUID> ids = worldIndex.get(chunkKey(cx, cz));
                if (ids == null) continue;
                for (UUID id : ids) {
                    if (!checked.add(id)) continue;
                    Plot plot = plots.get(id);
                    if (plot != null && plot.overlaps(world, minX, minZ, maxX, maxZ)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 새 구획을 소유자의 인접 구획과 자동 병합합니다.
     * 여러 부지에 동시에 붙으면 하나로 통합합니다.
     *
     * @return 기존 부지와 병합되었으면 true (독립 부지 신설이면 false)
     */
    private boolean mergeIntoNeighbours(Plot newPlot, boolean protectionDefault) {
        UUID owner = newPlot.getOwner();
        Set<UUID> ownerPlotIds = ownerPlots.getOrDefault(owner, new HashSet<>());

        // 인접한 부지들 수집
        Set<UUID> adjacentEstates = new LinkedHashSet<>();
        for (UUID pid : ownerPlotIds) {
            if (pid.equals(newPlot.getId())) continue;
            Plot other = plots.get(pid);
            if (other == null || other.getEstateId() == null) continue;
            if (other.getWorld().equals(newPlot.getWorld()) && other.isEdgeAdjacent(newPlot)) {
                adjacentEstates.add(other.getEstateId());
            }
        }

        if (adjacentEstates.isEmpty()) {
            // 독립 부지 신설
            Estate estate = new Estate(owner, newPlot.getWorld(), protectionDefault);
            estate.addPlot(newPlot.getId());
            newPlot.setEstateId(estate.getId());
            estates.put(estate.getId(), estate);
            return false;
        }

        // 대표 부지 하나 선택 후 나머지를 통합
        List<UUID> estateList = new ArrayList<>(adjacentEstates);
        Estate target = estates.get(estateList.get(0));
        for (int i = 1; i < estateList.size(); i++) {
            Estate other = estates.remove(estateList.get(i));
            if (other == null) continue;
            for (UUID pid : other.getPlotIds()) {
                Plot p = plots.get(pid);
                if (p != null) p.setEstateId(target.getId());
                target.addPlot(pid);
            }
        }

        target.addPlot(newPlot.getId());
        newPlot.setEstateId(target.getId());
        return true;
    }

    // ---------------------------------------------------------------
    // 판매
    // ---------------------------------------------------------------

    /**
     * 부지를 판매(삭제)하고, 화폐별 총 구매가를 반환합니다.
     * (환불 비율 적용은 호출측에서 처리)
     */
    public Map<String, Long> sellEstate(UUID estateId) {
        Estate estate = estates.remove(estateId);
        Map<String, Long> priceByCurrency = new HashMap<>();
        if (estate == null) return priceByCurrency;

        for (UUID pid : new ArrayList<>(estate.getPlotIds())) {
            Plot plot = plots.remove(pid);
            if (plot == null) continue;
            removeFromIndex(plot);
            Set<UUID> owned = ownerPlots.get(plot.getOwner());
            if (owned != null) owned.remove(pid);

            String currency = plot.getCurrency() == null ? "" : plot.getCurrency();
            priceByCurrency.merge(currency, plot.getPrice(), Long::sum);
        }

        save();
        return priceByCurrency;
    }

    public boolean toggleProtection(UUID estateId) {
        Estate estate = estates.get(estateId);
        if (estate == null) return false;
        estate.setProtection(!estate.isProtection());
        save();
        return estate.isProtection();
    }

    // ---------------------------------------------------------------
    // 보호 판정
    // ---------------------------------------------------------------

    public boolean canBuild(Player player, String world, int x, int z) {
        if (player.hasPermission("land.bypass")) return true;
        return canBuild(player.getUniqueId(), world, x, z);
    }

    public boolean canBuild(UUID actor, String world, int x, int z) {
        Estate estate = getEstateAt(world, x, z);
        if (estate == null) return true;                 // 미소유 지역
        if (!estate.isProtection()) return true;         // 보호 해제됨
        return estate.getOwner().equals(actor);          // 소유자만 허용
    }

    /** 위치가 보호된 남의 토지인지(폭발/그리핑 등 판정용) */
    public boolean isProtectedArea(String world, int x, int z) {
        Estate estate = getEstateAt(world, x, z);
        return estate != null && estate.isProtection();
    }

    // ---------------------------------------------------------------
    // 부지 경계/중심
    // ---------------------------------------------------------------

    /** {minX, minZ, maxX, maxZ} */
    public int[] estateBounds(Estate estate) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (UUID pid : estate.getPlotIds()) {
            Plot plot = plots.get(pid);
            if (plot == null) continue;
            minX = Math.min(minX, plot.getMinX());
            minZ = Math.min(minZ, plot.getMinZ());
            maxX = Math.max(maxX, plot.getMaxX());
            maxZ = Math.max(maxZ, plot.getMaxZ());
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    public int[] estateCenter(Estate estate) {
        int[] b = estateBounds(estate);
        return new int[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
    }

    public int plotCountOf(Estate estate) {
        return estate.getPlotIds().size();
    }

    public Plot firstPlot(Estate estate) {
        for (UUID pid : estate.getPlotIds()) {
            Plot p = plots.get(pid);
            if (p != null) return p;
        }
        return null;
    }

    // ---------------------------------------------------------------
    // 공간 인덱스
    // ---------------------------------------------------------------

    private void addToIndex(Plot plot) {
        Map<Long, Set<UUID>> worldIndex = chunkIndex.computeIfAbsent(plot.getWorld(), k -> new HashMap<>());
        for (int cx = plot.getMinX() >> 4; cx <= (plot.getMaxX() >> 4); cx++) {
            for (int cz = plot.getMinZ() >> 4; cz <= (plot.getMaxZ() >> 4); cz++) {
                worldIndex.computeIfAbsent(chunkKey(cx, cz), k -> new HashSet<>()).add(plot.getId());
            }
        }
    }

    private void removeFromIndex(Plot plot) {
        Map<Long, Set<UUID>> worldIndex = chunkIndex.get(plot.getWorld());
        if (worldIndex == null) return;
        for (int cx = plot.getMinX() >> 4; cx <= (plot.getMaxX() >> 4); cx++) {
            for (int cz = plot.getMinZ() >> 4; cz <= (plot.getMaxZ() >> 4); cz++) {
                long key = chunkKey(cx, cz);
                Set<UUID> ids = worldIndex.get(key);
                if (ids != null) {
                    ids.remove(plot.getId());
                    if (ids.isEmpty()) worldIndex.remove(key);
                }
            }
        }
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }
}
