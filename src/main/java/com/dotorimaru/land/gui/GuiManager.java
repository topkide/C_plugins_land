package com.dotorimaru.land.gui;

import com.dotorimaru.land.LandConfig;
import com.dotorimaru.land.LandPlugin;
import com.dotorimaru.land.manager.LandManager;
import com.dotorimaru.land.manager.MessageManager;
import com.dotorimaru.land.model.Estate;
import com.dotorimaru.land.model.Plot;
import com.dotorimaru.land.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 토지 관리 GUI (목록 / 상세 / 판매 확인) 생성·열기·클릭 처리.
 */
public class GuiManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final LandPlugin plugin;
    private final LandConfig config;
    private final MessageManager messages;
    private final LandManager landManager;

    public GuiManager(LandPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getLandConfig();
        this.messages = plugin.getMessageManager();
        this.landManager = plugin.getLandManager();
    }

    private Component title(String key) {
        return LEGACY.deserialize(ItemBuilder.color("&8" + messages.getRaw(key)));
    }

    // ---------------------------------------------------------------
    // 목록
    // ---------------------------------------------------------------

    public void openList(Player player) {
        List<Estate> owned = landManager.getEstatesOf(player.getUniqueId());
        GuiHolder holder = new GuiHolder(GuiHolder.Type.LIST, null);
        Inventory inv = Bukkit.createInventory(holder, 54, title("gui-list-title"));
        holder.setInventory(inv);

        if (owned.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.PAPER)
                    .setDisplayName(messages.getRaw("gui-empty"))
                    .build());
        } else {
            int slot = 0;
            for (Estate estate : owned) {
                if (slot >= 54) break;
                inv.setItem(slot, estateIcon(estate));
                holder.getSlotEstates().put(slot, estate.getId());
                slot++;
            }
        }
        player.openInventory(inv);
    }

    private ItemStack estateIcon(Estate estate) {
        int[] center = landManager.estateCenter(estate);
        int[] b = landManager.estateBounds(estate);
        int count = landManager.plotCountOf(estate);

        ItemBuilder icon = new ItemBuilder(Material.GRASS_BLOCK)
                .setDisplayName("&a토지 &7(" + estate.getWorld() + ")")
                .addLore("&7위치: &f" + center[0] + ", " + center[1]);

        if (count == 1) {
            Plot p = landManager.firstPlot(estate);
            if (p != null) icon.addLore("&7크기: &f" + p.width() + " x " + p.length());
        } else {
            int bw = b[2] - b[0] + 1;
            int bl = b[3] - b[1] + 1;
            icon.addLore("&7구획: &f" + count + "개 &7(범위 " + bw + "x" + bl + ")");
        }
        icon.addLore("&7보호: " + (estate.isProtection() ? "&a켜짐" : "&c꺼짐"));
        icon.addLore("");
        icon.addLore("&e좌클릭 &7- 상세 정보");
        return icon.build();
    }

    // ---------------------------------------------------------------
    // 상세
    // ---------------------------------------------------------------

    public void openDetail(Player player, UUID estateId) {
        Estate estate = landManager.getEstate(estateId);
        if (estate == null) {
            player.closeInventory();
            return;
        }
        GuiHolder holder = new GuiHolder(GuiHolder.Type.DETAIL, estateId);
        Inventory inv = Bukkit.createInventory(holder, 27, title("gui-detail-title"));
        holder.setInventory(inv);

        int[] center = landManager.estateCenter(estate);
        int count = landManager.plotCountOf(estate);

        inv.setItem(9, new ItemBuilder(Material.ENDER_PEARL)
                .setDisplayName("&b텔레포트")
                .setLore("&7토지 중심으로 이동합니다.")
                .build());

        inv.setItem(11, new ItemBuilder(estate.isProtection() ? Material.LIME_DYE : Material.GRAY_DYE)
                .setDisplayName(estate.isProtection() ? "&a보호: 켜짐" : "&c보호: 꺼짐")
                .setLore("&7클릭하여 전환합니다.")
                .build());

        inv.setItem(13, new ItemBuilder(Material.FILLED_MAP)
                .setDisplayName("&f토지 정보")
                .setLore(
                        "&7월드: &f" + estate.getWorld(),
                        "&7중심: &f" + center[0] + ", " + center[1],
                        "&7구획 수: &f" + count + "개"
                )
                .build());

        inv.setItem(15, new ItemBuilder(Material.GOLD_INGOT)
                .setDisplayName("&e판매하기")
                .setLore("&7환불 비율: &f" + config.getRefundPercent() + "%")
                .build());

        inv.setItem(22, new ItemBuilder(Material.BARRIER)
                .setDisplayName("&c닫기")
                .build());

        player.openInventory(inv);
    }

    // ---------------------------------------------------------------
    // 판매 확인
    // ---------------------------------------------------------------

    public void openConfirm(Player player, UUID estateId) {
        Estate estate = landManager.getEstate(estateId);
        if (estate == null) {
            player.closeInventory();
            return;
        }
        GuiHolder holder = new GuiHolder(GuiHolder.Type.CONFIRM, estateId);
        Inventory inv = Bukkit.createInventory(holder, 27, title("gui-confirm-title"));
        holder.setInventory(inv);

        inv.setItem(11, new ItemBuilder(Material.GREEN_WOOL)
                .setDisplayName("&a예, 판매합니다")
                .setLore("&7환불 비율: &f" + config.getRefundPercent() + "%")
                .build());

        inv.setItem(13, new ItemBuilder(Material.PAPER)
                .setDisplayName("&f판매 시 토지가 사라지고")
                .setLore("&f설정된 비율만큼 환불됩니다.")
                .build());

        inv.setItem(15, new ItemBuilder(Material.RED_WOOL)
                .setDisplayName("&c아니오")
                .build());

        player.openInventory(inv);
    }

    // ---------------------------------------------------------------
    // 클릭 처리
    // ---------------------------------------------------------------

    public void handleClick(Player player, GuiHolder holder, int slot, ClickType click) {
        switch (holder.getType()) {
            case LIST -> {
                UUID id = holder.getSlotEstates().get(slot);
                if (id != null) openDetail(player, id);
            }
            case DETAIL -> {
                Estate estate = landManager.getEstate(holder.getEstateId());
                if (estate == null) {
                    player.closeInventory();
                    return;
                }
                switch (slot) {
                    case 9 -> teleport(player, estate);
                    case 11 -> {
                        boolean now = landManager.toggleProtection(estate.getId());
                        messages.send(player, now ? "protection-on" : "protection-off");
                        openDetail(player, estate.getId());
                    }
                    case 15 -> openConfirm(player, estate.getId());
                    case 22 -> player.closeInventory();
                    default -> {
                    }
                }
            }
            case CONFIRM -> {
                switch (slot) {
                    case 11 -> doSell(player, holder.getEstateId());
                    case 15 -> openDetail(player, holder.getEstateId());
                    default -> {
                    }
                }
            }
        }
    }

    private void teleport(Player player, Estate estate) {
        World world = Bukkit.getWorld(estate.getWorld());
        if (world == null) return;
        int[] c = landManager.estateCenter(estate);
        int y = world.getHighestBlockYAt(c[0], c[1]) + 1;
        player.closeInventory();
        player.teleport(new Location(world, c[0] + 0.5, y, c[1] + 0.5));
    }

    private void doSell(Player player, UUID estateId) {
        Estate estate = landManager.getEstate(estateId);
        if (estate == null) {
            player.closeInventory();
            return;
        }
        if (!estate.getOwner().equals(player.getUniqueId())) {
            messages.send(player, "sell-not-owner");
            player.closeInventory();
            return;
        }

        Map<String, Long> priceByCurrency = landManager.sellEstate(estateId);
        player.closeInventory();
        messages.send(player, "sell-success");

        int percent = config.getRefundPercent();
        boolean anyVoucher = false;

        for (Map.Entry<String, Long> e : priceByCurrency.entrySet()) {
            String currency = e.getKey();
            long refund = Math.round(e.getValue() * percent / 100.0);
            if (refund <= 0) continue;

            plugin.getEconomy().refund(player, currency, refund);

            switch (config.getRefundMode()) {
                case BRIDGE -> messages.sendRawLine(player,
                        messages.get("sell-refund", "%amount%", String.format("%,d", refund), "%currency%", currency));
                case VOUCHER -> anyVoucher = true;
                case NONE -> {
                }
            }
        }
        if (anyVoucher) {
            messages.send(player, "sell-refund-voucher");
        }
    }
}
