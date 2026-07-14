# atrain — AI 上下文記憶庫

## 專案核心用意

Minecraft Paper 1.21+ 金磚站點與火車插件。管理員透過 `/train gui` 管理路線、站點順序與軌道錄製，供站點導引沿錄製軌跡行駛。

## 使用者決策動機

Avery 回報路線管理無法用說明的方式調整站點順序，以及軌道錄製邏輯有問題。要求：
- **操作方式與原本文件一致**（不需重設設定檔）
- 多輪子代理盤點 → 修復 → 驗證，連續兩輪無問題

## 路線詳情站點操作（最終 UX，與 lang 一致）

| 操作 | 行為 |
|------|------|
| 左鍵 | 傳送至站點 |
| 右鍵 | 站點編輯 |
| Shift+左鍵 | 上移一站 |
| Shift+右鍵 | 下移一站 |
| Q | 從路線移除 |

實作使用 `isPlainLeftClick` / `isShiftLeftClick` 等 helper，正確處理 Paper `isShiftClick()`。

## 歷史變更軌跡

### 2026-07-12 — 路線管理與錄製完整修復（雙輪驗證 PASS）

**第一輪問題盤點：**
- Shift+點擊排序在部分客戶端不穩定（需 `isShiftClick()` 輔助）
- 曾誤將左/右鍵改為排序（已還原）
- 錄製中下車礦車被刪、GUI 停止未實作、>12 段無法選、環線缺閉合段等

**修復摘要：**

1. `GuiListener` — 還原原始點擊綁定；錄製中鎖定路線結構編輯
2. `LineManager.moveStopInLine` — 回傳 boolean + 邊界提示
3. `EmptyCartListener` — 錄製礦車下車不刪除
4. `RouteRecordingManager` — 附近 Rideable 重用、過遠 detach 重召、`pendingRecordingCarts`、`stopAllForReload`
5. `RouteRecordingSession` — 延後覆寫至首次採樣、取消可還原備份、停靠下車暫停倒數、礦車遺失提示
6. `GuiManager` — 路段分頁（每頁 12）、錄製中兩分頁皆顯示停止/取消
7. `Line` — 環狀線多一段末站→首站
8. `PlayerInteractListener` — 錄製中蹲下右鍵仍召車
9. `AtrainPlugin.reloadAll` — 先存錄製再 reload

**驗證：** 兩輪獨立子代理（盤點 / 驗證分離），第二輪重驗 8/8 PASS、0 BLOCKER、0 MAJOR。

### 2026-07-13 — 修復進站/過場時，因軌道密化取樣導致的無限迴圈/主執行緒卡死

**修改原因：**
- 發生於 `CinematicTransitTask.beginMove` 觸發 `RailPathSampler` 計算站點間路徑時。
- 若站點距離過遠且未記錄 `RoutePoint` 軌跡，`bfsAlongRails` 等搜尋會 fallback 為一直線飛行（例如跨越數千格）。
- 在 `densifyAndSnap` 時，原先的邏輯會將直線以 0.35 格的間距插值出數萬個節點，每個節點又會呼叫 `snapToRail` -> `RailUtil.findNearestRailBlock` 進行鐵軌搜尋（每點 81 個 block）。
- 當節點落入未載入區塊時，`w.getBlockAt` 會觸發同步加載區塊（Synchronous Chunk Load），導致百萬次的 `getBlockAt` 與大量區塊載入癱瘓伺服器主執行緒，被 Server Watchdog 判定為 Infinite Loop 於第 69 行中止並報錯。

**修復摘要：**
1. `RailPathSampler.snapToRail`：新增區塊是否已加載的判斷 `!loc.getWorld().isChunkLoaded(chunkX, chunkZ)`，如果在未加載區塊，則直接回傳原坐標，不執行 `findNearestRailBlock`，完全根絕了因同步載入導致的主執行緒凍結卡死。
2. `RailPathSampler.densifyAndSnap`：加入 `steps` 上限限制。當距離極遠時（如跨越數千格），步數強制上限為 1000，大幅減少記憶體佔用與重複運算。

### 2026-07-13 — 修復 BlockCoords 打包位移時的符號擴展 (Sign Extension) 問題

