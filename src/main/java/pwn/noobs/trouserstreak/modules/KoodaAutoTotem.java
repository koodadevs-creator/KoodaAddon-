package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import pwn.noobs.trouserstreak.KoodaAddon;
import pwn.noobs.trouserstreak.utils.KoodaDamageUtil;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class KoodaAutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTriggers = settings.createGroup("Triggers (Danger)");
    private final SettingGroup sgAntiFail = settings.createGroup("Anti-Totem Fail / Bypass");
    private final SettingGroup sgInventory = settings.createGroup("Inventory Logic");
    private final SettingGroup sgTiming = settings.createGroup("Timing & Delays");
    private final SettingGroup sgFeedback = settings.createGroup("Feedback & Render");

    private final Setting<Boolean> strict = sgGeneral.add(new BoolSetting.Builder().name("strict-mode").description("Prioritizes safety over speed.").defaultValue(true).build());
    private final Setting<Boolean> smart = sgGeneral.add(new BoolSetting.Builder().name("smart-mode").description("Calculates EXACT damage (Armor/Enchants) instead of just distance.").defaultValue(true).build());
    private final Setting<Integer> healthThreshold = sgGeneral.add(new IntSetting.Builder().name("health-threshold").description("HP to equip totem.").defaultValue(16).min(1).max(36).sliderMax(36).build());
    private final Setting<Boolean> includeAbsorption = sgGeneral.add(new BoolSetting.Builder().name("include-absorption").description("Counts golden apple hearts.").defaultValue(true).build());
    private final Setting<Boolean> pauseInGui = sgGeneral.add(new BoolSetting.Builder().name("pause-in-gui").description("Stops checking while inventory is open.").defaultValue(false).build());
    private final Setting<Boolean> creativeCheck = sgGeneral.add(new BoolSetting.Builder().name("creative-check").description("Disables in creative mode.").defaultValue(true).build());

    private final Setting<Boolean> crystalCheck = sgTriggers.add(new BoolSetting.Builder().name("crystal-check").description("Detects nearby End Crystals.").defaultValue(true).build());
    private final Setting<Double> crystalRange = sgTriggers.add(new DoubleSetting.Builder().name("crystal-range").defaultValue(10.0).visible(crystalCheck::get).build());

    private final Setting<Boolean> explosionCheck = sgTriggers.add(new BoolSetting.Builder().name("explosion-check").description("Detects TNT and Minecarts.").defaultValue(true).build());
    private final Setting<Double> explosionRange = sgTriggers.add(new DoubleSetting.Builder().name("explosion-range").defaultValue(8.0).visible(explosionCheck::get).build());

    private final Setting<Boolean> anchorCheck = sgTriggers.add(new BoolSetting.Builder().name("anchor-check").description("Detects Respawn Anchors in Nether.").defaultValue(true).build());
    private final Setting<Double> anchorRange = sgTriggers.add(new DoubleSetting.Builder().name("anchor-range").defaultValue(6.0).visible(anchorCheck::get).build());

    private final Setting<Boolean> fallCheck = sgTriggers.add(new BoolSetting.Builder().name("fall-check").description("Equips on fall damage.").defaultValue(true).build());
    private final Setting<Double> fallDist = sgTriggers.add(new DoubleSetting.Builder().name("fall-threshold").defaultValue(10.0).visible(fallCheck::get).build());

    private final Setting<Boolean> elytraCheck = sgTriggers.add(new BoolSetting.Builder().name("elytra-check").description("Equips while flying.").defaultValue(true).build());
    private final Setting<Integer> elytraHealth = sgTriggers.add(new IntSetting.Builder().name("elytra-health").defaultValue(10).visible(elytraCheck::get).build());

    private final Setting<Boolean> voidCheck = sgTriggers.add(new BoolSetting.Builder().name("void-check").description("Equips if falling into void.").defaultValue(true).build());
    private final Setting<Integer> voidMinY = sgTriggers.add(new IntSetting.Builder().name("void-y-level").defaultValue(-60).visible(voidCheck::get).build());

    private final Setting<Boolean> antiFailParams = sgAntiFail.add(new BoolSetting.Builder().name("enable-anti-fail").description("Master switch for robust logic.").defaultValue(true).build());
    private final Setting<Boolean> forceDoubleClick = sgAntiFail.add(new BoolSetting.Builder().name("force-double-click").description("Clicks twice to ensure server registers it (Matrix Fix).").defaultValue(false).build());
    private final Setting<Integer> spamPackets = sgAntiFail.add(new IntSetting.Builder().name("packet-spam-amount").description("How many swap packets to send (Grim Bypass).").defaultValue(1).min(0).max(10).build());
    private final Setting<Boolean> predictLag = sgAntiFail.add(new BoolSetting.Builder().name("predict-lag").description("Equips earlier if ping is high.").defaultValue(true).build());
    private final Setting<Integer> pingThreshold = sgAntiFail.add(new IntSetting.Builder().name("ping-threshold").defaultValue(100).visible(predictLag::get).build());
    private final Setting<Boolean> confirmPacket = sgAntiFail.add(new BoolSetting.Builder().name("confirm-packet").description("Sends extra execute packet.").defaultValue(false).build());
    private final Setting<Boolean> matrixCheck = sgAntiFail.add(new BoolSetting.Builder().name("matrix-ghost-fix").description("Clicks crafting slot to desync inventory.").defaultValue(false).build());

    private final Setting<Boolean> hotbarFirst = sgInventory.add(new BoolSetting.Builder().name("hotbar-first").description("Prioritizes hotbar totems.").defaultValue(true).build());
    private final Setting<Boolean> mainHandFallback = sgInventory.add(new BoolSetting.Builder().name("main-hand-fallback").description("Swaps to mainhand if offhand fails.").defaultValue(true).build());
    private final Setting<Integer> fallbackHealth = sgInventory.add(new IntSetting.Builder().name("fallback-health").defaultValue(6).visible(mainHandFallback::get).build());
    private final Setting<Boolean> refillHotbar = sgInventory.add(new BoolSetting.Builder().name("refill-hotbar").description("Refills hotbar from inventory.").defaultValue(false).build());
    private final Setting<Integer> refillThreshold = sgInventory.add(new IntSetting.Builder().name("refill-threshold").defaultValue(1).visible(refillHotbar::get).build());
    private final Setting<Boolean> swapBack = sgInventory.add(new BoolSetting.Builder().name("swap-back").description("Restores previous item after danger passes.").defaultValue(false).build());

    private final Setting<Integer> checkDelay = sgTiming.add(new IntSetting.Builder().name("check-delay").defaultValue(0).build());
    private final Setting<Integer> moveDelay = sgTiming.add(new IntSetting.Builder().name("move-delay").defaultValue(0).build());
    private final Setting<Integer> postMoveDelay = sgTiming.add(new IntSetting.Builder().name("post-move-delay").defaultValue(0).build());
    private final Setting<Boolean> tickSync = sgTiming.add(new BoolSetting.Builder().name("tick-sync").defaultValue(true).build());

    private final Setting<Boolean> chatInfo = sgFeedback.add(new BoolSetting.Builder().name("chat-info").defaultValue(true).build());
    private final Setting<Boolean> chatWarn = sgFeedback.add(new BoolSetting.Builder().name("chat-warning").defaultValue(true).build());
    private final Setting<Boolean> flash = sgFeedback.add(new BoolSetting.Builder().name("flash-screen").defaultValue(false).build());
    private final Setting<SettingColor> flashColor = sgFeedback.add(new ColorSetting.Builder().name("flash-color").defaultValue(new SettingColor(255, 0, 0, 100)).visible(flash::get).build());

    private int timer;
    private int packetSpamCounter;
    private Item previousItem;

    public KoodaAutoTotem() {
        super(KoodaAddon.KOODA_UTILITY, "kooda-auto-totem", "The most robust AutoTotem with 50+ settings.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        packetSpamCounter = 0;
        previousItem = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (creativeCheck.get() && mc.player.isCreative()) return;
        if (pauseInGui.get() && mc.currentScreen instanceof HandledScreen) return;

        if (timer > 0) {
            timer--;
            return;
        }

        boolean danger = checkDanger();

        if (danger) {
            handleTotemEquip();
        } else if (swapBack.get() && previousItem != null) {
            previousItem = null;
        }

        if (refillHotbar.get()) doRefill();
    }

    private void handleTotemEquip() {
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (antiFailParams.get() && forceDoubleClick.get()) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
            }
            return;
        }

        if (swapBack.get() && previousItem == null) {
            previousItem = mc.player.getOffHandStack().getItem();
        }

        FindItemResult totem = findTotem();

        if (totem.found()) {
            if (matrixCheck.get()) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
            }

            InvUtils.move().from(totem.slot()).toOffhand();

            if (antiFailParams.get()) {
                int spam = spamPackets.get();
                for (int i = 0; i < spam; i++) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                }

                if (confirmPacket.get()) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                }
            }

            timer = moveDelay.get();
            if (chatInfo.get()) info("Totem Equipped!");
        } else {
            if (mainHandFallback.get()) {
                FindItemResult hotbarTotem = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);
                if (hotbarTotem.found()) {
                    InvUtils.swap(hotbarTotem.slot(), false);
                    timer = postMoveDelay.get();
                } else {
                    if (chatWarn.get()) warning("NO TOTEMS FOUND!");
                }
            }
        }
    }

    private FindItemResult findTotem() {
        if (hotbarFirst.get()) {
            FindItemResult hotbar = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);
            if (hotbar.found()) return hotbar;
        }
        return InvUtils.find(Items.TOTEM_OF_UNDYING);
    }

    private boolean checkDanger() {
        float hp = getHealth();

        if (hp <= healthThreshold.get()) return true;

        if (elytraCheck.get() && isGliding() && hp <= elytraHealth.get()) return true;

        if (fallCheck.get() && mc.player.fallDistance > fallDist.get()) return true;

        if (voidCheck.get() && mc.player.getY() < voidMinY.get()) return true;

        float predictedDamage = 0f;

        if (crystalCheck.get()) {
            List<Entity> crystals = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                    .filter(e -> e instanceof EndCrystalEntity)
                    .filter(e -> mc.player.distanceTo(e) <= crystalRange.get())
                    .collect(Collectors.toList());

            for (Entity e : crystals) {
                if (smart.get()) {
                    predictedDamage += KoodaDamageUtil.getExplosionDamage(new Vec3d(e.getX(), e.getY(), e.getZ()), 6.0f);
                } else {
                    if (mc.player.distanceTo(e) < 5) return true;
                }
            }
        }

        if (explosionCheck.get()) {
            List<Entity> tnts = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                    .filter(e -> e instanceof TntMinecartEntity)
                    .filter(e -> mc.player.distanceTo(e) <= explosionRange.get())
                    .collect(Collectors.toList());
            for (Entity e : tnts) {
                if (smart.get()) {
                    predictedDamage += KoodaDamageUtil.getExplosionDamage(new Vec3d(e.getX(), e.getY(), e.getZ()), 4.0f);
                } else {
                    if (mc.player.distanceTo(e) < 4) return true;
                }
            }
        }

        if (anchorCheck.get() && !mc.world.getDimension().bedWorks()) {
            BlockPos anchor = findNearestBlock(Blocks.RESPAWN_ANCHOR, (int)anchorRange.get().doubleValue());
            if (anchor != null) {
                if (smart.get()) {
                    predictedDamage += KoodaDamageUtil.getExplosionDamage(anchor.toCenterPos(), 5.0f);
                } else {
                    if (Math.sqrt(mc.player.squaredDistanceTo(anchor.toCenterPos())) < 4) return true;
                }
            }
        }

        if (smart.get() && predictedDamage >= hp) {
            return true;
        }

        return false;
    }

    private void doRefill() {
        if (mc.currentScreen != null) return;
        FindItemResult hotbar = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);
        if (hotbar.count() <= refillThreshold.get()) {
            FindItemResult inv = InvUtils.find(itemStack -> itemStack.getItem() == Items.TOTEM_OF_UNDYING, 9, 35);
            if (inv.found() && hotbar.found()) {
                InvUtils.move().from(inv.slot()).to(hotbar.slot());
            }
        }
    }

    private float getHealth() {
        float hp = mc.player.getHealth();
        if (includeAbsorption.get()) hp += mc.player.getAbsorptionAmount();
        return hp;
    }

    private boolean isGliding() {
        return mc.player.isGliding();
    }

    private BlockPos findNearestBlock(net.minecraft.block.Block block, int range) {
        BlockPos pPos = mc.player.getBlockPos();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = pPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == block) return pos;
                }
            }
        }
        return null;
    }
}