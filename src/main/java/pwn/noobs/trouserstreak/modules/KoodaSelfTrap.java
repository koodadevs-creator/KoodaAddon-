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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KoodaSelfTrap extends Module {


    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShape = settings.createGroup("Shape & Mode");
    private final SettingGroup sgLogic = settings.createGroup("Logic & Safety");
    private final SettingGroup sgPlace = settings.createGroup("Placement");
    private final SettingGroup sgRender = settings.createGroup("Visuals");


    public enum Mode {
        Full,
        Top,
        Head,
        AntiFace,
        FullAntiStep
    }

    public enum CenterMode {
        None,
        Teleport,
        Nudge
    }




    private final Setting<Mode> mode = sgShape.add(new EnumSetting.Builder<Mode>()
            .name("trap-mode")
            .description("The geometric shape of the self-trap.")
            .defaultValue(Mode.Full)
            .build()
    );

    private final Setting<CenterMode> centerMode = sgShape.add(new EnumSetting.Builder<CenterMode>()
            .name("centering")
            .description("How to center the player before building.")
            .defaultValue(CenterMode.Teleport)
            .build()
    );

    private final Setting<Boolean> autoDisable = sgLogic.add(new BoolSetting.Builder()
            .name("auto-disable")
            .description("Disables module after finishing the trap.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> prediction = sgLogic.add(new BoolSetting.Builder()
            .name("movement-prediction")
            .description("Predicts your future position based on velocity.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> predictionTicks = sgLogic.add(new IntSetting.Builder()
            .name("prediction-ticks")
            .defaultValue(2)
            .min(1)
            .sliderMax(5)
            .visible(prediction::get)
            .build()
    );

    private final Setting<Boolean> smartSupport = sgLogic.add(new BoolSetting.Builder()
            .name("smart-support")
            .description("Finds support blocks if AirPlace is off.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> breakCrystals = sgLogic.add(new BoolSetting.Builder()
            .name("break-crystals")
            .description("Attacks crystals blocking placement.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> delay = sgPlace.add(new IntSetting.Builder()
            .name("place-delay")
            .description("Ticks between placement batches.")
            .defaultValue(0)
            .min(0)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> blocksPerTick = sgPlace.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .defaultValue(4)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Strict rotation to bypass anti-cheat.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> airPlace = sgPlace.add(new BoolSetting.Builder()
            .name("air-place")
            .description("Attempt to place in mid-air.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> swing = sgPlace.add(new BoolSetting.Builder()
            .name("swing")
            .description("Render hand swing.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(0, 255, 255, 20)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(0, 255, 255, 150)).build());
    private final Setting<Double> slideSpeed = sgRender.add(new DoubleSetting.Builder().name("slide-speed").defaultValue(8.0).min(1.0).max(20.0).build());
    private final Setting<Double> renderTime = sgRender.add(new DoubleSetting.Builder().name("render-time").description("How long the box stays visible.").defaultValue(1.0).min(0).max(5).build());

    private int timer = 0;
    private int ticksPassed = 0;

    private final Map<BlockPos, Long> renderQueue = new ConcurrentHashMap<>();
    private final Map<BlockPos, Double> animationY = new ConcurrentHashMap<>();

    public KoodaSelfTrap() {
        super(KoodaAddon.KOODA_COMBAT, "kooda-mega-trap", "Robust SelfTrap with prediction and smart placement.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        ticksPassed = 0;
        renderQueue.clear();
        animationY.clear();

        if (mc.player == null) return;

        // Centering Logic
        if (centerMode.get() == CenterMode.Teleport) {
            PlayerUtils.centerPlayer();
        } else if (centerMode.get() == CenterMode.Nudge) {
            Vec3d centerPos = Vec3d.ofBottomCenter(mc.player.getBlockPos());
            mc.player.setVelocity(
                    (centerPos.x - mc.player.getX()) * 0.5,
                    mc.player.getVelocity().y,
                    (centerPos.z - mc.player.getZ()) * 0.5
            );
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) {
            toggle();
            return;
        }

        ticksPassed++;

        if (timer > 0) {
            timer--;
            return;
        }

        FindItemResult item = findBlock();
        if (!item.found()) {
            if (autoDisable.get()) toggle();
            return;
        }

        BlockPos playerPos;
        if (prediction.get()) {
            Vec3d velocity = mc.player.getVelocity();
            Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Vec3d predictedVec = currentPos.add(velocity.x * predictionTicks.get(), 0, velocity.z * predictionTicks.get());
            playerPos = BlockPos.ofFloored(predictedVec);
        } else {
            playerPos = mc.player.getBlockPos();
        }

        if (!mc.world.getBlockState(playerPos).isReplaceable()) {
            playerPos = playerPos.up();
        }

        List<BlockPos> placementQueue = getPlacementPositions(playerPos);

        if (placementQueue.isEmpty()) {
            if (autoDisable.get()) toggle();
            return;
        }

        int placedThisTick = 0;

        for (BlockPos pos : placementQueue) {
            if (placedThisTick >= blocksPerTick.get()) break;

            if (!isValidPos(pos)) continue;

            // Entity Blocking Logic (Crystal Breaker)
            if (checkEntityBlocking(pos)) {
                // If blocked by Crystal, break it
                if (breakCrystals.get() && attackCrystals(pos)) {
                    // Attack logic handled, might need to wait for server
                } else {
                    // Blocked by Player/Item, skip this block
                    continue;
                }
            }

            // Placement
            if (attemptPlace(pos, item)) {
                placedThisTick++;

                // Add to Render
                renderQueue.put(pos, System.currentTimeMillis());
                // Initialize animation 2 blocks high
                if (!animationY.containsKey(pos)) {
                    animationY.put(pos, (double) pos.getY() + 2.0);
                }
            }
        }

        if (placedThisTick > 0) {
            timer = delay.get();
        }
    }

    // ================= SCHEMA ENGINE =================

    private List<BlockPos> getPlacementPositions(BlockPos base) {
        List<BlockPos> tasks = new ArrayList<>();
        List<BlockPos> geometry = new ArrayList<>();

        // Geometry Definition
        switch (mode.get()) {
            case Top -> geometry.add(base.up(2));
            case Head -> {
                addCardinals(geometry, base.up());
                geometry.add(base.up(2));
            }
            case Full -> {
                addCardinals(geometry, base); // Feet
                addCardinals(geometry, base.up()); // Head
                geometry.add(base.up(2)); // Top
            }
            case AntiFace -> {
                addCardinals(geometry, base.up());
                // Add block in front of player's looking direction
                geometry.add(base.offset(mc.player.getHorizontalFacing()));
                geometry.add(base.up(2));
            }
            case FullAntiStep -> {
                addCardinals(geometry, base);
                addCardinals(geometry, base.up());
                geometry.add(base.up(2));
                // Anti step: blocks around feet + 1
                addCardinals(geometry, base.up(2)); // Extra protection
            }
        }

        // Support Finding (Recursive)
        for (BlockPos target : geometry) {
            // Filter already placed blocks
            if (!mc.world.getBlockState(target).isReplaceable()) continue;

            if (BlockUtils.canPlace(target) || airPlace.get()) {
                tasks.add(target);
            } else if (smartSupport.get()) {
                // Need support
                BlockPos support = findSupport(target);
                if (support != null) {
                    // Add support first
                    if (mc.world.getBlockState(support).isReplaceable()) {
                        tasks.add(support);
                    }
                    tasks.add(target);
                }
            }
        }
        return tasks;
    }

    private void addCardinals(List<BlockPos> list, BlockPos origin) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            list.add(origin.offset(d));
        }
    }

    private BlockPos findSupport(BlockPos pos) {
        // 1. Check Down (Best support)
        if (isValidPos(pos.down()) && hasSolidNeighbor(pos.down())) return pos.down();

        // 2. Check Cardinals
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos offset = pos.offset(d);
            if (isValidPos(offset) && hasSolidNeighbor(offset)) return offset;
        }

        // 3. Check Down-Cardinals (Diagonal support)
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos offset = pos.down().offset(d);
            if (isValidPos(offset) && hasSolidNeighbor(offset)) return offset;
        }

        return null; // No nearby support found
    }

    // ================= ACTION ENGINE =================

    private FindItemResult findBlock() {
        // Priority list
        FindItemResult res = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (res.found()) return res;

        res = InvUtils.findInHotbar(Items.ENDER_CHEST);
        if (res.found()) return res;

        res = InvUtils.findInHotbar(Items.NETHERITE_BLOCK);
        if (res.found()) return res;

        res = InvUtils.findInHotbar(Items.CRYING_OBSIDIAN);
        if (res.found()) return res;

        return InvUtils.findInHotbar(Items.ANVIL);
    }

    private boolean attemptPlace(BlockPos pos, FindItemResult item) {
        // Final Validation
        if (!BlockUtils.canPlace(pos) && !airPlace.get()) return false;

        // State Saving
        int prevSlot = mc.player.getInventory().selectedSlot;

        // Swap
        InvUtils.swap(item.slot(), false);

        // Place Packet
        boolean placed = false;

        if (rotate.get()) {
            // Using Meteor's rotation system for safety
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 50, () -> {
                BlockUtils.place(pos, item, false, 0, swing.get(), true, false);
            });
            placed = true; // Assume success if rotated
        } else {
            // Direct placement
            placed = BlockUtils.place(pos, item, false, 0, swing.get(), true, false);
        }

        // Swap Back
        InvUtils.swap(prevSlot, false);

        return placed;
    }

    private boolean attackCrystals(BlockPos pos) {
        boolean attacked = false;
        Box box = new Box(pos);
        for (Entity e : mc.world.getOtherEntities(null, box)) {
            if (e instanceof EndCrystalEntity) {
                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(e, mc.player.isSneaking()));
                if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
                attacked = true;
            }
        }
        return attacked;
    }

    // ================= CHECKS =================

    private boolean isValidPos(BlockPos pos) {
        // Out of world or blocked by bedrock?
        if (!mc.world.isInBuildLimit(pos)) return false;
        // Is replaceable?
        return mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean checkEntityBlocking(BlockPos pos) {
        Box box = new Box(pos);

        for (Entity e : mc.world.getOtherEntities(null, box)) {
            // FIXED: ItemEntity imported correctly now
            if (e instanceof ItemEntity) continue;

            // If it's a crystal, return true so we can break it
            if (e instanceof EndCrystalEntity) return true;

            // If it's another player or mob, return true to block placement
            if (!e.isSpectator()) return true;
        }
        return false;
    }

    private boolean hasSolidNeighbor(BlockPos pos) {
        if (airPlace.get()) return true;
        for (Direction d : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(d)).isReplaceable()) return true;
        }
        return false;
    }

    // ================= RENDER ENGINE (SMOOTH SLIDE) =================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderQueue.isEmpty()) return;

        List<BlockPos> toRemove = new ArrayList<>();
        long now = System.currentTimeMillis();
        double maxTime = renderTime.get() * 1000;

        renderQueue.forEach((pos, startTime) -> {
            long elapsed = now - startTime;

            if (elapsed > maxTime) {
                toRemove.add(pos);
            } else {
                // Animation Logic
                double targetY = pos.getY();
                double currentY = animationY.getOrDefault(pos, targetY + 2.0);

                // Lerp towards target based on delta time
                double lerpFactor = Math.min(1.0, event.tickDelta * (slideSpeed.get() / 10.0));
                double newY = MathHelper.lerp(lerpFactor, currentY, targetY);

                animationY.put(pos, newY);

                // Alpha fade out
                double alphaFactor = 1.0 - (elapsed / maxTime);

                Color sC = sideColor.get().copy();
                Color lC = lineColor.get().copy();
                sC.a = (int)(sC.a * alphaFactor);
                lC.a = (int)(lC.a * alphaFactor);

                // Draw Box with animated Y
                event.renderer.box(
                        pos.getX(), newY, pos.getZ(),
                        pos.getX() + 1, newY + 1, pos.getZ() + 1,
                        sC, lC, shapeMode.get(), 0
                );
            }
        });

        // Cleanup
        toRemove.forEach(p -> {
            renderQueue.remove(p);
            animationY.remove(p);
        });
    }
}