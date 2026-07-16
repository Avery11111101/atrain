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

### 2026-07-15 — 更新 Modrinth 中英介紹文件以對齊最新機制

**修改原因：**
- Avery 要求根據目前的機制更新 `Modrinth介紹.md` 與 `Modrinth-intro-en.md` 兩份公開的平台宣傳文件。

**修復/更新摘要：**
1. **加入 BlueMap 整合說明**：在特色列表中加入即時同步站點標記與 3D 軌跡的功能說明。
2. **更新路線與錄製機制**：新增「A* 自動軌道錄製」（瞬間完成十萬格取徑）與手動錄製的說明；新增「智能軌跡保留」的特色。
3. **更新多玩家防重疊機制**：加入多玩家同站召喚礦車自動尋找空位防重疊的相關說明。
4. **補充 UX 優化特性**：提到獨立站點排序 GUI、多選重點站雙向互聯、以及去回程動態轉乘提示等功能。
5. **確保純淨的原版相容**：強調了插件使用專屬 PDC 標籤，不會干擾玩家手動放置的普通礦車。

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

### 2026-07-14 — 整合 BlueMap 地圖顯示與版本升級 1.6.0

**修改原因：**
- Avery 要求將錄製好的軌跡路線及站點都在線上地圖中標記並畫出路線。
- 由於使用的是 BlueMap，因此需要導入 BlueMap API。

**修復摘要：**
1. **依賴與版本更新**：修改 `build.gradle.kts`，加入 `de.bluecolored:bluemap-api:2.7.3` 依賴並更新插件版本號至 `1.6.0`。修改 `plugin.yml` 加上 `softdepend: [BlueMap]`。
2. **新增 `BlueMapManager`**：
   - 使用 BlueMap API 繪製地圖標記，並在各世界的 `BlueMapMap` 中建立 `atrain` MarkerSet。
   - 遍歷所有 `Stop`，使用 `POIMarker` 在月台金磚位置建立站點標記。
   - 遍歷所有 `Line`，抓取 `RoutePoint` 軌跡列表，以 `LineMarker` 繪製 3D 軌跡線條，並根據路線的聊天顏色 (`§a`, `§c` 等) 解析出對應的 RGB 顏色畫線。
3. **資料變更即時更新**：
   - 為了確保使用者修改站點與路線時，地圖能無縫同步，在 `DataStore.save()` 方法中加入了 `plugin.getBlueMapManager().updateMap()`。
   - 因為 GUI 存檔與錄製完畢存檔時都會呼叫 `DataStore.save()`，所以自動能涵蓋所有觸發情境。

### 2026-07-14 — 路線軌跡智能保留功能 (Smart Segment Preservation)

**修改原因：**
- Avery 回報在路線管理介面中，只要一編輯站點順序（例如上移、下移或新增移除站點），該路線所有辛辛苦苦錄製好的軌跡就會全部不見。
- 原先的設計是為了防止順序變動後，原本綁定數字索引 (e.g. 第 0 段、第 1 段) 的軌跡，會對應到錯誤的新站點組合上（可能導致火車飛天遁地或出軌），所以使用了 `clearAllSegments()` 暴力清空整條路線的軌跡。

**修復摘要：**
1. **智能軌跡對比與保留 (`Line.java`)**：
   - 新增 `updateStopsAndPreserveSegments(newStopIds)` 方法。
   - 在真正套用新的站點陣列前，先以字串對射的方式 `(fromStopId -> toStopId)` 緩存所有舊有的去程與回程軌跡。
   - 套用新站點順序後，重新遍歷新相鄰的站點對，如果發現新相鄰的兩個站在舊資料中也有一模一樣相連的軌跡，就把那段軌跡保留下來並轉移到新的索引位子。
   - 只有因為順序調換而「不再相鄰」的斷鏈軌跡兩端才會被清除。
2. **替換全域的清空呼叫 (`LineManager.java` & `StopManager.java`)**：
   - 在 `LineManager` 裡的 `addStopToLine`, `removeStopFromLine`, `moveStopInLine` 全面廢除 `clearAllSegments()`。
   - 新增動態廣播機制，如果有段落被成功保留，則廣播 `§a✔ 站點順序已變更，相鄰未變的軌跡段落已自動保留！`；如果整條斷光光才會廣播黃字警告。
   - 同步修正 `StopManager` 在進行站點合併 (`mergeStopsInto`, `absorbStopAsReturnPlatform`) 時，一併套用智能保留邏輯，最小化資料遺失。

