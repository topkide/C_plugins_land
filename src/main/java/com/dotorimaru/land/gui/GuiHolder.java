package com.dotorimaru.land.gui;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 이 플러그인의 GUI 임을 식별하기 위한 InventoryHolder.
 */
@Getter
public class GuiHolder implements InventoryHolder {

    public enum Type {LIST, DETAIL, CONFIRM}

    private final Type type;
    private final UUID estateId;                       // DETAIL/CONFIRM 에서 사용
    private final Map<Integer, UUID> slotEstates = new HashMap<>(); // LIST 에서 슬롯→부지 매핑

    @Setter
    private Inventory inventory;

    public GuiHolder(Type type, UUID estateId) {
        this.type = type;
        this.estateId = estateId;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
