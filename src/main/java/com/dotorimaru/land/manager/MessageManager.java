package com.dotorimaru.land.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * messages.yml 기반 메시지 매니저 (Core 의 MessageManager 패턴을 따름).
 */
public class MessageManager {

    private final JavaPlugin plugin;
    private final Map<String, String> messages = new ConcurrentHashMap<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        messages.clear();
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                messages.put(key, yaml.getString(key));
            }
        }
        plugin.getLogger().info("✅ %d개의 메시지를 로드했습니다.".formatted(messages.size()));
    }

    public String getRaw(String key) {
        return messages.getOrDefault(key, "&c메시지 없음: " + key);
    }

    /** 색상 적용 + %prefix% 및 가변 치환자 처리 */
    public String get(String key, String... replacements) {
        String message = getRaw(key);
        message = message.replace("%prefix%", messages.getOrDefault("prefix", ""));
        Map<String, String> ph = new HashMap<>();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            ph.put(replacements[i], replacements[i + 1]);
        }
        for (Map.Entry<String, String> e : ph.entrySet()) {
            message = message.replace(e.getKey(), e.getValue());
        }
        return colorize(message);
    }

    public void send(CommandSender sender, String key, String... replacements) {
        String msg = get(key, replacements);
        if (!msg.isBlank()) sender.sendMessage(msg);
    }

    public void sendRawLine(CommandSender sender, String legacyLine) {
        sender.sendMessage(colorize(legacyLine));
    }

    /** 진입 안내: 설정에 따라 Title 또는 ActionBar */
    public void showEntry(Player player, boolean actionBar, String key, String... replacements) {
        Component comp = LEGACY.deserialize(get(key, replacements));
        if (actionBar) {
            player.sendActionBar(comp);
        } else {
            player.showTitle(Title.title(
                    comp,
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))
            ));
        }
    }

    public boolean has(String key) {
        return messages.containsKey(key);
    }

    private String colorize(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
