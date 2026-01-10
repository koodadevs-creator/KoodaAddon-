package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class RealTimeClock extends HudElement {
    public static final HudElementInfo<RealTimeClock> INFO = new HudElementInfo<>(
            KoodaAddon.KOODA_HUD_GROUP,
            "real-time-clock",
            "Displays real time for specific zones.",
            RealTimeClock::new
    );

    public enum TimeZone {
        USA_Central("America/Chicago"),
        Spain_Central("Europe/Madrid");

        private final String zoneId;

        TimeZone(String zoneId) {
            this.zoneId = zoneId;
        }

        public ZoneId getZoneId() {
            return ZoneId.of(zoneId);
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<TimeZone> timeZone = sgGeneral.add(new EnumSetting.Builder<TimeZone>()
            .name("time-zone")
            .description("The time zone to display.")
            .defaultValue(TimeZone.USA_Central)
            .build()
    );

    private final Setting<Boolean> seconds = sgGeneral.add(new BoolSetting.Builder()
            .name("seconds")
            .description("Show seconds.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Scale of the text.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderMax(4.0)
            .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
            .name("color")
            .description("Color of the text.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .build()
    );

    public RealTimeClock() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        String formatString = seconds.get() ? "HH:mm:ss" : "HH:mm";
        ZonedDateTime time = ZonedDateTime.now(timeZone.get().getZoneId());
        String text = time.format(DateTimeFormatter.ofPattern(formatString));

        double width = renderer.textWidth(text, true) * scale.get();
        double height = renderer.textHeight(true) * scale.get();

        setSize(width, height);
        renderer.text(text, x, y, color.get(), true, scale.get());
    }
}