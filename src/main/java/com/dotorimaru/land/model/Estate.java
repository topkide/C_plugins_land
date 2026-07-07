package com.dotorimaru.land.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * "부지" — 같은 소유자의 인접 구획들을 묶은 단위입니다.
 * 진입 안내("○○님의 땅")와 보호 ON/OFF 의 기준이 됩니다.
 */
@Getter
public class Estate {

    private final UUID id;
    private final UUID owner;
    private final String world;
    private final Set<UUID> plotIds;

    /** 보호 활성화 여부 */
    @Setter
    private boolean protection;

    public Estate(UUID id, UUID owner, String world, Set<UUID> plotIds, boolean protection) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.plotIds = (plotIds == null) ? new HashSet<>() : new HashSet<>(plotIds);
        this.protection = protection;
    }

    public Estate(UUID owner, String world, boolean protection) {
        this(UUID.randomUUID(), owner, world, new HashSet<>(), protection);
    }

    public void addPlot(UUID plotId) {
        plotIds.add(plotId);
    }

    public void removePlot(UUID plotId) {
        plotIds.remove(plotId);
    }

    public boolean isEmpty() {
        return plotIds.isEmpty();
    }
}
