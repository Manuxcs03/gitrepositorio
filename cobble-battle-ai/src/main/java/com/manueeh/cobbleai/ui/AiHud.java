package com.manueeh.cobbleai.ui;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.manueeh.cobbleai.AiConfig;
import com.manueeh.cobbleai.BattleDriver;
import com.manueeh.cobbleai.bridge.StateBuilder;
import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Advisor;
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
    private static final int FOE = 0xFF9E9E;
    private static final int DANGER = 0xFF7043;
    /** The panel never grows wider than this (GUI pixels); longer lines wrap. */
    private static final int MAX_WIDTH = 380;

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
            Advisor.Advice adv = plan.advice;
            if (cfg.hudDetail && adv != null) {
                if (adv.closeCall) add(lines, colors, Component.translatable("cobblebattleai.hud.close_call"), GOLD);
                if (!adv.foeMoves.isEmpty()) {
                    List<String> parts = new ArrayList<>();
                    for (Advisor.FoeMove f : adv.foeMoves) parts.add(f.toString());
                    add(lines, colors, Component.translatable("cobblebattleai.hud.foe_moves", String.join(", ", parts)), FOE);
                }
                for (Advisor.Danger dg : adv.dangers) {
                    add(lines, colors, Component.translatable("cobblebattleai.hud.danger", dg.mine,
                        dg.move + (dg.guessed ? "?" : ""), dg.foe, dg.percent), DANGER);
                }
                add(lines, colors, Component.translatable("cobblebattleai.hud.material",
                    String.format(Locale.ROOT, "%.1f", adv.myMaterial), String.format(Locale.ROOT, "%.1f", adv.oppMaterial)), DARK_GREY);
            }
            if (d.phase() == BattleDriver.Phase.READY && cfg.mode == AiConfig.Mode.SUGGEST) {
                add(lines, colors, Component.translatable("cobblebattleai.hud.press_execute",
                    CobbleKeys.EXECUTE.getTranslatedKeyMessage()), GOLD);
            }
        }

        // Long lines (alternatives, foe moves) wrap instead of running off the screen.
        int maxWidth = Math.max(80, Math.min(MAX_WIDTH, g.guiWidth() - 8));
        List<Component> wrapped = new ArrayList<>();
        List<Integer> wrappedColors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            for (String part : wrap(font, lines.get(i).getString(), maxWidth)) {
                wrapped.add(Component.literal(part));
                wrappedColors.add(colors.get(i));
            }
        }
        lines = wrapped;
        colors = wrappedColors;
        int width = 0;
        for (Component c : lines) width = Math.max(width, font.width(c));
        width = Math.min(width, maxWidth);
        int x = (g.guiWidth() - width) / 2;
        int y = 2;
        int h = lines.size() * (font.lineHeight + 1) + 3;
        g.fill(x - 4, y - 1, x + width + 4, y + h, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + 1, colors.get(i), false);
            y += font.lineHeight + 1;
        }
    }

    /** Splits {@code text} at spaces into pieces no wider than {@code maxWidth} (continuations are indented). */
    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (font.width(Component.literal(text)) <= maxWidth) {
            out.add(text);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && font.width(Component.literal(next)) > maxWidth) {
                out.add(cur.toString());
                cur = new StringBuilder("   ").append(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static void add(List<Component> lines, List<Integer> colors, Component c, int color) {
        lines.add(c);
        colors.add(color);
    }
}
