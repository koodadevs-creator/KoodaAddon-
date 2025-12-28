package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.*;
import java.util.stream.Collectors;

public class VisualRangeHud extends HudElement {
    public static final HudElementInfo<VisualRangeHud> INFO = new HudElementInfo<>(
            KoodaAddon.KOODA_HUD_GROUP,
            "visual-range-hud",
            "Visual Range HUD",
            "Displays notifications when players enter render distance.",
            VisualRangeHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // -- Settings --

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("The scale of the notification.")
            .defaultValue(1.0)
            .min(0.5)
            .max(3.0)
            .sliderMax(3.0)
            .build()
    );

    private final Setting<Double> displayTime = sgGeneral.add(new DoubleSetting.Builder()
            .name("duration")
            .description("How long the notification stays on screen (seconds).")
            .defaultValue(4.0)
            .min(1.0)
            .max(10.0)
            .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
            .name("text-color")
            .description("Color of the text.")
            .defaultValue(new SettingColor(255, 255, 255))
            .build()
    );

    private final Setting<SettingColor> accentColor = sgGeneral.add(new ColorSetting.Builder()
            .name("accent-color")
            .description("Color of the progress bar and emphasis.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 255))
            .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Background color of the notification.")
            .defaultValue(new SettingColor(20, 20, 20, 150))
            .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Don't notify for friends.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showFakes = sgGeneral.add(new BoolSetting.Builder()
            .name("show-fake-players")
            .description("Shows notifications for players with your own name (FakePlayers). Useful for testing.")
            .defaultValue(true)
            .build()
    );

    // -- State --
    private final List<Notification> notifications = new ArrayList<>();
    // Use a Set to remember UUIDs of players currently in range
    private final Set<UUID> knownPlayers = new HashSet<>();

    public VisualRangeHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        double s = scale.get();
        double notificationHeight = (renderer.textHeight() + 8) * s;
        double spacing = 2 * s;

        // --- 1. SYNC LOGIC (The Fix) ---

        // A. Get all currently loaded players
        List<AbstractClientPlayerEntity> currentPlayers = mc.world.getPlayers();

        // Create a quick lookup set of UUIDs present RIGHT NOW
        Set<UUID> currentUUIDs = currentPlayers.stream()
                .map(AbstractClientPlayerEntity::getUuid)
                .collect(Collectors.toSet());

        // B. FORGET PHASE: Remove anyone from 'knownPlayers' who is NOT in the world anymore.
        // This ensures that as soon as you leave render distance, they are forgotten.
        knownPlayers.removeIf(uuid -> !currentUUIDs.contains(uuid));

        // C. DISCOVERY PHASE: Find new players
        for (AbstractClientPlayerEntity player : currentPlayers) {
            UUID uuid = player.getUuid();

            // If we don't know them, they are new!
            if (!knownPlayers.contains(uuid)) {

                // Filters
                if (player.equals(mc.player)) continue;

                boolean isFakePlayer = player.getName().getString().equals(mc.player.getName().getString());
                if (isFakePlayer && !showFakes.get()) continue;
                if (!isFakePlayer && ignoreFriends.get() && meteordevelopment.meteorclient.systems.friends.Friends.get().isFriend(player)) continue;

                // Notify
                Notification newNotif = new Notification(player.getName().getString());
                notifications.add(0, newNotif);

                // Mark as known so we don't spam
                knownPlayers.add(uuid);
            }
        }

        // --- 2. RENDER & ANIMATION ---

        boolean alignRight = getX() + (getWidth() / 2) > mc.getWindow().getScaledWidth() / 2.0;

        if (notifications.isEmpty()) {
            if (isInEditor()) {
                String text = "Player123 entered visual range";
                double w = (renderer.textWidth(text) + 12) * s;
                renderer.quad(x, y, w, notificationHeight, backgroundColor.get());
                renderer.text(text, x + (6 * s), y + (4 * s), textColor.get(), true, s);
                renderer.quad(x, y + notificationHeight - (2 * s), w, 2 * s, accentColor.get());
                setSize(w, notificationHeight);
            } else {
                setSize(0, 0);
            }
            return;
        }

        double maxW = 0;
        double targetYOffset = 0;

        for (int i = 0; i < notifications.size(); i++) {
            Notification notif = notifications.get(i);

            long now = System.currentTimeMillis();
            long timeAlive = now - notif.startTime;
            double maxTimeMillis = displayTime.get() * 1000;

            if (timeAlive > maxTimeMillis) {
                notifications.remove(i);
                i--;
                continue;
            }

            // Vertical Slide (Lerp)
            notif.yPos = MathHelper.lerp(0.2, notif.yPos, targetYOffset);

            // Horizontal Slide (Fade In/Out)
            double progress = (double) timeAlive / maxTimeMillis;
            double animationFactor;
            double fadeTime = 400;

            if (timeAlive < fadeTime) {
                animationFactor = MathHelper.clamp(timeAlive / fadeTime, 0, 1);
                animationFactor = Math.sin(animationFactor * Math.PI / 2);
            } else if (timeAlive > maxTimeMillis - fadeTime) {
                double timeLeft = maxTimeMillis - timeAlive;
                animationFactor = MathHelper.clamp(timeLeft / fadeTime, 0, 1);
            } else {
                animationFactor = 1.0;
            }

            String text = notif.playerName + " entered visual range";
            double textW = renderer.textWidth(text);
            double boxW = (textW + 12) * s;

            if (boxW > maxW) maxW = boxW;

            double drawX;
            double offsetX = boxW * (1.0 - animationFactor);

            if (alignRight) {
                double rightEdge = x + maxW;
                drawX = (rightEdge - boxW) + offsetX;
            } else {
                drawX = x - offsetX;
            }

            double drawY = y + notif.yPos;

            // Background
            renderer.quad(drawX, drawY, boxW, notificationHeight, backgroundColor.get());

            // Text
            renderer.text(text, drawX + (6 * s), drawY + (4 * s), textColor.get(), true, s);

            // Bar
            double barWidth = boxW * (1.0 - progress);
            renderer.quad(drawX, drawY + notificationHeight - (2 * s), barWidth, 2 * s, accentColor.get());

            targetYOffset += notificationHeight + spacing;
        }

        if (maxW < 50) maxW = 50;
        double totalHeight = targetYOffset;
        if (totalHeight < 10) totalHeight = 10;

        setSize(maxW, totalHeight);
    }

    private static class Notification {
        String playerName;
        long startTime;
        double yPos;

        public Notification(String name) {
            this.playerName = name;
            this.startTime = System.currentTimeMillis();
            this.yPos = -20;
        }
    }
}