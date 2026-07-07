package com.dotorimaru.land.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * land_bridge.sk 의 명령을 콘솔로 호출하여 currency.sk 화폐에 즉시 입금합니다.
 * 상점 판매가 돈을 지급하는 방식과 동일하게 동작합니다.
 */
public class BridgeEconomy implements Economy {

    private final Plugin plugin;
    private final String commandTemplate;

    public BridgeEconomy(Plugin plugin, String commandTemplate) {
        this.plugin = plugin;
        this.commandTemplate = commandTemplate;
    }

    @Override
    public void refund(Player player, String currency, long amount) {
        if (amount <= 0) return;
        String command = commandTemplate
                .replace("%player%", player.getName())
                .replace("%currency%", currency)
                .replace("%amount%", String.valueOf(amount));

        // 메인 스레드에서 콘솔 명령 실행
        if (Bukkit.isPrimaryThread()) {
            dispatch(command);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> dispatch(command));
        }
    }

    private void dispatch(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
