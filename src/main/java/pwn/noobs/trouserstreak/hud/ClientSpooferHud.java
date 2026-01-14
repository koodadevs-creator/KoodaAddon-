package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Identifier;

public class ClientSpooferHud extends HudElement {
    public static final HudElementInfo<ClientSpooferHud> INFO = new HudElementInfo<>(
            KoodaAddon.KOODA_HUD_GROUP,
            "client-spoofer",
            "Displays logos of other clients.",
            ClientSpooferHud::new
    );

    public enum ClientLogo {
        Boze,
        Future,
        RusherHack,
        Konas
    }


    private static final Identifier BOZE = Identifier.of("kooda", "textures/spoof/boze.png");
    private static final Identifier FUTURE = Identifier.of("kooda", "textures/spoof/future.png");
    private static final Identifier RUSHERHACK = Identifier.of("kooda", "textures/spoof/rusher.png");
    private static final Identifier KONAS = Identifier.of("kooda", "textures/spoof/konas.png");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ClientLogo> client = sgGeneral.add(new EnumSetting.Builder<ClientLogo>()
            .name("client")
            .description("The client logo to display.")
            .defaultValue(ClientLogo.Future)
            .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("The scale of the logo.")
            .defaultValue(2.0)
            .min(0.5)
            .sliderMax(5.0)
            .build()
    );

    public ClientSpooferHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double s = scale.get();
        double size = 32 * s;

        setSize(size, size);

        Identifier texture = switch (client.get()) {
            case Boze -> BOZE;
            case RusherHack -> RUSHERHACK;
            case Konas -> KONAS;
            default -> FUTURE;
        };

        renderer.texture(texture, x, y, size, size, Color.WHITE);
    }
}