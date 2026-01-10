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
    private final SettingGroup sgLogic = settings.createGroup("Logic");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("range")
            .description("Radius around the player to search for holes.")
            .defaultValue(8)
            .min(1)
            .sliderMax(32)
            .build()
    );

    private final Setting<Boolean> doubles = sgLogic.add(new BoolSetting.Builder()
            .name("doubles")
            .description("Detects 2x1 and 1x2 holes.")
            .defaultValue(true)
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

    private final List<Hole> holes = new ArrayList<>();
    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();

    public KoodaHoleESP() {
        super(KoodaAddon.KOODA_RENDER, "kooda-hole-esp", "High-performance Hole ESP with double hole support.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        holes.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();
        int rSq = r * r;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -r; y <= r; y++) {
                    if (x * x + y * y + z * z > rSq) continue;

                    mutablePos.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);

                    if (ignoreOwn.get() && mutablePos.equals(playerPos)) continue;

                    HoleType type = checkHole(mutablePos);
                    if (type != HoleType.NONE) {
                        holes.add(new Hole(mutablePos.toImmutable(), type, false));
                    } else if (doubles.get()) {
                        HoleType dType = checkDoubleHole(mutablePos);
                        if (dType != HoleType.NONE) {
                            holes.add(new Hole(mutablePos.toImmutable(), dType, true));
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        for (Hole hole : holes) {
            renderHole(event, hole.pos, hole.type, hole.isDouble);
        }
    }

    private void renderHole(Render3DEvent event, BlockPos pos, HoleType type, boolean isDouble) {
        SettingColor side;
        SettingColor line;

        switch (type) {
            case SAFE -> { side = safeColorSide.get(); line = safeColorLine.get(); }
            case MIXED -> { side = mixedColorSide.get(); line = mixedColorLine.get(); }
            default -> { side = unsafeColorSide.get(); line = unsafeColorLine.get(); }
        }

        double h = height.get();

        if (renderShape.get() == RenderShape.Box) {
            event.renderer.box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + h, pos.getZ() + 1, side, line, shapeMode.get(), 0);
        } else {
            renderCustomCylinder(event, pos, side, line, h);
        }
    }

    private void renderCustomCylinder(Render3DEvent event, BlockPos pos, SettingColor sideColor, SettingColor lineColor, double height) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        double r = 0.5;
        int segments = 24;

        ShapeMode mode = shapeMode.get();
        double prevX = x + r;
        double prevZ = z;

        for (int i = 1; i <= segments; i++) {
            double angle = (Math.PI * 2 * i) / segments;
            double nextX = x + Math.cos(angle) * r;
            double nextZ = z + Math.sin(angle) * r;

            if (mode == ShapeMode.Sides || mode == ShapeMode.Both) {
                event.renderer.quad(prevX, y, prevZ, prevX, y + height, prevZ, nextX, y + height, nextZ, nextX, y, nextZ, sideColor);
            }

            if (mode == ShapeMode.Lines || mode == ShapeMode.Both) {
                if (bottomQuad.get()) event.renderer.line(prevX, y, prevZ, nextX, y, nextZ, lineColor);
                if (topQuad.get()) event.renderer.line(prevX, y + height, prevZ, nextX, y + height, nextZ, lineColor);
                if (i % 6 == 0) event.renderer.line(prevX, y, prevZ, prevX, y + height, prevZ, lineColor);
            }

            prevX = nextX;
            prevZ = nextZ;
        }
    }

    private HoleType checkHole(BlockPos pos) {
        if (!isAir(pos)) return HoleType.NONE;

        for (int i = 1; i <= holeHeight.get(); i++) {
            if (!isAir(pos.up(i))) return HoleType.NONE;
        }

        if (!isValidBlock(pos.down())) return HoleType.NONE;

        int bedrockCount = 0;
        int obsidianCount = 0;

        if (mc.world.getBlockState(pos.down()).getBlock() == Blocks.BEDROCK) bedrockCount++;
        else obsidianCount++;

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos offset = pos.offset(dir);
            if (!isValidBlock(offset)) return HoleType.NONE;

            if (mc.world.getBlockState(offset).getBlock() == Blocks.BEDROCK) bedrockCount++;
            else obsidianCount++;
        }

        if (bedrockCount == 5) return HoleType.SAFE;
        if (obsidianCount == 5) return HoleType.UNSAFE;
        return HoleType.MIXED;
    }

    private HoleType checkDoubleHole(BlockPos pos) {
        if (!isAir(pos) || !isAir(pos.up())) return HoleType.NONE;

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(dir);
            if (!isAir(neighbor) || !isAir(neighbor.up())) continue;

            int bedrock = 0;
            int obsidian = 0;
            boolean valid = true;

            for (BlockPos p : new BlockPos[]{pos, neighbor}) {
                if (!isValidBlock(p.down())) { valid = false; break; }
                if (mc.world.getBlockState(p.down()).getBlock() == Blocks.BEDROCK) bedrock++; else obsidian++;

                for (Direction d : Direction.Type.HORIZONTAL) {
                    BlockPos side = p.offset(d);
                    if (side.equals(pos) || side.equals(neighbor)) continue;
                    if (!isValidBlock(side)) { valid = false; break; }
                    if (mc.world.getBlockState(side).getBlock() == Blocks.BEDROCK) bedrock++; else obsidian++;
                }
                if (!valid) break;
            }

            if (valid) {
                if (obsidian == 0) return HoleType.SAFE;
                if (bedrock == 0) return HoleType.UNSAFE;
                return HoleType.MIXED;
            }
        }
        return HoleType.NONE;
    }

    private boolean isAir(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || (webs.get() && state.getBlock() == Blocks.COBWEB);
    }

    private boolean isValidBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.getBlock() == Blocks.BEDROCK
                || state.getBlock() == Blocks.OBSIDIAN
                || state.getBlock() == Blocks.CRYING_OBSIDIAN
                || state.getBlock() == Blocks.NETHERITE_BLOCK
                || state.getBlock() == Blocks.ENDER_CHEST
                || state.getBlock() == Blocks.RESPAWN_ANCHOR
                || state.getBlock() == Blocks.ANVIL;
    }

    public enum RenderShape { Box, Cylinder }
    private enum HoleType { NONE, SAFE, MIXED, UNSAFE }
    private record Hole(BlockPos pos, HoleType type, boolean isDouble) {}
}