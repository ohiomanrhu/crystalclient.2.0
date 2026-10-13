package dev.crystalclient.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

import java.util.function.Predicate;

public final class InventoryUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private InventoryUtil() {}

    /** Inventory index (0-8 hotbar, 9-35 main) -> PlayerScreenHandler slot id. */
    public static int screenSlot(int invIndex) { return invIndex < 9 ? 36 + invIndex : invIndex; }

    public static int findHotbar(Predicate<ItemStack> p) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) if (p.test(inv.getStack(i))) return i;
        return -1;
    }

    /** Hotbar first, then main inventory (if allowed). Never returns the offhand. */
    public static int find(Predicate<ItemStack> p, boolean includeMain) {
        int h = findHotbar(p);
        if (h >= 0 || !includeMain) return h;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 9; i < 36; i++) if (p.test(inv.getStack(i))) return i;
        return -1;
    }

    public static int count(Predicate<ItemStack> p) {
        PlayerInventory inv = mc.player.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack s = inv.getStack(i); if (p.test(s)) n += s.getCount(); }
        ItemStack off = mc.player.getOffHandStack();
        if (p.test(off)) n += off.getCount();
        return n;
    }

    public static boolean select(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return false;
        PlayerInventory inv = mc.player.getInventory();
        if (inv.getSelectedSlot() != hotbarSlot) inv.setSelectedSlot(hotbarSlot); // synced automatically on next interaction
        return true;
    }

    /**
     * Swaps the given inventory slot with the offhand using the SWAP click (button 40).
     * Needs no free slot: the old offhand item takes the source slot's place.
     * Returns false if the action cannot be sent (another container open, bad index).
     */
    public static boolean swapOffhand(int invIndex) {
        var p = mc.player;
        if (p == null || mc.interactionManager == null || invIndex < 0 || invIndex > 35) return false;
        if (p.currentScreenHandler != p.playerScreenHandler) return false;
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, screenSlot(invIndex), 40, SlotActionType.SWAP, p);
        return true;
    }

    /** Estimated round-trip latency in ticks, used as the "wait for server confirmation" window. */
    public static int latencyTicks() {
        try {
            var nh = mc.getNetworkHandler();
            var e = nh == null ? null : nh.getPlayerListEntry(mc.player.getUuid());
            return e == null ? 2 : Math.min(10, e.getLatency() / 50 + 1);
        } catch (Throwable t) { return 2; }
    }
}
