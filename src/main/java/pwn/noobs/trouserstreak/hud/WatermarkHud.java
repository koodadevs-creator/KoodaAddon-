package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WatermarkHud extends HudElement {
    public static final HudElementInfo<WatermarkHud> INFO = new HudElementInfo<>(
            KoodaAddon.KOODA_HUD_GROUP,
            "kooda-watermark",
            "Kooda Watermark",
            "Advanced watermark with dynamic placeholders and chroma.",
            WatermarkHud::new
    );

    // --- RESOURCES ---
    private static final Identifier LOGO_TEXTURE = Identifier.of("kooda", "logo.png");
    private static final int BASE_LOGO_SIZE = 64;

    // --- SETTINGS ---
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style");

    // -- Content Settings --

    private final Setting<Boolean> showLogo = sgGeneral.add(new BoolSetting.Builder()
            .name("show-logo")
            .description("Displays the Kooda logo image.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> logoScale = sgGeneral.add(new DoubleSetting.Builder()
            .name("logo-scale")
            .description("Scale of the logo image.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderMax(5.0)
            .visible(showLogo::get)
            .build()
    );

    private final Setting<Boolean> showText = sgGeneral.add(new BoolSetting.Builder()
            .name("show-text")
            .description("Displays the watermark text.")
            .defaultValue(true)
            .build()
    );

    private final Setting<String> format = sgGeneral.add(new StringSetting.Builder()
            .name("text-format")
            .description("Text to display. Supports {fps}, {ping}, {tps}, {name}, {time}.")
            .defaultValue("Kooda Client | {fps} FPS | {ping}ms")
            .visible(showText::get)
            .build()
    );

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
            .name("text-scale")
            .description("Scale of the text.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderMax(3.0)
            .visible(showText::get)
            .build()
    );

    // -- Layout Settings --

    private final Setting<Integer> spacing = sgGeneral.add(new IntSetting.Builder()
            .name("spacing")
            .description("Space between the logo and the text.")
            .defaultValue(10)
            .min(0)
            .sliderMax(50)
            .visible(() -> showLogo.get() && showText.get())
            .build()
    );

    private final Setting<Double> textOffsetY = sgGeneral.add(new DoubleSetting.Builder()
            .name("text-offset-y")
            .description("Vertical offset to align text with the logo.")
            .defaultValue(0.0)
            .min(-20.0)
            .sliderMax(20.0)
            .visible(showText::get)
            .build()
    );

    // -- Style Settings --

    private final Setting<Boolean> chroma = sgStyle.add(new BoolSetting.Builder()
            .name("chroma")
            .description("Applies a rainbow effect to the text.")
            .defaultValue(false)
            .visible(showText::get)
            .build()
    );

    private final Setting<Double> chromaSpeed = sgStyle.add(new DoubleSetting.Builder()
            .name("chroma-speed")
            .description("Speed of the chroma animation.")
            .defaultValue(2.0)
            .visible(() -> showText.get() && chroma.get())
            .build()
    );

    private final Setting<SettingColor> textColor = sgStyle.add(new ColorSetting.Builder()
            .name("text-color")
            .description("Color of the text.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 255))
            .visible(() -> showText.get() && !chroma.get())
            .build()
    );

    private final Setting<Boolean> drawBackground = sgStyle.add(new BoolSetting.Builder()
            .name("background")
            .description("Draws a background box behind the watermark.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> backgroundColor = sgStyle.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Color of the background.")
            .defaultValue(new SettingColor(20, 20, 20, 100))
            .visible(drawBackground::get)
            .build()
    );

    public WatermarkHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (!showLogo.get() && !showText.get()) {
            // Invisible box to allow selection in editor if everything is hidden
            setSize(10, 10);
            return;
        }

        // 1. CALCULATE CONTENT
        double realLogoSize = showLogo.get() ? (BASE_LOGO_SIZE * logoScale.get()) : 0;

        // Dynamic Text generation
        String displayText = getFormattedText();

        // Calculate text dimensions considering the custom scale
        double textW = showText.get() ? renderer.textWidth(displayText) * textScale.get() : 0;
        double textH = showText.get() ? renderer.textHeight() * textScale.get() : 0;

        // 2. CALCULATE HUD SIZE
        double contentWidth = 0;
        if (showLogo.get()) contentWidth += realLogoSize;
        if (showText.get()) contentWidth += textW;
        if (showLogo.get() && showText.get()) contentWidth += spacing.get();

        double contentHeight = Math.max(realLogoSize, textH);

        // Add padding for the background
        double padding = 4.0;
        double totalWidth = contentWidth + (padding * 2);
        double totalHeight = contentHeight + (padding * 2);

        setSize(totalWidth, totalHeight);

        // 3. RENDER BACKGROUND
        if (drawBackground.get()) {
            renderer.quad(x, y, totalWidth, totalHeight, backgroundColor.get());
        }

        // 4. RENDER ELEMENTS
        double currentX = x + padding;
        double centerY = y + (totalHeight / 2);

        // Draw Logo
        if (showLogo.get()) {
            double logoY = centerY - (realLogoSize / 2);
            renderer.texture(LOGO_TEXTURE, currentX, logoY, realLogoSize, realLogoSize, Color.WHITE);
            currentX += realLogoSize + spacing.get();
        }

        // Draw Text
        if (showText.get()) {
            double textY = centerY - (textH / 2) + textOffsetY.get();

            Color finalColor = chroma.get() ? getChromaColor() : textColor.get();

            renderer.text(displayText, currentX, textY, finalColor, true, textScale.get());
        }
    }

    private String getFormattedText() {
        if (!showText.get()) return "";

        String txt = format.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        // Replace {fps}
        if (txt.contains("{fps}")) {
            txt = txt.replace("{fps}", String.valueOf(mc.getCurrentFps()));
        }

        // Replace {ping}
        if (txt.contains("{ping}")) {
            int ping = PlayerUtils.getPing();
            txt = txt.replace("{ping}", String.valueOf(ping));
        }

        // Replace {tps}
        if (txt.contains("{tps}")) {
            String tps = String.format("%.1f", TickRate.INSTANCE.getTickRate());
            txt = txt.replace("{tps}", tps);
        }

        // Replace {name}
        if (txt.contains("{name}")) {
            String name = mc.player != null ? mc.player.getName().getString() : "Player";
            txt = txt.replace("{name}", name);
        }

        // Replace {time}
        if (txt.contains("{time}")) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            txt = txt.replace("{time}", time);
        }

        return txt;
    }

    private Color getChromaColor() {
        double speed = chromaSpeed.get() * 0.4;
        long time = System.currentTimeMillis();
        float hue = (float) ((time * speed) % 3000) / 3000F;
        return new Color(java.awt.Color.HSBtoRGB(hue, 1.0F, 1.0F));
    }
}