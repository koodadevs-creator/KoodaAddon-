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
            "Advanced watermark with dynamic icons and stats.",
            WatermarkHud::new
    );

    public enum IconMode {
        Open,
        Closed
    }

    private static final Identifier ICON_OPEN = Identifier.of("kooda", "logo.png");
    private static final Identifier ICON_CLOSED = Identifier.of("kooda", "logo2.png");
    private static final int BASE_LOGO_SIZE = 64;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgText = settings.createGroup("Text");
    private final SettingGroup sgStyle = settings.createGroup("Style");
    private final SettingGroup sgBackground = settings.createGroup("Background");

    private final Setting<Boolean> showLogo = sgGeneral.add(new BoolSetting.Builder()
            .name("show-icon")
            .description("Displays the Kooda icon.")
            .defaultValue(true)
            .build()
    );

    private final Setting<IconMode> iconMode = sgGeneral.add(new EnumSetting.Builder<IconMode>()
            .name("icon-variant")
            .description("Select which icon to display.")
            .defaultValue(IconMode.Open)
            .visible(showLogo::get)
            .build()
    );

    private final Setting<Double> logoScale = sgGeneral.add(new DoubleSetting.Builder()
            .name("icon-scale")
            .description("Scale of the icon image.")
            .defaultValue(0.8)
            .min(0.1)
            .sliderMax(3.0)
            .visible(showLogo::get)
            .build()
    );

    private final Setting<Boolean> showText = sgText.add(new BoolSetting.Builder()
            .name("show-text")
            .description("Displays the watermark text info.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> spacing = sgGeneral.add(new IntSetting.Builder()
            .name("spacing")
            .description("Space between the logo and the text.")
            .defaultValue(6)
            .min(0)
            .sliderMax(20)
            .visible(() -> showLogo.get() && showText.get())
            .build()
    );

    private final Setting<String> format = sgText.add(new StringSetting.Builder()
            .name("format")
            .description("Variables: {fps}, {ping}, {tps}, {name}, {time}, {server}, {coords}")
            .defaultValue("Kooda | {fps} FPS | {ping}ms")
            .visible(showText::get)
            .build()
    );

    private final Setting<Double> textScale = sgText.add(new DoubleSetting.Builder()
            .name("text-scale")
            .description("Scale of the text.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderMax(3.0)
            .visible(showText::get)
            .build()
    );

    private final Setting<Double> textOffsetY = sgText.add(new DoubleSetting.Builder()
            .name("text-offset-y")
            .description("Vertical offset to align text with the logo.")
            .defaultValue(-1.0)
            .min(-10.0)
            .sliderMax(10.0)
            .visible(showText::get)
            .build()
    );

    private final Setting<Boolean> chroma = sgStyle.add(new BoolSetting.Builder()
            .name("chroma-text")
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

    private final Setting<Boolean> drawBackground = sgBackground.add(new BoolSetting.Builder()
            .name("background")
            .description("Draws a background box.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
            .name("bg-color")
            .description("Color of the background.")
            .defaultValue(new SettingColor(20, 20, 20, 150))
            .visible(drawBackground::get)
            .build()
    );

    private final Setting<Boolean> border = sgBackground.add(new BoolSetting.Builder()
            .name("border")
            .description("Draws a border around the hud.")
            .defaultValue(true)
            .visible(drawBackground::get)
            .build()
    );

    private final Setting<SettingColor> borderColor = sgBackground.add(new ColorSetting.Builder()
            .name("border-color")
            .description("Color of the border.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 255))
            .visible(() -> drawBackground.get() && border.get())
            .build()
    );

    public WatermarkHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (!showLogo.get() && !showText.get()) {
            setSize(10, 10);
            return;
        }

        double realLogoSize = showLogo.get() ? (BASE_LOGO_SIZE * logoScale.get()) : 0;

        String displayText = getFormattedText();
        double txtW = 0;
        double txtH = 0;

        if (showText.get()) {
            txtW = renderer.textWidth(displayText) * textScale.get();
            txtH = renderer.textHeight() * textScale.get();
        }

        double gap = (showLogo.get() && showText.get()) ? spacing.get() : 0;

        double contentWidth = realLogoSize + gap + txtW;
        double contentHeight = Math.max(realLogoSize, txtH);

        double padX = 4.0;
        double padY = 4.0;

        double totalW = contentWidth + (padX * 2);
        double totalH = contentHeight + (padY * 2);

        setSize(totalW, totalH);

        if (drawBackground.get()) {
            renderer.quad(x, y, totalW, totalH, backgroundColor.get());

            if (border.get()) {
                Color bc = borderColor.get();
                renderer.quad(x, y, totalW, 1, bc);
                renderer.quad(x, y + totalH - 1, totalW, 1, bc);
                renderer.quad(x, y, 1, totalH, bc);
                renderer.quad(x + totalW - 1, y, 1, totalH, bc);
            }
        }

        double currentX = x + padX;
        double centerY = y + (totalH / 2);

        if (showLogo.get()) {
            double iconY = centerY - (realLogoSize / 2);
            Identifier currentTexture = (iconMode.get() == IconMode.Closed) ? ICON_CLOSED : ICON_OPEN;
            renderer.texture(currentTexture, currentX, iconY, realLogoSize, realLogoSize, Color.WHITE);
            currentX += realLogoSize + gap;
        }

        if (showText.get()) {
            double textY = centerY - (txtH / 2) + textOffsetY.get();
            Color finalColor = chroma.get() ? getChromaColor() : textColor.get();
            renderer.text(displayText, currentX, textY, finalColor, true, textScale.get());
        }
    }

    private String getFormattedText() {
        if (!showText.get()) return "";

        String txt = format.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "Loading...";

        if (txt.contains("{fps}")) txt = txt.replace("{fps}", String.valueOf(mc.getCurrentFps()));
        if (txt.contains("{ping}")) txt = txt.replace("{ping}", String.valueOf(PlayerUtils.getPing()));
        if (txt.contains("{tps}")) txt = txt.replace("{tps}", String.format("%.1f", TickRate.INSTANCE.getTickRate()));
        if (txt.contains("{name}")) txt = txt.replace("{name}", mc.player.getName().getString());
        if (txt.contains("{time}")) txt = txt.replace("{time}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        if (txt.contains("{server}")) {
            String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
            txt = txt.replace("{server}", server);
        }

        if (txt.contains("{coords}")) {
            String coords = String.format("%d, %d, %d", (int)mc.player.getX(), (int)mc.player.getY(), (int)mc.player.getZ());
            txt = txt.replace("{coords}", coords);
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