package com.dotorimaru.land.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 구매서 아이템 식별 유틸.
 *
 * 상점 스크립트는 아이템을 YAML(skript-yaml → Skript Classes.serialize → Bukkit ItemMeta 직렬화)
 * 로 저장했다가 다시 지급합니다. 이 경로에서 재질/CustomModelData/이름/로어 같은
 * "보이는 메타"는 확실히 보존되지만, 숨은 PDC 태그는 버전/구현에 따라 보존이 보장되지 않습니다.
 *
 * 따라서 식별은 시그니처(재질+CMD+이름+로어) 매칭을 "정본"으로 사용하고,
 * PDC 태그는 살아있을 경우에만 빠른 경로로 활용합니다.
 */
public final class Items {

    private Items() {
    }

    private static final String CERT_KEY = "cert";

    public static NamespacedKey certKey(Plugin plugin) {
        return new NamespacedKey(plugin, CERT_KEY);
    }

    /** 아이템에 심어둔 토지권 이름 태그를 읽습니다(없으면 null). */
    public static String readCertTag(Plugin plugin, ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(certKey(plugin), PersistentDataType.STRING);
    }

    /** ItemMeta 에 토지권 이름 태그를 심습니다. */
    public static void writeCertTag(Plugin plugin, ItemMeta meta, String certName) {
        meta.getPersistentDataContainer().set(certKey(plugin), PersistentDataType.STRING, certName);
    }

    /** 상점 왕복에도 보존되는 필드만 뽑아낸 시그니처. */
    public static Signature signatureOf(ItemStack item) {
        if (item == null) return new Signature(Material.AIR, "", Collections.emptyList(), -1);
        Material type = item.getType();
        String name = "";
        List<String> lore = Collections.emptyList();
        int cmd = -1;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) name = meta.getDisplayName();
            if (meta.hasLore()) lore = meta.getLore();
            // 신형 CustomModelData 컴포넌트만 있는 경우 getCustomModelData() 가 예외를 던질 수 있어 방어
            try {
                if (meta.hasCustomModelData()) cmd = meta.getCustomModelData();
            } catch (Throwable ignored) {
                cmd = -1;
            }
        }
        return new Signature(type, name, lore, cmd);
    }

    public static boolean sameSignature(ItemStack a, ItemStack b) {
        return signatureOf(a).equals(signatureOf(b));
    }

    /**
     * 두 아이템이 "보이는" 부분에서 완전히 동일한지(등록 시 시그니처 충돌 검사용).
     */
    public record Signature(Material material, String name, List<String> lore, int customModelData) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Signature s)) return false;
            return customModelData == s.customModelData
                    && material == s.material
                    && Objects.equals(name, s.name)
                    && Objects.equals(lore, s.lore);
        }

        @Override
        public int hashCode() {
            return Objects.hash(material, name, lore, customModelData);
        }
    }
}
