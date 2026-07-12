package com.avery.atrain.manager;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.SpeedBlock;
import com.avery.atrain.util.RailUtil;
import com.avery.atrain.util.StationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 調速方塊（軌下鑽石塊）管理：讀寫 speed_blocks.yml、依座標查詢、以及依礦車位置找軌下調速塊。
 */
public class SpeedBlockManager {

    private final AtrainPlugin plugin;
    private final Map<String, SpeedBlock> blocks = new HashMap<>();

    public SpeedBlockManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        blocks.clear();
        File file = new File(plugin.getDataFolder(), "speed_blocks.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("speed_blocks");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            String world = s.getString("world");
            if (world == null) continue;
            SpeedBlock sb = new SpeedBlock(world,
                    s.getInt("x"), s.getInt("y"), s.getInt("z"),
                    s.getDouble("speed", 0.4), s.getInt("ramp", 4));
            blocks.put(sb.key(), sb);
        }
    }

    public void save() {
        File file = new File(plugin.getDataFolder(), "speed_blocks.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (SpeedBlock sb : blocks.values()) {
            String path = "speed_blocks.b" + (i++);
            yaml.set(path + ".world", sb.getWorld());
            yaml.set(path + ".x", sb.getX());
            yaml.set(path + ".y", sb.getY());
            yaml.set(path + ".z", sb.getZ());
            yaml.set(path + ".speed", sb.getSpeed());
            yaml.set(path + ".ramp", sb.getRamp());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("無法儲存 speed_blocks.yml: " + e.getMessage());
        }
    }

    /** 該方塊（須為鑽石塊）是否為已設定的調速方塊 */
    public SpeedBlock getAt(Block block) {
        if (block == null || block.getType() != Material.DIAMOND_BLOCK) return null;
        return blocks.get(SpeedBlock.key(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ()));
    }

    /** 礦車所在軌道柱內若含調速鑽石塊則回傳（鐵軌下方可隔金磚等方塊） */
    public SpeedBlock getUnderRail(Location cartLoc) {
        Block rail = RailUtil.findRailBlock(cartLoc);
        if (rail == null) return null;
        Block diamond = StationUtil.findSpeedBlockBelow(rail);
        return resolveAt(diamond);
    }

    /**
     * 解析調速鑽石塊設定；世界內已擺放但未登記者會以預設值自動登記。
     * 呼叫端須已確認該柱有鐵軌（例如經 {@link #getUnderRail} 或路徑探查）。
     */
    public SpeedBlock resolveAt(Block diamond) {
        if (diamond == null || diamond.getType() != Material.DIAMOND_BLOCK) return null;
        SpeedBlock existing = getAt(diamond);
        if (existing != null) return existing;
        var cfg = plugin.getConfigManager();
        return getOrCreate(diamond, cfg.getSpeedBlockDefault(), cfg.getSpeedBlockDefaultRamp());
    }

    public void set(Block block, double speed, int ramp) {
        SpeedBlock sb = new SpeedBlock(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), speed, ramp);
        blocks.put(sb.key(), sb);
        save();
    }

    public void updateSpeed(String key, double speed) {
        SpeedBlock sb = blocks.get(key);
        if (sb == null) return;
        sb.setSpeed(Math.max(0.05, Math.min(speed, 2.0)));
        save();
    }

    public void updateRamp(String key, int ramp) {
        SpeedBlock sb = blocks.get(key);
        if (sb == null) return;
        sb.setRamp(Math.max(0, Math.min(ramp, 20)));
        save();
    }

    public SpeedBlock getOrCreate(Block block, double defaultSpeed, int defaultRamp) {
        SpeedBlock existing = getAt(block);
        if (existing != null) return existing;
        set(block, defaultSpeed, defaultRamp);
        return getAt(block);
    }

    public SpeedBlock getByKey(String key) {
        return blocks.get(key);
    }

    /** 破壞方塊時清除設定（依座標，不論方塊當前型別） */
    public boolean removeAt(Block block) {
        if (block == null) return false;
        boolean removed = blocks.remove(SpeedBlock.key(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ())) != null;
        if (removed) save();
        return removed;
    }
}
