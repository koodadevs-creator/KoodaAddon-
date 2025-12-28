package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon; // Ensure this import matches your Main class location
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class ToxicAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAntiSpam = settings.createGroup("Anti-Spam");

    // --- General Settings ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Range to detect players.")
            .defaultValue(6.0)
            .min(1.0)
            .max(100.0)
            .sliderMax(20.0)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("global-delay-ticks")
            .description("Minimum ticks between ANY toxic message (20 ticks = 1 second).")
            .defaultValue(40)
            .min(10)
            .max(200)
            .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Don't be toxic to your friends.")
            .defaultValue(true)
            .build()
    );

    // --- Robustness Settings (Added automatically) ---
    private final Setting<Integer> playerCooldown = sgAntiSpam.add(new IntSetting.Builder()
            .name("player-cooldown-seconds")
            .description("How many seconds to wait before insulting the SAME player again.")
            .defaultValue(10)
            .min(1)
            .max(60)
            .build()
    );

    private final Setting<Boolean> antiSpamBypass = sgAntiSpam.add(new BoolSetting.Builder()
            .name("anti-spam-bypass")
            .description("Adds random characters at the end to bypass server anti-spam.")
            .defaultValue(true)
            .build()
    );

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
            .name("messages")
            .description("Messages to send. Use [NAME] for the player's name.")
            .defaultValue(List.of(
                    "Imagine being [NAME] and still losing LOL",
                    "Hey [NAME], nice tactics, where'd you get them? The trash can?",
                    "Kooda On Top! Right, [NAME]?",
                    "Are you even trying, [NAME]?",
                    "[NAME] is my favorite NPC.",
                    "GG [NAME]... oh wait, it wasn't a good game for you."
            ))
            .build()
    );

    // Variables for logic
    private int timer;
    private final Random random = new Random();
    // Stores the system time (ms) when a player was last insulted
    private final Map<String, Long> lastInsultedMap = new HashMap<>();

    public ToxicAura() {
        // Using the category from your Main class as requested
        super(KoodaAddon.KOODA_COMBAT, "toxic-aura", "Sends toxic messages to players in range.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        lastInsultedMap.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Global timer management
        if (timer > 0) {
            timer--;
            return;
        }

        PlayerEntity target = findBestTarget();

        if (target != null) {
            sendToxicMessage(target);

            // Record the time this specific player was insulted
            lastInsultedMap.put(target.getName().getString(), System.currentTimeMillis());

            // Reset global timer
            timer = delay.get();
        }

        // Clean up memory map to prevent memory leaks over long sessions
        cleanupMap();
    }

    /**
     * Finds the best target based on proximity and cooldowns.
     * @return The optimal PlayerEntity or null if none found.
     */
    private PlayerEntity findBestTarget() {
        PlayerEntity bestTarget = null;
        double closestDistance = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;

            String playerName = player.getName().getString();
            double distance = mc.player.distanceTo(player);

            if (distance <= range.get()) {
                // Check if this specific player is on cooldown
                if (isPlayerOnCooldown(playerName)) continue;

                // Prioritize closest player
                if (distance < closestDistance) {
                    closestDistance = distance;
                    bestTarget = player;
                }
            }
        }
        return bestTarget;
    }

    private boolean isPlayerOnCooldown(String name) {
        if (!lastInsultedMap.containsKey(name)) return false;
        long lastTime = lastInsultedMap.get(name);
        // Convert seconds setting to milliseconds
        return (System.currentTimeMillis() - lastTime) < (playerCooldown.get() * 1000L);
    }

    private void sendToxicMessage(PlayerEntity target) {
        if (messages.get().isEmpty()) return;

        String playerName = target.getName().getString();
        String template = messages.get().get(random.nextInt(messages.get().size()));
        String finalMessage = template.replace("[NAME]", playerName);

        // Robustness: Add bypass suffix if enabled
        if (antiSpamBypass.get()) {
            finalMessage += " " + getRandomString(3);
        }

        if (mc.getNetworkHandler() != null) {
            mc.player.networkHandler.sendChatMessage(finalMessage);
        }
    }

    /**
     * Generates a small random string to bypass duplicate message filters.
     */
    private String getRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return "[" + sb.toString() + "]";
    }

    /**
     * Removes old entries from the map to keep memory usage low.
     */
    private void cleanupMap() {
        if (lastInsultedMap.size() > 50) {
            long now = System.currentTimeMillis();
            long cooldownMs = playerCooldown.get() * 1000L;
            lastInsultedMap.entrySet().removeIf(entry -> (now - entry.getValue()) > cooldownMs);
        }
    }
}