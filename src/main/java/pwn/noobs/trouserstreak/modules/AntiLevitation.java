package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

public class AntiLevitation extends Module {
    public enum Mode {
        Velocity,
        Cancel
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("How to handle the levitation effect.")
            .defaultValue(Mode.Velocity)
            .build()
    );

    private final Setting<Boolean> applyGravity = sgGeneral.add(new BoolSetting.Builder()
            .name("apply-gravity")
            .description("Applies normal gravity so you fall instead of floating in place.")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Velocity)
            .build()
    );

    public AntiLevitation() {
        super(KoodaAddon.KOODA_MOVEMENT, "anti-levitation", "Prevents the Levitation effect from floating you upwards.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
            if (mode.get() == Mode.Cancel) {
                mc.player.removeStatusEffect(StatusEffects.LEVITATION);
                return;
            }

            Vec3d velocity = mc.player.getVelocity();

            if (applyGravity.get()) {
                if (!mc.player.isOnGround()) {
                    mc.player.setVelocity(velocity.x, Math.max(velocity.y - 0.08, -3.92), velocity.z);
                }
            } else {
                if (velocity.y > 0) {
                    mc.player.setVelocity(velocity.x, 0, velocity.z);
                }
            }
        }
    }
}