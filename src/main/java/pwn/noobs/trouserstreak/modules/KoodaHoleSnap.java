package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class KoodaHoleSnap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // --- SETTINGS ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Movement method. Use Legit for Grim/NCP.")
            .defaultValue(Mode.Legit)
            .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("The radius to search for holes.")
            .defaultValue(3.0)
            .min(1.0)
            .sliderMax(5.0)
            .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Pull strength (Only for Rage mode).")
            .defaultValue(1.0)
            .min(0.1)
            .max(5.0)
            .visible(() -> mode.get() == Mode.Rage)
            .build()
    );

    // Removed Timer setting as mc.timer is inaccessible in 1.21 without Mixins
    // This ensures the module compiles and runs without crashing.

    private final Setting<Boolean> anchor = sgGeneral.add(new BoolSetting.Builder()
            .name("anchor")
            .description("Stops all movement when exactly over a hole to fall in perfectly.")
            .defaultValue(true)
            .build()
    );

    public KoodaHoleSnap() {
        super(KoodaAddon.KOODA_MOVEMENT, "kooda-hole-snap", "Magnetically pulls you into safe holes.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // ROBUSTNESS FIX: Using isGliding() instead of isFallFlying() for 1.21 compatibility
        if (mc.player.isUsingItem() || mc.player.isGliding() || mc.player.isClimbing()) return;

        // If in hole, optionally anchor and return
        if (isInHole()) {
            if (anchor.get() && mc.player.isOnGround()) {
                centerPlayer();
            }
            return;
        }

        BlockPos targetHole = findHole();

        if (targetHole != null) {
            Vec3d center = Vec3d.ofBottomCenter(targetHole);

            // ROBUSTNESS FIX: Manually constructing Vec3d since getPos() can be ambiguous in some mappings
            Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            double distToCenter = playerPos.distanceTo(new Vec3d(center.x, mc.player.getY(), center.z));

            // LOGIC SPLIT BASED ON MODE
            if (mode.get() == Mode.Rage) {
                handleRageMode(center, distToCenter);
            } else {
                handleLegitMode(center, distToCenter);
            }
        }
    }

    // --- MODES LOGIC ---

    private void handleRageMode(Vec3d center, double dist) {
        double x = center.x - mc.player.getX();
        double z = center.z - mc.player.getZ();
        double yaw = Math.atan2(z, x);

        // Raw Velocity Set (Fast but Detectable)
        double finalSpeed = Math.min(dist, speed.get());
        double moveX = Math.cos(yaw) * finalSpeed;
        double moveZ = Math.sin(yaw) * finalSpeed;

        mc.player.setVelocity(moveX, mc.player.getVelocity().y, moveZ);
    }

    private void handleLegitMode(Vec3d center, double dist) {
        // GRIM/NCP STRATEGY:
        // 1. Only modify motion significantly if in Air (AirStrafe).
        // 2. Use "Anchor" logic heavily (Stop X/Z motion when close to align).

        // If we are VERY close to the hole (horizontally), kill velocity to drop in
        if (dist < 0.3) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);

            // Nudge slightly to perfect center
            double nudgeX = (center.x - mc.player.getX()) * 0.5;
            double nudgeZ = (center.z - mc.player.getZ()) * 0.5;

            // Adding velocity is safer than setting it on Grim
            mc.player.addVelocity(nudgeX, 0, nudgeZ);
        }
        // If we are farther and IN THE AIR, guide towards hole
        else if (!mc.player.isOnGround()) {
            double x = center.x - mc.player.getX();
            double z = center.z - mc.player.getZ();
            double yaw = Math.atan2(z, x);

            // Very subtle pull (Air Strafe)
            double strafeSpeed = 0.05; // Low value for bypass
            double moveX = Math.cos(yaw) * strafeSpeed;
            double moveZ = Math.sin(yaw) * strafeSpeed;

            mc.player.addVelocity(moveX, 0, moveZ);
        }
    }

    // --- UTILS ---

    private void centerPlayer() {
        // Stops momentum to prevent walking out of hole
        mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
    }

    @Override
    public void onDeactivate() {
        // Timer reset logic removed for stability
    }

    private BlockPos findHole() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos bestHole = null;
        double closestDist = Double.MAX_VALUE;
        int r = (int) Math.ceil(range.get());

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -1; y <= 0; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (!mc.world.getBlockState(pos).isAir()) continue;

                    if (isValidHole(pos)) {
                        // ROBUSTNESS FIX: Manual position construction
                        Vec3d holeVec = Vec3d.ofBottomCenter(pos);
                        // Squared distance check
                        double dist = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                                .squaredDistanceTo(holeVec);

                        if (dist < closestDist && dist <= range.get() * range.get()) {
                            closestDist = dist;
                            bestHole = pos;
                        }
                    }
                }
            }
        }
        return bestHole;
    }

    private boolean isValidHole(BlockPos pos) {
        if (!isBlastResistant(pos.down())) return false;
        return isBlastResistant(pos.north()) &&
                isBlastResistant(pos.south()) &&
                isBlastResistant(pos.east()) &&
                isBlastResistant(pos.west());
    }

    private boolean isInHole() {
        return isValidHole(mc.player.getBlockPos());
    }

    private boolean isBlastResistant(BlockPos pos) {
        if (mc.world == null) return false;
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK || block == Blocks.CRYING_OBSIDIAN || block == Blocks.ENDER_CHEST;
    }

    public enum Mode {
        Rage,
        Legit
    }
}