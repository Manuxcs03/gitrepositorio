package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.data.ItemDex;
import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chooses our actions: enumerates candidates per slot, simulates them against the predicted
 * opponent responses and keeps the best expected evaluation.
 */
public final class Planner {

    /** What Showdown allows a slot to do this turn. */
    public static final class SlotRequest {
        public int slot;
        public boolean pass;
        public boolean forceSwitch;
        public boolean trapped;
        public List<MoveInfo> moves = new ArrayList<>();
        public List<Integer> switchOptions = new ArrayList<>();
        public boolean canMega;
        public boolean canUltraBurst;
        public boolean canTera;
        public boolean canDynamax;
        /** Mega / Ultra Burst form of the active Pokemon, when it can transform this turn. */
        public Battler megaForm;
        /** Base move id -> Z-move replacement. */
        public Map<String, MoveInfo> zMoves = new HashMap<>();
        /** Base move id -> Max move replacement. */
        public Map<String, MoveInfo> maxMoves = new HashMap<>();
    }

    public static final class Options {
        public boolean useTera = true;
        public boolean useMega = true;
        public boolean useZ = true;
        public boolean useDynamax = false;
        public boolean allowSwitching = true;
        /** 0 = trust the predicted distribution, 1 = assume the worst response. */
        public double riskAversion = 0.35;
    }

    public static final class Scored {
        public final List<Action> actions;
        public final double value;

        Scored(List<Action> actions, double value) {
            this.actions = actions;
            this.value = value;
        }
    }

    public static final class Plan {
        /** One action per requested slot, in request order. */
        public final List<Action> actions = new ArrayList<>();
        public double value;
        public final List<Scored> ranking = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public long micros;
        /** Live advice for the HUD (filled by the driver after deciding; null inside the search). */
        public Advisor.Advice advice;
    }

    /** Candidate actions per slot kept for the joint search (after quick pruning). */
    private static final int MAX_PER_SLOT = 9;

    /** Responses rarer than this do not count as "the worst case" (a 6% target switch is not a plan). */
    private static final double MIN_WORST_PROB = 0.12;

    private final Options opt;
    /** Extra turns searched for close calls (0 inside the nested search). */
    private int lookahead = 2;
    /** Candidate actions per slot kept for the joint search. */
    private int perSlotCap = MAX_PER_SLOT;
    /** Foe responses kept per foe slot. */
    private int oppPerSlot = 4;

    public Planner(Options opt) {
        this.opt = opt;
    }

    /** Candidates whose one-turn value is this close to the best get a look at the turn after. */
    private static final double LOOKAHEAD_MARGIN = 0.3;
    private static final int LOOKAHEAD_TOP = 4;
    /** Weight of the next-turn correction added to the one-turn score when re-ranking. */
    private static final double LOOKAHEAD_WEIGHT = 0.15;
    /** In endgames the follow-up search is cheap and reliable: it weighs more. */
    private static final double ENDGAME_LOOKAHEAD_WEIGHT = 0.4;

