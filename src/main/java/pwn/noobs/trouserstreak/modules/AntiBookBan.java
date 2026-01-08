package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.Formatting;

public class AntiBookBan extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> limit = sgGeneral.add(new IntSetting.Builder()
            .name("character-limit")
            .description("Maximum NBT length allowed before stripping data.")
            .defaultValue(50000)
            .min(1000)
            .sliderMax(100000)
            .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
            .name("notify-in-chat")
            .description("Prints a message when an item is stripped.")
            .defaultValue(true)
            .build()
    );

    public AntiBookBan() {
        super(KoodaAddon.KOODA_UTILITY, "anti-book-ban", "Prevents kicks/crashes by stripping data from heavy items.");
    }

    @Override
    public void onActivate() {
        warning("§c§lWARNING: §rThis module causes §4INVENTORY DESYNC§r.");
        warning("Only use this if you are currently book-banned or chunk-banned.");
        warning("Disable it immediately after clearing the dangerous items.");
    }

    public static boolean shouldStrip(int length) {
        Module mod = Modules.get().get(AntiBookBan.class);
        if (mod == null || !mod.isActive()) return false;
        return length > ((AntiBookBan) mod).limit.get();
    }

    public static void notifyStrip(int length) {
        Module mod = Modules.get().get(AntiBookBan.class);
        if (mod instanceof AntiBookBan abb && abb.isActive() && abb.debug.get()) {
            ChatUtils.sendMsg(Formatting.GRAY, "[AntiBookBan] Stripped heavy item (Size: " + length + ")");
        }
    }
}