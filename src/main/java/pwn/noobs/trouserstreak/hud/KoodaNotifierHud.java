package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KoodaNotifierHud extends HudElement {
    public static final HudElementInfo<KoodaNotifierHud> INFO = new HudElementInfo<>(
            KoodaAddon.KOODA_HUD_GROUP,
            "kooda-notifier-ultimate",
            "Kooda Notifier Ultimate",
            "The complete notification suite. RGB, Pulse Animations, and robust engine.",
            KoodaNotifierHud::new
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    // --- GROUPS ---
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTriggers = settings.createGroup("Triggers");
    private final SettingGroup sgVisual = settings.createGroup("Visuals & Style");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // --- GENERAL ---
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale").description("Global scale.").defaultValue(1.0).min(0.5).sliderMax(2.0).build());
    private final Setting<Double> displayTime = sgGeneral.add(new DoubleSetting.Builder()
            .name("duration").description("Time on screen (seconds).").defaultValue(3.0).min(1.0).max(10.0).build());
    private final Setting<Integer> maxNotifs = sgGeneral.add(new IntSetting.Builder()
            .name("max-visible").description("Limit notifications to prevent spam.").defaultValue(8).min(1).build());
    private final Setting<Boolean> logChat = sgGeneral.add(new BoolSetting.Builder()
            .name("log-to-chat").description("Print notifications to local chat.").defaultValue(false).build());

    // --- AUDIO ---
    private final Setting<Boolean> sounds = sgGeneral.add(new BoolSetting.Builder()
            .name("sounds").description("Play sounds.").defaultValue(true).build());
    private final Setting<Boolean> pitchScaling = sgGeneral.add(new BoolSetting.Builder()
            .name("pitch-scaling").description("Higher pitch for stacks.").defaultValue(true).visible(sounds::get).build());
    private final Setting<Boolean> customSound = sgGeneral.add(new BoolSetting.Builder()
            .name("custom-wav")
            .description("Plays 'notification.wav' from .minecraft/KOODA/NotificatorHudSound/")
            .defaultValue(false)
            .visible(sounds::get)
            .build()
    );

    // --- TRIGGERS ---
    private final Setting<Boolean> popEnabled = sgTriggers.add(new BoolSetting.Builder()
            .name("totem-pops").defaultValue(true).build());
    private final Setting<Boolean> burrowEnabled = sgTriggers.add(new BoolSetting.Builder()
            .name("burrow-detect").defaultValue(true).build());
    private final Setting<Boolean> pearlEnabled = sgTriggers.add(new BoolSetting.Builder()
            .name("pearl-throws").defaultValue(true).build());
    private final Setting<Boolean> killFeed = sgTriggers.add(new BoolSetting.Builder()
            .name("kill-feed").defaultValue(true).build());
    private final Setting<Boolean> mentionEnabled = sgTriggers.add(new BoolSetting.Builder()
            .name("chat-mentions").description("Notify when mentioned in chat.").defaultValue(true).build());
    private final Setting<Boolean> visualRange = sgTriggers.add(new BoolSetting.Builder()
            .name("visual-range").defaultValue(true).build());
    private final Setting<Boolean> armorEnabled = sgTriggers.add(new BoolSetting.Builder()
            .name("low-armor").defaultValue(true).build());
    private final Setting<Boolean> toolEnabled = sgTriggers.add(new BoolSetting.Builder()
            .name("low-tool").description("Warn for main hand tool durability.").defaultValue(true).build());
    private final Setting<Integer> durabilityThreshold = sgTriggers.add(new IntSetting.Builder()
            .name("durability-%").defaultValue(30).min(1).max(99).visible(() -> armorEnabled.get() || toolEnabled.get()).build());
    private final Setting<Boolean> lagEnabled = sgTriggers.add(new BoolSetting.Builder()
            .name("server-lag").defaultValue(true).build());

    // --- VISUALS ---
    private final Setting<Style> style = sgVisual.add(new EnumSetting.Builder<Style>()
            .name("style").description("Visual Theme.").defaultValue(Style.Kooda).build());
    private final Setting<Animation> animType = sgVisual.add(new EnumSetting.Builder<Animation>()
            .name("animation").description("Entry/Exit animation.").defaultValue(Animation.Slide).build());
    private final Setting<Boolean> timeBar = sgVisual.add(new BoolSetting.Builder()
            .name("time-bar").description("Show progress bar.").defaultValue(true).build());

    // --- COLORS ---
    private final Setting<Boolean> chroma = sgColors.add(new BoolSetting.Builder()
            .name("chroma-mode").description("RGB mode for accents.").defaultValue(false).build());

    private final Setting<SettingColor> colorInfo = sgColors.add(new ColorSetting.Builder()
            .name("info-color").defaultValue(new SettingColor(0, 200, 255)).build());
    private final Setting<SettingColor> colorWarn = sgColors.add(new ColorSetting.Builder()
            .name("warn-color").defaultValue(new SettingColor(255, 100, 0)).build()); // Sunset Orange
    private final Setting<SettingColor> colorDanger = sgColors.add(new ColorSetting.Builder()
            .name("danger-color").defaultValue(new SettingColor(255, 50, 50)).build());
    private final Setting<SettingColor> colorText = sgColors.add(new ColorSetting.Builder()
            .name("text-color").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<SettingColor> colorBg = sgColors.add(new ColorSetting.Builder()
            .name("background-color").defaultValue(new SettingColor(20, 20, 20, 160)).build());

    // --- VARIABLES ---
    private final CopyOnWriteArrayList<Notification> notifications = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> popCounts = new ConcurrentHashMap<>();
    private final Set<UUID> burrowedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, String> knownPlayers = new ConcurrentHashMap<>();

    private PlayerEntity lastTarget = null;
    private long lastAttackTime = 0;
    private long lastPacketTime = 0;
    private boolean lagNotified = false;
    private boolean armorNotified = false;
    private boolean toolNotified = false;

    private final File cachedSoundFile;
    private final ExecutorService soundExecutor = Executors.newCachedThreadPool();

    public KoodaNotifierHud() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
        File soundDir = new File(new File(MeteorClient.FOLDER.getParentFile(), "KOODA"), "NotificatorHudSound");
        if (!soundDir.exists()) soundDir.mkdirs();
        cachedSoundFile = new File(soundDir, "notification.wav");
    }

    // ================= EVENTS =================

    @EventHandler
    private void onGameLeave(GameLeftEvent event) {
        notifications.clear();
        popCounts.clear();
        knownPlayers.clear();
        burrowedPlayers.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        notifications.removeIf(n -> now - n.startTime > (displayTime.get() * 1000) + 1000);

        // Limit Queue Size
        while (notifications.size() > maxNotifs.get()) {
            notifications.remove(notifications.size() - 1);
        }

        // Lag
        if (lagEnabled.get()) {
            long diff = now - lastPacketTime;
            if (diff > 3000 && !lagNotified) {
                addNotification("Server Lag Detected!", Type.WARNING, Items.CLOCK.getDefaultStack());
                lagNotified = true;
            } else if (diff < 1000) lagNotified = false;
        }

        // Armor Check
        if (armorEnabled.get()) {
            boolean low = false;
            EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
            for (EquipmentSlot slot : slots) {
                ItemStack s = mc.player.getEquippedStack(slot);
                if (!s.isEmpty() && s.isDamageable()) {
                    int pct = (int)(((double)(s.getMaxDamage() - s.getDamage()) / s.getMaxDamage()) * 100);
                    if (pct <= durabilityThreshold.get()) { low = true; break; }
                }
            }
            if (low && !armorNotified) {
                addNotification("Armor Critical < " + durabilityThreshold.get() + "%", Type.DANGER, Items.NETHERITE_CHESTPLATE.getDefaultStack());
                armorNotified = true;
            } else if (!low) armorNotified = false;
        }

        // Tool Check
        if (toolEnabled.get()) {
            ItemStack main = mc.player.getMainHandStack();
            if (!main.isEmpty() && main.isDamageable()) {
                int pct = (int)(((double)(main.getMaxDamage() - main.getDamage()) / main.getMaxDamage()) * 100);
                if (pct <= durabilityThreshold.get()) {
                    if (!toolNotified) {
                        addNotification("Tool Critical < " + durabilityThreshold.get() + "%", Type.DANGER, main.copy());
                        toolNotified = true;
                    }
                } else toolNotified = false;
            } else toolNotified = false;
        }

        if (visualRange.get()) updateVisualRange();
        if (burrowEnabled.get()) updateBurrow();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null) return;

        lastPacketTime = System.currentTimeMillis();
        if (event.packet instanceof EntityStatusS2CPacket p) {
            Entity e = p.getEntity(mc.world);

            // Pop
            if (popEnabled.get() && p.getStatus() == 35 && e instanceof PlayerEntity player && !player.equals(mc.player)) {
                int c = popCounts.getOrDefault(player.getUuid(), 0) + 1;
                popCounts.put(player.getUuid(), c);
                addNotification(player.getName().getString() + " popped " + c + "!", Type.WARNING, Items.TOTEM_OF_UNDYING.getDefaultStack());
            }

            // Death
            if (killFeed.get() && p.getStatus() == 3 && e instanceof PlayerEntity player) {
                if (player.equals(mc.player)) {
                    addNotification("You died.", Type.DANGER, Items.SKELETON_SKULL.getDefaultStack());
                    popCounts.remove(mc.player.getUuid());
                } else if (lastTarget != null && lastTarget.equals(player) && System.currentTimeMillis() - lastAttackTime < 5000) {
                    addNotification("Killed " + player.getName().getString(), Type.INFO, Items.DIAMOND_SWORD.getDefaultStack());
                    popCounts.remove(player.getUuid());
                }
            }
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof PlayerEntity p) {
            lastTarget = p;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        // --- MENTION FIX START ---
        if (!mentionEnabled.get() || mc.player == null || mc.currentScreen instanceof ChatScreen) return;

        String msg = event.getMessage().getString();
        String myName = mc.player.getName().getString();

        // Safety check if name is empty
        if (myName.isEmpty()) return;

        // Check if message contains my name (case insensitive)
        if (msg.toLowerCase().contains(myName.toLowerCase())) {

            // IGNORE if message STARTS with my name (e.g. "[MyName] Hello")
            // This covers standard chat formats like "<MyName> msg" or "MyName: msg"
            if (msg.startsWith(myName) || msg.startsWith("<" + myName) || msg.startsWith("[" + myName)) {
                return;
            }

            addNotification("Mentioned in Chat", Type.INFO, Items.PAPER.getDefaultStack());
        }
        // --- MENTION FIX END ---
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (pearlEnabled.get() && event.entity instanceof EnderPearlEntity pearl) {
            if (pearl.getOwner() instanceof PlayerEntity p && !p.equals(mc.player) && !Friends.get().isFriend(p)) {
                addNotification("Pearl: " + p.getName().getString(), Type.INFO, Items.ENDER_PEARL.getDefaultStack());
            }
        }
    }

    // ================= LOGIC HELPERS =================

    private void updateVisualRange() {
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p.equals(mc.player) || Friends.get().isFriend(p)) continue;
            if (!knownPlayers.containsKey(p.getUuid())) {
                addNotification("Spotted: " + p.getName().getString(), Type.WARNING, Items.SPYGLASS.getDefaultStack());
                knownPlayers.put(p.getUuid(), p.getName().getString());
            }
        }
    }

    private void updateBurrow() {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.equals(mc.player)) continue;
            BlockPos pos = p.getBlockPos();
            Block b = mc.world.getBlockState(pos).getBlock();
            boolean trap = (b == Blocks.OBSIDIAN || b == Blocks.BEDROCK || b == Blocks.ENDER_CHEST);
            if (trap) {
                if (!burrowedPlayers.contains(p.getUuid())) {
                    addNotification("Burrow: " + p.getName().getString(), Type.WARNING, new ItemStack(b));
                    burrowedPlayers.add(p.getUuid());
                }
            } else burrowedPlayers.remove(p.getUuid());
        }
    }

    public void addNotification(String text, Type type, ItemStack icon) {
        // Chat Logger
        if (logChat.get() && mc.player != null) {
            Formatting fmt = switch (type) {
                case WARNING -> Formatting.GOLD;
                case DANGER -> Formatting.RED;
                default -> Formatting.AQUA;
            };
            ChatUtils.sendMsg(Text.literal("[Kooda] " + text).formatted(fmt));
        }

        // Stacking Logic with Pulse
        if (!notifications.isEmpty()) {
            Notification last = notifications.get(0);
            if (last.text.equals(text) && last.type == type) {
                last.stack++;
                last.startTime = System.currentTimeMillis();
                last.pulseTimer = 1.0f; // Reset Pulse
                playSound(last.stack);
                return;
            }
        }
        notifications.add(0, new Notification(text, type, icon));
        playSound(1);
    }

    private void playSound(int stack) {
        if (!sounds.get()) return;
        float pitch = pitchScaling.get() ? MathHelper.clamp(1.0f + (stack - 1) * 0.1f, 0.5f, 2.0f) : 1.0f;
        if (customSound.get() && cachedSoundFile.exists()) {
            soundExecutor.submit(() -> {
                try {
                    AudioInputStream ais = AudioSystem.getAudioInputStream(cachedSoundFile);
                    Clip clip = AudioSystem.getClip();
                    clip.open(ais);
                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
                        ((FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN)).setValue(-5.0f);
                    clip.start();
                } catch (Exception e) {}
            });
        } else {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_IN, pitch, 1.0f));
        }
    }

    // ================= RENDER ENGINE =================

    @Override
    public void render(HudRenderer r) {
        if (isInEditor()) {
            Notification preview = new Notification("Kooda Preview", Type.WARNING, Items.NETHER_STAR.getDefaultStack());
            double s = scale.get();
            double w = measureWidth(r, preview, s);
            double h = (r.textHeight() + 10) * s;
            setSize(w, h);
            renderInternal(r, preview, x, y, s);
            return;
        }

        if (notifications.isEmpty()) { setSize(0, 0); return; }

        double s = scale.get();
        double h = (r.textHeight() + 10) * s;
        double gap = 2 * s;
        double maxW = 0;
        double totalH = 0;

        List<Notification> renderList = new ArrayList<>(notifications);
        for (Notification n : renderList) {
            // Update Pulse
            if (n.pulseTimer > 0) n.pulseTimer = Math.max(0, n.pulseTimer - (r.delta * 5));

            long elapsed = System.currentTimeMillis() - n.startTime;
            double life = displayTime.get() * 1000;
            if (elapsed > life) n.animProgress = MathHelper.clamp(1.0 - ((elapsed - life) / 500.0), 0, 1);
            else n.animProgress = MathHelper.clamp(elapsed / 300.0, 0, 1);

            n.ySmooth = MathHelper.lerp(0.2, n.ySmooth, totalH);
            double w = measureWidth(r, n, s);
            maxW = Math.max(maxW, w);
            double animScale = (animType.get() == Animation.Scale) ? n.animProgress : 1.0;
            totalH += (h + gap) * animScale;
        }
        setSize(maxW, Math.max(totalH, 10));

        boolean alignRight = getX() + (getWidth() / 2) > mc.getWindow().getScaledWidth() / 2;
        for (Notification n : renderList) {
            if (n.animProgress <= 0.05) continue;
            double w = measureWidth(r, n, s);
            double xPos = alignRight ? (x + maxW - w) : x;
            double xOff = 0;
            if (animType.get() == Animation.Slide) {
                double dist = w + 20;
                xOff = alignRight ? dist * (1 - n.animProgress) : -dist * (1 - n.animProgress);
            }
            renderInternal(r, n, xPos + xOff, y + n.ySmooth, s);
        }
    }

    // --- COLOR LOGIC (FIXED) ---
    private Color getColorFor(Notification n) {
        if (chroma.get()) {
            // FIXED: Manual Rainbow generation using Java AWT
            float hue = (System.currentTimeMillis() % 2000) / 2000f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 1, 1);
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
        }
        return n.type.getColor(this);
    }

    // --- HELPER: Manual Brighter Logic (FIXED) ---
    private Color makeBrighter(Color c) {
        int r = Math.min(255, (int)(c.r * 1.3));
        int g = Math.min(255, (int)(c.g * 1.3));
        int b = Math.min(255, (int)(c.b * 1.3));
        return new Color(r, g, b, c.a);
    }

    private double measureWidth(HudRenderer r, Notification n, double s) {
        String t = getFormattedText(n);
        double txtW = r.textWidth(t) * s;
        double icon = 16 * s;
        return switch (style.get()) {
            case Kooda -> (5*s) + icon + (5*s) + txtW + (5*s);
            case CSGO -> (6*s) + icon + (6*s) + txtW + (10*s);
            case Cyber, Future -> (12*s) + txtW + (12*s);
            case Minimal -> (10*s) + txtW + (10*s);
            case Bubble -> (6*s) + icon + (6*s) + txtW + (6*s);
            case Retro -> (8*s) + txtW + (8*s);
        };
    }

    private String getFormattedText(Notification n) {
        String t = n.text;
        int c = n.stack;
        return switch (style.get()) {
            case Cyber, Future -> (t + (c > 1 ? " [" + c + "]" : "")).toUpperCase();
            case Retro -> "> " + t + (c > 1 ? " (" + c + ")" : "");
            default -> t + (c > 1 ? " x" + c : "");
        };
    }

    private void renderInternal(HudRenderer r, Notification n, double x, double y, double s) {
        double pct;
        if (isInEditor()) { pct = 1.0; n.animProgress = 1.0; }
        else {
            long elapsed = System.currentTimeMillis() - n.startTime;
            pct = MathHelper.clamp(1.0 - (double)elapsed / (displayTime.get() * 1000), 0, 1);
        }

        switch (style.get()) {
            case CSGO -> renderCSGO(r, n, x, y, s, n.animProgress, pct);
            case Cyber -> renderCyber(r, n, x, y, s, n.animProgress, pct);
            case Minimal -> renderMinimal(r, n, x, y, s, n.animProgress, pct);
            case Future -> renderFuture(r, n, x, y, s, n.animProgress, pct);
            case Bubble -> renderBubble(r, n, x, y, s, n.animProgress, pct);
            case Retro -> renderRetro(r, n, x, y, s, n.animProgress, pct);
            default -> renderKooda(r, n, x, y, s, n.animProgress, pct);
        }
    }

    // --- RENDERERS (FIXED with makeBrighter) ---

    private void renderKooda(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        double w = measureWidth(r, n, s);
        double h = (r.textHeight() + 8) * s;
        Color bg = colorBg.get().copy(); bg.a = (int)(bg.a * a);
        Color accent = getColorFor(n); accent.a = (int)(255 * a);
        Color txt = colorText.get().copy(); txt.a = (int)(255 * a);

        if (n.pulseTimer > 0) bg = makeBrighter(bg);

        r.quad(x, y, w, h, bg);
        r.quad(x, y, w, 2*s, accent);
        r.item(n.icon, (int)(x + 5*s), (int)(y + (h - 16*s)/2), (float)s, true);
        r.text(getFormattedText(n), x + 21*s + 5*s, y + h/2 - r.textHeight()*s/2, txt, true, s);
        if (timeBar.get()) r.quad(x, y + h - 2*s, w * pct, 2*s, accent);
    }

    private void renderCSGO(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        double w = measureWidth(r, n, s);
        double h = (r.textHeight() + 10) * s;
        Color bg = new Color(0,0,0, (int)(180*a));
        Color border = getColorFor(n); border.a = (int)(255*a);
        if (n.pulseTimer > 0) border = makeBrighter(border);

        r.quad(x, y, w, h, bg);
        r.quad(x, y, 3*s, h, border);
        r.item(n.icon, (int)(x + 8*s), (int)(y + (h - 16*s)/2), (float)s, true);
        r.text(getFormattedText(n), x + 28*s, y + h/2 - r.textHeight()*s/2, colorText.get(), true, s);
        if (timeBar.get()) r.quad(x + 3*s, y + h - 1.5*s, (w - 3*s) * pct, 1.5*s, border);
    }

    private void renderCyber(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        double w = measureWidth(r, n, s);
        double h = (r.textHeight() + 8) * s;
        Color accent = getColorFor(n); accent.a = (int)(255*a);
        Color bg = new Color(0,0,0, (int)(120*a));
        r.quad(x, y, 2*s, h, accent);
        r.quad(x+w-2*s, y, 2*s, h, accent);
        r.quad(x+2*s, y, w-4*s, h, bg);
        r.text(getFormattedText(n), x + (w - r.textWidth(getFormattedText(n))*s)/2, y + h/2 - r.textHeight()*s/2, accent, true, s);
        if (timeBar.get()) r.quad(x+2*s, y+h-2*s, (w-4*s)*pct, 1*s, accent);
    }

    private void renderMinimal(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        double w = measureWidth(r, n, s);
        double h = (r.textHeight() + 6) * s;
        Color bg = new Color(245, 245, 245, (int)(240 * a));
        Color txt = new Color(20, 20, 20, (int)(255 * a));
        r.quad(x, y, w, h, bg);
        r.text(getFormattedText(n), x + w/2 - (r.textWidth(getFormattedText(n))*s)/2, y + h/2 - r.textHeight()*s/2, txt, false, s);
        Color bar = getColorFor(n); bar.a = (int)(255*a);
        if (timeBar.get()) r.quad(x, y+h-2*s, w*pct, 2*s, bar);
    }

    private void renderFuture(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        double w = measureWidth(r, n, s);
        double h = (r.textHeight() + 10) * s;
        Color c = getColorFor(n); c.a = (int)(255*a);
        Color bg = new Color(0,0,0, (int)(150*a));
        r.quad(x, y, w, h, bg);
        r.quad(x, y, 1*s, h, c);
        r.quad(x+w-1*s, y, 1*s, h, c);
        r.quad(x, y, w, 1*s, c);
        r.quad(x, y+h-1*s, w, 1*s, c);
        r.text(getFormattedText(n), x + w/2 - r.textWidth(getFormattedText(n))*s/2, y + h/2 - r.textHeight()*s/2, c, true, s);
    }

    private void renderBubble(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        double w = measureWidth(r, n, s);
        double h = (r.textHeight() + 8) * s;
        Color bg = getColorFor(n); bg.a = (int)(200*a);
        r.quad(x + 2*s, y, w - 4*s, h, bg);
        r.quad(x, y + 2*s, 2*s, h - 4*s, bg);
        r.quad(x + w - 2*s, y + 2*s, 2*s, h - 4*s, bg);
        r.item(n.icon, (int)(x + 5*s), (int)(y + (h - 16*s)/2), (float)s, true);
        r.text(getFormattedText(n), x + 25*s, y + h/2 - r.textHeight()*s/2, new Color(255,255,255, (int)(255*a)), true, s);
    }

    private void renderRetro(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        double w = measureWidth(r, n, s);
        double h = (r.textHeight() + 6) * s;
        Color c = getColorFor(n); c.a = (int)(255*a);
        r.quad(x, y, w, h, new Color(0,0,0, (int)(240*a)));
        r.text(getFormattedText(n), x + 4*s, y + h/2 - r.textHeight()*s/2, c, true, s);
        if (timeBar.get()) r.quad(x, y+h-1*s, w*pct, 1*s, c);
    }

    private static class Notification {
        String text; Type type; ItemStack icon;
        long startTime; int stack = 1;
        double animProgress = 0; double ySmooth = 0;
        double pulseTimer = 0;
        public Notification(String t, Type y, ItemStack i) {
            text = t; type = y; icon = i; startTime = System.currentTimeMillis();
        }
    }

    public enum Type {
        INFO, WARNING, DANGER;
        public Color getColor(KoodaNotifierHud h) {
            return switch(this) {
                case INFO -> h.colorInfo.get();
                case WARNING -> h.colorWarn.get();
                case DANGER -> h.colorDanger.get();
            };
        }
    }

    public enum Style { Kooda, CSGO, Cyber, Minimal, Future, Bubble, Retro }
    public enum Animation { Slide, Fade, Scale }
}