    /**
     * Two-turn look for close calls: play each of the best few candidates against the likeliest foe responses,
     * then search our best follow-up from the likeliest results. A one-turn search cannot see "Protect now,
     * their Tailwind is over next turn" or "this switch leaves us in a better spot after the trade".
     */
    private void lookAhead(BattleState s, List<Scored> scored, List<List<Action>> combos, List<Double> probs,
                           List<Action> fixed) {
        if (lookahead <= 0 || scored.size() < 2) return;
        // Endgames (few Pokemon left) are small enough to look further for every sensible option.
        boolean endgame = s.aliveCount(true) + s.aliveCount(false) + Math.max(0, s.oppUnseenReserves) <= (s.doubles ? 4 : 2);
        double top = scored.get(0).value;
        int n = 0;
        int cap = endgame ? 6 : LOOKAHEAD_TOP;
        double margin = endgame ? 1.5 : LOOKAHEAD_MARGIN;
        while (n < scored.size() && n < cap && scored.get(n).value >= top - margin) n++;
        if (n < 2) return;
        double weight = endgame ? ENDGAME_LOOKAHEAD_WEIGHT : LOOKAHEAD_WEIGHT;
        // The two likeliest foe responses, renormalised.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < combos.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble((Integer i) -> -probs.get(i)));
        List<Integer> keep = order.subList(0, Math.min(2, order.size()));
        double mass = 0;
        for (int i : keep) mass += probs.get(i);
        if (mass <= 0) return;
        Planner nested = new Planner(opt);
        nested.lookahead = endgame && lookahead > 1 ? lookahead - 1 : 0;
        nested.perSlotCap = endgame ? 6 : 5;
        nested.oppPerSlot = endgame ? 3 : 2;
        TurnSimulator sim = new TurnSimulator(false);
        List<Scored> rescored = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            Scored cand = scored.get(c);
            double v2 = 0;
            for (int i : keep) {
                List<Action> acts = new ArrayList<>(cand.actions);
                acts.addAll(fixed);
                acts.addAll(combos.get(i));
                List<TurnSimulator.Outcome> outs = new ArrayList<>(sim.run(s, acts));
                outs.sort(Comparator.comparingDouble((TurnSimulator.Outcome o) -> -o.prob()));
                List<TurnSimulator.Outcome> likely = outs.subList(0, Math.min(2, outs.size()));
                double outMass = 0, val = 0;
                for (TurnSimulator.Outcome o : likely) {
                    outMass += o.prob();
                    // How much better (or worse) the position really is once the next turn is played than
                    // the static evaluation said: a correction on top of the one-turn score, which keeps its costs.
                    val += o.prob() * (nested.followUp(o.state()) - Evaluator.evaluate(o.state()));
                }
                if (outMass > 0) v2 += probs.get(i) / mass * val / outMass;
            }
            rescored.add(new Scored(cand.actions, cand.value + weight * v2));
        }
        rescored.sort(Comparator.comparingDouble((Scored x) -> -x.value));
        // Only the order among the close candidates changes; their reported values stay one-turn values.
        List<Scored> head = new ArrayList<>();
        for (Scored r : rescored) {
            for (int c = 0; c < n; c++) if (scored.get(c).actions.equals(r.actions)) head.add(scored.get(c));
        }
        for (int c = 0; c < n; c++) scored.set(c, head.get(c));
    }

    /** Value of our best play next turn from {@code t} (static evaluation when the battle is decided or a slot is empty). */
    private double followUp(BattleState t) {
        if (t.aliveCount(true) == 0 || (t.aliveCount(false) == 0 && t.oppUnseenReserves <= 0)) return Evaluator.evaluate(t);
        List<SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < t.slots(); i++) {
            Battler b = t.my(i);
            if (b == null || !b.alive()) {
                // Replacements happen between turns: judged by the evaluation instead of a search.
                if (!t.bench(true).isEmpty()) return Evaluator.evaluate(t);
                SlotRequest r = new SlotRequest();
                r.slot = i;
                r.pass = true;
                reqs.add(r);
                continue;
            }
            SlotRequest r = new SlotRequest();
            r.slot = i;
            for (MoveInfo m : b.moves) if (m.usable() && (b.lockedMove == null || b.lockedMove.equals(m.id))) r.moves.add(m);
            if (b.pendingMega != null) {
                r.canMega = true;
                r.megaForm = b.pendingMega;
            }
            r.switchOptions.addAll(t.bench(true));
            reqs.add(r);
        }
        boolean anyone = false;
        for (SlotRequest r : reqs) anyone |= !r.pass;
        if (!anyone) return Evaluator.evaluate(t);
        Plan p = t.doubles ? decideDoubles(t, reqs) : decideSingles(t, reqs);
        return p.value;
    }

    public Plan decide(BattleState s, List<SlotRequest> reqs) {
        long t0 = System.nanoTime();
        Plan plan = s.doubles ? decideDoubles(s, reqs) : decideSingles(s, reqs);
        plan.micros = (System.nanoTime() - t0) / 1000;
        explain(s, plan);
        return plan;
    }

    // ------------------------------------------------------------------ candidates

    List<Action> candidates(BattleState s, SlotRequest r) {
        List<Action> out = new ArrayList<>();
        if (r.pass) {
            out.add(Action.pass(true, r.slot));
            return out;
        }
        if (r.forceSwitch) {
            for (int idx : r.switchOptions) out.add(Action.switchTo(true, r.slot, idx));
            if (out.isEmpty()) out.add(Action.pass(true, r.slot));
            return out;
        }
        Battler me = s.my(r.slot);
        List<MoveInfo> usable = new ArrayList<>();
        for (MoveInfo m : r.moves) if (m.usable()) usable.add(m);
        boolean onlyForced = usable.size() == 1;
        boolean choiceHolder = me != null && ItemDex.isChoice(me.item);
        if (choiceHolder && !onlyForced) {
            List<MoveInfo> damaging = new ArrayList<>();
            for (MoveInfo m : usable) if (m.category != Category.STATUS) damaging.add(m);
            // A choice item locks us into the move: Protect/Stealth Rock/etc. would waste every following turn.
            if (!damaging.isEmpty()) usable = damaging;
        }
        for (MoveInfo m : usable) {
            if (!onlyForced && MoveDex.AVOID.contains(m.id) && usable.size() > 1 && !asleepTalker(me, m)) continue;
            if (!onlyForced && me != null && MoveDex.FIRST_TURN_ONLY.contains(m.id) && me.turnsActive > 0) continue;
            List<Action> base = new ArrayList<>();
            if (m.needsTargetChoice() && s.doubles) {
                for (int t = 0; t < s.slots(); t++) {
                    Battler foe = s.opp(t);
                    if (foe == null || !foe.alive()) continue;
                    // Never aim a status move at a target it cannot affect (Sleep Powder into a Grass-type
                    // Amoonguss, a second status on an already poisoned foe...).
                    if (!onlyForced && pointlessStatus(s, me, foe, m)) continue;
                    base.add(Action.move(true, r.slot, m, false, t));
                }
                if (base.isEmpty() && (onlyForced || m.category != Category.STATUS || MoveDex.INFLICT_STATUS.get(m.id) == null))
                    base.add(Action.move(true, r.slot, m, false, 0));
            } else if (m.needsTargetChoice()) {
                base.add(Action.move(true, r.slot, m, false, 0));
            } else {
                base.add(Action.move(true, r.slot, m, false, -1));
            }
            for (Action a : base) {
                Action plain = a;
                if (opt.useMega && (r.canMega || r.canUltraBurst)) {
                    Action mega = a.withGimmick(r.canMega ? "mega" : "ultra", null).withMegaForm(r.megaForm);
                    out.add(mega);
                    // Without a known form we cannot judge the timing: always evolve.
                    if (r.megaForm != null) out.add(a);
                } else {
                    out.add(plain);
                }
                if (opt.useTera && r.canTera) out.add(plain.withGimmick("terastal", null));
                if (opt.useZ && r.zMoves.containsKey(m.id)) out.add(a.withGimmick("zmove", r.zMoves.get(m.id)));
                if (opt.useDynamax && r.canDynamax && r.maxMoves.containsKey(m.id))
                    out.add(a.withGimmick("max", r.maxMoves.get(m.id)));
            }
        }
        if (out.isEmpty() && !usable.isEmpty()) {
            MoveInfo m = usable.get(0);
            out.add(Action.move(true, r.slot, m, false, m.needsTargetChoice() ? 0 : -1));
        }
        if (opt.allowSwitching && !r.trapped) {
            for (int idx : r.switchOptions) out.add(Action.switchTo(true, r.slot, idx));
        }
        if (out.isEmpty()) out.add(Action.pass(true, r.slot));
        return out;
    }

    /** A status move that cannot land on {@code foe} (immunity, already statused, Safety Goggles...). */
    private static boolean pointlessStatus(BattleState s, Battler me, Battler foe, MoveInfo m) {
        if (me == null || m.category != Category.STATUS) return false;
        String inflict = MoveDex.INFLICT_STATUS.get(m.id);
        if (inflict == null || inflict.length() != 3) return false;
        return !TurnSimulator.canStatus(s, me, foe, inflict, m);
    }

    /** Sleep Talk / Snore are only worth it while the user is asleep. */
    private static boolean asleepTalker(Battler me, MoveInfo m) {
        return me != null && "slp".equals(me.status) && ("sleeptalk".equals(m.id) || "snore".equals(m.id));
    }

    /**
     * Loop breaker: if the damaging moves we picked last turn did not scratch the foes (Wide Guard,
     * Protect, a wall...), repeating them gets progressively less attractive.
     */
    private static double stallPenalty(BattleState s, List<Action> actions) {
        if (s.stallTurns <= 0 || s.lastMoveIds.isEmpty()) return 0;
        double p = 0;
        for (Action a : actions) {
            if (a.kind != Action.Kind.MOVE) continue;
            MoveInfo m = a.executed();
            if (m.category == Category.STATUS) continue;
            if (s.lastMoveIds.contains(a.slot + ":" + m.id)) p += Math.min(1.0, 0.2 * s.stallTurns);
        }
        return p;
    }

    /** Evaluation penalty for spending a once-per-battle resource. */
    private double resourceCost(BattleState s, List<Action> actions) {
        double cost = 0;
        int alive = s.aliveCount(true);
        for (Action a : actions) {
            if (a.gimmick == null) continue;
            switch (a.gimmick) {
                case "terastal" -> cost += alive > 1 ? 0.12 : 0.0;
                case "zmove" -> cost += alive > 1 ? 0.1 : 0.0;
                case "max" -> cost += alive > 1 ? 0.15 : 0.0;
                default -> { }
            }
        }
        return cost;
    }

    // ------------------------------------------------------------------ singles

    private Plan decideSingles(BattleState s, List<SlotRequest> reqs) {
        Plan plan = new Plan();
        SlotRequest r = reqs.get(0);
        List<Action> mine = candidates(s, r);
        TurnSimulator sim = new TurnSimulator(true);

        List<OpponentModel.Weighted> opp = new ArrayList<>();
        Battler foe = s.opp(0);
        boolean foeActs = !r.forceSwitch && foe != null && foe.alive();
        if (foeActs) opp = OpponentModel.policy(s, false, 0, s.oppKind);

        List<Scored> scored = new ArrayList<>();
        for (Action a : mine) {
            double v;
            if (opp.isEmpty()) {
                v = expectedEval(sim.run(s, List.of(a)));
            } else {
                double mean = 0, worst = Double.MAX_VALUE;
                for (OpponentModel.Weighted w : opp) {
                    double e = expectedEval(sim.run(s, List.of(a, w.action())));
                    mean += w.prob() * e;
                    if (w.prob() >= MIN_WORST_PROB) worst = Math.min(worst, e);
                }
                if (worst == Double.MAX_VALUE) worst = mean;
                double risk = riskFor(s);
                v = (1 - risk) * mean + risk * worst;
            }
            v -= resourceCost(s, List.of(a));
            v -= stallPenalty(s, List.of(a));
            // A Choice item locks singles too (Flint: Scarf Garchomp locked into False Swipe had to switch out).
            v -= lockRegret(s, List.of(a));
            v += tieBreak(s, a);
            scored.add(new Scored(List.of(a), v));
        }
        scored.sort(Comparator.comparingDouble((Scored x) -> -x.value));
        List<List<Action>> combos = new ArrayList<>();
        List<Double> probs = new ArrayList<>();
        for (OpponentModel.Weighted w : opp) {
            combos.add(List.of(w.action()));
            probs.add(w.prob());
        }
        if (combos.isEmpty()) {
            combos.add(List.of());
            probs.add(1.0);
        }
        lookAhead(s, scored, combos, probs, List.of());
        Scored best = scored.get(0);
        plan.actions.addAll(best.actions);
        plan.value = best.value;
        plan.ranking.addAll(scored.subList(0, Math.min(12, scored.size())));
        return plan;
    }

    private double riskFor(BattleState s) {
        return switch (s.oppKind) {
            case WILD -> Math.min(opt.riskAversion, 0.15);
            case NPC -> opt.riskAversion;
            case PLAYER -> Math.max(opt.riskAversion, 0.5);
        };
    }

    /** Outcomes at least this likely count as "the bad luck case" of a line. */
    private static final double LUCK_WORST_PROB = 0.2;
    /** Weight of that bad-luck case: a Tower streak ends on one loss, so gambles are paid for. */
    private static final double LUCK_RISK = 0.3;

    /**
     * Expected evaluation over the luck of the turn (accuracy, damage rolls, speed ties, status landing),
     * tilted towards its likely bad-luck branch. A plain average let a 75% Sleep Powder beat a safe Protect
     * although the miss left Venusaur dead to Fake Out + Flare Blitz on turn 1 (Cira).
     */
    private static double expectedEval(List<TurnSimulator.Outcome> outs) {
        double v = 0, p = 0, worst = Double.MAX_VALUE;
        for (TurnSimulator.Outcome o : outs) {
            double e = Evaluator.evaluate(o.state());
            v += o.prob() * e;
            p += o.prob();
            if (o.prob() >= LUCK_WORST_PROB) worst = Math.min(worst, e);
        }
        if (p <= 0) return 0;
        double mean = v / p;
        if (worst == Double.MAX_VALUE || worst >= mean) return mean;
        return (1 - LUCK_RISK) * mean + LUCK_RISK * worst;
    }

    /**
     * Tiny preferences so equal lines resolve sensibly (attack > switch, accurate > inaccurate, and above all
     * the move that does more damage). When the simulation expects the user to faint before moving, every
     * move scores the same: without the damage term a 3% Torkoal picked Eruption (4% damage, 100% accuracy)
     * over Heat Wave, and then survived and wasted its turn (#15 Lorena).
     */
    private static double tieBreak(BattleState s, Action a) {
        if (a.kind != Action.Kind.MOVE) return 0;
        MoveInfo m = a.executed();
        double v = 0.001 * m.accuracy;
        if (m.category == Category.STATUS) return v;
        v += 0.0005;
        Battler user = s.my(a.slot);
        if (user == null) return v;
        double share = 0;
        for (int t = 0; t < s.slots(); t++) {
            Battler foe = s.opp(t);
            if (foe == null || !foe.alive()) continue;
            if (!m.isSpread() && a.targetSlot >= 0 && a.targetSlot != t && s.doubles) continue;
            DamageCalc.Result r = DamageCalc.calc(user, foe, m, s.field, true, s.doubles, s.doubles && m.isSpread(), false);
            share += Math.min(1, r.avg() * r.hitChance / Math.max(1, foe.hp));
        }
        return v + 0.01 * Math.min(2, share);
    }

    // ------------------------------------------------------------------ doubles

    /** Predicted actions of every slot we do not control, as weighted joint combinations. */
    /** {@code rarest}: smallest single-foe probability inside each combo (what "worst case" is judged on). */
    private record Responses(List<Action> fixed, List<List<Action>> combos, List<Double> probs, List<Double> rarest) {}

    private Responses responses(BattleState s, List<SlotRequest> reqs) {
        List<Action> fixed = new ArrayList<>();
        boolean anyForce = false;
        for (SlotRequest r : reqs) anyForce |= r.forceSwitch;
        List<List<OpponentModel.Weighted>> oppSlots = new ArrayList<>();
        if (!anyForce) {
            for (int slot = 0; slot < s.slots(); slot++) {
                boolean requested = false;
                for (SlotRequest r : reqs) requested |= r.slot == slot;
                if (!requested && s.my(slot) != null && s.my(slot).alive()) {
                    List<OpponentModel.Weighted> ally = OpponentModel.policy(s, true, slot, BattleState.OpponentKind.NPC);
                    if (!ally.isEmpty()) fixed.add(ally.get(0).action());
                }
                List<OpponentModel.Weighted> p = OpponentModel.policy(s, false, slot, s.oppKind);
                if (!p.isEmpty()) oppSlots.add(trimTo(p, oppPerSlot));
            }
        }
        List<List<Action>> oppCombos = new ArrayList<>();
        List<Double> oppProbs = new ArrayList<>();
        List<Double> rarest = new ArrayList<>();
        combine(oppSlots, 0, new ArrayList<>(), 1.0, 1.0, oppCombos, oppProbs, rarest);
        if (oppCombos.isEmpty()) {
            oppCombos.add(List.of());
            oppProbs.add(1.0);
            rarest.add(1.0);
        }
        return new Responses(fixed, oppCombos, oppProbs, rarest);
    }

    /** Score of one joint action for our slots (same formula the doubles search uses). */
    public double scoreJoint(BattleState s, List<SlotRequest> reqs, List<Action> joint) {
        Responses resp = responses(s, reqs);
        return scoreJoint(s, new TurnSimulator(false), resp, joint);
    }

    private double scoreJoint(BattleState s, TurnSimulator sim, Responses resp, List<Action> joint) {
        double mean = 0, worst = Double.MAX_VALUE;
        for (int k = 0; k < resp.combos().size(); k++) {
            List<Action> acts = new ArrayList<>(joint);
            acts.addAll(resp.fixed());
            acts.addAll(resp.combos().get(k));
            double e = expectedEval(sim.run(s, acts));
            mean += resp.probs().get(k) * e;
            // One foe's plausible move is enough to count (Rock Slide 8% next to a partner's Protect 56%).
            if (resp.rarest().get(k) >= MIN_WORST_PROB) worst = Math.min(worst, e);
        }
        if (worst == Double.MAX_VALUE) worst = mean;
        double risk = riskFor(s);
        double v = (1 - risk) * mean + risk * worst - resourceCost(s, joint) - stallPenalty(s, joint);
        v -= protectTempoPenalty(s, joint) + lockRegret(s, joint) + allyFireLockCost(s, joint) + switchChurn(s, joint);
        v -= idleProtectPenalty(s, resp, joint);
        v -= friendlyFireKo(s, joint);
        for (Action a : joint) v += tieBreak(s, a);
        v -= redundantStatusTarget(joint);
        return v;
    }

    /**
     * A status move into the foe our partner is already attacking is worth at most the same (the simulator
     * retargets it when that foe faints) and less when that foe protects: #19 Boris, Venusaur slept into a 9%
     * Garchomp's Protect while the untouched Metagross knocked it out. Prefer the other foe on a tie.
     */
    private static double redundantStatusTarget(List<Action> joint) {
        double c = 0;
        for (Action a : joint) {
            if (a.kind != Action.Kind.MOVE || !a.mine || a.targetMine || a.targetSlot < 0) continue;
            MoveInfo m = a.executed();
            if (m.category != Category.STATUS || m.isSpread()) continue;
            for (Action b : joint)
                if (b != a && b.kind == Action.Kind.MOVE && b.mine && !b.targetMine && b.targetSlot == a.targetSlot
                    && b.executed().isDamaging() && !b.executed().isSpread()) c += 0.004;
        }
        return c;
    }

    /**
     * Protecting with both Pokemon wastes the whole turn (no damage, weather/Tailwind ticking) and leaves
     * both unable to Protect next turn, still facing the same attackers. The search is one turn deep, so
     * this teaches it that follow-up cost. A single Protect is not touched (it is a normal scouting or
     * blocking play), and neither is protecting against a first-turn Fake Out.
     */
    /**
     * Protect on a Pokemon the foes are not expected to hit just hands them a free turn (#31: Mega Charizard
     * protected twice while both foes were going into Venusaur). Costs more the less damage it would block.
     */
    /**
     * A spread move that knocks out our own partner (#27 Enelsitio02: Earthquake finished a 25% Venusaur, the only
     * Spore-immune Pokemon left) costs more than the material: the partner's turn and role are lost too.
     */
    private static double friendlyFireKo(BattleState s, List<Action> joint) {
        double pen = 0;
        for (Action a : joint) {
            if (a.kind != Action.Kind.MOVE || !a.executed().hitsAlly()) continue;
            Battler user = s.my(a.slot);
            if (user == null || !user.alive()) continue;
            for (int k = 0; k < s.slots(); k++) {
                Battler ally = s.my(k);
                if (ally == null || ally == user || !ally.alive()) continue;
                boolean allyProtects = false;
                for (Action b : joint) {
                    if (b.slot == k && b.kind == Action.Kind.MOVE && MoveDex.PROTECT.contains(b.executed().id)) allyProtects = true;
                    if (b.slot == k && b.kind == Action.Kind.SWITCH) allyProtects = true;
                }
                if (allyProtects) continue;
                DamageCalc.Result r = DamageCalc.calc(user, ally, a.executed(), s.field, true, s.doubles, true, false);
                double ko = r.koChance(ally.hp);
                if (ko > 0) pen += 0.3 * ko;
            }
        }
        return pen;
    }

    private static double idleProtectPenalty(BattleState s, Responses resp, List<Action> joint) {
        double pen = 0;
        for (Action a : joint) {
            if (a.kind != Action.Kind.MOVE || !MoveDex.PROTECT.contains(a.executed().id)) continue;
            Battler me = s.my(a.slot);
            if (me == null || !me.alive()) continue;
            double blocked = 0;
            for (int k = 0; k < resp.combos().size(); k++) {
                double p = resp.probs().get(k);
                for (Action o : resp.combos().get(k)) {
                    if (o.kind != Action.Kind.MOVE || o.mine) continue;
                    MoveInfo m = o.executed();
                    if (!m.isDamaging()) continue;
                    boolean hitsMe = m.isSpread() || (o.targetMine && o.targetSlot == a.slot);
                    if (!hitsMe) continue;
                    Battler foe = s.opp(o.slot);
                    if (foe == null || !foe.alive()) continue;
                    DamageCalc.Result r = DamageCalc.calc(foe, me, m, s.field, false, s.doubles, m.isSpread(), false);
                    blocked += p * Math.min(1, r.expected() / Math.max(1, me.hp));
                }
            }
            if (blocked < 0.25) pen += 0.12 * (1 - blocked / 0.25);
        }
        return pen;
    }

    private static double protectTempoPenalty(BattleState s, List<Action> joint) {
        if (!s.doubles) return 0;
        int protects = 0;
        double exposure = 0;
        boolean fakeOutThreat = false;
        for (Action a : joint) {
            if (a.kind != Action.Kind.MOVE || !MoveDex.PROTECT.contains(a.executed().id)) continue;
            Battler me = s.my(a.slot);
            if (me == null || !me.alive()) continue;
            protects++;
            for (int k = 0; k < s.slots(); k++) {
                Battler foe = s.opp(k);
                if (foe == null || !foe.alive()) continue;
                if (foe.turnsActive == 0) {
                    for (MoveInfo fm : foe.moves) if (MoveDex.FIRST_TURN_ONLY.contains(fm.id)) fakeOutThreat = true;
                }
                exposure += Math.min(1, Evaluator.bestAttack(foe, me, s).dmg / Math.max(1, me.hp));
            }
        }
        if (protects < 2) return 0;
        double pen = 0.10 + 0.05 * Math.min(2, exposure);
        // Fake Out makes one Protect sensible, rarely two: both sitting still wastes a sun turn (#3 Cira).
        return fakeOutThreat ? pen * 0.5 : pen;
    }

    /**
     * Pulling out a healthy Pokemon that only just came in throws away its free entry and drops the next one
     * in front of the same attacks (#31 T3, #14 Boris, Cira, Nacho: a fresh Scarf Garchomp swapped straight for
     * Charizard, who fell at once). The one-turn search does not see that, so it costs a little.
     */
    private static double switchChurn(BattleState s, List<Action> joint) {
        double cost = 0;
        for (Action a : joint) {
            if (a.kind != Action.Kind.SWITCH) continue;
            Battler out = s.my(a.slot);
            if (out == null || !out.alive() || out.turnsActive > 0 || out.hpFrac() < 0.5) continue;
            if (out.status != null || out.drowsy) continue;
            cost += 0.08;
        }
        return cost + switchInDanger(s, joint);
    }

    /**
     * Switching a Pokemon in front of a faster foe that has SHOWN a move able to one-shot it (Ice Spinner into
     * Garchomp, #5 Cesar) hands that foe a free KO now or next turn, whatever it targets this turn.
     */
    private static double switchInDanger(BattleState s, List<Action> joint) {
        double cost = 0;
        for (Action a : joint) {
            if (a.kind != Action.Kind.SWITCH) continue;
            Battler in = s.myTeam.get(a.switchTo);
            if (in == null || !in.alive()) continue;
            // Staying is no safer when a faster foe already one-shots the Pokemon going out (#15 Nagore: Scarf
            // Garchomp 39% attacked into a Tailwind Earthquake instead of switching to the immune Charizard).
            // Only the extra danger over the safest other switch-in counts then (log 17 Ruben: Torkoal out of
            // Earthquake into a Rock Slide Charizard, while our Garchomp could take both).
            if (outrunKo(s, s.my(a.slot))) {
                double other = Double.MAX_VALUE;
                for (int idx : s.bench(true)) {
                    Battler c = s.myTeam.get(idx);
                    if (c == in || !c.alive()) continue;
                    other = Math.min(other, Math.max(entryDanger(s, c, 0.5, false), comboDanger(s, c)));
                }
                // Only a near-certain one-shot on the incoming mon beats the certain loss of the one staying.
                // The end-of-turn chip (Solar Power...) counts for the Pokemon we would send in, not for the
                // alternatives: a risky switch is never excused by pointing at another risky one.
                if (other != Double.MAX_VALUE) cost += Math.max(0, entryDanger(s, in, 0.7, true) - other);
                continue;
            }
            for (int k = 0; k < s.slots(); k++) {
                Battler foe = s.opp(k);
                if (foe == null || !foe.alive()) continue;
                // A shown Will-O-Wisp lands on whoever comes in: foes aim it at our physical attackers (Arcanine
                // burned Garchomp 9 times out of 12 in the logs; Kaprus_ #8 Cira switched Garchomp right into it).
                if (in.atk > in.spa && in.status == null) {
                    for (MoveInfo m : foe.moves) {
                        if (!"willowisp".equals(m.id) || !m.revealed || !m.usable()) continue;
                        if (TurnSimulator.canStatus(s, foe, in, "brn", m)) cost += 0.15;
                    }
                }
            }
            cost += entryDanger(s, in, 0.5, true);
        }
        return cost;
    }

    /**
     * An alternative switch-in is only "safe" if both foes together cannot take it down either (#15 Nagore:
     * Venusaur survives any single hit but not Heat Wave plus Earthquake). Spread moves reach it whatever the
     * foe aims at; a single-target hit only half the time.
     */
    private static double comboDanger(BattleState s, Battler in) {
        double sum = 0;
        for (int k = 0; k < s.slots(); k++) {
            Battler foe = s.opp(k);
            if (foe == null || !foe.alive()) continue;
            double best = 0;
            for (MoveInfo m : foe.moves) {
                double w = threatWeight(foe, m);
                if (w <= 0) continue;
                double d = w * DamageCalc.calc(foe, in, m, s.field, false, s.doubles, m.isSpread(), false).expected();
                best = Math.max(best, m.isSpread() || !s.doubles ? d : 0.5 * d);
            }
            sum += best;
        }
        return sum >= in.hp ? 0.45 : 0;
    }

    /**
     * HP a Pokemon that comes in has to survive this turn with: the end-of-turn chip (Solar Power and Dry Skin in
     * sun, sand, burn, poison) comes on top of the hit. A Mega Charizard-Y line switched in at full HP was left on 1%
     * by a hit plus Solar Power, which a check on the hit alone called safe.
     */
    public static double turnHpForTest(BattleState s, Battler b) {
        return turnHp(s, b);
    }

    static double turnHp(BattleState s, Battler b) {
        return Math.max(1, b.hp - Math.max(0, Evaluator.residualPerTurn(b, s.field)));
    }

    /** Guessed moves count for danger only from this likelihood on: below it they are mostly noise. */
    private static final double MIN_GUESS_THREAT = 0.3;

    /**
     * How much a foe's move counts when judging danger: fully once shown, by the chance it really is in the set
     * while only guessed (movepool / memory), and not at all when that chance is small. Judging danger on shown
     * moves alone left every fresh foe looking harmless: the first time a foe appears nothing is revealed, and
     * that is exactly when a 4x-weak Pokemon is left in front of it (Ice Beam on a Ground/Dragon, Rock Slide on a
     * Fire/Flying, Earth Power on a Fire/Steel...).
     */
    static double threatWeight(Battler foe, MoveInfo m) {
        if (!m.isDamaging() || !m.usable()) return 0;
        if (foe.lockedMove != null && !foe.lockedMove.equals(m.id)) return 0;
        if (MoveDex.FIRST_TURN_ONLY.contains(m.id) && foe.turnsActive > 0) return 0;
        if (m.revealed) return 1;
        // A foe that already showed its full set has nothing left to hide.
        int shown = 0;
        for (MoveInfo x : foe.moves) if (x.revealed) shown++;
        if (shown >= 4) return 0;
        double e = OpponentModel.existence(foe, m);
        return e >= MIN_GUESS_THREAT ? e : 0;
    }

    /**
     * The move ranking is near-certain which move a Tower trainer picks, yet they strayed from it on the very turn
     * we switched (log 17: Nacho's Kingdra Ice Beamed the incoming Garchomp, Ruben's Garchomp Rock Slid the incoming
     * Charizard, twice). A shown one-shot on the incoming Pokemon costs real value, more from a faster foe, and a
     * spread move reaches it whatever slot the foe aims at.
     */
    private static double entryDanger(BattleState s, Battler in, double minKo, boolean withChip) {
        double cost = 0;
        for (int k = 0; k < s.slots(); k++) {
            Battler foe = s.opp(k);
            if (foe == null || !foe.alive()) continue;
            boolean foeFaster = Speed.movesFirst(foe, in, s.field);
            double shown = 0, guessedMiss = 1;
            for (MoveInfo m : foe.moves) {
                double w = threatWeight(foe, m);
                // A guessed attack from a slower foe leaves us a move to answer it first: only shown ones count then.
                if (w <= 0 || (!m.revealed && !foeFaster)) continue;
                DamageCalc.Result r = DamageCalc.calc(foe, in, m, s.field, false, s.doubles, m.isSpread(), false);
                double hp = withChip ? turnHp(s, in) : in.hp;
                if (r.koChance(hp) < minKo) continue;
                double ko = r.koChance(hp) * DamageCalc.hitChance(foe, in, m, s.field) * (m.isSpread() && s.doubles ? 1.5 : 1.0);
                if (m.revealed) shown = Math.max(shown, ko);
                else guessedMiss *= 1 - Math.min(1, w * ko);
            }
            // Several guessed answers add up (it needs only one of them), but never beyond a shown one-shot.
            double worst = Math.max(shown, 1 - guessedMiss);
            cost += worst * (foeFaster ? 0.6 : 0.2);
        }
        return cost;
    }

    /**
     * A foe that moves first has an attack with at least an even chance to knock {@code b} out this turn: shown,
     * or guessed with enough likelihood (all its likely guesses together).
     */
    private static boolean outrunKo(BattleState s, Battler b) {
        return outrunKoChance(s, b) >= 0.5;
    }

    /** Chance that some foe moving first holds an attack that likely knocks {@code b} out this turn. */
    static double outrunKoChance(BattleState s, Battler b) {
        if (b == null || !b.alive()) return 0;
        double safe = 1;
        for (int k = 0; k < s.slots(); k++) {
            Battler foe = s.opp(k);
            if (foe == null || !foe.alive()) continue;
            boolean first = Speed.movesFirst(foe, b, s.field);
            for (MoveInfo m : foe.moves) {
                double w = threatWeight(foe, m);
                // A priority attack (Ice Shard, Sucker Punch...) lands first whatever the speeds.
                if (w <= 0 || !(first || Speed.priority(foe, m, s.field) > 0)) continue;
                if (DamageCalc.calc(foe, b, m, s.field, false, s.doubles, false, false).koChance(b.hp) >= 0.5) safe *= 1 - w;
            }
        }
        return 1 - safe;
    }

    /**
     * A Choice item locks the holder into the move it picks now. Penalise picks that are much worse than
     * the holder's alternatives against the foes still on the field or in the back (Rock Slide into a
     * Steel/Ground Pokemon), measured as the share of the foe's remaining HP the move fails to remove.
     */
    private static double lockRegret(BattleState s, List<Action> joint) {
        double cost = 0;
        for (Action a : joint) {
            if (a.kind != Action.Kind.MOVE || a.executed().category == Category.STATUS) continue;
            Battler me = s.my(a.slot);
            if (me == null || !me.alive() || !ItemDex.isChoice(me.item)) continue;
            double sum = 0, weight = 0;
            for (int idx = 0; idx < s.oppTeam.size(); idx++) {
                Battler foe = s.oppTeam.get(idx);
                if (!foe.alive()) continue;
                // The foes on the field are the ones the lock has to beat first; a benched foe may never
                // come back in front of us (#31: Dragon Claw "for the benched Garchomp" beat Rock Slide on
                // Lugia, then Whimsicott walled the lock).
                double w = s.isActive(false, idx) ? 1.0 : 0.5;
                double mine = removedShare(me, foe, a.executed(), s);
                double best = mine;
                for (MoveInfo alt : me.moves) {
                    if (!alt.usable() || alt.category == Category.STATUS) continue;
                    if (MoveDex.SELF_KO.contains(alt.id) || MoveDex.FIRST_TURN_ONLY.contains(alt.id)) continue;
                    best = Math.max(best, removedShare(me, foe, alt, s));
                }
                if (best > 0.05) sum += w * (best - mine) / best;
                weight += w;
            }
            if (weight > 0) cost += 0.12 * sum / weight;
        }
        return cost;
    }

    /**
     * A Choice holder that picks Earthquake/Surf-style moves next to a grounded partner is locked into
     * hurting that partner every following turn: the partner has to spend Protect (which fails when
     * repeated) or take the hit. One turn of search does not see that, so charge for it up front unless
     * the move is about to finish the fight. Skipped when the partner is immune.
     */
    private static double allyFireLockCost(BattleState s, List<Action> joint) {
        if (!s.doubles) return 0;
        double cost = 0;
        for (Action a : joint) {
            if (a.kind != Action.Kind.MOVE || !a.executed().hitsAlly() || a.executed().category == Category.STATUS) continue;
            Battler me = s.my(a.slot);
            if (me == null || !me.alive() || !ItemDex.isChoice(me.item) || me.lockedMove != null) continue;
            for (int k = 0; k < s.slots(); k++) {
                Battler ally = s.my(k);
                if (ally == null || ally == me || !ally.alive()) continue;
                DamageCalc.Result r = DamageCalc.calc(me, ally, a.executed(), s.field, true, true, false, false);
                double hurt = Math.min(1, r.avg() * r.hitChance / Math.max(1, ally.hp));
                if (hurt < 0.05) continue;
                cost += 0.30 + 0.30 * hurt;
            }
        }
        return cost;
    }

    /** Fraction of the foe's current HP the move removes on average (capped at 1: overkill is not a plus). */
    private static double removedShare(Battler me, Battler foe, MoveInfo m, BattleState s) {
        // Moves that would hit our own partner are already judged by the simulator; do not count them as better.
        if (m.hitsAlly() && s.doubles) return 0;
        DamageCalc.Result r = DamageCalc.calc(me, foe, m, s.field, true, s.doubles, false, false);
        return Math.min(1, r.avg() * r.hitChance / Math.max(1, foe.hp));
    }

    private Plan decideDoubles(BattleState s, List<SlotRequest> reqs) {
        Plan plan = new Plan();
        TurnSimulator sim = new TurnSimulator(false);
        Responses resp = responses(s, reqs);
        List<Action> fixed = resp.fixed();
        List<List<Action>> oppCombos = resp.combos();

        // Per-slot candidates, pruned by a quick single-slot evaluation.
        List<List<Action>> perSlot = new ArrayList<>();
        for (SlotRequest r : reqs) {
            List<Action> cands = candidates(s, r);
            if (cands.size() > perSlotCap) {
                List<Scored> quick = new ArrayList<>();
                List<Action> oppLikely = oppCombos.get(0);
                for (Action a : cands) {
                    List<Action> acts = new ArrayList<>(fixed);
                    acts.addAll(oppLikely);
                    acts.add(a);
                    quick.add(new Scored(List.of(a), expectedEval(sim.run(s, acts)) - resourceCost(s, List.of(a))));
                }
                quick.sort(Comparator.comparingDouble((Scored x) -> -x.value));
                // The quick score sees a single foe response and no partner: never let it drop a switch or
                // Protect, whose worth only shows against the other responses (#14 Boris: the saving switch of
                // a doomed Venusaur was cut here and never compared).
                List<Action> kept = new ArrayList<>();
                for (Action a : cands) {
                    if (a.kind == Action.Kind.SWITCH || (a.kind == Action.Kind.MOVE && a.gimmick == null
                        && MoveDex.PROTECT.contains(a.move.id))) kept.add(a);
                }
                for (int i = 0; i < quick.size() && kept.size() < perSlotCap; i++) {
                    Action a = quick.get(i).actions.get(0);
                    if (!kept.contains(a)) kept.add(a);
                }
                cands = kept;
            }
            perSlot.add(cands);
        }

        List<List<Action>> joints = new ArrayList<>();
        enumerateJoint(perSlot, 0, new ArrayList<>(), joints);

        List<Scored> scored = new ArrayList<>();
        for (List<Action> joint : joints) scored.add(new Scored(joint, scoreJoint(s, sim, resp, joint)));
        if (scored.isEmpty()) {
            List<Action> fallback = new ArrayList<>();
            for (List<Action> c : perSlot) fallback.add(c.isEmpty() ? Action.pass(true, 0) : c.get(0));
            scored.add(new Scored(fallback, 0));
        }
        scored.sort(Comparator.comparingDouble((Scored x) -> -x.value));
        lookAhead(s, scored, oppCombos, resp.probs(), fixed);
        Scored best = scored.get(0);
        plan.actions.addAll(best.actions);
        plan.value = best.value;
        plan.ranking.addAll(scored.subList(0, Math.min(12, scored.size())));
        return plan;
    }

    private static List<OpponentModel.Weighted> trimTo(List<OpponentModel.Weighted> p, int n) {
        List<OpponentModel.Weighted> sorted = new ArrayList<>(p);
        sorted.sort(Comparator.comparingDouble((OpponentModel.Weighted w) -> -w.prob()));
        List<OpponentModel.Weighted> kept = sorted.subList(0, Math.min(n, sorted.size()));
        double sum = 0;
        for (OpponentModel.Weighted w : kept) sum += w.prob();
        List<OpponentModel.Weighted> out = new ArrayList<>();
        for (OpponentModel.Weighted w : kept) out.add(new OpponentModel.Weighted(w.action(), w.prob() / sum, w.score()));
        return out;
    }

    private static void combine(List<List<OpponentModel.Weighted>> slots, int i, List<Action> cur, double p, double min,
                                List<List<Action>> out, List<Double> probs, List<Double> rarest) {
        if (slots.isEmpty()) return;
        if (i == slots.size()) {
            out.add(new ArrayList<>(cur));
            probs.add(p);
            rarest.add(min);
            return;
        }
        for (OpponentModel.Weighted w : slots.get(i)) {
            if (!compatible(cur, w.action())) continue;
            cur.add(w.action());
            combine(slots, i + 1, cur, p * w.prob(), Math.min(min, w.prob()), out, probs, rarest);
            cur.remove(cur.size() - 1);
        }
    }

    private static void enumerateJoint(List<List<Action>> perSlot, int i, List<Action> cur, List<List<Action>> out) {
        if (i == perSlot.size()) {
            out.add(new ArrayList<>(cur));
            return;
        }
        boolean any = false;
        for (Action a : perSlot.get(i)) {
            if (!compatible(cur, a)) continue;
            any = true;
            cur.add(a);
            enumerateJoint(perSlot, i + 1, cur, out);
            cur.remove(cur.size() - 1);
        }
        if (!any && !perSlot.get(i).isEmpty()) {
            // e.g. both slots must switch but only one Pokémon is left: the other passes.
            cur.add(Action.pass(true, perSlot.get(i).get(0).slot));
            enumerateJoint(perSlot, i + 1, cur, out);
            cur.remove(cur.size() - 1);
        }
    }

    /** Two slots may not switch to the same Pokémon or share a once-per-turn gimmick. */
    private static boolean compatible(List<Action> chosen, Action a) {
        for (Action c : chosen) {
            if (a.kind == Action.Kind.SWITCH && c.kind == Action.Kind.SWITCH && a.switchTo == c.switchTo) return false;
            if (a.gimmick != null && a.gimmick.equals(c.gimmick)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ explanation

    private void explain(BattleState s, Plan plan) {
        for (Action a : plan.actions) {
            if (a.kind == Action.Kind.MOVE && a.executed().category != Category.STATUS) {
                Battler user = s.my(a.slot);
                if (user == null) continue;
                List<Battler> targets = new ArrayList<>();
                if (a.executed().isSpread()) {
                    for (int t = 0; t < s.slots(); t++) if (s.opp(t) != null && s.opp(t).alive()) targets.add(s.opp(t));
                } else {
                    Battler t = a.targetSlot >= 0 ? s.opp(a.targetSlot) : s.opp(0);
                    if (t != null) targets.add(t);
                }
                Battler u = user.copy();
                if ("terastal".equals(a.gimmick)) u.terastallize();
                if (a.megaForm != null) {
                    u.types = a.megaForm.types;
                    u.baseTypes = a.megaForm.baseTypes;
                    u.atk = a.megaForm.atk;
                    u.spa = a.megaForm.spa;
                    u.spe = a.megaForm.spe;
                    u.ability = a.megaForm.ability;
                }
                for (Battler t : targets) {
                    DamageCalc.Result r = DamageCalc.calc(u, t, a.executed(), s.field, true, s.doubles,
                        s.doubles && a.executed().isSpread() && targets.size() > 1, false);
                    double lo = 100 * r.min / t.maxHp, hi = 100 * r.max / t.maxHp;
                    String line = String.format(Locale.ROOT, "%s -> %s: %.0f-%.0f%% (KO %.0f%%)",
                        a.executed(), t.name, lo, hi, 100 * r.koChance(t.hp));
                    if (!t.statsExact) line += " ~";
                    plan.notes.add(line);
                }
            } else if (a.kind == Action.Kind.SWITCH) {
                Battler in = s.myTeam.get(a.switchTo);
                for (int t = 0; t < s.slots(); t++) {
                    Battler foe = s.opp(t);
                    if (foe == null || !foe.alive()) continue;
                    double race = Evaluator.race(in, foe, s);
                    plan.notes.add(String.format(Locale.ROOT, "%s vs %s: %s", in.name, foe.name,
                        race > 0.15 ? "+" : race < -0.15 ? "-" : "="));
                }
            }
        }
    }
}
