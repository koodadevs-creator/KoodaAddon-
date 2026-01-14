package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

public class KoodaBurrow extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description(" The method used to glitch into the block.")
            .defaultValue(Mode.Packet)
            .build()
    );

    private final Setting<Double> offset = sgGeneral.add(new DoubleSetting.Builder()
            .name("offset")
            .description("How high to teleport to force the rubberband (try 3.0 or -2.0).")
            .defaultValue(2.5)
            .min(-10)
            .max(10)
            .sliderMin(-5)
            .sliderMax(5)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Forces rotation towards the block when placing.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
            .name("center")
            .description("Centers the player on the block before burrowing.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
            .name("instant-disable")
            .description("Disables the module immediately after success.")
            .defaultValue(true)
            .build()
    );

    public KoodaBurrow() {
        super(KoodaAddon.KOODA_COMBAT, "kooda-burrow", "Glitches you into a block. Requires NoPush!");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        if (!mc.player.isOnGround()) {
            error("You must be on the ground to burrow!");
            toggle();
            return;
        }

        FindItemResult result = InvUtils.findInHotbar(itemStack ->
                itemStack.getItem() instanceof BlockItem && (
                        itemStack.getItem() == Items.OBSIDIAN ||
                                itemStack.getItem() == Items.ENDER_CHEST ||
                                itemStack.getItem() == Items.NETHERITE_BLOCK ||
                                itemStack.getItem() == Items.ANVIL ||
                                itemStack.getItem() == Items.CRYING_OBSIDIAN
                )
        );

        if (!result.found()) {
            error("No suitable block found (Obi, EChest, Anvil).");
            toggle();
            return;
        }

        BlockPos pos = mc.player.getBlockPos();

        if (!mc.world.getBlockState(pos).isReplaceable()) {
            error("Current block is not replaceable.");
            toggle();
            return;
        }

        if (center.get()) {
            PlayerUtils.centerPlayer();
        }

        if (mode.get() == Mode.Packet) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.41999998688698, mc.player.getZ(), true, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.7531999805212, mc.player.getZ(), true, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.00133597911214, mc.player.getZ(), true, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.16610926093821, mc.player.getZ(), true, false));
        }

        int prevSlot = mc.player.getInventory().selectedSlot;
        InvUtils.swap(result.slot(), false);

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> placeBlock(pos));
        } else {
            placeBlock(pos);
        }

        InvUtils.swap(prevSlot, false);

        double off = offset.get();

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(),
                mc.player.getY() + off,
                mc.player.getZ(),
                false,
                false
        ));

        if (instant.get()) {
            toggle();
        }
    }

    private void placeBlock(BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
                Direction.UP,
                pos,
                false
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    public enum Mode {
        Packet,
        Teleport
    }
}