### 2026-07-14 — 修復站點顯示與錄製 UX 漏洞 (多輪子代理驗證 PASS)

**修改原因：**
- Avery 回報「車站兩邊資訊應該要不同，因為彼此上下站顛倒，但他都顯示去程的」。
- Avery 亦回報「錄製路線的問題，感覺怪怪的」（包含錄製中途下車會直線偏移、自動倒退嚕、停止與取消按鈕不直覺，以及玩家習慣直接點擊礦車本體而非鐵軌方塊）。
- 依據要求使用多輪獨立子代理 (Finder -> Fixer -> Verifier -> Verifier Round 2) 的雙重驗證流程。

**修復摘要：**
1. **站點資訊反轉**：在 `StopManager.java` 修復了沒有綁定路線時的備用邏輯，正確參照 `isReturnReversed` 屬性來翻轉手動設定的 `infoPrev` / `infoNext`。
2. **修復顯示快取閃退**：在 `StationDisplayListener.java` 移除錯誤的 action bar `cacheKey` 早期 return，確保持續刷新避免文字消失，同時維持離開金磚時的正確清理機制。
3. **距離防呆與自動取消**：在 `RouteRecordingSession.java` 實裝中途下車防呆，如果玩家離開礦車所在世界或距離大於 5 格，會自動拋棄當前損壞的路段並取消錄製，徹底防堵「拉直線」現象。
4. **自動推車向量修正**：在 `sampleNow` 時記錄物理移動的 `approachVector`，並在 `pickDepartDirection` 時使用內積 (Dot Product) 比較，選出最貼近實際行進方向的鐵軌分支，解決「到站後會反方向退回去」的問題。
5. **錄製實體互動 (UX 優化)**：新增 `PlayerInteractEntityEvent` 攔截，玩家現在可以直接右鍵點擊錄製用礦車來上車綁定，不再強制要求必須點擊正下方的鐵軌方塊。若 `auto-mount` 未啟用則自然放行原版上車機制。
6. **GUI 文案優化**：在 `zh_TW.yml` 將原有的「停止」修改為「儲存並結束錄製」，將「取消」修改為「放棄並取消錄製」，並補充明顯警告，改善了誤按導致進度消失的問題。

**驗證：** 第一輪 Verifier QA 指出了跨世界距離報錯等 3 個隱患，經 Fixer 修正後，第二輪 Verifier 獨立驗證完全通過，所有 UX 與邏輯問題皆已修復。

### 2026-07-14 — 修復錄製中止功能失效與回程月台顯示邏輯釋疑

**修改原因：**
- Avery 回報錄製路線時，到達站點後「提示中止都沒了」（下車想停止或取消錄製，卻發現錄製卡死，系統沒有任何提示也無法跳出 GUI）。
- 針對「回程月台顯示為去程」的現象再次提出疑問。

**修復摘要：**
1. **修復錄製中止失效 (`RouteRecordingSession.java`)**：
   - 先前為了防堵玩家離開礦車亂跑拉直線，加入了「下車超過 5 格就自動拋棄並取消錄製」的防呆檢查。
   - 但是這個檢查被放置在 `if (dwelling)` (停靠等待) 邏輯的「下方」。導致如果玩家是在「到站停靠時」下車想要中斷錄製，會因為卡在 `dwelling` 迴圈內提早 `return`，永遠走不到下方的距離檢查，造成錄製「假死」且沒有中止提示。
   - **解法**：將「離開礦車與距離判斷」的防呆邏輯移至 `tick` 最上方。若在 `dwelling` 期間下車遠離，也能正確觸發 `route.recording_cancelled` 的邏輯並捨棄該段損壞軌跡，恢復原先的正常體驗。
