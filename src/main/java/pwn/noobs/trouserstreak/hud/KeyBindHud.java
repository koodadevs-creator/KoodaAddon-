package pwn.noobs.trouserstreak.hud;

import pwn.noobs.trouserstreak.KoodaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KeyBindHud extends HudElement {
    public static final HudElementInfo<KeyBindHud> INFO = new HudElementInfo<>(
            KoodaAddon.KOODA_HUD_GROUP,
            "keybind-hud",
            "KeyBind HUD",
            "Displays modules with keybinds in a unified list.",
            KeyBindHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("The scale of the text.")
            .defaultValue(1.0)
            .min(0.5)
            .max(3.0)
            .sliderMax(3.0)
            .build()
    );

    private final Setting<Boolean> onlyActive = sgGeneral.add(new BoolSetting.Builder()
            .name("only-active")
            .description("Only displays modules that are currently toggled ON.")
            .defaultValue(false)
            .build()
    );

    private final Setting<RenderMode> renderMode = sgGeneral.add(new EnumSetting.Builder<RenderMode>()
            .name("render-mode")
            .description("Compact removes spacing to create a joined list look.")
            .defaultValue(RenderMode.Compact)
            .build()
    );

    private final Setting<LayoutMode> layoutMode = sgGeneral.add(new EnumSetting.Builder<LayoutMode>()
            .name("layout-mode")
            .description("The format of the text display.")
            .defaultValue(LayoutMode.KeyFirst)
            .build()
    );

    private final Setting<Boolean> chroma = sgStyle.add(new BoolSetting.Builder()
            .name("chroma-text")
            .description("Applies a rainbow effect to the text.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> chromaSpeed = sgStyle.add(new DoubleSetting.Builder()
            .name("chroma-speed")
            .description("The speed of the chroma animation.")
            .defaultValue(2.0)
            .visible(chroma::get)
            .build()
    );

    private final Setting<SettingColor> activeColor = sgStyle.add(new ColorSetting.Builder()
            .name("active-color")
            .description("Color of the module name when it is ENABLED.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 255))
            .visible(() -> !chroma.get())
            .build()
    );

    private final Setting<SettingColor> inactiveColor = sgStyle.add(new ColorSetting.Builder()
            .name("inactive-color")
            .description("Color of the module name when it is DISABLED.")
            .defaultValue(new SettingColor(170, 170, 170))
            .visible(() -> !chroma.get())
            .build()
    );

    private final Setting<SettingColor> keybindColor = sgStyle.add(new ColorSetting.Builder()
            .name("keybind-color")
            .description("Color of the brackets and key name.")
            .defaultValue(new SettingColor(100, 100, 100))
            .build()
    );

    private final Setting<Boolean> drawBackground = sgStyle.add(new BoolSetting.Builder()
            .name("background")
            .description("Draws a background behind the text.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> backgroundColor = sgStyle.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Color of the background.")
            .defaultValue(new SettingColor(20, 20, 20, 150))
            .visible(drawBackground::get)
            .build()
    );

    private final Setting<Boolean> drawOutline = sgStyle.add(new BoolSetting.Builder()
            .name("outline")
            .description("Draws an outline around the entries.")
            .defaultValue(false)
            .visible(drawBackground::get)
            .build()
    );

    private final Setting<OutlineMode> outlineMode = sgStyle.add(new EnumSetting.Builder<OutlineMode>()
            .name("outline-mode")
            .description("Controls which sides of the outline are drawn.")
            .defaultValue(OutlineMode.SidesOnly)
            .visible(drawOutline::get)
            .build()
    );

    private final Setting<SettingColor> outlineColor = sgStyle.add(new ColorSetting.Builder()
            .name("outline-color")
            .description("Color of the outline.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 255))
            .visible(drawOutline::get)
            .build()
    );

    private final Setting<Boolean> sideBar = sgStyle.add(new BoolSetting.Builder()
            .name("sidebar")
            .description("Draws a vertical line on the side of the HUD.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> sideBarColor = sgStyle.add(new ColorSetting.Builder()
            .name("sidebar-color")
            .description("Color of the sidebar.")
            .defaultValue(new SettingColor(KoodaAddon.KOODA_COLOR.r, KoodaAddon.KOODA_COLOR.g, KoodaAddon.KOODA_COLOR.b, 255))
            .visible(sideBar::get)
            .build()
    );

    private final List<ModuleDisplayInfo> renderList = new ArrayList<>();

    public KeyBindHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        renderList.clear();

        boolean isCompact = renderMode.get() == RenderMode.Compact;

        double s = scale.get();
        double innerSpacing = 3.0 * s;
        double paddingX = 4.0 * s;
        double sideBarWidth = 2.0 * s;

        double lineSpacing = isCompact ? 0.0 : (2.0 * s);
        double textHeight = renderer.textHeight() * s;

        double rowHeight = isCompact ? (textHeight + (2 * s)) : (textHeight + 2);

        for (Module module : Modules.get().getAll()) {
            if (module.keybind.isSet()) {
                if (onlyActive.get() && !module.isActive()) continue;

                String moduleName = module.title;
                String bindText = "[" + module.keybind.toString() + "]";

                double nameW = renderer.textWidth(moduleName) * s;
                double bindW = renderer.textWidth(bindText) * s;
                double totalW = nameW + bindW + innerSpacing;

                renderList.add(new ModuleDisplayInfo(module, moduleName, bindText, nameW, bindW, totalW));
            }
        }

        if (renderList.isEmpty()) {
            if (isInEditor()) {
                String placeholder = "KeyBind HUD (Empty)";
                double pWidth = renderer.textWidth(placeholder) * s;
                double pHeight = renderer.textHeight() * s;
                renderer.text(placeholder, x, y, inactiveColor.get(), true, s);
                setSize(pWidth, pHeight);
            }
            return;
        }

        renderList.sort(Comparator.comparingDouble(ModuleDisplayInfo::getTotalWidth).reversed());

        boolean alignRight = getX() + (getWidth() / 2) > MinecraftClient.getInstance().getWindow().getScaledWidth() / 2.0;
        double maxTextWidth = renderList.get(0).getTotalWidth();

        double currentY = 0;

        for (int i = 0; i < renderList.size(); i++) {
            ModuleDisplayInfo info = renderList.get(i);

            double drawX;
            double bgX;
            double barX;
            double sideBarOffset = sideBar.get() ? (sideBarWidth + 2) : 0;

            if (alignRight) {
                drawX = x + (maxTextWidth - info.totalWidth) - sideBarOffset;
                bgX = drawX - (paddingX / 2);
                barX = x + maxTextWidth + (paddingX / 2);
            } else {
                drawX = x + sideBarOffset;
                bgX = drawX - (paddingX / 2);
                barX = x;
            }

            Color nameColor;
            if (chroma.get()) {
                nameColor = getChromaColor(i);
            } else {
                nameColor = info.module.isActive() ? activeColor.get() : inactiveColor.get();
            }

            double itemHeight = rowHeight;
            double bgY = y + currentY;

            if (drawBackground.get()) {
                double bgWidth = info.totalWidth + paddingX;

                renderer.quad(bgX, bgY, bgWidth, itemHeight, backgroundColor.get());

                if (drawOutline.get()) {
                    double th = 1.0 * s;
                    Color outColor = outlineColor.get();
                    OutlineMode mode = outlineMode.get();

                    if (mode == OutlineMode.Full || mode == OutlineMode.SidesOnly) {
                        renderer.quad(bgX, bgY, th, itemHeight, outColor);
                        renderer.quad(bgX + bgWidth - th, bgY, th, itemHeight, outColor);
                    }

                    if (mode == OutlineMode.Full) {
                        if (!isCompact || i == 0) {
                            renderer.quad(bgX, bgY, bgWidth, th, outColor);
                        }
                        renderer.quad(bgX, bgY + itemHeight - th, bgWidth, th, outColor);
                    }
                }
            }

            if (sideBar.get()) {
                Color barColor = chroma.get() ? getChromaColor(i) : sideBarColor.get();
                renderer.quad(barX, bgY, sideBarWidth, itemHeight, barColor);
            }

            double textY = y + currentY + ((itemHeight - textHeight) / 2) - (s * 0.5);

            if (layoutMode.get() == LayoutMode.KeyFirst) {
                renderer.text(info.bind, drawX, textY, keybindColor.get(), true, s);
                renderer.text(info.name, drawX + info.bindWidth + innerSpacing, textY, nameColor, true, s);
            } else {
                renderer.text(info.name, drawX, textY, nameColor, true, s);
                renderer.text(info.bind, drawX + info.nameWidth + innerSpacing, textY, keybindColor.get(), true, s);
            }

            currentY += itemHeight + lineSpacing;
        }

        double totalWidth = maxTextWidth + paddingX + (sideBar.get() ? sideBarWidth + 2 : 0);
        if (totalWidth < 20) totalWidth = 20;
        if (currentY < 10) currentY = 10;
        setSize(totalWidth, currentY);
    }

    private Color getChromaColor(int index) {
        double speed = chromaSpeed.get() * 0.4;
        double offset = index * 100;
        long time = System.currentTimeMillis();
        float hue = (float) ((time * speed + offset) % 3000) / 3000F;
        return new Color(java.awt.Color.HSBtoRGB(hue, 1.0F, 1.0F));
    }

    private static class ModuleDisplayInfo {
        Module module;
        String name;
        String bind;
        double nameWidth;
        double bindWidth;
        double totalWidth;

        public ModuleDisplayInfo(Module module, String name, String bind, double nameWidth, double bindWidth, double totalWidth) {
            this.module = module;
            this.name = name;
            this.bind = bind;
            this.nameWidth = nameWidth;
            this.bindWidth = bindWidth;
            this.totalWidth = totalWidth;
        }
        public double getTotalWidth() { return totalWidth; }
    }

    public enum LayoutMode {
        KeyFirst,
        NameFirst
    }

    public enum RenderMode {
        Spaced,
        Compact
    }

    public enum OutlineMode {
        Full,
        SidesOnly
    }
}