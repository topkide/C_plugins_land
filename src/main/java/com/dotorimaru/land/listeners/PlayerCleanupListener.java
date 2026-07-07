package com.dotorimaru.land.listeners;

import com.dotorimaru.land.manager.SelectionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 접속 종료 시 선택 모드와 미리보기를 정리합니다.
 */
public class PlayerCleanupListener implements Listener {

    private final SelectionManager selectionManager;

    public PlayerCleanupListener(SelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectionManager.cancel(event.getPlayer());
    }
}
