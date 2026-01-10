package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class KoodaHoleSnap extends Module {
    public enum Mode {
        Instant,
        Vector,
        Legit
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Movement calculation method.")
            .defaultValue(Mode.Vector)
            .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Radius to search for holes.")
            .defaultValue(4.5)
            .min(1.0)
            .sliderMax(6.0)
            .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Movement speed factor.")
            .defaultValue(1.5)
            .min(0.1)
            .max(5.0)
            .visible(() -> mode.get() != Mode.Legit)
            .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-disable")
            .description("Disables the module when you are safely in a hole.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders the target hole.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shape is rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 25))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 200))
            .visible(render::get)
            .build()
    );

    private BlockPos currentTarget;

    public KoodaHoleSnap() {
        super(KoodaAddon.KOODA_MOVEMENT, "kooda-hole-snap", "Robust hole magnet system.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isUsingItem() || mc.player.isGliding() || mc.player.isClimbing()) {
            return;
        }

        if (isInHole()) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);

            Vec3d center = Vec3d.ofBottomCenter(mc.player.getBlockPos());
            double distX = center.x - mc.player.getX();
            double distZ = center.z - mc.player.getZ();

            if (Math.sqrt(distX * distX + distZ * distZ) > 0.1) {
                mc.player.addVelocity(distX * 0.2, 0, distZ * 0.2);
            }

            if (autoDisable.get()) {
                toggle();
            }
            return;
        }

        currentTarget = findHole();

        if (currentTarget != null) {
            Vec3d targetVec = Vec3d.ofBottomCenter(currentTarget);
            double yawRad = Math.atan2(targetVec.z - mc.player.getZ(), targetVec.x - mc.player.getX());
            double dist = Math.sqrt(mc.player.squaredDistanceTo(targetVec.x, mc.player.getY(), targetVec.z));

            if (mode.get() == Mode.Instant) {
                double moveSpeed = Math.min(dist, speed.get());
                mc.player.setVelocity(Math.cos(yawRad) * moveSpeed, mc.player.getVelocity().y, Math.sin(yawRad) * moveSpeed);
            } else if (mode.get() == Mode.Vector) {
                double speedVal = speed.get();
                if (mc.player.isOnGround()) speedVal *= 0.5;
                mc.player.addVelocity(Math.cos(yawRad) * speedVal * 0.1, 0, Math.sin(yawRad) * speedVal * 0.1);
            } else if (mode.get() == Mode.Legit) {
                float yaw = (float) Math.toDegrees(yawRad) - 90;
                Rotations.rotate(yaw, mc.player.getPitch());

                if (!mc.player.isOnGround()) {
                    double legitSpeed = 0.05;
                    mc.player.addVelocity(Math.cos(yawRad) * legitSpeed, 0, Math.sin(yawRad) * legitSpeed);
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || currentTarget == null) return;
        event.renderer.box(currentTarget, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private BlockPos findHole() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos bestHole = null;
        double closest = Double.MAX_VALUE;
        int r = (int) Math.ceil(range.get());

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -2; y <= 1; y++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (mc.world.getBlockState(pos).isAir() &&
                            mc.world.getBlockState(pos.up()).isAir() &&
                            mc.world.getBlockState(pos.up(2)).isAir()) {

                        if (isSafe(pos)) {
                            double dist = mc.player.squaredDistanceTo(Vec3d.ofBottomCenter(pos));
                            if (dist < closest && dist <= range.get() * range.get()) {
                                closest = dist;
                                bestHole = pos;
                            }
                        }
                    }
                }
            }
        }
        return bestHole;
    }

    private boolean isInHole() {
        return isSafe(mc.player.getBlockPos());
    }

    private boolean isSafe(BlockPos pos) {
        return isBlastResistant(pos.down()) &&
                isBlastResistant(pos.north()) &&
                isBlastResistant(pos.south()) &&
                isBlastResistant(pos.east()) &&
                isBlastResistant(pos.west());
    }

    private boolean isBlastResistant(BlockPos pos) {
        if (mc.world == null) return false;
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN ||
                block == Blocks.BEDROCK ||
                block == Blocks.CRYING_OBSIDIAN ||
                block == Blocks.ENDER_CHEST ||
                block == Blocks.NETHERITE_BLOCK ||
                block == Blocks.ANVIL ||
                block == Blocks.CHIPPED_ANVIL ||
                block == Blocks.DAMAGED_ANVIL;
    }
}