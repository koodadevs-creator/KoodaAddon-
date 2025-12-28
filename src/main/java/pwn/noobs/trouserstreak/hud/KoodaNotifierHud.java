package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvents;
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
            "kooda-notifier",
            "Kooda Notifier",
            "Mega Robust system with Custom Sounds (.wav) and 8 Visual Styles.",
            KoodaNotifierHud::new
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgVisual = settings.createGroup("Visual Range");
    private final SettingGroup sgCombat = settings.createGroup("Combat");
    private final SettingGroup sgMisc = settings.createGroup("Misc");
    private final SettingGroup sgStyle = settings.createGroup("Style");

    // --- GENERAL SETTINGS ---
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale").description("Notification scale.").defaultValue(1.0).min(0.5).max(3.0).sliderMax(3.0).build());
    private final Setting<Double> displayTime = sgGeneral.add(new DoubleSetting.Builder()
            .name("duration").description("Time on screen (seconds).").defaultValue(3.0).min(1.0).max(10.0).build());
    private final Setting<Boolean> sounds = sgGeneral.add(new BoolSetting.Builder()
            .name("sounds").description("Play a sound when a notification arrives.").defaultValue(true).build());

    // Custom Sound Toggle
    private final Setting<Boolean> customSound = sgGeneral.add(new BoolSetting.Builder()
            .name("custom-sound")
            .description("Plays 'notification.wav' from .minecraft/KOODA/NotificatorHudSound/")
            .defaultValue(false)
            .visible(sounds::get)
            .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Background color for Standard style.")
            .defaultValue(new SettingColor(20, 20, 20, 150))
            .build()
    );

    // --- VISUAL RANGE ---
    private final Setting<Boolean> vrEnabled = sgVisual.add(new BoolSetting.Builder()
            .name("enable-visual-range").description("Notify entering/leaving.").defaultValue(true).build());
    private final Setting<Boolean> vrEnter = sgVisual.add(new BoolSetting.Builder()
            .name("notify-enter").description("Notify when players enter.").defaultValue(true).visible(vrEnabled::get).build());
    private final Setting<Boolean> vrLeave = sgVisual.add(new BoolSetting.Builder()
            .name("notify-leave").description("Notify when players leave.").defaultValue(true).visible(vrEnabled::get).build());
    private final Setting<Boolean> ignoreFriends = sgVisual.add(new BoolSetting.Builder()
            .name("ignore-friends").defaultValue(true).visible(vrEnabled::get).build());

    // --- COMBAT ---
    private final Setting<Boolean> popEnabled = sgCombat.add(new BoolSetting.Builder()
            .name("totem-pops").description("Notify when someone pops.").defaultValue(true).build());
    private final Setting<Boolean> burrowEnabled = sgCombat.add(new BoolSetting.Builder()
            .name("burrow-detect").description("Notify when someone is burrowed.").defaultValue(true).build());
    private final Setting<Boolean> pearlEnabled = sgCombat.add(new BoolSetting.Builder()
            .name("pearl-throws").description("Notify pearl throws.").defaultValue(true).build());
    private final Setting<Boolean> killFeed = sgCombat.add(new BoolSetting.Builder()
            .name("kill-feed").description("Notify kills and deaths.").defaultValue(true).build());

    // --- MISC ---
    private final Setting<Boolean> mentionEnabled = sgMisc.add(new BoolSetting.Builder()
            .name("chat-mentions").description("Notify when your name is mentioned.").defaultValue(true).build());

    // --- STYLE SETTINGS ---
    private final Setting<Style> style = sgStyle.add(new EnumSetting.Builder<Style>()
            .name("style").description("Visual style of the notifications.").defaultValue(Style.Kooda).build());

    private final Setting<SettingColor> colorInfo = sgStyle.add(new ColorSetting.Builder()
            .name("info-color").description("Color for general info.").defaultValue(new SettingColor(100, 200, 255)).build());
    private final Setting<SettingColor> colorWarn = sgStyle.add(new ColorSetting.Builder()
            .name("warn-color").description("Color for warnings (Pops/Burrow).").defaultValue(new SettingColor(255, 170, 0)).build());
    private final Setting<SettingColor> colorDanger = sgStyle.add(new ColorSetting.Builder()
            .name("danger-color").description("Color for danger (Death/Kill).").defaultValue(new SettingColor(255, 50, 50)).build());
    private final Setting<SettingColor> colorText = sgStyle.add(new ColorSetting.Builder()
            .name("text-color").defaultValue(new SettingColor(255, 255, 255)).build());

    // --- INTERNAL STATE (Thread Safe) ---
    private final List<Notification> notifications = new CopyOnWriteArrayList<>();
    private final Map<UUID, String> knownPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> popCounts = new ConcurrentHashMap<>();
    private final Set<UUID> burrowedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private PlayerEntity lastTarget = null;
    private long lastAttackTime = 0;

    // --- SOUND SYSTEM ---
    private final File cachedSoundFile;
    private final ExecutorService soundExecutor = Executors.newSingleThreadExecutor();

    public KoodaNotifierHud() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);

        File soundDir = new File(new File(MeteorClient.FOLDER.getParentFile(), "KOODA"), "NotificatorHudSound");
        if (!soundDir.exists() && !soundDir.mkdirs()) {
            KoodaAddon.LOG.warn("Failed to create Kooda sound directory.");
        }
        cachedSoundFile = new File(soundDir, "notification.wav");
    }

    // ================= EVENTS =================

    @EventHandler
    @SuppressWarnings("unused")
    private void onGameLeave(GameLeftEvent event) {
        knownPlayers.clear();
        popCounts.clear();
        burrowedPlayers.clear();
        notifications.clear();
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof PlayerEntity player) {
            lastTarget = player;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof EntityStatusS2CPacket packet) {
            Entity entity = packet.getEntity(mc.world);

            // Totem Pop (Status 35)
            if (popEnabled.get() && packet.getStatus() == 35) {
                if (entity instanceof PlayerEntity player) {
                    if (player.equals(mc.player)) return;
                    UUID uuid = player.getUuid();
                    int count = popCounts.getOrDefault(uuid, 0) + 1;
                    popCounts.put(uuid, count);
                    addNotification(player.getName().getString() + " popped " + count + "!", Type.WARNING, Items.TOTEM_OF_UNDYING.getDefaultStack());
                }
            }

            // Death (Status 3)
            if (killFeed.get() && packet.getStatus() == 3) {
                if (entity instanceof PlayerEntity player) {
                    if (player.equals(mc.player)) {
                        addNotification("You died.", Type.DANGER, Items.SKELETON_SKULL.getDefaultStack());
                        popCounts.remove(mc.player.getUuid());
                    }
                    else if (lastTarget != null && player.equals(lastTarget) && System.currentTimeMillis() - lastAttackTime < 5000) {
                        ItemStack weapon = mc.player.getMainHandStack().isEmpty() ? Items.NETHERITE_SWORD.getDefaultStack() : mc.player.getMainHandStack();
                        addNotification("Killed " + player.getName().getString(), Type.DANGER, weapon);
                        popCounts.remove(player.getUuid());
                        lastTarget = null;
                    }
                }
            }
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onEntityAdded(EntityAddedEvent event) {
        if (!pearlEnabled.get() || mc.player == null) return;
        if (event.entity instanceof EnderPearlEntity pearl) {
            if (pearl.getOwner() instanceof PlayerEntity owner && !owner.equals(mc.player)) {
                if (ignoreFriends.get() && Friends.get().isFriend(owner)) return;
                addNotification("Pearl: " + owner.getName().getString(), Type.INFO, Items.ENDER_PEARL.getDefaultStack());
            }
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!mentionEnabled.get() || mc.player == null) return;
        String msg = event.getMessage().getString();
        String myName = mc.player.getName().getString();

        if (!myName.isEmpty() && msg.toLowerCase().contains(myName.toLowerCase())) {
            if (!msg.startsWith(myName) && !msg.startsWith("<" + myName)) {
                addNotification("Mentioned in chat", Type.INFO, Items.PAPER.getDefaultStack());
            }
        }
    }

    // ================= RENDER =================

    @Override
    public void render(HudRenderer renderer) {
        if (mc.world == null || mc.player == null) return;

        updateVisualRange();
        updateBurrow();

        // Copy list to prevent concurrent modification and to allow adding preview safely
        List<Notification> renderList = new ArrayList<>(notifications);

        // FIXED: Inject Preview Notification if in Editor and list is empty
        if (renderList.isEmpty() && isInEditor()) {
            Notification preview = new Notification("Kooda Preview", Type.INFO, Items.NETHER_STAR.getDefaultStack());
            // Force yOffset to 0 so it's fully visible immediately
            preview.yOffset = 0;
            renderList.add(preview);
        }

        if (renderList.isEmpty()) return;

        double s = scale.get();
        double h = (renderer.textHeight() + 8) * s;
        double spacing = 2 * s;

        boolean alignRight = getX() + (getWidth() / 2.0) > mc.getWindow().getScaledWidth() / 2.0;

        double maxW = 0;
        double targetY = 0;

        List<Notification> toRemove = new ArrayList<>();

        for (Notification n : renderList) {
            long timeAlive = System.currentTimeMillis() - n.startTime;
            double maxTime = displayTime.get() * 1000;

            // FIXED: If in editor, force the notification to stay alive
            if (isInEditor()) {
                timeAlive = 0; // Tricks the renderer into showing it with full alpha
            } else if (timeAlive > maxTime) {
                toRemove.add(n);
                continue;
            }

            n.yOffset = MathHelper.lerp(0.1, n.yOffset, targetY);

            // Pass the modified timeAlive if needed, or rely on startTime adjustment
            double w = renderNotificationInternal(renderer, n, s, alignRight);

            if (w > maxW) maxW = w;
            targetY += h + spacing;
        }

        // Only remove real notifications
        notifications.removeAll(toRemove);

        // Calculate size based on the rendered content (including preview)
        if (maxW < 50) maxW = 50;
        if (targetY < 10) targetY = 10;
        setSize(maxW, targetY);
    }

    private double renderNotificationInternal(HudRenderer renderer, Notification n, double s, boolean alignRight) {
        long timeAlive = System.currentTimeMillis() - n.startTime;

        // FIXED: Override timeAlive logic for Editor Preview to prevent fading
        if (isInEditor()) {
            timeAlive = 100; // Constant low value ensures alpha is 1.0
        }

        double maxTime = displayTime.get() * 1000;
        double fadeTime = 250;

        double animAlpha = 1.0;
        if (timeAlive < fadeTime) {
            animAlpha = MathHelper.clamp((double)timeAlive / fadeTime, 0, 1);
        } else if (timeAlive > maxTime - fadeTime) {
            animAlpha = MathHelper.clamp((maxTime - timeAlive) / fadeTime, 0, 1);
        }

        return switch (style.get()) {
            case Kooda -> renderKooda(renderer, n, s, alignRight, animAlpha, n.yOffset);
            case CSGO -> renderCSGO(renderer, n, s, alignRight, animAlpha, n.yOffset);
            case Cyber -> renderCyber(renderer, n, s, alignRight, animAlpha, n.yOffset);
            case Minimal -> renderMinimal(renderer, n, s, alignRight, animAlpha, n.yOffset);
            case Future -> renderFuture(renderer, n, s, alignRight, animAlpha, n.yOffset);
            case Bubble -> renderBubble(renderer, n, s, alignRight, animAlpha, n.yOffset);
            case Retro -> renderRetro(renderer, n, s, alignRight, animAlpha, n.yOffset);
            default -> renderStandard(renderer, n, s, alignRight, animAlpha, n.yOffset);
        };
    }

    // --- STYLES IMPLEMENTATION ---

    private double renderKooda(HudRenderer r, Notification n, double s, boolean right, double alpha, double yOff) {
        String text = n.text + (n.stackCount > 1 ? " x" + n.stackCount : "");
        double textW = r.textWidth(text) * s;
        double iconSize = 16 * s;
        double padding = 5 * s;
        double totalW = padding + iconSize + padding + textW + padding;

        double drawX = right ? (x + getWidth() - totalW) : x;
        double drawY = y + yOff;
        double h = (r.textHeight() + 8) * s;

        Color bg = new Color(15, 15, 15, (int)(220 * alpha));
        Color accent = new Color(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, (int)(255 * alpha));
        Color txt = colorText.get().copy();
        txt.a = (int)(255 * alpha);

        r.quad(drawX, drawY, totalW, h, bg);
        r.quad(drawX, drawY, totalW, 2 * s, accent);
        r.item(n.icon, (int)(drawX + padding), (int)(drawY + (h - iconSize) / 2 + (1 * s)), (float)s, true);
        r.text(text, drawX + padding + iconSize + padding, drawY + (h / 2) - (r.textHeight() * s / 2) + (1 * s), txt, true, s);

        return totalW;
    }

    private double renderCSGO(HudRenderer r, Notification n, double s, boolean right, double alpha, double yOff) {
        String text = n.text + (n.stackCount > 1 ? " x" + n.stackCount : "");
        double textW = r.textWidth(text) * s;
        double iconSize = 16 * s;
        double padding = 6 * s;
        double totalW = padding + iconSize + padding + textW + padding + (4 * s);

        double drawX = right ? (x + getWidth() - totalW) : x;
        double drawY = y + yOff;
        double h = (r.textHeight() + 8) * s;

        Color bg = new Color(0, 0, 0, (int)(180 * alpha));
        Color border = n.type.getColor(this);
        border.a = (int)(255 * alpha);
        Color txt = colorText.get().copy();
        txt.a = (int)(255 * alpha);

        r.quad(drawX, drawY, totalW, h, bg);
        r.quad(drawX, drawY, 3 * s, h, border);
        r.item(n.icon, (int)(drawX + (4 * s) + padding), (int)(drawY + (h - iconSize) / 2), (float)s, true);
        r.text(text, drawX + (4 * s) + padding + iconSize + padding, drawY + (h / 2) - (r.textHeight() * s / 2), txt, true, s);

        return totalW;
    }

    private double renderCyber(HudRenderer r, Notification n, double s, boolean right, double alpha, double yOff) {
        String text = n.text.toUpperCase();
        if (n.stackCount > 1) text += " [" + n.stackCount + "]";
        double textW = r.textWidth(text) * s;
        double totalW = (12 * s) + textW;
        double drawX = right ? (x + getWidth() - totalW) : x;
        double drawY = y + yOff;
        double h = (r.textHeight() + 8) * s;

        Color accent = n.type.getColor(this);
        accent.a = (int)(255 * alpha);

        r.quad(drawX, drawY, 2*s, h, accent);
        r.quad(drawX, drawY, 6*s, 2*s, accent);
        r.quad(drawX, drawY + h - 2*s, 6*s, 2*s, accent);

        double rX = drawX + totalW;
        r.quad(rX - 2*s, drawY, 2*s, h, accent);
        r.quad(rX - 6*s, drawY, 6*s, 2*s, accent);
        r.quad(rX - 6*s, drawY + h - 2*s, 6*s, 2*s, accent);

        r.text(text, drawX + (6*s), drawY + (4*s), accent, true, s);
        return totalW;
    }

    private double renderMinimal(HudRenderer r, Notification n, double s, boolean right, double alpha, double yOff) {
        String text = n.text + (n.stackCount > 1 ? " (" + n.stackCount + ")" : "");
        double textW = r.textWidth(text) * s;
        double totalW = (10 * s) + textW;
        double drawX = right ? (x + getWidth() - totalW) : x;
        double drawY = y + yOff;
        double h = (r.textHeight() + 6) * s;

        Color bg = new Color(240, 240, 240, (int)(240 * alpha));
        Color txt = new Color(20, 20, 20, (int)(255 * alpha));
        Color shadow = new Color(0, 0, 0, (int)(80 * alpha));

        r.quad(drawX + (2*s), drawY + (2*s), totalW, h, shadow);
        r.quad(drawX, drawY, totalW, h, bg);

        Color ind = n.type.getColor(this);
        ind.a = (int)(255 * alpha);
        r.quad(drawX, drawY + h - (1.5*s), totalW, 1.5*s, ind);

        r.text(text, drawX + (5*s), drawY + (3*s), txt, false, s);
        return totalW;
    }

    private double renderFuture(HudRenderer r, Notification n, double s, boolean right, double alpha, double yOff) {
        String text = n.text.toUpperCase();
        if (n.stackCount > 1) text += " [" + n.stackCount + "]";

        double textW = r.textWidth(text) * s;
        double padding = 6 * s;
        double totalW = padding + textW + padding;
        double h = (r.textHeight() + 8) * s;

        double drawX = right ? (x + getWidth() - totalW) : x;
        double drawY = y + yOff;

        Color border = n.type.getColor(this);
        border.a = (int)(255 * alpha);
        Color bg = new Color(0, 0, 0, (int)(150 * alpha));

        r.quad(drawX, drawY, totalW, h, bg);

        double bSize = 1 * s;
        r.quad(drawX, drawY, totalW, bSize, border);
        r.quad(drawX, drawY + h - bSize, totalW, bSize, border);
        r.quad(drawX, drawY, bSize, h, border);
        r.quad(drawX + totalW - bSize, drawY, bSize, h, border);

        r.text(text, drawX + padding, drawY + (h / 2) - (r.textHeight() * s / 2), border, true, s);
        return totalW;
    }

    private double renderBubble(HudRenderer r, Notification n, double s, boolean right, double alpha, double yOff) {
        String text = n.text + (n.stackCount > 1 ? " x" + n.stackCount : "");
        double textW = r.textWidth(text) * s;
        double iconSize = 14 * s;
        double padding = 6 * s;
        double totalW = padding + iconSize + padding + textW + padding;
        double h = (r.textHeight() + 10) * s;

        double drawX = right ? (x + getWidth() - totalW) : x;
        double drawY = y + yOff;

        Color bg = n.type.getColor(this);
        bg.a = (int)(180 * alpha);
        Color txt = new Color(255, 255, 255, (int)(255 * alpha));

        r.quad(drawX + (2*s), drawY, totalW - (4*s), h, bg);
        r.quad(drawX, drawY + (2*s), 2*s, h - (4*s), bg);
        r.quad(drawX + totalW - (2*s), drawY + (2*s), 2*s, h - (4*s), bg);

        r.item(n.icon, (int)(drawX + padding), (int)(drawY + (h - iconSize) / 2), (float)s, true);
        r.text(text, drawX + padding + iconSize + padding, drawY + (h / 2) - (r.textHeight() * s / 2), txt, true, s);
        return totalW;
    }

    private double renderRetro(HudRenderer r, Notification n, double s, boolean right, double alpha, double yOff) {
        String text = "> " + n.text + (n.stackCount > 1 ? " (" + n.stackCount + ")" : "");
        double textW = r.textWidth(text) * s;
        double totalW = (8 * s) + textW + (8 * s);
        double h = (r.textHeight() + 6) * s;

        double drawX = right ? (x + getWidth() - totalW) : x;
        double drawY = y + yOff;

        Color fg = new Color(0, 255, 0, (int)(255 * alpha));
        if (n.type == Type.DANGER) fg = new Color(255, 0, 0, (int)(255 * alpha));
        if (n.type == Type.WARNING) fg = new Color(255, 165, 0, (int)(255 * alpha));

        Color bg = new Color(0, 0, 0, (int)(240 * alpha));

        r.quad(drawX, drawY, totalW, h, bg);
        r.quad(drawX + (4*s), drawY + h - (2*s), totalW - (8*s), 1*s, fg);

        r.text(text, drawX + (8*s), drawY + (3*s), fg, true, s);
        return totalW;
    }

    private double renderStandard(HudRenderer r, Notification n, double s, boolean right, double alpha, double yOff) {
        String text = n.text + (n.stackCount > 1 ? " x" + n.stackCount : "");
        double textW = r.textWidth(text) * s;
        double totalW = (8 * s) + textW;
        double drawX = right ? (x + getWidth() - totalW) : x;
        double drawY = y + yOff;
        double h = (r.textHeight() + 8) * s;

        Color bg = backgroundColor.get().copy();
        bg.a = (int)(bg.a * alpha);
        Color txt = colorText.get().copy();
        txt.a = (int)(255 * alpha);

        r.quad(drawX, drawY, totalW, h, bg);
        r.text(text, drawX + (4*s), drawY + (4*s), txt, true, s);
        return totalW;
    }

    // --- LOGIC ---

    private void updateVisualRange() {
        if (!vrEnabled.get() || mc.world == null) return;

        List<AbstractClientPlayerEntity> playerList = mc.world.getPlayers();
        Set<UUID> currentUUIDs = new HashSet<>();
        for (AbstractClientPlayerEntity p : playerList) {
            currentUUIDs.add(p.getUuid());
        }

        Iterator<Map.Entry<UUID, String>> it = knownPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            UUID uuid = entry.getKey();
            String name = entry.getValue();

            if (!currentUUIDs.contains(uuid)) {
                if (vrLeave.get()) {
                    addNotification("Left: " + name, Type.INFO, Items.SPYGLASS.getDefaultStack());
                }
                it.remove();
            }
        }

        for (AbstractClientPlayerEntity p : playerList) {
            if (shouldSkip(p)) continue;
            if (!knownPlayers.containsKey(p.getUuid())) {
                if (vrEnter.get()) {
                    addNotification("Entered: " + p.getName().getString(), Type.INFO, Items.SPYGLASS.getDefaultStack());
                }
                knownPlayers.put(p.getUuid(), p.getName().getString());
            }
        }
    }

    private void updateBurrow() {
        if (!burrowEnabled.get() || mc.world == null) return;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (shouldSkip(player)) continue;
            BlockPos pos = player.getBlockPos();
            Block block = mc.world.getBlockState(pos).getBlock();
            boolean isBurrowBlock = block == Blocks.OBSIDIAN || block == Blocks.ENDER_CHEST || block == Blocks.BEDROCK || block == Blocks.ANVIL;

            if (isBurrowBlock) {
                if (!burrowedPlayers.contains(player.getUuid())) {
                    addNotification("Burrow: " + player.getName().getString(), Type.WARNING, new ItemStack(block));
                    burrowedPlayers.add(player.getUuid());
                }
            } else {
                burrowedPlayers.remove(player.getUuid());
            }
        }
    }

    private void addNotification(String text, Type type, ItemStack icon) {
        if (!notifications.isEmpty()) {
            Notification last = notifications.getFirst();
            if (last.text.equals(text) && last.type == type) {
                last.stackCount++;
                last.startTime = System.currentTimeMillis();
                return;
            }
        }
        notifications.addFirst(new Notification(text, type, icon));

        if (sounds.get()) {
            if (customSound.get()) {
                playExternalSound();
            } else {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_IN, 1.0f, 1.0f));
            }
        }
    }

    private void playExternalSound() {
        soundExecutor.submit(() -> {
            try {
                if (cachedSoundFile != null && cachedSoundFile.exists()) {
                    AudioInputStream audioIn = AudioSystem.getAudioInputStream(cachedSoundFile);
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioIn);

                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                        gainControl.setValue(-5.0f);
                    }

                    clip.start();
                    Thread.sleep(2000);

                    clip.close();
                    audioIn.close();
                } else {
                    mc.execute(() -> mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_IN, 1.0f, 1.0f)));
                }
            } catch (Exception e) {
                mc.execute(() -> mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_IN, 1.0f, 1.0f)));
            }
        });
    }

    private boolean shouldSkip(PlayerEntity player) {
        if (player.equals(mc.player)) return true;
        return ignoreFriends.get() && Friends.get().isFriend(player);
    }

    // --- CLASSES ---

    private static class Notification {
        String text;
        Type type;
        ItemStack icon;
        long startTime;
        int stackCount = 1;
        double yOffset = -20;

        public Notification(String text, Type type, ItemStack icon) {
            this.text = text;
            this.type = type;
            this.icon = icon;
            this.startTime = System.currentTimeMillis();
        }
    }

    public enum Style {
        Kooda,
        Standard,
        CSGO,
        Cyber,
        Minimal,
        Future,
        Bubble,
        Retro
    }

    public enum Type {
        INFO,
        WARNING,
        DANGER;

        public Color getColor(KoodaNotifierHud hud) {
            return switch (this) {
                case WARNING -> hud.colorWarn.get();
                case DANGER -> hud.colorDanger.get();
                default -> hud.colorInfo.get();
            };
        }
    }
}