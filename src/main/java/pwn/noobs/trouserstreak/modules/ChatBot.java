package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import org.meteordev.starscript.Script;
import org.meteordev.starscript.compiler.Compiler;
import org.meteordev.starscript.compiler.Parser;
import org.meteordev.starscript.utils.StarscriptError;

import java.util.ArrayList;
import java.util.List;

public class ChatBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
            .name("prefix")
            .description("Command prefix for the bot.")
            .defaultValue("!")
            .build()
    );

    private final Setting<Boolean> help = sgGeneral.add(new BoolSetting.Builder()
            .name("help")
            .description("Add help command.")
            .defaultValue(true)
            .build()
    );

    private final Setting<List<String>> commands = sgGeneral.add(new StringListSetting.Builder()
            .name("commands")
            .description("Format: 'trigger:response'. Supports Starscript.")
            .defaultValue(List.of(
                    "ping:Pong!",
                    "tps:Current TPS: {server.tps}",
                    "time:It's currently {server.time}",
                    "pos:I am @ {player.pos}"
            ))
            .build()
    );

    public ChatBot() {
        super(KoodaAddon.KOODA_MISC, "chat-bot", "Bot which automatically responds to chat messages.");
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        String currentPrefix = prefix.get();

        if (help.get() && msg.endsWith(currentPrefix + "help")) {
            List<String> triggers = new ArrayList<>();
            for (String entry : commands.get()) {
                String[] parts = entry.split(":", 2);
                if (parts.length > 0) triggers.add(parts[0]);
            }

            ChatUtils.sendPlayerMsg("Available commands: " + String.join(", ", triggers));
            return;
        }

        for (String entry : commands.get()) {
            String[] parts = entry.split(":", 2);
            if (parts.length < 2) continue;

            String trigger = parts[0];
            String responseTemplate = parts[1];

            if (msg.endsWith(currentPrefix + trigger)) {
                Script script = compile(responseTemplate);

                if (script == null) {
                    ChatUtils.sendPlayerMsg("Error compiling script for: " + trigger);
                    return;
                }

                try {
                    var section = MeteorStarscript.ss.run(script);
                    ChatUtils.sendPlayerMsg(section.text);
                } catch (StarscriptError e) {
                    MeteorStarscript.printChatError(e);
                    ChatUtils.sendPlayerMsg("An error occurred executing command.");
                }
                return;
            }
        }
    }

    private static Script compile(String script) {
        if (script == null) return null;
        Parser.Result result = Parser.parse(script);
        if (result.hasErrors()) {
            MeteorStarscript.printChatError(result.errors.get(0));
            return null;
        }
        return Compiler.compile(result);
    }
}