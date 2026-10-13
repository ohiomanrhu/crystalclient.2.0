package dev.crystalclient.modules;

import dev.crystalclient.core.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Comparator;

public final class TargetSelector {
    private static PlayerEntity target;
    private TargetSelector() {}

    public static PlayerEntity get() { return target; }

    public static void update(MinecraftClient mc) {
        target = null;
        CoreSettings c = CoreSettings.I;
        if (c == null || mc.world == null || mc.player == null) return;
        double range = c.targetRange.d();
        Comparator<PlayerEntity> cmp = c.targetSort.is("Health")
                ? Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount())
                : Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p));
        target = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && !p.isSpectator() && !p.isCreative())
                .filter(p -> mc.player.distanceTo(p) <= range)
                .filter(p -> !Config.isFriend(p.getName().getString()))
                .min(cmp).orElse(null);
    }
}
