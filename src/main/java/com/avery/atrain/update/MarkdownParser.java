package com.avery.atrain.update;

import java.util.ArrayList;
import java.util.List;

public class MarkdownParser {

    public static List<String> parseMarkdownToMinecraft(String markdown) {
        return parseMarkdownToMinecraft(markdown, 15);
    }

    public static List<String> parseMarkdownToMinecraft(String markdown, int maxLines) {
        List<String> output = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return List.of("§7  (無更新說明)");
        }

        String[] rawLines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int count = 0;

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("```") || trimmed.startsWith("---") || trimmed.startsWith("___")) {
                continue;
            }

            // 過濾 HTML 標籤
            trimmed = trimmed.replaceAll("<[^>]*>", "");
            if (trimmed.isBlank()) continue;

            // 粗體轉換 **text** -> §e§ltext§r§7
            trimmed = trimmed.replaceAll("\\*\\*([^*]+)\\*\\*", "§e§l$1§r§7");
            // code 轉換 `code` -> §f§ncode§r§7
            trimmed = trimmed.replaceAll("`([^`]+)`", "§f$1§7");

            if (trimmed.startsWith("# ")) {
                output.add("§6§l=== " + trimmed.substring(2) + " ===");
            } else if (trimmed.startsWith("## ")) {
                output.add("§e§l▸ " + trimmed.substring(3));
            } else if (trimmed.startsWith("### ")) {
                output.add("§b§l  • " + trimmed.substring(4));
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                output.add("§7    • §f" + trimmed.substring(2));
            } else {
                output.add("§7    " + trimmed);
            }

            count++;
            if (count >= maxLines) {
                output.add("§8    ... (更多詳細資訊請至 GitHub Releases 查閱)");
                break;
            }
        }

        return output.isEmpty() ? List.of("§7  (無更新說明)") : output;
    }
}

