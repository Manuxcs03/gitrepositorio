package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live advice for the player (SUGGEST mode): the best line and its closest alternatives, what each foe is
 * expected to do, which of our Pokemon may fall before moving, and how close the call is. Pure engine code, so
 * it is the same text the HUD shows and the log records.
 */
public final class Advisor {
    private Advisor() {}

    /** Below this gap to the best line, the alternative is practically as good. */
    public static final double CLOSE_CALL = 0.05;
    /** A foe action is shown when at least this likely. */
    private static final double SHOW_FOE = 0.2;
    /** A danger is shown from this chance on. */
    private static final double SHOW_DANGER = 0.35;

    public static final class Option {
        public final String label;
        /** Value difference to the best line (0 for the best, negative for worse ones). */
        public final double delta;

        Option(String label, double delta) {
            this.label = label;
            this.delta = delta;
        }
    }

    public static final class FoeMove {
        public final String foe;
        public final String action;
        public final int percent;

        FoeMove(String foe, String action, int percent) {
            this.foe = foe;
            this.action = action;
            this.percent = percent;
        }

        @Override
        public String toString() {
            return foe + ": " + action + " " + percent + "%";
        }
    }

    public static final class Danger {
        public final String mine;
        public final String foe;
        public final String move;
        public final int percent;
        /** The move was not shown yet: it is a likely guess from the foe's movepool. */
        public final boolean guessed;

        Danger(String mine, String foe, String move, int percent, boolean guessed) {
            this.mine = mine;
            this.foe = foe;
            this.move = move;
            this.percent = percent;
            this.guessed = guessed;
        }

        @Override
        public String toString() {
            return mine + " may fall before moving (" + move + (guessed ? "?" : "") + " from " + foe + ", " + percent + "%)";
        }
    }

    /** An inaccurate move in the recommended line whose miss leaves its user to a foe that can knock it out. */
    public static final class Gamble {
        public final String mine;
        public final String move;
        public final int missPercent;
        public final String foe;

        Gamble(String mine, String move, int missPercent, String foe) {
            this.mine = mine;
            this.move = move;
            this.missPercent = missPercent;
            this.foe = foe;
        }

        @Override
        public String toString() {
            return "if " + move + " misses (" + missPercent + "%), " + foe + " can knock out " + mine;
        }
    }

