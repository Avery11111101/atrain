package com.avery.atrain.command;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.model.Line;
import com.avery.atrain.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TrainCommand implements CommandExecutor, TabCompleter {

    private final AtrainPlugin plugin;

    public TrainCommand(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var lang = plugin.getLanguageManager();

        if (args.length == 0) {
            if (sender instanceof Player p) {
                if (!p.hasPermission("atrain.gui")) {
                    TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                plugin.getGuiManager().openMain(p);
            } else {
                sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "gui" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
                    return true;
                }
                if (!p.hasPermission("atrain.gui")) {
                    TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                plugin.getGuiManager().openMain(p);
            }
            case "reload" -> {
                if (!sender.hasPermission("atrain.admin")) {
                    if (sender instanceof Player p) TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                plugin.reloadAll();
                if (!(sender instanceof Player)) {
                    sender.sendMessage("atrain reloaded.");
                }
            }
            case "record" -> {
                if (!(sender instanceof Player p)) return true;
                if (!p.hasPermission("atrain.create")) {
                    TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    TextUtil.send(p, lang.get(p, "command.record_usage"));
                    return true;
                }
                String lineId = args[1];
                if (plugin.getLineManager().getLine(lineId) == null) {
                    TextUtil.send(p, lang.get(p, "line.not_found", Map.of("id", lineId)));
                    return true;
                }
                if (!p.isInsideVehicle()) {
                    TextUtil.send(p, lang.get(p, "route.need_cart"));
                    return true;
                }
                if (plugin.getRouteRecorder().isRecording(p)) {
                    String current = plugin.getRouteRecorder().getRecordingLine(p);
                    if (lineId.equals(current)) {
                        plugin.getRouteRecorder().stopRecording(p);
                        TextUtil.send(p, lang.get(p, "route.recording_stop"));
                        return true;
                    }
                    TextUtil.send(p, lang.get(p, "route.recording_other_line",
                            Map.of("line", current != null ? current : "?")));
                    return true;
                }
                if (plugin.getRouteRecorder().getRoutePointCount(lineId) > 0) {
                    TextUtil.send(p, lang.get(p, "route.confirm_overwrite_cmd",
                            Map.of("count", String.valueOf(plugin.getRouteRecorder().getRoutePointCount(lineId)))));
                    TextUtil.send(p, lang.get(p, "route.use_gui_confirm"));
                    return true;
                }
                plugin.getRouteRecorder().startRecording(p, lineId);
                Line line = plugin.getLineManager().getLine(lineId);
                String lineName = line != null ? line.getDisplayName() : lineId;
                TextUtil.send(p, lang.get(p, "route.recording_start", Map.of("line", lineName)));
            }
            case "stoprecord" -> {
                if (!(sender instanceof Player p)) return true;
                if (!plugin.getRouteRecorder().isRecording(p)) {
                    TextUtil.send(p, lang.get(p, "route.not_recording"));
                    return true;
                }
                plugin.getRouteRecorder().stopRecording(p);
                TextUtil.send(p, lang.get(p, "route.recording_stop"));
            }
            case "lines", "stops" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
                    return true;
                }
                if (!p.hasPermission("atrain.gui")) {
                    TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                if ("lines".equals(sub)) plugin.getGuiManager().openLineList(p, 0);
                else plugin.getGuiManager().openStopList(p, 0);
            }
            case "line" -> handleLine(sender, args);
            case "help" -> sendHelp(sender);
            default -> {
                if (sender instanceof Player p) {
                    TextUtil.send(p, lang.get(p, "command.unknown"));
                }
            }
        }
        return true;
    }

    private void handleLine(CommandSender sender, String[] args) {
        var lang = plugin.getLanguageManager();
        if (args.length < 2) {
            if (sender instanceof Player p) {
                TextUtil.send(p, lang.get(p, "command.line_usage"));
            } else {
                sender.sendMessage("/train line addstop|clearroute <路線ID> [站點ID]");
            }
            return;
        }
        if (!sender.hasPermission("atrain.create")) {
            if (sender instanceof Player p) {
                TextUtil.send(p, lang.get(p, "error.no_permission"));
            }
            return;
        }
        String sub = args[1].toLowerCase();
        if ("create".equals(sub)) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
                return;
            }
            String id = "line_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            if (!plugin.getLineManager().createLine(id, lang.get(p, "line.default_name", Map.of("id", id)))) {
                TextUtil.send(p, lang.get(p, "line.create_failed"));
                return;
            }
            TextUtil.send(p, lang.get(p, "line.created", Map.of("id", id)));
            plugin.getGuiManager().openLineDetail(p, id);
            return;
        }
        if ("addstop".equals(sub)) {
            if (args.length < 4) {
                if (sender instanceof Player p) {
                    TextUtil.send(p, lang.get(p, "command.addstop_usage"));
                } else {
                    sender.sendMessage("/train line addstop <lineID> <stopID>");
                }
                return;
            }
            String lineId = args[2], stopId = args[3];
            if (plugin.getLineManager().getLine(lineId) == null) {
                if (sender instanceof Player p) {
                    TextUtil.send(p, lang.get(p, "line.not_found", Map.of("id", lineId)));
                } else {
                    sender.sendMessage("Line not found: " + lineId);
                }
                return;
            }
            if (plugin.getStopManager().getStop(stopId) == null) {
                if (sender instanceof Player p) {
                    TextUtil.send(p, lang.get(p, "stop.not_found", Map.of("id", stopId)));
                } else {
                    sender.sendMessage("Stop not found: " + stopId);
                }
                return;
            }
            plugin.getLineManager().addStopToLine(lineId, stopId, -1);
            if (sender instanceof Player p) {
                TextUtil.send(p, lang.get(p, "line.stop_added", Map.of("stop", stopId, "line", lineId)));
            } else {
                sender.sendMessage("Added " + stopId + " to " + lineId);
            }
        } else if ("clearroute".equals(sub)) {
            if (args.length < 3) {
                if (sender instanceof Player p) {
                    TextUtil.send(p, lang.get(p, "command.clearroute_usage"));
                } else {
                    sender.sendMessage("/train line clearroute <lineID>");
                }
                return;
            }
            String lineId = args[2];
            if (plugin.getLineManager().getLine(lineId) == null) {
                if (sender instanceof Player p) {
                    TextUtil.send(p, lang.get(p, "line.not_found", Map.of("id", lineId)));
                } else {
                    sender.sendMessage("Line not found: " + lineId);
                }
                return;
            }
            int cleared = plugin.getRouteRecorder().clearRoute(lineId);
            if (sender instanceof Player p) {
                TextUtil.send(p, lang.get(p, "route.cleared", Map.of("line", lineId, "count", String.valueOf(cleared))));
            } else {
                sender.sendMessage("Cleared " + cleared + " route points from " + lineId);
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        String langCode = sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p)
                : plugin.getLanguageManager().getDefaultLanguage();
        List<String> lines = Arrays.asList(
                plugin.getLanguageManager().getRaw(langCode, "command.help_header"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_gui"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_lines"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_stops"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_line_create"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_record"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_addstop"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_clearroute"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_lang"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_reload")
        );
        for (String line : lines) {
            sender.sendMessage(TextUtil.colorize(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("gui", "reload", "record", "stoprecord", "line", "lines", "stops", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("record")) {
            return filterLineIds(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("line")) {
            return filter(List.of("addstop", "clearroute", "create"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("line") && args[1].equalsIgnoreCase("clearroute")) {
            return filterLineIds(args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("line") && args[1].equalsIgnoreCase("addstop")) {
            return filterLineIds(args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("line") && args[1].equalsIgnoreCase("addstop")) {
            List<String> ids = new ArrayList<>();
            plugin.getStopManager().getAllStops().forEach(s -> ids.add(s.getId()));
            return filter(ids, args[3]);
        }
        return List.of();
    }

    private List<String> filterLineIds(String input) {
        List<String> ids = new ArrayList<>();
        plugin.getLineManager().getAllLines().forEach(l -> ids.add(l.getId()));
        return filter(ids, input);
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
