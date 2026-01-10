package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class AntiRegearKooda extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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

    private int ticks;

    public AntiRegearKooda() {
        super(KoodaAddon.KOODA_COMBAT, "anti-regear-kooda", "Automatically breaks shulker boxes around you.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        if (delay.get() > 0 && ticks < delay.get()) {
            ticks++;
            return;
        }

        BlockPos bestPos = findShulker();

        if (bestPos != null) {
            ticks = 0;
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(bestPos), Rotations.getPitch(bestPos), 50, () -> breakBlock(bestPos));
            } else {
                breakBlock(bestPos);
            }
        }
    }

    private void breakBlock(BlockPos pos) {
        mc.interactionManager.updateBlockBreakingProgress(pos, net.minecraft.util.math.Direction.UP);
        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
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
}