package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SoundLocator extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General
    private final Setting<Boolean> whitelist = sgGeneral.add(new BoolSetting.Builder()
            .name("whitelist")
            .description("Enable sounds filter whitelist.")
            .defaultValue(false)
            .build()
    );

    private final Setting<List<SoundEvent>> sounds = sgGeneral.add(new SoundEventListSetting.Builder()
            .name("sounds")
            .description("Sounds to find.")
            .defaultValue(List.of())
            .visible(whitelist::get)
            .build()
    );

    private final Setting<Boolean> chatActive = sgGeneral.add(new BoolSetting.Builder()
            .name("log-chat")
            .description("Send the position of the sound in the chat.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> timeS = sgGeneral.add(new IntSetting.Builder()
            .name("time")
            .description("The time (in ticks) to render the sound.")
            .defaultValue(60)
            .min(1)
            .sliderMax(200)
            .build()
    );

    // Render
    private final Setting<Boolean> renderActive = sgRender.add(new BoolSetting.Builder()
            .name("render-positions")
            .description("Renders boxes where the sound was emitted.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the target sound rendering.")
            .defaultValue(new SettingColor(255, 0, 0, 70))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the target sound rendering.")
            .defaultValue(new SettingColor(255, 0, 0))
            .build()
    );

    private final Setting<Boolean> fadeOut = sgRender.add(new BoolSetting.Builder()
            .name("fade-out")
            .description("Fade out the render as time passes.")
            .defaultValue(true)
            .build()
    );


    private final List<SoundRecord> soundRecords = new CopyOnWriteArrayList<>();

    public SoundLocator() {
        super(KoodaAddon.KOODA_EXPLOIT, "sound-locator", "Prints locations of sound events.");
    }

    @Override
    public void onDeactivate() {
        soundRecords.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        soundRecords.removeIf(record -> {
            record.tick();
            return record.isExpired();
        });
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.sound;

        if (whitelist.get()) {
            boolean found = false;
            for (SoundEvent s : sounds.get()) {
                if (s.id().equals(sound.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) return;
        }

        Vec3d pos = new Vec3d(sound.getX() - 0.5, sound.getY() - 0.5, sound.getZ() - 0.5);
        Vec3d checkPos = new Vec3d(sound.getX(), sound.getY(), sound.getZ());

        for (SoundRecord record : soundRecords) {
            if (record.origin.squaredDistanceTo(checkPos) < 1 && record.age < 5) return;
        }

        soundRecords.add(new SoundRecord(pos, checkPos, timeS.get()));

        if (chatActive.get()) {
            WeightedSoundSet soundSet = mc.getSoundManager().get(sound.getId());
            MutableText text;

            if (soundSet == null || soundSet.getSubtitle() == null) {
                text = Text.literal(sound.getId().toString());
            } else {
                text = soundSet.getSubtitle().copy();
            }

            text.append(Text.literal(" at ").formatted(Formatting.GRAY));
            text.append(ChatUtils.formatCoords(new Vec3d(sound.getX(), sound.getY(), sound.getZ())));

            info(text);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderActive.get()) return;

        for (SoundRecord record : soundRecords) {
            double progress = 1.0;
            if (fadeOut.get()) {
                progress = (double) record.ticksLeft / timeS.get();
            }

            Color sColor = new Color(sideColor.get());
            Color lColor = new Color(lineColor.get());

            sColor.a = (int) (sColor.a * progress);
            lColor.a = (int) (lColor.a * progress);

            Box box = new Box(record.pos.x, record.pos.y, record.pos.z,
                    record.pos.x + 1, record.pos.y + 1, record.pos.z + 1);

            event.renderer.box(box, sColor, lColor, shapeMode.get(), 0);
        }
    }

    private static class SoundRecord {
        public final Vec3d pos;
        public final Vec3d origin;
        public int ticksLeft;
        public int age;

        public SoundRecord(Vec3d pos, Vec3d origin, int ticksLeft) {
            this.pos = pos;
            this.origin = origin;
            this.ticksLeft = ticksLeft;
            this.age = 0;
        }

        public void tick() {
            ticksLeft--;
            age++;
        }

        public boolean isExpired() {
            return ticksLeft <= 0;
        }
    }
}