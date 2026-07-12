# atrain

Minecraft 金磚站點插件 — **零指令**操作，蹲下右鍵即可設定站點資訊顯示。內建可選的**連結列車控速**：直線跑滿速、彎道與上下坡自動減速、相鄰礦車自動連結成一列並整列進站發車。

## 功能

### 資訊顯示（核心）
- 站在 **金磚** 上，actionbar 顯示上一站 / 本站 / 下一站
- **鐵軌正下方放鑽石塊** = 調速方塊：礦車經過即加/減速（蹲下右鍵設定速度）
- 可設定 **重點站** 與 **行駛方向**（如：台北 → 南下）
- **管理員備註** 僅管理員可見
- 所有文字在站點 GUI 自行輸入，不依路線

### 連結列車控速（預設開啟）
- 直線路段以 `settings.cart_speed` 全速巡航；**接近轉彎、上下坡、非直線**時自動降到 `settings.curve_speed`
- 把多台礦車放在相鄰軌道 → **自動連結成一列**（建議 4 節）
- 搭配自動停車時，整列一起停靠、停留時間到**整列自動往前發車**，車上乘客會看到停靠倒數提示
- 想回歸純原版物理可設 `settings.train_control: false`

### 自動停車（可選，預設關閉）
- 在 `config.yml` 開啟 `settings.auto_stop: true` 後，礦車經過金磚站會自動停留
- 停留時間僅管理員可在 GUI 調整
- 與連結列車控速搭配時，觸發整列一起停靠與發車

## 安裝

1. **Paper 1.21+**、**Java 21+**
2. 放入 `plugins/atrain-1.0.jar`
3. 重啟伺服器

## 快速開始（不需指令）

1. 金磚上方鋪鐵軌
2. **蹲下 + 右鍵** 金磚或鐵軌 → 開啟站點 GUI
3. 設定站名、上一站/下一站、重點站與方向
4. 旁邊放鑽石塊（可選）→ 站上即可看資訊
5. 放礦車上軌：直線跑滿速、彎道/坡道自動減速；多台相鄰會連成一列
6. （可選）開 `auto_stop` → 整列進站停靠、時間到自動發車

## 指令（管理員選用）

| 指令 | 說明 |
|------|------|
| `/train` / `/train gui` | 管理介面 |
| `/train stops` | 站點列表 |
| `/train reload` | 重新載入（管理員） |
| `/lang <語言>` | 切換語言 |

## 權限

所有權限**預設僅 OP**；若要開放給特定身分組，請用 LuckPerms 等插件授予對應節點。

| 權限 | 說明 | 預設 |
|------|------|------|
| `atrain.admin` | 管理員全權（含下方所有子權限） | OP |
| `atrain.gui` | 開啟 `/train` GUI、站點/路線列表 | OP |
| `atrain.station.edit` | 蹲下右鍵編輯金磚站點 | OP |
| `atrain.speed.edit` | 蹲下右鍵編輯調速鑽石塊 | OP |
| `atrain.cart.spawn` | 站點右鍵召喚礦車 | OP |
| `atrain.block.break` | 破壞已註冊的金磚站點與調速鑽石塊 | OP |

已註冊的**金磚**（站點）與**鑽石塊**（調速方塊）受保護，無 `atrain.block.break` 的玩家無法拆除。

## 設定（`config.yml`）

| 項目 | 說明 | 預設 |
|------|------|------|
| `settings.station_display` | 資訊顯示開關 | `true` |
| `settings.train_control` | 連結列車控速總開關 | `true` |
| `settings.cart_speed` | 巡航滿速（越大越快） | `0.6` |
| `settings.curve_speed` | 彎道/上下坡/非直線減速上限 | `0.4` |
| `settings.train_max_cars` | 建議連結節數 | `4` |
| `settings.train_couple_distance` | 自動連結偵測距離 | `1.6` |
| `settings.auto_stop` | 自動停車開關 | `false` |
| `settings.default_dwell_time` | 新站停留（tick，auto_stop 開啟時） | `80`（4 秒） |
| `hang_rail.enabled` | 懸浮軌道 | `false` |

## 更新日誌

版本紀錄見 [docx/更新日誌.md](docx/更新日誌.md)。

## 玩家教學

完整搭車與建站說明見 [docx/玩家教學.md](docx/玩家教學.md)。遊戲內亦可 `/train gui` → 教學指南。
