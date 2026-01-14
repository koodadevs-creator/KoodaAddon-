package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import pwn.noobs.trouserstreak.KoodaAddon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KoodaCrawlTrap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("The maximum range to target players.")
            .defaultValue(5.0)
            .min(0)
            .sliderMax(6)
            .build()
    );

    private final Setting<Boolean> detectSelf = sgGeneral.add(new BoolSetting.Builder()
            .name("detect-self")
            .description("Target yourself for testing purposes.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Sends rotation packets to the server for GrimAC bypass.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> packetPlace = sgGeneral.add(new BoolSetting.Builder()
            .name("packet-place")
            .description("Uses packets to place blocks (No animation, faster).")
            .defaultValue(true)
            .build()
    );

    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
            .name("switch-mode")
            .description("How to switch to the obsidian.")
            .defaultValue(SwitchMode.Silent)
            .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
            .name("swing")
            .description("Renders the hand swing client-side.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> bpt = sgTiming.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Maximum blocks to place per tick.")
            .defaultValue(2)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> delay = sgTiming.add(new IntSetting.Builder()
            .name("delay")
            .description("Tick delay between placement cycles.")
            .defaultValue(0)
            .min(0)
            .sliderMax(10)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders the block placement.")
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

    private int timer;
    private final List<BlockPos> renderPositions = new ArrayList<>();

    public KoodaCrawlTrap() {
        super(KoodaAddon.KOODA_COMBAT, "kooda-crawl-trap", "Traps crawling players by placing blocks above them.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        renderPositions.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        renderPositions.clear();

        if (timer > 0) {
            timer--;
            return;
        }

        FindItemResult blockResult = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!blockResult.found()) blockResult = InvUtils.findInHotbar(Items.ENDER_CHEST);
        if (!blockResult.found()) blockResult = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
        if (!blockResult.found()) blockResult = InvUtils.findInHotbar(Items.NETHERITE_BLOCK);

        if (!blockResult.found()) return;

        List<PlayerEntity> targets = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player && !detectSelf.get()) continue;

            if (player != mc.player && Friends.get().isFriend(player)) continue;

            if (mc.player.distanceTo(player) > range.get()) continue;

            if (player.isCrawling()) {
                targets.add(player);
            }
        }

        if (targets.isEmpty()) return;

        targets.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));

        int blocksPlaced = 0;
        boolean acted = false;

        for (PlayerEntity target : targets) {
            if (blocksPlaced >= bpt.get()) break;

            BlockPos headTrapPos = target.getBlockPos().up();

            if (BlockUtils.canPlace(headTrapPos)) {
                renderPositions.add(headTrapPos);

                int prevSlot = mc.player.getInventory().selectedSlot;
                boolean shouldSwap = mc.player.getInventory().selectedSlot != blockResult.slot();

                if (shouldSwap) {
                    InvUtils.swap(blockResult.slot(), switchMode.get() == SwitchMode.Silent);
                }

                boolean placed = BlockUtils.place(headTrapPos, blockResult, rotate.get(), 50, swing.get(), true, packetPlace.get());

                if (placed) {
                    blocksPlaced++;
                    acted = true;
                }

                if (shouldSwap && switchMode.get() == SwitchMode.Silent) {
                    InvUtils.swap(prevSlot, true);
                }
            }
        }

        if (acted) {
            timer = delay.get();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || renderPositions.isEmpty()) return;

        for (BlockPos pos : renderPositions) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    public enum SwitchMode {
        Silent,
        Normal
    }
}