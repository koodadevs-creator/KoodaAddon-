package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.security.SecureRandom;
import java.util.*;

public class AutoLogin extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay in ms before executing the command.")
            .defaultValue(1000)
            .min(0)
            .sliderMax(10000)
            .build()
    );

    private final Setting<Boolean> smart = sgGeneral.add(new BoolSetting.Builder()
            .name("smart")
            .description("Automatically saves your login command when you type it.")
            .defaultValue(false)
            .build()
    );

    private final Setting<List<String>> commands = sgGeneral.add(new StringListSetting.Builder()
            .name("commands")
            .description("Format: 'IP:Command'. Use '*' for universal.")
            .defaultValue(Collections.singletonList("localhost:/login 123456"))
            .build()
    );

    private final Timer timer = new Timer();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public AutoLogin() {
        super(KoodaAddon.KOODA_MISC, "auto-login", "Runs command when joining specified server.");
        runInMainMenu = true;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList l = theme.horizontalList();
        WButton btn = l.add(theme.button("Generate Random Password")).widget();
        btn.action = () -> {
            String password = generateRandomPassword(16);
            String command = String.format("/register %s %s", password, password);

            MutableText text = Text.literal(Formatting.BOLD + "Suggested Password (Copy/Paste): ");
            text.append(Text.literal(Formatting.RESET + command));
            info(text);
        };
        return l;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive()) return;

        String command = getCommandFor("*");
        if (command == null) {
            command = getCommandFor(Utils.getWorldName());
        }

        if (command != null) {
            String finalCommand = command;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (mc.player != null) {
                        mc.execute(() -> ChatUtils.sendPlayerMsg(finalCommand));
                    }
                }
            }, delay.get());
        }
    }

    @EventHandler
    private void onPacketSent(PacketEvent.Send event) {
        if (!smart.get()) return;

        if (event.packet instanceof CommandExecutionC2SPacket packet) {
            String fullCommand = packet.command();
            List<String> hint = Arrays.asList("reg", "register", "l", "login", "log");
            String[] cmds = fullCommand.split(" ");

            if (cmds.length >= 2 && hint.contains(cmds[0].toLowerCase())) {
                String ip = Utils.getWorldName();
                String newEntry = ip + ":/login " + cmds[1];
                updateCommandList(ip, newEntry);
            }
        }
    }

    private String getCommandFor(String ip) {
        for (String entry : commands.get()) {
            String[] split = entry.split(":", 2);
            if (split.length == 2 && split[0].equalsIgnoreCase(ip)) {
                return split[1];
            }
        }
        return null;
    }

    private void updateCommandList(String ip, String newEntry) {
        List<String> current = new ArrayList<>(commands.get());
        current.removeIf(s -> s.toLowerCase().startsWith(ip.toLowerCase() + ":"));
        current.add(newEntry);
        commands.set(current);
    }

    private String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}