    public static final class Advice {
        public final List<Option> options = new ArrayList<>();
        public final List<FoeMove> foeMoves = new ArrayList<>();
        public final List<Danger> dangers = new ArrayList<>();
        public final List<Gamble> gambles = new ArrayList<>();
        /** The second best line is within {@link #CLOSE_CALL}: the player's read of the opponent matters here. */
        public boolean closeCall;
        /** Remaining HP of each side counted in Pokemon (a full one = 1; unseen foes count as full). */
        public double myMaterial, oppMaterial;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < options.size(); i++) {
                Option o = options.get(i);
                if (i > 0) sb.append(" | ");
                sb.append(o.label);
                if (i > 0) sb.append(String.format(Locale.ROOT, " (%+.2f)", o.delta));
            }
            if (closeCall) sb.append(" [close call]");
            if (!foeMoves.isEmpty()) sb.append(" || foe: ").append(foeMoves);
            if (!dangers.isEmpty()) sb.append(" || danger: ").append(dangers);
            if (!gambles.isEmpty()) sb.append(" || gamble: ").append(gambles);
            sb.append(String.format(Locale.ROOT, " || material %.1f vs %.1f", myMaterial, oppMaterial));
            return sb.toString();
        }
    }

    public static Advice advise(BattleState s, Planner.Plan plan) {
        Advice adv = new Advice();
        if (plan == null) return adv;
        for (int i = 0; i < Math.min(3, plan.ranking.size()); i++) {
            Planner.Scored sc = plan.ranking.get(i);
            adv.options.add(new Option(label(s, sc.actions), sc.value - plan.ranking.get(0).value));
        }
        if (adv.options.isEmpty() && !plan.actions.isEmpty()) adv.options.add(new Option(label(s, plan.actions), 0));
        adv.closeCall = adv.options.size() > 1 && adv.options.get(1).delta > -CLOSE_CALL;

        for (int slot = 0; slot < s.slots(); slot++) {
            Battler foe = s.opp(slot);
            if (foe == null || !foe.alive()) continue;
            List<OpponentModel.Weighted> pol = OpponentModel.policy(s, false, slot, s.oppKind);
            // Merge the same move on different targets only for display when no target is dominant.
            for (OpponentModel.Weighted w : pol) {
                if (w.prob() < SHOW_FOE) continue;
                adv.foeMoves.add(new FoeMove(foe.name, describeFoe(s, w.action()), (int) Math.round(100 * w.prob())));
            }
        }

        for (int slot = 0; slot < s.slots(); slot++) {
            Battler me = s.my(slot);
            if (me == null || !me.alive() || leavesField(plan, slot)) continue;
            Danger d = worstDanger(s, me);
            if (d != null) adv.dangers.add(d);
        }

        for (Action a : plan.actions) {
            Gamble g = gamble(s, a);
            if (g != null) adv.gambles.add(g);
        }

        for (Battler b : s.myTeam) if (b.alive()) adv.myMaterial += b.hpFrac();
        for (Battler b : s.oppTeam) if (b.alive()) adv.oppMaterial += b.hpFrac();
        adv.oppMaterial += Math.max(0, s.oppUnseenReserves);
        return adv;
    }

    /** The recommended line already takes this slot out of harm's way (switch or Protect). */
    private static boolean leavesField(Planner.Plan plan, int slot) {
        for (Action a : plan.actions) {
            if (a.slot != slot) continue;
            if (a.kind == Action.Kind.SWITCH) return true;
            if (a.kind == Action.Kind.MOVE && MoveDex.PROTECT.contains(a.executed().id)) return true;
        }
        return false;
    }

    private static String label(BattleState s, List<Action> actions) {
        List<String> parts = new ArrayList<>();
        for (Action a : actions) if (a.kind != Action.Kind.PASS) parts.add(a.describe(s));
        return parts.isEmpty() ? "Pass" : String.join(" + ", parts);
    }

    private static String describeFoe(BattleState s, Action a) {
        if (a.kind == Action.Kind.SWITCH) return "switch -> " + s.oppTeam.get(a.switchTo).name;
        if (a.kind != Action.Kind.MOVE) return "-";
        MoveInfo m = a.executed();
        String name = m + (m.revealed ? "" : "?");
        if (a.targetSlot >= 0 && !m.isSpread() && m.needsTargetChoice()) {
            Battler t = s.active(a.targetMine, a.targetSlot);
            if (t != null) return name + " -> " + t.name;
        }
        return name;
    }

    /** Misses this likely are worth a word (a 90% Heat Wave is not; a 75% Sleep Powder or 70% Hurricane is). */
    private static final double GAMBLE_MISS = 0.15;

    /**
     * The move bets on its accuracy to stop {@code target} (put it to sleep, paralyse it, knock it out) while that
     * target holds an attack that knocks the user out: a miss costs the user. (#4 Cira: a 75% Sleep Powder on
     * Arcanine missed and Flare Blitz took Venusaur.)
     */
    private static Gamble gamble(BattleState s, Action a) {
        if (a.kind != Action.Kind.MOVE || a.targetMine || a.targetSlot < 0) return null;
        MoveInfo m = a.executed();
        double miss = 1 - m.accuracy;
        if (miss < GAMBLE_MISS || MoveDex.PROTECT.contains(m.id)) return null;
        Battler me = s.my(a.slot), foe = s.opp(a.targetSlot);
        if (me == null || foe == null || !me.alive() || !foe.alive()) return null;
        for (MoveInfo fm : foe.moves) {
            if (Planner.threatWeight(foe, fm) < 0.5) continue;
            if (DamageCalc.calc(foe, me, fm, s.field, false, s.doubles, false, false).koChance(me.hp) < 0.5) continue;
            return new Gamble(me.name, m.toString(), (int) Math.round(100 * miss), foe.name);
        }
        return null;
    }

    /** The likeliest way a foe that moves first knocks {@code me} out this turn, if it is likely enough. */
    private static Danger worstDanger(BattleState s, Battler me) {
        double total = Planner.outrunKoChance(s, me);
        if (total < SHOW_DANGER) return null;
        Danger best = null;
        double bestW = 0;
        for (int k = 0; k < s.slots(); k++) {
            Battler foe = s.opp(k);
            if (foe == null || !foe.alive()) continue;
            boolean first = Speed.movesFirst(foe, me, s.field);
            for (MoveInfo m : foe.moves) {
                double w = Planner.threatWeight(foe, m);
                if (w <= bestW || !(first || Speed.priority(foe, m, s.field) > 0)) continue;
                if (DamageCalc.calc(foe, me, m, s.field, false, s.doubles, false, false).koChance(me.hp) < 0.5) continue;
                bestW = w;
                best = new Danger(me.name, foe.name, m.toString(), (int) Math.round(100 * total), !m.revealed);
            }
        }
        return best;
    }
}
