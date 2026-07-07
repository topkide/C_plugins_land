package com.dotorimaru.land.economy;

import com.dotorimaru.land.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * currency.sk 의 수표(종이) 아이템과 동일한 포맷으로 환불 수표를 발급합니다.
 *
 * currency.sk 의 우클릭 입금 로직은 아래만 검사/파싱하므로 그 형식을 정확히 맞춥니다.
 *   - 아이템: 종이(PAPER)
 *   - 표시 이름: "§7[수표]" 로 시작
 *   - 로어 1: "§7화폐: §b<화폐이름>"
 *   - 로어 2: "§7금액: §e<금액>"   (단위는 붙이지 않아도 파싱에 문제 없음)
 */
public class VoucherEconomy implements Economy {

    @Override
    public void refund(Player player, String currency, long amount) {
        if (amount <= 0) return;

        ItemStack voucher = new ItemBuilder(Material.PAPER)
                .setDisplayName("&7[수표] &r" + amount)
                .setLore(
                        "&7화폐: &b" + currency,
                        "&7금액: &e" + amount,
                        "",
                        "&7| &e우클릭 &7: &a지갑에 추가합니다"
                )
                .build();

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(voucher);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }
}
