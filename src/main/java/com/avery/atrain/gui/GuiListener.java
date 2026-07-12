package com.avery.atrain.gui;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.listener.PlayerInteractListener;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.PlatformSide;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.util.StopPlatformUtil;
import com.avery.atrain.util.TextUtil;
import com.avery.atrain.util.TrackUtil;
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
            case LINE_PLATFORMS -> handleLinePlatforms(player, slot, holder, gui);
            case STOP_LIST -> handleStopList(player, slot, holder, gui);
            case STOP_DETAIL -> handleStopDetail(player, slot, holder, gui);
            case LINE_CHOICE -> handleLineChoice(player, slot, holder);
            case LANGUAGE -> handleLanguage(player, slot, holder, gui);
            case CONFIRM -> handleConfirm(player, slot, holder, gui);
            case RECORD_SELECT -> handleRecordSelect(player, slot, holder, gui);
            case QUICK_SETUP -> handleQuickSetup(player, slot, holder, gui);
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
            case 16 -> gui.openRecordSelect(player);
            case 20 -> createStop(player);
            case 22 -> createLine(player);
            case 24 -> gui.openQuickSetup(player);
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
                double speed = Math.max(0.1, line.getMaxSpeed() - 0.05);
                line.setMaxSpeed(speed);
                plugin.getDataStore().save();
                TextUtil.send(player, lang(player, "line.speed_set", Map.of("speed", String.valueOf(speed))));
                gui.openLineDetail(player, lineId);
            }
            case 13 -> {
                double speed = Math.min(0.8, line.getMaxSpeed() + 0.05);
                line.setMaxSpeed(speed);
                plugin.getDataStore().save();
                TextUtil.send(player, lang(player, "line.speed_set", Map.of("speed", String.valueOf(speed))));
                gui.openLineDetail(player, lineId);
            }
            case 15 -> {
                if (plugin.getRouteRecorder().isRecording(player)) {
                    String current = plugin.getRouteRecorder().getRecordingLine(player);
                    if (!lineId.equals(current)) {
                        TextUtil.send(player, lang(player, "route.recording_other_line",
                                Map.of("line", current != null ? current : "?")));
                        return;
                    }
                    plugin.getRouteRecorder().stopRecording(player);
                    TextUtil.send(player, lang(player, "route.recording_stop"));
                    gui.openLineDetail(player, lineId);
                    return;
                }
                if (!player.isInsideVehicle()) {
                    TextUtil.send(player, lang(player, "route.need_cart"));
                    return;
                }
                int existing = plugin.getRouteRecorder().getRoutePointCount(lineId);
                if (existing > 0) {
                    gui.openConfirm(player, "start_record", lineId,
                            lang(player, "route.confirm_overwrite", Map.of("count", String.valueOf(existing))),
                            "line_detail", lineId);
                    return;
                }
                plugin.getRouteRecorder().startRecording(player, lineId);
                TextUtil.send(player, lang(player, "route.recording_start", Map.of("line", line.getDisplayName())));
                player.closeInventory();
            }
            case 20 -> gui.openManageStops(player, lineId);
            case 22 -> gui.openAddStopToLine(player, lineId);
            case 28 -> gui.openLinePlatforms(player, lineId);
            case 33 -> {
                gui.openConfirm(player, "clear_route", lineId,
                        lang(player, "gui.confirm.clear_route",
                                Map.of("id", lineId, "count", String.valueOf(line.getRoutePoints().size()))),
                        "line_detail", lineId);
            }
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
        if (slot == GuiSlots.CREATE) {
            createStop(player);
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
        if (slot == GuiSlots.CREATE) { createStop(player); return; }
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
        if (stopId != null) gui.openStopDetail(player, stopId);
    }

    private void handleStopDetail(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (!requireCreate(player)) return;
        String stopId = holder.get("stop_id");
        if (stopId == null) return;
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) return;

        switch (slot) {
            case 11 -> setPlatformPoint(player, stop, stopId, PlatformSide.FORWARD, gui);
            case 13 -> setPlatformPoint(player, stop, stopId, PlatformSide.RETURN, gui);
            case 15 -> teleportPlatform(player, stop, PlatformSide.FORWARD);
            case 17 -> teleportPlatform(player, stop, PlatformSide.RETURN);
            case 40 -> gui.openConfirm(player, "delete_stop", stopId,
                    lang(player, "gui.confirm.delete_stop", Map.of("id", stopId)), "stop_detail", stopId);
            default -> {
                if (isBackSlot(holder.getInventory(), slot)) gui.openStopList(player, 0);
            }
        }
    }

    private void handleRecordSelect(Player player, int slot, GuiHolder holder, GuiManager gui) {
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) { gui.openMain(player); return; }
        if (!requireCreate(player)) return;
        String lineId = holder.get("line_" + slot);
        if (lineId == null) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        if (plugin.getRouteRecorder().isRecording(player)
                && lineId.equals(plugin.getRouteRecorder().getRecordingLine(player))) {
            plugin.getRouteRecorder().stopRecording(player);
            TextUtil.send(player, lang(player, "route.recording_stop"));
            gui.openRecordSelect(player);
            return;
        }
        if (!player.isInsideVehicle()) {
            TextUtil.send(player, lang(player, "route.need_cart"));
            gui.openLineDetail(player, lineId);
            return;
        }
        int existing = plugin.getRouteRecorder().getRoutePointCount(lineId);
        if (existing > 0) {
            gui.openConfirm(player, "start_record", lineId,
                    lang(player, "route.confirm_overwrite", Map.of("count", String.valueOf(existing))),
                    "record_select", "");
            return;
        }
        plugin.getRouteRecorder().startRecording(player, lineId);
        TextUtil.send(player, lang(player, "route.recording_start", Map.of("line", line.getDisplayName())));
        player.closeInventory();
    }

    private void handleQuickSetup(Player player, int slot, GuiHolder holder, GuiManager gui) {
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) { gui.openMain(player); return; }
        switch (slot) {
            case 10 -> {
                TextUtil.send(player, lang(player, "gui.quick_setup.step1_hint"));
                player.closeInventory();
            }
            case 12 -> createStop(player);
            case 14 -> createLine(player);
            case 16 -> gui.openRecordSelect(player);
        }
    }

    private void setPlatformPoint(Player player, Stop stop, String stopId, PlatformSide side, GuiManager gui) {
        if (!TrackUtil.isBoardableBlock(plugin, player.getLocation().getBlock())) {
            TextUtil.send(player, lang(player, "stop.need_powered_rail"));
            return;
        }
        stop.setBoardPoint(side, player.getLocation());
        var conflict = StopPlatformUtil.checkConflict(stop);
        if (conflict == StopPlatformUtil.ConflictLevel.SAME_DIRECTION_BLOCK) {
            stop.clearBoardPoint(side);
            TextUtil.send(player, lang(player, "stop.platform_conflict"));
            gui.openStopDetail(player, stopId);
            return;
        }
        plugin.getDataStore().save();
        TextUtil.send(player, lang(player, side == PlatformSide.FORWARD
                ? "stop.forward_set" : "stop.return_set", Map.of("id", stopId)));
        if (conflict == StopPlatformUtil.ConflictLevel.SAME_RAIL_WARN) {
            TextUtil.send(player, lang(player, "stop.platform_same_rail_warn"));
        }
        gui.openStopDetail(player, stopId);
    }

    private void teleportPlatform(Player player, Stop stop, PlatformSide side) {
        var loc = stop.getBoardPoint(side);
        if (loc == null || loc.getWorld() == null) {
            TextUtil.send(player, lang(player, side == PlatformSide.FORWARD
                    ? "stop.no_forward_point" : "stop.no_return_point"));
            return;
        }
        player.teleport(loc);
    }

    private void handleLinePlatforms(Player player, int slot, GuiHolder holder, GuiManager gui) {
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) {
            String lineId = holder.get("line_id");
            if (lineId != null) gui.openLineDetail(player, lineId);
            return;
        }
        String stopId = holder.get("stop_" + slot);
        if (stopId != null) gui.openStopDetail(player, stopId);
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
        PlatformSide platform = PlatformSide.fromString(holder.get("platform"));
        TravelDirection direction = platform.toTravelDirection();
        player.closeInventory();
        var loc = stop.getBoardPoint(platform);
        if (loc == null) loc = player.getLocation();
        interactListener.spawnAndRide(player, stop, line, loc, direction);
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
        if (!requireCreate(player)) return;

        String targetId = holder.get("target_id");
        if ("delete_line".equals(action)) {
            plugin.getLineManager().deleteLine(targetId);
            TextUtil.send(player, lang(player, "line.deleted", Map.of("id", targetId)));
            gui.openLineList(player, 0);
        } else if ("delete_stop".equals(action)) {
            plugin.getStopManager().deleteStop(targetId);
            TextUtil.send(player, lang(player, "stop.deleted", Map.of("id", targetId)));
            gui.openStopList(player, 0);
        } else if ("start_record".equals(action)) {
            if (!player.isInsideVehicle()) {
                TextUtil.send(player, lang(player, "route.need_cart"));
                navigateBack(player, holder, gui);
                return;
            }
            plugin.getRouteRecorder().startRecording(player, targetId);
            Line line = plugin.getLineManager().getLine(targetId);
            String lineName = line != null ? line.getDisplayName() : targetId;
            TextUtil.send(player, lang(player, "route.recording_start", Map.of("line", lineName)));
            player.closeInventory();
        } else if ("clear_route".equals(action)) {
            int cleared = plugin.getRouteRecorder().clearRoute(targetId);
            TextUtil.send(player, lang(player, "route.cleared",
                    Map.of("line", targetId, "count", String.valueOf(cleared))));
            gui.openLineDetail(player, targetId);
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
        else if ("main".equals(type)) gui.openMain(player);
        else if ("record_select".equals(type)) gui.openRecordSelect(player);
        else if ("stop_detail".equals(type) && id != null) gui.openStopDetail(player, id);
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

    private void createStop(Player player) {
        if (!requireCreate(player)) return;
        var sel = plugin.getSelectionManager();
        if (!sel.hasBothCorners(player)) {
            TextUtil.send(player, lang(player, "stop.need_selection"));
            return;
        }
        String id = "stop_" + UUID.randomUUID().toString().substring(0, 8);
        if (!plugin.getStopManager().createStop(id, lang(player, "stop.default_name", Map.of("id", id)), sel.getCorner1(player), sel.getCorner2(player))) {
            TextUtil.send(player, lang(player, "stop.create_failed"));
            return;
        }
        sel.clear(player);
        TextUtil.send(player, lang(player, "stop.created", Map.of("id", id)));
        plugin.getGuiManager().openStopDetail(player, id);
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
