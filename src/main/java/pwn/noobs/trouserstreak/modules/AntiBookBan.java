package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class AntiBookBan extends Module {

    // HARDCODED LIMIT: 50,000 characters.
    // This value is high enough to allow ANY legitimate item (even full shulkers),
    // but low enough to block any packet that would cause a kick or client crash.
    public static final int LIMIT = 50000;

    public AntiBookBan() {
        super(KoodaAddon.KOODA_UTILITY, "anti-book-ban", "Automatically removes data from heavy items to prevent crashes. No configuration needed.");
    }

    // Static helper to check if module is active
    public static boolean isEffective() {
        Module module = Modules.get().get(AntiBookBan.class);
        return module != null && module.isActive();
    }
}