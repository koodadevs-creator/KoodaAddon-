package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import pwn.noobs.trouserstreak.KoodaAddon;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SpawnGlitch extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> burstAmount = sgGeneral.add(new IntSetting.Builder()
            .name("burst-amount")
            .description("How many position packets to send to force the glitch.")
            .defaultValue(10)
            .min(1)
            .max(50)
            .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
            .name("debug")
            .description("Shows technical debug messages.")
            .defaultValue(false)
            .build()
    );

    private Vec3d savedPos;
    private boolean awaitingTp = false;

    public SpawnGlitch() {
        super(KoodaAddon.KOODA_UTILITY, "spawn-glitch", "Teleports back instantly if server moves you after /spawn.");
    }

    @Override
    public void onActivate() {
        savedPos = null;
        awaitingTp = false;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        String command = null;

        if (event.packet instanceof CommandExecutionC2SPacket p) {
            command = p.command();
        } else if (event.packet instanceof ChatMessageC2SPacket p) {
            command = p.chatMessage();
        }

        if (command != null && (command.equalsIgnoreCase("spawn") || command.startsWith("/spawn"))) {
            if (mc.player != null) {
                savedPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                awaitingTp = true;
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket && awaitingTp && savedPos != null) {

            double packetX = getPacketDouble(event.packet, "x", "getX");
            double packetY = getPacketDouble(event.packet, "y", "getY");
            double packetZ = getPacketDouble(event.packet, "z", "getZ");

            if (Double.isNaN(packetX)) packetX = 999999;

            double dist = Math.sqrt(Math.pow(packetX - savedPos.x, 2) + Math.pow(packetY - savedPos.y, 2) + Math.pow(packetZ - savedPos.z, 2));

            if (dist > 5) {
                ChatUtils.sendMsg(Text.literal("[Kooda] ").formatted(Formatting.AQUA)
                        .append(Text.literal("Try closer to spawn!").formatted(Formatting.WHITE)));

                if (debug.get()) ChatUtils.info("Detected Server TP (" + (int)dist + " blocks). Bursting...");

                int packets = burstAmount.get();
                try {
                    for (int i = 0; i < packets; i++) {
                        sendPositionPacket(savedPos.x, savedPos.y, savedPos.z, mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround());
                    }
                } catch (Exception e) {
                    if (debug.get()) ChatUtils.error("Burst failed: " + e.getMessage());
                }

                awaitingTp = false;
            }
        }
    }


    private double getPacketDouble(Packet<?> packet, String fieldName, String methodName) {
        Class<?> clazz = packet.getClass();

        try {
            Field field = clazz.getField(fieldName);
            return field.getDouble(packet);
        } catch (Exception ignored) {}

        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getDouble(packet);
        } catch (Exception ignored) {}

        try {
            Method method = clazz.getMethod(methodName);
            return (double) method.invoke(packet);
        } catch (Exception ignored) {}

        try {
            Method method = clazz.getMethod(fieldName);
            return (double) method.invoke(packet);
        } catch (Exception ignored) {}

        return Double.NaN;
    }

    private void sendPositionPacket(double x, double y, double z, float yaw, float pitch, boolean onGround) throws Exception {
        Class<?> fullPacketClass = null;
        for (Class<?> clazz : PlayerMoveC2SPacket.class.getDeclaredClasses()) {
            if (clazz.getSimpleName().equals("Full")) {
                fullPacketClass = clazz;
                break;
            }
        }

        if (fullPacketClass == null) return;

        Constructor<?>[] constructors = fullPacketClass.getDeclaredConstructors();
        Object packet = null;

        for (Constructor<?> c : constructors) {
            c.setAccessible(true);
            int paramCount = c.getParameterCount();

            try {
                if (paramCount == 6) {
                    packet = c.newInstance(x, y, z, yaw, pitch, onGround);
                } else if (paramCount == 7) {
                    boolean collision = mc.player.horizontalCollision;
                    packet = c.newInstance(x, y, z, yaw, pitch, onGround, collision);
                }
            } catch (Exception ignored) {}

            if (packet != null) break;
        }

        if (packet != null) {
            mc.player.networkHandler.sendPacket((Packet<?>) packet);
        }
    }
}