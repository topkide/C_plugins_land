package com.dotorimaru.land.command;

import com.dotorimaru.land.LandPlugin;
import com.dotorimaru.land.manager.CertificateManager;
import com.dotorimaru.land.manager.LandManager;
import com.dotorimaru.land.manager.MessageManager;
import com.dotorimaru.land.model.CertificateType;
import com.dotorimaru.land.model.Estate;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class LandAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "아이템지정", "만들기", "삭제", "목록", "지급", "정보", "reload");

    private final LandPlugin plugin;

    public LandAdminCommand(LandPlugin plugin) {
        this.plugin = plugin;
    }

    private MessageManager msg() {
        return plugin.getMessageManager();
    }

    private CertificateManager certs() {
        return plugin.getCertificateManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("land.admin")) {
            msg().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0]) {
            case "아이템지정" -> setBase(sender);
            case "만들기" -> create(sender, args);
            case "삭제" -> delete(sender, args);
            case "목록" -> list(sender);
            case "지급" -> give(sender, args);
            case "정보" -> info(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                msg().send(sender, "reload-done");
            }
            default -> help(sender);
        }
        return true;
    }

    private void setBase(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "player-only");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg().send(player, "cert-base-empty");
            return;
        }
        certs().setBaseItem(hand);
        msg().send(player, "cert-base-set");
    }

    private void create(CommandSender sender, String[] args) {
        // 만들기 <가로> <세로> <가격> <화폐> [이름]
        if (args.length < 5) {
            msg().sendRawLine(sender, "&c/부동산관리 만들기 <가로> <세로> <가격> <화폐> [이름]");
            return;
        }
        Integer width = parseInt(args[1]);
        Integer length = parseInt(args[2]);
        Long price = parseLong(args[3]);
        String currency = args[4];
        if (width == null || length == null || price == null || width <= 0 || length <= 0 || price < 0) {
            msg().sendRawLine(sender, "&c가로/세로/가격 값이 올바르지 않습니다.");
            return;
        }
        String name = (args.length >= 6) ? args[5] : (width + "x" + length);

        CertificateManager.CreateResult result = certs().createCertificate(name, width, length, price, currency);
        switch (result) {
            case NO_BASE -> msg().send(sender, "cert-need-base");
            case NAME_EXISTS -> msg().send(sender, "cert-exists", "%name%", name);
            case SIGNATURE_COLLISION -> msg().sendRawLine(sender,
                    "&c이미 동일한 디자인의 토지권이 있습니다. 기본 아이템을 다르게(이름/설명/모델) 지정한 뒤 만들어주세요.");
            case SUCCESS -> msg().send(sender, "cert-created",
                    "%name%", name,
                    "%width%", String.valueOf(width),
                    "%length%", String.valueOf(length),
                    "%price%", String.format("%,d", price));
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg().sendRawLine(sender, "&c/부동산관리 삭제 <이름>");
            return;
        }
        String name = args[1];
        if (certs().deleteCertificate(name)) {
            msg().send(sender, "cert-deleted", "%name%", name);
        } else {
            msg().send(sender, "cert-not-found", "%name%", name);
        }
    }

    private void list(CommandSender sender) {
        if (certs().getCertificates().isEmpty()) {
            msg().send(sender, "cert-list-empty");
            return;
        }
        msg().send(sender, "cert-list-header");
        for (CertificateType type : certs().getCertificates()) {
            msg().sendRawLine(sender, msg().get("cert-list-line",
                    "%name%", type.getName(),
                    "%width%", String.valueOf(type.getWidth()),
                    "%length%", String.valueOf(type.getLength()),
                    "%price%", String.format("%,d", type.getPrice()),
                    "%currency%", type.getCurrency()));
        }
    }

    private void give(CommandSender sender, String[] args) {
        // 지급 <플레이어> <이름> [개수]
        if (args.length < 3) {
            msg().sendRawLine(sender, "&c/부동산관리 지급 <플레이어> <토지권이름> [개수]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg().send(sender, "cert-give-target-offline");
            return;
        }
        CertificateType type = certs().getCertificate(args[2]);
        if (type == null) {
            msg().send(sender, "cert-not-found", "%name%", args[2]);
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            Integer a = parseInt(args[3]);
            if (a != null && a > 0) amount = a;
        }

        ItemStack item = type.createItem(amount);
        if (!target.getInventory().addItem(item).isEmpty()) {
            msg().send(sender, "cert-give-inventory-full");
            return;
        }
        msg().send(sender, "cert-given",
                "%target%", target.getName(),
                "%name%", type.getName(),
                "%amount%", String.valueOf(amount));
    }

    private void info(CommandSender sender, String[] args) {
        UUID target;
        String targetName;
        if (args.length >= 2) {
            Player p = Bukkit.getPlayerExact(args[1]);
            if (p == null) {
                msg().send(sender, "cert-give-target-offline");
                return;
            }
            target = p.getUniqueId();
            targetName = p.getName();
        } else if (sender instanceof Player self) {
            target = self.getUniqueId();
            targetName = self.getName();
        } else {
            msg().send(sender, "player-only");
            return;
        }

        LandManager land = plugin.getLandManager();
        List<Estate> estates = land.getEstatesOf(target);
        msg().sendRawLine(sender, "&a" + targetName + " &7님의 토지: &f" + estates.size() + "개");
        for (Estate estate : estates) {
            int[] c = land.estateCenter(estate);
            int count = land.plotCountOf(estate);
            msg().sendRawLine(sender, "&7| &f" + estate.getWorld() + " &7(" + c[0] + ", " + c[1] + ") "
                    + "&7구획 &f" + count + "개 &7보호 " + (estate.isProtection() ? "&a켜짐" : "&c꺼짐"));
        }
    }

    private void help(CommandSender sender) {
        msg().sendRawLine(sender, "&a===== 부동산 관리 =====");
        msg().sendRawLine(sender, "&f/부동산관리 아이템지정 &7- 손에 든 아이템을 구매서 기본 디자인으로");
        msg().sendRawLine(sender, "&f/부동산관리 만들기 <가로> <세로> <가격> <화폐> [이름]");
        msg().sendRawLine(sender, "&f/부동산관리 삭제 <이름>");
        msg().sendRawLine(sender, "&f/부동산관리 목록");
        msg().sendRawLine(sender, "&f/부동산관리 지급 <플레이어> <이름> [개수]");
        msg().sendRawLine(sender, "&f/부동산관리 정보 [플레이어]");
        msg().sendRawLine(sender, "&f/부동산관리 reload");
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("land.admin")) return List.of();

        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            switch (args[0]) {
                case "삭제" -> {
                    return filter(certNames(), args[1]);
                }
                case "지급", "정보" -> {
                    return filter(onlinePlayers(), args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3 && args[0].equals("지급")) {
            return filter(certNames(), args[2]);
        }
        return List.of();
    }

    private List<String> certNames() {
        return certs().getCertificates().stream().map(CertificateType::getName).collect(Collectors.toList());
    }

    private List<String> onlinePlayers() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String prefix) {
        String low = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(low)) out.add(o);
        }
        return out;
    }
}
