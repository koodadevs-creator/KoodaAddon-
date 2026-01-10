package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

public class KoodaDoubleHand extends Module {

    public enum Mode {
        Smart,
        Panic
    }

    public enum SwapMode {
        Normal,
        Silent
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLogic = settings.createGroup("Logic");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Behavior logic.")
            .defaultValue(Mode.Smart)
            .build()
    );

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
            .name("health")
            .description("Health threshold to activate.")
            .defaultValue(14.0)
            .min(1)
            .max(36)
            .sliderMax(36)
            .build()
    );

    private final Setting<Double> stayHealth = sgGeneral.add(new DoubleSetting.Builder()
            .name("stay-health")
            .description("Health required to swap back.")
            .defaultValue(18.0)
            .min(1)
            .max(36)
            .sliderMax(36)
            .build()
    );

    private final Setting<Boolean> predict = sgGeneral.add(new BoolSetting.Builder()
            .name("predict-crystals")
            .description("Predicts lethal damage from nearby crystals.")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Smart)
            .build()
    );

    private final Setting<SwapMode> swapMode = sgLogic.add(new EnumSetting.Builder<SwapMode>()
            .name("swap-mode")
            .description("Silent uses packets. Normal changes hotbar.")
            .defaultValue(SwapMode.Silent)
            .build()
    );

    private final Setting<Boolean> checkOffhand = sgLogic.add(new BoolSetting.Builder()
            .name("check-offhand")
            .description("Don't activate if offhand has Totem.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> overrideTicks = sgLogic.add(new IntSetting.Builder()
            .name("override-ticks")
            .description("Ticks to pause auto-totem after you manually switch slots (allows interactions).")
            .defaultValue(10)
            .min(0)
            .max(40)
            .build()
    );

    private final Setting<Boolean> renderSlot = sgRender.add(new BoolSetting.Builder()
            .name("render-slot")
            .description("Highlights the totem slot.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
            .name("color")
            .description("Highlight color.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 80))
            .visible(renderSlot::get)
            .build()
    );

    private int serverSlot = -1;
    private int restoreSlot = -1;
    private boolean locked = false;
    private int pauseTimer = 0;

    public KoodaDoubleHand() {
        super(KoodaAddon.KOODA_COMBAT, "kooda-double-hand", "Advanced mainhand totem with full interaction override.");
    }

    @Override
    public void onActivate() {
        serverSlot = -1;
        restoreSlot = -1;
        locked = false;
        pauseTimer = 0;
    }

    @Override
    public void onDeactivate() {
        if (locked && restoreSlot != -1) {
            setSlot(restoreSlot);
        }
        serverSlot = -1;
        restoreSlot = -1;
        locked = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;


        if (pauseTimer > 0) {
            pauseTimer--;

            if (mc.player.isUsingItem()) {
                pauseTimer = 5;
            }

            return;
        }


        if (mc.player.isUsingItem()) return;

        int totemSlot = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING).slot();

        if (totemSlot == -1) {
            if (locked) unlock();
            return;
        }

        if (checkOffhand.get() && mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (locked) unlock();
            return;
        }

        boolean unsafe = checkSafety();

        if (unsafe) {
            if (!locked) {
                restoreSlot = mc.player.getInventory().selectedSlot;
                locked = true;
            }
            setSlot(totemSlot);
        } else {
            if (locked) {
                unlock();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof UpdateSelectedSlotC2SPacket packet) {

            if (locked && packet.getSelectedSlot() != serverSlot) {

                pauseTimer = overrideTicks.get();


                serverSlot = packet.getSelectedSlot();
            }
        }
    }

    private void setSlot(int slot) {
        if (slot < 0 || slot > 8) return;

        if (swapMode.get() == SwapMode.Silent) {
            if (serverSlot != slot) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                serverSlot = slot;
            }
        } else {
            if (mc.player.getInventory().selectedSlot != slot) {
                InvUtils.swap(slot, false);
                serverSlot = slot;
            }
        }
    }

    private void unlock() {
        if (restoreSlot != -1) {
            setSlot(restoreSlot);
        }
        locked = false;
        restoreSlot = -1;
        serverSlot = mc.player.getInventory().selectedSlot;
    }

    private boolean checkSafety() {
        float hp = PlayerUtils.getTotalHealth();
        double threshold = locked ? stayHealth.get() : health.get();

        if (hp <= threshold) return true;

        if (mode.get() == Mode.Smart && predict.get()) {
            return isCrystalThreat(hp);
        }

        return false;
    }

    private boolean isCrystalThreat(float currentHp) {
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity) {
                if (mc.player.squaredDistanceTo(e) > 144) continue;

                Vec3d pos = new Vec3d(e.getX(), e.getY(), e.getZ());
                double damage = calculateDamage(pos, 6.0f);
                if (damage >= currentHp || (currentHp - damage) < 3.0) return true;
            }
        }
        return false;
    }

    private double calculateDamage(Vec3d source, float power) {
        if (mc.player == null || mc.world.getDifficulty() == Difficulty.PEACEFUL) return 0;

        double dist = Math.sqrt(mc.player.squaredDistanceTo(source));
        if (dist > 12) return 0;

        double exposure = getExposure(source, mc.player);
        double impact = (1.0 - (dist / 12.0)) * exposure;
        double damage = (impact * impact + impact) / 2.0 * 7.0 * (double) power + 1.0;

        damage = getDifficultyDamage(damage);
        damage = getReduction(damage);

        return Math.max(0, damage);
    }

    private double getDifficultyDamage(double damage) {
        Difficulty dif = mc.world.getDifficulty();
        if (dif == Difficulty.PEACEFUL) return 0;
        if (dif == Difficulty.EASY) return Math.min(damage / 2.0 + 1.0, damage);
        if (dif == Difficulty.HARD) return damage * 1.5;
        return damage;
    }

    private double getReduction(double damage) {
        int armor = mc.player.getArmor();

        float toughness = (float) mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ARMOR_TOUGHNESS);

        float f = 2.0F + toughness / 4.0F;
        float g = Math.min(Math.max(armor - 4.0F * toughness / 8.0F, armor * 0.2F), 20.0F);
        damage = damage * (1.0F - g / 25.0F);

        if (mc.player.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amp = (mc.player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 5;
            damage = Math.max(damage * (1.0 - amp / 25.0), 0);
        }

        if (armor > 0) {
            damage = Math.max(damage * 0.8, 0);
        }

        return damage;
    }

    private float getExposure(Vec3d source, Entity entity) {
        Box box = entity.getBoundingBox();
        double d = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double e = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double f = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
        double h = (1.0 - Math.floor(1.0 / e) * e) / 2.0;
        double i = (1.0 - Math.floor(1.0 / f) * f) / 2.0;

        if (!(d < 0.0) && !(e < 0.0) && !(f < 0.0)) {
            int j = 0;
            int k = 0;
            for (float l = 0.0F; l <= 1.0F; l = (float) ((double) l + d)) {
                for (float m = 0.0F; m <= 1.0F; m = (float) ((double) m + e)) {
                    for (float n = 0.0F; n <= 1.0F; n = (float) ((double) n + f)) {
                        double o = box.minX + (box.maxX - box.minX) * (double) l;
                        double p = box.minY + (box.maxY - box.minY) * (double) m;
                        double q = box.minZ + (box.maxZ - box.minZ) * (double) n;
                        if (mc.world.raycast(new RaycastContext(new Vec3d(o + g, p + h, q + i), source, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity)).getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
                            j++;
                        }
                        k++;
                    }
                }
            }
            return (float) j / (float) k;
        }
        return 0.0F;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!renderSlot.get()) return;

        int totemSlot = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING).slot();
        boolean active = locked || (totemSlot != -1 && checkSafety());

        if (!active || totemSlot == -1) return;

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        int slotX = (width / 2) - 91 + 3 + (totemSlot * 20);
        int slotY = height - 19;

        if (event.drawContext != null) {
            int c = color.get().getPacked();
            event.drawContext.fill(slotX, slotY, slotX + 16, slotY + 16, c);
        }
    }
}