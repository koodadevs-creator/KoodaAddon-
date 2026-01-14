package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.Random;

public class Confuse extends Module {

    public enum Mode {
        RandomTP,
        Switch,
        Circle
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("The mode of confusion.")
            .defaultValue(Mode.RandomTP)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Tick delay between movements.")
            .defaultValue(3)
            .min(0)
            .sliderMax(20)
            .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("radius")
            .description("Range to search for targets.")
            .defaultValue(6)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
            .name("priority")
            .description("How to select the target.")
            .defaultValue(SortPriority.LowestHealth)
            .build()
    );

    private final Setting<Integer> circleSpeed = sgGeneral.add(new IntSetting.Builder()
            .name("circle-speed")
            .description("Speed of rotation in Circle mode.")
            .defaultValue(10)
            .min(1)
            .sliderMax(180)
            .visible(() -> mode.get() == Mode.Circle)
            .build()
    );

    private final Setting<Boolean> moveThroughBlocks = sgGeneral.add(new BoolSetting.Builder()
            .name("move-through-blocks")
            .description("Attempt to teleport through walls.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> budgetGraphics = sgGeneral.add(new BoolSetting.Builder()
            .name("budget-graphics")
            .description("Reduces render quality for better performance.")
            .defaultValue(false)
            .build()
    );

    private final Setting<SettingColor> circleColor = sgGeneral.add(new ColorSetting.Builder()
            .name("circle-color")
            .description("Color for the render.")
            .defaultValue(new SettingColor(0, 255, 0))
            .visible(budgetGraphics::get)
            .build()
    );

    private final Random random = new Random();
    private int delayTimer = 0;
    private double circleProgress = 0;
    private double rainbowOffset = 0.0;
    private Entity target = null;

    public Confuse() {
        super(KoodaAddon.KOODA_MOVEMENT, "confuse", "Teleports around enemies to confuse them.");
    }

    @Override
    public void onActivate() {
        delayTimer = 0;
        circleProgress = 0;
        rainbowOffset = 0.0;
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        delayTimer++;
        if (delayTimer < delay.get()) return;
        delayTimer = 0;

        target = getTarget();
        if (target == null) return;

        switch (mode.get()) {
            case RandomTP -> handleRandomTP();
            case Switch -> handleSwitch();
            case Circle -> handleCircle();
        }
    }

    private void handleRandomTP() {
        double r = range.get();
        double half = r / 2.0;

        double x = (random.nextDouble() * r) - half;
        double z = (random.nextDouble() * r) - half;

        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d goal = targetPos.add(x, 0, z);

        if (!isSafePos(goal)) {
            goal = new Vec3d(goal.x, mc.player.getY(), goal.z);
        }

        if (isSafePos(goal) && canRaycast(goal)) {
            mc.player.updatePosition(goal.x, goal.y, goal.z);
        } else {
            delayTimer = Math.max(0, delay.get() - 1);
        }
    }

    private void handleSwitch() {
        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d diff = targetPos.subtract(playerPos);

        double half = range.get() / 2.0;
        double clampedX = MathHelper.clamp(diff.x, -half, half);
        double clampedY = MathHelper.clamp(diff.y, -half, half);
        double clampedZ = MathHelper.clamp(diff.z, -half, half);

        Vec3d goal = targetPos.add(clampedX, clampedY, clampedZ);

        if (canRaycast(goal)) {
            mc.player.updatePosition(goal.x, goal.y, goal.z);
        } else {
            delayTimer = Math.max(0, delay.get() - 1);
        }
    }

    private void handleCircle() {
        delayTimer = delay.get();

        circleProgress += circleSpeed.get();
        if (circleProgress >= 360) circleProgress -= 360;

        double rad = Math.toRadians(circleProgress);
        double radius = 3.0;

        double x = Math.sin(rad) * radius;
        double z = Math.cos(rad) * radius;

        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d goal = new Vec3d(targetPos.x + x, mc.player.getY(), targetPos.z + z);

        if (canRaycast(goal) || moveThroughBlocks.get()) {
            mc.player.updatePosition(goal.x, goal.y, goal.z);
        }
    }

    private boolean isSafePos(Vec3d pos) {
        BlockPos bp = BlockPos.ofFloored(pos);
        return mc.world.getBlockState(bp).getBlock() == Blocks.AIR;
    }

    private boolean canRaycast(Vec3d goal) {
        if (moveThroughBlocks.get()) return true;

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                goal,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY,
                mc.player
        ));

        return !hit.isInsideBlock();
    }

    private Entity getTarget() {
        return mc.world.getPlayers().stream()
                .filter(e -> e != mc.player)
                .filter(e -> !e.isDead())
                .filter(e -> !Friends.get().isFriend(e))
                .filter(e -> mc.player.distanceTo(e) <= range.get())
                .min(getSortComparator())
                .orElse(null);
    }

    private Comparator<Entity> getSortComparator() {
        return switch (priority.get()) {
            case LowestHealth -> Comparator.comparingDouble(e -> ((PlayerEntity) e).getHealth() + ((PlayerEntity) e).getAbsorptionAmount());
            case HighestHealth -> (e1, e2) -> Double.compare(((PlayerEntity) e2).getHealth(), ((PlayerEntity) e1).getHealth());
            case LowestDistance -> Comparator.comparingDouble(e -> mc.player.distanceTo(e));
            default -> Comparator.comparingDouble(e -> mc.player.distanceTo(e));
        };
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null) return;

        boolean simple = budgetGraphics.get();
        Vec3d lastPos = null;

        rainbowOffset += simple ? 0 : 1.0;
        if (rainbowOffset > 360) rainbowOffset = 0;

        int step = simple ? 10 : 2;
        Vec3d tPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        double y = tPos.y + (target.getHeight() / 2.0);

        for (int i = 0; i <= 360; i += step) {
            double rad = Math.toRadians(i);
            double sin = Math.sin(rad) * 3;
            double cos = Math.cos(rad) * 3;

            Vec3d currentPos = new Vec3d(tPos.x + sin, y, tPos.z + cos);

            Color c;
            if (simple) {
                c = circleColor.get();
            } else {
                c = getRainbowColor(i);
            }

            if (lastPos != null) {
                event.renderer.line(lastPos.x, lastPos.y, lastPos.z, currentPos.x, currentPos.y, currentPos.z, c);
            }
            lastPos = currentPos;
        }
    }

    private Color getRainbowColor(int index) {
        double rot = (index + rainbowOffset) % 360;
        float hue = (float) (rot / 360.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }
}