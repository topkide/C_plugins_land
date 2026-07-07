package com.dotorimaru.land;

import com.dotorimaru.land.command.LandAdminCommand;
import com.dotorimaru.land.command.LandCommand;
import com.dotorimaru.land.economy.BridgeEconomy;
import com.dotorimaru.land.economy.Economy;
import com.dotorimaru.land.economy.NoneEconomy;
import com.dotorimaru.land.economy.VoucherEconomy;
import com.dotorimaru.land.gui.GuiManager;
import com.dotorimaru.land.listeners.CertificateUseListener;
import com.dotorimaru.land.listeners.GuiListener;
import com.dotorimaru.land.listeners.LandEntryListener;
import com.dotorimaru.land.listeners.PlayerCleanupListener;
import com.dotorimaru.land.listeners.ProtectionListener;
import com.dotorimaru.land.manager.CertificateManager;
import com.dotorimaru.land.manager.LandManager;
import com.dotorimaru.land.manager.MessageManager;
import com.dotorimaru.land.manager.SelectionManager;
import com.dotorimaru.land.storage.Storage;
import com.dotorimaru.land.storage.YamlStorage;
import lombok.Getter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class LandPlugin extends JavaPlugin {

    @Getter
    private static LandPlugin instance;

    private LandConfig landConfig;
    private MessageManager messageManager;
    private Storage storage;
    private CertificateManager certificateManager;
    private LandManager landManager;
    private SelectionManager selectionManager;
    private GuiManager guiManager;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;

        // 설정 (config.yml 주석 보존을 위해 saveConfig 는 호출하지 않음. 누락 키는 코드 기본값으로 폴백)
        saveDefaultConfig();

        landConfig = new LandConfig();
        landConfig.load(getConfig());

        messageManager = new MessageManager(this);

        // 저장소
        storage = new YamlStorage(this);
        storage.init();

        // 매니저
        certificateManager = new CertificateManager(this, storage);
        certificateManager.load();

        landManager = new LandManager(this, storage);
        landManager.load();

        selectionManager = new SelectionManager(this, landConfig, landManager);

        economy = createEconomy();

        guiManager = new GuiManager(this);

        registerListeners();
        registerCommands();
        scheduleAutosave();

        getLogger().info("✅ Land(부동산) 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        getLogger().info("🛑 Land 종료 시작...");
        if (selectionManager != null) selectionManager.shutdown();
        if (landManager != null) landManager.save();
        getLogger().info("✅ Land 가 안전하게 종료되었습니다.");
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new CertificateUseListener(landConfig, messageManager, certificateManager, selectionManager, landManager), this);
        pm.registerEvents(new ProtectionListener(landConfig, messageManager, landManager), this);
        pm.registerEvents(new LandEntryListener(landConfig, messageManager, landManager), this);
        pm.registerEvents(new PlayerCleanupListener(selectionManager), this);
        pm.registerEvents(new GuiListener(guiManager), this);
    }

    private void registerCommands() {
        LandCommand landCommand = new LandCommand(this);
        LandAdminCommand adminCommand = new LandAdminCommand(this);

        PluginCommand land = getCommand("부동산");
        if (land != null) {
            land.setExecutor(landCommand);
            land.setTabCompleter(landCommand);
        }
        PluginCommand admin = getCommand("부동산관리");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }
    }

    private void scheduleAutosave() {
        int minutes = landConfig.getAutosaveMinutes();
        if (minutes <= 0) return;
        long ticks = minutes * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            landManager.save();
            certificateManager.saveAll();
        }, ticks, ticks);
    }

    private Economy createEconomy() {
        return switch (landConfig.getRefundMode()) {
            case BRIDGE -> new BridgeEconomy(this, landConfig.getGiveCommand());
            case VOUCHER -> new VoucherEconomy();
            case NONE -> new NoneEconomy();
        };
    }

    /** /부동산관리 reload */
    public void reloadAll() {
        reloadConfig();
        landConfig.load(getConfig());
        messageManager.reload();
        economy = createEconomy();
        getLogger().info("✅ 설정을 다시 불러왔습니다.");
    }
}
