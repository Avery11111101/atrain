package com.avery.atrain.i18n;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.util.TextUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LanguageManager {
    private final AtrainPlugin plugin;
    private final Map<String, YamlConfiguration> languages = new LinkedHashMap<>();
    private final Map<UUID, String> playerLang = new HashMap<>();
    private File playerLangFile;
    private String defaultLang;

    public LanguageManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        languages.clear();
        defaultLang = plugin.getConfigManager().getDefaultLanguage();
        playerLangFile = new File(plugin.getDataFolder(), "player_languages.yml");
        loadPlayerLanguages();
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        String[] builtIn = {"zh_TW", "zh_CN", "en_US", "ja_JP"};
        for (String code : builtIn) {
            File out = new File(langDir, code + ".yml");
            if (!out.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
            loadFile(out, code);
        }

        File[] extras = langDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (extras != null) {
            for (File f : extras) {
                String code = f.getName().replace(".yml", "");
                if (!languages.containsKey(code)) loadFile(f, code);
            }
        }
    }

    private void loadFile(File file, String code) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                yaml.setDefaults(defaults);
            }
        } catch (Exception ignored) {}
        languages.put(code, yaml);
    }

    public List<String> getAvailableLanguages() {
        return new ArrayList<>(languages.keySet());
    }

    public String getDefaultLanguage() { return defaultLang; }

    public String getPlayerLanguage(Player player) {
        return playerLang.getOrDefault(player.getUniqueId(), defaultLang);
    }

    public boolean setPlayerLanguage(Player player, String code) {
        if (!languages.containsKey(code)) return false;
        playerLang.put(player.getUniqueId(), code);
        savePlayerLanguages();
        return true;
    }

    private void loadPlayerLanguages() {
        playerLang.clear();
        if (playerLangFile == null || !playerLangFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerLangFile);
        for (String key : yaml.getKeys(false)) {
            try {
                playerLang.put(UUID.fromString(key), yaml.getString(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void savePlayerLanguages() {
        if (playerLangFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : playerLang.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(playerLangFile);
        } catch (Exception e) {
            plugin.getLogger().warning("無法儲存玩家語言設定: " + e.getMessage());
        }
    }

    public String get(Player player, String key) {
        return get(player, key, Map.of());
    }

    public String get(Player player, String key, Map<String, String> placeholders) {
        String lang = player != null ? getPlayerLanguage(player) : defaultLang;
        String msg = resolve(lang, key);
        if (msg == null && !lang.equals(defaultLang)) {
            msg = resolve(defaultLang, key);
        }
        if (msg == null) return "§c[" + key + "]";
        return TextUtil.format(msg, placeholders);
    }

    public String getRaw(String lang, String key) {
        String msg = resolve(lang, key);
        return msg != null ? msg : "§c[" + key + "]";
    }

    private String resolve(String lang, String key) {
        YamlConfiguration yaml = languages.get(lang);
        if (yaml == null) return null;
        String val = yaml.getString(key);
        if (val != null) return val;
        return yaml.getString(key.replace('.', '_'));
    }

    public List<String> getList(Player player, String key) {
        String lang = getPlayerLanguage(player);
        List<String> list = resolveList(lang, key);
        if (list.isEmpty() && !lang.equals(defaultLang)) {
            list = resolveList(defaultLang, key);
        }
        return list;
    }

    private List<String> resolveList(String lang, String key) {
        YamlConfiguration yaml = languages.get(lang);
        if (yaml == null) return List.of();
        List<String> list = yaml.getStringList(key);
        if (list.isEmpty()) {
            list = yaml.getStringList(key.replace('.', '_'));
        }
        return list.isEmpty() ? List.of() : list;
    }
}
