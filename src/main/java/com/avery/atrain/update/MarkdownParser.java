package com.avery.atrain.update;

import java.util.ArrayList;
import java.util.List;

public class MarkdownParser {

    public static List<String> parseMarkdownToMinecraft(String markdown) {
        List<String> result = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }

        String[] lines = markdown.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("# ")) {
                result.add("§b§l=== " + trimmed.substring(2) + " ===");
            } else if (trimmed.startsWith("## ")) {
                result.add("§e§l[ " + trimmed.substring(3) + " ]");
            } else if (trimmed.startsWith("### ")) {
                result.add("§a§l▶ " + trimmed.substring(4));
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                String content = parseInline(trimmed.substring(2));
                result.add(" §7• §f" + content);
            } else {
                result.add(" §7" + parseInline(trimmed));
            }
        }
        return result;
    }

    private static String parseInline(String text) {
        String boldParsed = text.replaceAll("\\*\\*(.*?)\\*\\*", "§l$1§r§f");
        String codeParsed = boldParsed.replaceAll("`(.*?)`", "§e$1§f");
        return codeParsed;
    }
}
