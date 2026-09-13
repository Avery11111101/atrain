package com.avery.atrain.update;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.util.TextUtil;
import org.bukkit.Bukkit;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateService {

    private static final String REPO_OWNER = "Avery11111101";
    private static final String REPO_NAME = "atrain";
    private static final String API_URL = "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases";

    private final AtrainPlugin plugin;
    private final HttpClient httpClient;

    private ReleaseInfo latestRelease;
    private ReleaseInfo latestBeta;

    public UpdateService(AtrainPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public CompletableFuture<Void> fetchReleasesAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "atrain-Minecraft-Plugin")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    parseReleasesJson(response.body());
                } else {
                    plugin.getLogger().warning("無法抓取 GitHub Release API，HTTP 狀態碼: " + response.statusCode());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("抓取 GitHub Release API 時發生錯誤: " + e.getMessage());
            }
        });
    }

    private void parseReleasesJson(String json) {
        Pattern releasePattern = Pattern.compile("\\{[^{}]*\"tag_name\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}");
        Matcher matcher = releasePattern.matcher(json);

        latestRelease = null;
        latestBeta = null;

        while (matcher.find()) {
            String block = matcher.group(0);
            String tag = extractJsonValue(block, "tag_name");
            String name = extractJsonValue(block, "name");
            String body = extractJsonValue(block, "body");
            boolean prerelease = block.contains("\"prerelease\":true") || block.contains("\"prerelease\": true");
            String jarUrl = extractJarDownloadUrl(block);

            ReleaseInfo info = new ReleaseInfo(tag, name, body, prerelease, jarUrl);

            if (latestBeta == null && prerelease) {
                latestBeta = info;
            }
            if (latestRelease == null && !prerelease) {
                latestRelease = info;
            }

            if (latestRelease != null && latestBeta != null) {
                break;
            }
        }
    }

    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\\"", "\"");
        }
        return "";
    }

    private String extractJarDownloadUrl(String json) {
        Pattern p = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    public void displayVersionInfo(org.bukkit.command.CommandSender sender) {
        String currentVer = plugin.getPluginMeta().getVersion();
        sender.sendMessage(TextUtil.colorize("<gold>======== [ atrain 版本與更新資訊 ] ========"));
        sender.sendMessage(TextUtil.colorize("<green>目前運行版本: <yellow>v" + currentVer));

        fetchReleasesAsync().thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (latestRelease != null) {
                sender.sendMessage(TextUtil.colorize("<green>最新正式版 (Release 🌟): <yellow>" + latestRelease.tagName() + " <gray>- " + latestRelease.name()));
            }
            if (latestBeta != null) {
                sender.sendMessage(TextUtil.colorize("<aqua>最新測試版 (Beta 🧪): <yellow>" + latestBeta.tagName() + " <gray>- " + latestBeta.name()));
            }

            ReleaseInfo target = (latestRelease != null) ? latestRelease : latestBeta;
            if (target != null && isNewerVersion(currentVer, target.tagName())) {
                sender.sendMessage(TextUtil.colorize("<yellow>⚠️ 檢測到新版本可供更新！執行 <gold>/train update download <yellow>即可線上下載。"));
                sender.sendMessage(TextUtil.colorize("<gold>----- 更新日誌 -----"));
                List<String> formattedNotes = MarkdownParser.parseMarkdownToMinecraft(target.body());
                for (String noteLine : formattedNotes) {
                    sender.sendMessage(noteLine);
                }
            } else {
                sender.sendMessage(TextUtil.colorize("<green>✅ 您的 atrain 插件已是最新版本！"));
            }
            sender.sendMessage(TextUtil.colorize("<gold>=========================================="));
        }));
    }

    public void checkUpdate(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("<yellow>正在檢查 GitHub 最新版本資訊..."));
        String currentVer = plugin.getPluginMeta().getVersion();
        fetchReleasesAsync().thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            boolean hasReleaseUpdate = latestRelease != null && isNewerVersion(currentVer, latestRelease.tagName());
            boolean hasBetaUpdate = latestBeta != null && isNewerVersion(currentVer, latestBeta.tagName());

            if (!hasReleaseUpdate && !hasBetaUpdate) {
                sender.sendMessage(TextUtil.colorize("<green>目前版本 (v" + currentVer + ") 已是最新狀態，無需更新。"));
                return;
            }

            sender.sendMessage(TextUtil.colorize("<gold>=== 可用的更新項目 ==="));
            if (hasReleaseUpdate) {
                sender.sendMessage(TextUtil.colorize("<green>🌟 正式版: <gold>" + latestRelease.tagName() + " <white>- /train update download release"));
            }
            if (hasBetaUpdate) {
                sender.sendMessage(TextUtil.colorize("<aqua>🧪 測試版: <gold>" + latestBeta.tagName() + " <white>- /train update download beta"));
            }
        }));
    }

    public void downloadUpdate(org.bukkit.command.CommandSender sender, String track) {
        sender.sendMessage(TextUtil.colorize("<yellow>正在準備下載更新檔 (" + track + ")..."));

        fetchReleasesAsync().thenRun(() -> {
            ReleaseInfo target = "beta".equalsIgnoreCase(track) ? latestBeta : latestRelease;
            if (target == null) {
                target = latestRelease != null ? latestRelease : latestBeta;
            }

            if (target == null || target.downloadUrl().isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(TextUtil.colorize("<red>找不到可下載的 Jar 檔案。")));
                return;
            }

            String downloadUrl = target.downloadUrl();
            ReleaseInfo finalTarget = target;

            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(TextUtil.colorize("<yellow>開始下載 <gold>" + finalTarget.tagName() + " <yellow>檔...")));

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("User-Agent", "atrain-Minecraft-Plugin")
                        .GET()
                        .build();

                File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
                if (!updateFolder.exists()) {
                    updateFolder.mkdirs();
                }

                File targetJar = new File(updateFolder, plugin.getPluginFile().getName());


                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    try (InputStream in = response.body()) {
                        Files.copy(in, targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(TextUtil.colorize("<green>✅ 下載完成！更新檔已存放於: <gold>plugins/update/" + targetJar.getName()));
                        sender.sendMessage(TextUtil.colorize("<yellow>💡 請重啟伺服器，核心將會在開機時自動完成升級套用 (Windows Safe Update)。"));
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(TextUtil.colorize("<red>下載失敗，HTTP 狀態碼: " + response.statusCode())));
                }
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(TextUtil.colorize("<red>下載過程發生錯誤: " + e.getMessage())));
            }
        });
    }

    public boolean isNewerVersion(String current, String remote) {
        if (remote == null || remote.isBlank()) return false;
        String c = current.replaceAll("[^0-9.]", "");
        String r = remote.replaceAll("[^0-9.]", "");

        String[] cParts = c.split("\\.");
        String[] rParts = r.split("\\.");

        int length = Math.max(cParts.length, rParts.length);
        for (int i = 0; i < length; i++) {
            int cNum = i < cParts.length && !cParts[i].isEmpty() ? Integer.parseInt(cParts[i]) : 0;
            int rNum = i < rParts.length && !rParts[i].isEmpty() ? Integer.parseInt(rParts[i]) : 0;

            if (rNum > cNum) return true;
            if (cNum > rNum) return false;
        }
        return false;
    }

    public ReleaseInfo getLatestRelease() { return latestRelease; }
    public ReleaseInfo getLatestBeta() { return latestBeta; }
}
