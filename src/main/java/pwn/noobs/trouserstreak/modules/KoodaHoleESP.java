package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public class KoodaHoleESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- GENERAL SETTINGS ---
    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("range")
            .description("Radius around the player to search for holes.")
            .defaultValue(8)
            .min(1)
            .sliderMax(12)
            .build()
    );

    private final Setting<Integer> holeHeight = sgGeneral.add(new IntSetting.Builder()
            .name("min-height")
            .description("Minimum height of air required above the hole.")
            .defaultValue(1)
            .min(1)
            .build()
    );

    private final Setting<Boolean> ignoreOwn = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-own")
            .description("Does not render the hole you are currently standing in.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> webs = sgGeneral.add(new BoolSetting.Builder()
            .name("webs")
            .description("Considers holes with webs inside as valid holes.")
            .defaultValue(true)
            .build()
    );

    // --- RENDER SETTINGS ---
    private final Setting<RenderShape> renderShape = sgRender.add(new EnumSetting.Builder<RenderShape>()
            .name("render-shape")
            .description("The visual shape used to render the holes.")
            .defaultValue(RenderShape.Cylinder)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How to render the holes (Lines, Sides, or Both).")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<Double> height = sgRender.add(new DoubleSetting.Builder()
            .name("render-height")
            .description("Height of the rendered object.")
            .defaultValue(0.2)
            .min(0.0)
            .max(2.0)
            .build()
    );

    private final Setting<Boolean> topQuad = sgRender.add(new BoolSetting.Builder()
            .name("render-top")
            .description("Renders the top surface of the hole ESP.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> bottomQuad = sgRender.add(new BoolSetting.Builder()
            .name("render-bottom")
            .description("Renders the bottom surface of the hole ESP.")
            .defaultValue(true)
            .build()
    );

    // -- Safe Holes (Bedrock - Indestructible) --
    private final Setting<SettingColor> safeColorSide = sgRender.add(new ColorSetting.Builder()
            .name("safe-fill")
            .description("Fill color for Safe (Bedrock) holes.")
            .defaultValue(new SettingColor(0, 255, 0, 40))
            .build()
    );

    private final Setting<SettingColor> safeColorLine = sgRender.add(new ColorSetting.Builder()
            .name("safe-line")
            .description("Line color for Safe (Bedrock) holes.")
            .defaultValue(new SettingColor(0, 255, 0, 200))
            .build()
    );

    // -- Mixed Holes (Bedrock + Obsidian - Partially Safe) --
    private final Setting<SettingColor> mixedColorSide = sgRender.add(new ColorSetting.Builder()
            .name("mixed-fill")
            .description("Fill color for Mixed (Bedrock + Obsidian) holes.")
            .defaultValue(new SettingColor(255, 255, 0, 40))
            .build()
    );

    private final Setting<SettingColor> mixedColorLine = sgRender.add(new ColorSetting.Builder()
            .name("mixed-line")
            .description("Line color for Mixed holes.")
            .defaultValue(new SettingColor(255, 255, 0, 200))
            .build()
    );

    // -- Unsafe Holes (Obsidian - Breakable) --
    private final Setting<SettingColor> unsafeColorSide = sgRender.add(new ColorSetting.Builder()
            .name("unsafe-fill")
            .description("Fill color for Unsafe (Obsidian) holes.")
            .defaultValue(new SettingColor(255, 0, 0, 40))
            .build()
    );

    private final Setting<SettingColor> unsafeColorLine = sgRender.add(new ColorSetting.Builder()
            .name("unsafe-line")
            .description("Line color for Unsafe (Obsidian) holes.")
            .defaultValue(new SettingColor(255, 0, 0, 200))
            .build()
    );

    // Robustness: Cache list to prevent lag during render loop
    private final List<Hole> holes = new ArrayList<>();

    // Robustness: Reusable MutablePos to avoid garbage collection overhead
    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();

    public KoodaHoleESP() {
        super(KoodaAddon.KOODA_RENDER, "kooda-hole-esp", "High-performance Hole ESP with web support and thread-safety.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Clear cache
        holes.clear();

        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();

        // Robustness: Using Mutable BlockPos for heavy scanning loops
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -r; y <= r; y++) {
                    // Optimization: Spherical check to reduce unnecessary checks in corners
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) > r * 1.5) continue;

                    mutablePos.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);

                    if (ignoreOwn.get() && mutablePos.equals(playerPos)) continue;

                    HoleType type = checkHole(mutablePos);
                    if (type != HoleType.NONE) {
                        // Create an immutable copy for the list
                        holes.add(new Hole(mutablePos.toImmutable(), type));
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // Robustness: Thread safety - Create a copy of the list or iterate safely
        // Since we are on main thread for both tick and render in Meteor usually, basic iteration is fine.
        // If async scan is added later, synchronize this block.

        for (Hole hole : holes) {
            renderHole(event, hole.pos, hole.type);
        }
    }

    private void renderHole(Render3DEvent event, BlockPos pos, HoleType type) {
        SettingColor side;
        SettingColor line;

        switch (type) {
            case SAFE:
                side = safeColorSide.get();
                line = safeColorLine.get();
                break;
            case MIXED:
                side = mixedColorSide.get();
                line = mixedColorLine.get();
                break;
            case UNSAFE:
            default:
                side = unsafeColorSide.get();
                line = unsafeColorLine.get();
                break;
        }

        double h = height.get();

        if (renderShape.get() == RenderShape.Box) {
            event.renderer.box(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + h, pos.getZ() + 1,
                    side, line, shapeMode.get(), 0
            );
        } else {
            renderCustomCylinder(event, pos, side, line, h);
        }
    }

    private void renderCustomCylinder(Render3DEvent event, BlockPos pos, SettingColor sideColor, SettingColor lineColor, double height) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        double r = 0.5;
        int segments = 16;

        ShapeMode mode = shapeMode.get();

        // Optimization: Use precomputed values if possible, or just local vars
        double prevX = x + r; // cos(0) * r
        double prevZ = z;     // sin(0) * r

        for (int i = 1; i <= segments; i++) {
            double angle = (Math.PI * 2 * i) / segments;
            double nextX = x + Math.cos(angle) * r;
            double nextZ = z + Math.sin(angle) * r;

            // Sides
            if (mode == ShapeMode.Sides || mode == ShapeMode.Both) {
                event.renderer.quad(
                        prevX, y, prevZ,
                        prevX, y + height, prevZ,
                        nextX, y + height, nextZ,
                        nextX, y, nextZ,
                        sideColor
                );
            }

            // Lines
            if (mode == ShapeMode.Lines || mode == ShapeMode.Both) {
                // Bottom Ring
                if (bottomQuad.get()) event.renderer.line(prevX, y, prevZ, nextX, y, nextZ, lineColor);
                // Top Ring
                if (topQuad.get()) event.renderer.line(prevX, y + height, prevZ, nextX, y + height, nextZ, lineColor);
                // Verticals (only 4 for cleaner look, or all if preferred)
                if (i % 4 == 0) event.renderer.line(prevX, y, prevZ, prevX, y + height, prevZ, lineColor);
            }

            prevX = nextX;
            prevZ = nextZ;
        }
    }

    private HoleType checkHole(BlockPos pos) {
        // 1. Center Check (Air or Web)
        BlockState centerState = mc.world.getBlockState(pos);
        if (!centerState.isAir() && !(webs.get() && centerState.getBlock() == Blocks.COBWEB)) {
            return HoleType.NONE;
        }

        // 2. Headroom Check
        for (int i = 1; i <= holeHeight.get(); i++) {
            BlockState upState = mc.world.getBlockState(pos.up(i));
            if (!upState.isAir() && !(webs.get() && upState.getBlock() == Blocks.COBWEB)) {
                return HoleType.NONE;
            }
        }

        // 3. Floor Check
        if (!isValidBlock(pos.down())) return HoleType.NONE;

        int bedrockCount = 0;
        int obsidianCount = 0;

        // Check Floor Type
        if (mc.world.getBlockState(pos.down()).getBlock() == Blocks.BEDROCK) bedrockCount++;
        else obsidianCount++;

        // 4. Check Surroundings
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos offset = pos.offset(dir);
            if (!isValidBlock(offset)) return HoleType.NONE;

            BlockState state = mc.world.getBlockState(offset);
            if (state.getBlock() == Blocks.BEDROCK) bedrockCount++;
            else obsidianCount++;
        }

        // 5. Determine Type
        if (bedrockCount == 5) return HoleType.SAFE;
        if (obsidianCount == 5) return HoleType.UNSAFE;
        return HoleType.MIXED;
    }

    private boolean isValidBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        // Robustness: Added Crying Obsidian, Ancient Debris, and Netherite Block
        return state.getBlock() == Blocks.BEDROCK
                || state.getBlock() == Blocks.OBSIDIAN
                || state.getBlock() == Blocks.CRYING_OBSIDIAN
                || state.getBlock() == Blocks.NETHERITE_BLOCK
                || state.getBlock() == Blocks.ANCIENT_DEBRIS
                || state.getBlock() == Blocks.ENDER_CHEST
                || state.getBlock() == Blocks.ANVIL
                || state.getBlock() == Blocks.CHIPPED_ANVIL
                || state.getBlock() == Blocks.DAMAGED_ANVIL
                || state.getBlock() == Blocks.RESPAWN_ANCHOR; // Be careful with anchors, but they are blast resistant
    }

    public enum RenderShape {
        Box,
        Cylinder
    }

    private enum HoleType {
        NONE,
        SAFE,
        MIXED,
        UNSAFE
    }

    // Simple data class to store hole info for the render thread
    private static class Hole {
        public final BlockPos pos;
        public final HoleType type;

        public Hole(BlockPos pos, HoleType type) {
            this.pos = pos;
            this.type = type;
        }
    }
}