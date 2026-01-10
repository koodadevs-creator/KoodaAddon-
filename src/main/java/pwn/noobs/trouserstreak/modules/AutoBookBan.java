package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class AutoBookBan extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> pages = sgGeneral.add(new IntSetting.Builder()
            .name("pages")
            .description("Number of pages. Max 100.")
            .defaultValue(100)
            .min(1)
            .max(100)
            .build()
    );

    private final Setting<Integer> length = sgGeneral.add(new IntSetting.Builder()
            .name("length-per-page")
            .description("Characters per page.")
            .defaultValue(500)
            .min(100)
            .max(1024)
            .build()
    );

    private final Setting<String> title = sgGeneral.add(new StringSetting.Builder()
            .name("title")
            .description("Title of the ban book.")
            .defaultValue("Kooda Ban")
            .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
            .name("debug-info")
            .description("Show verification logs.")
            .defaultValue(false)
            .build()
    );

    private boolean isReady = false;
    private int timer = 0;
    private int attempts = 0;
    private final Random random = new Random();
    private Stage stage = Stage.Find;

    private enum Stage {
        Find,
        Write,
        Wait,
        Drop
    }

    public AutoBookBan() {
        super(KoodaAddon.KOODA_EXPLOIT, "auto-book-ban", "Generates uncompressible heavy books.");
    }

    @Override
    public void onActivate() {
        isReady = false;
        timer = 0;
        stage = Stage.Find;
        attempts = 0;

        ChatUtils.sendMsg(Text.literal("")
                .append(Text.literal("[Kooda] ").styled(s -> s.withColor(TextColor.fromRgb(KoodaAddon.KOODA_COLOR.getPacked()))))
                .append(Text.literal("READY. Type ").formatted(Formatting.GRAY))
                .append(Text.literal("!ready").formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal(" to start.").formatted(Formatting.GRAY))
        );
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!isReady && event.message.trim().equalsIgnoreCase("!ready")) {
            event.cancel();
            isReady = true;
            ChatUtils.sendMsg(Text.literal("[Kooda] ").styled(s -> s.withColor(TextColor.fromRgb(KoodaAddon.KOODA_COLOR.getPacked())))
                    .append(Text.literal("Starting Production...").formatted(Formatting.GREEN)));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isReady) return;

        if (timer > 0) {
            timer--;
            return;
        }

        switch (stage) {
            case Find:
                int slot = InvUtils.find(Items.WRITABLE_BOOK).slot();
                if (slot == -1) {
                    ChatUtils.sendMsg(Text.literal("[Kooda] ").styled(s -> s.withColor(TextColor.fromRgb(KoodaAddon.KOODA_COLOR.getPacked())))
                            .append(Text.literal("Inventory empty.").formatted(Formatting.GRAY)));
                    toggle();
                    return;
                }
                InvUtils.move().from(slot).toHotbar(mc.player.getInventory().selectedSlot);
                stage = Stage.Write;
                timer = 1;
                break;

            case Write:
                if (mc.player.getMainHandStack().getItem() != Items.WRITABLE_BOOK) {
                    stage = Stage.Find;
                    return;
                }

                List<String> pageContent = new ArrayList<>();
                int charLimit = length.get();

                for (int i = 0; i < pages.get(); i++) {
                    pageContent.add(generateRandomJunk(charLimit));
                }

                mc.player.networkHandler.sendPacket(new BookUpdateC2SPacket(
                        mc.player.getInventory().selectedSlot,
                        pageContent,
                        Optional.of(generateRandomJunk(10))
                ));

                attempts = 0;
                stage = Stage.Wait;
                timer = 2;
                break;

            case Wait:
                if (mc.player.getMainHandStack().getItem() == Items.WRITTEN_BOOK) {
                    if (debug.get()) info("Signed successfully.");
                    stage = Stage.Drop;
                    timer = 0;
                } else {
                    attempts++;
                    if (attempts > 40) {
                        if (debug.get()) warning("Sign timed out. Retrying...");
                        stage = Stage.Write;
                    } else {
                        timer = 1;
                    }
                }
                break;

            case Drop:
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.DROP_ALL_ITEMS,
                        BlockPos.ORIGIN,
                        Direction.DOWN
                ));
                stage = Stage.Find;
                timer = 2;
                break;
        }
    }

    private String generateRandomJunk(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {

            int randomChar = random.nextInt(0xD7FF);

            if (randomChar < 0x20) randomChar += 0x20;
            sb.append((char) randomChar);
        }
        return sb.toString();
    }
}