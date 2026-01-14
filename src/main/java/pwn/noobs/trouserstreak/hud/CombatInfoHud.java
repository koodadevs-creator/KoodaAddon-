package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public class CombatInfoHud extends HudElement {
    public static final HudElementInfo<CombatInfoHud> INFO = new HudElementInfo<>(
            KoodaAddon.KOODA_HUD_GROUP,
            "combat-info",
            "Combat Info",
            "Displays counts of selected PvP items with high performance.",
            CombatInfoHud::new
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style");

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
            .name("items")
            .description("Select the items to display.")
            .defaultValue(
                    Items.TOTEM_OF_UNDYING,
                    Items.END_CRYSTAL,
                    Items.RESPAWN_ANCHOR,
                    Items.GLOWSTONE,
                    Items.OBSIDIAN,
                    Items.ENCHANTED_GOLDEN_APPLE,
                    Items.EXPERIENCE_BOTTLE,
                    Items.COBWEB
            )
            .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("The scale of the HUD.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderMax(3.0)
            .build()
    );

    private final Setting<Orientation> orientation = sgGeneral.add(new EnumSetting.Builder<Orientation>()
            .name("orientation")
            .description("How the items are arranged.")
            .defaultValue(Orientation.Vertical)
            .build()
    );

    private final Setting<Boolean> hideIfZero = sgGeneral.add(new BoolSetting.Builder()
            .name("hide-if-zero")
            .description("Hides the item if you don't have any in your inventory.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> lowThreshold = sgGeneral.add(new IntSetting.Builder()
            .name("low-threshold")
            .description("The count at which text turns warning color.")
            .defaultValue(5)
            .min(1)
            .sliderMax(64)
            .build()
    );

    // --- STYLE SETTINGS ---
    private final Setting<SettingColor> backgroundColor = sgStyle.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Color of the background.")
            .defaultValue(new SettingColor(20, 20, 20, 150))
            .build()
    );

    private final Setting<SettingColor> textColor = sgStyle.add(new ColorSetting.Builder()
            .name("text-color")
            .description("Text color when supplies are sufficient.")
            .defaultValue(new SettingColor(255, 255, 255))
            .build()
    );

    private final Setting<SettingColor> warningColor = sgStyle.add(new ColorSetting.Builder()
            .name("warning-color")
            .description("Text color when supplies are low.")
            .defaultValue(new SettingColor(255, 50, 50))
            .build()
    );

    // Render cache to avoid inventory scanning in the render loop
    private final List<RenderEntry> renderCache = new ArrayList<>();

    public CombatInfoHud() {
        super(INFO);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Rebuild cache only once per tick
        renderCache.clear();

        for (Item item : items.get()) {
            int count = getItemCount(item);

            // Skip if hidden
            if (hideIfZero.get() && count == 0) continue;

            // Add to cache
            renderCache.add(new RenderEntry(item.getDefaultStack(), String.valueOf(count), count));
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        // Fallback for Editor
        if (isInEditor() && renderCache.isEmpty()) {
            fillEditorCache();
        }

        if (renderCache.isEmpty()) return;

        double s = scale.get();
        double padding = 4 * s;
        double itemSize = 16 * s;
        double spacing = 2 * s;
        double gapBetweenItems = 4 * s;

        // 1. Calculate Dimensions
        double totalWidth = 0;
        double totalHeight = 0;

        if (orientation.get() == Orientation.Vertical) {
            // Vertical: Max Width of any row, Sum of Heights
            for (RenderEntry entry : renderCache) {
                double textW = renderer.textWidth(entry.text) * s;
                double rowW = itemSize + spacing + textW;
                if (rowW > totalWidth) totalWidth = rowW;
                totalHeight += itemSize + spacing;
            }
            // Remove last spacing
            if (!renderCache.isEmpty()) totalHeight -= spacing;

        } else {
            // Horizontal: Sum of Widths, Max Height
            totalHeight = itemSize; // Fixed height for items
            for (RenderEntry entry : renderCache) {
                double textW = renderer.textWidth(entry.text) * s;
                totalWidth += itemSize + spacing + textW + gapBetweenItems;
            }
            if (!renderCache.isEmpty()) totalWidth -= gapBetweenItems;
        }

        // Add padding
        double boxWidth = totalWidth + (padding * 2);
        double boxHeight = totalHeight + (padding * 2);

        // 2. Draw Background
        renderer.quad(x, y, boxWidth, boxHeight, backgroundColor.get());

        // 3. Draw Items
        double startX = x + padding;
        double startY = y + padding;

        double currentX = startX;
        double currentY = startY;

        for (RenderEntry entry : renderCache) {
            Color color = (entry.count <= lowThreshold.get()) ? warningColor.get() : textColor.get();
            double textW = renderer.textWidth(entry.text) * s;

            // Render Item
            renderer.item(entry.stack, (int)currentX, (int)currentY, (float)s, true);

            // Render Text
            double textX = currentX + itemSize + spacing;
            double textY = currentY + (itemSize / 2) - ((renderer.textHeight() * s) / 2);

            renderer.text(entry.text, textX, textY, color, true, s);

            // Advance position
            if (orientation.get() == Orientation.Vertical) {
                currentY += itemSize + spacing;
            } else {
                currentX += itemSize + spacing + textW + gapBetweenItems;
            }
        }

        setSize(boxWidth, boxHeight);
    }

    private int getItemCount(Item item) {
        if (mc.player == null) return 0;

        // Robust count: Checks main inventory
        int count = mc.player.getInventory().count(item);

        // Explicitly check cursor stack if user is dragging an item
        if (mc.player.currentScreenHandler != null && mc.player.currentScreenHandler.getCursorStack().getItem() == item) {
            count += mc.player.currentScreenHandler.getCursorStack().getCount();
        }

        return count;
    }

    private void fillEditorCache() {
        renderCache.clear();
        for (Item item : items.get()) {
            renderCache.add(new RenderEntry(item.getDefaultStack(), "64", 64));
        }
        if (renderCache.isEmpty()) {
            renderCache.add(new RenderEntry(Items.TOTEM_OF_UNDYING.getDefaultStack(), "64", 64));
        }
    }

    private static class RenderEntry {
        final ItemStack stack;
        final String text;
        final int count;

        public RenderEntry(ItemStack stack, String text, int count) {
            this.stack = stack;
            this.text = text;
            this.count = count;
        }
    }

    public enum Orientation {
        Vertical,
        Horizontal
    }
}