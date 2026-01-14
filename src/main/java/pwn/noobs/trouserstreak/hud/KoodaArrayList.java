package pwn.noobs.trouserstreak.hud;

import meteordevelopment.meteorclient.systems.hud.HudBox;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import pwn.noobs.trouserstreak.KoodaAddon;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KoodaArrayList extends HudElement {
    public static final HudElementInfo<KoodaArrayList> INFO = new HudElementInfo<>(KoodaAddon.KOODA_HUD_GROUP, "kooda-arraylist", "Displays active Kooda modules.", KoodaArrayList::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
            .name("color")
            .description("Color of the text.")
            .defaultValue(new SettingColor(0, 255, 255, 255))
            .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
            .name("shadow")
            .description("Renders shadow behind text.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> animationSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("animation-speed")
            .description("Speed of the slide animation.")
            .defaultValue(10.0)
            .min(1.0)
            .max(20.0)
            .build()
    );

    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
            .name("sort-mode")
            .description("How to sort the modules.")
            .defaultValue(SortMode.Longest)
            .build()
    );

    private final Map<Module, Double> animations = new HashMap<>();
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private Field boxXField;
    private Field boxYField;
    private Field boxWidthField;

    public KoodaArrayList() {
        super(INFO);
        try {
            boxXField = HudBox.class.getDeclaredField("x");
            boxXField.setAccessible(true);

            boxYField = HudBox.class.getDeclaredField("y");
            boxYField.setAccessible(true);

            boxWidthField = HudBox.class.getDeclaredField("width");
            boxWidthField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        List<Module> modules = Modules.get().getAll().stream()
                .filter(m -> m.category.name.startsWith("Kooda"))
                .collect(Collectors.toList());

        if (sortMode.get() == SortMode.Alphabetical) {
            modules.sort(Comparator.comparing(m -> m.title));
        } else {
            modules.sort((m1, m2) -> Double.compare(renderer.textWidth(m2.title), renderer.textWidth(m1.title)));
        }

        double delta = renderer.delta;

        int boxX = getBoxX();
        int boxY = getBoxY();
        int boxWidth = getBoxWidth();

        double yOffset = 0;
        boolean alignRight = boxX > mc.getWindow().getScaledWidth() / 2.0;

        for (Module module : modules) {
            double currentAnim = animations.getOrDefault(module, 0.0);
            double targetAnim = module.isActive() ? 1.0 : 0.0;

            if (currentAnim < targetAnim) {
                currentAnim = Math.min(targetAnim, currentAnim + delta * animationSpeed.get());
            } else if (currentAnim > targetAnim) {
                currentAnim = Math.max(targetAnim, currentAnim - delta * animationSpeed.get());
            }

            animations.put(module, currentAnim);

            if (currentAnim > 0.01) {
                String text = module.title;
                double textWidth = renderer.textWidth(text);
                double textHeight = renderer.textHeight(true);

                double xOffset;
                if (alignRight) {
                    xOffset = boxWidth - textWidth + (textWidth * (1.0 - currentAnim));
                } else {
                    xOffset = -(textWidth * (1.0 - currentAnim));
                }

                renderer.text(text, boxX + xOffset, boxY + yOffset, color.get(), shadow.get());
                yOffset += textHeight * currentAnim;
            }
        }

        double totalWidth = 0;
        double calcHeight = 0;

        for (Module module : modules) {
            if (module.isActive() || animations.getOrDefault(module, 0.0) > 0.01) {
                totalWidth = Math.max(totalWidth, renderer.textWidth(module.title));
                calcHeight += renderer.textHeight(true) * animations.getOrDefault(module, 0.0);
            }
        }

        box.setSize((int) totalWidth, (int) calcHeight);
    }


    private int getBoxX() {
        try {
            return boxXField != null ? boxXField.getInt(box) : 0;
        } catch (Exception e) { return 0; }
    }

    private int getBoxY() {
        try {
            return boxYField != null ? boxYField.getInt(box) : 0;
        } catch (Exception e) { return 0; }
    }

    private int getBoxWidth() {
        try {
            return boxWidthField != null ? boxWidthField.getInt(box) : 0;
        } catch (Exception e) { return 0; }
    }

    public enum SortMode {
        Alphabetical,
        Longest
    }
}