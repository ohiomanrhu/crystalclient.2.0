# CrystalClient (Fabric, Minecraft Java 1.21.11)

Client-side Crystal PvP / Anchor PvP toolkit: priority-managed combat modules, searchable GUI, HUD editor, profiles.

**Use only in private worlds or on servers whose rules explicitly allow client-side combat automation.**
Most public servers ban this. You are responsible for where you run it.

## Status / honesty note
This source was written without access to a compiler or the Minecraft jars, so it has **not been built or run**.
Expect a handful of mapping-name fixes on first compile (see "Likely compile fixes"). Architecture, logic and
build config are complete.

## Version compatibility (verify at https://fabricmc.net/develop/)
| Item | Value |
|---|---|
| Minecraft | 1.21.11 (last obfuscated release) |
| Mappings | Yarn `1.21.11+build.4` (Yarn ends at 1.21.11) |
| Loom plugin | `net.fabricmc.fabric-loom-remap` (obfuscated versions) |
| Fabric API | `0.141.1+1.21.11` |
| Loader | `0.18.1` (check latest) |
| Java | 21 |

## Build
1. Install JDK 21 and Gradle (or generate the wrapper from the Fabric template page for 1.21.11 and copy `gradlew*` + `gradle/` here).
2. `gradle build` (or `./gradlew build`)
3. Jar: `build/libs/crystalclient-1.0.0.jar`

## Install / launch
1. Install Fabric Loader for 1.21.11 with the Fabric installer; put Fabric API in `.minecraft/mods`.
2. Drop the CrystalClient jar into `.minecraft/mods`.
3. Launch the Fabric profile. **Right Shift** opens the GUI (rebind in Controls).
Dev run: `gradle runClient`.

## Layout
```
dev/crystalclient/
  CrystalClient.java        entrypoint, tick + HUD hooks, debug()
  core/    Module, ModuleManager, PriorityManager, InventoryUtil, BlockPlacer, Config, HudModule
  setting/ Setting (Bool, Num, Choice, Key)
  modules/ AutoTotem, AutoSwap, CrystalAssist, AutoAnchor, TargetSelector, DamageCalc, TargetHud, ModuleListHud, CoreSettings
  gui/     ClickGui, HudEditorScreen
```

## Priority system
Tiers: 0 Survival, 1 Defensive, 2 Combat, 3 Utility, 4 Cosmetic. Modules tick in tier order; any action that
touches hands/hotbar first calls `PriorityManager.acquire(module, tier, ticks)`. Lower tier preempts, equal tier
never does, locks expire. Auto Totem is fixed at 0 and combat modules pause while `AutoTotem.busy()`.
Combat/Auto Swap tiers are configurable per module ("Priority tier").

## Modules
- **Auto Totem**: Danger/Always mode, HP threshold, crystal + fall damage prediction, hotbar/inventory search,
  latency-aware verification, retry with cooldown, optional restore of previous offhand item. Uses SWAP-to-offhand
  so a full inventory is never a problem. Missing totems -> log warning + HUD alert.
- **Crystal Assist**: damage-scored placement (target dmg - weight x self dmg), breaking, face-place, kill-shot,
  defensive break (tier 1), optional auto obsidian, playstyle presets (Aggressive/Balanced/Safe).
- **Auto Anchor**: place -> charge -> detonate with self-damage and margin checks. Idle in the Nether.
- **Auto Swap**: hotkeys for crystal/obsidian/glowstone/anchor/totem + shared `hold()` helper + slot restore.
- **Target HUD / Active Modules**: movable, scalable HUD elements.
- **Core**: target range/priority, assumed blast protection (0-20 EPF), debug logging/action-bar.

