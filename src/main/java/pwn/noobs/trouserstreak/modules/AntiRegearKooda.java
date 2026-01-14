package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import pwn.noobs.trouserstreak.KoodaAddon;

import java.util.Arrays;
import java.util.List;

public class AntiRegearKooda extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMining = settings.createGroup("Mining");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("The range to break shulkers.")
            .defaultValue(5.0)
            .min(1.0)
            .sliderMax(6.0)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotates towards the block.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
            .name("swing")
            .description("Swings your hand client-side.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks to wait between breaks.")
            .defaultValue(0)
            .min(0)
            .build()
    );

    private final Setting<BreakMode> mode = sgMining.add(new EnumSetting.Builder<BreakMode>()
            .name("break-mode")
            .description("How to break the shulker.")
            .defaultValue(BreakMode.Packet)
            .build()
    );

    private final Setting<Double> damage = sgMining.add(new DoubleSetting.Builder()
            .name("damage")
            .description("Block breaking progress required (1.0 = full break).")
            .defaultValue(1.0)
            .min(0.7)
            .max(1.0)
            .build()
    );

    private final Setting<SwapMode> swap = sgMining.add(new EnumSetting.Builder<SwapMode>()
            .name("swap")
            .description("How to switch to a pickaxe.")
            .defaultValue(SwapMode.Silent)
            .build()
    );

    private final Setting<Boolean> switchBack = sgMining.add(new BoolSetting.Builder()
            .name("switch-back")
            .description("Switches back to original slot after breaking.")
            .defaultValue(true)
            .visible(() -> swap.get() == SwapMode.Normal)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders the block being broken.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color.")
            .defaultValue(new SettingColor(255, 0, 0, 75))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
    );

    private int ticks;
    private BlockPos currentPos;

    private static final List<Item> PICKAXES = Arrays.asList(
            Items.NETHERITE_PICKAXE,
            Items.DIAMOND_PICKAXE,
            Items.IRON_PICKAXE,
            Items.GOLDEN_PICKAXE,
            Items.STONE_PICKAXE,
            Items.WOODEN_PICKAXE
    );

    public AntiRegearKooda() {
        super(KoodaAddon.KOODA_COMBAT, "anti-regear-kooda", "Automatically destroys shulker boxes to prevent regearing.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        currentPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        currentPos = null;

        if (delay.get() > 0 && ticks < delay.get()) {
            ticks++;
            return;
        }

        BlockPos bestPos = findShulker();

        if (bestPos != null) {
            currentPos = bestPos;
            ticks = 0;

            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(bestPos), Rotations.getPitch(bestPos), 50, () -> processBreak(bestPos));
            } else {
                processBreak(bestPos);
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || currentPos == null) return;
        event.renderer.box(currentPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private void processBreak(BlockPos pos) {
        FindItemResult pickaxe = InvUtils.find(item -> PICKAXES.contains(item.getItem()));
        int prevSlot = mc.player.getInventory().selectedSlot;
        boolean shouldSwap = pickaxe.found() && swap.get() != SwapMode.None;

        if (shouldSwap) {
            InvUtils.swap(pickaxe.slot(), swap.get() == SwapMode.Silent);
        }

        BlockState state = mc.world.getBlockState(pos);
        int toolSlot = pickaxe.found() ? pickaxe.slot() : mc.player.getInventory().selectedSlot;

        double progress = (double) BlockUtils.getBreakDelta(toolSlot, state);
        double requiredDamage = damage.get();

        if (progress >= requiredDamage) {
            if (mode.get() == BreakMode.Packet) {
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
            } else {
                mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
            }

            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
        }

        if (shouldSwap && swap.get() == SwapMode.Normal && switchBack.get()) {
            InvUtils.swap(prevSlot, false);
        }
    }

    private BlockPos findShulker() {
        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.get());
        BlockPos best = null;
        double minDst = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -r; y <= r; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    double dist = Math.sqrt(mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()));

                    if (dist > range.get()) continue;

                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block instanceof ShulkerBoxBlock) {
                        if (dist < minDst) {
                            minDst = dist;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    public enum BreakMode {
        Normal,
        Packet
    }

    public enum SwapMode {
        Silent,
        Normal,
        None
    }
}