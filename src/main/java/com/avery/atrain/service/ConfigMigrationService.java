package com.avery.atrain.service;

import com.avery.atrain.AtrainPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * 設定檔無痛遷移引擎 (ConfigMigrationService)
 * 保留服主原本設定與註解，並自動注入 Jar 內新發布的鍵值與繁中說明註解。
 */
public class ConfigMigrationService {

    private final AtrainPlugin plugin;

    public ConfigMigrationService(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkAndMigrate() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        int currentVersion = currentConfig.getInt("config-version", 1);

        InputStream jarConfigStream = plugin.getResource("config.yml");
        if (jarConfigStream == null) return;
        YamlConfiguration jarConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(jarConfigStream, StandardCharsets.UTF_8));
        int targetVersion = jarConfig.getInt("config-version", 1);

        if (currentVersion >= targetVersion) {
            return;
        }

        plugin.getLogger().info("檢測到 config.yml 版本 (v" + currentVersion + ") 低於最新版本 (v" + targetVersion + ")，正在執行無痛遷移...");

        File backupFile = new File(plugin.getDataFolder(), "config.backup-v" + currentVersion + ".yml");
        try {
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("已建立舊設定檔備份: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("建立設定檔備份失敗: " + e.getMessage());
        }

        try {
            migrateConfigFile(configFile, currentConfig);
            plugin.getLogger().info("config.yml 已成功無痛升級至 v" + targetVersion + "！");
        } catch (Exception e) {
            plugin.getLogger().severe("升級 config.yml 時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void migrateConfigFile(File configFile, YamlConfiguration currentConfig) throws IOException {
        InputStream jarConfigStream = plugin.getResource("config.yml");
        if (jarConfigStream == null) return;

        List<String> templateLines = new BufferedReader(new InputStreamReader(jarConfigStream, StandardCharsets.UTF_8))
                .lines().toList();

        List<String> outputLines = new ArrayList<>();
        Stack<String> pathStack = new Stack<>();

        for (String line : templateLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                outputLines.add(line);
                continue;
            }

            int indent = getIndent(line);
            int keyEndIndex = line.indexOf(':');
            if (keyEndIndex == -1) {
                outputLines.add(line);
                continue;
            }

            String key = line.substring(indent, keyEndIndex).trim();

            while (!pathStack.isEmpty() && indent <= (pathStack.size() - 1) * 2) {
                pathStack.pop();
            }
            pathStack.push(key);

            String fullPath = String.join(".", pathStack);

            if (currentConfig.contains(fullPath) && !currentConfig.isConfigurationSection(fullPath)) {
                Object userValue = currentConfig.get(fullPath);
                String valueStr = formatYamlValue(userValue, indent);
                String prefix = line.substring(0, keyEndIndex + 1);
                outputLines.add(prefix + " " + valueStr);
            } else {
                outputLines.add(line);
            }
        }

        Files.write(configFile.toPath(), outputLines, StandardCharsets.UTF_8);
    }

    private int getIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private String formatYamlValue(Object val, int indent) {
        if (val instanceof String s) {
            if (s.contains("\n")) {
                return "|\n" + " ".repeat(indent + 2) + s.replace("\n", "\n" + " ".repeat(indent + 2));
            }
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
        if (val instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("\n");
            String pad = " ".repeat(indent + 2);
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    sb.append(pad).append("- ");
                    boolean first = true;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (!first) sb.append("\n").append(pad).append("  ");
                        sb.append(entry.getKey()).append(": ").append(formatYamlValue(entry.getValue(), indent + 4));
                        first = false;
                    }
                    sb.append("\n");
                } else {
                    sb.append(pad).append("- ").append(formatYamlValue(item, indent + 2)).append("\n");
                }
            }
            return sb.toString().stripTrailing();
        }
        return String.valueOf(val);
    }
}
