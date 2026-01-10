package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class BitCoinHud extends HudElement {
    public static final HudElementInfo<BitCoinHud> INFO = new HudElementInfo<>(
            KoodaAddon.KOODA_HUD_GROUP,
            "bitcoin-hud",
            "Displays live Bitcoin price.",
            BitCoinHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Scale of the text.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderMax(4.0)
            .build()
    );

    private final Setting<Integer> updateDelay = sgGeneral.add(new IntSetting.Builder()
            .name("update-delay")
            .description("Update interval in seconds.")
            .defaultValue(60)
            .min(10)
            .sliderMax(300)
            .build()
    );

    private double currentPrice = 0;
    private double oldPrice = 0;
    private int timer = 0;
    private boolean fetching = false;
    private boolean error = false;

    public BitCoinHud() {
        super(INFO);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
        } else {
            fetchPrice();
            timer = updateDelay.get() * 20;
        }
    }

    private void fetchPrice() {
        if (fetching) return;
        fetching = true;
        error = false;

        new Thread(() -> {
            try {
                URL url = new URL("https://api.coindesk.com/v1/bpi/currentprice/USD.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                reader.close();

                double newPrice = json.getAsJsonObject("bpi").getAsJsonObject("USD").get("rate_float").getAsDouble();

                synchronized (this) {
                    if (currentPrice != 0) oldPrice = currentPrice;
                    else oldPrice = newPrice;

                    currentPrice = newPrice;
                }

            } catch (Exception e) {
                error = true;
                e.printStackTrace();
            } finally {
                fetching = false;
            }
        }).start();
    }

    @Override
    public void render(HudRenderer renderer) {
        String text;
        Color textColor;

        synchronized (this) {
            if (error) {
                text = "BTC - Error";
                textColor = new Color(255, 50, 50, 255);
            } else if (currentPrice == 0) {
                text = "BTC - Loading...";
                textColor = Color.GRAY;
            } else {
                text = String.format("BTC - $%.2f", currentPrice);

                if (currentPrice > oldPrice) {
                    textColor = new Color(0, 255, 0, 255);
                } else if (currentPrice < oldPrice) {
                    textColor = new Color(255, 0, 0, 255);
                } else {
                    textColor = new Color(0, 255, 255, 255);
                }
            }
        }

        double width = renderer.textWidth(text, true) * scale.get();
        double height = renderer.textHeight(true) * scale.get();

        setSize(width, height);
        renderer.text(text, x, y, textColor, true, scale.get());
    }
}