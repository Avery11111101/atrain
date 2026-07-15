# atrain — Gold Block Stations & Coupled Train Control

## Summary (paste into Modrinth Summary, plain text single line)

atrain is a Minecraft Paper 1.21 station plugin with BlueMap integration, A* instant route pathfinding, anti-overlap, auto coupling minecarts and auto stop for railway RPG servers

---

## Full description (paste into Modrinth Description)

Build a rail experience on your Minecraft server with station names, travel direction, and arrival and departure feel. No commands to memorize. Sneak and right-click a gold block to create and edit stations. Minecarts automatically couple into trains, cruise at full speed on straights, slow on curves, and depart together after a full consist stops at the platform.

atrain is a Minecraft Paper train plugin and minecart station plugin for servers that need zero command station setup, auto coupling minecart trains, and optional station auto stop. Build gold block stations on rails with sneak right click, ideal for railway RPG, city survival, metro, and scenic line servers.

---

## Why atrain?

- **Zero-command setup**: Gold block + rails — sneak + right-click to open the GUI and set station names, directions, and multiple linked key stations
- **Web Map Integration**: BlueMap support to instantly display station POIs and 3D route paths on your live web map
- **Immersive info display**: Action bar shows previous / current / next stop, with dynamic chat prompts for transfer information
- **Coupled trains & Anti-overlap**: Auto-finds empty spots for multiple players to prevent overlap. Adjacent carts auto-couple, full cruise on straights, auto slowdown on curves
- **A* Auto Recording**: Built-in A* algorithm instantly paths between stations up to 100,000 blocks away, or use manual ride recording
- **Multilingual**: Traditional Chinese, Simplified Chinese, English, and Japanese UI

Want vanilla minecart physics back? Vanilla minecarts are ignored by default, or just set `train_control` to `false`.

---

## Requirements

| Item | Version |
|------|---------|
| Server | Paper **1.21+** |
| Java | **21 or 25+** |

---

## Quick Start

1. Download `atrain-x.x.x.jar` and place it in `plugins/` (Install BlueMap if desired)
2. Restart the server
3. Place rails on top of a gold block → **sneak + right-click** to open the station GUI
4. Set station name and direction; optionally place a diamond block under the rails as a speed block
5. Place minecarts on the track — adjacent carts auto-couple, full speed on straights, auto slowdown on curves
6. (Optional) Enable `auto_stop` in `config.yml` for full-consist station stops

---

## Features

### Station info (gold block)
- Action bar shows previous / current / next stop, auto-reversed based on the platform side
- Multiple key stations with bi-directional linking, dynamic chat prompts for transfers
- Admin notes and dedicated station reorder GUI
- Registered gold blocks are protected — permission required to break

### Web Map Integration (BlueMap)
- Automatically syncs station markers (POIs) and 3D route paths to the web map
- Route paths are rendered using their configured chat colors

### Coupled trains & Anti-overlap
- Auto-finds empty adjacent rails when multiple players spawn carts to prevent overlap
- Straights: full cruise; Curves / slopes: automatic slowdown
- Adjacent carts auto-couple, with optional full consist auto-stop & depart
- Isolated PDC tags — does not interfere with the vanilla physics of player-placed minecarts

### Route recording & Guided transit
- **A* Auto Pathfinding**: Instantly calculates and saves the track between stations (up to 100k blocks)
- **Manual Recording**: Ride a minecart to record the route, with smart detection for dismounting
- **Smart Path Preservation**: Automatically preserves unchanged track segments when reordering stations
- Guided transit mode drives smoothly along the track and accurately stops at stations

### Speed blocks & Hang rail
- **Speed blocks (diamond)**: Place under rails to set pass-over speeds via GUI
- **Hang rail**: Minecarts stay suspended under iron bars at cruise speed

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