## Configuration
Files in `.minecraft/config/crystalclient/`:
- `current.json` auto-saved on GUI close and exit.
- `profiles/crystal-pvp.json`, `profiles/anchor-pvp.json` generated on first run; GUI buttons load them.
- Any other `profiles/*.json` via the GUI (< > cycle, Save, Load, New).
- `friends`: array of player names in the JSON, never targeted.
Each module stores `enabled` + every setting by display name. Unknown/bad values are ignored with a log warning.

GUI: tabs, search (matches settings too), left-click module = toggle, right-click = expand, sliders drag,
choices click (right-click back), key rows click then press key (Backspace/Delete or right-click clears), HUD button
opens the editor (drag, scroll resize, right-click hide).

## Testing guide (private/creative world, second account or armor stand/dummy player)
1. Load: log shows "CrystalClient loaded"; GUI opens; HUD shows the module list.
2. Auto Totem: enable, set mode Always, put a totem in hotbar -> offhand fills. Move totem to main inventory, repeat.
   Fill every slot -> still swaps. Remove all totems -> "NO TOTEMS" alert and one warning per ~10s.
   Danger mode: threshold 20 -> equips immediately; set 10, damage yourself below 10 (/damage) -> equips.
3. Danger prediction: place a crystal 3 blocks away, expect equip even at high HP if predicted damage is lethal-ish.
4. Crystal Assist: obsidian + crystals, second player near obsidian. Verify place/break delays, min/max damage.
   Lower Max self damage to 1 -> it must refuse. Enable Core > Debug logging to read decisions.
5. Auto Anchor (Overworld): anchors + glowstone, target nearby. Watch place -> charge -> detonate.
   In the Nether it must stay idle.
6. Conflicts: enable all three with Debug logging; trigger low HP mid-fight. Log should show Auto Totem preempting;
   CONFLICT lines show denied lower-priority requests. Raise Crystal Assist to tier "Survival" to see equal-tier denial.
7. Latency: use a proxy/throttle (or a laggy server) and confirm no swap spam (one swap per confirm window).
8. Profiles: tweak a slider, Save, restart, confirm persisted; Crystal PvP / Anchor PvP buttons switch module sets.

## Likely compile fixes (mapping drift in 1.21.9-1.21.11)
- `Click`/`KeyInput`/`CharInput` accessors in `ClickGui` / `HudEditorScreen` (`x()`, `y()`, `button()`, `key()`, `codepoint()`).
- `InputUtil.isKeyPressed(mc.getWindow(), code)` in `Module.pressed`.
- `Entity.getEntityPos()` (older name `getPos()`), `KeyBinding.Category.create(...)`.
- `HudElementRegistry` / `VanillaHudElements` names in Fabric API; `DrawContext.getMatrices()` is a 2D matrix stack (`pushMatrix`, `popMatrix`).
- `PlayerInventory.getSelectedSlot()/setSelectedSlot()`, `EntityAttributes.ARMOR_TOUGHNESS`.
If a name differs, search Yarn for 1.21.11 (`./gradlew genSources`).

## Known limitations
- No rotation/aim handling and no anti-cheat awareness; server-side checks may reject or flag actions.
- Blast protection of armor is an assumed setting, not read from enchantments. Anchor/TNT threats are not
  considered by Auto Totem's danger detection (crystals and falls only).
- Exposure uses vanilla-style raycasts without simulating blocks destroyed by the blast itself.
- Existing anchors near the target are used even if you did not place them.
- Auto Swap restores the hotbar slot only; it does not move items between inventory slots.
- Placement search is capped (14 crystal spots / 10 anchor spots per search) to bound raycast cost.
- Supported: singleplayer/LAN and permissive servers on 1.21.11 + Fabric only.

## Build without installing anything (GitHub Actions)
1. Create a free GitHub account, make a new repository, and upload all files in this folder
   (including the hidden `.github` folder).
2. Open the repo's **Actions** tab -> "Build CrystalClient" -> wait for the green check.
3. Open the finished run and download the **crystalclient-jar** artifact (a zip containing the jar).
If the run fails, open it and copy the red error lines.
