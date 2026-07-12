package com.avery.atrain.gui;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.model.Stop;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;
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

    private Map<String, String> safePh(Map<String, String> ph) {
        Map<String, String> out = new java.util.HashMap<>();
        ph.forEach((k, v) -> out.put(k, TextUtil.escapePlain(v)));
        return out;
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

    public void openMain(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.MAIN);
        Inventory inv = Bukkit.createInventory(holder, 45, msg(player, "gui.main.title"));
        holder.setInventory(inv);

        inv.setItem(10, new ItemBuilder(Material.BOOK)
                .name(msg(player, "gui.main.tutorial"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.tutorial_lore"))
                .build());

        inv.setItem(13, new ItemBuilder(Material.GOLD_BLOCK)
                .name(msg(player, "gui.main.stops"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.stops_lore"))
                .build());

        inv.setItem(16, new ItemBuilder(Material.MINECART)
                .name(msg(player, "gui.main.lines"))
                .lore(plugin.getLanguageManager().getList(player, "gui.main.lines_lore"))
                .build());

        inv.setItem(30, new ItemBuilder(Material.NAME_TAG)
                .name(msg(player, "gui.main.language"))
                .lore(msg(player, "gui.main.language_lore", Map.of(
                        "lang", plugin.getLanguageManager().getPlayerLanguage(player))))
                .build());

        inv.setItem(32, new ItemBuilder(Material.REDSTONE)
                .name(msg(player, player.hasPermission("atrain.admin")
                        ? "gui.main.reload" : "gui.main.reload_locked"))
                .lore(msg(player, player.hasPermission("atrain.admin")
                        ? "gui.main.reload_lore" : "gui.main.admin_only"))
                .build());

        boolean hangOn = plugin.getConfigManager().isHangRailEnabled();
        boolean admin = player.hasPermission("atrain.admin");
        inv.setItem(34, new ItemBuilder(Material.IRON_BARS)
                .name(msg(player, admin
                        ? (hangOn ? "gui.main.hangrail_on" : "gui.main.hangrail_off")
                        : "gui.main.hangrail_locked"))
                .lore(msg(player, admin ? "gui.main.hangrail_lore" : "gui.main.admin_only"))
                .build());

        fillBorder(inv);
        addClose(inv, player);
        player.openInventory(inv);
    }

    public void openTutorial(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.TUTORIAL);
        Inventory inv = Bukkit.createInventory(holder, 45, msg(player, "gui.tutorial.title"));
        holder.setInventory(inv);

        String[] cats = {"quickstart", "stop", "autostop", "display", "hangrail"};
        Material[] icons = {Material.LIME_DYE, Material.GOLD_BLOCK, Material.MINECART,
                Material.DIAMOND_BLOCK, Material.IRON_BARS};
        int[] slots = {11, 13, 15, 21, 23};

        for (int i = 0; i < cats.length; i++) {
            inv.setItem(slots[i], new ItemBuilder(icons[i])
                    .name(msg(player, "tutorial." + cats[i] + ".title"))
                    .lore(plugin.getLanguageManager().getList(player, "tutorial." + cats[i] + ".summary"))
                    .build());
            holder.set("cat_" + slots[i], cats[i]);
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
            inv.setItem(slot++, new ItemBuilder(Material.GOLD_BLOCK)
                    .name("§a" + stop.getDisplayName() + " §7(" + stop.getId() + ")")
                    .lore(buildStopListLore(player, stop))
                    .build());
            holder.set("stop_" + (slot - 1), stop.getId());
        }

        inv.setItem(GuiSlots.CREATE, new ItemBuilder(Material.GOLD_BLOCK)
                .name(msg(player, "gui.stop_list.hint"))
                .lore(plugin.getLanguageManager().getList(player, "gui.stop_list.create_lore"))
                .build());

        if (stops.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg(player, "gui.stop_list.empty"))
                    .lore(plugin.getLanguageManager().getList(player, "gui.stop_list.empty_lore"))
                    .build());
        }

        if (page > 0) inv.setItem(GuiSlots.PREV_PAGE, new ItemBuilder(Material.ARROW).name("§e◀").build());
        if (start + perPage < stops.size()) inv.setItem(GuiSlots.NEXT_PAGE, new ItemBuilder(Material.ARROW).name("§e▶").build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openStationEdit(Player player, String stopId) {
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) return;

        GuiHolder holder = new GuiHolder(GuiHolder.Type.STATION_EDIT);
        holder.set("stop_id", stopId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                msg(player, "gui.station_edit.title", safePh(Map.of("name", stop.getDisplayName()))));
        holder.setInventory(inv);

        int dwellSec = stop.getDwellTimeTicks() / 20;
        var stopMgr = plugin.getStopManager();
        Line displayLine = stopMgr.resolveDisplayLine(stop);
        String prev = stopMgr.resolveDisplayPrev(stop);
        String next = stopMgr.resolveDisplayNext(stop);
        String lineName = displayLine != null ? displayLine.getDisplayName() : "-";

        inv.setItem(4, new ItemBuilder(Material.NAME_TAG)
                .name(msg(player, "gui.station_edit.rename"))
                .lore(msg(player, "gui.station_edit.rename_lore", safePh(Map.of("name", stop.getDisplayName()))))
                .build());
        inv.setItem(10, new ItemBuilder(Material.ARROW)
                .name(msg(player, "gui.station_edit.info_prev"))
                .lore(buildPrevNextLore(player, prev, displayLine != null, lineName))
                .build());
        inv.setItem(12, new ItemBuilder(Material.OAK_SIGN)
                .name(msg(player, "gui.station_edit.info_current"))
                .lore(msg(player, "gui.station_edit.info_current_lore", safePh(Map.of("name", stop.getDisplayName()))))
                .build());
        inv.setItem(14, new ItemBuilder(Material.ARROW)
                .name(msg(player, "gui.station_edit.info_next"))
                .lore(buildPrevNextLore(player, next, displayLine != null, lineName))
                .build());
        inv.setItem(15, new ItemBuilder(Material.RAIL)
                .name(msg(player, "gui.station_edit.manage_lines"))
                .lore(plugin.getLanguageManager().getList(player, "gui.station_edit.manage_lines_lore"))
                .build());
        inv.setItem(16, new ItemBuilder(Material.NETHER_STAR)
                .name(msg(player, "gui.station_edit.key_station"))
                .lore(msg(player, "gui.station_edit.key_station_lore", safePh(Map.of("name", stop.getKeyStation()))))
                .build());
        inv.setItem(18, new ItemBuilder(Material.COMPASS)
                .name(msg(player, "gui.station_edit.key_direction"))
                .lore(msg(player, "gui.station_edit.key_direction_lore", safePh(Map.of("name", stop.getKeyDirection()))))
                .build());

        boolean showDwell = plugin.getConfigManager().isAutoStopEnabled()
                && player.hasPermission("atrain.admin");
        if (showDwell) {
            inv.setItem(20, new ItemBuilder(Material.REDSTONE)
                    .name(msg(player, "gui.station_edit.dwell_down"))
                    .build());
            inv.setItem(22, new ItemBuilder(Material.CLOCK)
                    .name(msg(player, "gui.station_edit.dwell", Map.of("sec", String.valueOf(dwellSec))))
                    .lore(msg(player, "gui.station_edit.dwell_lore"))
                    .build());
            inv.setItem(24, new ItemBuilder(Material.GLOWSTONE_DUST)
                    .name(msg(player, "gui.station_edit.dwell_up"))
                    .build());
        }
        inv.setItem(29, new ItemBuilder(Material.GOLD_BLOCK)
                .name(msg(player, "gui.station_edit.gold_count",
                        Map.of("count", String.valueOf(stop.getGoldBlocks().size()))))
                .lore(msg(player, "gui.station_edit.gold_hint"))
                .build());
        inv.setItem(31, new ItemBuilder(Material.DIAMOND_BLOCK)
                .name(msg(player, "gui.station_edit.display_count",
                        Map.of("count", String.valueOf(stop.getDisplayBlocks().size()))))
                .lore(msg(player, "gui.station_edit.display_hint"))
                .build());
        inv.setItem(33, new ItemBuilder(Material.EMERALD)
                .name(msg(player, "gui.station_edit.rescan_display"))
                .build());

        int returnGoldCount = stop.getReturnGoldBlocks().size();
        inv.setItem(28, new ItemBuilder(Material.IRON_BLOCK)
                .name(msg(player, "gui.station_edit.bind_return"))
                .lore(buildBindReturnLore(player, returnGoldCount))
                .build());

        Line returnLine = stop.getReturnLineId() != null
                ? plugin.getLineManager().getLine(stop.getReturnLineId()) : null;
        String returnLineName = returnLine != null ? returnLine.getDisplayName() : "-";
        inv.setItem(34, new ItemBuilder(Material.POWERED_RAIL)
                .name(msg(player, "gui.station_edit.return_line"))
                .lore(msg(player, "gui.station_edit.return_line_lore",
                        safePh(Map.of("line", returnLineName))))
                .build());

        if (player.hasPermission("atrain.admin")) {
            inv.setItem(38, new ItemBuilder(Material.WRITABLE_BOOK)
                    .name(msg(player, "gui.station_edit.admin_info"))
                    .lore(msg(player, "gui.station_edit.admin_info_lore",
                            safePh(Map.of("info", stop.getAdminInfo().isBlank() ? "-" : stop.getAdminInfo()))))
                    .build());
            inv.setItem(40, new ItemBuilder(Material.TNT)
                    .name(msg(player, "gui.station_edit.delete"))
                    .lore(msg(player, "gui.station_edit.delete_lore"))
                    .build());
        }

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    private List<String> buildStopListLore(Player player, Stop stop) {
        List<String> lore = new ArrayList<>();
        lore.add(msg(player, "gui.stop_list.world", Map.of("world", stop.getWorld())));
        if (plugin.getConfigManager().isAutoStopEnabled()) {
            lore.add(msg(player, "gui.stop_list.dwell", Map.of("sec", String.valueOf(stop.getDwellTimeTicks() / 20))));
        }
        if (stop.hasKeyInfo()) {
            lore.add(msg(player, "gui.stop_list.key_info", Map.of(
                    "station", stop.getKeyStation(),
                    "direction", stop.getKeyDirection())));
        }
        int lineCount = stop.getLineIds().size();
        if (lineCount > 0) {
            lore.add(msg(player, "gui.stop_list.lines", Map.of("count", String.valueOf(lineCount))));
        }
        lore.add("");
        lore.add(msg(player, "gui.click_to_manage"));
        return lore;
    }

    public void openLanguage(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.LANGUAGE);
        Inventory inv = Bukkit.createInventory(holder, 27, msg(player, "gui.language.title"));
        holder.setInventory(inv);

        int slot = 10;
        int max = inv.getSize() - 10;
        for (String code : plugin.getLanguageManager().getAvailableLanguages()) {
            if (slot >= max) break;
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

    public void openLineList(Player player, int page) {
        openLineList(player, page, null, null);
    }

    public void openLineList(Player player, int page, String returnStopId) {
        openLineList(player, page, returnStopId, null);
    }

    public void openLineList(Player player, int page, String returnStopId, String pickReturnLineForStop) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_LIST);
        holder.set("page", String.valueOf(page));
        if (returnStopId != null) holder.set("return_stop_id", returnStopId);
        if (pickReturnLineForStop != null) holder.set("pick_return_line", pickReturnLineForStop);
        Inventory inv = Bukkit.createInventory(holder, 54, msg(player, "gui.line_list.title"));
        holder.setInventory(inv);

        List<Line> lines = new ArrayList<>(plugin.getLineManager().getAllLines());
        int perPage = 28, start = page * perPage;
        int slot = 10;

        for (int i = start; i < Math.min(start + perPage, lines.size()); i++) {
            Line line = lines.get(i);
            if (slot % 9 == 8) slot += 2;
            inv.setItem(slot, new ItemBuilder(Material.MINECART)
                    .name("§b" + line.getDisplayName() + " §7(" + line.getId() + ")")
                    .lore(List.of(
                            msg(player, "gui.line_list.stops_count", Map.of("count", String.valueOf(line.getStopIds().size()))),
                            line.isCircular() ? msg(player, "gui.line_list.circular") : msg(player, "gui.line_list.oneway"),
                            "",
                            msg(player, "gui.click_to_manage")))
                    .build());
            holder.set("line_" + slot, line.getId());
            slot++;
        }

        inv.setItem(GuiSlots.CREATE, new ItemBuilder(Material.LIME_DYE)
                .name(msg(player, "gui.line_list.create"))
                .lore(plugin.getLanguageManager().getList(player, "gui.line_list.create_lore"))
                .build());

        if (lines.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg(player, "gui.line_list.empty"))
                    .lore(plugin.getLanguageManager().getList(player, "gui.line_list.empty_lore"))
                    .build());
        }

        if (page > 0) inv.setItem(GuiSlots.PREV_PAGE, new ItemBuilder(Material.ARROW).name("§e◀").build());
        if (start + perPage < lines.size()) inv.setItem(GuiSlots.NEXT_PAGE, new ItemBuilder(Material.ARROW).name("§e▶").build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openLineDetail(Player player, String lineId, int stopPage) {
        openLineDetail(player, lineId, stopPage, null);
    }

    public void openLineDetail(Player player, String lineId, int stopPage, String returnStopId) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_DETAIL);
        holder.set("line_id", lineId);
        holder.set("stop_page", String.valueOf(stopPage));
        if (returnStopId != null) holder.set("return_stop_id", returnStopId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                msg(player, "gui.line_detail.title", safePh(Map.of("name", line.getDisplayName()))));
        holder.setInventory(inv);

        inv.setItem(4, new ItemBuilder(Material.NAME_TAG)
                .name(msg(player, "gui.line_detail.rename"))
                .lore(msg(player, "gui.line_detail.rename_lore", safePh(Map.of("name", line.getDisplayName()))))
                .build());

        inv.setItem(6, new ItemBuilder(line.isCircular() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(msg(player, line.isCircular() ? "gui.line_detail.circular_on" : "gui.line_detail.circular_off"))
                .lore(msg(player, "gui.line_detail.circular_lore"))
                .build());

        inv.setItem(8, new ItemBuilder(Material.TNT)
                .name(msg(player, "gui.line_detail.delete"))
                .lore(plugin.getLanguageManager().getList(player, "gui.line_detail.delete_lore"))
                .build());

        List<String> stopIds = line.getStopIds();
        int perPage = 7, start = stopPage * perPage;
        int[] stopSlots = {19, 20, 21, 22, 23, 24, 25};

        if (stopIds.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg(player, "gui.line_detail.no_stops"))
                    .build());
        } else {
            for (int i = 0; i < perPage && start + i < stopIds.size(); i++) {
                String stopId = stopIds.get(start + i);
                Stop stop = plugin.getStopManager().getStop(stopId);
                String name = stop != null ? stop.getDisplayName() : stopId;
                int index = start + i + 1;
                inv.setItem(stopSlots[i], new ItemBuilder(Material.GOLD_BLOCK)
                        .name("§e#" + index + " §a" + name)
                        .lore(plugin.getLanguageManager().getList(player, "gui.line_detail.stop_lore"))
                        .build());
                holder.set("stop_" + stopSlots[i], stopId);
            }
        }

        inv.setItem(GuiSlots.CREATE, new ItemBuilder(Material.LIME_DYE)
                .name(msg(player, "gui.line_detail.add_stop"))
                .lore(plugin.getLanguageManager().getList(player, "gui.line_detail.add_stop_lore"))
                .build());

        if (stopPage > 0) inv.setItem(GuiSlots.PREV_PAGE, new ItemBuilder(Material.ARROW).name("§e◀").build());
        if (start + perPage < stopIds.size()) inv.setItem(GuiSlots.NEXT_PAGE, new ItemBuilder(Material.ARROW).name("§e▶").build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    public void openLineAddStop(Player player, String lineId, int page) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;

        GuiHolder holder = new GuiHolder(GuiHolder.Type.LINE_ADD_STOP);
        holder.set("line_id", lineId);
        holder.set("page", String.valueOf(page));
        Inventory inv = Bukkit.createInventory(holder, 54,
                msg(player, "gui.add_stop.title", safePh(Map.of("line", line.getDisplayName()))));
        holder.setInventory(inv);

        List<Stop> stops = new ArrayList<>(plugin.getStopManager().getAllStops());
        int perPage = 28, start = page * perPage;
        int slot = 10;

        for (int i = start; i < Math.min(start + perPage, stops.size()); i++) {
            Stop stop = stops.get(i);
            if (slot % 9 == 8) slot += 2;
            boolean inLine = line.getStopIds().contains(stop.getId());
            inv.setItem(slot, new ItemBuilder(inLine ? Material.BARRIER : Material.GOLD_BLOCK)
                    .name((inLine ? "§7" : "§a") + stop.getDisplayName())
                    .lore(inLine
                            ? List.of(msg(player, "gui.add_stop.in_line"))
                            : List.of(msg(player, "gui.add_stop.click")))
                    .build());
            if (!inLine) holder.set("stop_" + slot, stop.getId());
            slot++;
        }

        if (stops.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg(player, "gui.add_stop.empty"))
                    .lore(plugin.getLanguageManager().getList(player, "gui.add_stop.empty_lore"))
                    .build());
        }

        if (page > 0) inv.setItem(GuiSlots.PREV_PAGE, new ItemBuilder(Material.ARROW).name("§e◀").build());
        if (start + perPage < stops.size()) inv.setItem(GuiSlots.NEXT_PAGE, new ItemBuilder(Material.ARROW).name("§e▶").build());

        fillBorder(inv);
        addBack(inv, player);
        player.openInventory(inv);
    }

    private List<String> buildBindReturnLore(Player player, int returnGoldCount) {
        List<String> lore = new ArrayList<>(plugin.getLanguageManager().getList(player, "gui.station_edit.bind_return_lore"));
        lore.add(msg(player, "gui.station_edit.bind_return_count", Map.of("count", String.valueOf(returnGoldCount))));
        return lore;
    }

    private List<String> buildPrevNextLore(Player player, String value, boolean fromLine, String lineName) {
        List<String> lore = new ArrayList<>();
        lore.add(msg(player, "gui.station_edit.info_value", safePh(Map.of("name", value))));
        if (fromLine) {
            lore.add(msg(player, "gui.station_edit.from_line", safePh(Map.of("line", lineName))));
        } else {
            lore.add(msg(player, "gui.station_edit.no_line_hint"));
        }
        lore.add("");
        lore.add(msg(player, "gui.station_edit.manage_lines_hint"));
        return lore;
    }
}