**修改原因：**
- 在先前的伺服器崩潰日誌與 `2026-07-12-6.log` 中，發現伺服器會拋出 `java.lang.IllegalStateException: Trying to create chunk out of reasonable bounds: [4193697, 80]` 的嚴重例外，並伴隨 `CinematicTransitTask.tick` 在執行 `cart.teleport(pos)` 時當掉。
- `4193697` 的 chunk X 轉換為方塊 X 座標高達 `67,099,152`，遠超 Minecraft 世界邊界（三千萬格）。
- 經查 `BlockCoords.java` 中的 `unpackX` 實作：`(int) ((packed >> 38) & 0x3FFFFFFL);` 以及 `unpackZ`：`(int) (packed & 0x3FFFFFFL);`。
- 由於使用了位元及 `& 0x3FFFFFFL` 運算，導致如果原本存入的 `x` 或 `z` 座標為負數，解包時其負號位元（sign bit）會被強制抹除，使其成為一個介於 `67108863` 的超大正數，進而使礦車傳送到無效的世界座標。

**修復摘要：**
1. `BlockCoords.unpackX`：移除不必要的 `& 0x3FFFFFFL`，直接回傳 `(int) (packed >> 38)`。由於 `long` 在 `>> 38` 時會自動進行算術位移（Arithmetic Shift）保留負號，轉成 `int` 時即可正確還原負數 X 座標。
2. `BlockCoords.unpackZ`：修改為 `((int) packed << 6) >> 6`。先擷取後段 32 bits，左移 6 bits 將資料推至頂端對齊 sign bit，再透過算術右移 `>> 6`，正確還原 Z 的負數值並過濾掉上方的 Y 座標資料。

### 2026-07-13 — 升級過時 API 以相容 Paper 26.2 (1.26.2)

**修改原因：**
- 為了確保插件能在 Paper 26.2 上順利運作，排除了所有的「棄用 API 警告 (Deprecated API)」。
- Paper 1.21+ 全面推行 Adventure API (`Component`)，舊版的字串介面標題與物品名稱已被標註為過時。

**修復摘要：**
1. **Inventory 介面標題**：將所有 `Bukkit.createInventory(holder, size, String)` 替換為 `Bukkit.createInventory(holder, size, TextUtil.component(String))`。
2. **ItemMeta 物品名稱與 Lore**：將 `ItemBuilder` 內的 `setDisplayName` 和 `setLore` 替換為 `displayName(Component)` 與 `lore(List<Component>)`。
3. **Plugin Meta**：將過時的 `getDescription().getVersion()` 替換為 26.2 建議的 `getPluginMeta().getVersion()`。

### 2026-07-13 — 修復終點站依然可以自由發車的漏洞

**修改原因：**
- Avery 回報在終點站上車時依然可以開動列車。
- 經查，原有的 `CinematicTransitManager.onBoard` 在判定為終點站時，會回傳 `false` 給事件監聽器。這導致系統誤判導引模式「沒有接管」此次上車行為，進而觸發後續的自由控速模式 (`TrainController`)，使玩家能以物理方式把礦車開走。

- 修改 `CinematicTransitManager`：在終點站判定成立時，除了傳送提示訊息，還會透過 Scheduler 排程 `cart.removePassenger(player)` 將玩家踢下車，同時改為回傳 `true`，成功攔截該次事件並阻止進入自由控速模式。

### 2026-07-13 — 修復玩家手動放置礦車會吃到插件特性的問題

**修改原因：**
- Avery 回報自己放置的普通礦車（非透過插件指令或站點召喚），也會出現插件的特性，例如懸浮軌道、進站自動煞車、以及空車自動清除等問題。
- 經查，原有的 `HangRailTask`、`StationAutoStopListener` 與 `EmptyCartListener` 等監聽器，單純只判斷了實體是否為 `Minecart`，沒有進一步區分該礦車是「系統召喚」還是「玩家手動放的」。

**修復摘要：**
1. **建立專屬標籤**：在 `AtrainPlugin` 中新增 `NamespacedKey` (`managed_cart`) 作為 PDC 標籤，並加入 `markAsManagedCart` 與 `isManagedCart` 方法。
2. **生成時打標**：在 `CartSpawnManager` 與 `RouteRecordingManager` 生成礦車時，一律打上 `managed_cart` 標籤。
3. **過濾非專屬礦車**：在 `HangRailTask`、`StationAutoStopListener`、`EmptyCartListener` 與 `VehicleListener` 中新增安全檢查 `if (!plugin.isManagedCart(cart)) return;`，將未打標的礦車排除在插件影響之外，保留原版機制。
4. **修復玩家右鍵攔截**：修改 `PlayerInteractListener`，若玩家手持礦車對站點鐵軌右鍵時，不再攔截並替換為插件礦車，而是讓原版遊戲正常放置普通礦車。
5. **修復全域列車接管**：修改 `TrainController.tick()`，原本它會無差別掃描全世界所有在鐵軌上的礦車並強迫套用巡航速度，現已補上 `!plugin.isManagedCart(cart)` 過濾，徹底解除對原版礦車的物理引擎干涉。

