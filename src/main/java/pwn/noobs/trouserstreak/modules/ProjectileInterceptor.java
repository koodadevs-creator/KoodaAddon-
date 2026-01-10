package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProjectileInterceptor extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("scan-range")
            .description("Radius to scan for projectiles.")
            .defaultValue(20.0)
            .build()
    );

    private final Setting<Double> interceptDist = sgGeneral.add(new DoubleSetting.Builder()
            .name("intercept-distance")
            .description("How close the projectile must be to you to trigger interception.")
            .defaultValue(6.0)
            .build()
    );

    private final Setting<Boolean> pearls = sgGeneral.add(new BoolSetting.Builder()
            .name("intercept-pearls")
            .description("Intercept Ender Pearls.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> arrows = sgGeneral.add(new BoolSetting.Builder()
            .name("intercept-arrows")
            .description("Intercept Arrows.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Don't intercept projectiles from friends.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate towards placement (Grim Bypass).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Render the interception block.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Color of the block render.")
            .defaultValue(new SettingColor(255, 0, 0, 75))
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Color of the block outline.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(render::get)
            .build()
    );

    private BlockPos renderPos = null;
    private int renderTimer = 0;

    public ProjectileInterceptor() {
        super(KoodaAddon.KOODA_COMBAT, "projectile-interceptor", "Calculates trajectory and places blocks to block incoming projectiles.");
    }

    @Override
    public void onActivate() {
        renderPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (renderTimer > 0) renderTimer--;
        else renderPos = null;

        if (mc.world == null || mc.player == null) return;

        FindItemResult web = InvUtils.findInHotbar(Items.COBWEB);
        FindItemResult obsi = InvUtils.findInHotbar(Items.OBSIDIAN);

        if (!web.found() && !obsi.found()) return;

        List<ProjectileEntity> dangerousProjectiles = new ArrayList<>();

        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ProjectileEntity proj) {
                if (mc.player.distanceTo(e) > range.get()) continue;

                if (e instanceof EnderPearlEntity && !pearls.get()) continue;
                if (e instanceof ArrowEntity && !arrows.get()) continue;

                Entity owner = proj.getOwner();
                if (owner != null) {
                    if (owner.equals(mc.player)) continue;

                    if (ignoreFriends.get() && owner instanceof PlayerEntity && Friends.get().isFriend((PlayerEntity) owner)) continue;
                }

                dangerousProjectiles.add(proj);
            }
        }

        dangerousProjectiles.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));

        for (ProjectileEntity proj : dangerousProjectiles) {
            BlockPos interceptPos = calculateInterception(proj);
            if (interceptPos != null) {
                FindItemResult placeItem = web.found() ? web : obsi;
                if (BlockUtils.place(interceptPos, placeItem, rotate.get(), 50)) {
                    renderPos = interceptPos;
                    renderTimer = 10;
                    break;
                }
            }
        }
    }

    private BlockPos calculateInterception(ProjectileEntity proj) {

        Vec3d pos = new Vec3d(proj.getX(), proj.getY(), proj.getZ());
        Vec3d vel = proj.getVelocity();


        for (int i = 0; i < 20; i++) {
            pos = pos.add(vel);
            vel = vel.multiply(0.99);
            vel = vel.subtract(0, 0.03, 0);

            if (mc.player.squaredDistanceTo(pos) < (interceptDist.get() * interceptDist.get())) {
                BlockPos bPos = BlockPos.ofFloored(pos);

                if (canPlace(bPos)) {
                    return bPos;
                }
            }
        }
        return null;
    }

    private boolean canPlace(BlockPos pos) {
        if (mc.world == null) return false;
        if (!World.isValid(pos)) return false;
        BlockState state = mc.world.getBlockState(pos);

        if (!state.isReplaceable()) return false;
        if (mc.player.getBoundingBox().intersects(new Box(pos))) return false;

        return BlockUtils.canPlace(pos);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (render.get() && renderPos != null) {
            event.renderer.box(renderPos, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }
}