package pwn.noobs.trouserstreak.modules;

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.orbit.EventHandler;
import pwn.noobs.trouserstreak.KoodaAddon;

import java.util.List;

public class KoodaRPC extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgContent = settings.createGroup("Content");
    private final SettingGroup sgImages = settings.createGroup("Images");

    private final Setting<String> appId = sgGeneral.add(new StringSetting.Builder()
            .name("app-id")
            .description("The Discord Application ID. Create one at discord.com/developers.")
            .defaultValue("1459997667487907872")
            .build()
    );

    private final Setting<Integer> updateDelay = sgGeneral.add(new IntSetting.Builder()
            .name("update-delay")
            .description("How many seconds to wait between RPC updates.")
            .defaultValue(2)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<List<String>> details = sgContent.add(new StringListSetting.Builder()
            .name("details")
            .description("The top line of text. Supports Starscript placeholders. Rotates if multiple.")
            .defaultValue(List.of("Playing KoodaAddon", "Owning {server}"))
            .renderer(StarscriptTextBoxRenderer.class)
            .build()
    );

    private final Setting<List<String>> state = sgContent.add(new StringListSetting.Builder()
            .name("state")
            .description("The bottom line of text. Supports Starscript placeholders. Rotates if multiple.")
            .defaultValue(List.of("Health: {health}", "Ping: {ping}"))
            .renderer(StarscriptTextBoxRenderer.class)
            .build()
    );

    private final Setting<Boolean> showTime = sgContent.add(new BoolSetting.Builder()
            .name("show-time")
            .description("Displays the elapsed time since you started the game.")
            .defaultValue(true)
            .build()
    );

    private final Setting<String> largeImageKey = sgImages.add(new StringSetting.Builder()
            .name("large-image-key")
            .description("The asset key for the large image.")
            .defaultValue("kooda_logo")
            .build()
    );

    private final Setting<String> largeImageText = sgImages.add(new StringSetting.Builder()
            .name("large-image-text")
            .description("Text when hovering the large image.")
            .defaultValue("Kooda Addon on Top")
            .renderer(StarscriptTextBoxRenderer.class)
            .build()
    );

    private final Setting<String> smallImageKey = sgImages.add(new StringSetting.Builder()
            .name("small-image-key")
            .description("The asset key for the small image (circle).")
            .defaultValue("meteor_logo")
            .build()
    );

    private final Setting<String> smallImageText = sgImages.add(new StringSetting.Builder()
            .name("small-image-text")
            .description("Text when hovering the small image.")
            .defaultValue("{player}")
            .renderer(StarscriptTextBoxRenderer.class)
            .build()
    );

    private final RichPresence rpc = new RichPresence();
    private int ticks = 0;
    private int detailsIndex = 0;
    private int stateIndex = 0;

    public KoodaRPC() {
        super(KoodaAddon.KOODA_MISC, "kooda-rpc", "Highly configurable Rich Presence with Starscript support.");
    }

    @Override
    public void onActivate() {
        if (!appId.get().isEmpty()) {
            try {
                DiscordIPC.start(Long.parseLong(appId.get()), null);
                if (showTime.get()) rpc.setStart(System.currentTimeMillis() / 1000L);
                updateRPC();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDeactivate() {
        DiscordIPC.stop();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (ticks >= updateDelay.get() * 20) {
            updateRPC();
            ticks = 0;
        } else {
            ticks++;
        }
    }

    private void updateRPC() {
        if (details.get().isEmpty()) rpc.setDetails(null);
        else {
            if (detailsIndex >= details.get().size()) detailsIndex = 0;
            rpc.setDetails(MeteorStarscript.run(MeteorStarscript.compile(details.get().get(detailsIndex))));
            detailsIndex++;
        }

        if (state.get().isEmpty()) rpc.setState(null);
        else {
            if (stateIndex >= state.get().size()) stateIndex = 0;
            rpc.setState(MeteorStarscript.run(MeteorStarscript.compile(state.get().get(stateIndex))));
            stateIndex++;
        }

        rpc.setLargeImage(largeImageKey.get(), MeteorStarscript.run(MeteorStarscript.compile(largeImageText.get())));
        rpc.setSmallImage(smallImageKey.get(), MeteorStarscript.run(MeteorStarscript.compile(smallImageText.get())));

        DiscordIPC.setActivity(rpc);
    }
}