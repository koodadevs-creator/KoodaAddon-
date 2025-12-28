package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class KoodaStorageESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChest = settings.createGroup("Chests");
    private final SettingGroup sgTrapped = settings.createGroup("Trapped Chests");
    private final SettingGroup sgEnder = settings.createGroup("Ender Chests");
    private final SettingGroup sgShulker = settings.createGroup("Shulkers");
    private final SettingGroup sgBarrel = settings.createGroup("Barrels");

    // --- General Settings ---
    private final Setting<Double> renderDistance = sgGeneral.add(new DoubleSetting.Builder()
            .name("render-distance")
            .description("Maximum distance to render ESP.")
            .defaultValue(128)
            .min(0)
            .sliderMax(256)
            .build()
    );

    private final Setting<Integer> maxBlocks = sgGeneral.add(new IntSetting.Builder()
            .name("max-blocks")
            .description("Limit the number of rendered blocks to prevent FPS drops in huge stashes.")
            .defaultValue(1000)
            .min(1)
            .sliderMax(5000)
            .build()
    );

    // --- Chest Settings ---
    private final Setting<Boolean> chestEnabled = sgChest.add(new BoolSetting.Builder()
            .name("display-chests")
            .description("Render chests.")
            .defaultValue(true)
            .build()
    );
    private final Setting<ShapeMode> chestShape = sgChest.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How to render chests.")
            .defaultValue(ShapeMode.Both)
            .build()
    );
    private final Setting<SettingColor> chestSideColor = sgChest.add(new ColorSetting.Builder()
            .name("fill-color")
            .description("The color of the sides.")
            .defaultValue(new SettingColor(255, 160, 0, 50))
            .build()
    );
    private final Setting<SettingColor> chestLineColor = sgChest.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The color of the lines.")
            .defaultValue(new SettingColor(255, 160, 0, 255))
            .build()
    );

    // --- Trapped Chest Settings ---
    private final Setting<Boolean> trappedEnabled = sgTrapped.add(new BoolSetting.Builder()
            .name("display-trapped")
            .description("Render trapped chests.")
            .defaultValue(true)
            .build()
    );
    private final Setting<ShapeMode> trappedShape = sgTrapped.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How to render trapped chests.")
            .defaultValue(ShapeMode.Both)
            .build()
    );
    private final Setting<SettingColor> trappedSideColor = sgTrapped.add(new ColorSetting.Builder()
            .name("fill-color")
            .description("The color of the sides (Red for Danger).")
            .defaultValue(new SettingColor(255, 0, 0, 50))
            .build()
    );
    private final Setting<SettingColor> trappedLineColor = sgTrapped.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The color of the lines.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
    );

    // --- Ender Chest Settings ---
    private final Setting<Boolean> echestEnabled = sgEnder.add(new BoolSetting.Builder()
            .name("display-echests")
            .description("Render ender chests.")
            .defaultValue(true)
            .build()
    );
    private final Setting<ShapeMode> echestShape = sgEnder.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How to render ender chests.")
            .defaultValue(ShapeMode.Both)
            .build()
    );
    private final Setting<Boolean> echestTracer = sgEnder.add(new BoolSetting.Builder()
            .name("tracer")
            .description("Draws a tracer line from crosshair to Ender Chests.")
            .defaultValue(false)
            .build()
    );
    private final Setting<SettingColor> echestSideColor = sgEnder.add(new ColorSetting.Builder()
            .name("fill-color")
            .description("The color of the sides.")
            .defaultValue(new SettingColor(120, 0, 255, 50))
            .build()
    );
    private final Setting<SettingColor> echestLineColor = sgEnder.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The color of the lines.")
            .defaultValue(new SettingColor(120, 0, 255, 255))
            .build()
    );

    // --- Shulker Settings ---
    private final Setting<Boolean> shulkerEnabled = sgShulker.add(new BoolSetting.Builder()
            .name("display-shulkers")
            .description("Render shulker boxes.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> shulkerDynamic = sgShulker.add(new BoolSetting.Builder()
            .name("dynamic-color")
            .description("Use the actual color of the shulker box.")
            .defaultValue(true)
            .build()
    );
    private final Setting<ShapeMode> shulkerShape = sgShulker.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How to render shulker boxes.")
            .defaultValue(ShapeMode.Both)
            .build()
    );
    private final Setting<Boolean> shulkerTracer = sgShulker.add(new BoolSetting.Builder()
            .name("tracer")
            .description("Draws a tracer line from crosshair to Shulkers.")
            .defaultValue(false)
            .build()
    );
    private final Setting<SettingColor> shulkerSideColor = sgShulker.add(new ColorSetting.Builder()
            .name("fill-color")
            .description("The default color of the sides.")
            .defaultValue(new SettingColor(255, 0, 255, 50))
            .build()
    );
    private final Setting<SettingColor> shulkerLineColor = sgShulker.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The default color of the lines.")
            .defaultValue(new SettingColor(255, 0, 255, 255))
            .build()
    );

    // --- Barrel Settings ---
    private final Setting<Boolean> barrelEnabled = sgBarrel.add(new BoolSetting.Builder()
            .name("display-barrels")
            .description("Render barrels.")
            .defaultValue(true)
            .build()
    );
    private final Setting<ShapeMode> barrelShape = sgBarrel.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How to render barrels.")
            .defaultValue(ShapeMode.Both)
            .build()
    );
    private final Setting<SettingColor> barrelSideColor = sgBarrel.add(new ColorSetting.Builder()
            .name("fill-color")
            .description("The color of the sides.")
            .defaultValue(new SettingColor(100, 60, 20, 50))
            .build()
    );
    private final Setting<SettingColor> barrelLineColor = sgBarrel.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The color of the lines.")
            .defaultValue(new SettingColor(100, 60, 20, 255))
            .build()
    );

    public KoodaStorageESP() {
        super(KoodaAddon.KOODA_RENDER, "kooda-storage-esp", "Robust ESP for storage blocks with dynamic colors and optimization.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        int count = 0;
        int limit = maxBlocks.get();
        double distSq = renderDistance.get() * renderDistance.get();

        for (BlockEntity blockEntity : Utils.blockEntities()) {
            // Robustness: Safety break to prevent huge FPS drops
            if (count >= limit) break;

            BlockPos pos = blockEntity.getPos();

            // Robustness: Distance Check
            if (mc.player != null && mc.player.squaredDistanceTo(pos.toCenterPos()) > distSq) {
                continue;
            }

            // Removed manual isOnScreen check to prevent compilation errors.
            // Distance check is sufficient for basic optimization.

            if (blockEntity instanceof TrappedChestBlockEntity && trappedEnabled.get()) {
                renderBlock(event, pos, trappedShape.get(), trappedSideColor.get(), trappedLineColor.get(), false);
                count++;
            }
            else if (blockEntity instanceof ChestBlockEntity && chestEnabled.get()) {
                renderBlock(event, pos, chestShape.get(), chestSideColor.get(), chestLineColor.get(), false);
                count++;
            }
            else if (blockEntity instanceof EnderChestBlockEntity && echestEnabled.get()) {
                renderBlock(event, pos, echestShape.get(), echestSideColor.get(), echestLineColor.get(), echestTracer.get());
                count++;
            }
            else if (blockEntity instanceof ShulkerBoxBlockEntity shulker && shulkerEnabled.get()) {
                Color side = shulkerSideColor.get();
                Color line = shulkerLineColor.get();

                // Robustness: Dynamic Color Handling (Fixed for 1.21)
                if (shulkerDynamic.get()) {
                    DyeColor dye = shulker.getColor();
                    if (dye != null) {
                        // FIX: usage of getEntityColor() for better compatibility
                        int rgb = dye.getEntityColor();
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;

                        side = new Color(r, g, b, shulkerSideColor.get().a);
                        line = new Color(r, g, b, shulkerLineColor.get().a);
                    }
                }

                renderBlock(event, pos, shulkerShape.get(), side, line, shulkerTracer.get());
                count++;
            }
            else if (blockEntity instanceof BarrelBlockEntity && barrelEnabled.get()) {
                renderBlock(event, pos, barrelShape.get(), barrelSideColor.get(), barrelLineColor.get(), false);
                count++;
            }
        }
    }

    private void renderBlock(Render3DEvent event, BlockPos pos, ShapeMode shapeMode, Color sideColor, Color lineColor, boolean tracer) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        // Render the box
        event.renderer.box(x, y, z, x + 1, y + 1, z + 1, sideColor, lineColor, shapeMode, 0);

        // Render tracer if enabled
        if (tracer) {
            // Robust Tracer Logic:
            // Calculate start point slightly in front of the camera to act as a "stem" from the crosshair
            Vec3d forward = mc.player.getRotationVec(event.tickDelta).multiply(0.5); // Length of the stem start
            Vec3d start = mc.gameRenderer.getCamera().getPos().add(forward);

            event.renderer.line(start.x, start.y, start.z, x + 0.5, y + 0.5, z + 0.5, lineColor);
        }
    }
}