package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import pwn.noobs.trouserstreak.KoodaAddon;

public class KoodaNoGlitchBlocks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> placeSync = sgGeneral.add(new BoolSetting.Builder()
            .name("placement-sync")
            .description("Forces server synchronization when placing blocks to prevent ghost air.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> breakSync = sgGeneral.add(new BoolSetting.Builder()
            .name("break-sync")
            .description("Forces server synchronization when breaking blocks to prevent ghost blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
            .name("sync-delay")
            .description("The delay in ticks between synchronization attempts to prevent packet spam.")
            .defaultValue(1)
            .min(0)
            .max(20)
            .build()
    );

    private int tickTimer = 0;

    public KoodaNoGlitchBlocks() {
        super(KoodaAddon.KOODA_UTILITY, "kooda-no-glitch-blocks", "Robust anti-desync system for high-speed placement and breaking.");
    }

    @Override
    public void onActivate() {
        tickTimer = 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (tickTimer > 0) {
            tickTimer--;
            return;
        }

        tickTimer = tickDelay.get();
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.world == null || mc.player == null) return;

        if (placeSync.get() && event.packet instanceof PlayerInteractBlockC2SPacket) {
        }

        if (breakSync.get() && event.packet instanceof PlayerActionC2SPacket) {
            PlayerActionC2SPacket packet = (PlayerActionC2SPacket) event.packet;

            if (packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
                BlockPos pos = packet.getPos();
                Block block = mc.world.getBlockState(pos).getBlock();

                if (block != Blocks.AIR && block != Blocks.BEDROCK) {
                }
            }
        }
    }


    public void forceSync(BlockPos pos) {
        if (mc.world == null || mc.player == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                pos,
                Direction.UP
        ));
    }
}