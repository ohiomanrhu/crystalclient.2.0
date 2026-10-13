package dev.crystalclient.modules;

import dev.crystalclient.core.*;
import dev.crystalclient.core.Module;
import dev.crystalclient.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.function.Predicate;

/**
 * Hotkey item switching plus the shared {@link #hold} helper that combat modules use to equip items
 * through the PriorityManager (so they never fight over the hotbar).
 */
public class AutoSwap extends Module {
    public static AutoSwap I;

    private final Setting.Key crystalKey = key("Crystal key", "Select end crystals");
    private final Setting.Key obsidianKey = key("Obsidian key", "Select obsidian");
    private final Setting.Key glowstoneKey = key("Glowstone key", "Select glowstone");
    private final Setting.Key anchorKey = key("Anchor key", "Select respawn anchors");
    private final Setting.Key totemKey = key("Totem key", "Select a totem in hand");
    private final Setting.Bool restoreSlot = bool("Restore slot", "Return to the original hotbar slot after combat actions", true);
    private final Setting.Num restoreDelay = num("Restore delay (ticks)", "Idle time before returning", 6, 1, 40, 1);

    private int prevSlot = -1, idle;

    public AutoSwap() { super("Auto Swap", "Fast item switching with priority-aware restore", Category.COMBAT, 3, true); I = this; }

    public static void noteActivity() { if (I != null) I.idle = 0; }

    /**
     * Makes sure an item matching {@code p} is in a hand. Returns the hand, or null if unavailable
     * or the PriorityManager denied the switch.
     */
    public static Hand hold(Module requester, Predicate<ItemStack> p) {
        var pl = MinecraftClient.getInstance().player;
        if (p.test(pl.getMainHandStack())) return Hand.MAIN_HAND;
        if (p.test(pl.getOffHandStack())) return Hand.OFF_HAND;
        int slot = InventoryUtil.findHotbar(p);
        if (slot < 0) return null;
        if (!PriorityManager.acquire(requester, requester.level(), 2)) return null;
        if (I != null && I.isEnabled() && I.prevSlot < 0) I.prevSlot = pl.getInventory().getSelectedSlot();
        InventoryUtil.select(slot);
        return Hand.MAIN_HAND;
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (pressed(crystalKey)) quick(requestSlot(s -> s.isOf(Items.END_CRYSTAL)));
        if (pressed(obsidianKey)) quick(requestSlot(s -> s.isOf(Items.OBSIDIAN)));
        if (pressed(glowstoneKey)) quick(requestSlot(s -> s.isOf(Items.GLOWSTONE)));
        if (pressed(anchorKey)) quick(requestSlot(s -> s.isOf(Items.RESPAWN_ANCHOR)));
        if (pressed(totemKey)) quick(requestSlot(s -> s.isOf(Items.TOTEM_OF_UNDYING)));

        if (restoreSlot.get() && prevSlot >= 0) {
            if (++idle >= restoreDelay.i() && PriorityManager.isFree()) {
                InventoryUtil.select(prevSlot);
                prevSlot = -1; idle = 0;
            }
        }
    }

    private int requestSlot(Predicate<ItemStack> p) { return InventoryUtil.findHotbar(p); }

    private void quick(int slot) {
        if (slot < 0 || !PriorityManager.acquire(this, level(), 2)) return;
        InventoryUtil.select(slot);
        prevSlot = -1;   // manual choice: do not auto-restore over it
    }
}
