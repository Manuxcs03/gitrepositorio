package com.manueeh.cobbleai.ui;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.manueeh.cobbleai.AiConfig;
import com.manueeh.cobbleai.BattleDriver;
import com.manueeh.cobbleai.bridge.StateBuilder;
import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Planner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small panel at the top centre of the screen showing what the AI is doing. */
public final class AiHud {
    private AiHud() {}

    private static final int GREEN = 0x7CFC9A;
    private static final int GREY = 0xBBBBBB;
    private static final int DARK_GREY = 0x888888;
    private static final int GOLD = 0xFFD166;

    public static void render(GuiGraphics g) {
        AiConfig cfg = AiConfig.get();
        if (!cfg.showHud) return;
        if (CobblemonClient.INSTANCE.getBattle() == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        BattleDriver d = BattleDriver.INSTANCE;

        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        add(lines, colors, Component.translatable("cobblebattleai.hud.title",
            Component.translatable("cobblebattleai.mode." + cfg.mode.name().toLowerCase(Locale.ROOT))), 0xFFFFFF);
        switch (d.phase()) {
            case WAITING -> add(lines, colors, Component.translatable("cobblebattleai.hud.waiting"), GREY);
            case THINKING -> add(lines, colors, Component.translatable("cobblebattleai.hud.thinking"), GREY);
            case ERROR -> add(lines, colors, Component.translatable("cobblebattleai.hud.error", String.valueOf(d.lastError())), 0xFF6B6B);
            default -> { }
        }
        Planner.Plan plan = d.plan();
        StateBuilder.Snapshot snap = d.snapshot();
        if (plan != null && snap != null && (d.phase() == BattleDriver.Phase.READY || d.phase() == BattleDriver.Phase.SENT)) {
            StringBuilder sb = new StringBuilder();
            for (Action a : plan.actions) {
                if (sb.length() > 0) sb.append("  |  ");
                sb.append(a.describe(snap.state));
            }
            add(lines, colors, Component.literal("> " + sb), GREEN);
            for (int i = 0; i < Math.min(3, plan.notes.size()); i++) {
                add(lines, colors, Component.literal("  " + plan.notes.get(i)), GREY);
            }
            if (plan.ranking.size() > 1) {
                StringBuilder alt = new StringBuilder();
                for (int i = 1; i < Math.min(3, plan.ranking.size()); i++) {
                    Planner.Scored sc = plan.ranking.get(i);
                    if (alt.length() > 0) alt.append(", ");
                    List<String> parts = new ArrayList<>();
                    for (Action a : sc.actions) parts.add(a.describe(snap.state));
                    alt.append(String.join(" + ", parts))
                        .append(String.format(Locale.ROOT, " (%+.2f)", sc.value - plan.value));
                }
                add(lines, colors, Component.translatable("cobblebattleai.hud.alternatives", alt.toString()), DARK_GREY);
            }
            if (d.phase() == BattleDriver.Phase.READY && cfg.mode == AiConfig.Mode.SUGGEST) {
                add(lines, colors, Component.translatable("cobblebattleai.hud.press_execute",
                    CobbleKeys.EXECUTE.getTranslatedKeyMessage()), GOLD);
            }
        }

        int width = 0;
        for (Component c : lines) width = Math.max(width, font.width(c));
        width = Math.min(width, g.guiWidth() - 8);
        int x = (g.guiWidth() - width) / 2;
        int y = 2;
        int h = lines.size() * (font.lineHeight + 1) + 3;
        g.fill(x - 4, y - 1, x + width + 4, y + h, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + 1, colors.get(i), false);
            y += font.lineHeight + 1;
        }
    }

    private static void add(List<Component> lines, List<Integer> colors, Component c, int color) {
        lines.add(c);
        colors.add(color);
    }
}
