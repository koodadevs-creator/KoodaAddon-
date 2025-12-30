package pwn.noobs.trouserstreak;

import pwn.noobs.trouserstreak.hud.*;
import pwn.noobs.trouserstreak.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KoodaAddon extends MeteorAddon {
    // Logger specifically for Kooda Addon identification in the console
    public static final Logger LOG = LoggerFactory.getLogger(KoodaAddon.class);

    // --- HUD & COLOR DEFINITIONS ---

    // The group where your HUDs will appear in the editor under the "Kooda" tab
    public static final HudGroup KOODA_HUD_GROUP = new HudGroup("Kooda");

    // The signature Kooda Blue color (Cyan)
    public static final Color KOODA_COLOR = new Color(0, 255, 255);

    // --- CATEGORIES DEFINITION ---

    // Test: Using a Command Block to indicate testing/dev modules
    public static final Category KOODA_TEST = new Category("Kooda Test", Items.COMMAND_BLOCK.getDefaultStack());

    // Combat: A Netherite Sword is classic for combat modules
    public static final Category KOODA_COMBAT = new Category("Kooda Combat", Items.NETHERITE_SWORD.getDefaultStack());

    // Exploit: TNT represents destruction, crashes, or exploits
    public static final Category KOODA_EXPLOIT = new Category("Kooda Exploit", Items.TNT.getDefaultStack());

    // Utility: A Crafting Table indicates utility tools
    public static final Category KOODA_UTILITY = new Category("Kooda Utility", Items.CRAFTING_TABLE.getDefaultStack());

    // World: A Golden Pickaxe for world interaction
    public static final Category KOODA_WORLD = new Category("Kooda World", Items.GOLDEN_PICKAXE.getDefaultStack());

    // Misc: A Bundle for miscellaneous items
    public static final Category KOODA_MISC = new Category("Kooda Misc", Items.BUNDLE.getDefaultStack());

    // Movement: Elytra for movement related hacks
    public static final Category KOODA_MOVEMENT = new Category("Kooda Move", Items.ELYTRA.getDefaultStack());

    // Render: Glow Ink Sac for visuals (ESP, Xray, etc.)
    public static final Category KOODA_RENDER = new Category("Kooda Render", Items.GLOW_INK_SAC.getDefaultStack());


    @Override
    public void onInitialize() {
        LOG.info("Initializing Kooda Addon!");

        // --- MODULE REGISTRATION ---
        // Registering modules here. Ensure AntiBookBan exists in your modules folder.
        Modules.get().add(new AntiBookBan());
        Modules.get().add(new KoodaSelfTrap());
        Modules.get().add(new KoodaStorageESP());
        Modules.get().add(new KoodaHoleESP());
        Modules.get().add(new AutoToxic());
        Modules.get().add(new KoodaNoGlitchBlocks());
        Modules.get().add(new ToxicAura());
        Modules.get().add(new KoodaHoleSnap());

        // --- HUD REGISTRATION ---
        // Registering the KeyBindHud.
        // FIXED: We now simply register the INFO object, because it contains the constructor inside it.
        Hud.get().register(KeyBindHud.INFO);
        Hud.get().register(WatermarkHud.INFO);
        Hud.get().register(VisualRangeHud.INFO);
        Hud.get().register(KoodaNotifierHud.INFO);
        Hud.get().register(CombatInfoHud.INFO);
        Hud.get().register(WelcomerHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        // Registering all categories in the system so they appear in the GUI
        Modules.registerCategory(KOODA_TEST);
        Modules.registerCategory(KOODA_COMBAT);
        Modules.registerCategory(KOODA_EXPLOIT);
        Modules.registerCategory(KOODA_UTILITY);
        Modules.registerCategory(KOODA_WORLD);
        Modules.registerCategory(KOODA_MISC);
        Modules.registerCategory(KOODA_MOVEMENT);
        Modules.registerCategory(KOODA_RENDER);
    }

    @Override
    public String getPackage() {
        // This must match your package structure in the src folder
        return "pwn.noobs.trouserstreak";
    }
}