package com.dotorimaru.land.economy;

import org.bukkit.entity.Player;

/**
 * 환불 지급 추상화. 기존 Skript 화폐 시스템과 연동하는 방식을 캡슐화합니다.
 */
public interface Economy {

    /**
     * 지정 화폐로 금액을 지급(환불)합니다.
     *
     * @param player   대상 플레이어
     * @param currency currency.sk 기준 화폐 이름
     * @param amount   지급 금액
     */
    void refund(Player player, String currency, long amount);
}
