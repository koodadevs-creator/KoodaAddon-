package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import pwn.noobs.trouserstreak.KoodaAddon;

public class AutoObsidian extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgControl = settings.createGroup("Control");
    private final SettingGroup sgMining = settings.createGroup("Mining");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> targetAmount = sgGeneral.add(new IntSetting.Builder()
            .name("target-amount")
            .description("How much obsidian to farm before stopping.")
            .defaultValue(64)
            .min(1)
            .sliderMax(6400)
            .build()
    );

    private final Setting<Boolean> inventorySearch = sgGeneral.add(new BoolSetting.Builder()
            .name("inventory-search")
            .description("Refills hotbar with Ender Chests from inventory.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> stopMovement = sgControl.add(new BoolSetting.Builder()
            .name("stop-movement")
            .description("Stops player movement input while farming.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> stopEat = sgControl.add(new BoolSetting.Builder()
            .name("stop-eat")
            .description("Prevents eating while farming.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> doubleMine = sgMining.add(new BoolSetting.Builder()
            .name("double-mine")
            .description("Places and mines 2 Ender Chests at once.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> packetMine = sgMining.add(new BoolSetting.Builder()
            .name("packet-mine")
            .description("Uses packets to break blocks instantly.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> rotate = sgMining.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Looks at the block when placing/breaking.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> noSwing = sgMining.add(new BoolSetting.Builder()
            .name("no-swing")
            .description("Disables hand swing animation.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> delay = sgMining.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks to wait between actions.")
            .defaultValue(2)
            .min(0)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders the block being mined.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> mineColor = sgRender.add(new ColorSetting.Builder()
            .name("mine-color")
            .description("Color while mining.")
            .defaultValue(new SettingColor(255, 0, 0, 100))
            .build()
    );

    private final Setting<SettingColor> successColor = sgRender.add(new ColorSetting.Builder()
            .name("success-color")
            .description("Color when target amount is reached.")
            .defaultValue(new SettingColor(0, 255, 255, 150))
            .build()
    );

    private BlockPos placePos;
    private int timer;
    private boolean isFarming = false;
    private int successTimer = 0;

    public AutoObsidian() {
        super(KoodaAddon.KOODA_WORLD, "auto-obsidian", "Farms Obsidian from Ender Chests automatically.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        placePos = null;
        isFarming = false;
        successTimer = 0;
    }

    @Override
    public void onDeactivate() {
        placePos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        int obsidianCount = InvUtils.find(Items.OBSIDIAN).count();

        if (successTimer > 0) successTimer--;

        if (obsidianCount >= targetAmount.get()) {
            if (isFarming) {
                isFarming = false;
                successTimer = 20;
            }
            return;
        }

        startFarming();

        if (stopEat.get() && mc.player.isUsingItem() && mc.player.getActiveItem().contains(DataComponentTypes.FOOD)) {
            mc.player.stopUsingItem();
        }

        if (timer > 0) {
            timer--;
            return;
        }

        FindItemResult echest = InvUtils.findInHotbar(Items.ENDER_CHEST);

        if (!echest.found() && inventorySearch.get()) {
            FindItemResult invEchest = InvUtils.find(Items.ENDER_CHEST);
            if (invEchest.found()) {
                InvUtils.move().from(invEchest.slot()).toHotbar(0);
                echest = InvUtils.findInHotbar(Items.ENDER_CHEST);
            }
        }

        if (!echest.found()) return;

        FindItemResult pickaxe = InvUtils.findInHotbar(itemStack -> {
            Item item = itemStack.getItem();
            return item == Items.NETHERITE_PICKAXE ||
                    item == Items.DIAMOND_PICKAXE ||
                    item == Items.IRON_PICKAXE ||
                    item == Items.GOLDEN_PICKAXE ||
                    item == Items.STONE_PICKAXE ||
                    item == Items.WOODEN_PICKAXE;
        });

        if (!pickaxe.found()) return;

        if (placePos == null || mc.world.getBlockState(placePos).isAir()) {
            placePos = findPlacePos();
            if (placePos != null) {
                BlockUtils.place(placePos, echest, rotate.get(), 50, true);

                if (doubleMine.get()) {
                    BlockPos secondPos = placePos.up();
                    if (BlockUtils.canPlace(secondPos)) {
                        BlockUtils.place(secondPos, echest, rotate.get(), 50, true);
                    }
                }

                timer = delay.get();
            }
        } else if (mc.world.getBlockState(placePos).getBlock() == Blocks.ENDER_CHEST) {
            mineBlock(pickaxe, placePos);

            if (doubleMine.get()) {
                BlockPos secondPos = placePos.up();
                if (mc.world.getBlockState(secondPos).getBlock() == Blocks.ENDER_CHEST) {
                    mineBlock(pickaxe, secondPos);
                }
            }

            timer = delay.get();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || placePos == null) return;

        if (isFarming) {
            event.renderer.box(placePos, mineColor.get(), mineColor.get(), ShapeMode.Both, 0);
            if (doubleMine.get()) {
                event.renderer.box(placePos.up(), mineColor.get(), mineColor.get(), ShapeMode.Both, 0);
            }
        } else if (successTimer > 0) {
            event.renderer.box(placePos, successColor.get(), successColor.get(), ShapeMode.Both, 0);
            if (doubleMine.get()) {
                event.renderer.box(placePos.up(), successColor.get(), successColor.get(), ShapeMode.Both, 0);
            }
        }
    }

    private void startFarming() {
        if (!isFarming) {
            isFarming = true;
            if (stopMovement.get()) {
                mc.options.forwardKey.setPressed(false);
                mc.options.backKey.setPressed(false);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                mc.options.jumpKey.setPressed(false);
            }
        }
    }

    private BlockPos findPlacePos() {
        BlockPos pos = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());
        if (!BlockUtils.canPlace(pos)) {
            pos = mc.player.getBlockPos().up(2);
            if (!BlockUtils.canPlace(pos)) return null;
        }
        if (doubleMine.get() && !BlockUtils.canPlace(pos.up())) {
            return null;
        }
        return pos;
    }

    private void mineBlock(FindItemResult pickaxe, BlockPos target) {
        InvUtils.swap(pickaxe.slot(), false);

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target));
        }

        if (packetMine.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, Direction.UP));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, Direction.UP));
        } else {
            mc.interactionManager.updateBlockBreakingProgress(target, Direction.UP);
        }

        if (!noSwing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}