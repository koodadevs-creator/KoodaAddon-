package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

public class KoodaDoubleHand extends Module {

    public enum Mode {
        Smart,
        Panic
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLogic = settings.createGroup("Logic");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Behavior mode. Smart calculates damage, Panic checks health.")
            .defaultValue(Mode.Smart)
            .build()
    );

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
            .name("health")
            .description("Health threshold to activate Double Hand.")
            .defaultValue(14.0)
            .min(0)
            .max(36)
            .sliderMax(36)
            .build()
    );

    private final Setting<Double> stayHealth = sgGeneral.add(new DoubleSetting.Builder()
            .name("stay-health")
            .description("Health required to stop double handing.")
            .defaultValue(18.0)
            .min(0)
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

    private final Setting<Integer> delay = sgLogic.add(new IntSetting.Builder()
            .name("swap-delay")
            .description("Ticks to wait between swaps to prevent desync.")
            .defaultValue(0)
            .min(0)
            .build()
    );

    private final Setting<Boolean> swapBack = sgLogic.add(new BoolSetting.Builder()
            .name("swap-back")
            .description("Swaps back to the original weapon when safe.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> inInventory = sgLogic.add(new BoolSetting.Builder()
            .name("in-inventory")
            .description("Active even while inventory is open.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> holdingSword = sgLogic.add(new BoolSetting.Builder()
            .name("only-sword")
            .description("Only double hand if initially holding a sword.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> ignoreOffhand = sgLogic.add(new BoolSetting.Builder()
            .name("ignore-offhand")
            .description("Double hand even if you have a totem in offhand.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> renderAlways = sgRender.add(new BoolSetting.Builder()
            .name("render-always")
            .description("Highlights the totem slot even if not currently swapping.")
            .defaultValue(false)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
            .name("color")
            .description("The color of the slot highlight.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 80))
            .build()
    );

    private int originalSlot = -1;
    private int bestTotemSlot = -1;
    private int swapTimer = 0;
    private boolean isActive = false;

    public KoodaDoubleHand() {
        super(KoodaAddon.KOODA_COMBAT, "kooda-double-hand", "Robust mainhand totem management.");
    }

    @Override
    public void onActivate() {
        originalSlot = -1;
        bestTotemSlot = -1;
        isActive = false;
        swapTimer = 0;

        if (mc.inGameHud != null) {
            MutableText prefix = Text.literal("[Kooda] ").styled(style -> style.withColor(Formatting.AQUA));
            MutableText body = Text.literal("DoubleHand activated.").styled(style -> style.withColor(Formatting.GRAY));
            mc.inGameHud.getChatHud().addMessage(prefix.append(body));
        }
    }

    @Override
    public void onDeactivate() {
        if (isActive && swapBack.get() && originalSlot != -1) {
            InvUtils.swap(originalSlot, false);
        }
        originalSlot = -1;
        isActive = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (!inInventory.get() && mc.currentScreen != null) return;

        int foundTotem = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING).slot();
        bestTotemSlot = foundTotem;

        if (foundTotem == -1) {
            isActive = false;
            return;
        }

        if (swapTimer > 0) {
            swapTimer--;
            return;
        }

        boolean unsafe = isUnsafe();

        if (unsafe) {
            if (!isActive && holdingSword.get()) {
                String itemName = mc.player.getMainHandStack().getItem().toString().toLowerCase();
                if (!itemName.contains("sword")) {
                    return;
                }
            }

            if (!isActive) {
                originalSlot = mc.player.getInventory().selectedSlot;
            }

            if (mc.player.getInventory().selectedSlot != foundTotem) {
                InvUtils.swap(foundTotem, false);
                swapTimer = delay.get();
            }

            isActive = true;

        } else {
            if (isActive) {
                if (swapBack.get() && originalSlot != -1) {
                    if (mc.player.getInventory().selectedSlot == foundTotem) {
                        InvUtils.swap(originalSlot, false);
                    }
                }
                isActive = false;
                originalSlot = -1;
                swapTimer = delay.get();
            }
        }
    }

    private boolean isUnsafe() {
        if (!ignoreOffhand.get() && mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            return false;
        }

        float hp = PlayerUtils.getTotalHealth();

        if (isActive) {
            if (hp <= stayHealth.get()) return true;
        } else {
            if (hp <= health.get()) return true;
        }

        if (mode.get() == Mode.Smart && predict.get()) {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof EndCrystalEntity) {
                    if (mc.player.squaredDistanceTo(e) > 144) continue;

                    Vec3d crystalPos = new Vec3d(e.getX(), e.getY(), e.getZ());
                    double damage = calculateCrystalDamage(crystalPos, 6.0f);

                    if (damage >= hp || (hp - damage) < 3.0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private double calculateCrystalDamage(Vec3d source, float power) {
        if (mc.player == null || mc.world == null) return 0;

        if (mc.world.getDifficulty() == Difficulty.PEACEFUL) return 0;

        double distance = Math.sqrt(mc.player.squaredDistanceTo(source));
        if (distance > 12) return 0;

        double exposure = getExposure(source, mc.player);
        double impact = (1.0 - (distance / 12.0)) * exposure;

        double damage = (impact * impact + impact) / 2.0 * 7.0 * (double)power + 1.0;

        if (mc.world.getDifficulty() == Difficulty.EASY) damage = Math.min(damage / 2.0 + 1.0, damage);
        else if (mc.world.getDifficulty() == Difficulty.HARD) damage = damage * 1.5;

        float armor = (float) mc.player.getArmor();
        float toughness = 0.0f;

        float f = 2.0F + toughness / 4.0F;
        float g = Math.min(Math.max(armor - 4.0F * toughness / 8.0F, armor * 0.2F), 20.0F);
        damage = damage * (1.0F - g / 25.0F);

        if (mc.player.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amplifier = (mc.player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 5;
            damage = Math.max(damage * (1.0 - amplifier / 25.0), 0);
        }

        if (armor > 10) {
            damage *= 0.7;
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
                        Vec3d vec3d = new Vec3d(o + g, p + h, q + i);
                        if (mc.world.raycast(new RaycastContext(vec3d, source, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity)).getType() == HitResult.Type.MISS) {
                            j++;
                        }
                        k++;
                    }
                }
            }
            return (float) j / (float) k;
        } else {
            return 0.0F;
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        boolean shouldRender = isActive || (renderAlways.get() && bestTotemSlot != -1);

        if (!shouldRender || bestTotemSlot == -1 || mc.player == null) return;

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        int startX = (width / 2) - 91;
        int slotX = startX + 3 + (bestTotemSlot * 20);
        int slotY = height - 19;

        if (event.drawContext != null) {
            int borderC = color.get().getPacked() | 0xFF000000;

            event.drawContext.fill(slotX - 1, slotY - 1, slotX + 17, slotY, borderC);
            event.drawContext.fill(slotX - 1, slotY + 16, slotX + 17, slotY + 17, borderC);
            event.drawContext.fill(slotX - 1, slotY, slotX, slotY + 16, borderC);
            event.drawContext.fill(slotX + 16, slotY, slotX + 17, slotY + 16, borderC);
        }
    }
}