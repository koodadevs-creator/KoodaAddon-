package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.RainbowColor;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WelcomerHud extends HudElement {
    public static final HudElementInfo<WelcomerHud> INFO = new HudElementInfo(
            KoodaAddon.KOODA_HUD_GROUP,
            "kooda-welcomer",
            "Advanced Welcomer",
            "Displays a customizable welcome message on screen.",
            WelcomerHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style");

    // --- GENERAL SETTINGS ---
    private final Setting<String> format = sgGeneral.add(new StringSetting.Builder()
            .name("format")
            .description("The text to display. Placeholders: {player}, {server}, {fps}, {time}, {ping}.")
            .defaultValue("KoodaAddon on Top! Hello {player} :^)")
            .build()
    );

    private final Setting<Double> scale = sgStyle.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Scale of the text.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderMax(5.0)
            .build()
    );

    // --- COLOR SETTINGS ---
    private final Setting<Boolean> chroma = sgStyle.add(new BoolSetting.Builder()
            .name("chroma")
            .description("Applies a rainbow effect to the text.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> chromaSpeed = sgStyle.add(new DoubleSetting.Builder()
            .name("chroma-speed")
            .description("Speed of the rainbow effect.")
            .defaultValue(0.5)
            .min(0.01)
            .sliderMax(2.0)
            .visible(chroma::get)
            .build()
    );

    private final Setting<SettingColor> color = sgStyle.add(new ColorSetting.Builder()
            .name("text-color")
            .description("Color of the text (ignored if Chroma is on).")
            .defaultValue(new SettingColor(255, 255, 255))
            .visible(() -> !chroma.get())
            .build()
    );

    private final Setting<Boolean> shadow = sgStyle.add(new BoolSetting.Builder()
            .name("shadow")
            .description("Renders a shadow behind the text for better visibility.")
            .defaultValue(true)
            .build()
    );

    // Internal Rainbow utility from Meteor
    private final RainbowColor rainbow = new RainbowColor();

    public WelcomerHud() {
        super(INFO);
    }

    // FIXED: Removed 'onTick' method which caused the override error.
    // Logic moved to 'render' for smoother animation (per frame instead of per tick).

    @Override
    public void render(HudRenderer renderer) {
        // Update rainbow logic per frame for smoothness
        if (chroma.get()) {
            rainbow.setSpeed(chromaSpeed.get() / 100.0);
            rainbow.getNext();
        }

        // Prepare the text
        String text = getFormattedText();

        double s = scale.get();

        // Calculate size for the HUD editor box
        double w = renderer.textWidth(text) * s;
        double h = renderer.textHeight() * s;
        setSize(w, h);

        // Determine final color
        Color finalColor;
        if (chroma.get()) {
            // Get the current color state without advancing it again to avoid double-skipping
            finalColor = rainbow.getNext();
        } else {
            finalColor = color.get();
        }

        // Render
        renderer.text(text, x, y, finalColor, shadow.get(), s);
    }

    private String getFormattedText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "Loading...";

        String text = format.get();

        // {player} - Username
        if (text.contains("{player}")) {
            text = text.replace("{player}", mc.player.getName().getString());
        }

        // {server} - Server IP or Singleplayer
        if (text.contains("{server}")) {
            String serverName = "Singleplayer";
            if (mc.getCurrentServerEntry() != null) {
                serverName = mc.getCurrentServerEntry().address;
            }
            text = text.replace("{server}", serverName);
        }

        // {fps} - Frames Per Second
        if (text.contains("{fps}")) {
            text = text.replace("{fps}", String.valueOf(mc.getCurrentFps()));
        }

        // {time} - Local Time (HH:mm:ss)
        if (text.contains("{time}")) {
            text = text.replace("{time}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }

        // {ping} - Player Latency
        if (text.contains("{ping}")) {
            int ping = 0;
            // Robust check to avoid NullPointer if network handler is missing
            if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
                ping = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency();
            }
            text = text.replace("{ping}", String.valueOf(ping));
        }

        return text;
    }
}