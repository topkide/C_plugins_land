package com.dotorimaru.land.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 선택 범위의 테두리를 해당 플레이어에게만 보이는 가짜 블록으로 표시하고 복원합니다.
 * (실제 월드 블록은 변경하지 않습니다 — sendBlockChange 는 클라이언트 표시용)
 */
public final class PreviewRenderer {

    private PreviewRenderer() {
    }

    /**
     * 사각형 테두리를 지형 높이에 맞춰 그립니다.
     *
     * @return 표시한 위치 목록(복원용)
     */
    public static List<Location> drawBorder(Player player, World world,
                                            int minX, int minZ, int maxX, int maxZ,
                                            int yOffset, BlockData data) {
        List<Location> shown = new ArrayList<>();

        // 위/아래 변 (Z = minZ, maxZ)
        for (int x = minX; x <= maxX; x++) {
            place(player, world, x, minZ, yOffset, data, shown);
            if (maxZ != minZ) place(player, world, x, maxZ, yOffset, data, shown);
        }
        // 좌/우 변 (X = minX, maxX), 모서리 중복 제외
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            place(player, world, minX, z, yOffset, data, shown);
            if (maxX != minX) place(player, world, maxX, z, yOffset, data, shown);
        }
        return shown;
    }

    private static void place(Player player, World world, int x, int z, int yOffset,
                              BlockData data, List<Location> shown) {
        int y = world.getHighestBlockYAt(x, z) + yOffset;
        Location loc = new Location(world, x, y, z);
        player.sendBlockChange(loc, data);
        shown.add(loc);
    }

    /** 표시했던 가짜 블록을 실제 블록으로 되돌립니다. */
    public static void restore(Player player, List<Location> shown) {
        if (shown == null) return;
        for (Location loc : shown) {
            if (loc.getWorld() == null) continue;
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }
}
