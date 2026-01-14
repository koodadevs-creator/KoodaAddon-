package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import pwn.noobs.trouserstreak.KoodaAddon;

import java.lang.reflect.Field;
import java.util.List;

public class AntiCrash extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThresholds = settings.createGroup("Thresholds");

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
            .name("chat-info")
            .description("Notifies you in chat when a crash attempt is blocked.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> maxCoordinates = sgThresholds.add(new DoubleSetting.Builder()
            .name("max-coordinates")
            .description("Maximum valid coordinate value.")
            .defaultValue(30000000.0)
            .min(100000.0)
            .build()
    );

    private final Setting<Double> maxExplosionRadius = sgThresholds.add(new DoubleSetting.Builder()
            .name("max-explosion-radius")
            .description("Maximum valid explosion radius.")
            .defaultValue(1000.0)
            .min(10.0)
            .build()
    );

    private final Setting<Integer> maxExplosionBlocks = sgThresholds.add(new IntSetting.Builder()
            .name("max-explosion-blocks")
            .description("Maximum amount of blocks affected by a single explosion.")
            .defaultValue(100000)
            .min(100)
            .build()
    );

    private final Setting<Double> maxVelocity = sgThresholds.add(new DoubleSetting.Builder()
            .name("max-velocity")
            .description("Maximum velocity value.")
            .defaultValue(30000000.0)
            .min(1000.0)
            .build()
    );

    private final Setting<Integer> maxParticles = sgThresholds.add(new IntSetting.Builder()
            .name("max-particles")
            .description("Maximum particle count.")
            .defaultValue(100000)
            .min(100)
            .build()
    );

    public AntiCrash() {
        super(KoodaAddon.KOODA_EXPLOIT, "anti-crash", "Prevents server-side packets intended to crash your client.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        try {
            if (event.packet instanceof ExplosionS2CPacket packet) {
                checkExplosion(event, packet);
            } else if (event.packet instanceof ParticleS2CPacket packet) {
                checkParticles(event, packet);
            } else if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
                checkPosLook(event, packet);
            } else if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
                checkVelocity(event, packet);
            }
        } catch (Exception e) {
        }
    }

    private void checkExplosion(PacketEvent.Receive event, ExplosionS2CPacket packet) {
        double x = PacketUtils.getDouble(packet, 0);
        double y = PacketUtils.getDouble(packet, 1);
        double z = PacketUtils.getDouble(packet, 2);
        float radius = PacketUtils.getFloat(packet, 0);

        List<BlockPos> blocks = PacketUtils.getList(packet);

        float pVelX = PacketUtils.getFloat(packet, 1);
        float pVelY = PacketUtils.getFloat(packet, 2);
        float pVelZ = PacketUtils.getFloat(packet, 3);

        if (Math.abs(x) > maxCoordinates.get() || Math.abs(y) > maxCoordinates.get() || Math.abs(z) > maxCoordinates.get()) {
            cancel(event, "Explosion Position");
            return;
        }
        if (radius > maxExplosionRadius.get()) {
            cancel(event, "Explosion Radius");
            return;
        }
        if (blocks != null && blocks.size() > maxExplosionBlocks.get()) {
            cancel(event, "Explosion Blocks");
            return;
        }
        if (Math.abs(pVelX) > maxVelocity.get() || Math.abs(pVelY) > maxVelocity.get() || Math.abs(pVelZ) > maxVelocity.get()) {
            cancel(event, "Explosion Player Velocity");
        }
    }

    private void checkParticles(PacketEvent.Receive event, ParticleS2CPacket packet) {
        int count = PacketUtils.getInt(packet, PacketUtils.getIntCount(packet) - 1);
        if (count > maxParticles.get()) {
            cancel(event, "Particle Count");
        }
    }

    private void checkPosLook(PacketEvent.Receive event, PlayerPositionLookS2CPacket packet) {
        double x = PacketUtils.getDouble(packet, 0);
        double y = PacketUtils.getDouble(packet, 1);
        double z = PacketUtils.getDouble(packet, 2);

        if (Math.abs(x) > maxCoordinates.get() || Math.abs(y) > maxCoordinates.get() || Math.abs(z) > maxCoordinates.get()) {
            cancel(event, "Player Position");
        }
    }

    private void checkVelocity(PacketEvent.Receive event, EntityVelocityUpdateS2CPacket packet) {
        int velX = PacketUtils.getInt(packet, 1);
        int velY = PacketUtils.getInt(packet, 2);
        int velZ = PacketUtils.getInt(packet, 3);

        if (Math.abs(velX) > maxVelocity.get() || Math.abs(velY) > maxVelocity.get() || Math.abs(velZ) > maxVelocity.get()) {
            cancel(event, "Entity Velocity");
        }
    }

    private void cancel(PacketEvent.Receive event, String reason) {
        event.cancel();
        if (chatInfo.get()) {
            ChatUtils.warning("AntiCrash prevented: " + reason);
        }
    }

    private static class PacketUtils {
        public static double getDouble(Object packet, int index) {
            return getField(packet, double.class, index).doubleValue();
        }

        public static float getFloat(Object packet, int index) {
            return getField(packet, float.class, index).floatValue();
        }

        public static int getInt(Object packet, int index) {
            return getField(packet, int.class, index).intValue();
        }

        public static List getList(Object packet) {
            try {
                for (Field f : packet.getClass().getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return (List) f.get(packet);
                    }
                }
            } catch (Exception e) { return null; }
            return null;
        }

        public static int getIntCount(Object packet) {
            int count = 0;
            for (Field f : packet.getClass().getDeclaredFields()) {
                if (f.getType() == int.class) count++;
            }
            return count;
        }

        private static <T> Number getField(Object packet, Class<?> type, int index) {
            try {
                int current = 0;
                for (Field f : packet.getClass().getDeclaredFields()) {
                    if (f.getType() == type) {
                        if (current == index) {
                            f.setAccessible(true);
                            return (Number) f.get(packet);
                        }
                        current++;
                    }
                }
            } catch (Exception e) { return 0; }
            return 0;
        }
    }
}