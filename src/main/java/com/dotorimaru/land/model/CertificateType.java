package com.dotorimaru.land.model;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;

/**
 * 토지권(구매서) 타입.
 * template 은 실제로 발급/유통되는 구매서 아이템이며,
 * 이 아이템의 시그니처(재질+CMD+이름+로어)로 우클릭 시 종류를 식별합니다.
 */
@Getter
public class CertificateType {

    private final String name;
    private final int width;
    private final int length;
    private final long price;
    private final String currency;
    private final ItemStack template;

    public CertificateType(String name, int width, int length, long price, String currency, ItemStack template) {
        this.name = name;
        this.width = width;
        this.length = length;
        this.price = price;
        this.currency = currency;
        this.template = template;
    }

    /** 지급용 아이템 1개 복제본 */
    public ItemStack createItem(int amount) {
        ItemStack item = template.clone();
        item.setAmount(Math.max(1, amount));
        return item;
    }
}
