package com.dotorimaru.land.storage;

import com.dotorimaru.land.model.CertificateType;
import com.dotorimaru.land.model.Estate;
import com.dotorimaru.land.model.Plot;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 데이터 저장소 추상화.
 * 현재는 YAML 구현만 존재하며, 추후 멀티서버 확장 시
 * 이 인터페이스를 구현한 MySQL/Redis 백엔드로 교체할 수 있습니다.
 */
public interface Storage {

    void init();

    Map<UUID, Plot> loadPlots();

    Map<UUID, Estate> loadEstates();

    void saveLands(Collection<Plot> plots, Collection<Estate> estates);

    ItemStack loadBaseItem();

    void saveBaseItem(ItemStack item);

    Map<String, CertificateType> loadCertificates();

    void saveCertificates(Collection<CertificateType> certificates);
}
