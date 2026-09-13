package com.avery.atrain.update;

import com.avery.atrain.AtrainPlugin;
import com.avery.atrain.util.TextUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateService {

    public record UpdateCatalog(
            ReleaseInfo latestOfficial,
            ReleaseInfo latestBeta,
            ReleaseInfo currentVersionInfo,
            List<ReleaseInfo> allReleases
    ) {}

    private static final String REPO_OWNER = "Avery11111101";
    private static final String REPO_NAME = "atrain";
    private static final String API_URL = "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases?per_page=15";
    private static final long CACHE_TTL_MS = 3 * 60 * 1000L; // 3 分鐘快取

    private final AtrainPlugin plugin;
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private final AtomicBoolean isChecking = new AtomicBoolean(false);

    private volatile UpdateCatalog cachedCatalog = null;
    private volatile long lastFetchTime = 0L;

    public UpdateService(AtrainPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<UpdateCatalog> fetchReleasesAsync(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedCatalog != null && (now - lastFetchTime < CACHE_TTL_MS)) {
            return CompletableFuture.completedFuture(cachedCatalog);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                var url = URI.create(API_URL).toURL();
                var conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "atrain-Minecraft-Plugin/" + plugin.getPluginMeta().getVersion());
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().warning("無法抓取 GitHub Release API，HTTP 狀態碼: " + responseCode);
                    return cachedCatalog;
                }

                JsonArray array;
                try (var reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    array = JsonParser.parseReader(reader).getAsJsonArray();
                }

                List<ReleaseInfo> list = new ArrayList<>();
                ReleaseInfo firstOfficial = null;
                ReleaseInfo firstBeta = null;
                ReleaseInfo currentMatch = null;

                String currentVer = plugin.getPluginMeta().getVersion().trim().toLowerCase();
                String cleanCurrent = currentVer.startsWith("v") ? currentVer.substring(1) : currentVer;

                for (JsonElement elem : array) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();

                    boolean draft = obj.has("draft") && obj.get("draft").getAsBoolean();
                    if (draft) continue;

                    boolean prereleaseFromGh = obj.has("prerelease") && obj.get("prerelease").getAsBoolean();
                    String tagName = obj.has("tag_name") ? obj.get("tag_name").getAsString() : "";
                    String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : tagName;
                    String body = obj.has("body") && !obj.get("body").isJsonNull() ? obj.get("body").getAsString() : "";
                    String htmlUrl = obj.has("html_url") ? obj.get("html_url").getAsString() : "";
                    String publishedAt = obj.has("published_at") && !obj.get("published_at").isJsonNull()
                            ? obj.get("published_at").getAsString() : "";

                    // 判斷是否為預發布/測試版：GitHub 標記為 prerelease，或 tag/name 中包含 beta/alpha/rc/preview/dev/snapshot/pre
                    boolean isBetaOrPre = prereleaseFromGh
                            || tagName.toLowerCase().matches(".*[-._](beta|alpha|rc|pre|preview|snapshot|dev).*")
                            || name.toLowerCase().matches(".*[-._](beta|alpha|rc|pre|preview|snapshot|dev).*");

                    String downloadUrl = null;
                    String assetFileName = null;
                    if (obj.has("assets") && obj.get("assets").isJsonArray()) {
                        for (JsonElement a : obj.getAsJsonArray("assets")) {
                            if (a.isJsonObject()) {
                                var assetObj = a.getAsJsonObject();
                                String aname = assetObj.get("name").getAsString();
                                if (aname.endsWith(".jar")) {
                                    assetFileName = aname;
                                    downloadUrl = assetObj.get("browser_download_url").getAsString();
                                    break;
                                }
                            }
                        }
                    }

                    if (assetFileName == null && !tagName.isBlank()) {
                        String clean = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                        assetFileName = "atrain-" + clean + ".jar";
                    }

                    var release = new ReleaseInfo(tagName, name, body, htmlUrl, downloadUrl, assetFileName, isBetaOrPre, publishedAt);
                    list.add(release);

                    if (!isBetaOrPre && firstOfficial == null) {
                        firstOfficial = release;
                    }
                    if (isBetaOrPre && firstBeta == null) {
                        firstBeta = release;
                    }

                    String cleanTag = tagName.trim().toLowerCase();
                    if (cleanTag.startsWith("v")) cleanTag = cleanTag.substring(1);
                    if (cleanTag.equalsIgnoreCase(cleanCurrent) && currentMatch == null) {
                        currentMatch = release;
                    }
                }

                UpdateCatalog catalog = new UpdateCatalog(firstOfficial, firstBeta, currentMatch, Collections.unmodifiableList(list));
                this.cachedCatalog = catalog;
                this.lastFetchTime = System.currentTimeMillis();
                return catalog;
            } catch (Exception e) {
                plugin.getLogger().warning("抓取 GitHub Release API 時發生錯誤: " + e.getMessage());
                return cachedCatalog;
            }
        });
    }

    public CompletableFuture<Void> fetchReleasesAsync() {
        return fetchReleasesAsync(false).thenAccept(cat -> {});
    }

    public void displayVersionInfo(CommandSender sender) {
        String currentVer = plugin.getPluginMeta().getVersion();
        sender.sendMessage(TextUtil.colorize("<gold>======== [ atrain 版本與更新資訊 ] ========"));
        sender.sendMessage(TextUtil.colorize("<green>目前運行版本: <yellow>v" + currentVer));

        fetchReleasesAsync(false).thenAccept(catalog -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (catalog == null) {
                sender.sendMessage(TextUtil.colorize("<red>無法自 GitHub 取得版本資訊，請檢查網路連線。"));
                sender.sendMessage(TextUtil.colorize("<gold>=========================================="));
                return;
            }

            // 1. 顯示目前對應版本的詳細更新日誌
            if (catalog.currentVersionInfo() != null) {
                var info = catalog.currentVersionInfo();
                sender.sendMessage(TextUtil.colorize("<gray>發布標題: <white>" + info.name()));
                if (!info.publishedAt().isBlank()) {
                    sender.sendMessage(TextUtil.colorize("<gray>發布時間: <white>" + info.publishedAt().replace("T", " ").replace("Z", " UTC")));
                }
                sender.sendMessage(TextUtil.colorize("<gray>版本類型: " + (info.isPrerelease() ? "<aqua>[搶先測試版 🧪]" : "<green>[正式穩定版 🌟]")));
                sender.sendMessage(TextUtil.colorize("<gray>發布連結: <aqua>" + info.htmlUrl()));
                sender.sendMessage(TextUtil.colorize("<gold>----- 本版更新日誌 -----"));
                List<String> formattedNotes = MarkdownParser.parseMarkdownToMinecraft(info.body(), 15);
                for (String noteLine : formattedNotes) {
                    sender.sendMessage(noteLine);
                }
            } else {
                sender.sendMessage(TextUtil.colorize("<gray>(此版本為本地開發建置版，或尚未在 GitHub Releases 登錄)"));
            }

            // 2. 檢查最新線上版本
            boolean hasOfficialUpdate = catalog.latestOfficial() != null && isNewerVersion(currentVer, catalog.latestOfficial().tagName());
            boolean hasBetaUpdate = catalog.latestBeta() != null && isNewerVersion(currentVer, catalog.latestBeta().tagName());

            sender.sendMessage(TextUtil.colorize(""));
            if (catalog.latestOfficial() != null) {
                sender.sendMessage(TextUtil.colorize("<green>🌟 線上最新正式版: <yellow>" + catalog.latestOfficial().tagName() + " <gray>- " + catalog.latestOfficial().name()));
            }
            if (catalog.latestBeta() != null) {
                sender.sendMessage(TextUtil.colorize("<aqua>🧪 線上最新測試版: <yellow>" + catalog.latestBeta().tagName() + " <gray>- " + catalog.latestBeta().name()));
            }

            if (hasOfficialUpdate || hasBetaUpdate) {
                sender.sendMessage(TextUtil.colorize("<yellow>⚠️ 檢測到有更新版本可供升級！執行 <gold>/train update download [release|beta] <yellow>即可更新。"));
            } else {
                sender.sendMessage(TextUtil.colorize("<green>✅ 您的 atrain 插件已是最新版本！"));
            }
            sender.sendMessage(TextUtil.colorize("<gold>=========================================="));
        }));
    }

    public void checkUpdate(CommandSender sender) {
        if (isChecking.getAndSet(true)) {
            sender.sendMessage(TextUtil.colorize("<yellow>正在連線 GitHub 檢查更新中，請稍候..."));
            return;
        }

        sender.sendMessage(TextUtil.colorize("<yellow>正在檢查 GitHub 最新版本資訊..."));
        String currentVer = plugin.getPluginMeta().getVersion();

        fetchReleasesAsync(true).thenAccept(catalog -> {
            isChecking.set(false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (catalog == null) {
                    sender.sendMessage(TextUtil.colorize("<red>無法連線至 GitHub 取得更新資訊，請稍後再試。"));
                    return;
                }

                boolean hasReleaseUpdate = catalog.latestOfficial() != null && isNewerVersion(currentVer, catalog.latestOfficial().tagName());
                boolean hasBetaUpdate = catalog.latestBeta() != null && isNewerVersion(currentVer, catalog.latestBeta().tagName());

                sender.sendMessage(TextUtil.colorize("<gold>======== [ atrain 雙軌更新檢測 ] ========"));
                sender.sendMessage(TextUtil.colorize("<gray>目前安裝版本: <white>v" + currentVer));

                if (catalog.latestOfficial() != null) {
                    var off = catalog.latestOfficial();
                    if (hasReleaseUpdate) {
                        sender.sendMessage(TextUtil.colorize("<green>🌟 [最新正式穩定版] <gold>" + off.tagName() + " <gray>- <white>" + off.name()));
                        sender.sendMessage(TextUtil.colorize("  <yellow>👉 下載正式版: <gold>/train update download release"));
                    } else {
                        sender.sendMessage(TextUtil.colorize("<gray>🌟 [最新正式穩定版] " + off.tagName() + " <green>(目前已是最新正式版)"));
                    }
                }

                if (catalog.latestBeta() != null) {
                    var beta = catalog.latestBeta();
                    if (hasBetaUpdate) {
                        sender.sendMessage(TextUtil.colorize("<aqua>🧪 [最新搶先測試版] <gold>" + beta.tagName() + " <gray>- <white>" + beta.name()));
                        sender.sendMessage(TextUtil.colorize("  <yellow>👉 下載測試版: <gold>/train update download beta"));
                    } else {
                        sender.sendMessage(TextUtil.colorize("<gray>🧪 [最新搶先測試版] " + beta.tagName() + " <dark_gray>(目前版本已等於或高於此測試版)"));
                    }
                }

                if (!hasReleaseUpdate && !hasBetaUpdate) {
                    sender.sendMessage(TextUtil.colorize("<green>✔ 目前版本已是最新狀態，無需更新。"));
                }
                sender.sendMessage(TextUtil.colorize("<gold>=========================================="));
            });
        });
    }

    public void downloadUpdate(CommandSender sender, String track) {
        if (isDownloading.getAndSet(true)) {
            if (sender != null) sender.sendMessage(TextUtil.colorize("<yellow>檔案正在下載中，請稍候..."));
            return;
        }

        String trackDisplay = (track == null || track.isBlank() || "auto".equalsIgnoreCase(track) || "latest".equalsIgnoreCase(track)) ? "最新版" : track;
        if (sender != null) sender.sendMessage(TextUtil.colorize("<yellow>正在自 GitHub 準備下載更新檔 (" + trackDisplay + ")..."));

        fetchReleasesAsync(false).thenAccept(catalog -> {
            if (catalog == null) {
                isDownloading.set(false);
                if (sender != null) Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(TextUtil.colorize("<red>無法取得 Releases 清單，請確認網路連線。")));
                return;
            }

            ReleaseInfo target;
            if ("beta".equalsIgnoreCase(track)) {
                target = catalog.latestBeta() != null ? catalog.latestBeta() : catalog.latestOfficial();
            } else if ("release".equalsIgnoreCase(track) || "official".equalsIgnoreCase(track)) {
                target = catalog.latestOfficial() != null ? catalog.latestOfficial() : catalog.latestBeta();
            } else {
                // 自動模式：挑選兩軌中真正最新的版本
                if (catalog.latestOfficial() != null && catalog.latestBeta() != null) {
                    boolean betaIsNewer = isNewerVersion(catalog.latestOfficial().tagName(), catalog.latestBeta().tagName());
                    target = betaIsNewer ? catalog.latestBeta() : catalog.latestOfficial();
                } else {
                    target = catalog.latestOfficial() != null ? catalog.latestOfficial() : catalog.latestBeta();
                }
            }

            if (target == null || target.downloadUrl() == null || target.downloadUrl().isEmpty()) {
                isDownloading.set(false);
                if (sender != null) Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(TextUtil.colorize("<red>找不到可下載的 Jar 檔案。")));
                return;
            }

            String downloadUrl = target.downloadUrl();
            ReleaseInfo finalTarget = target;

            if (sender != null) Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(TextUtil.colorize("<yellow>開始下載 <gold>" + finalTarget.tagName() + " <yellow>更新檔 (" + finalTarget.fileName() + ")...")));

            try {
                var url = URI.create(downloadUrl).toURL();
                var conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "atrain-Minecraft-Plugin/" + plugin.getPluginMeta().getVersion());
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
                    conn.setRequestProperty("User-Agent", "atrain-Minecraft-Plugin/" + plugin.getPluginMeta().getVersion());
                }

                File pluginsFolder = plugin.getDataFolder().getParentFile();
                if (pluginsFolder == null || !pluginsFolder.exists()) {
                    throw new IllegalStateException("無法定位 plugins 目錄！");
                }

                String targetFileName = finalTarget.fileName();
                if (targetFileName == null || targetFileName.isBlank()) {
                    String cleanTag = finalTarget.tagName().startsWith("v") ? finalTarget.tagName().substring(1) : finalTarget.tagName();
                    targetFileName = "atrain-" + cleanTag + ".jar";
                }

                // 暫存下載檔
                File tempFile = new File(pluginsFolder, "." + targetFileName + ".part");
                if (tempFile.exists()) tempFile.delete();

                try (var in = new BufferedInputStream(conn.getInputStream());
                     var out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }

                if (!tempFile.exists() || tempFile.length() == 0) {
                    throw new IllegalStateException("下載檔案為空或寫入失敗！");
                }

                // 放置新版本至 /plugins
                File newJarFile = new File(pluginsFolder, targetFileName);
                Files.move(tempFile.toPath(), newJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // 處理舊版 Jar 檔案置換與移除
                File currentJar = plugin.getPluginFile();
                boolean oldDeleted = false;
                String oldJarName = (currentJar != null) ? currentJar.getName() : "舊版檔案";

                if (currentJar != null && currentJar.exists()) {
                    boolean isSameFile = false;
                    try {
                        isSameFile = currentJar.getCanonicalPath().equalsIgnoreCase(newJarFile.getCanonicalPath());
                    } catch (Exception ignored) {}

                    if (!isSameFile) {
                        // 嘗試直接刪除舊版檔案
                        try {
                            oldDeleted = currentJar.delete();
                        } catch (Exception ignored) {}

                        if (!oldDeleted) {
                            // Windows 常見檔案被佔用鎖定：嘗試重新命名排除 .jar 附檔名，以防重載載入雙版本
                            File backupFile = new File(pluginsFolder, currentJar.getName() + ".old");
                            if (backupFile.exists()) {
                                try { backupFile.delete(); } catch (Exception ignored) {}
                            }
                            boolean renamed = false;
                            try {
                                renamed = currentJar.renameTo(backupFile);
                            } catch (Exception ignored) {}

                            if (renamed) {
                                backupFile.deleteOnExit();
                                oldDeleted = true;
                            } else {
                                // 若無法重命名，標記於 JVM 關閉時刪除，並註冊待清理清單
                                currentJar.deleteOnExit();
                                plugin.registerPendingOldJar(currentJar);
                            }
                        }
                    } else {
                        oldDeleted = true; // 同檔名已原地覆蓋
                    }
                }

                boolean finalOldDeleted = oldDeleted;
                if (sender != null) Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(TextUtil.colorize("<green>✅ 下載完成！新版檔案已存放於: <gold>plugins/" + newJarFile.getName()));
                    if (finalOldDeleted) {
                        sender.sendMessage(TextUtil.colorize("<green>🗑️ 已成功置換並移除舊版本檔案: <gray>" + oldJarName));
                        sender.sendMessage(TextUtil.colorize("<yellow>💡 您可以直接執行 <gold>/plugman restart atrain <yellow>或重啟伺服器套用新版！"));
                    } else {
                        sender.sendMessage(TextUtil.colorize("<yellow>⚠️ 舊版檔案 <gray>" + oldJarName + " <yellow>目前被系統鎖定，已排程於關機/重載時自動清除。"));
                        sender.sendMessage(TextUtil.colorize("<yellow>💡 請重啟伺服器或使用 PlugMan 重載以完成置換！"));
                    }
                });

            } catch (Exception e) {
                if (sender != null) Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(TextUtil.colorize("<red>下載過程發生錯誤: " + e.getMessage())));
            } finally {
                isDownloading.set(false);
            }
        });
    }

    public static boolean isNewerVersion(String currentStr, String latestStr) {
        if (currentStr == null || latestStr == null) return false;

        String c = currentStr.trim().toLowerCase();
        String l = latestStr.trim().toLowerCase();

        if (c.startsWith("v")) c = c.substring(1);
        if (l.startsWith("v")) l = l.substring(1);

        if (c.equalsIgnoreCase(l)) return false;

        String[] cParts = c.split("-", 2);
        String[] lParts = l.split("-", 2);

        String[] cCore = cParts[0].split("\\.");
        String[] lCore = lParts[0].split("\\.");

        int maxLen = Math.max(cCore.length, lCore.length);
        for (int i = 0; i < maxLen; i++) {
            int cNum = 0;
            int lNum = 0;
            if (i < cCore.length) {
                try { cNum = Integer.parseInt(cCore[i]); } catch (NumberFormatException ignored) {}
            }
            if (i < lCore.length) {
                try { lNum = Integer.parseInt(lCore[i]); } catch (NumberFormatException ignored) {}
            }
            if (lNum > cNum) return true;
            if (lNum < cNum) return false;
        }

        boolean cHasPre = cParts.length > 1;
        boolean lHasPre = lParts.length > 1;

        if (cHasPre && !lHasPre) {
            return true; // 例如 current = 2.0.0-beta.1, latest = 2.0.0 -> 正式版較新
        }
        if (!cHasPre && lHasPre) {
            return false; // 例如 current = 2.0.0, latest = 2.0.0-beta.2 -> current 已是正式版
        }

        if (cHasPre && lHasPre) {
            return comparePreRelease(cParts[1], lParts[1]) < 0;
        }

        return false;
    }

    private static int comparePreRelease(String pre1, String pre2) {
        String[] p1 = pre1.split("\\.");
        String[] p2 = pre2.split("\\.");
        int len = Math.max(p1.length, p2.length);

        for (int i = 0; i < len; i++) {
            if (i >= p1.length) return -1;
            if (i >= p2.length) return 1;

            String s1 = p1[i];
            String s2 = p2[i];

            boolean s1IsNum = s1.matches("\\d+");
            boolean s2IsNum = s2.matches("\\d+");

            if (s1IsNum && s2IsNum) {
                int n1 = Integer.parseInt(s1);
                int n2 = Integer.parseInt(s2);
                if (n1 != n2) return Integer.compare(n1, n2);
            } else {
                int cmp = s1.compareToIgnoreCase(s2);
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }

    public ReleaseInfo getLatestRelease() {
        return cachedCatalog != null ? cachedCatalog.latestOfficial() : null;
    }

    public ReleaseInfo getLatestBeta() {
        return cachedCatalog != null ? cachedCatalog.latestBeta() : null;
    }
}

