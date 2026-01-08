package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class KoodaGrimVelocity extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgGrim = settings.createGroup("GrimAC Bypass");
    private final SettingGroup sgPhysics = settings.createGroup("Physics");

    private final Setting<Double> horizontal = sgGeneral.add(new DoubleSetting.Builder()
            .name("horizontal")
            .description("Horizontal velocity factor (0 = no knockback).")
            .defaultValue(0)
            .min(0)
            .max(100)
            .sliderMax(100)
            .build()
    );

    private final Setting<Double> vertical = sgGeneral.add(new DoubleSetting.Builder()
            .name("vertical")
            .description("Vertical velocity factor (0 = no jump).")
            .defaultValue(0)
            .min(0)
            .max(100)
            .sliderMax(100)
            .build()
    );

    private final Setting<Boolean> explosions = sgGeneral.add(new BoolSetting.Builder()
            .name("explosions")
            .description("Apply velocity reduction to explosions.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> liquids = sgGeneral.add(new BoolSetting.Builder()
            .name("in-liquids")
            .description("Enable velocity modification while in water/lava.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> grimMode = sgGrim.add(new BoolSetting.Builder()
            .name("grim-mode")
            .description("Uses Counter-Force logic instead of cancelling packets.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> randomness = sgGrim.add(new DoubleSetting.Builder()
            .name("randomness")
            .description("Adds small random variations to avoid detection.")
            .defaultValue(0.05)
            .min(0)
            .max(1.0)
            .visible(grimMode::get)
            .build()
    );

    public final Setting<Boolean> noPushBlocks = sgPhysics.add(new BoolSetting.Builder()
            .name("no-push-blocks")
            .description("Prevents being pushed out of blocks.")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> noPushEntities = sgPhysics.add(new BoolSetting.Builder()
            .name("no-push-entities")
            .description("Prevents being pushed by other players/mobs.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
            .name("debug")
            .description("Prints packet info to chat.")
            .defaultValue(false)
            .build()
    );

    private Vec3d grimPending = null;

    public KoodaGrimVelocity() {
        super(KoodaAddon.KOODA_COMBAT, "kooda-grim-velocity", "Robust Velocity for 1.21.10.");
    }

    @Override
    public void onDeactivate() {
        grimPending = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (!liquids.get() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;

        if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
            try {
                List<Integer> ints = getIntFields(packet);
                int myId = mc.player.getId();
                int index = -1;

                for (int i = 0; i < ints.size(); i++) {
                    if (ints.get(i) == myId) {
                        index = i;
                        break;
                    }
                }

                if (index != -1 && index + 3 < ints.size()) {
                    double vx = ints.get(index + 1) / 8000.0;
                    double vy = ints.get(index + 2) / 8000.0;
                    double vz = ints.get(index + 3) / 8000.0;

                    if (debug.get()) info("Velocity Packet Found: X" + vx + " Y" + vy);
                    processVelocity(event, vx, vy, vz);
                }
            } catch (Exception e) {
                if (debug.get()) warning("Velocity Error: " + e.getMessage());
            }
        } else if (event.packet instanceof ExplosionS2CPacket packet && explosions.get()) {
            try {
                List<Float> floats = getFloatFields(packet);
                if (floats.size() >= 3) {
                    double vx = floats.get(floats.size() - 3);
                    double vy = floats.get(floats.size() - 2);
                    double vz = floats.get(floats.size() - 1);

                    if (debug.get()) info("Explosion Packet Found: X" + vx + " Y" + vy);
                    processVelocity(event, vx, vy, vz);
                }
            } catch (Exception e) {
                if (debug.get()) warning("Explosion Error: " + e.getMessage());
            }
        }
    }

    private void processVelocity(PacketEvent.Receive event, double vx, double vy, double vz) {
        if (grimMode.get()) {
            double rng = 1.0;
            if (randomness.get() > 0) {
                rng = 1.0 + ((Math.random() - 0.5) * randomness.get());
            }
            grimPending = new Vec3d(vx * rng, vy * rng, vz * rng);
        } else {
            event.cancel();
            if (horizontal.get() > 0 || vertical.get() > 0) {
                double h = horizontal.get() / 100.0;
                double v = vertical.get() / 100.0;
                mc.player.setVelocity(mc.player.getVelocity().add(vx * h, vy * v, vz * h));
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || grimPending == null) return;

        double hFactor = horizontal.get() / 100.0;
        double vFactor = vertical.get() / 100.0;

        mc.player.setVelocity(mc.player.getVelocity().subtract(
                grimPending.x * (1 - hFactor),
                grimPending.y * (1 - vFactor),
                grimPending.z * (1 - hFactor)
        ));

        grimPending = null;
    }

    private List<Integer> getIntFields(Object obj) {
        List<Integer> list = new ArrayList<>();
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f.getType() == int.class) {
                try {
                    f.setAccessible(true);
                    list.add(f.getInt(obj));
                } catch (Exception ignored) { }
            }
        }
        return list;
    }

    private List<Float> getFloatFields(Object obj) {
        List<Float> list = new ArrayList<>();
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f.getType() == float.class) {
                try {
                    f.setAccessible(true);
                    list.add(f.getFloat(obj));
                } catch (Exception ignored) { }
            }
        }
        return list;
    }
}