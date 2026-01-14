package pwn.noobs.trouserstreak.modules;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoToxic extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAutoEz = settings.createGroup("Auto EZ (Kills)");
    private final SettingGroup sgAutoPop = settings.createGroup("Auto Pop (Totems)");
    private final SettingGroup sgAutoExcuse = settings.createGroup("Auto Excuse (Deaths)");

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Prevents sending toxic messages to friends.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> minDelay = sgGeneral.add(new DoubleSetting.Builder()
            .name("min-delay")
            .description("Minimum delay in seconds between messages to avoid spam kicks.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderMax(5.0)
            .build()
    );

    private final Setting<Boolean> randomOrder = sgGeneral.add(new BoolSetting.Builder()
            .name("random-order")
            .description("Selects messages randomly from the list. If off, it cycles sequentially.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> enableEz = sgAutoEz.add(new BoolSetting.Builder()
            .name("enable-auto-ez")
            .description("Sends a message when you kill a player.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> targetTimeout = sgAutoEz.add(new DoubleSetting.Builder()
            .name("kill-credit-timeout")
            .description("Time in seconds after hitting a player to claim the kill.")
            .defaultValue(3.5)
            .min(0.5)
            .sliderMax(10.0)
            .visible(enableEz::get)
            .build()
    );

    private final Setting<List<String>> ezMessages = sgAutoEz.add(new StringListSetting.Builder()
            .name("kill-messages")
            .description("Messages to send on kill. Placeholders: {player}, {pops}, {ping}, {hp}, {my_hp}, {weapon}.")
            .defaultValue(List.of(
                    "I just killed {player} after popping {pops} totems! EZ!",
                    "Sit down {player}, Kooda Addon owns you.",
                    "Imagine dying to me {player}, couldn't be me.",
                    "Trash aim {player}, go back to vanilla.",
                    "Nice try {player}, but {my_hp} HP left is enough for you."
            ))
            .visible(enableEz::get)
            .build()
    );

    private final Setting<Boolean> enablePop = sgAutoPop.add(new BoolSetting.Builder()
            .name("enable-auto-pop")
            .description("Sends a message when a player pops a totem.")
            .defaultValue(true)
            .build()
    );

    private final Setting<List<String>> popMessages = sgAutoPop.add(new StringListSetting.Builder()
            .name("pop-messages")
            .description("Messages to send on totem pop. Placeholders: {player}, {pops}, {ping}.")
            .defaultValue(List.of(
                    "{player} popped {pops} times!",
                    "{player} keep popping like a good boy.",
                    "Nice totem {player}, do you have more?",
                    "Pop {pops}! You are running out of totems {player}!"
            ))
            .visible(enablePop::get)
            .build()
    );

    private final Setting<Boolean> enableExcuse = sgAutoExcuse.add(new BoolSetting.Builder()
            .name("enable-auto-excuse")
            .description("Sends a message when you die.")
            .defaultValue(true)
            .build()
    );

    private final Setting<List<String>> excuseMessages = sgAutoExcuse.add(new StringListSetting.Builder()
            .name("excuse-messages")
            .description("Messages to send on death.")
            .defaultValue(List.of(
                    "Not in my Main...",
                    "My cat walked on my keyboard!",
                    "Bro the lag is insane.",
                    "My game froze, lucky kill.",
                    "Desync hit hard there.",
                    "I was tabbed out watching YouTube."
            ))
            .visible(enableExcuse::get)
            .build()
    );

    private final Map<UUID, Integer> popCounts = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private PlayerEntity currentTarget = null;
    private long lastAttackTime = 0;
    private long lastMessageTime = 0;

    private int ezIndex = 0;
    private int popIndex = 0;
    private int excuseIndex = 0;

    public AutoToxic() {
        super(KoodaAddon.KOODA_COMBAT, "auto-toxic", "Handles toxicity automatically (Kills, Pops, Deaths).");
    }

    @Override
    public void onActivate() {
        popCounts.clear();
        currentTarget = null;
        ezIndex = 0;
        popIndex = 0;
        excuseIndex = 0;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        popCounts.clear();
        currentTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (currentTarget != null && System.currentTimeMillis() - lastAttackTime > targetTimeout.get() * 1000) {
            currentTarget = null;
        }

        if (mc.world != null && mc.player != null && mc.player.age % 100 == 0) {
            popCounts.keySet().removeIf(uuid -> mc.world.getPlayerByUuid(uuid) == null);
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof PlayerEntity player) {
            currentTarget = player;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof EntityStatusS2CPacket packet) {
            Entity entity = packet.getEntity(mc.world);

            if (!(entity instanceof PlayerEntity player)) return;

            if (packet.getStatus() == 35) {
                if (player.equals(mc.player)) return;

                UUID uuid = player.getUuid();
                int count = popCounts.getOrDefault(uuid, 0) + 1;
                popCounts.put(uuid, count);

                if (enablePop.get()) {
                    sendMessage(popMessages, player, count, false);
                }
            }

            if (packet.getStatus() == 3) {
                if (player.equals(mc.player)) {
                    popCounts.clear();
                    currentTarget = null;

                    if (enableExcuse.get()) {
                        sendMessage(excuseMessages, null, 0, true);
                    }
                }
                else {
                    if (enableEz.get() && currentTarget != null && currentTarget.equals(player)) {
                        int finalPops = popCounts.getOrDefault(player.getUuid(), 0);
                        sendMessage(ezMessages, player, finalPops, false);

                        currentTarget = null;
                    }
                    popCounts.remove(player.getUuid());
                }
            }
        }
    }

    private void sendMessage(Setting<List<String>> setting, PlayerEntity target, int pops, boolean isExcuse) {
        List<String> messages = setting.get();
        if (messages.isEmpty()) return;

        if (System.currentTimeMillis() - lastMessageTime < minDelay.get() * 1000) return;

        if (!isExcuse && target != null && ignoreFriends.get() && Friends.get().isFriend(target)) {
            return;
        }

        String rawMessage = getNextMessage(setting, isExcuse);

        String finalMessage = formatMessage(rawMessage, target, pops);
        ChatUtils.sendPlayerMsg(finalMessage);

        lastMessageTime = System.currentTimeMillis();
    }

    private String getNextMessage(Setting<List<String>> setting, boolean isExcuse) {
        List<String> messages = setting.get();

        if (randomOrder.get()) {
            return messages.get(random.nextInt(messages.size()));
        }

        if (isExcuse) {
            return messages.get(excuseIndex++ % messages.size());
        } else if (setting == popMessages) {
            return messages.get(popIndex++ % messages.size());
        } else {
            return messages.get(ezIndex++ % messages.size());
        }
    }

    private String formatMessage(String text, PlayerEntity target, int pops) {
        String result = text;

        if (target != null) {
            String name = target.getName().getString();

            int ping = 0;
            if (mc.getNetworkHandler() != null) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(target.getUuid());
                if (entry != null) {
                    ping = entry.getLatency();
                }
            }

            int hp = (int) (target.getHealth() + target.getAbsorptionAmount());

            result = result.replace("{player}", name);
            result = result.replace("{name}", name);
            result = result.replace("{ping}", String.valueOf(ping));
            result = result.replace("{hp}", String.valueOf(hp));
        }

        result = result.replace("{pops}", String.valueOf(pops));
        result = result.replace("{random}", String.valueOf(random.nextInt(100)));

        if (mc.player != null) {
            int myHp = (int) (mc.player.getHealth() + mc.player.getAbsorptionAmount());
            String weapon = mc.player.getMainHandStack().getName().getString();

            result = result.replace("{my_hp}", String.valueOf(myHp));
            result = result.replace("{weapon}", weapon);
        }

        if (mc.getCurrentServerEntry() != null) {
            result = result.replace("{server}", mc.getCurrentServerEntry().address);
        }

        return result;
    }
}