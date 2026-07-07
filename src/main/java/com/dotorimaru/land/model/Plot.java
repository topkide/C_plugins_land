package com.dotorimaru.land.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 하나의 "구획" — 구매서 1장으로 구매한 축 정렬 직사각형입니다.
 * X/Z 로 경계를 가지며 Y 는 전체(바닥~하늘)를 차지합니다.
 * 병합되어도 개별 구획 정보(가격/화폐)는 유지되어 환불 계산에 사용됩니다.
 */
@Getter
public class Plot {

    private final UUID id;
    private final UUID owner;
    private final String world;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    /** 구매 당시 가격(환불 기준 스냅샷) */
    private final long price;
    /** 구매 당시 화폐 이름(currency.sk 기준) */
    private final String currency;

    /** 소속된 부지(Estate) id. 병합에 따라 변경됩니다. */
    @Setter
    private UUID estateId;

    public Plot(UUID id, UUID owner, String world,
                int minX, int minZ, int maxX, int maxZ,
                long price, String currency, UUID estateId) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxZ = Math.max(minZ, maxZ);
        this.price = price;
        this.currency = currency;
        this.estateId = estateId;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int length() {
        return maxZ - minZ + 1;
    }

    public boolean contains(String world, int x, int z) {
        return this.world.equals(world) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** 두 직사각형이 한 칸이라도 겹치는지 */
    public boolean overlaps(String world, int minX, int minZ, int maxX, int maxZ) {
        if (!this.world.equals(world)) return false;
        return this.minX <= maxX && this.maxX >= minX
                && this.minZ <= maxZ && this.maxZ >= minZ;
    }

    /**
     * 다른 구획과 변(edge)을 맞대고 인접한지 여부.
     * 모서리(대각선)만 닿는 경우는 인접으로 보지 않습니다.
     */
    public boolean isEdgeAdjacent(Plot o) {
        if (!world.equals(o.world)) return false;

        boolean zOverlap = this.minZ <= o.maxZ && this.maxZ >= o.minZ;
        boolean xOverlap = this.minX <= o.maxX && this.maxX >= o.minX;

        // 좌/우로 붙어있고 Z 범위가 겹치면 인접
        boolean horizontallyTouching = (this.maxX + 1 == o.minX || o.maxX + 1 == this.minX) && zOverlap;
        // 앞/뒤로 붙어있고 X 범위가 겹치면 인접
        boolean verticallyTouching = (this.maxZ + 1 == o.minZ || o.maxZ + 1 == this.minZ) && xOverlap;

        return horizontallyTouching || verticallyTouching;
    }
}
