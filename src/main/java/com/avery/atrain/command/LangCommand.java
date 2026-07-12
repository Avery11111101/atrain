package com.avery.atrain.command;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class LangCommand implements CommandExecutor, TabCompleter {

    private final AtrainPlugin plugin;

    public LangCommand(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize(
                    plugin.getLanguageManager().getRaw(plugin.getLanguageManager().getDefaultLanguage(), "error.players_only")));
            return true;
        }

        var lang = plugin.getLanguageManager();
        if (args.length == 0) {
            TextUtil.send(player, lang.get(player, "lang.usage"));
            TextUtil.send(player, lang.get(player, "lang.available",
                    Map.of("langs", String.join(", ", lang.getAvailableLanguages()))));
            return true;
        }

        String code = args[0];
        if (!lang.setPlayerLanguage(player, code)) {
            TextUtil.send(player, lang.get(player, "lang.not_found", Map.of("lang", code)));
            TextUtil.send(player, lang.get(player, "lang.available",
                    Map.of("langs", String.join(", ", lang.getAvailableLanguages()))));
            return true;
        }

        TextUtil.send(player, lang.get(player, "lang.changed", Map.of("lang", code)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return plugin.getLanguageManager().getAvailableLanguages().stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .toList();
        }
        return List.of();
    }
}
