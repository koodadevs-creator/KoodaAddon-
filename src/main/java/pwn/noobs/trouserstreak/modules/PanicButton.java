package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class PanicButton extends Module {

    public enum Mode {
        Modules,
        Client
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Panic behavior.")
            .defaultValue(Mode.Modules)
            .build()
    );

    private final List<Module> savedModules = new ArrayList<>();
    private final List<HudElement> savedHuds = new ArrayList<>();
    private boolean isPanicActive = false;
    private boolean ignoreToggle = false;

    public PanicButton() {
        super(KoodaAddon.KOODA_UTILITY, "panic-button", "Emergency switch. Prevents crashes via safe-toggle.");
    }

    @Override
    public void onActivate() {
        if (ignoreToggle) return;

        switch (mode.get()) {
            case Modules:
                handleModulesPanic();
                break;
            case Client:
                handleClientSelfDestruct();
                break;
        }

        ignoreToggle = true;
        if (this.isActive()) {
            this.toggle();
        }
        ignoreToggle = false;
    }

    private void handleModulesPanic() {
        if (!isPanicActive) {
            savedModules.clear();
            savedHuds.clear();

            for (Module module : Modules.get().getAll()) {
                if (module.isActive() && module != this) {
                    savedModules.add(module);
                    module.toggle();
                }
            }

            for (HudElement element : Hud.get()) {
                if (element.isActive()) {
                    savedHuds.add(element);
                    element.toggle();
                }
            }

            isPanicActive = true;
            ChatUtils.sendMsg(Formatting.RED, "[Panic] All systems disabled.");

        } else {
            for (Module module : savedModules) {
                if (!module.isActive()) {
                    module.toggle();
                }
            }

            for (HudElement element : savedHuds) {
                if (!element.isActive()) {
                    element.toggle();
                }
            }

            savedModules.clear();
            savedHuds.clear();
            isPanicActive = false;
            ChatUtils.sendMsg(Formatting.GREEN, "[Panic] Systems restored.");
        }
    }

    private void handleClientSelfDestruct() {
        ChatUtils.sendMsg(Formatting.RED, "Unloading Kooda & Meteor Client...");

        for (Module module : Modules.get().getAll()) {
            if (module.isActive() && module != this) {
                module.toggle();
            }
        }

        for (HudElement element : Hud.get()) {
            if (element.isActive()) {
                element.toggle();
            }
        }

        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().clear(false);
        }
    }
}