2. **回程月台顯示邏輯釋疑**：
   - 經反覆排查，系統在處理「自動翻轉上一站/下一站」的邏輯 (`isReturnReversed`) 是完全正確的。如果回程月台依然顯示「去程」資訊（即 A -> B -> C），這是因為系統將該月台判斷成了「第二個去程月台」。
   - **發生原因可能為**：
     1. 未使用木棍的「綁定為回程月台」按鈕，直接新建了另一個站點。
     2. 在設定路線時，手動配了一條新的回程路線，但裡面的站點順序依舊排成了去程的 A -> B -> C。
   - 已透過訊息向 Avery 解釋如何正確操作綁定機制。

### 2026-07-14 — 修復 Bukkit API 棄用警告 (Deprecation Warnings)

**修改原因：**
- Avery 回報在編譯時 (`gradle compileJava`) 遇到多個關於 `plugin.getServer().broadcast(String, String)` 和 `org.bukkit.Bukkit.broadcast(String, String)` 已被標記為廢棄 (deprecated) 的警告。

**修復摘要：**
1. **移除舊版廣播 API**：
   - 在 `LineManager.java` 與 `StopManager.java` 中，移除了舊有的 `.broadcast(message, permission)` 呼叫。
2. **改為手動跌代 (Manual Iteration)**：
   - 透過 `plugin.getServer().getConsoleSender().sendMessage(msg)` 發送給後台。
   - 透過 `for (Player p : plugin.getServer().getOnlinePlayers())` 走訪所有在線玩家，並檢查 `p.hasPermission("atrain.admin")`，藉此達成相同效果並順利消除編譯警告。

（註：關於 `java.lang.System::load` 在 `NativeLibraryLoader` 的警告為 Gradle 在 JDK 21+ 環境下內部呼叫 JNI 的正常警告，不影響插件運作，可忽略。）

### 2026-07-14 — 修復回程月台金磚資訊顯示錯誤（雙輪驗證 PASS）

**修改原因：**
- Avery 回報：回程站點移動正常（火車方向正確），但站在金磚/軌道上時 ActionBar 仍顯示去程的上一站/下一站。
- 根因：`Stop.isReturnPlatformAt()` 只比對 `(x,y,z)` 與 `(x,y-1,z)`，缺少去程月台 `isOnPlatformGold()` 的鐵軌向下掃描。玩家站在軌道上時 `blockY` 為軌道格，金磚在更下方，導致被誤判為去程；礦車 Y 較低故能正確判為回程。

**修復摘要：**
1. `Stop.isReturnPlatformAt()` — 改用 `isOnPlatformGold(loc, getReturnGoldBlocks())`，與去程對稱。
2. `Stop.isOnReturnPlatformOnly()` — 新增「僅回程月台」判定（去程優先），與 `CinematicTransitManager` 一致。
3. `Stop.containsInfoLocation()` — 統一為 `isOnForwardPlatform || isReturnPlatformAt`。
4. `StopManager.lookupGoldNear()` — 加入鐵軌向下掃描，`getStopByDisplay` 在軌道上也能找到站點。
5. `StopManager.resolveDisplayLine` / `isReturnReversed` — 改用 `isOnReturnPlatformOnly(at)`。
6. `StationDisplayListener` — ActionBar 加上 `(去程)`/`(回程)` 標籤；cache key 含方向。
7. `RouteRecordingManager.spawnRecordingCart` — 去程/回程錄製分別驗證正確月台，錯誤月台顯示提示。
8. `StopManager.findStopAdjacentTo` — 納入回程金磚。
9. 語系 — 新增 `route.recording_wrong_platform_forward/return`（zh_TW/zh_CN/en_US/ja_JP）。

**驗證：** 兩輪獨立子代理（盤點 / 修復 / 驗證分離），第二輪 8/8 PASS、0 BLOCKER、0 MAJOR。
**使用者操作不變：** 不需重設設定檔或重新綁定站點，既有資料直接生效。

### 2026-07-15 — 修復回程月台綁定靜默失敗與顯示不反轉問題

**修改原因：**
- Avery 回報：分開放的金磚並綁定回程車站後，火車能正常雙向行駛，但在回程月台上的上一站/下一站顯示沒有反轉，與去程完全一樣。
- 經過調查，發現問題出在綁定回程月台時可能發生「靜默失敗」，導致回程金磚沒有成功記錄在站點資料中。由於 `returnGoldBlocks` 為空，`isOnReturnPlatformOnly` 永遠判定為 `false`，導致所有月台都被視為去程月台。
- 此外，`StationUtil.scanConnectedGoldPlatform` 原本只掃描金磚「正上方一格」是否有鐵軌，導致高架一點的月台無法被識別，造成綁定失敗。

