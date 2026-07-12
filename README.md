# atrain

Minecraft 金磚站點插件 — **零指令**操作，蹲下右鍵即可設定站點資訊顯示。內建可選的**連結列車控速**：直線跑滿速、彎道與上下坡自動減速、相鄰礦車自動連結成一列並整列進站發車。

## 功能

### 資訊顯示（核心）
- 站在 **金磚** 或 **鑽石塊** 上，actionbar 顯示上一站 / 本站 / 下一站
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

| 權限 | 說明 | 預設 |
|------|------|------|
| `atrain.station.edit` | 蹲下右鍵編輯站點 | 所有人 |
| `atrain.gui` | 開啟 GUI | 所有人 |
| `atrain.admin` | reload、管理員備註等 | OP |

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

## Changelog

### v1.2
- 懸浮軌道改為每 tick 主動吊住，礦車在鐵欄杆下方可穩定懸浮不再墜落（靜止也吊住）
- 懸浮段維持巡航速度，不會滑行到停
- 轉彎/上下坡「提前減速」：新增 `settings.curve_lookahead`（預設 5 格）

### v1.1
- 新增連結列車控速：`cart_speed` 巡航滿速、彎道/上下坡自動減速至 `curve_speed`
- 相鄰礦車自動連結成一列，搭配自動停車可整列停靠、時間到整列自動發車
- 車上乘客顯示停靠倒數與發車提示
- 可用 `settings.train_control: false` 回歸原版物理

### v1.0
- 金磚站點資訊顯示（上一站/本站/下一站/重點站/方向）
- 蹲下右鍵零指令建站與編輯
- 管理員備註專區
- 可選自動停車（預設關閉）
- 多語言介面
