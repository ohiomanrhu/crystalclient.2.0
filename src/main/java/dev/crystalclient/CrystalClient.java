package dev.crystalclient;

import dev.crystalclient.core.Config;
import dev.crystalclient.core.ModuleManager;
import dev.crystalclient.gui.ClickGui;
import dev.crystalclient.modules.CoreSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrystalClient implements ClientModInitializer {
    public static final String ID = "crystalclient";
    public static final Logger LOG = LoggerFactory.getLogger("CrystalClient");
    private static KeyBinding openGui;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Config.ensurePresets();
        Config.loadCurrent();

        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crystalclient.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of(ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                while (openGui.wasPressed()) mc.setScreen(new ClickGui());
                ModuleManager.tick(mc);
            } catch (Throwable t) { LOG.error("Client tick failure", t); }
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.of(ID, "hud"),
                (ctx, tickCounter) -> ModuleManager.renderHud(ctx));

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.saveCurrent());
        LOG.info("CrystalClient loaded (Minecraft 1.21.11). Press RIGHT SHIFT for the GUI.");
    }

    /** Debug output: always to the log when "Debug logging" is on, optionally to the action bar. */
    public static void debug(String fmt, Object... args) {
        CoreSettings c = CoreSettings.I;
        if (c == null || !c.debugLog.get()) return;
        LOG.info("[debug] " + fmt.replace("{}", "%s").formatted(args));
        MinecraftClient mc = MinecraftClient.getInstance();
        if (c.debugChat.get() && mc.player != null)
            mc.player.sendMessage(Text.literal("[CC] " + fmt.replace("{}", "%s").formatted(args)), true);
    }
}
