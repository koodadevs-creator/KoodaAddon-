package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color; // IMPORT AÑADIDO
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KoodaSelfTrap extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLogic = settings.createGroup("Logic & Safety");
    private final SettingGroup sgPlace = settings.createGroup("Placement");
    private final SettingGroup sgRender = settings.createGroup("Visuals");

    // --- MODES ---
    public enum Mode {
        TopOnly,    // Only the block above head
        Head,       // Surround head + Top
        Full,       // Full body casing (Anti-City)
        AntiFace    // Focus on face-place angles
    }

    // --- GENERAL ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("The structure shape to build.")
            .defaultValue(Mode.Full)
            .build()
    );

    private final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
            .name("center")
            .description("Forces player to center of block to prevent clipping.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-disable")
            .description("Disable after finishing the trap.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> breakCrystals = sgGeneral.add(new BoolSetting.Builder()
            .name("break-crystals")
            .description("Breaks crystals that block the trap placement.")
            .defaultValue(true)
            .build()
    );

    // --- LOGIC ---
    private final Setting<Boolean> smartSupport = sgLogic.add(new BoolSetting.Builder()
            .name("smart-support")
            .description("Recursively finds support blocks down to the ground.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> holeCheck = sgLogic.add(new BoolSetting.Builder()
            .name("hole-check")
            .description("Only activates if you are in a safe hole.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> timeout = sgLogic.add(new IntSetting.Builder()
            .name("timeout-ticks")
            .description("Auto disable if trap takes too long (Lag protection).")
            .defaultValue(20)
            .min(5)
            .sliderMax(60)
            .build()
    );

    // --- PLACEMENT ---
    private final Setting<Integer> delay = sgPlace.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks between block placements.")
            .defaultValue(0)
            .min(0)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> blocksPerTick = sgPlace.add(new IntSetting.Builder()
            .name("bpt")
            .description("Blocks per tick.")
            .defaultValue(4)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Grim/NCP Rotation bypass.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> airPlace = sgPlace.add(new BoolSetting.Builder()
            .name("air-place")
            .description("Allow air placement (Vanilla/NoCheatPlus).")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> swing = sgPlace.add(new BoolSetting.Builder()
            .name("swing")
            .description("Render hand swing.")
            .defaultValue(true)
            .build()
    );

    // --- RENDER ---
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").defaultValue(new SettingColor(0, 255, 255, 30)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").defaultValue(new SettingColor(0, 255, 255, 200)).build());
    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
            .name("fade-time").description("Seconds for the render to fade out.").defaultValue(0.5).min(0).max(2).build());

    // --- STATE VARIABLES ---
    private int ticksPassed = 0;
    private int placeTimer = 0;
    private final Map<BlockPos, Long> renderMap = new ConcurrentHashMap<>();
    private final List<BlockPos> currentTasks = new ArrayList<>();

    public KoodaSelfTrap() {
        super(KoodaAddon.KOODA_COMBAT, "kooda-mega-trap", "The ultimate self-defense tool with Crystal Breaker.");
    }

    @Override
    public void onActivate() {
        ticksPassed = 0;
        placeTimer = 0;
        currentTasks.clear();
        renderMap.clear();

        if (center.get()) {
            PlayerUtils.centerPlayer();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        // 1. Safety Checks
        if (holeCheck.get() && !PlayerUtils.isInHole(true)) {
            // Not in hole, wait.
            return;
        }

        ticksPassed++;
        if (ticksPassed > timeout.get()) {
            if (autoDisable.get()) toggle();
            return;
        }

        if (placeTimer > 0) {
            placeTimer--;
            return;
        }

        // 2. Resource Check
        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) {
            obsidian = InvUtils.findInHotbar(Items.ENDER_CHEST);
            if (!obsidian.found()) return; // Silent fail if no blocks
        }

        // 3. Logic Calculation
        currentTasks.clear();
        BlockPos playerPos = mc.player.getBlockPos();

        // Adjust for "Burrow" lag (jumping inside block)
        if (mc.player.getY() % 1 > 0.2) playerPos = playerPos.up();

        calculatePositions(playerPos);

        if (currentTasks.isEmpty()) {
            if (autoDisable.get()) toggle();
            return;
        }

        // 4. Execution Loop
        int placed = 0;
        for (BlockPos pos : currentTasks) {
            if (placed >= blocksPerTick.get()) break;

            if (isValidSpot(pos)) {
                // Crystal Breaker Logic
                if (breakCrystals.get() && checkCrystalBlocking(pos)) {
                    // Crystal broken, we might need to wait a tick or continue depending on server speed
                    // For safety, we count this as an action
                    placed++;
                    continue;
                }

                // Entity Blocking Check (Players) - Fixed: Using isReplaceable()
                if (!mc.world.getBlockState(pos).isReplaceable()) continue;
                if (checkEntityBlocking(pos)) continue;

                // Place
                boolean result = placeBlock(pos, obsidian);
                if (result) {
                    placed++;
                    renderMap.put(pos, System.currentTimeMillis());
                }
            }
        }

        if (placed > 0) {
            placeTimer = delay.get();
        }
    }

    // --- CALCULATION ENGINE ---

    private void calculatePositions(BlockPos base) {
        // Define offsets based on mode
        List<BlockPos> rawPositions = new ArrayList<>();

        switch (mode.get()) {
            case TopOnly -> {
                rawPositions.add(base.up(2));
            }
            case Head -> {
                rawPositions.add(base.up(2));
                addSurround(rawPositions, base.up());
            }
            case Full -> {
                rawPositions.add(base.up(2));
                addSurround(rawPositions, base.up()); // Head
                addSurround(rawPositions, base);      // Legs
            }
            case AntiFace -> {
                rawPositions.add(base.up(2));
                addSurround(rawPositions, base.up());
                // Only front legs
                rawPositions.add(base.offset(mc.player.getHorizontalFacing()));
            }
        }

        // Process supports and validity
        for (BlockPos pos : rawPositions) {
            if (mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN ||
                    mc.world.getBlockState(pos).getBlock() == Blocks.BEDROCK ||
                    mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
                continue; // Already safe
            }

            // Check if we have a neighbor to place off of
            if (!BlockUtils.canPlace(pos) && !airPlace.get()) {
                if (smartSupport.get()) {
                    BlockPos support = findSupportBlock(pos);
                    if (support != null) {
                        currentTasks.add(support);
                        currentTasks.add(pos);
                    }
                }
            } else {
                currentTasks.add(pos);
            }
        }
    }

    private void addSurround(List<BlockPos> list, BlockPos center) {
        list.add(center.north());
        list.add(center.south());
        list.add(center.east());
        list.add(center.west());
    }

    /**
     * Recursively (limited depth) finds a path to a solid block.
     */
    private BlockPos findSupportBlock(BlockPos target) {
        // Simple heuristic: Try down, then sides.
        BlockPos down = target.down();
        if (isValidSpot(down) && hasNeighbor(down)) return down;

        for (Direction d : Direction.values()) {
            if (d == Direction.UP || d == Direction.DOWN) continue;
            BlockPos side = target.offset(d);
            if (isValidSpot(side) && hasNeighbor(side)) return side;
        }
        return null; // Could not find simple support
    }

    // --- INTERACTION ENGINE ---

    private boolean checkCrystalBlocking(BlockPos pos) {
        Box box = new Box(pos);
        boolean broken = false;
        for (Entity e : mc.world.getOtherEntities(null, box)) {
            if (e instanceof EndCrystalEntity) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(e, mc.player.isSneaking()));
                if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
                broken = true;
            }
        }
        return broken;
    }

    private boolean checkEntityBlocking(BlockPos pos) {
        Box box = new Box(pos);
        for (Entity e : mc.world.getOtherEntities(null, box)) {
            if (!(e instanceof EndCrystalEntity)) {
                return true; // Something (player/mob) is in the way
            }
        }
        return false;
    }

    private boolean placeBlock(BlockPos pos, FindItemResult item) {
        if (!BlockUtils.canPlace(pos)) return false;

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 50, () -> executePlace(pos, item));
        } else {
            executePlace(pos, item);
        }
        return true;
    }

    private void executePlace(BlockPos pos, FindItemResult item) {
        InvUtils.swap(item.slot(), false);
        BlockUtils.place(pos, item, false, 0, swing.get(), true, false);
        InvUtils.swapBack();
    }

    // --- UTILS ---

    private boolean isValidSpot(BlockPos pos) {
        // FIXED: Replaced .getMaterial().isReplaceable() with .isReplaceable()
        return mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean hasNeighbor(BlockPos pos) {
        for (Direction d : Direction.values()) {
            // FIXED: Replaced .getMaterial().isReplaceable() with .isReplaceable()
            if (!mc.world.getBlockState(pos.offset(d)).isReplaceable()) return true;
        }
        return false;
    }

    // --- RENDERER ---

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderMap.isEmpty()) return;

        List<BlockPos> toRemove = new ArrayList<>();
        long now = System.currentTimeMillis();
        double fadeMillis = fadeTime.get() * 1000;

        renderMap.forEach((pos, time) -> {
            long elapsed = now - time;
            if (elapsed > fadeMillis) {
                toRemove.add(pos);
            } else {
                // Calculate Alpha
                double alphaPct = 1.0 - (elapsed / fadeMillis);

                // FIXED: Color class is now imported correctly
                Color sC = sideColor.get().copy();
                Color lC = lineColor.get().copy();

                sC.a = (int)(sC.a * alphaPct);
                lC.a = (int)(lC.a * alphaPct);

                event.renderer.box(pos, sC, lC, shapeMode.get(), 0);
            }
        });

        toRemove.forEach(renderMap::remove);
    }
}