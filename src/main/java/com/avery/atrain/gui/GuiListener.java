package com.avery.atrain.gui;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.listener.ChatInputListener;
import com.avery.atrain.listener.PlayerInteractListener;
import com.avery.atrain.manager.ChatInputManager;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiListener implements Listener {

    private final AtrainPlugin plugin;
    private final PlayerInteractListener interactListener;

    public GuiListener(AtrainPlugin plugin, PlayerInteractListener interactListener) {
        this.plugin = plugin;
        this.interactListener = interactListener;
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
            case LINE_LIST -> handleLineList(player, slot, holder, gui);
            case LINE_DETAIL -> handleLineDetail(player, slot, holder, gui);
            case LINE_ADD_STOP -> handleAddStop(player, slot, holder, gui);
            case LINE_MANAGE_STOPS -> handleManageStops(player, slot, holder, gui);
            case STOP_LIST -> handleStopList(player, slot, holder, gui);
            case STATION_EDIT -> handleStationEdit(player, slot, holder, gui);
            case LINE_CHOICE -> handleLineChoice(player, slot, holder);
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
            case 12 -> gui.openLineList(player, 0);
            case 14 -> gui.openStopList(player, 0);
            case 20 -> createLine(player);
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

    private void handleLineList(Player player, int slot, GuiHolder holder, GuiManager gui) {
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) { gui.openMain(player); return; }
        if (slot == GuiSlots.CREATE) { createLine(player); return; }
        if (slot == GuiSlots.PREV_PAGE) {
            int page = parsePage(holder);
            if (page > 0) gui.openLineList(player, page - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            int page = parsePage(holder);
            List<Line> lines = new ArrayList<>(plugin.getLineManager().getAllLines());
            if ((page + 1) * 28 < lines.size()) gui.openLineList(player, page + 1);
            return;
        }
        String lineId = holder.get("line_" + slot);
        if (lineId != null) gui.openLineDetail(player, lineId);
    }

    private void handleLineDetail(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (!requireCreate(player)) return;
        String lineId = holder.get("line_id");
        if (lineId == null) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        switch (slot) {
            case 11 -> {
                double speed = Math.max(ChatInputListener.MIN_LINE_SPEED, line.getMaxSpeed() - 0.05);
                line.setMaxSpeed(speed);
                plugin.getDataStore().save();
                TextUtil.send(player, lang(player, "line.speed_set",
                        Map.of("speed", ChatInputListener.formatSpeed(speed))));
                gui.openLineDetail(player, lineId);
            }
            case 12 -> {
                double speed = Math.min(ChatInputListener.MAX_LINE_SPEED, line.getMaxSpeed() + 0.05);
                line.setMaxSpeed(speed);
                plugin.getDataStore().save();
                TextUtil.send(player, lang(player, "line.speed_set",
                        Map.of("speed", ChatInputListener.formatSpeed(speed))));
                gui.openLineDetail(player, lineId);
            }
            case 13 -> {
                player.closeInventory();
                plugin.getChatInputManager().setPending(player,
                        ChatInputManager.Type.LINE_SPEED, lineId);
                TextUtil.send(player, lang(player, "line.speed_prompt", Map.of(
                        "min", ChatInputListener.formatSpeed(ChatInputListener.MIN_LINE_SPEED),
                        "max", ChatInputListener.formatSpeed(ChatInputListener.MAX_LINE_SPEED))));
            }
            case 20 -> gui.openManageStops(player, lineId);
            case 22 -> gui.openAddStopToLine(player, lineId);
            case 31 -> {
                line.setCircular(!line.isCircular());
                plugin.getDataStore().save();
                TextUtil.send(player, lang(player, line.isCircular() ? "line.circular_on" : "line.circular_off"));
                gui.openLineDetail(player, lineId);
            }
            case 24 -> gui.openConfirm(player, "delete_line", lineId,
                    lang(player, "gui.confirm.delete_line", Map.of("id", lineId)), "line_detail", lineId);
            default -> {
                if (isBackSlot(holder.getInventory(), slot)) gui.openLineList(player, 0);
            }
        }
    }

    private void handleAddStop(Player player, int slot, GuiHolder holder, GuiManager gui) {
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) {
            String lineId = holder.get("line_id");
            if (lineId != null) gui.openLineDetail(player, lineId);
            return;
        }
        String lineId = holder.get("line_id");
        if (lineId == null) return;
        int page = parsePage(holder);
        if (slot == GuiSlots.PREV_PAGE && page > 0) {
            gui.openAddStopToLine(player, lineId, page - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            Line line = plugin.getLineManager().getLine(lineId);
            if (line == null) return;
            int available = 0;
            for (Stop s : plugin.getStopManager().getAllStops()) {
                if (!line.getStopIds().contains(s.getId())) available++;
            }
            if ((page + 1) * 28 < available) gui.openAddStopToLine(player, lineId, page + 1);
            return;
        }
        String stopId = holder.get("add_" + slot);
        if (stopId == null) return;
        plugin.getLineManager().addStopToLine(lineId, stopId, -1);
        TextUtil.send(player, lang(player, "line.stop_added", Map.of("stop", stopId, "line", lineId)));
        gui.openLineDetail(player, lineId);
    }

    private void handleManageStops(Player player, int slot, GuiHolder holder, GuiManager gui) {
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) {
            String lineId = holder.get("line_id");
            if (lineId != null) gui.openLineDetail(player, lineId);
            return;
        }
        String lineId = holder.get("line_id");
        if (lineId == null) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        int page = parsePage(holder);
        if (slot == GuiSlots.PREV_PAGE && page > 0) {
            gui.openManageStops(player, lineId, page - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE && (page + 1) * 28 < line.getStopIds().size()) {
            gui.openManageStops(player, lineId, page + 1);
            return;
        }
        String stopId = holder.get("stop_" + slot);
        if (stopId == null) return;
        gui.openConfirm(player, "remove_stop_from_line", stopId,
                lang(player, "gui.confirm.remove_stop_from_line", Map.of("stop", stopId, "line", lineId)),
                "manage_stops", lineId + ":" + page);
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
            case 4 -> {
                player.closeInventory();
                plugin.getChatInputManager().setPending(player, ChatInputManager.Type.STOP_NAME, stopId);
                TextUtil.send(player, lang(player, "stop.name_prompt", Map.of("name", stop.getDisplayName())));
            }
            case 11 -> {
                stop.setDwellTimeTicks(stop.getDwellTimeTicks() - 20);
                plugin.getDataStore().save();
                gui.openStationEdit(player, stopId);
            }
            case 15 -> {
                stop.setDwellTimeTicks(stop.getDwellTimeTicks() + 20);
                plugin.getDataStore().save();
                gui.openStationEdit(player, stopId);
            }
            case 24 -> {
                plugin.getStopManager().rescanDisplayBlocks(stop);
                TextUtil.send(player, lang(player, "stop.display_rescanned",
                        Map.of("count", String.valueOf(stop.getDisplayBlocks().size()))));
                gui.openStationEdit(player, stopId);
            }
            case 31 -> gui.openConfirm(player, "delete_stop", stopId,
                    lang(player, "gui.confirm.delete_stop", Map.of("id", stopId)), "station_edit", stopId);
            default -> {
                if (isBackSlot(holder.getInventory(), slot)) gui.openStopList(player, 0);
            }
        }
    }

    private void handleLineChoice(Player player, int slot, GuiHolder holder) {
        if (slot == holder.getInventory().getSize() - 1) {
            player.closeInventory();
            return;
        }
        String lineId = holder.get("choice_" + slot);
        String stopId = holder.get("stop_id");
        if (lineId == null || stopId == null) return;
        Stop stop = plugin.getStopManager().getStop(stopId);
        Line line = plugin.getLineManager().getLine(lineId);
        if (stop == null || line == null) return;

        Location railLoc = resolveRailLocation(player, holder);
        player.closeInventory();
        interactListener.spawnAndRide(player, stop, line, railLoc != null ? railLoc : player.getLocation());
    }

    private Location resolveRailLocation(Player player, GuiHolder holder) {
        String rx = holder.get("rail_x");
        String ry = holder.get("rail_y");
        String rz = holder.get("rail_z");
        if (rx == null || ry == null || rz == null) return null;
        World world = player.getWorld();
        try {
            return new Location(world,
                    Integer.parseInt(rx) + 0.5,
                    Integer.parseInt(ry) + 0.0,
                    Integer.parseInt(rz) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
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
        if (!requireCreate(player) && !"delete_stop".equals(action)) return;
        if ("delete_stop".equals(action) && !player.hasPermission("atrain.station.edit")) {
            TextUtil.send(player, lang(player, "error.no_permission"));
            return;
        }

        String targetId = holder.get("target_id");
        if ("delete_line".equals(action)) {
            plugin.getLineManager().deleteLine(targetId);
            TextUtil.send(player, lang(player, "line.deleted", Map.of("id", targetId)));
            gui.openLineList(player, 0);
        } else if ("delete_stop".equals(action)) {
            plugin.getStopManager().deleteStop(targetId);
            TextUtil.send(player, lang(player, "stop.deleted", Map.of("id", targetId)));
            gui.openStopList(player, 0);
        } else if ("remove_stop_from_line".equals(action)) {
            String returnId = holder.get("return_id");
            String lineId = returnId != null && returnId.contains(":") ? returnId.split(":")[0] : returnId;
            int page = 0;
            if (returnId != null && returnId.contains(":")) {
                try { page = Integer.parseInt(returnId.split(":")[1]); } catch (NumberFormatException ignored) {}
            }
            if (lineId != null) {
                plugin.getLineManager().removeStopFromLine(lineId, targetId);
                TextUtil.send(player, lang(player, "line.stop_removed", Map.of("stop", targetId, "line", lineId)));
                gui.openManageStops(player, lineId, page);
            }
        }
    }

    private void navigateBack(Player player, GuiHolder holder, GuiManager gui) {
        String type = holder.get("return_type");
        String id = holder.get("return_id");
        if ("line_detail".equals(type) && id != null) gui.openLineDetail(player, id);
        else if ("manage_stops".equals(type) && id != null) {
            String lineId = id.contains(":") ? id.split(":")[0] : id;
            int page = 0;
            if (id.contains(":")) {
                try { page = Integer.parseInt(id.split(":")[1]); } catch (NumberFormatException ignored) {}
            }
            gui.openManageStops(player, lineId, page);
        }
        else if ("station_edit".equals(type) && id != null) gui.openStationEdit(player, id);
        else if ("main".equals(type)) gui.openMain(player);
        else gui.openMain(player);
    }

    private void createLine(Player player) {
        if (!requireCreate(player)) return;
        String id = "line_" + UUID.randomUUID().toString().substring(0, 8);
        if (!plugin.getLineManager().createLine(id, lang(player, "line.default_name", Map.of("id", id)))) {
            TextUtil.send(player, lang(player, "line.create_failed"));
            return;
        }
        TextUtil.send(player, lang(player, "line.created", Map.of("id", id)));
        plugin.getGuiManager().openLineDetail(player, id);
    }

    private boolean requireCreate(Player player) {
        if (player.hasPermission("atrain.create")) return true;
        TextUtil.send(player, lang(player, "error.no_permission"));
        return false;
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
