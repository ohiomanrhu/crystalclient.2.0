package dev.crystalclient.modules;

import dev.crystalclient.core.Category;
import dev.crystalclient.core.Module;
import dev.crystalclient.setting.Setting;

/** Global options shared by all combat modules. Its settings are read even when toggled off. */
public class CoreSettings extends Module {
    public static CoreSettings I;
    public final Setting.Num targetRange = num("Target range", "Max distance for target selection", 12, 4, 40, 1);
    public final Setting.Choice targetSort = choice("Target priority", "How targets are ranked", "Distance", "Distance", "Health");
    public final Setting.Num targetProt = num("Assumed target protection", "Blast protection EPF assumed on targets (0-20)", 0, 0, 20, 1);
    public final Setting.Num selfProt = num("Assumed self protection", "Your own blast protection EPF (0-20)", 0, 0, 20, 1);
    public final Setting.Bool debugLog = bool("Debug logging", "Log module decisions and priority conflicts", false);
    public final Setting.Bool debugChat = bool("Debug action bar", "Also show debug lines on the action bar", false);

    public CoreSettings() { super("Core", "Global targeting and debug options", Category.CLIENT, 4, false); I = this; }
}
