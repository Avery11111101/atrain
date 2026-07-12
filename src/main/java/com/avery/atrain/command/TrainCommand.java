package com.avery.atrain.command;

import com.avery.atrain.AtrainPlugin;
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
            case "reload" -> {
                if (!sender.hasPermission("atrain.admin")) {
                    if (sender instanceof Player p) TextUtil.send(p, lang.get(p, "error.no_permission"));
                    return true;
                }
                plugin.reloadAll();
            }
            case "help" -> sendHelp(sender);
            default -> {
                if (sender instanceof Player p) TextUtil.send(p, lang.get(p, "command.unknown"));
            }
        }
        return true;
    }

    private void openGui(CommandSender sender, com.avery.atrain.i18n.LanguageManager lang) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize(lang.getRaw(lang.getDefaultLanguage(), "error.players_only")));
            return;
        }
        if (!p.hasPermission("atrain.gui")) {
            TextUtil.send(p, lang.get(p, "error.no_permission"));
            return;
        }
        plugin.getGuiManager().openMain(p);
    }

    private void sendHelp(CommandSender sender) {
        String langCode = sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p)
                : plugin.getLanguageManager().getDefaultLanguage();
        List<String> lines = Arrays.asList(
                plugin.getLanguageManager().getRaw(langCode, "command.help_header"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_gui"),
                plugin.getLanguageManager().getRaw(langCode, "command.help_stops"),
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
            return filter(List.of("gui", "stops", "reload", "help"), args[0]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
