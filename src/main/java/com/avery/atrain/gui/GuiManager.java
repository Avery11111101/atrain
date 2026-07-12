package com.avery.atrain.gui;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GuiManager {

    private final AtrainPlugin plugin;

    public GuiManager(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    private String msg(Player p, String key) {
        return TextUtil.colorize(plugin.getLanguageManager().get(p, key));
    }

    private String msg(Player p, String key, Map<String, String> ph) {
        return TextUtil.colorize(plugin.getLanguageManager().get(p, key, ph));
    }

    private void fillBorder(Inventory inv) {
        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    private void addBack(Inventory inv, Player p) {
        inv.setItem(GuiSlots.back(inv), new ItemBuilder(Material.ARROW)
                .name(msg(p, "gui.back")).build());
    }

    private void addClose(Inventory inv, Player p) {
        inv.setItem(inv.getSize() - 1, new ItemBuilder(Material.BARRIER)
                .name(msg(p, "gui.close")).build());
    }

    // ── 主選單 ──
    public void openMain(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.MAIN);
        Inventory inv = Bukkit.createInventory(holder, 45, msg(player, "gui.main.title"));
        holder.setInventory(inv);

        inv.setItem(10, new ItemBuilder(Material.BOOK)
                .name(msg(player, "gui.main.tutorial"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.tutorial_lore"))
                .build());

        inv.setItem(12, new ItemBuilder(Material.MINECART)
                .name(msg(player, "gui.main.lines"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.lines_lore"))
                .build());

        inv.setItem(14, new ItemBuilder(Material.OAK_SIGN)
                .name(msg(player, "gui.main.stops"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.stops_lore"))
                .build());

        inv.setItem(16, new ItemBuilder(Material.COMPASS)
                .name(msg(player, "gui.main.record"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.record_lore"))
                .build());

        inv.setItem(20, new ItemBuilder(Material.GOLDEN_AXE)
                .name(msg(player, "gui.main.create_stop"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.create_stop_lore"))
                .build());
        inv.setItem(22, new ItemBuilder(Material.EMERALD)
                .name(msg(player, "gui.main.create_line"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.create_line_lore"))
                .build());
        inv.setItem(24, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(msg(player, "gui.main.quick_setup"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.quick_setup_lore"))
                .build());

        inv.setItem(30, new ItemBuilder(Material.NAME_TAG)
                .name(msg(player, "gui.main.language"))
                .lore(msg(player, "gui.main.language_lore", Map.of(
                        "lang", plugin.getLanguageManager().getPlayerLanguage(player))))
                .build());

        inv.setItem(32, new ItemBuilder(Material.REDSTONE)
                .name(msg(player, "gui.main.reload"))
                .lore(msg(player, "gui.main.reload_lore"))
                .build());

        boolean hangOn = plugin.getConfigManager().isHangRailEnabled();
        inv.setItem(34, new ItemBuilder(Material.IRON_BARS)
                .name(msg(player, hangOn ? "gui.main.hangrail_on" : "gui.main.hangrail_off"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.hangrail_lore"))
                .build());

        fillBorder(inv);
        addClose(inv, player);
        player.openInventory(inv);
    }

    // ── 教學選單 ──
    public void openTutorial(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.TUTORIAL);
        Inventory inv = Bukkit.createInventory(holder, 54, msg(player, "gui.tutorial.title"));
        holder.setInventory(inv);

        String[] cats = {"quickstart", "stop", "line", "route", "hangrail", "ride", "advanced"};
        Material[] icons = {Material.LIME_DYE, Material.OAK_SIGN, Material.RAIL,
                Material.MINECART, Material.IRON_BARS, Material.DIAMOND, Material.NETHER_STAR};
        int[] slots = {10, 11, 13, 15, 21, 23, 25};

        for (int i = 0; i < cats.length; i++) {
            String cat = cats[i];
            inv.setItem(slots[i], new ItemBuilder(icons[i])
                    .name(msg(player, "tutorial." + cat + ".title"))
                    .lore(plugin.getLanguageManager().getList(player, "tutorial." + cat + ".summary"))
                    .build());
            holder.set("cat_" + slots[i], cat);
        }

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openTutorialCategory(Player player, String category) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.TUTORIAL_CATEGORY);
        holder.set("category", category);
        Inventory inv = Bukkit.createInventory(holder, 54,
                msg(player, "tutorial." + category + ".title"));
        holder.setInventory(inv);

        List<String> steps = plugin.getLanguageManager().getList(player, "tutorial." + category + ".steps");
        int slot = 10;
        int stepNum = 1;
        for (String step : steps) {
            if (slot > 43) break;
            if (slot % 9 == 8) slot += 2;
            inv.setItem(slot++, new ItemBuilder(Material.PAPER)
                    .name("§e" + stepNum++ + ". §f" + step.split("\n")[0])
                    .lore(step.split("\n"))
                    .build());
        }

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    // ── 路線列表 ──
    public void openLineList(Player player, int page) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_LIST);
        holder.set("page", String.valueOf(page));
        Inventory inv = Bukkit.createInventory(holder, 54, msg(player, "gui.line_list.title"));
        holder.setInventory(inv);

        List<Line> lines = new ArrayList<>(plugin.getLineManager().getAllLines());
        int perPage = 28, start = page * perPage;
        int slot = 10;

        for (int i = start; i < Math.min(start + perPage, lines.size()); i++) {
            Line line = lines.get(i);
            if (slot % 9 == 8) slot += 2;
            inv.setItem(slot++, new ItemBuilder(Material.RAIL)
                    .name(line.getFormattedName() + " §7(" + line.getId() + ")")
                    .lore(
                            msg(player, "gui.line_list.stops_count", Map.of("count", String.valueOf(line.getStopIds().size()))),
                            msg(player, "gui.line_list.route_count", Map.of("count", String.valueOf(line.getRoutePoints().size()))),
                            "",
                            msg(player, "gui.click_to_manage")
                    ).build());
            holder.set("line_" + (slot - 1), line.getId());
        }

        if (page > 0) {
            inv.setItem(GuiSlots.PREV_PAGE, new ItemBuilder(Material.ARROW).name("§e◀").build());
        }
        if (start + perPage < lines.size()) {
            inv.setItem(GuiSlots.NEXT_PAGE, new ItemBuilder(Material.ARROW).name("§e▶").build());
        }

        inv.setItem(GuiSlots.CREATE, new ItemBuilder(Material.EMERALD)
                .name(msg(player, "gui.line_list.create"))
                .lore(msg(player, "gui.line_list.create_lore"))
                .build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openRecordSelect(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.RECORD_SELECT);
        Inventory inv = Bukkit.createInventory(holder, 54, msg(player, "gui.record_select.title"));
        holder.setInventory(inv);

        int slot = 10;
        for (Line line : plugin.getLineManager().getAllLines()) {
            if (slot % 9 == 8) slot += 2;
            if (slot > 43) break;
            boolean recording = plugin.getRouteRecorder().isRecording(player)
                    && line.getId().equals(plugin.getRouteRecorder().getRecordingLine(player));
            inv.setItem(slot, new ItemBuilder(recording ? Material.REDSTONE : Material.COMPASS)
                    .name(line.getFormattedName())
                    .lore(
                            msg(player, "gui.line_list.route_count", Map.of("count", String.valueOf(line.getRoutePoints().size()))),
                            "",
                            msg(player, recording ? "gui.record_select.stop" : "gui.record_select.start")
                    ).build());
            holder.set("line_" + slot, line.getId());
            slot++;
        }

        if (plugin.getLineManager().getAllLines().isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg(player, "gui.record_select.no_lines"))
                    .lore(msg(player, "gui.record_select.no_lines_lore"))
                    .build());
        }

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openQuickSetup(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.QUICK_SETUP);
        Inventory inv = Bukkit.createInventory(holder, 45, msg(player, "gui.quick_setup.title"));
        holder.setInventory(inv);

        var sel = plugin.getSelectionManager();
        String selStatus = sel.hasBothCorners(player)
                ? msg(player, "gui.quick_setup.selection_ready")
                : msg(player, "gui.quick_setup.selection_missing");

        inv.setItem(10, new ItemBuilder(Material.GOLDEN_AXE)
                .name(msg(player, "gui.quick_setup.step1"))
                .lore(selStatus, "", msg(player, "gui.quick_setup.step1_hint"))
                .build());
        inv.setItem(12, new ItemBuilder(Material.OAK_SIGN)
                .name(msg(player, "gui.quick_setup.step2"))
                .lore(msg(player, "gui.quick_setup.step2_hint"))
                .build());
        inv.setItem(14, new ItemBuilder(Material.RAIL)
                .name(msg(player, "gui.quick_setup.step3"))
                .lore(msg(player, "gui.quick_setup.step3_hint"))
                .build());
        inv.setItem(16, new ItemBuilder(Material.COMPASS)
                .name(msg(player, "gui.quick_setup.step4"))
                .lore(msg(player, "gui.quick_setup.step4_hint"))
                .build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openLineDetail(Player player, String lineId) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) { openLineList(player, 0); return; }

        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_DETAIL);
        holder.set("line_id", lineId);
        Inventory inv = Bukkit.createInventory(holder, 45,
                msg(player, "gui.line_detail.title", Map.of("name", line.getDisplayName())));
        holder.setInventory(inv);

        inv.setItem(11, new ItemBuilder(Material.REDSTONE)
                .name(msg(player, "gui.line_detail.speed_down"))
                .build());
        inv.setItem(13, new ItemBuilder(Material.SUGAR)
                .name(msg(player, "gui.line_detail.speed", Map.of("speed", String.valueOf(line.getMaxSpeed()))))
                .build());
        inv.setItem(15, new ItemBuilder(Material.COMPASS)
                .name(msg(player, plugin.getRouteRecorder().isRecording(player)
                        ? "gui.line_detail.stop_record" : "gui.line_detail.record"))
                .lore(plugin.getLanguageManager().getList(player, "gui.line_detail.record_lore"))
                .build());
        inv.setItem(20, new ItemBuilder(Material.OAK_SIGN)
                .name(msg(player, "gui.line_detail.manage_stops"))
                .lore(listStopNames(player, line))
                .build());
        inv.setItem(22, new ItemBuilder(Material.EMERALD)
                .name(msg(player, "gui.line_detail.add_stop"))
                .lore(msg(player, "gui.line_detail.add_stop_lore"))
                .build());
        inv.setItem(28, new ItemBuilder(Material.MAP)
                .name(msg(player, "gui.line_detail.platforms"))
                .lore(msg(player, "gui.line_detail.platforms_lore"))
                .build());
        inv.setItem(33, new ItemBuilder(Material.BUCKET)
                .name(msg(player, "gui.line_detail.clear_route"))
                .lore(msg(player, "gui.line_detail.clear_route_lore",
                        Map.of("count", String.valueOf(line.getRoutePoints().size()))))
                .build());
        inv.setItem(31, new ItemBuilder(Material.REPEATER)
                .name(msg(player, line.isCircular() ? "gui.line_detail.circular_on" : "gui.line_detail.circular_off"))
                .build());
        inv.setItem(24, new ItemBuilder(Material.TNT)
                .name(msg(player, "gui.line_detail.delete"))
                .lore(msg(player, "gui.line_detail.delete_lore"))
                .build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    private List<String> listStopNames(Player player, Line line) {
        List<String> names = new ArrayList<>();
        int i = 1;
        for (String sid : line.getStopIds()) {
            Stop s = plugin.getStopManager().getStop(sid);
            names.add("§7" + i++ + ". §f" + (s != null ? s.getDisplayName() : sid));
        }
        if (names.isEmpty()) names.add(msg(player, "gui.line_detail.no_stops"));
        return names;
    }

    public void openAddStopToLine(Player player, String lineId) {
        openAddStopToLine(player, lineId, 0);
    }

    public void openAddStopToLine(Player player, String lineId, int page) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) { openLineList(player, 0); return; }

        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_ADD_STOP);
        holder.set("line_id", lineId);
        holder.set("page", String.valueOf(page));
        Inventory inv = Bukkit.createInventory(holder, 54,
                msg(player, "gui.add_stop.title", Map.of("line", line.getDisplayName())));
        holder.setInventory(inv);

        List<Stop> available = new ArrayList<>();
        for (Stop stop : plugin.getStopManager().getAllStops()) {
            if (!line.getStopIds().contains(stop.getId())) {
                available.add(stop);
            }
        }

        int perPage = 28, start = page * perPage;
        int slot = 10;
        for (int i = start; i < Math.min(start + perPage, available.size()); i++) {
            Stop stop = available.get(i);
            if (slot % 9 == 8) slot += 2;
            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("§a" + stop.getDisplayName())
                    .lore("§7" + stop.getId(), "", msg(player, "gui.add_stop.click"))
                    .build());
            holder.set("add_" + slot, stop.getId());
            slot++;
        }

        if (page > 0) {
            inv.setItem(GuiSlots.PREV_PAGE, new ItemBuilder(Material.ARROW).name("§e◀").build());
        }
        if (start + perPage < available.size()) {
            inv.setItem(GuiSlots.NEXT_PAGE, new ItemBuilder(Material.ARROW).name("§e▶").build());
        }

        inv.setItem(GuiSlots.CREATE, new ItemBuilder(Material.EMERALD)
                .name(msg(player, "gui.add_stop.create_stop"))
                .lore(msg(player, "gui.add_stop.create_stop_lore"))
                .build());

        if (available.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg(player, "gui.add_stop.empty"))
                    .lore(msg(player, "gui.add_stop.empty_lore"))
                    .build());
        }

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openManageStops(Player player, String lineId) {
        openManageStops(player, lineId, 0);
    }

    public void openManageStops(Player player, String lineId, int page) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) { openLineList(player, 0); return; }

        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_MANAGE_STOPS);
        holder.set("line_id", lineId);
        holder.set("page", String.valueOf(page));
        Inventory inv = Bukkit.createInventory(holder, 54,
                msg(player, "gui.manage_stops.title", Map.of("line", line.getDisplayName())));
        holder.setInventory(inv);

        List<String> stopIds = new ArrayList<>(line.getStopIds());
        int perPage = 28, start = page * perPage;
        int slot = 10;
        for (int i = start; i < Math.min(start + perPage, stopIds.size()); i++) {
            String sid = stopIds.get(i);
            Stop stop = plugin.getStopManager().getStop(sid);
            if (slot % 9 == 8) slot += 2;
            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("§7" + (i + 1) + ". §f" + (stop != null ? stop.getDisplayName() : sid))
                    .lore("§7" + sid, "", msg(player, "gui.manage_stops.remove_hint"))
                    .build());
            holder.set("stop_" + slot, sid);
            slot++;
        }

        if (page > 0) {
            inv.setItem(GuiSlots.PREV_PAGE, new ItemBuilder(Material.ARROW).name("§e◀").build());
        }
        if (start + perPage < stopIds.size()) {
            inv.setItem(GuiSlots.NEXT_PAGE, new ItemBuilder(Material.ARROW).name("§e▶").build());
        }

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    // ── 站點列表 ──
    public void openStopList(Player player, int page) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.STOP_LIST);
        holder.set("page", String.valueOf(page));
        Inventory inv = Bukkit.createInventory(holder, 54, msg(player, "gui.stop_list.title"));
        holder.setInventory(inv);

        List<Stop> stops = new ArrayList<>(plugin.getStopManager().getAllStops());
        int perPage = 28, start = page * perPage;
        int slot = 10;

        for (int i = start; i < Math.min(start + perPage, stops.size()); i++) {
            Stop stop = stops.get(i);
            if (slot % 9 == 8) slot += 2;
            inv.setItem(slot++, new ItemBuilder(Material.OAK_SIGN)
                    .name("§a" + stop.getDisplayName() + " §7(" + stop.getId() + ")")
                    .lore(
                            msg(player, "gui.stop_list.world", Map.of("world", stop.getWorld())),
                            msg(player, "gui.stop_list.lines", Map.of("count", String.valueOf(stop.getLineIds().size()))),
                            "",
                            msg(player, "gui.click_to_manage")
                    ).build());
            holder.set("stop_" + (slot - 1), stop.getId());
        }

        inv.setItem(GuiSlots.CREATE, new ItemBuilder(Material.EMERALD)
                .name(msg(player, "gui.stop_list.create"))
                .lore(plugin.getLanguageManager().getList(player, "gui.stop_list.create_lore"))
                .build());

        if (stops.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg(player, "gui.stop_list.empty"))
                    .lore(msg(player, "gui.stop_list.empty_lore"))
                    .build());
        }

        if (page > 0) inv.setItem(GuiSlots.PREV_PAGE, new ItemBuilder(Material.ARROW).name("§e◀").build());
        if (start + perPage < stops.size()) inv.setItem(GuiSlots.NEXT_PAGE, new ItemBuilder(Material.ARROW).name("§e▶").build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openStopDetail(Player player, String stopId) {
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) { openStopList(player, 0); return; }

        GuiHolder holder = new GuiHolder(GuiHolder.Type.STOP_DETAIL);
        holder.set("stop_id", stopId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                msg(player, "gui.stop_detail.title", Map.of("name", stop.getDisplayName())));
        holder.setInventory(inv);

        inv.setItem(11, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(msg(player, "gui.stop_detail.set_forward"))
                .lore(plugin.getLanguageManager().getList(player, "gui.stop_detail.set_forward_lore"))
                .build());
        inv.setItem(13, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(msg(player, "gui.stop_detail.set_return"))
                .lore(plugin.getLanguageManager().getList(player, "gui.stop_detail.set_return_lore"))
                .build());
        inv.setItem(15, new ItemBuilder(Material.ENDER_PEARL)
                .name(msg(player, "gui.stop_detail.tp_forward"))
                .build());
        inv.setItem(17, new ItemBuilder(Material.ENDER_PEARL)
                .name(msg(player, "gui.stop_detail.tp_return"))
                .build());

        var conflict = com.avery.atrain.util.StopPlatformUtil.checkConflict(stop);
        Material statusMat = conflict == com.avery.atrain.util.StopPlatformUtil.ConflictLevel.OK
                ? Material.LIME_DYE
                : conflict == com.avery.atrain.util.StopPlatformUtil.ConflictLevel.SAME_RAIL_WARN
                ? Material.YELLOW_DYE : Material.RED_DYE;
        inv.setItem(22, new ItemBuilder(statusMat)
                .name(msg(player, "gui.stop_detail.platform_status"))
                .lore(msg(player, "gui.stop_detail.platform_status_" + conflict.name().toLowerCase()))
                .build());

        inv.setItem(40, new ItemBuilder(Material.TNT)
                .name(msg(player, "gui.stop_detail.delete"))
                .build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openLinePlatforms(Player player, String lineId) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) { openLineList(player, 0); return; }

        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_PLATFORMS);
        holder.set("line_id", lineId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                msg(player, "gui.line_platforms.title", Map.of("line", line.getDisplayName())));
        holder.setInventory(inv);

        int slot = 10;
        int order = 1;
        for (String sid : line.getStopIds()) {
            Stop stop = plugin.getStopManager().getStop(sid);
            if (stop == null) continue;
            if (slot % 9 == 8) slot += 2;
            String f = stop.hasForwardPoint() ? "§a✔" : "§c✘";
            String r = stop.hasReturnPoint() ? "§a✔" : "§c✘";
            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("§7" + order++ + ". §f" + stop.getDisplayName())
                    .lore(
                            msg(player, "gui.line_platforms.forward", Map.of("status", f)),
                            msg(player, "gui.line_platforms.return", Map.of("status", r)),
                            "",
                            msg(player, "gui.line_platforms.click_edit")
                    ).build());
            holder.set("stop_" + slot, sid);
            slot++;
            if (slot > 43) break;
        }

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    // ── 換乘選線 ──
    public void openLineChoice(Player player, Stop stop, List<Line> lines) {
        openLineChoice(player, stop, lines, com.avery.atrain.model.PlatformSide.FORWARD, null);
    }

    public void openLineChoice(Player player, Stop stop, List<Line> lines,
                               com.avery.atrain.model.PlatformSide platform, Location railLoc) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_CHOICE);
        holder.set("stop_id", stop.getId());
        holder.set("platform", platform.toDataString());
        if (railLoc != null && railLoc.getWorld() != null) {
            holder.set("rail_x", String.valueOf(railLoc.getBlockX()));
            holder.set("rail_y", String.valueOf(railLoc.getBlockY()));
            holder.set("rail_z", String.valueOf(railLoc.getBlockZ()));
        }
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg(player, "gui.line_choice.title", Map.of("stop", stop.getDisplayName())));
        holder.setInventory(inv);

        String dirHint = msg(player, platform == com.avery.atrain.model.PlatformSide.FORWARD
                ? "ride.direction_forward" : "ride.direction_reverse");
        int slot = 10;
        for (Line line : lines) {
            inv.setItem(slot, new ItemBuilder(Material.MINECART)
                    .name(line.getFormattedName())
                    .lore(msg(player, "gui.line_choice.dir", Map.of("dir", dirHint)),
                            msg(player, "gui.line_choice.click"))
                    .build());
            holder.set("choice_" + slot, line.getId());
            slot++;
        }

        fillBorder(inv);
        addClose(inv, player);
        player.openInventory(inv);
    }

    // ── 語言選擇 ──
    public void openLanguage(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.LANGUAGE);
        Inventory inv = Bukkit.createInventory(holder, 27, msg(player, "gui.language.title"));
        holder.setInventory(inv);

        int slot = 10;
        for (String code : plugin.getLanguageManager().getAvailableLanguages()) {
            inv.setItem(slot++, new ItemBuilder(Material.PAPER)
                    .name("§f" + code)
                    .lore(code.equals(plugin.getLanguageManager().getPlayerLanguage(player))
                            ? msg(player, "gui.language.current") : msg(player, "gui.language.click"))
                    .build());
            holder.set("lang_" + (slot - 1), code);
        }

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    // ── 確認對話框 ──
    public void openConfirm(Player player, String action, String targetId, String message, String returnType, String returnId) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.CONFIRM);
        holder.set("action", action);
        holder.set("target_id", targetId);
        holder.set("return_type", returnType);
        holder.set("return_id", returnId);
        Inventory inv = Bukkit.createInventory(holder, 27, msg(player, "gui.confirm.title"));
        holder.setInventory(inv);

        inv.setItem(11, new ItemBuilder(Material.LIME_WOOL)
                .name(msg(player, "gui.confirm.yes"))
                .lore(message)
                .build());
        inv.setItem(15, new ItemBuilder(Material.RED_WOOL)
                .name(msg(player, "gui.confirm.no"))
                .build());

        fillBorder(inv);
        player.openInventory(inv);
    }
}