### 2026-07-14 — 多代理自動修復與 UX 優化 (Finder/Fixer/Verifier)

**修改原因：**
- 啟動全域掃描尋找潛在的邏輯與使用者體驗 (UX) 問題。
- 發現包含軌段無預警清空、合併站點崩潰、全域掃描效能低落、GUI 同步存檔卡頓，以及重載廣播過度擾民等五大問題。

**修復摘要：**
1. **LineManager 站點排序**：在變更站點順序時，加回 `clearAllSegments()` 確保邏輯安全，並新增管理員警告廣播，避免使用者不知情下軌跡被清空。
2. **StopManager 站點合併**：若站點合併導致路線陣列改變，同步清空該路線的錄製軌跡並廣播，防止舊軌跡對應到錯誤索引造成崩潰。
3. **TrainController 效能最佳化**：建立 `AtrainPlugin.activeManagedCarts` 快取清單並由 `VehicleListener` 事件維護。取代了每 Tick 的全域實體掃描，大幅降低 TPS 負載。
4. **GUI 存檔線程安全**：將站點/路線設定的 `plugin.getDataStore().save()` 還原為主執行緒同步執行，解決了封裝在非同步執行時可能引發的 `ConcurrentModificationException` 與存檔損壞風險。
5. **重載廣播優化**：`/train reload` 的成功訊息改為只發送給擁有 `atrain.admin` 權限的在線玩家以及後台 Console，減少對一般玩家的打擾。

**驗證：**
經過兩輪獨立子代理 (Verifier 1 & Verifier 2) 雙重覆核，已確認所有修改邏輯正確、無 NPE 或 CME 風險。版本升級為 1.5.5。

### 2026-07-13 — 站點管理介面大改版與自動錄製功能 (多選重點站、排序分離、自動取徑)

**修改原因：**
- Avery 回報使用「左右鍵點擊」去控制站點上下順序會與「傳送/管理」功能衝突，要求將排序功能獨立成專屬介面。
- 重點站設定需求變更，需要支援多選，並且站點之間要能雙向互聯 (A 加入 B 為重點站，B 也會自動把 A 設為重點站)。
- 手動錄製路線時，如果是直線段，跑礦車非常沒效率，希望能有兩點自動取徑的功能，並保留手動錄製的選項。
- 管理員訊息希望能有簡單的刪除方式。

**修復摘要：**
1. **獨立站點排序介面**：在「路線詳情」中，將調整站點順序的功能從「Shift + 點擊」移出，新增專屬的「調整站點順序」按鈕與 `LineReorder` GUI，使用左右鍵控制上移與下移。
2. **多選重點站與互聯**：
   - 資料模型 (`Stop.java` / `DataStore.java`)：將 `keyStation` 字串欄位升級為 `Set<String> keyStations`，支援同時綁定多個重點站，並確保向後相容讀取舊格式。
   - 介面與邏輯 (`GuiListener.java` / `GuiManager.java`)：新增專屬的 `KeyStationSelect` GUI。點擊站點可切換綁定狀態，且實作了雙向綁定邏輯 (A 與 B 同步新增/移除)。
3. **自動直線錄製**：
   - 在「路段錄製」點擊後，先跳出 `RecordModeSelect` 模式選擇 GUI，讓玩家選擇「手動錄製」或「自動直線取徑」。
   - `RouteRecordingManager.java`：新增 `autoRecordSegment`，自動呼叫 `RailPathSampler.betweenStops` 循著實際鐵軌方塊執行 BFS 取徑，並直接寫入記憶體與存檔，達到瞬間完成免跑礦車。
4. **管理員訊息刪除**：在 `StationEdit` 介面中，右鍵點擊管理員訊息的圖示 (Slot 38) 即可快速清空該站的管理員訊息。
5. **動態重點站去回程顯示 (移至聊天欄)**：將原本擠在 ActionBar 的重點站轉乘資訊移除，改為在玩家「即將進站 (Approaching)」時，發送至聊天欄。顯示格式為 `本站可轉乘 {路線名稱}線`，下一行動態顯示 `➔ {重點站名} (去) => {下一站} / (回) => {上一站}`，若是終點站則只會顯示單向。
6. **編譯錯誤與語系修復**：修正相關類別呼叫舊版 `getKeyStation()` 產生的編譯錯誤，並在 `zh_TW.yml` 補齊所有新增的 GUI 顯示文字與 lore 說明。維持外掛版本號不變。
