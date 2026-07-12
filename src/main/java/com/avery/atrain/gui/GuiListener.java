package com.avery.atrain.gui;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.manager.ChatInputManager;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GuiListener implements Listener {

    private final AtrainPlugin plugin;

    public GuiListener(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        var gui = plugin.getGuiManager();

        switch (holder.getType()) {
            case MAIN -> handleMain(player, slot, gui);
            case TUTORIAL -> handleTutorial(player, slot, holder, gui);
            case TUTORIAL_CATEGORY -> {
                if (isBackSlot(event.getInventory(), slot)) gui.openTutorial(player);
            }
            case STOP_LIST -> handleStopList(player, slot, holder, gui);
            case STATION_EDIT -> handleStationEdit(player, slot, holder, gui);
            case LANGUAGE -> handleLanguage(player, slot, holder, gui);
            case CONFIRM -> handleConfirm(player, slot, holder, gui);
        }
    }

    private boolean isBackSlot(Inventory inv, int slot) {
        return slot == GuiSlots.back(inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMain(Player player, int slot, GuiManager gui) {
        switch (slot) {
            case 10 -> gui.openTutorial(player);
            case 13 -> gui.openStopList(player, 0);
            case 30 -> gui.openLanguage(player);
            case 32 -> {
                if (!player.hasPermission("atrain.admin")) {
                    TextUtil.send(player, lang(player, "error.no_permission"));
                    return;
                }
                gui.openConfirm(player, "reload", "",
                        lang(player, "gui.confirm.reload"),
                        "main", "");
            }
            case 34 -> {
                if (!player.hasPermission("atrain.admin")) {
                    TextUtil.send(player, lang(player, "error.no_permission"));
                    return;
                }
                boolean next = !plugin.getConfigManager().isHangRailEnabled();
                plugin.getConfigManager().setHangRailEnabled(next);
                TextUtil.send(player, lang(player, next ? "hangrail.enabled" : "hangrail.disabled"));
                gui.openMain(player);
            }
            case 44 -> player.closeInventory();
        }
    }

    private void handleTutorial(Player player, int slot, GuiHolder holder, GuiManager gui) {
        Inventory inv = holder.getInventory();
        if (inv != null && isBackSlot(inv, slot)) {
            gui.openMain(player);
            return;
        }
        String cat = holder.get("cat_" + slot);
        if (cat != null) gui.openTutorialCategory(player, cat);
    }

    private void handleStopList(Player player, int slot, GuiHolder holder, GuiManager gui) {
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) { gui.openMain(player); return; }
        if (slot == GuiSlots.PREV_PAGE) {
            int page = parsePage(holder);
            if (page > 0) gui.openStopList(player, page - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            int page = parsePage(holder);
            List<Stop> stops = new ArrayList<>(plugin.getStopManager().getAllStops());
            if ((page + 1) * 28 < stops.size()) gui.openStopList(player, page + 1);
            return;
        }
        String stopId = holder.get("stop_" + slot);
        if (stopId != null) {
            if (!player.hasPermission("atrain.station.edit")) {
                TextUtil.send(player, lang(player, "error.no_permission"));
                return;
            }
            gui.openStationEdit(player, stopId);
        }
    }

    private void handleStationEdit(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (!player.hasPermission("atrain.station.edit")) {
            TextUtil.send(player, lang(player, "error.no_permission"));
            return;
        }
        String stopId = holder.get("stop_id");
        if (stopId == null) return;
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) return;

        switch (slot) {
            case 4 -> prompt(player, ChatInputManager.Type.STOP_NAME, stopId, "stop.name_prompt",
                    Map.of("name", stop.getDisplayName()));
            case 10 -> prompt(player, ChatInputManager.Type.STOP_INFO_PREV, stopId, "stop.info_prev_prompt",
                    Map.of("name", stop.getInfoPrev()));
            case 12 -> prompt(player, ChatInputManager.Type.STOP_NAME, stopId, "stop.name_prompt",
                    Map.of("name", stop.getDisplayName()));
            case 14 -> prompt(player, ChatInputManager.Type.STOP_INFO_NEXT, stopId, "stop.info_next_prompt",
                    Map.of("name", stop.getInfoNext()));
            case 16 -> prompt(player, ChatInputManager.Type.STOP_KEY_STATION, stopId, "stop.key_station_prompt",
                    Map.of("name", stop.getKeyStation()));
            case 18 -> prompt(player, ChatInputManager.Type.STOP_KEY_DIRECTION, stopId, "stop.key_direction_prompt",
                    Map.of("name", stop.getKeyDirection()));
            case 20 -> {
                if (!plugin.getConfigManager().isAutoStopEnabled() || !player.hasPermission("atrain.admin")) break;
                int next = Math.max(0, stop.getDwellTimeTicks() - 20);
                stop.setDwellTimeTicks(next);
                plugin.getDataStore().save();
                gui.openStationEdit(player, stopId);
            }
            case 24 -> {
                if (!plugin.getConfigManager().isAutoStopEnabled() || !player.hasPermission("atrain.admin")) break;
                stop.setDwellTimeTicks(stop.getDwellTimeTicks() + 20);
                plugin.getDataStore().save();
                gui.openStationEdit(player, stopId);
            }
            case 33 -> {
                plugin.getStopManager().rescanDisplayBlocks(stop);
                TextUtil.send(player, lang(player, "stop.display_rescanned",
                        Map.of("count", String.valueOf(stop.getDisplayBlocks().size()))));
                gui.openStationEdit(player, stopId);
            }
            case 38 -> {
                if (!player.hasPermission("atrain.admin")) {
                    TextUtil.send(player, lang(player, "error.no_permission"));
                    return;
                }
                prompt(player, ChatInputManager.Type.STOP_ADMIN_INFO, stopId, "stop.admin_info_prompt",
                        Map.of("info", stop.getAdminInfo().isBlank() ? "-" : stop.getAdminInfo()));
            }
            case 40 -> {
                if (!player.hasPermission("atrain.admin")) {
                    TextUtil.send(player, lang(player, "error.no_permission"));
                    return;
                }
                Stop s = plugin.getStopManager().getStop(stopId);
                String label = s != null ? s.getDisplayName() : stopId;
                gui.openConfirm(player, "delete_stop", stopId,
                        lang(player, "gui.confirm.delete_stop", Map.of("name", label)),
                        "station_edit", stopId);
            }
            default -> {
                if (isBackSlot(holder.getInventory(), slot)) gui.openStopList(player, 0);
            }
        }
    }

    private void prompt(Player player, ChatInputManager.Type type, String stopId, String key, Map<String, String> ph) {
        player.closeInventory();
        plugin.getChatInputManager().setPending(player, type, stopId);
        TextUtil.send(player, lang(player, key, ph));
        TextUtil.send(player, lang(player, "input.chat_hint"));
    }

    private void handleLanguage(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (isBackSlot(holder.getInventory(), slot)) { gui.openMain(player); return; }
        String code = holder.get("lang_" + slot);
        if (code != null && plugin.getLanguageManager().setPlayerLanguage(player, code)) {
            TextUtil.send(player, lang(player, "lang.changed", Map.of("lang", code)));
            gui.openLanguage(player);
        }
    }

    private void handleConfirm(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (slot == 15) {
            navigateBack(player, holder, gui);
            return;
        }
        if (slot != 11) return;
        String action = holder.get("action");
        if ("reload".equals(action)) {
            if (!player.hasPermission("atrain.admin")) {
                TextUtil.send(player, lang(player, "error.no_permission"));
                return;
            }
            plugin.reloadAll();
            gui.openMain(player);
            return;
        }
        if ("delete_stop".equals(action)) {
            if (!player.hasPermission("atrain.admin")) {
                TextUtil.send(player, lang(player, "error.no_permission"));
                return;
            }
            String targetId = holder.get("target_id");
            plugin.getStopManager().deleteStop(targetId);
            TextUtil.send(player, lang(player, "stop.deleted", Map.of("id", targetId)));
            gui.openStopList(player, 0);
        }
    }

    private void navigateBack(Player player, GuiHolder holder, GuiManager gui) {
        String type = holder.get("return_type");
        String id = holder.get("return_id");
        if ("station_edit".equals(type) && id != null) gui.openStationEdit(player, id);
        else if ("main".equals(type)) gui.openMain(player);
        else gui.openMain(player);
    }

    private int parsePage(GuiHolder holder) {
        return Integer.parseInt(holder.get("page") != null ? holder.get("page") : "0");
    }

    private String lang(Player player, String key) {
        return plugin.getLanguageManager().get(player, key);
    }

    private String lang(Player player, String key, Map<String, String> ph) {
        return plugin.getLanguageManager().get(player, key, ph);
    }
}