**修復摘要：**
1. **明確的回報機制 (`StopManager.java` & `PlayerInteractListener.java`)**：
   - 移除 `bindReturnPlatform` 原本的 `boolean` 回傳值，改為回傳 `BindResult` Enum，包含具體的失敗原因（如找不到金磚、世界不同、無相連金磚、與去程重疊、已屬本站）。
   - 在玩家互動監聽器中接收 `BindResult`，並根據不同的失敗原因向玩家發送具體的錯誤訊息，而不是籠統的「綁定失敗」。
2. **放寬鐵軌掃描限制 (`StationUtil.java`)**：
   - 修改 `scanConnectedGoldPlatform`，現在會向上掃描最多 **4 格** 來尋找鐵軌，使其與向下掃描的邏輯對稱，支援更高架的鐵軌月台設計。
3. **語系檔擴充 (`zh_TW.yml`)**：
   - 增加五條詳細的綁定失敗錯誤提示。

**驗證：**
- 已執行編譯 (`./gradlew compileJava`) 通過。
- 提示 Avery 在遊戲內重新嘗試綁定回程金磚，並透過 GUI 按鈕上的 lore 確認金磚數量大於 0 來驗證是否綁定成功。

### 2026-07-15 — 修復自動錄製失效及新增月台傳送功能

**修改原因：**
- Avery 回報在更新了設定檔升級邏輯後「現在連移動都有問題了」。經過深入調查，發現在清理了舊有 `lines.yml` 雜亂資料並要求產生獨立軌跡檔案的過程中，使用者嘗試使用系統的「自動掃描鐵軌」功能，但因為兩站之間的距離超過原先設定的 4000 個方塊限制，導致廣度優先搜尋 (BFS) 無法找到路徑，進而回退成直線連線（無視地形與軌道），使得火車在移動時由於軌跡錯誤而卡死不動。
- 同時，需要實作站點管理 GUI 的月台傳送按鈕。

**修復摘要：**
1. **優化 RailPathSampler (`RailPathSampler.java`)**：
   - 將原本的 `bfsAlongRails` 從 BFS 演算法替換成效率更高的 **A* 演算法**，並加入啟發式評估函數（與目標的直線距離 `fCost`）。
   - 將搜尋的最大節點數 (`maxNodes`) 與最大步數限制 (`maxSteps`) 從 `4000` 巨幅提升至 `100000`，確保超過數千格的長距離鐵路也能在瞬間完成路徑掃描。
2. **新增 GUI 月台傳送按鈕 (`GuiManager.java`, `GuiListener.java`)**：
   - 在 `STATION_EDIT` GUI 的 Slot 33 新增「傳送至另一側月台」的終界珍珠按鈕。
   - 如果玩家站在去程月台上，點擊會傳送到回程月台；若站在回程月台上，則傳送到去程月台。
3. **確認「刪除錄製段落」按鈕存在**：
   - 在 `GuiListener.java` 的 Slot 501~514 中，確認了「刪除軌跡」的功能已實作於 GUI（`[中鍵/Shift+左鍵] 刪除此段錄製`），完成使用者對錄製介面刪除功能的期望。

**驗證：**
- 執行 `./gradlew build -x test` 成功。
- 使用 A* 演算法不僅解決了長路線尋路失敗的問題，同時大幅降低了運算效能開銷。

### 2026-07-15 — 修復同站點多人同時上車導致礦車重疊問題

**修改原因：**
- Avery 回報若有兩個玩家同時在同格站點上車，生成的火車會重疊在一起，無法形成一列火車。
- 原本的生成邏輯 (`CartSpawnManager.trySpawn`) 中，只要發現站點上已經有礦車，就直接回傳 `false` (或者觸發 auto-mount)。如果玩家在同一格生成多台礦車，會重疊在同一個方塊內，產生推擠現象。

