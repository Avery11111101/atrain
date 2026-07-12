# atrain — Gold Block Stations & Coupled Train Control

## Summary (paste into Modrinth Summary, plain text single line)

atrain is a Minecraft Paper 1.21 train and minecart station plugin with zero command gold block stations auto coupling minecarts station auto stop curve slowdown route recording and multilingual support for railway RPG survival servers

---

## Full description (paste into Modrinth Description)

Build a rail experience on your Minecraft server with station names, travel direction, and arrival and departure feel. No commands to memorize. Sneak and right-click a gold block to create and edit stations. Minecarts automatically couple into trains, cruise at full speed on straights, slow on curves, and depart together after a full consist stops at the platform.

atrain is a Minecraft Paper train plugin and minecart station plugin for servers that need zero command station setup, auto coupling minecart trains, and optional station auto stop. Build gold block stations on rails with sneak right click, ideal for railway RPG, city survival, metro, and scenic line servers.

---

## Why atrain?

- **Zero-command station setup**: Gold block + rails — sneak + right-click to open the GUI and set station name, previous/next stop, key station flag, and travel direction
- **Immersive info display**: Stand on a gold block to see previous / current / next stop in the action bar
- **Coupled train speed control**: Adjacent minecarts auto-couple into a consist; full cruise on straights, automatic slowdown on curves and slopes
- **Optional auto-stop**: When enabled, the whole train stops at stations, counts down, then departs together — passengers see on-screen prompts
- **Route recording & guided transit**: Record a full route path, then run cinematic transit along the saved track
- **Multilingual**: Traditional Chinese, Simplified Chinese, English, and Japanese UI

Want vanilla minecart physics back? Just set `train_control` to `false`.

---

## Requirements

| Item | Version |
|------|---------|
| Server | Paper **1.21+** |
| Java | **21 or 25+** |

---

## Quick Start

1. Download `atrain-x.x.x.jar` and place it in `plugins/`
2. Restart the server
3. Place rails on top of a gold block → **sneak + right-click** to open the station GUI
4. Set station name and direction; optionally place a diamond block under the rails as a speed block
5. Place minecarts on the track — adjacent carts auto-couple, full speed on straights, auto slowdown on curves
6. (Optional) Enable `auto_stop` in `config.yml` for full-consist station stops

---

## Features

### Station info (gold block)
- Action bar shows previous / current / next stop
- Key station flag and travel direction (e.g. Taipei → Southbound)
- Admin notes (OP only)
- Registered gold blocks are protected — permission required to break

### Speed blocks (diamond block)
- Place **directly under the rails** — minecarts speed up or slow down when passing over
- Sneak + right-click to open the GUI and set speed

### Coupled trains
- Straights: full cruise at `cart_speed`
- Curves / slopes: automatically reduced to `curve_speed`
- Recommended 4 cars; adjacent carts auto-couple
- With auto-stop: full consist stops and departs together

### Route recording
- Start/stop recording from the admin GUI
- Recording uses vanilla physics along the full route, 4-second dwell at each station
- After recording, guided transit runs along the saved path

### Hang rail (optional)
- Enable with `hang_rail.enabled: true`
- Minecarts stay suspended under iron bars at cruise speed

---

## Permissions (OP by default)

| Permission | Description |
|------------|-------------|
| `atrain.admin` | Full admin access |
| `atrain.gui` | Open `/train` admin GUI |
| `atrain.station.edit` | Sneak + right-click to edit gold block stations |
| `atrain.speed.edit` | Sneak + right-click to edit speed diamond blocks |
| `atrain.cart.spawn` | Right-click station to spawn a minecart |
| `atrain.block.break` | Break registered stations and speed blocks |

Use LuckPerms or similar to grant permissions to specific groups.

---

## Commands

| Command | Description |
|---------|-------------|
| `/train` / `/train gui` | Admin GUI |
| `/train stops` | Station list |
| `/train reload` | Reload config |
| `/lang <locale>` | Switch language (zh_TW, zh_CN, en_US, ja_JP) |

---

## Links

- Full documentation & config: see project README
- Changelog: see `docx/更新日誌.md`
