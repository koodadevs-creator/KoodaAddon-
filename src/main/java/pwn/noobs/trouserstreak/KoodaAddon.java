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
    public static final Logger LOG = LoggerFactory.getLogger(KoodaAddon.class);

    public static final HudGroup KOODA_HUD_GROUP = new HudGroup("Kooda");
    public static final Color KOODA_COLOR = new Color(0, 255, 255);

    public static final Category KOODA_TEST = new Category("Kooda Test", Items.COMMAND_BLOCK.getDefaultStack());
    public static final Category KOODA_COMBAT = new Category("Kooda Combat", Items.NETHERITE_SWORD.getDefaultStack());
    public static final Category KOODA_EXPLOIT = new Category("Kooda Exploit", Items.TNT.getDefaultStack());
    public static final Category KOODA_UTILITY = new Category("Kooda Utility", Items.CRAFTING_TABLE.getDefaultStack());
    public static final Category KOODA_WORLD = new Category("Kooda World", Items.GOLDEN_PICKAXE.getDefaultStack());
    public static final Category KOODA_MISC = new Category("Kooda Misc", Items.BUNDLE.getDefaultStack());
    public static final Category KOODA_MOVEMENT = new Category("Kooda Move", Items.ELYTRA.getDefaultStack());
    public static final Category KOODA_RENDER = new Category("Kooda Render", Items.GLOW_INK_SAC.getDefaultStack());

    @Override
    public void onInitialize() {
        System.out.println("  _  __                _       ");
        System.out.println(" | |/ /               | |      ");
        System.out.println(" | ' / ___   ___   __| | __ _ ");
        System.out.println(" |  < / _ \\ / _ \\ / _` |/ _` |");
        System.out.println(" | . \\ (_) | (_) | (_| | (_| |");
        System.out.println(" |_|\\_\\___/ \\___/ \\__,_|\\__,_|");
        System.out.println("                               ");
        System.out.println(" Kooda Addon v0.3.2 Initialized");

        Modules.get().add(new PanicButton());
        Modules.get().add(new AutoBookBan());
        Modules.get().add(new AntiBookBan());
        Modules.get().add(new KoodaSoundFX());
        Modules.get().add(new AntiLevitation());
        Modules.get().add(new KoodaDoubleHand());
        Modules.get().add(new KoodaGrimVelocity());
        Modules.get().add(new KoodaBurrow());
        Modules.get().add(new KoodaSelfTrap());
        Modules.get().add(new KoodaStorageESP());
        Modules.get().add(new KoodaHoleESP());
        Modules.get().add(new AutoToxic());
        Modules.get().add(new KoodaNoGlitchBlocks());
        Modules.get().add(new ToxicAura());
        Modules.get().add(new KoodaHoleSnap());
        Modules.get().add(new AntiRegearKooda());
        Modules.get().add(new SurroundPlus());
        Modules.get().add(new KoodaParticles());
        Modules.get().add(new ProjectileInterceptor());


        Hud.get().register(KeyBindHud.INFO);
        Hud.get().register(WatermarkHud.INFO);
        Hud.get().register(VisualRangeHud.INFO);
        Hud.get().register(KoodaNotifierHud.INFO);
        Hud.get().register(CombatInfoHud.INFO);
        Hud.get().register(WelcomerHud.INFO);
        Hud.get().register(RealTimeClock.INFO);
        Hud.get().register(BitCoinHud.INFO);
        Hud.get().register(RealTimeClock.INFO);
        Hud.get().register(BitCoinHud.INFO);
        Hud.get().register(ClientSpooferHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
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
        return "pwn.noobs.trouserstreak";
    }
}