package com.dotorimaru.land.listeners;

import com.dotorimaru.land.LandConfig;
import com.dotorimaru.land.manager.CertificateManager;
import com.dotorimaru.land.manager.LandManager;
import com.dotorimaru.land.manager.MessageManager;
import com.dotorimaru.land.manager.SelectionManager;
import com.dotorimaru.land.model.CertificateType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 구매서(토지권) 우클릭 처리.
 *   - 첫 우클릭: 선택 모드 시작 (미리보기 표시)
 *   - 두 번째 우클릭: 현재 위치에 구매 확정
 *   - 웅크린 채 우클릭: 선택 취소
 */
public class CertificateUseListener implements Listener {

    private final LandConfig config;
    private final MessageManager messages;
    private final CertificateManager certManager;
    private final SelectionManager selectionManager;
    private final LandManager landManager;

    /** 연타/AIR+BLOCK 중복 이벤트 방지용 */
    private final Map<UUID, Long> lastProcessed = new HashMap<>();

    public CertificateUseListener(LandConfig config, MessageManager messages,
                                  CertificateManager certManager,
                                  SelectionManager selectionManager,
                                  LandManager landManager) {
        this.config = config;
        this.messages = messages;
        this.certManager = certManager;
        this.selectionManager = selectionManager;
        this.landManager = landManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        CertificateType cert = certManager.identify(item);
        boolean selecting = selectionManager.isSelecting(player);

        // 구매서를 든 것도, 선택 중인 것도 아니면 관여하지 않음
        if (cert == null && !selecting) return;

        // 우리 상호작용이므로 블록 설치/사용을 막음
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = lastProcessed.get(player.getUniqueId());
        if (last != null && now - last < 250) return;
        lastProcessed.put(player.getUniqueId(), now);

        // 웅크리고 우클릭 → 취소
        if (player.isSneaking()) {
            if (selecting) {
                selectionManager.cancel(player);
                messages.send(player, "select-cancel");
            }
            return;
        }

        if (!selecting) {
            if (!config.isWorldAllowed(player.getWorld().getName())) {
                messages.send(player, "buy-world-not-allowed");
                return;
            }
            selectionManager.start(player, cert);
            messages.send(player, "select-start");
        } else {
            if (!selectionManager.canConfirm(player)) return; // 즉시 확정 방지
            confirm(player);
        }
    }

    private void confirm(Player player) {
        CertificateType cert = selectionManager.getCert(player);
        if (cert == null) {
            selectionManager.cancel(player);
            return;
        }

        // 여전히 같은 구매서를 들고 있는지 확인
        ItemStack hand = player.getInventory().getItemInMainHand();
        CertificateType held = certManager.identify(hand);
        if (held == null || !held.getName().equals(cert.getName())) {
            selectionManager.cancel(player);
            return;
        }

        SelectionManager.Rect rect = selectionManager.computeRect(player, cert);
        if (rect == null) {
            messages.send(player, "select-look-air");
            return;
        }

        LandManager.BuyResult result = landManager.tryBuy(
                player, rect.world(),
                rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ(),
                cert.getPrice(), cert.getCurrency(),
                config.isProtectionDefaultEnabled(), config.getMaxPlotsPerPlayer()
        );

        switch (result.status()) {
            case OVERLAP -> messages.send(player, "buy-overlap"); // 선택 유지: 위치를 옮겨 재시도 가능
            case LIMIT -> {
                messages.send(player, "buy-limit-reached", "%max%", String.valueOf(config.getMaxPlotsPerPlayer()));
                selectionManager.cancel(player);
            }
            case SUCCESS -> {
                consumeOne(player, hand); // 구매서 1장 소모
                selectionManager.cancel(player);
                messages.send(player, "buy-success",
                        "%width%", String.valueOf(cert.getWidth()),
                        "%length%", String.valueOf(cert.getLength()),
                        "%currency%", cert.getCurrency(),
                        "%price%", String.format("%,d", cert.getPrice()));
                if (result.merged()) {
                    messages.send(player, "buy-merged");
                }
            }
        }
    }

    private void consumeOne(Player player, ItemStack hand) {
        int newAmount = hand.getAmount() - 1;
        if (newAmount <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(newAmount);
            player.getInventory().setItemInMainHand(hand);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastProcessed.remove(event.getPlayer().getUniqueId());
    }
}
