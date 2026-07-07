package com.dotorimaru.land.storage;

import com.dotorimaru.land.model.CertificateType;
import com.dotorimaru.land.model.Estate;
import com.dotorimaru.land.model.Plot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class YamlStorage implements Storage {

    private final Plugin plugin;
    private final File dataDir;
    private final File landsFile;
    private final File certsFile;

    public YamlStorage(Plugin plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "data");
        this.landsFile = new File(dataDir, "lands.yml");
        this.certsFile = new File(dataDir, "certificates.yml");
    }

    @Override
    public void init() {
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().warning("데이터 폴더를 생성하지 못했습니다: " + dataDir.getPath());
        }
    }

    // ---------------------------------------------------------------
    // 토지(구획/부지)
    // ---------------------------------------------------------------

    @Override
    public Map<UUID, Plot> loadPlots() {
        Map<UUID, Plot> plots = new HashMap<>();
        if (!landsFile.exists()) return plots;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(landsFile);
        ConfigurationSection root = yaml.getConfigurationSection("plots");
        if (root == null) return plots;

        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                UUID id = UUID.fromString(key);
                Plot plot = new Plot(
                        id,
                        UUID.fromString(s.getString("owner")),
                        s.getString("world"),
                        s.getInt("minX"), s.getInt("minZ"),
                        s.getInt("maxX"), s.getInt("maxZ"),
                        s.getLong("price"),
                        s.getString("currency", ""),
                        s.contains("estate") ? UUID.fromString(s.getString("estate")) : null
                );
                plots.put(id, plot);
            } catch (Exception e) {
                plugin.getLogger().warning("구획 로드 실패(" + key + "): " + e.getMessage());
            }
        }
        return plots;
    }

    @Override
    public Map<UUID, Estate> loadEstates() {
        Map<UUID, Estate> estates = new HashMap<>();
        if (!landsFile.exists()) return estates;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(landsFile);
        ConfigurationSection root = yaml.getConfigurationSection("estates");
        if (root == null) return estates;

        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                UUID id = UUID.fromString(key);
                Set<UUID> plotIds = new HashSet<>();
                for (String p : s.getStringList("plots")) {
                    plotIds.add(UUID.fromString(p));
                }
                Estate estate = new Estate(
                        id,
                        UUID.fromString(s.getString("owner")),
                        s.getString("world"),
                        plotIds,
                        s.getBoolean("protection", true)
                );
                estates.put(id, estate);
            } catch (Exception e) {
                plugin.getLogger().warning("부지 로드 실패(" + key + "): " + e.getMessage());
            }
        }
        return estates;
    }

    @Override
    public void saveLands(Collection<Plot> plots, Collection<Estate> estates) {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Plot plot : plots) {
            String base = "plots." + plot.getId();
            yaml.set(base + ".owner", plot.getOwner().toString());
            yaml.set(base + ".world", plot.getWorld());
            yaml.set(base + ".minX", plot.getMinX());
            yaml.set(base + ".minZ", plot.getMinZ());
            yaml.set(base + ".maxX", plot.getMaxX());
            yaml.set(base + ".maxZ", plot.getMaxZ());
            yaml.set(base + ".price", plot.getPrice());
            yaml.set(base + ".currency", plot.getCurrency());
            if (plot.getEstateId() != null) {
                yaml.set(base + ".estate", plot.getEstateId().toString());
            }
        }

        for (Estate estate : estates) {
            String base = "estates." + estate.getId();
            yaml.set(base + ".owner", estate.getOwner().toString());
            yaml.set(base + ".world", estate.getWorld());
            yaml.set(base + ".protection", estate.isProtection());
            yaml.set(base + ".plots", estate.getPlotIds().stream().map(UUID::toString).toList());
        }

        save(yaml, landsFile);
    }

    // ---------------------------------------------------------------
    // 토지권(구매서)
    // ---------------------------------------------------------------

    @Override
    public ItemStack loadBaseItem() {
        if (!certsFile.exists()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(certsFile);
        return yaml.getItemStack("base");
    }

    @Override
    public void saveBaseItem(ItemStack item) {
        YamlConfiguration yaml = certsFile.exists()
                ? YamlConfiguration.loadConfiguration(certsFile)
                : new YamlConfiguration();
        yaml.set("base", item);
        save(yaml, certsFile);
    }

    @Override
    public Map<String, CertificateType> loadCertificates() {
        Map<String, CertificateType> certs = new HashMap<>();
        if (!certsFile.exists()) return certs;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(certsFile);
        ConfigurationSection root = yaml.getConfigurationSection("certificates");
        if (root == null) return certs;

        for (String name : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(name);
                if (s == null) continue;
                ItemStack template = s.getItemStack("item");
                if (template == null) continue;
                CertificateType type = new CertificateType(
                        name,
                        s.getInt("width"),
                        s.getInt("length"),
                        s.getLong("price"),
                        s.getString("currency", ""),
                        template
                );
                certs.put(name, type);
            } catch (Exception e) {
                plugin.getLogger().warning("토지권 로드 실패(" + name + "): " + e.getMessage());
            }
        }
        return certs;
    }

    @Override
    public void saveCertificates(Collection<CertificateType> certificates) {
        YamlConfiguration yaml = certsFile.exists()
                ? YamlConfiguration.loadConfiguration(certsFile)
                : new YamlConfiguration();

        yaml.set("certificates", null); // 초기화 후 재작성
        for (CertificateType type : certificates) {
            String base = "certificates." + type.getName();
            yaml.set(base + ".width", type.getWidth());
            yaml.set(base + ".length", type.getLength());
            yaml.set(base + ".price", type.getPrice());
            yaml.set(base + ".currency", type.getCurrency());
            yaml.set(base + ".item", type.getTemplate());
        }
        save(yaml, certsFile);
    }

    // ---------------------------------------------------------------

    private void save(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("파일 저장 실패(" + file.getName() + "): " + e.getMessage());
        }
    }
}