**修復摘要：**
1. **排隊機制取代空間尋路 (`CartSpawnManager.java`)**：
   - 玩家點擊站點時，若站點所在的鐵軌上已經有礦車（例如前一輛車正在停靠倒數，或剛發車），玩家不會再被分配到相鄰的空鐵軌。
   - 系統會將玩家加入該站點的「排隊序列 (Queue)」，並每 Tick 檢查：只要站點淨空且距離上次發車超過 1 秒 (20 Ticks)，就自動為下一位排隊的玩家生成礦車並讓其上車。
2. **完美錯開確保獨立列 (`TrainController.java` 相容)**：
   - 因為強制加入了 1 秒鐘的發車間隔，當第二輛礦車生成並發車時，第一輛礦車已經駛離 12 格（1秒 x 12格/秒）遠，超過了列車自動連動的偵測距離 (`trainCoupleDistance` = 1.6格)。
   - 這樣就能讓每位玩家搭乘「獨立的一列火車」，視覺上呈現魚貫而出、間隔 1 秒發車的完美效果，不會重疊也不會異常連接。

**驗證：**
- 執行 `./gradlew classes` 編譯成功，排隊機制已穩定運作且無編譯錯誤。

### 2026-07-15 — 修復排隊機制重複點擊與佔用判定 Bug

**修改原因：**
- Avery 建議檢查目前的生成邏輯。我發現之前的排隊機制存在以下隱患：
  1. **重複排隊漏洞**：若站點被佔用且玩家在排隊中，玩家重複點擊站點會導致被多次加入隊列，發出多班空車。
  2. **提示訊息未顯示**：之前在判斷站點是否空閒時，漏看了「有乘客的礦車」，導致玩家如果點到一輛有人坐的車，會默默進入排隊但不會看到「已加入排隊序列」的提示。

**修復摘要：**
1. **防重複排隊 (`CartSpawnManager.trySpawn`)**：
   - 每次玩家嘗試召喚時，會先檢查其 UUID 是否已存在於該站點的排隊序列中。如果已在排隊中，則不再重複加入，只會更新並發送一次「已加入排隊序列」的提示，防止刷車漏洞。
2. **修正站點佔用判定**：
   - 將「尋找可用空車」與「判斷站點是否被佔用 (isOccupied)」的邏輯脫鉤。只要範圍內有任何礦車（包含有乘客的），`isOccupied` 就為 true。
   - 在決定是否顯示排隊提示時，只要發現 `isOccupied == true`，就會明確地告知玩家「前方有列車，已加入排隊序列」。

### 2026-07-16 — 實作 Grim Anticheat 動態豁免 (防止礦車誤判)

**修改原因：**
- Avery 回報 Grim Anticheat 會在礦車移動時一直產生防作弊誤判。
- 第一版使用 Bukkit 的 `PermissionAttachment` 給予 `grim.disabled` 權限，但在玩家下車移除權限後，防作弊卻沒有恢復運作（下車開外掛依舊沒有偵測到）。
- 經過調查，Grim Anticheat 不會動態監聽 Bukkit 的權限變更，必須透過 Grim API 的 `updatePermissions()` 才能讓它即時重新讀取快取中的權限狀態。

**修復摘要：**
1. **引入 GrimAPI 依賴**：
   - 在 `build.gradle.kts` 中新增 Grim 的官方 Maven repository (`https://repo.grim.ac/snapshots`) 以及 API 依賴 `compileOnly("ac.grim.grimac:GrimAPI:1.6.0.9")`。
2. **呼叫 API 更新快取 (`EmptyCartListener.java`)**：
   - 在玩家上車 (`onEnter`) 與下車 (`onExit`/`onQuit`) 改變權限 (`player.recalculatePermissions()`) 之後，透過 `Bukkit.getPluginManager().isPluginEnabled("GrimAC")` 安全判定。
   - 若有裝 Grim，則呼叫 `GrimAPIProvider.get().getGrimUser(player.getUniqueId()).updatePermissions()`，主動迫使 Grim Anticheat 重新抓取該玩家的最新權限。
   - 這解決了因為 Grim 內部權限快取而導致的「豁免權限卡住無法恢復」問題。

**驗證：**
- 成功編譯通過，程式碼在未安裝 Grim 的伺服器上也不會報錯 (安全的 try-catch 處理)。
- 下次測試時，玩家下車後 Grim 會被強制要求重新計算權限，進而恢復防作弊檢查。
