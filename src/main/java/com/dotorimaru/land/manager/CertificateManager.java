package com.dotorimaru.land.manager;

import com.dotorimaru.land.model.CertificateType;
import com.dotorimaru.land.storage.Storage;
import com.dotorimaru.land.util.ItemBuilder;
import com.dotorimaru.land.util.Items;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 토지권(구매서) 관리자.
 * - /부동산관리 아이템지정 으로 캡처한 "기본 디자인"을 보관
 * - 각 크기별 토지권 타입을 생성/삭제
 * - 우클릭한 아이템이 어떤 토지권인지 식별
 */
public class CertificateManager {

    public enum CreateResult {SUCCESS, NAME_EXISTS, SIGNATURE_COLLISION, NO_BASE}

    private final Plugin plugin;
    private final Storage storage;

    @Getter
    private ItemStack baseItem;
    private final Map<String, CertificateType> certificates = new LinkedHashMap<>();

    public CertificateManager(Plugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load() {
        baseItem = storage.loadBaseItem();
        certificates.clear();
        certificates.putAll(storage.loadCertificates());
        plugin.getLogger().info("✅ %d개의 토지권을 로드했습니다.".formatted(certificates.size()));
    }

    // ----- 기본 디자인 -----

    public boolean hasBase() {
        return baseItem != null;
    }

    public void setBaseItem(ItemStack item) {
        this.baseItem = item.clone();
        this.baseItem.setAmount(1);
        storage.saveBaseItem(this.baseItem);
    }

    // ----- 토지권 타입 -----

    public Collection<CertificateType> getCertificates() {
        return certificates.values();
    }

    public CertificateType getCertificate(String name) {
        return certificates.get(name);
    }

    public CreateResult createCertificate(String name, int width, int length, long price, String currency) {
        if (baseItem == null) return CreateResult.NO_BASE;
        if (certificates.containsKey(name)) return CreateResult.NAME_EXISTS;

        ItemStack template = mintTemplate(name, width, length);

        // 시그니처 충돌 검사(같은 디자인이 다른 이름으로 이미 등록되어 있으면 식별 불가)
        Items.Signature sig = Items.signatureOf(template);
        for (CertificateType existing : certificates.values()) {
            if (Items.signatureOf(existing.getTemplate()).equals(sig)) {
                return CreateResult.SIGNATURE_COLLISION;
            }
        }

        certificates.put(name, new CertificateType(name, width, length, price, currency, template));
        storage.saveCertificates(certificates.values());
        return CreateResult.SUCCESS;
    }

    public boolean deleteCertificate(String name) {
        if (certificates.remove(name) == null) return false;
        storage.saveCertificates(certificates.values());
        return true;
    }

    /** 기본 디자인 + 크기 로어 + PDC 태그로 실제 유통 아이템을 생성 */
    private ItemStack mintTemplate(String name, int width, int length) {
        ItemStack template = baseItem.clone();
        template.setAmount(1);
        ItemMeta meta = template.getItemMeta();

        if (meta != null) {
            if (!meta.hasDisplayName()) {
                meta.setDisplayName(ItemBuilder.color("&e토지 계약서"));
            }
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ItemBuilder.color("&7크기: &f" + width + " x " + length));
            lore.add(ItemBuilder.color("&8우클릭하여 토지 범위를 지정합니다"));
            meta.setLore(lore);

            // 살아남으면 빠른 식별용, 사라져도 시그니처로 식별되므로 안전
            Items.writeCertTag(plugin, meta, name);
            template.setItemMeta(meta);
        }
        return template;
    }

    /** 기본 디자인 + 토지권 목록을 저장(자동저장/종료용) */
    public void saveAll() {
        if (baseItem != null) storage.saveBaseItem(baseItem);
        storage.saveCertificates(certificates.values());
    }

    // ----- 식별 -----

    /** 우클릭한 아이템이 토지권이면 해당 타입을, 아니면 null 을 반환 */
    public CertificateType identify(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        // 1) PDC 빠른 경로
        String tag = Items.readCertTag(plugin, item);
        if (tag != null) {
            CertificateType byTag = certificates.get(tag);
            if (byTag != null) return byTag;
        }

        // 2) 시그니처(재질+CMD+이름+로어) 매칭 — 상점 왕복에도 안전
        for (CertificateType type : certificates.values()) {
            if (Items.sameSignature(item, type.getTemplate())) {
                return type;
            }
        }
        return null;
    }
}
