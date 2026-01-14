package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class KoodaSoundFX extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgVolume = settings.createGroup("Volume");


    private final Setting<Boolean> crystalSound = sgGeneral.add(new BoolSetting.Builder()
            .name("crystal-explosion")
            .description("Plays random sound from Crystal folder.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> popSound = sgGeneral.add(new BoolSetting.Builder()
            .name("totem-pop")
            .description("Plays random sound from Pop folder.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> anchorSound = sgGeneral.add(new BoolSetting.Builder()
            .name("anchor-sounds")
            .description("Plays random sound from Anchor folder.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> eatSound = sgGeneral.add(new BoolSetting.Builder()
            .name("eat-sound")
            .description("Plays sounds from Eat/Eating and Eat/Finished folders.")
            .defaultValue(true)
            .build()
    );


    private final Setting<Double> masterVolume = sgVolume.add(new DoubleSetting.Builder()
            .name("master-volume")
            .defaultValue(1.0)
            .min(0.1)
            .max(2.0)
            .build()
    );


    private final File mainFolder = new File(MinecraftClient.getInstance().runDirectory, "Kooda/SoundFX");


    private final File crystalFolder = new File(mainFolder, "Crystal");
    private final File popFolder = new File(mainFolder, "Pop");
    private final File anchorFolder = new File(mainFolder, "Anchor");


    private final File eatMainFolder = new File(mainFolder, "Eat");
    private final File eatChewFolder = new File(eatMainFolder, "Eating");
    private final File eatFinishFolder = new File(eatMainFolder, "Finished");

    private boolean isEating = false;
    private int eatTimer = 0;
    private final Random random = new Random();

    public KoodaSoundFX() {
        super(KoodaAddon.KOODA_MISC, "kooda-soundfx", "Plays random custom WAV sounds.");
    }

    @Override
    public void onActivate() {
        createDirectories();
    }

    private void createDirectories() {
        if (!mainFolder.exists()) mainFolder.mkdirs();

        if (!crystalFolder.exists()) crystalFolder.mkdir();
        if (!popFolder.exists()) popFolder.mkdir();
        if (!anchorFolder.exists()) anchorFolder.mkdir();

        if (!eatMainFolder.exists()) eatMainFolder.mkdir();
        if (!eatChewFolder.exists()) eatChewFolder.mkdir();
        if (!eatFinishFolder.exists()) eatFinishFolder.mkdir();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;


        if (event.packet instanceof EntityStatusS2CPacket packet && popSound.get()) {
            if (packet.getStatus() == 35) {
                playRandomSound(popFolder);
            }
        }


        if (event.packet instanceof PlaySoundS2CPacket packet) {

            String soundId = "unknown";


            if (packet.getSound().getKey().isPresent()) {
                soundId = packet.getSound().getKey().get().getValue().toString();
            } else {

                soundId = packet.getSound().value().toString();
            }

            if (crystalSound.get() && (soundId.equals("minecraft:entity.generic.explode") || soundId.contains("crystal") || soundId.equals("entity.generic.explode"))) {
                playRandomSound(crystalFolder);
            }

            if (anchorSound.get()) {
                if (soundId.contains("respawn_anchor.charge") || soundId.contains("respawn_anchor.deplete") || soundId.contains("respawn_anchor.set_spawn")) {
                    playRandomSound(anchorFolder);
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !eatSound.get()) return;

        if (mc.player.isUsingItem()) {
            ItemStack stack = mc.player.getActiveItem();


            boolean isFood = stack.contains(DataComponentTypes.FOOD);
            boolean isPotion = stack.getItem().toString().contains("potion");

            if (isFood || isPotion) {
                isEating = true;
                eatTimer++;


                if (eatTimer % 4 == 0) {
                    playRandomSound(eatChewFolder);
                }
            }
        } else {
            if (isEating) {
                if (eatTimer > 20) {
                    playRandomSound(eatFinishFolder);
                }
                isEating = false;
                eatTimer = 0;
            }
        }
    }

    private void playRandomSound(File folder) {
        if (!folder.exists()) return;

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));

        if (files == null || files.length == 0) return;

        File targetFile = files[random.nextInt(files.length)];

        new Thread(() -> {
            try {
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(targetFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);

                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float gain = (float) (Math.log10(masterVolume.get()) * 20);
                    gainControl.setValue(Math.min(Math.max(gain, gainControl.getMinimum()), gainControl.getMaximum()));
                }

                clip.start();

                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });

            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                KoodaAddon.LOG.error("Error playing sound: " + targetFile.getName());
            }
        }).start();
    }
}