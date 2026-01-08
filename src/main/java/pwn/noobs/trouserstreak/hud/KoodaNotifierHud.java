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
            "Advanced notification system with compact styles.",
            KoodaNotifierHud::new
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTriggers = settings.createGroup("Triggers");
    private final SettingGroup sgVisual = settings.createGroup("Style & Visuals");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale").description("Global scale.").defaultValue(1.0).min(0.5).sliderMax(2.0).build());
    private final Setting<Double> displayTime = sgGeneral.add(new DoubleSetting.Builder()
            .name("duration").description("Seconds on screen.").defaultValue(3.0).min(1.0).max(10.0).build());
    private final Setting<Integer> maxNotifs = sgGeneral.add(new IntSetting.Builder()
            .name("max-visible").defaultValue(6).min(1).build());
    private final Setting<Boolean> logChat = sgGeneral.add(new BoolSetting.Builder()
            .name("log-to-chat").defaultValue(false).build());

    private final Setting<Boolean> sounds = sgGeneral.add(new BoolSetting.Builder()
            .name("sounds").defaultValue(true).build());
    private final Setting<Boolean> pitchScaling = sgGeneral.add(new BoolSetting.Builder()
            .name("pitch-scaling").defaultValue(true).visible(sounds::get).build());
    private final Setting<Boolean> customSound = sgGeneral.add(new BoolSetting.Builder()
            .name("custom-wav").defaultValue(false).visible(sounds::get).build());

    private final Setting<Boolean> visualRange = sgTriggers.add(new BoolSetting.Builder()
            .name("visual-range").defaultValue(true).build());
    private final Setting<Boolean> vrEnter = sgTriggers.add(new BoolSetting.Builder()
            .name("vr-enter").defaultValue(true).visible(visualRange::get).build());
    private final Setting<Boolean> vrLeave = sgTriggers.add(new BoolSetting.Builder()
            .name("vr-leave").defaultValue(true).visible(visualRange::get).build());

    private final Setting<Boolean> popEnabled = sgTriggers.add(new BoolSetting.Builder().name("totem-pops").defaultValue(true).build());
    private final Setting<Boolean> burrowEnabled = sgTriggers.add(new BoolSetting.Builder().name("burrow-detect").defaultValue(true).build());
    private final Setting<Boolean> pearlEnabled = sgTriggers.add(new BoolSetting.Builder().name("pearl-throws").defaultValue(true).build());
    private final Setting<Boolean> killFeed = sgTriggers.add(new BoolSetting.Builder().name("kill-feed").defaultValue(true).build());
    private final Setting<Boolean> mentionEnabled = sgTriggers.add(new BoolSetting.Builder().name("chat-mentions").defaultValue(true).build());

    private final Setting<Boolean> armorEnabled = sgTriggers.add(new BoolSetting.Builder().name("low-armor").defaultValue(true).build());
    private final Setting<Boolean> toolEnabled = sgTriggers.add(new BoolSetting.Builder().name("low-tool").defaultValue(true).build());
    private final Setting<Integer> durabilityThreshold = sgTriggers.add(new IntSetting.Builder()
            .name("durability-%").defaultValue(30).min(1).max(99).visible(() -> armorEnabled.get() || toolEnabled.get()).build());

    private final Setting<Boolean> lagEnabled = sgTriggers.add(new BoolSetting.Builder().name("server-lag").defaultValue(true).build());

    private final Setting<Style> style = sgVisual.add(new EnumSetting.Builder<Style>()
            .name("style").defaultValue(Style.Kooda).build());
    private final Setting<Animation> animType = sgVisual.add(new EnumSetting.Builder<Animation>()
            .name("animation").defaultValue(Animation.Slide).build());
    private final Setting<Boolean> timeBar = sgVisual.add(new BoolSetting.Builder()
            .name("time-bar").defaultValue(true).visible(() -> style.get() != Style.MinecraftToast).build());
    private final Setting<Boolean> compact = sgVisual.add(new BoolSetting.Builder()
            .name("compact-mode").defaultValue(true).build());
    private final Setting<Boolean> glowEffect = sgVisual.add(new BoolSetting.Builder()
            .name("glow-effect").defaultValue(true).visible(() -> style.get() == Style.Kooda).build());

    private final Setting<Boolean> chroma = sgColors.add(new BoolSetting.Builder()
            .name("chroma-mode").defaultValue(false).build());
    private final Setting<SettingColor> colorInfo = sgColors.add(new ColorSetting.Builder()
            .name("info-color").defaultValue(new SettingColor(0, 200, 255)).build());
    private final Setting<SettingColor> colorWarn = sgColors.add(new ColorSetting.Builder()
            .name("warn-color").defaultValue(new SettingColor(255, 170, 0)).build());
    private final Setting<SettingColor> colorDanger = sgColors.add(new ColorSetting.Builder()
            .name("danger-color").defaultValue(new SettingColor(255, 50, 50)).build());
    private final Setting<SettingColor> colorText = sgColors.add(new ColorSetting.Builder()
            .name("text-color").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<SettingColor> colorBg = sgColors.add(new ColorSetting.Builder()
            .name("background-color").defaultValue(new SettingColor(20, 20, 20, 200)).build());

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
    private final ExecutorService soundExecutor = Executors.newFixedThreadPool(2);

    public KoodaNotifierHud() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
        File soundDir = new File(new File(MeteorClient.FOLDER.getParentFile(), "KOODA"), "NotificatorHudSound");
        if (!soundDir.exists()) soundDir.mkdirs();
        cachedSoundFile = new File(soundDir, "notification.wav");
    }

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

        while (notifications.size() > maxNotifs.get()) {
            notifications.remove(notifications.size() - 1);
        }

        if (lagEnabled.get()) {
            long diff = now - lastPacketTime;
            if (diff > 3000 && !lagNotified) {
                addNotification("Lag Detected", "Server froze > 3s", Type.WARNING, Items.CLOCK.getDefaultStack());
                lagNotified = true;
            } else if (diff < 1000) {
                lagNotified = false;
            }
        }

        if (armorEnabled.get()) {
            boolean low = false;
            EquipmentSlot[] armorSlots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
            for (EquipmentSlot slot : armorSlots) {
                ItemStack s = mc.player.getEquippedStack(slot);
                if (!s.isEmpty() && s.isDamageable()) {
                    int pct = (int)(((double)(s.getMaxDamage() - s.getDamage()) / s.getMaxDamage()) * 100);
                    if (pct <= durabilityThreshold.get()) { low = true; break; }
                }
            }
            if (low && !armorNotified) {
                addNotification("Armor Critical", "Durability < " + durabilityThreshold.get() + "%", Type.DANGER, Items.NETHERITE_CHESTPLATE.getDefaultStack());
                armorNotified = true;
            } else if (!low) {
                armorNotified = false;
            }
        }

        if (toolEnabled.get()) {
            ItemStack main = mc.player.getMainHandStack();
            if (!main.isEmpty() && main.isDamageable()) {
                int pct = (int)(((double)(main.getMaxDamage() - main.getDamage()) / main.getMaxDamage()) * 100);
                if (pct <= durabilityThreshold.get()) {
                    if (!toolNotified) {
                        addNotification("Tool Critical", "Durability < " + durabilityThreshold.get() + "%", Type.DANGER, main.copy());
                        toolNotified = true;
                    }
                } else toolNotified = false;
            } else toolNotified = false;
        }

        if (visualRange.get()) updateVisualRange();
        if (burrowEnabled.get()) updateBurrow();
    }

    private void updateVisualRange() {
        if (mc.world == null) return;

        Set<UUID> loadedUUIDs = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p.equals(mc.player) || Friends.get().isFriend(p)) continue;

            loadedUUIDs.add(p.getUuid());

            if (!knownPlayers.containsKey(p.getUuid())) {
                if (vrEnter.get()) {
                    addNotification("Visual Range", "Entered: " + p.getName().getString(), Type.WARNING, Items.SPYGLASS.getDefaultStack());
                }
                knownPlayers.put(p.getUuid(), p.getName().getString());
            }
        }

        Iterator<Map.Entry<UUID, String>> it = knownPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            UUID id = entry.getKey();
            String name = entry.getValue();

            if (!loadedUUIDs.contains(id)) {
                if (vrLeave.get()) {
                    addNotification("Visual Range", "Left: " + name, Type.INFO, Items.ENDER_EYE.getDefaultStack());
                }
                it.remove();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null) return;
        lastPacketTime = System.currentTimeMillis();

        if (event.packet instanceof EntityStatusS2CPacket p) {
            Entity e = p.getEntity(mc.world);

            if (popEnabled.get() && p.getStatus() == 35 && e instanceof PlayerEntity player && !player.equals(mc.player)) {
                int c = popCounts.getOrDefault(player.getUuid(), 0) + 1;
                popCounts.put(player.getUuid(), c);
                addNotification("Totem Pop", player.getName().getString() + " popped " + c, Type.WARNING, Items.TOTEM_OF_UNDYING.getDefaultStack());
            }

            if (killFeed.get() && p.getStatus() == 3 && e instanceof PlayerEntity player) {
                if (player.equals(mc.player)) {
                    addNotification("You Died", "Press F to pay respects", Type.DANGER, Items.SKELETON_SKULL.getDefaultStack());
                    popCounts.remove(mc.player.getUuid());
                } else if (lastTarget != null && lastTarget.equals(player) && System.currentTimeMillis() - lastAttackTime < 5000) {
                    addNotification("Kill", "Eliminated " + player.getName().getString(), Type.INFO, Items.DIAMOND_SWORD.getDefaultStack());
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
        if (!mentionEnabled.get() || mc.player == null || mc.currentScreen instanceof ChatScreen) return;

        String msg = event.getMessage().getString();
        String myName = mc.player.getName().getString();

        if (myName.isEmpty()) return;

        if (msg.toLowerCase().contains(myName.toLowerCase())) {
            if (msg.startsWith(myName) || msg.startsWith("<" + myName) || msg.startsWith("[" + myName)) return;
            addNotification("Mention", "Mentioned in chat", Type.INFO, Items.PAPER.getDefaultStack());
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (pearlEnabled.get() && event.entity instanceof EnderPearlEntity pearl) {
            if (pearl.getOwner() instanceof PlayerEntity p && !p.equals(mc.player) && !Friends.get().isFriend(p)) {
                addNotification("Pearl", "By " + p.getName().getString(), Type.INFO, Items.ENDER_PEARL.getDefaultStack());
            }
        }
    }

    private void updateBurrow() {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.equals(mc.player)) continue;
            BlockPos pos = p.getBlockPos();
            Block b = mc.world.getBlockState(pos).getBlock();

            if (b == Blocks.OBSIDIAN || b == Blocks.BEDROCK || b == Blocks.ENDER_CHEST || b == Blocks.ANVIL) {
                if (!burrowedPlayers.contains(p.getUuid())) {
                    addNotification("Burrow", p.getName().getString() + " is burrowed", Type.WARNING, new ItemStack(b));
                    burrowedPlayers.add(p.getUuid());
                }
            } else {
                burrowedPlayers.remove(p.getUuid());
            }
        }
    }

    public void addNotification(String title, String text, Type type, ItemStack icon) {
        if (logChat.get() && mc.player != null) {
            Formatting fmt = switch (type) {
                case WARNING -> Formatting.GOLD;
                case DANGER -> Formatting.RED;
                default -> Formatting.AQUA;
            };
            ChatUtils.sendMsg(Text.literal("[Kooda] " + title + ": " + text).formatted(fmt));
        }

        if (!notifications.isEmpty()) {
            Notification last = notifications.get(0);
            if (last.text.equals(text) && last.title.equals(title) && last.type == type) {
                last.stack++;
                last.startTime = System.currentTimeMillis();
                last.pulseTimer = 1.0f;
                playSound(last.stack);
                return;
            }
        }

        notifications.add(0, new Notification(title, text, type, icon));
        playSound(1);
    }

    public void addNotification(String text, Type type, ItemStack icon) {
        addNotification("Alert", text, type, icon);
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } else {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_IN, pitch, 1.0f));
        }
    }

    @Override
    public void render(HudRenderer r) {
        if (isInEditor()) {
            Notification preview = new Notification("Preview", "Kooda Notifier", Type.WARNING, Items.NETHER_STAR.getDefaultStack());
            double s = scale.get();
            double w = measureWidth(r, preview, s);
            double baseH = getBoxHeight(r, s);
            setSize(w, baseH);
            renderInternal(r, preview, x, y, s);
            return;
        }

        if (notifications.isEmpty()) { setSize(0, 0); return; }

        double s = scale.get();
        double baseH = getBoxHeight(r, s);
        double gap = compact.get() ? 1 * s : 2 * s;
        double maxW = 0;
        double totalH = 0;

        List<Notification> renderList = new ArrayList<>(notifications);
        for (Notification n : renderList) {
            if (n.pulseTimer > 0) n.pulseTimer = Math.max(0, n.pulseTimer - (r.delta * 5));

            long elapsed = System.currentTimeMillis() - n.startTime;
            double life = displayTime.get() * 1000;

            if (elapsed > life) n.animProgress = MathHelper.clamp(1.0 - ((elapsed - life) / 500.0), 0, 1);
            else n.animProgress = MathHelper.clamp(elapsed / 300.0, 0, 1);

            if (n.animProgress <= 0.05) continue;

            n.ySmooth = MathHelper.lerp(0.2, n.ySmooth, totalH);

            double w = measureWidth(r, n, s);
            maxW = Math.max(maxW, w);

            double animScale = (animType.get() == Animation.Scale) ? n.animProgress : 1.0;
            totalH += (baseH + gap) * animScale;
        }
        setSize(maxW, Math.max(totalH, 10));

        boolean alignRight = getX() + (getWidth() / 2) > mc.getWindow().getScaledWidth() / 2;

        for (Notification n : renderList) {
            if (n.animProgress <= 0.05) continue;

            double w = measureWidth(r, n, s);
            double xPos = alignRight ? (x + maxW - w) : x;
            double xOff = 0;

            if (animType.get() == Animation.Slide) {
                double dist = w + 10;
                xOff = alignRight ? dist * (1 - n.animProgress) : -dist * (1 - n.animProgress);
            }

            renderInternal(r, n, xPos + xOff, y + n.ySmooth, s);
        }
    }

    private double getBoxHeight(HudRenderer r, double s) {
        if (style.get() == Style.MinecraftToast) return 26 * s;
        double padding = compact.get() ? 2 * s : 4 * s;
        return Math.max(16 * s, r.textHeight() * s) + (padding * 2);
    }

    private double measureWidth(HudRenderer r, Notification n, double s) {
        String fullText = getRenderString(n);
        double txtW = r.textWidth(fullText) * s;
        double icon = 16 * s;
        double pad = compact.get() ? 3 * s : 5 * s;

        if (style.get() == Style.MinecraftToast) {
            double titleW = r.textWidth(n.title) * s;
            double bodyW = r.textWidth(n.text + (n.stack > 1 ? " x" + n.stack : "")) * s;
            return (8 * s) + icon + (5 * s) + Math.max(titleW, bodyW) + (8 * s);
        }

        return switch (style.get()) {
            case Kooda, CSGO, Bubble -> (pad) + icon + (4 * s) + txtW + (pad);
            case Cyber -> (pad * 2) + txtW + (pad * 2);
            case Minimal -> (6 * s) + txtW + (6 * s);
            case Future -> (8 * s) + txtW + (8 * s);
            case Retro -> (6 * s) + txtW + (6 * s);
            default -> (pad) + icon + (4 * s) + txtW + (pad);
        };
    }

    private String getRenderString(Notification n) {
        String stack = n.stack > 1 ? " x" + n.stack : "";
        return switch (style.get()) {
            case Cyber -> (n.title + " " + n.text + stack).toUpperCase();
            case Retro -> "> " + n.title + ": " + n.text + stack;
            case Minimal -> n.text + stack;
            default -> n.title + ": " + n.text + stack;
        };
    }

    private Color getColorFor(Notification n) {
        if (chroma.get()) {
            float hue = (System.currentTimeMillis() % 3000) / 3000f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 1, 1);
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
        }
        return n.type.getColor(this);
    }

    private Color makeBrighter(Color c) {
        return new Color(Math.min(255, (int)(c.r * 1.3)), Math.min(255, (int)(c.g * 1.3)), Math.min(255, (int)(c.b * 1.3)), c.a);
    }

    private void renderInternal(HudRenderer r, Notification n, double x, double y, double s) {
        double pct = MathHelper.clamp(1.0 - (double)(System.currentTimeMillis() - n.startTime) / (displayTime.get() * 1000), 0, 1);
        if (isInEditor()) { n.animProgress = 1; pct = 1; }

        int alpha = (int)(255 * n.animProgress);

        if (style.get() == Style.MinecraftToast) {
            renderToast(r, n, x, y, s, alpha);
        } else {
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
    }

    private void renderToast(HudRenderer r, Notification n, double x, double y, double s, int alpha) {
        double w = measureWidth(r, n, s);
        double h = 26 * s;

        Color bg = new Color(252, 252, 252, alpha);
        Color borderLight = new Color(220, 220, 220, alpha);
        Color borderDark = new Color(50, 50, 50, alpha);

        r.quad(x, y, w, h, bg);
        r.quad(x, y, w, 1*s, borderLight);
        r.quad(x, y+h-1*s, w, 1*s, borderDark);
        r.quad(x, y, 1*s, h, borderLight);
        r.quad(x+w-1*s, y, 1*s, h, borderDark);

        r.item(n.icon, (int)(x + 6*s), (int)(y + 5*s), (float)(s * 0.9), true);

        Color titleColor = (n.type == Type.DANGER) ? new Color(255, 80, 80, alpha) : new Color(20, 20, 20, alpha);
        Color bodyColor = new Color(80, 80, 80, alpha);

        r.text(n.title, x + 26*s, y + 3*s, titleColor, false, s * 0.9);
        r.text(n.text + (n.stack > 1 ? " x" + n.stack : ""), x + 26*s, y + 13*s, bodyColor, false, s * 0.9);
    }

    private void renderKooda(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        int alpha = (int)(255 * a);
        double w = measureWidth(r, n, s);
        double h = getBoxHeight(r, s);

        Color bgTop = colorBg.get().copy(); bgTop.a = (int)(bgTop.a * a);
        Color bgBot = new Color(bgTop.r, bgTop.g, bgTop.b, Math.max(0, bgTop.a - 20));
        Color accent = getColorFor(n); accent.a = alpha;
        Color txt = colorText.get().copy(); txt.a = alpha;

        if (n.pulseTimer > 0) bgTop = makeBrighter(bgTop);

        if (glowEffect.get()) {
            Color glow = accent.copy(); glow.a = (int)(50 * a);
            r.quad(x - 2*s, y - 2*s, w + 4*s, h + 4*s, glow);
        }

        r.quad(x, y, w, h, bgTop, bgTop, bgBot, bgBot);
        r.quad(x, y, 2*s, h, accent);

        double iconSize = 16 * s;
        double iconY = y + (h - iconSize) / 2;
        r.item(n.icon, (int)(x + 5*s), (int)iconY, (float)s, true);

        double textY = y + (h / 2) - (r.textHeight() * s / 2);
        r.text(getRenderString(n), x + iconSize + 8*s, textY, txt, true, s);

        if (timeBar.get()) r.quad(x + 2*s, y + h - 1.5*s, (w - 2*s) * pct, 1.5*s, accent);
    }

    private void renderCSGO(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        int alpha = (int)(255 * a);
        double w = measureWidth(r, n, s);
        double h = getBoxHeight(r, s);

        Color bgLeft = new Color(10, 10, 10, (int)(180 * a));
        Color bgRight = new Color(10, 10, 10, (int)(20 * a));

        r.quad(x, y, w, h, bgLeft, bgRight, bgRight, bgLeft);
        Color border = getColorFor(n); border.a = alpha;
        if (n.pulseTimer > 0) border = makeBrighter(border);

        r.quad(x, y, 1.5*s, h, border);

        double iconSize = 16 * s;
        r.item(n.icon, (int)(x + 4*s), (int)(y + (h - iconSize)/2), (float)s, true);

        Color tc = colorText.get().copy(); tc.a = alpha;
        r.text(getRenderString(n), x + iconSize + 8*s, y + h/2 - r.textHeight()*s/2, tc, true, s);

        if (timeBar.get()) r.quad(x + 1.5*s, y + h - 1*s, (w - 1.5*s) * pct, 1*s, border);
    }

    private void renderCyber(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        int alpha = (int)(255 * a);
        double w = measureWidth(r, n, s);
        double h = getBoxHeight(r, s);

        Color accent = getColorFor(n); accent.a = alpha;
        Color bg = new Color(0,0,0, (int)(100 * a));

        r.quad(x, y, 1*s, h, accent);
        r.quad(x+w-1*s, y, 1*s, h, accent);
        r.quad(x+1*s, y, w-2*s, h, bg);

        String t = getRenderString(n);
        r.text(t, x + (w - r.textWidth(t)*s)/2, y + h/2 - r.textHeight()*s/2, accent, true, s);
        if (timeBar.get()) r.quad(x+1*s, y+h-1*s, (w-2*s)*pct, 1*s, accent);
    }

    private void renderMinimal(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        int alpha = (int)(255 * a);
        double w = measureWidth(r, n, s);
        double h = getBoxHeight(r, s);

        Color bg = new Color(20, 20, 20, (int)(150 * a));
        r.quad(x, y, w, h, bg);

        String t = getRenderString(n);
        r.text(t, x + w/2 - (r.textWidth(t)*s)/2, y + h/2 - r.textHeight()*s/2, colorText.get(), true, s);

        Color bar = getColorFor(n); bar.a = alpha;
        if (timeBar.get()) r.quad(x, y+h-1.5*s, w*pct, 1.5*s, bar);
    }

    private void renderFuture(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        int alpha = (int)(255 * a);
        double w = measureWidth(r, n, s);
        double h = getBoxHeight(r, s);

        Color c = getColorFor(n); c.a = alpha;
        Color bg = new Color(0,0,0, (int)(150 * a));

        r.quad(x, y, w, h, bg);
        r.quad(x, y, 2*s, 2*s, c);
        r.quad(x+w-2*s, y, 2*s, 2*s, c);
        r.quad(x, y+h-2*s, 2*s, 2*s, c);
        r.quad(x+w-2*s, y+h-2*s, 2*s, 2*s, c);

        String t = getRenderString(n);
        r.text(t, x + w/2 - r.textWidth(t)*s/2, y + h/2 - r.textHeight()*s/2, c, true, s);
    }

    private void renderBubble(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        int alpha = (int)(255 * a);
        double w = measureWidth(r, n, s);
        double h = getBoxHeight(r, s);

        Color bg = getColorFor(n); bg.a = (int)(180 * a);

        r.quad(x + 2*s, y, w - 4*s, h, bg);
        r.quad(x, y + 2*s, 2*s, h - 4*s, bg);
        r.quad(x + w - 2*s, y + 2*s, 2*s, h - 4*s, bg);

        r.item(n.icon, (int)(x + 4*s), (int)(y + (h - 16*s)/2), (float)s, true);

        String t = getRenderString(n);
        r.text(t, x + 24*s, y + h/2 - r.textHeight()*s/2, new Color(255,255,255, alpha), true, s);
    }

    private void renderRetro(HudRenderer r, Notification n, double x, double y, double s, double a, double pct) {
        int alpha = (int)(255 * a);
        double w = measureWidth(r, n, s);
        double h = getBoxHeight(r, s);

        Color c = getColorFor(n); c.a = alpha;
        r.quad(x, y, w, h, new Color(0,0,0, (int)(240 * a)));
        r.quad(x, y, w, 1*s, c);

        String t = getRenderString(n);
        r.text(t, x + 4*s, y + h/2 - r.textHeight()*s/2, c, true, s);
    }

    private static class Notification {
        String title; String text; Type type; ItemStack icon;
        long startTime; int stack = 1;
        double animProgress = 0; double ySmooth = 0;
        double pulseTimer = 0;
        public Notification(String title, String text, Type type, ItemStack icon) {
            this.title = title; this.text = text; this.type = type; this.icon = icon; this.startTime = System.currentTimeMillis();
        }
    }

    public enum Type {
        INFO, WARNING, DANGER;
        public Color getColor(KoodaNotifierHud h) {
            return switch(this) {
                case INFO -> h.colorInfo.get();
                case WARNING -> h.colorWarn.get();
                case DANGER -> h.colorDanger.get();
                default -> h.colorInfo.get();
            };
        }
    }

    public enum Style { Kooda, MinecraftToast, CSGO, Cyber, Minimal, Future, Bubble, Retro }
    public enum Animation { Slide, Fade, Scale }
}