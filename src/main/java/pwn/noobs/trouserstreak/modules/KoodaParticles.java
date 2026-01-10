package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import pwn.noobs.trouserstreak.KoodaAddon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KoodaParticles extends Module {
    public enum Mode {
        Snow,
        Matrix,
        Bubbles,
        Constellation
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");

    private final Setting<Mode> particleMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("The style of particles.")
            .defaultValue(Mode.Snow)
            .build()
    );

    private final Setting<Integer> particleCount = sgGeneral.add(new IntSetting.Builder()
            .name("count")
            .description("Amount of particles.")
            .defaultValue(10)
            .min(10)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Double> particleSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Movement speed.")
            .defaultValue(100)
            .min(0)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Double> particleSize = sgGeneral.add(new DoubleSetting.Builder()
            .name("size")
            .description("Base size of particles.")
            .defaultValue(2)
            .min(0.5)
            .max(10)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> lineDistance = sgGeneral.add(new IntSetting.Builder()
            .name("line-distance")
            .description("Max distance to connect lines (Constellation).")
            .defaultValue(100)
            .min(10)
            .max(300)
            .sliderMax(300)
            .visible(() -> particleMode.get() == Mode.Constellation)
            .build()
    );

    private final Setting<SettingColor> color = sgAppearance.add(new ColorSetting.Builder()
            .name("color")
            .description("Particle color.")
            .defaultValue(new SettingColor(255, 255, 255, 150))
            .build()
    );

    private final Setting<SettingColor> color2 = sgAppearance.add(new ColorSetting.Builder()
            .name("secondary-color")
            .description("Secondary color (Matrix/Bubbles).")
            .defaultValue(new SettingColor(0, 255, 0, 200))
            .visible(() -> particleMode.get() == Mode.Matrix || particleMode.get() == Mode.Bubbles)
            .build()
    );

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    public KoodaParticles() {
        super(KoodaAddon.KOODA_RENDER, "kooda-particles", "Advanced GUI particle system.");
    }

    @Override
    public void onDeactivate() {
        particles.clear();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.currentScreen == null || !(mc.currentScreen instanceof WidgetScreen) || event.drawContext == null) return;

        updateParticles();
        renderParticles(event.drawContext);
    }

    private void updateParticles() {
        int targetCount = particleCount.get();
        if (particles.size() < targetCount) {
            for (int i = 0; i < targetCount - particles.size(); i++) {
                particles.add(new Particle());
            }
        } else if (particles.size() > targetCount) {
            particles.subList(targetCount, particles.size()).clear();
        }

        double speedFactor = particleSpeed.get() / 100.0;
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        long time = System.currentTimeMillis();

        for (Particle p : particles) {
            switch (particleMode.get()) {
                case Snow, Matrix -> {
                    p.y += p.speed * speedFactor;
                    p.x += Math.sin((time / 1000.0) * p.swaySpeed + p.offset) * 0.5;
                    if (p.y > height) resetParticle(p, width, height, true);
                }
                case Bubbles -> {
                    p.y -= p.speed * speedFactor * 0.5;
                    p.x += Math.sin((time / 500.0) * p.swaySpeed + p.offset);
                    if (p.y < -10) resetParticle(p, width, height, false);
                }
                case Constellation -> {
                    p.x += p.velX * speedFactor * 0.5;
                    p.y += p.velY * speedFactor * 0.5;
                    if (p.x <= 0 || p.x >= width) p.velX *= -1;
                    if (p.y <= 0 || p.y >= height) p.velY *= -1;
                }
            }
        }
    }

    private void renderParticles(DrawContext context) {
        int c1 = color.get().getPacked();
        int c2 = color2.get().getPacked();
        int lineDistSq = lineDistance.get() * lineDistance.get();


        if (particleMode.get() == Mode.Constellation) {
            int r = color.get().r;
            int g = color.get().g;
            int b = color.get().b;
            int baseA = color.get().a;

            for (int i = 0; i < particles.size(); i++) {
                Particle p1 = particles.get(i);
                for (int j = i + 1; j < particles.size(); j++) {
                    Particle p2 = particles.get(j);
                    double dx = p1.x - p2.x;
                    double dy = p1.y - p2.y;
                    double distSq = dx * dx + dy * dy;

                    if (distSq < lineDistSq) {
                        float alphaFactor = 1.0f - (float) (distSq / lineDistSq);
                        int alpha = (int) (baseA * alphaFactor);

                        int lineColor = (alpha << 24) | (r << 16) | (g << 8) | b;


                        drawLineSafe(context, (int)p1.x, (int)p1.y, (int)p2.x, (int)p2.y, lineColor);
                    }
                }
            }
        }


        for (Particle p : particles) {
            int size = (int) (p.sizeMod * particleSize.get());
            int x = (int) p.x;
            int y = (int) p.y;

            switch (particleMode.get()) {
                case Snow -> context.fill(x, y, x + size, y + size, c1);
                case Matrix -> {
                    if (random.nextInt(40) == 0) p.text = random.nextBoolean() ? "1" : "0";
                    context.drawText(mc.textRenderer, p.text, x, y, c2, true);
                }
                case Bubbles -> {
                    int bubbleColor = (p.offset > 3) ? c1 : c2;
                    context.fill(x, y, x + size, y + size, bubbleColor);
                }
                case Constellation -> context.fill(x - 1, y - 1, x + 1, y + 1, c1);
            }
        }
    }


    private void drawLineSafe(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            context.fill(x1, y1, x1 + 1, y1 + 1, color);

            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err = err - dy;
                x1 = x1 + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                y1 = y1 + sy;
            }
        }
    }

    private void resetParticle(Particle p, int width, int height, boolean topToBottom) {
        p.x = random.nextDouble() * width;
        p.y = topToBottom ? -random.nextInt(50) : height + random.nextInt(50);
        p.speed = random.nextDouble() * 2 + 1;
        p.sizeMod = random.nextDouble() + 0.5;
        p.text = random.nextBoolean() ? "1" : "0";
    }

    private class Particle {
        double x, y;
        double speed, swaySpeed, offset, sizeMod;
        double velX, velY;
        String text;

        Particle() {
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            x = random.nextDouble() * w;
            y = random.nextDouble() * h;
            speed = random.nextDouble() * 2 + 1;
            swaySpeed = random.nextDouble() * 0.5 + 0.1;
            offset = random.nextDouble() * Math.PI * 2;
            sizeMod = random.nextDouble() + 0.5;
            velX = (random.nextDouble() - 0.5) * 2;
            velY = (random.nextDouble() - 0.5) * 2;
            text = random.nextBoolean() ? "1" : "0";
        }
    }
}