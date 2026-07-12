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
