package com.dotorimaru.land.economy;

import org.bukkit.entity.Player;

/** 자동 환불 지급을 하지 않는 구현(운영자가 수동 처리). */
public class NoneEconomy implements Economy {

    @Override
    public void refund(Player player, String currency, long amount) {
        // no-op
    }
}
