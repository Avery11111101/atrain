package com.avery.atrain.command;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.util.SpeedBlockInteract;
import com.avery.atrain.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class TrainCommand implements CommandExecutor, TabCompleter {

    private final AtrainPlugin plugin;

    public TrainCommand(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var lang = plugin.getLanguageManager();

        if (args.length == 0) {
            openGui(sender, lang);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> openGui(sender, lang);
            case "stops" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
                    return true;
                }
                if (!p.hasPermission("atrain.gui")) {
                    TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                plugin.getGuiManager().openStopList(p, 0);
            }
            case "lines" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
                    return true;
                }
                if (!p.hasPermission("atrain.gui")) {
                    TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                plugin.getGuiManager().openLineList(p, 0);
            }
            case "reload" -> {
                if (!sender.hasPermission("atrain.admin")) {
                    if (sender instanceof Player p) TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                plugin.reloadAll();
            }
            case "speed" -> openSpeedBlock(sender, lang);
            case "version" -> plugin.getUpdateService().displayVersionInfo(sender);
            case "update" -> handleUpdateCommand(sender, args);
            case "help" -> sendHelp(sender);
            default -> {
                if (sender instanceof Player p) TextUtil.send(p, lang.get(p, "command.unknown"));
            }
        }
        return true;
    }

    private void handleUpdateCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("atrain.admin")) {
            if (sender instanceof Player p) TextUtil.send(p, plugin.getLanguageManager().get(p, "error.no_permission"));
            return;
        }

        if (args.length <= 1 || "check".equalsIgnoreCase(args[1])) {
            plugin.getUpdateService().checkUpdate(sender);
        } else if ("download".equalsIgnoreCase(args[1])) {
            String track = args.length > 2 ? args[2] : "auto";
            plugin.getUpdateService().downloadUpdate(sender, track);
        } else {
            sender.sendMessage(TextUtil.colorize("<yellow>用法: /train update [check|download] [release|beta|auto]"));
        }
    }

    private void openGui(CommandSender sender, com.avery.atrain.i18n.LanguageManager lang) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
            return;
        }
        if (p.hasPermission("atrain.admin")) {
            plugin.getGuiManager().openMain(p);
        } else {
            // 所有玩家打 /tr 預設均可開啟站點導覽選單（除非被權限插件顯式設為 false）
            if (p.isPermissionSet("atrain.user") && !p.hasPermission("atrain.user")) {
                TextUtil.send(p, lang.get(p, "error.no_permission"));
                return;
            }
            plugin.getGuiManager().openGuideMain(p);
        }
    }

    /** 對準調速方塊開啟編輯 GUI（右鍵無反應時的後備方式） */
    private void openSpeedBlock(CommandSender sender, com.avery.atrain.i18n.LanguageManager lang) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
            return;
        }
        var target = SpeedBlockInteract.rayTarget(p, 6);
        if (target == null) {
            TextUtil.send(p, lang.get(p, "speed_block.look_at_block"));
            return;
        }
        if (!SpeedBlockInteract.tryOpenEditor(plugin, p, target)) {
            TextUtil.send(p, lang.get(p, "speed_block.not_target"));
        }
    }

    private void sendHelp(CommandSender sender) {
        String langCode = sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p)
                : plugin.getLanguageManager().getDefaultLanguage();
        List<String> lines = Arrays.asList(
                plugin.getLanguageManager().getRaw(langCode, "command.help_header"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_gui"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_stops"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_lines"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_speed"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_version"),
                "<gold>/train update <white>- 檢查與下載最新插件版本 (Release/Beta)",
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
            return filter(List.of("gui", "stops", "lines", "speed", "version", "update", "reload", "help"), args[0]);
        } else if (args.length == 2 && "update".equalsIgnoreCase(args[0])) {
            return filter(List.of("check", "download"), args[1]);
        } else if (args.length == 3 && "update".equalsIgnoreCase(args[0]) && "download".equalsIgnoreCase(args[1])) {
            return filter(List.of("release", "beta"), args[2]);
        }
        return List.of();
    }


    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
