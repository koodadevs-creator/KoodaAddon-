package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

public class AntiBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    private final Setting<Boolean> removeInvisible = sgGeneral.add(new BoolSetting.Builder()
            .name("remove-invisible")
            .description("Only removes bots if they are invisible.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("check-delay")
            .description("Ticks between checks to save performance.")
            .defaultValue(20)
            .min(1)
            .sliderMax(100)
            .build()
    );

    private final Setting<Boolean> checkGameMode = sgFilters.add(new BoolSetting.Builder()
            .name("null-gamemode")
            .description("Removes players with no valid gamemode.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> checkTabEntry = sgFilters.add(new BoolSetting.Builder()
            .name("null-tab-entry")
            .description("Removes players not present in the tab list.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> checkProfile = sgFilters.add(new BoolSetting.Builder()
            .name("null-profile")
            .description("Removes players with no game profile (UUID/Name).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> checkPing = sgFilters.add(new BoolSetting.Builder()
            .name("suspicious-ping")
            .description("Removes players with exactly 0ms latency (common in bots).")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> checkUUID = sgFilters.add(new BoolSetting.Builder()
            .name("malformed-uuid")
            .description("Removes players with non-standard UUID versions.")
            .defaultValue(false)
            .build()
    );

    private int timer = 0;

    public AntiBot() {
        super(KoodaAddon.KOODA_MISC, "anti-bot", "Detects and client-side removes bot entities.");
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (timer > 0) {
            timer--;
            return;
        }
        timer = delay.get();

        List<PlayerEntity> toRemove = new ArrayList<>();

        for (PlayerEntity entity : mc.world.getPlayers()) {
            if (entity == null || entity.equals(mc.player)) continue;

            if (removeInvisible.get() && !entity.isInvisible()) continue;

            if (isBot(entity)) {
                toRemove.add(entity);
            }
        }

        for (PlayerEntity bot : toRemove) {
            bot.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    private boolean isBot(PlayerEntity entity) {
        if (mc.getNetworkHandler() == null) return false;

        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());

        if (checkTabEntry.get() && entry == null) {
            return true;
        }

        if (entry != null) {
            if (checkGameMode.get() && entry.getGameMode() == null) {
                return true;
            }

            if (checkProfile.get() && entry.getProfile() == null) {
                return true;
            }

            if (checkPing.get() && entry.getLatency() == 0) {
                return true;
            }
        }

        if (checkUUID.get() && entity.getUuid().version() != 4) {
            return true;
        }

        return false;
    }
}