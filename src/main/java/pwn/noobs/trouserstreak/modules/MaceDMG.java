package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import pwn.noobs.trouserstreak.KoodaAddon;

public class MaceDMG extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maceHeight = sgGeneral.add(new IntSetting.Builder()
            .name("mace-height-main")
            .description("Height for the initial main hit.")
            .defaultValue(22)
            .min(1)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> hit1Height = sgGeneral.add(new IntSetting.Builder()
            .name("hit-1-height")
            .description("Height for the first type of extra hit.")
            .defaultValue(15)
            .min(1)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> hit2Height = sgGeneral.add(new IntSetting.Builder()
            .name("hit-2-height")
            .description("Height for the second type of extra hit.")
            .defaultValue(10)
            .min(1)
            .sliderMax(100)
            .build()
    );

    private final Setting<SwapMode> swapMode = sgGeneral.add(new EnumSetting.Builder<SwapMode>()
            .name("swap-mode")
            .description("How to switch to the Mace.")
            .defaultValue(SwapMode.Silent)
            .build()
    );

    private final Setting<Integer> extraHits = sgGeneral.add(new IntSetting.Builder()
            .name("extra-hits")
            .description("Extra hits to add. Cycles between Hit 1 and Hit 2 logics.")
            .defaultValue(1)
            .min(0)
            .sliderMax(10)
            .build()
    );

    private boolean isOperating = false;

    public MaceDMG() {
        super(KoodaAddon.KOODA_COMBAT, "mace-dmg", "Mace exploit with MaceKill logic and multi-hit cycling.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null || isOperating) return;

        if (!(event.packet instanceof PlayerInteractEntityC2SPacket)) return;

        IPlayerInteractEntityC2SPacket packet = (IPlayerInteractEntityC2SPacket) event.packet;

        if (packet.meteor$getType() != PlayerInteractEntityC2SPacket.InteractType.ATTACK) return;
        if (!(packet.meteor$getEntity() instanceof LivingEntity target)) return;

        FindItemResult mace = InvUtils.findInHotbar(Items.MACE);
        if (!mace.found()) return;

        isOperating = true;

        int prevSlot = mc.player.getInventory().selectedSlot;
        boolean shouldSwap = mc.player.getMainHandStack().getItem() != Items.MACE;

        if (shouldSwap) {
            InvUtils.swap(mace.slot(), swapMode.get() == SwapMode.Silent);
        }

        performSmash(maceHeight.get());

        int extras = extraHits.get();
        if (extras > 0) {
            for (int i = 0; i < extras; i++) {
                int currentHeight = (i % 2 == 0) ? hit1Height.get() : hit2Height.get();

                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                mc.player.swingHand(Hand.MAIN_HAND);
                performSmash(currentHeight);
            }
        }

        if (shouldSwap && swapMode.get() == SwapMode.Silent) {
            InvUtils.swap(prevSlot, true);
        }

        isOperating = false;
    }

    private void performSmash(int height) {
        if (height <= 0) return;

        int packetsRequired = (int) Math.ceil(Math.abs(height / 10.0));
        if (packetsRequired > 20) packetsRequired = 1;

        for (int i = 0; i < packetsRequired; i++) {
            PlayerMoveC2SPacket onGroundPacket = new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision);
            ((IPlayerMoveC2SPacket) onGroundPacket).meteor$setTag(1337);
            mc.player.networkHandler.sendPacket(onGroundPacket);
        }

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        PlayerMoveC2SPacket upPacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                x, y + height, z, false, mc.player.horizontalCollision
        );

        PlayerMoveC2SPacket downPacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                x, y, z, false, mc.player.horizontalCollision
        );

        ((IPlayerMoveC2SPacket) upPacket).meteor$setTag(1337);
        ((IPlayerMoveC2SPacket) downPacket).meteor$setTag(1337);

        mc.player.networkHandler.sendPacket(upPacket);
        mc.player.networkHandler.sendPacket(downPacket);
    }

    public enum SwapMode {
        Silent,
        Normal
    }
}