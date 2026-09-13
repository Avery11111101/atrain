package com.avery.atrain.gui;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.manager.ChatInputManager;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.model.TravelDirection;
import com.avery.atrain.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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
            case STOP_LIST -> handleStopList(player, slot, holder, gui, event);
            case STATION_EDIT -> handleStationEdit(player, slot, holder, gui, event);
            case LINE_LIST -> handleLineList(player, slot, holder, gui, event);
            case LINE_DETAIL -> handleLineDetail(player, slot, holder, gui, event);
            case LINE_ADD_STOP -> handleLineAddStop(player, slot, holder, gui);
            case RECORD_SELECT -> handleRecordSelect(player, slot, holder, gui);
            case RECORD_SEGMENT -> handleRecordSegment(player, slot, holder, gui, event);
            case LANGUAGE -> handleLanguage(player, slot, holder, gui);
            case SPEED_BLOCK_EDIT -> handleSpeedBlockEdit(player, slot, holder, gui);
            case CONFIRM -> handleConfirm(player, slot, holder, gui, event);
            case KEY_STATION_SELECT -> handleKeyStationSelect(player, slot, holder, gui);
            case LINE_REORDER -> handleLineReorder(player, slot, holder, gui, event);
            case RECORD_MODE_SELECT -> handleRecordModeSelect(player, slot, holder, gui);
            case GUIDE_MAIN -> handleGuideMain(player, slot, holder, gui);
            case GUIDE_LINE_LIST -> handleGuideLineList(player, slot, holder, gui, event);
            case GUIDE_LINE_DETAIL -> handleGuideLineDetail(player, slot, holder, gui, event);
            case GUIDE_TRANSFER_LIST -> handleGuideTransferList(player, slot, holder, gui, event);
            case GUIDE_PLANNER -> handleGuidePlanner(player, slot, holder, gui, event);
            case GUIDE_SELECT_STOP -> handleGuideSelectStop(player, slot, holder, gui, event);
            case GUIDE_PLANNER_RESULT -> handleGuidePlannerResult(player, slot, holder, gui);
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
            case 16 -> gui.openLineList(player, 0);
            case 20 -> gui.openGuideMain(player);
            case 22 -> {
                if (!player.hasPermission("atrain.station.edit")) {
                    TextUtil.send(player, lang(player, "error.no_permission"));
                    return;
                }
                gui.openRecordSelect(player, 0);
            }
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

    private void handleStopList(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
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
            Stop stop = plugin.getStopManager().getStop(stopId);
            if (stop == null) return;
            if (event.getClick() == ClickType.LEFT) {
                teleportToStop(player, stop);
                return;
            }
            if (event.getClick() == ClickType.RIGHT) {
                gui.openStationEdit(player, stopId);
            }
        }
    }

    private void handleStationEdit(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
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
            case 10 -> {
                if (plugin.getStopManager().hasDisplayPrev(stop)) openLineManageForStop(player, stop, gui);
            }
            case 14 -> {
                if (plugin.getStopManager().hasDisplayNext(stop)) openLineManageForStop(player, stop, gui);
            }
            case 15 -> openLineManageForStop(player, stop, gui);
            case 12 -> prompt(player, ChatInputManager.Type.STOP_NAME, stopId, "stop.name_prompt",
                    Map.of("name", stop.getDisplayName()));
            case 16 -> gui.openKeyStationSelect(player, stopId, 0);
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
            case 30 -> {
                if (!plugin.getConfigManager().isCinematicTransitEnabled()) break;
                String targetLineId = holder.get("display_line");
                Line line = targetLineId != null ? plugin.getLineManager().getLine(targetLineId) : null;
                if (line == null) {
                    line = plugin.getStopManager().resolveDisplayLine(stop, player.getLocation());
                }
                if (line == null) break;
                int cur = stop.getTravelSecondsToNext(line.getId(), plugin.getConfigManager().getDefaultSegmentSeconds());
                stop.setTravelSecondsToNext(line.getId(), cur - 5);
                plugin.getDataStore().save();
                gui.openStationEdit(player, stopId);
            }
            case 32 -> {
                if (!plugin.getConfigManager().isCinematicTransitEnabled()) break;
                String targetLineId = holder.get("display_line");
                Line line = targetLineId != null ? plugin.getLineManager().getLine(targetLineId) : null;
                if (line == null) {
                    line = plugin.getStopManager().resolveDisplayLine(stop, player.getLocation());
                }
                if (line == null) break;
                int cur = stop.getTravelSecondsToNext(line.getId(), plugin.getConfigManager().getDefaultSegmentSeconds());
                stop.setTravelSecondsToNext(line.getId(), cur + 5);
                plugin.getDataStore().save();
                gui.openStationEdit(player, stopId);
            }
            case 28 -> {
                player.closeInventory();
                plugin.getBindPlatformManager().startBind(player, stopId);
                TextUtil.send(player, lang(player, "stop.return_bind_hint"));
            }
            case 33 -> {
                String tpTarget = holder.get("tp_target");
                if (tpTarget != null) {
                    org.bukkit.Location loc = tpTarget.equals("forward") 
                            ? stop.getPrimaryRailLocation() 
                            : stop.getRailLocation(com.avery.atrain.model.TravelDirection.REVERSE);
                    if (loc != null) {
                        player.closeInventory();
                        player.teleport(loc.clone().add(0.5, 1, 0.5));
                        TextUtil.send(player, "§a已傳送至" + (tpTarget.equals("forward") ? "去程" : "回程") + "月台。");
                    }
                }
            }
            case 34 -> gui.openLineList(player, 0, null, stopId);
            case 38 -> {
                if (!player.hasPermission("atrain.admin")) {
                    TextUtil.send(player, lang(player, "error.no_permission"));
                    return;
                }
                if (isPlainRightClick(event) || isShiftRightClick(event)) {
                    stop.setAdminInfo("");
                    plugin.getDataStore().save();
                    gui.openStationEdit(player, stopId);
                } else {
                    prompt(player, ChatInputManager.Type.STOP_ADMIN_INFO, stopId, "stop.admin_info_prompt",
                            Map.of("info", stop.getAdminInfo().isBlank() ? "-" : stop.getAdminInfo()));
                }
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

    private void openLineManageForStop(Player player, Stop stop, GuiManager gui) {
        var lines = plugin.getLineManager().getLinesAtStop(stop.getId());
        if (lines.size() == 1) {
            gui.openLineDetail(player, lines.get(0).getId(), 0);
        } else {
            gui.openLineList(player, 0, stop.getId());
        }
    }

    private void handleLineList(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        if (!player.hasPermission("atrain.station.edit")) {
            TextUtil.send(player, lang(player, "error.no_permission"));
            return;
        }
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) {
            String pickReturn = holder.get("pick_return_line");
            if (pickReturn != null) {
                gui.openStationEdit(player, pickReturn);
                return;
            }
            String returnStop = holder.get("return_stop_id");
            if (returnStop != null) gui.openStationEdit(player, returnStop);
            else gui.openMain(player);
            return;
        }
        if (slot == GuiSlots.CREATE) {
            player.closeInventory();
            plugin.getChatInputManager().setPending(player, ChatInputManager.Type.LINE_CREATE, null);
            TextUtil.send(player, lang(player, "line.name_prompt"));
            TextUtil.send(player, lang(player, "input.chat_hint"));
            return;
        }
        if (slot == GuiSlots.PREV_PAGE) {
            int page = parsePage(holder);
            if (page > 0) gui.openLineList(player, page - 1, holder.get("return_stop_id"), holder.get("pick_return_line"));
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            int page = parsePage(holder);
            List<Line> lines = new ArrayList<>(plugin.getLineManager().getAllLines());
            if ((page + 1) * 28 < lines.size()) {
                gui.openLineList(player, page + 1, holder.get("return_stop_id"), holder.get("pick_return_line"));
            }
            return;
        }
        String lineId = holder.get("line_" + slot);
        if (lineId != null) {
            String pickReturn = holder.get("pick_return_line");
            if (pickReturn != null) {
                Stop stop = plugin.getStopManager().getStop(pickReturn);
                Line line = plugin.getLineManager().getLine(lineId);
                if (stop != null && line != null) {
                    stop.setReturnLineId(lineId);
                    plugin.getDataStore().save();
                    TextUtil.send(player, lang(player, "stop.return_line_set",
                            Map.of("line", line.getDisplayName())));
                }
                gui.openStationEdit(player, pickReturn);
                return;
            }
            String returnStop = holder.get("return_stop_id");
            if (returnStop != null) {
                Stop stop = plugin.getStopManager().getStop(returnStop);
                if (stop != null) {
                    stop.setDisplayLineId(lineId);
                    plugin.getDataStore().save();
                }
            }
            gui.openLineDetail(player, lineId, 0);
        }
    }

    private void handleLineDetail(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        if (!player.hasPermission("atrain.station.edit")) {
            TextUtil.send(player, lang(player, "error.no_permission"));
            return;
        }
        String lineId = holder.get("line_id");
        if (lineId == null) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;
        int stopPage = parseInt(holder.get("stop_page"), 0);

        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) {
            gui.openLineList(player, 0);
            return;
        }
        if (slot == GuiSlots.CREATE) {
            if (blockLineEditDuringRecording(player, lineId)) return;
            gui.openLineAddStop(player, lineId, 0);
            return;
        }
        if (slot == GuiSlots.PREV_PAGE) {
            if (stopPage > 0) gui.openLineDetail(player, lineId, stopPage - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            if ((stopPage + 1) * 7 < line.getStopIds().size()) {
                gui.openLineDetail(player, lineId, stopPage + 1);
            }
            return;
        }
        switch (slot) {
            case 2 -> {
                player.closeInventory();
                plugin.getChatInputManager().setPending(player, ChatInputManager.Type.LINE_COLOR, lineId);
                TextUtil.send(player, lang(player, "line.color_prompt", Map.of(
                        "name", line.getDisplayName(),
                        "color", line.getFormattedColor() + (line.getColor() != null ? line.getColor() : "§a"))));
                TextUtil.send(player, lang(player, "input.chat_hint"));
            }
            case 4 -> {
                player.closeInventory();
                plugin.getChatInputManager().setPending(player, ChatInputManager.Type.LINE_RENAME, lineId);
                TextUtil.send(player, lang(player, "line.rename_prompt", Map.of("name", line.getDisplayName())));
                TextUtil.send(player, lang(player, "input.chat_hint"));
            }
            case 6 -> {
                plugin.getLineManager().toggleCircular(lineId);
                TextUtil.send(player, lang(player, line.isCircular() ? "line.circular_on" : "line.circular_off"));
                gui.openLineDetail(player, lineId, stopPage);
            }
            case 8 -> {
                if (!player.hasPermission("atrain.admin")) {
                    TextUtil.send(player, lang(player, "error.no_permission"));
                    return;
                }
                gui.openConfirm(player, "delete_line", lineId,
                        lang(player, "gui.confirm.delete_line", Map.of("id", line.getDisplayName())),
                        "line_detail", lineId);
            }
            case 40 -> gui.openRecordSegmentSelect(player, lineId);
            case 50 -> gui.openLineReorder(player, lineId, 0);
            default -> {
                String stopId = holder.get("stop_" + slot);
                if (stopId == null) return;
                Stop stop = plugin.getStopManager().getStop(stopId);
                if (stop == null) return;
                if (isPlainLeftClick(event) || isShiftLeftClick(event)) {
                    teleportToStop(player, stop);
                    return;
                }
                if (isPlainRightClick(event) || isShiftRightClick(event)) {
                    gui.openStationEdit(player, stopId);
                    return;
                }
                if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                    if (blockLineEditDuringRecording(player, lineId)) return;
                    String stopName = stop.getDisplayName();
                    gui.openConfirm(player, "remove_stop_from_line", stopId + "|" + lineId,
                            lang(player, "gui.confirm.remove_stop_from_line",
                                    Map.of("stop", stopName, "line", line.getDisplayName())),
                            "line_detail", lineId);
                }
            }
        }
    }

    private void handleRecordSelect(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (!player.hasPermission("atrain.station.edit")) {
            TextUtil.send(player, lang(player, "error.no_permission"));
            return;
        }
        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) {
            gui.openMain(player);
            return;
        }
        if (slot == GuiSlots.PREV_PAGE) {
            int page = parsePage(holder);
            if (page > 0) gui.openRecordSelect(player, page - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            int page = parsePage(holder);
            List<Line> lines = new ArrayList<>(plugin.getLineManager().getAllLines());
            if ((page + 1) * 28 < lines.size()) gui.openRecordSelect(player, page + 1);
            return;
        }
        String lineId = holder.get("line_" + slot);
        if (lineId != null) {
            var rm = plugin.getRouteRecordingManager();
            if (rm != null && rm.isRecording(player) && lineId.equals(rm.getRecordingLineId(player))) {
                player.closeInventory();
                rm.stop(player);
                gui.openRecordSelect(player, parsePage(holder));
                return;
            }
            gui.openRecordSegmentSelect(player, lineId);
        }
    }

    private void handleRecordSegment(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        if (!player.hasPermission("atrain.station.edit")) {
            TextUtil.send(player, lang(player, "error.no_permission"));
            return;
        }
        String lineId = holder.get("line_id");
        if (lineId == null) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        TravelDirection direction = TravelDirection.fromString(holder.get("direction"));
        var rm = plugin.getRouteRecordingManager();
        boolean recordingLine = rm != null && rm.isRecording(player) && lineId.equals(rm.getRecordingLineId(player));
        TravelDirection activeDir = recordingLine ? rm.getRecordingDirection(player) : direction;
        int segPage = parseInt(holder.get("seg_page"), 0);

        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) {
            gui.openRecordSelect(player, 0);
            return;
        }

        if (slot == 2) {
            gui.openRecordSegmentSelect(player, lineId, TravelDirection.FORWARD, segPage);
            return;
        }
        if (slot == 6) {
            gui.openRecordSegmentSelect(player, lineId, TravelDirection.REVERSE, segPage);
            return;
        }

        if (slot == 38 && recordingLine) {
            player.closeInventory();
            rm.stop(player);
            gui.openRecordSegmentSelect(player, lineId, activeDir, segPage);
            return;
        }
        if (slot == 42 && recordingLine) {
            player.closeInventory();
            rm.cancel(player);
            gui.openRecordSegmentSelect(player, lineId, activeDir, segPage);
            return;
        }

        if (slot == GuiSlots.PREV_PAGE) {
            if (segPage > 0) gui.openRecordSegmentSelect(player, lineId, direction, segPage - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            if ((segPage + 1) * 12 < line.getSegmentCount()) {
                gui.openRecordSegmentSelect(player, lineId, direction, segPage + 1);
            }
            return;
        }

        String segKey = holder.get("seg_" + slot);
        if (segKey == null) return;
        int segmentIndex = parseInt(segKey, -1);
        if (segmentIndex < 0) return;

        if (recordingLine) {
            TextUtil.send(player, lang(player, "route.recording_must_stop_first"));
            return;
        }
        if (rm != null && rm.isRecording(player)) {
            TextUtil.send(player, lang(player, "route.recording_other_line",
                    Map.of("line", formatRecordingLineName(rm.getRecordingLineId(player)))));
            return;
        }

        if (plugin.getRouteManager().hasSegment(line, segmentIndex, direction)) {
            if (event.getClick() == ClickType.MIDDLE || event.getClick() == ClickType.SHIFT_LEFT) {
                String fId = line.getSegmentFromStopId(segmentIndex, direction);
                String tId = line.getSegmentToStopId(segmentIndex, direction);
                if (fId != null && tId != null) {
                    Stop from = plugin.getStopManager().getStop(fId);
                    Stop to = plugin.getStopManager().getStop(tId);
                    if (from != null && to != null) {
                        plugin.getRouteManager().deleteRoute(from, to);
                        TextUtil.send(player, "§a已刪除路線：§e" + from.getDisplayName() + " §7→ §e" + to.getDisplayName());
                        gui.openRecordSegmentSelect(player, lineId, direction, segPage);
                    }
                }
                return;
            }

            int pts = 0;
            String fId = line.getSegmentFromStopId(segmentIndex, direction);
            String tId = line.getSegmentToStopId(segmentIndex, direction);
            if (fId != null && tId != null) {
                var r = plugin.getRouteManager().getRoute(fId, tId);
                if (r != null) pts = r.size();
            }
            gui.openConfirm(player, "start_record_segment",
                    lineId + "|" + segmentIndex + "|" + direction.name(),
                    lang(player, "route.confirm_overwrite_segment",
                            Map.of("from", segmentLabel(line, segmentIndex, direction, "from"),
                                    "to", segmentLabel(line, segmentIndex, direction, "to"),
                                    "count", String.valueOf(pts))),
                    "record_segment", lineId + "|" + direction.name());
            return;
        }

        gui.openRecordModeSelect(player, lineId, segmentIndex, direction);
    }

    private boolean blockLineEditDuringRecording(Player player, String lineId) {
        var rm = plugin.getRouteRecordingManager();
        if (rm != null && rm.isRecording(player) && lineId.equals(rm.getRecordingLineId(player))) {
            TextUtil.send(player, lang(player, "route.recording_line_locked"));
            return true;
        }
        return false;
    }

    private String formatRecordingLineName(String lineId) {
        if (lineId == null) return "?";
        Line line = plugin.getLineManager().getLine(lineId);
        return line != null ? line.getDisplayName() : lineId;
    }

    private String segmentLabel(Line line, int segmentIndex, TravelDirection direction, String part) {
        String id = "from".equals(part)
                ? line.getSegmentFromStopId(segmentIndex, direction)
                : line.getSegmentToStopId(segmentIndex, direction);
        if (id == null) return "?";
        Stop stop = plugin.getStopManager().getStop(id);
        return stop != null ? stop.getDisplayName() : id;
    }

    private void handleLineAddStop(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (!player.hasPermission("atrain.station.edit")) {
            TextUtil.send(player, lang(player, "error.no_permission"));
            return;
        }
        String lineId = holder.get("line_id");
        if (lineId == null) return;
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        Inventory inv = holder.getInventory();
        if (isBackSlot(inv, slot)) {
            gui.openLineDetail(player, lineId, 0);
            return;
        }
        if (slot == GuiSlots.PREV_PAGE) {
            int page = parsePage(holder);
            if (page > 0) gui.openLineAddStop(player, lineId, page - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            int page = parsePage(holder);
            List<Stop> stops = new ArrayList<>(plugin.getStopManager().getAllStops());
            if ((page + 1) * 28 < stops.size()) gui.openLineAddStop(player, lineId, page + 1);
            return;
        }
        String stopId = holder.get("stop_" + slot);
        if (stopId != null) {
            if (blockLineEditDuringRecording(player, lineId)) return;
            plugin.getLineManager().addStopToLine(lineId, stopId, -1);
            Stop stop = plugin.getStopManager().getStop(stopId);
            String stopName = stop != null ? stop.getDisplayName() : stopId;
            TextUtil.send(player, lang(player, "line.stop_added", Map.of("stop", stopName, "line", line.getDisplayName())));
            gui.openLineDetail(player, lineId, 0);
        }
    }

    private void teleportToStop(Player player, Stop stop) {
        if (plugin.getStopManager().teleportPlayerToStop(player, stop)) {
            player.closeInventory();
            TextUtil.send(player, lang(player, "stop.teleported",
                    Map.of("name", stop.getDisplayName())));
        } else {
            TextUtil.send(player, lang(player, "stop.teleport_failed",
                    Map.of("name", stop.getDisplayName())));
        }
    }

    private void prompt(Player player, ChatInputManager.Type type, String stopId, String key, Map<String, String> ph) {
        player.closeInventory();
        plugin.getChatInputManager().setPending(player, type, stopId);
        TextUtil.send(player, lang(player, key, ph));
        TextUtil.send(player, lang(player, "input.chat_hint"));
    }

    private void handleSpeedBlockEdit(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (!player.hasPermission("atrain.speed.edit")) {
            TextUtil.send(player, lang(player, "error.no_permission"));
            return;
        }
        String blockKey = holder.get("block_key");
        if (blockKey == null) return;
        var sbMgr = plugin.getSpeedBlockManager();
        var sb = sbMgr.getByKey(blockKey);
        if (sb == null) return;

        switch (slot) {
            case 11 -> {
                sbMgr.updateSpeed(blockKey, sb.getSpeed() - 0.05);
                gui.openSpeedBlockEdit(player, blockKey);
            }
            case 13 -> {
                player.closeInventory();
                plugin.getChatInputManager().setPending(player, ChatInputManager.Type.SPEED_BLOCK_SPEED, blockKey);
                TextUtil.send(player, lang(player, "speed_block.speed_prompt",
                        Map.of("speed", String.format("%.2f", sb.getSpeed()))));
                TextUtil.send(player, lang(player, "input.chat_hint"));
            }
            case 15 -> {
                sbMgr.updateSpeed(blockKey, sb.getSpeed() + 0.05);
                gui.openSpeedBlockEdit(player, blockKey);
            }
            case 20 -> {
                sbMgr.updateSpeed(blockKey, 0.2);
                gui.openSpeedBlockEdit(player, blockKey);
            }
            case 22 -> {
                sbMgr.updateSpeed(blockKey, plugin.getConfigManager().getCartSpeed());
                gui.openSpeedBlockEdit(player, blockKey);
            }
            case 24 -> {
                sbMgr.updateSpeed(blockKey, plugin.getConfigManager().getBoostSpeed());
                gui.openSpeedBlockEdit(player, blockKey);
            }
            case 30 -> {
                sbMgr.updateRamp(blockKey, sb.getRamp() - 1);
                gui.openSpeedBlockEdit(player, blockKey);
            }
            case 32 -> {
                sbMgr.updateRamp(blockKey, sb.getRamp() + 1);
                gui.openSpeedBlockEdit(player, blockKey);
            }
            case 44 -> player.closeInventory();
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

    private void handleConfirm(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
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
            return;
        }
        if ("delete_line".equals(action)) {
            if (!player.hasPermission("atrain.admin")) {
                TextUtil.send(player, lang(player, "error.no_permission"));
                return;
            }
            String targetId = holder.get("target_id");
            plugin.getLineManager().deleteLine(targetId);
            TextUtil.send(player, lang(player, "line.deleted", Map.of("id", targetId)));
            gui.openLineList(player, 0);
            return;
        }
        if ("remove_stop_from_line".equals(action)) {
            if (!player.hasPermission("atrain.station.edit")) {
                TextUtil.send(player, lang(player, "error.no_permission"));
                return;
            }
            String[] parts = holder.get("target_id").split("\\|", 2);
            if (parts.length == 2 && blockLineEditDuringRecording(player, parts[1])) return;
            if (parts.length == 2) {
                plugin.getLineManager().removeStopFromLine(parts[1], parts[0]);
                Stop stop = plugin.getStopManager().getStop(parts[0]);
                Line line = plugin.getLineManager().getLine(parts[1]);
                TextUtil.send(player, lang(player, "line.stop_removed", Map.of(
                        "stop", stop != null ? stop.getDisplayName() : parts[0],
                        "line", line != null ? line.getDisplayName() : parts[1])));
            }
            String returnId = holder.get("return_id");
            if (returnId != null) gui.openLineDetail(player, returnId, 0);
            else gui.openLineList(player, 0);
            return;
        }
        if ("start_record_segment".equals(action)) {
            if (!player.hasPermission("atrain.station.edit")) {
                TextUtil.send(player, lang(player, "error.no_permission"));
                return;
            }
            String[] parts = holder.get("target_id").split("\\|", 3);
            if (parts.length < 2) return;
            String lineId = parts[0];
            int segmentIndex = parseInt(parts[1], 0);
            TravelDirection direction = parts.length >= 3
                    ? TravelDirection.fromString(parts[2]) : TravelDirection.FORWARD;
            gui.openRecordModeSelect(player, lineId, segmentIndex, direction);
        }
    }

    private void navigateBack(Player player, GuiHolder holder, GuiManager gui) {
        String type = holder.get("return_type");
        String id = holder.get("return_id");
        if ("station_edit".equals(type) && id != null) gui.openStationEdit(player, id);
        else if ("line_detail".equals(type) && id != null) gui.openLineDetail(player, id, 0);
        else if ("record_select".equals(type)) gui.openRecordSelect(player, parseInt(id, 0));
        else if ("record_segment".equals(type) && id != null) {
            String[] parts = id.split("\\|", 2);
            TravelDirection dir = parts.length >= 2
                    ? TravelDirection.fromString(parts[1]) : TravelDirection.FORWARD;
            gui.openRecordSegmentSelect(player, parts[0], dir);
        }
        else if ("main".equals(type)) gui.openMain(player);
        else gui.openMain(player);
    }

    private boolean isPlainLeftClick(InventoryClickEvent event) {
        return event.getClick().isLeftClick() && !event.isShiftClick();
    }

    private boolean isPlainRightClick(InventoryClickEvent event) {
        return event.getClick().isRightClick() && !event.isShiftClick();
    }

    private boolean isShiftLeftClick(InventoryClickEvent event) {
        return event.isShiftClick() && event.getClick().isLeftClick();
    }

    private boolean isShiftRightClick(InventoryClickEvent event) {
        return event.isShiftClick() && event.getClick().isRightClick();
    }

    private void handleKeyStationSelect(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (!player.hasPermission("atrain.station.edit")) return;
        Inventory inv = holder.getInventory();
        String targetStopId = holder.get("stop_id");
        if (targetStopId == null) return;

        if (isBackSlot(inv, slot)) {
            gui.openStationEdit(player, targetStopId);
            return;
        }
        if (slot == GuiSlots.PREV_PAGE) {
            int page = parsePage(holder);
            if (page > 0) gui.openKeyStationSelect(player, targetStopId, page - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            int page = parsePage(holder);
            gui.openKeyStationSelect(player, targetStopId, page + 1);
            return;
        }

        String clickedStopId = holder.get("stop_" + slot);
        if (clickedStopId != null) {
            Stop targetStop = plugin.getStopManager().getStop(targetStopId);
            Stop clickedStop = plugin.getStopManager().getStop(clickedStopId);
            if (targetStop != null && clickedStop != null) {
                if (targetStop.hasKeyStation(clickedStopId)) {
                    targetStop.removeKeyStation(clickedStopId);
                    clickedStop.removeKeyStation(targetStopId);
                } else {
                    targetStop.addKeyStation(clickedStopId);
                    clickedStop.addKeyStation(targetStopId);
                }
                plugin.getDataStore().save();
                gui.openKeyStationSelect(player, targetStopId, parsePage(holder));
            }
        }
    }

    private void handleLineReorder(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        if (!player.hasPermission("atrain.station.edit")) return;
        Inventory inv = holder.getInventory();
        String lineId = holder.get("line_id");
        if (lineId == null) return;

        if (isBackSlot(inv, slot)) {
            gui.openLineDetail(player, lineId, 0);
            return;
        }
        int page = parsePage(holder);
        if (slot == GuiSlots.PREV_PAGE) {
            if (page > 0) gui.openLineReorder(player, lineId, page - 1);
            return;
        }
        if (slot == GuiSlots.NEXT_PAGE) {
            gui.openLineReorder(player, lineId, page + 1);
            return;
        }

        String stopId = holder.get("stop_" + slot);
        if (stopId != null) {
            if (blockLineEditDuringRecording(player, lineId)) return;
            Stop stop = plugin.getStopManager().getStop(stopId);
            if (stop == null) return;

            if (isPlainLeftClick(event)) {
                if (plugin.getLineManager().moveStopInLine(lineId, stopId, -1)) {
                    TextUtil.send(player, lang(player, "line.stop_moved_up", Map.of("stop", stop.getDisplayName())));
                } else {
                    TextUtil.send(player, lang(player, "line.stop_move_failed"));
                }
                gui.openLineReorder(player, lineId, page);
            } else if (isPlainRightClick(event)) {
                if (plugin.getLineManager().moveStopInLine(lineId, stopId, 1)) {
                    TextUtil.send(player, lang(player, "line.stop_moved_down", Map.of("stop", stop.getDisplayName())));
                } else {
                    TextUtil.send(player, lang(player, "line.stop_move_failed"));
                }
                gui.openLineReorder(player, lineId, page);
            }
        }
    }

    private void handleRecordModeSelect(Player player, int slot, GuiHolder holder, GuiManager gui) {
        if (!player.hasPermission("atrain.station.edit")) return;
        Inventory inv = holder.getInventory();
        String lineId = holder.get("line_id");
        if (lineId == null) return;

        if (isBackSlot(inv, slot)) {
            gui.openRecordSegmentSelect(player, lineId);
            return;
        }

        int segIdx = parseInt(holder.get("segment_index"), 0);
        com.avery.atrain.model.TravelDirection dir = com.avery.atrain.model.TravelDirection.valueOf(holder.get("direction"));

        if (slot == 11) {
            player.closeInventory();
            plugin.getRouteRecordingManager().start(player, lineId, segIdx, dir, true);
        } else if (slot == 15) {
            player.closeInventory();
            plugin.getRouteRecordingManager().autoRecordSegment(player, lineId, segIdx, dir);
            gui.openRecordSegmentSelect(player, lineId, dir, 0);
        }
    }

    private int parsePage(GuiHolder holder) {
        return Integer.parseInt(holder.get("page") != null ? holder.get("page") : "0");
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String lang(Player player, String key) {
        return plugin.getLanguageManager().get(player, key);
    }

    private String lang(Player player, String key, Map<String, String> ph) {
        return plugin.getLanguageManager().get(player, key, ph);
    }

    // ==========================================
    // 站點導覽與路線指南 事件處理 (Station Guide Event Handlers)
    // ==========================================

    private void handleGuideMain(Player player, int slot, GuiHolder holder, GuiManager gui) {
        switch (slot) {
            case 10 -> {
                String nearestId = holder.get("nearest_stop_id");
                if (nearestId != null) {
                    Stop stop = plugin.getStopManager().getStop(nearestId);
                    String coords = holder.get("nearest_coords");
                    double dist = Double.parseDouble(holder.get("nearest_dist") != null ? holder.get("nearest_dist") : "999");
                    String stopName = stop != null ? stop.getDisplayName() : nearestId;

                    if (dist <= 15.0) {
                        TextUtil.send(player, "§a✔ 您已抵達【" + stopName + "】附近，已將此站設為搭乘起點站！");
                        gui.openGuidePlanner(player, nearestId, null);
                    } else {
                        TextUtil.send(player, "§6📍 【最近車站座標指引】 §f" + stopName);
                        TextUtil.send(player, "§e車站座標: §f" + coords + " §7(距離您 §a" + String.format("%.1f", dist) + " §7公尺)");
                        TextUtil.send(player, "§e請依照座標指引步行前往該站點附近，抵達後方可在此設為搭乘起點！");
                        player.sendActionBar(TextUtil.component("§6📍 最近車站: §f" + stopName + " §7➔ 座標: §f" + coords + " §7(" + String.format("%.1f", dist) + "m)"));
                        player.closeInventory();
                    }
                } else {
                    TextUtil.send(player, "§c附近未找到金磚站點。");
                }
            }
            case 12 -> gui.openGuideLineList(player, 0);
            case 14 -> gui.openGuideTransferList(player, 0);
            case 16 -> gui.openGuidePlanner(player);
            case 20 -> {
                if (player.hasPermission("atrain.admin")) {
                    gui.openMain(player);
                }
            }
        }
    }

    private void handleGuideLineList(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        if (isBackSlot(event.getInventory(), slot)) {
            gui.openGuideMain(player);
            return;
        }
        int page = parsePage(holder);
        if (slot == 48 && page > 0) {
            gui.openGuideLineList(player, page - 1);
            return;
        }
        if (slot == 50) {
            gui.openGuideLineList(player, page + 1);
            return;
        }

        String lineId = holder.get("line_" + slot);
        if (lineId != null) {
            gui.openGuideLineDetail(player, lineId);
        }
    }

    private void handleGuideLineDetail(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        if (isBackSlot(event.getInventory(), slot)) {
            gui.openGuideLineList(player, 0);
            return;
        }

        String stopId = holder.get("stop_" + slot);
        if (stopId != null) {
            if (event.isRightClick()) {
                gui.openGuidePlanner(player, null, stopId);
            } else {
                gui.openGuidePlanner(player, stopId, null);
            }
        }
    }

    private void handleGuideTransferList(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        if (isBackSlot(event.getInventory(), slot)) {
            gui.openGuideMain(player);
            return;
        }
        int page = parsePage(holder);
        if (slot == 48 && page > 0) {
            gui.openGuideTransferList(player, page - 1);
            return;
        }
        if (slot == 50) {
            gui.openGuideTransferList(player, page + 1);
            return;
        }

        String stopId = holder.get("stop_" + slot);
        if (stopId != null) {
            if (event.isRightClick()) {
                gui.openGuidePlanner(player, null, stopId);
            } else {
                gui.openGuidePlanner(player, stopId, null);
            }
        }
    }

    private void handleGuidePlanner(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        String originId = holder.get("origin_stop_id");
        String destId = holder.get("dest_stop_id");

        if (isBackSlot(event.getInventory(), slot)) {
            gui.openGuideMain(player);
            return;
        }

        switch (slot) {
            case 11 -> gui.openGuideSelectStop(player, true, originId, destId, 0);
            case 15 -> gui.openGuideSelectStop(player, false, originId, destId, 0);
            case 22 -> {
                if (originId != null && destId != null && !originId.equals(destId)) {
                    var plan = plugin.getRoutePlannerService().calculateRoute(originId, destId);
                    gui.openGuidePlannerResult(player, plan);
                }
            }
            case 31 -> {
                plugin.getActiveNavigationManager().cancelNavigation(player);
                gui.openGuidePlanner(player, originId, destId);
            }
        }
    }

    private void handleGuideSelectStop(Player player, int slot, GuiHolder holder, GuiManager gui, InventoryClickEvent event) {
        boolean isOrigin = Boolean.parseBoolean(holder.get("is_origin"));
        int page = parsePage(holder);
        String originId = holder.get("origin_stop_id");
        String destId = holder.get("dest_stop_id");

        if (isBackSlot(event.getInventory(), slot)) {
            gui.openGuidePlanner(player, originId, destId);
            return;
        }
        if (slot == 48 && page > 0) {
            gui.openGuideSelectStop(player, isOrigin, originId, destId, page - 1);
            return;
        }
        if (slot == 50) {
            gui.openGuideSelectStop(player, isOrigin, originId, destId, page + 1);
            return;
        }

        String stopId = holder.get("stop_" + slot);
        if (stopId != null) {
            if (isOrigin) {
                gui.openGuidePlanner(player, stopId, destId);
            } else {
                gui.openGuidePlanner(player, originId, stopId);
            }
        }
    }

    private void handleGuidePlannerResult(Player player, int slot, GuiHolder holder, GuiManager gui) {
        String originId = holder.get("origin_stop_id");
        String destId = holder.get("dest_stop_id");

        if (slot == 38) { // 🚀 開始即時導航
            if (originId != null && destId != null) {
                var plan = plugin.getRoutePlannerService().calculateRoute(originId, destId);
                plugin.getActiveNavigationManager().startNavigation(player, plan);
                player.closeInventory();
            }
        } else if (slot == 40) { // 📢 分享至聊天室
            if (originId != null && destId != null) {
                var plan = plugin.getRoutePlannerService().calculateRoute(originId, destId);
                if (plan.found()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("§6【火車路線指引】 §f").append(player.getName()).append(" §7分享了路線搭乘指引：\n");
                    sb.append("§e起點: §f").append(plan.originStopName()).append(" §7➔ §e終點: §f").append(plan.destStopName());
                    sb.append(" §7(共 ").append(plan.totalStops()).append(" 站, ").append(plan.totalTransfers()).append(" 次轉乘)\n");
                    for (int i = 0; i < plan.steps().size(); i++) {
                        var step = plan.steps().get(i);
                        sb.append(" §7• 搭乘 ").append(step.lineColor()).append(step.lineDisplayName())
                          .append(" §7由 §f").append(step.fromStopName()).append(" §7到 §f").append(step.toStopName());
                        if (step.isTransferNext()) sb.append(" §e(轉乘)");
                        if (i < plan.steps().size() - 1) sb.append("\n");
                    }
                    org.bukkit.Bukkit.broadcast(TextUtil.component(sb.toString()));
                    TextUtil.send(player, "§a✔ 已成功將搭乘指引分享至公眾聊天室！");
                }
            }
        } else if (slot == 42) { // ❌ 取消 / 返回
            gui.openGuidePlanner(player, originId, destId);
        }
    }
}
