package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.data.AbilityDex;
import com.manueeh.cobbleai.data.ItemDex;
import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.data.TypeChart;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.Field;
import com.manueeh.cobbleai.model.MoveInfo;
import com.manueeh.cobbleai.model.SideState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plays one turn forward. In branching mode (singles) accuracy and KO rolls split the state into
 * weighted outcomes; otherwise (doubles) expected values keep a single line.
 */
public final class TurnSimulator {
    public record Outcome(BattleState state, double prob) {}

    private static final double MIN_BRANCH = 0.05;
    /** Max uncertain KOs a doubles turn is split on (each split doubles the outcomes). */
    private static final int MAX_KO_BRANCHES = 2;
    /** KO chances closer than this to 0 or 1 are treated as certain. */
    private static final double KO_BRANCH_MIN = 0.04;
    private final boolean branching;
    private final int maxBranchEvents;

    public TurnSimulator(boolean branching) {
        this.branching = branching;
        this.maxBranchEvents = branching ? 3 : 0;
    }

    public List<Outcome> run(BattleState start, List<Action> actions) {
        BattleState s = start.copy();
        // Gimmicks resolve before anything else.
        for (Action a : actions) {
            if (a.kind != Action.Kind.MOVE || a.gimmick == null) continue;
            Battler u = s.active(a.mine, a.slot);
            if (u != null && "terastal".equals(a.gimmick)) u.terastallize();
            if (u != null && a.megaForm != null && ("mega".equals(a.gimmick) || "ultra".equals(a.gimmick))) {
                Battler.applyForm(u, a.megaForm);
                u.pendingMega = null;
                entryAbility(s, u);
            }
        }
        // Switches go first.
        for (Action a : actions) {
            if (a.kind == Action.Kind.SWITCH) doSwitch(s, a.mine, a.slot, a.switchTo);
        }
        List<Outcome> out = new ArrayList<>();
        List<Action> moves = new ArrayList<>();
        for (Action a : actions) if (a.kind == Action.Kind.MOVE) moves.add(a);

        List<List<Action>> orders = new ArrayList<>();
        List<Double> orderProb = new ArrayList<>();
        List<Action> sorted = sortByTurnOrder(s, moves);
        orders.add(sorted);
        orderProb.add(1.0);
        // Speed reading is a guess for the foes: when two neighbours in the order are close (or, under Trick
        // Room, the foe's speed is unread and may be far lower than the estimate) split the turn on who
        // goes first. Singles branch on any pair; doubles on the first uncertain foe/ally pair only.
        int swapAt = -1;
        for (int i = 0; i + 1 < sorted.size(); i++) {
            if (sorted.get(i).mine == sorted.get(i + 1).mine) continue;
            if (speedUncertain(s, sorted.get(i), sorted.get(i + 1))) {
                swapAt = i;
                break;
            }
        }
        if (swapAt >= 0) {
            orderProb.set(0, 0.5);
            List<Action> swapped = new ArrayList<>(sorted);
            swapped.set(swapAt, sorted.get(swapAt + 1));
            swapped.set(swapAt + 1, sorted.get(swapAt));
            orders.add(swapped);
            orderProb.add(0.5);
        }
        for (int i = 0; i < orders.size(); i++) {
            BattleState copy = orders.size() > 1 ? s.copy() : s;
            step(copy, orders.get(i), 0, orderProb.get(i), 0, out);
        }
        return out;
    }

    // ------------------------------------------------------------------ ordering

    private static List<Action> sortByTurnOrder(BattleState s, List<Action> moves) {
        List<Action> list = new ArrayList<>(moves);
        Field f = s.field;
        list.sort(Comparator.comparingDouble((Action a) -> {
            Battler u = s.active(a.mine, a.slot);
            if (u == null) return 0.0;
            int prio = Speed.priority(u, a.executed(), f);
            double spe = Speed.effective(u, f);
            if (f.trickRoom) spe = 100000 - spe;
            // Ties favour the opponent (pessimistic).
            return -(prio * 1_000_000.0 + spe + (a.mine ? 0 : 0.25));
        }));
        return list;
    }

    private static boolean speedUncertain(BattleState s, Action first, Action second) {
        Battler a = s.active(first.mine, first.slot);
        Battler b = s.active(second.mine, second.slot);
        if (a == null || b == null) return false;
        if (Speed.priority(a, first.executed(), s.field) != Speed.priority(b, second.executed(), s.field))
            return false;
        double sa = Speed.effective(a, s.field), sb = Speed.effective(b, s.field);
        double margin = (a.statsExact && b.statsExact) ? 0.001 : 0.05;
        // Trick Room teams run minimum-speed sets: an unread foe may be much slower than the neutral
        // estimate, so a foe that "should" move last may well move first.
        if (s.field.trickRoom) {
            Battler foe = a.mine ? b : a;
            if (!foe.statsExact && !foe.speedKnown) margin = 0.35;
        }
        return Math.abs(sa - sb) / Math.max(1, Math.max(sa, sb)) <= margin;
    }

    // ------------------------------------------------------------------ main loop

    private void step(BattleState s, List<Action> order, int i, double prob, int branchEvents, List<Outcome> out) {
        if (s.speedDirty && i < order.size()) {
            // Gen 9 recalculates speed mid-turn: after Tailwind / Trick Room the rest of the turn reorders.
            s.speedDirty = false;
            List<Action> rest = sortByTurnOrder(s, order.subList(i, order.size()));
            List<Action> reordered = new ArrayList<>(order.subList(0, i));
            reordered.addAll(rest);
            order = reordered;
        }
        if (prob < 1e-4) return;
        if (i >= order.size()) {
            endOfTurn(s);
            out.add(new Outcome(s, prob));
            return;
        }
        Action a = order.get(i);
        Battler user = s.active(a.mine, a.slot);
        if (user == null || !user.alive()) {
            step(s, order, i + 1, prob, branchEvents, out);
            return;
        }
        user.acted = true;
        if (user.mustRecharge) {
            user.mustRecharge = false;
            step(s, order, i + 1, prob, branchEvents, out);
            return;
        }
        if (user.flinched) {
            step(s, order, i + 1, prob, branchEvents, out);
            return;
        }
        if ("slp".equals(user.status) && user.sleepTurns > 0 && !user.sleptThisTurn) {
            // Sleep lasts 1-3 turns and the counter is hidden: it may wake right now (1/3 on the first check,
            // 1/2 on the second). Treating it as certain lost #20 Adriano: Landorus "asleep" at 1% woke up and
            // Rock Slid Charizard while we spent the turn putting Cresselia to sleep.
            int limit = branching ? maxBranchEvents : MAX_KO_BRANCHES;
            double wake = user.sleepTurns >= 2 ? 1.0 / 3 : 0.5;
            if (branchEvents < limit) {
                BattleState awake = s.copy();
                Battler au = awake.active(a.mine, a.slot);
                au.status = null;
                au.sleepTurns = 0;
                au.sleptThisTurn = true;
                step(awake, order, i, prob * wake, branchEvents + 1, out);
                prob *= 1 - wake;
                branchEvents++;
            }
        }
        if ("slp".equals(user.status)) {
            if (user.sleepTurns > 0) {
                user.sleepTurns--;
                if (!"sleeptalk".equals(a.executed().id) && !"snore".equals(a.executed().id)) {
                    step(s, order, i + 1, prob, branchEvents, out);
                    return;
                }
            } else {
                user.status = null;
            }
        }
        if ("frz".equals(user.status) && !"flareblitz".equals(a.executed().id) && !"scald".equals(a.executed().id)
            && !"sacredfire".equals(a.executed().id)) {
            // 20% thaw per turn: treat as mostly skipped.
            s.chanceValue += sign(a.mine) * 0.2 * 0.1;
            step(s, order, i + 1, prob, branchEvents, out);
            return;
        }
        double act = 1;
        if ("par".equals(user.status)) act *= 0.75;
        if (user.confused) act *= 0.67;

        MoveInfo m = a.executed();
        if ("sleeptalk".equals(m.id)) {
            // Sleep Talk calls one of the user's other moves at random; it fails while awake.
            if (!"slp".equals(user.status)) {
                step(s, order, i + 1, prob, branchEvents, out);
                return;
            }
            Battler foe = null;
            for (int k = 0; k < s.slots() && foe == null; k++) {
                Battler c = s.active(!a.mine, k);
                if (c != null && c.alive()) foe = c;
            }
            MoveInfo pick = null;
            double best = -1;
            int options = 0;
            for (MoveInfo x : user.moves) {
                if ("sleeptalk".equals(x.id) || "snore".equals(x.id) || MoveDex.CHARGE.contains(x.id)) continue;
                options++;
                if (!x.isDamaging() || foe == null) continue;
                double d = DamageCalc.calc(user, foe, x, s.field, user.mine, s.doubles, false, false).expected();
                if (d > best) {
                    best = d;
                    pick = x;
                }
            }
            if (pick == null) {
                step(s, order, i + 1, prob, branchEvents, out);
                return;
            }
            m = pick;
            act *= Math.max(0.5, 1.0 - 0.15 * Math.max(0, options - 1));
        }
        if ("recharge".equals(m.id)) {
            step(s, order, i + 1, prob, branchEvents, out);
            return;
        }
        if (m.category == Category.STATUS) {
            if (branchStatus(s, order, i, a, user, m, act, prob, branchEvents, out)) return;
            applyStatusMove(s, a, user, m, act);
            if (ItemDex.isChoice(user.item) && user.alive()) user.lockedMove = m.id;
            step(s, order, i + 1, prob, branchEvents, out);
            return;
        }

        List<Battler> targets = resolveTargets(s, a, user, m);
        if (targets.isEmpty()) {
            step(s, order, i + 1, prob, branchEvents, out);
            return;
        }
        boolean instantCharge = "powerherb".equals(user.item)
            || (("solarbeam".equals(m.id) || "solarblade".equals(m.id)) && "sun".equals(s.field.weather))
            || ("electroshot".equals(m.id) && "rain".equals(s.field.weather));
        if (MoveDex.CHARGE.contains(m.id) && !instantCharge) {
            // Charging turn: nothing lands now; the hit comes next turn if the user survives, so only
            // part of it counts (the foe gets a free action meanwhile).
            Battler t = targets.get(0);
            DamageCalc.Result r = DamageCalc.calc(user, t, m, s.field, user.mine, s.doubles, false, user.helped);
            double frac = Math.min(1, r.expected() / Math.max(1, t.hp));
            // Paid at the end of the turn, and only if both are still there: a partner's KO on the same target
            // made Solar Beam out of sun look good next to a 4% Scrafty (#7 Simon, Kaprus_ run).
            user.pendingChargeValue += sign(a.mine) * 0.35 * frac * act;
            user.chargeTarget = t.uuid;
            step(s, order, i + 1, prob, branchEvents, out);
            return;
        }
        boolean spread = s.doubles && m.isSpread() && targets.size() > 1;
        boolean canBranch = branching && branchEvents < maxBranchEvents && targets.size() == 1;
        if (canBranch) {
            Battler t = targets.get(0);
            if (blocked(s, user, t, m)) {
                afterMoveUser(s, a, user, m, 0, act);
                step(s, order, i + 1, prob, branchEvents, out);
                return;
            }
            DamageCalc.Result r = DamageCalc.calc(user, t, m, s.field, user.mine, s.doubles, false, user.helped);
            if (t.substitute && bypassesSub(user, m) == false) {
                hitSubstitute(t, r, act);
                afterMoveUser(s, a, user, m, 0, act);
                step(s, order, i + 1, prob, branchEvents, out);
                return;
            }
            double pHit = r.hitChance * act;
            double pKo = r.koChanceOnHit(t.hp) * pHit;
            double pHitNoKo = pHit - pKo;
            double pMiss = 1 - pHit;
            // Fold small branches into the largest one to keep the tree narrow.
            double[] ps = {pKo, pHitNoKo, pMiss};
            int biggest = 0;
            for (int k = 1; k < 3; k++) if (ps[k] > ps[biggest]) biggest = k;
            for (int k = 0; k < 3; k++) {
                if (k != biggest && ps[k] < MIN_BRANCH) {
                    ps[biggest] += ps[k];
                    ps[k] = 0;
                }
            }
            int live = 0;
            for (double p : ps) if (p > 0) live++;
            int nextEvents = branchEvents + (live > 1 ? 1 : 0);
            for (int k = 0; k < 3; k++) {
                if (ps[k] <= 0) continue;
                BattleState c = live > 1 ? s.copy() : s;
                Battler cu = c.active(a.mine, a.slot);
                Battler ct = sameBattler(c, t);
                double dealt = 0;
                if (k == 0) {
                    dealt = ct.hp;
                    ct.hp = 0;
                    onHit(c, a, cu, ct, m, dealt, 1);
                } else if (k == 1) {
                    double dmg = r.max <= t.hp ? r.avg() : (r.min + Math.min(t.hp, r.max)) / 2.0;
                    dmg = Math.min(dmg, ct.hp - 1);
                    dealt = Math.max(0, dmg);
                    ct.hp -= dealt;
                    onHit(c, a, cu, ct, m, dealt, 1);
                }
                afterMoveUser(c, a, cu, m, dealt, 1);
                step(c, order, i + 1, prob * ps[k], nextEvents, out);
            }
            return;
        }

        // Doubles: a KO that only *might* happen decides whether the victim still gets to act this turn
        // (Rock Slide at 90%, a damage roll...). Split the turn on it instead of assuming the KO always lands.
        if (!branching && branchEvents < MAX_KO_BRANCHES && !"slp".equals(user.status)) {
            for (Battler t : targets) {
                if (!t.alive() || t.forcedKo != 0 || t.mine == user.mine || t == user) continue;
                if (blocked(s, user, t, m) || t.substitute) continue;
                DamageCalc.Result pr = DamageCalc.calc(user, t, m, s.field, user.mine, s.doubles, spread, user.helped);
                if (pr.immune) continue;
                double pk = pr.koChance(t.hp) * act;
                if (pk <= KO_BRANCH_MIN || pk >= 1 - KO_BRANCH_MIN) continue;
                // Even when the target has already moved, a coin-flip KO (Hurricane at 50% in sun) must not be
                // averaged into "half damage, alive": the end of the turn is either a KO or a survivor (#29 Delia).
                if (!actsLater(s, order, i, t) && (pk < 0.2 || pk > 0.8)) continue;
                for (int k = 0; k < 2; k++) {
                    BattleState c = s.copy();
                    sameBattler(c, t).forcedKo = k == 0 ? 1 : -1;
                    step(c, order, i, prob * (k == 0 ? pk : 1 - pk), branchEvents + 1, out);
                }
                return;
            }
        }

        // Deterministic expected-value resolution.
        double totalDealt = 0;
        for (Battler t : targets) {
            if (!t.alive()) continue;
            if (blocked(s, user, t, m)) continue;
            DamageCalc.Result r = DamageCalc.calc(user, t, m, s.field, user.mine, s.doubles, spread, user.helped);
            if (r.immune) {
                // Feeding Flash Fire powers up the absorber's own Fire moves (#6 Aaron: Eruption into Chandelure).
                if ("fire".equals(r.type) && t.hasAbility("flashfire") && t.mine != user.mine) t.flashFire = true;
                continue;
            }
            if (t.substitute && !bypassesSub(user, m) && t != user) {
                hitSubstitute(t, r, act);
                continue;
            }
            if (t.mine && t.turnsActive == 0 && !user.statsExact) {
                // A fresh switch-in cannot dodge: plan for a high roll from an attacker we only estimated.
                r.min = r.max * 0.95;
            }
            double pKo = r.koChance(t.hp) * act;
            boolean forced = t.forcedKo != 0;
            boolean ko = forced ? t.forcedKo > 0 : pKo >= 0.5;
            t.forcedKo = 0;
            double dealt;
            if (ko) {
                dealt = t.hp;
                t.hp = 0;
                // Credit the chance the target actually survives (already split into a branch when forced).
                if (!forced) s.chanceValue += sign(t.mine) * (1 - pKo) * 0.3;
            } else {
                dealt = Math.min(t.hp - 1, r.avg() * r.hitChance * act);
                dealt = Math.max(0, dealt);
                t.hp -= dealt;
                if (!forced) s.chanceValue -= sign(t.mine) * pKo * 0.3;
            }
            totalDealt += dealt;
            onHit(s, a, user, t, m, dealt, r.hitChance * act);
        }
        for (Battler t : targets) t.forcedKo = 0;
        afterMoveUser(s, a, user, m, totalDealt, act);
        step(s, order, i + 1, prob, branchEvents, out);
    }

    /**
     * Sleep Powder / Hypnosis / Thunder Wave... below 85% accuracy: split the turn on hit or miss. When it
     * lands before the target moves, the target loses its action right now (#18 Bruna: a 12% Venusaur that
     * outspeeds Mega Mawile should put it to sleep so it cannot Play Rough the partner).
     */
    private boolean branchStatus(BattleState s, List<Action> order, int i, Action a, Battler user, MoveInfo m, double act,
                                 double prob, int branchEvents, List<Outcome> out) {
        int limit = branching ? maxBranchEvents : MAX_KO_BRANCHES;
        if (branchEvents >= limit || "yawn".equals(m.id) || user.taunted) return false;
        String inflict = MoveDex.INFLICT_STATUS.get(m.id);
        if (inflict == null || inflict.length() != 3) return false;
        Battler target = a.targetSlot >= 0 ? s.active(a.targetMine, a.targetSlot) : null;
        if (target == null || !target.alive()) {
            for (int slot = 0; slot < s.slots() && (target == null || !target.alive()); slot++) target = s.active(!user.mine, slot);
        }
        if (target == null || !target.alive() || target.mine == user.mine || target.protecting) return false;
        if (target.hasAbility("magicbounce") || target.hasAbility("goodasgold")) return false;
        if (target.status != null || !canStatus(s, user, target, inflict, m)) return false;
        double hit = DamageCalc.hitChance(user, target, m, s.field) * act;
        if (hit <= 0.1 || hit >= 0.85) return false;
        for (int k = 0; k < 2; k++) {
            BattleState c = s.copy();
            if (k == 0) setStatus(sameBattler(c, target), inflict);
            Battler cu = c.active(a.mine, a.slot);
            if (cu != null && ItemDex.isChoice(cu.item)) cu.lockedMove = m.id;
            step(c, order, i + 1, prob * (k == 0 ? hit : 1 - hit), branchEvents + 1, out);
        }
        return true;
    }

    /**
     * Rough Skin / Iron Barbs (1/8) and Rocky Helmet (1/6) hurt a contact attacker, even when the hit KOs.
     * (#13: our Garchomp died to the foe Garchomp's Rough Skin after a Dragon Claw.)
     */
    private static void contactRecoil(Battler user, Battler t, MoveInfo m, double hitProb) {
        if (user == t || !user.alive() || !MoveDex.makesContact(m)) return;
        if ("protectivepads".equals(user.item) || user.hasAbility("magicguard") || user.hasAbility("longreach")) return;
        double frac = 0;
        double skin = Math.max(t.abilityChance("roughskin"), t.abilityChance("ironbarbs"));
        frac += skin / 8.0;
        if ("rockyhelmet".equals(t.item)) frac += 1.0 / 6.0;
        if (frac <= 0) return;
        user.hp = Math.max(0, user.hp - user.maxHp * frac * Math.min(1, hitProb));
    }

    /** True when {@code t} still has a move to make after position {@code i} of the turn order. */
    private static boolean actsLater(BattleState s, List<Action> order, int i, Battler t) {
        for (int j = i + 1; j < order.size(); j++) {
            Action b = order.get(j);
            if (s.active(b.mine, b.slot) == t) return true;
        }
        return false;
    }

    private static double sign(boolean mine) {
        return mine ? 1 : -1;
    }

    private static Battler sameBattler(BattleState s, Battler original) {
        for (int slot = 0; slot < s.slots(); slot++) {
            Battler b = s.active(original.mine, slot);
            if (b != null && b.uuid != null && b.uuid.equals(original.uuid)) return b;
        }
        for (Battler b : s.team(original.mine)) if (b.uuid != null && b.uuid.equals(original.uuid)) return b;
        return original;
    }

    // ------------------------------------------------------------------ targeting

    private static List<Battler> resolveTargets(BattleState s, Action a, Battler user, MoveInfo m) {
        List<Battler> out = new ArrayList<>();
        boolean foeSide = !a.mine;
        if (m.isSpread()) {
            for (int slot = 0; slot < s.slots(); slot++) {
                Battler t = s.active(foeSide, slot);
                if (t != null && t.alive()) out.add(t);
            }
            if (m.hitsAlly()) {
                for (int slot = 0; slot < s.slots(); slot++) {
                    Battler t = s.active(a.mine, slot);
                    if (t != null && t.alive() && t != user) out.add(t);
                }
            }
            if (s.wideGuard[a.mine ? 1 : 0]) out.removeIf(t -> t.mine != a.mine);
            if (s.wideGuard[a.mine ? 0 : 1]) out.removeIf(t -> t.mine == a.mine);
            return out;
        }
        Battler t = a.targetSlot >= 0 ? s.active(a.targetMine, a.targetSlot) : null;
        // Redirection only affects moves aimed at the redirecting side.
        int redirectSide = a.mine ? 1 : 0;
        int redirectSlot = s.redirect[redirectSide];
        if (redirectSlot >= 0 && (t == null || t.mine != a.mine)) {
            Battler r = s.active(!a.mine, redirectSlot);
            if (r != null && r.alive()) t = r;
        }
        if (t == null || !t.alive()) {
            // Retarget to any living foe (Showdown behaviour for adjacent foes).
            for (int slot = 0; slot < s.slots(); slot++) {
                Battler c = s.active(foeSide, slot);
                if (c != null && c.alive()) {
                    t = c;
                    break;
                }
            }
        }
        if (t != null && t.alive()) out.add(t);
        return out;
    }

    private static boolean blocked(BattleState s, Battler user, Battler t, MoveInfo m) {
        if (t == user) return false;
        if (t.mine != user.mine && s.quickGuard[t.mine ? 0 : 1] && !"feint".equals(m.id)
            && Speed.priority(user, m, s.field) > 0) return true;
        return t.protecting && !MoveDex.IGNORE_PROTECT.contains(m.id);
    }

    private static boolean bypassesSub(Battler user, MoveInfo m) {
        return MoveDex.SOUND.contains(m.id) || user.hasAbility("infiltrator");
    }

    private static void hitSubstitute(Battler t, DamageCalc.Result r, double act) {
        if (r.avg() * r.hitChance * act >= t.maxHp / 4.0) t.substitute = false;
    }

    // ------------------------------------------------------------------ effects of damaging moves

    private static void onHit(BattleState s, Action a, Battler user, Battler t, MoveInfo m, double dealt, double hitProb) {
        if (hitProb <= 0) return;
        double sg = sign(user.mine);
        contactRecoil(user, t, m, hitProb);
        if (MoveDex.FLINCH_ALWAYS.contains(m.id) && t.alive() && !t.acted && !t.hasAbility("innerfocus")) {
            t.flinched = true;
        }
        int[] drop = MoveDex.TARGET_DROP.get(m.id);
        if (drop != null && t.alive() && !t.hasAbility("clearbody") && !t.hasAbility("whitesmoke")
            && !t.hasAbility("fullmetalbody") && !"covertcloak".equals(t.item)) {
            double chance = m.effectChance > 0 ? m.effectChance : 1.0;
            chance *= hitProb;
            if (chance >= 0.5) applyBoosts(t, drop);
            else s.chanceValue += sg * chance * 0.05;
        }
        String sec = MoveDex.SECONDARY_STATUS.get(m.id);
        if (sec != null && t.alive() && t.status == null && canStatus(s, user, t, sec, null)) {
            double chance = (m.effectChance > 0 ? m.effectChance : 0.1) * hitProb;
            if (user.hasAbility("serenegrace")) chance = Math.min(1, chance * 2);
            if (user.hasAbility("sheerforce")) chance = 0;
            if (chance >= 0.5) setStatus(t, sec);
            else t.pendingStatusValue += sg * chance * Evaluator.statusValue(t, sec);
        }
        if ("knockoff".equals(m.id) && t.alive()) {
            if (t.item != null) {
                t.item = null;
                s.chanceValue += sg * 0.04;
            } else if (!t.mine) {
                s.chanceValue += sg * 0.03;
            }
        }
        if ("stoneaxe".equals(m.id)) s.field.side(!user.mine).stealthRock = true;
        if ("ceaselessedge".equals(m.id)) {
            SideState ss = s.field.side(!user.mine);
            ss.spikes = Math.min(3, ss.spikes + 1);
        }
        if ("clearsmog".equals(m.id) && t.alive()) t.boosts = new int[7];
        if ("mortalspin".equals(m.id) && t.alive() && t.status == null && canStatus(s, user, t, "psn", null)) setStatus(t, "psn");
        if ("nuzzle".equals(m.id) && t.alive() && t.status == null && canStatus(s, user, t, "par", m)) setStatus(t, "par");
        // Drain
        Double drain = MoveDex.DRAIN.get(m.id);
        if (drain != null && dealt > 0 && !t.hasAbility("liquidooze")) {
            user.hp = Math.min(user.maxHp, user.hp + dealt * drain * ("bigroot".equals(user.item) ? 1.3 : 1));
        }
        // Recoil
        Double recoil = MoveDex.RECOIL.get(m.id);
        if (recoil != null && dealt > 0 && !user.hasAbility("rockhead") && !user.hasAbility("magicguard")) {
            user.hp = Math.max(0, user.hp - dealt * recoil);
        }
        if ("struggle".equals(m.id)) user.hp = Math.max(0, user.hp - user.maxHp / 4.0);
    }

    private static void afterMoveUser(BattleState s, Action a, Battler user, MoveInfo m, double dealt, double act) {
        if (!user.alive()) return;
        double sg = sign(user.mine);
        if (MoveDex.SELF_KO.contains(m.id)) {
            user.hp = 0;
            return;
        }
        if ("lifeorb".equals(user.item) && dealt > 0 && !user.hasAbility("magicguard") && !user.hasAbility("sheerforce")) {
            user.hp = Math.max(0, user.hp - user.maxHp / 10.0);
        }
        int[] self = MoveDex.SELF_BOOST.get(m.id);
        if (self != null && m.category != Category.STATUS) {
            double chance = m.effectChance > 0 ? m.effectChance : 1.0;
            if (chance >= 0.5) applyBoosts(user, self);
            else s.chanceValue += sg * chance * 0.05;
        }
        int[] drop = MoveDex.SELF_DROP.get(m.id);
        if (drop != null && dealt > 0) applyBoosts(user, drop);
        if (MoveDex.RECHARGE.contains(m.id) && dealt > 0) user.mustRecharge = true;
        if (("rapidspin".equals(m.id) || "mortalspin".equals(m.id)) && dealt > 0) clearHazards(s.field.side(user.mine));
        if (MoveDex.PIVOT.contains(m.id) && !s.bench(user.mine).isEmpty()) s.chanceValue += sg * 0.04;
        if (ItemDex.isChoice(user.item)) user.lockedMove = m.id;
    }

    // ------------------------------------------------------------------ status moves

    private static void applyStatusMove(BattleState s, Action a, Battler user, MoveInfo m, double act) {
        double sg = sign(user.mine);
        String id = m.id;
        SideState mySide = s.field.side(user.mine);
        SideState foeSide = s.field.side(!user.mine);
        Battler target = a.targetSlot >= 0 ? s.active(a.targetMine, a.targetSlot) : null;
        if (target == null || !target.alive()) {
            for (int slot = 0; slot < s.slots(); slot++) {
                Battler c = s.active(!user.mine, slot);
                if (c != null && c.alive()) {
                    target = c;
                    break;
                }
            }
        }
        // Only moves aimed at a foe Pokemon interact with its Protect / Magic Bounce. Self, side and
        // field moves (Swords Dance, Wide Guard, Tailwind, Trick Room, hazards) are never blocked by it.
        boolean aimed = m.target == null || "normal".equals(m.target) || "any".equals(m.target)
            || "adjacentFoe".equals(m.target) || "allAdjacentFoes".equals(m.target) || "randomNormal".equals(m.target)
            || "allAdjacent".equals(m.target);
        boolean targetIsFoe = aimed && target != null && target.mine != user.mine;
        if (targetIsFoe && target.protecting && !MoveDex.PROTECT.contains(id)) return;
        if (targetIsFoe && s.quickGuard[target.mine ? 0 : 1] && Speed.priority(user, m, s.field) > 0) return;
        if (targetIsFoe && target.hasAbility("magicbounce")) {
            s.chanceValue -= sg * 0.05;
            return;
        }
        if (targetIsFoe && target.hasAbility("goodasgold")) return;
        if (user.taunted && !MoveDex.PROTECT.contains(id)) return;

        if (MoveDex.PROTECT.contains(id)) {
            if (user.protectStreak == 0) user.protecting = true;
            else s.chanceValue -= sg * 0.02;
            return;
        }
        if ("substitute".equals(id)) {
            if (!user.substitute && user.hp > user.maxHp / 4.0) {
                user.hp -= user.maxHp / 4.0;
                user.substitute = true;
            }
            return;
        }
        int[] boost = MoveDex.SELF_BOOST.get(id);
        if ("growth".equals(id) && "sun".equals(s.field.weather)) boost = new int[] {2, 0, 2, 0, 0, 0, 0};
        if (boost != null) {
            if ("bellydrum".equals(id)) {
                if (user.hp > user.maxHp / 2.0 && user.boosts[MoveDex.ATK] < 6) {
                    user.hp -= user.maxHp / 2.0;
                    user.boosts[MoveDex.ATK] = 6;
                }
                return;
            }
            if ("filletaway".equals(id)) {
                if (user.hp > user.maxHp / 2.0) {
                    user.hp -= user.maxHp / 2.0;
                    applyBoosts(user, boost);
                }
                return;
            }
            if ("curse".equals(id) && user.hasType("ghost")) {
                if (target != null && targetIsFoe && user.hp > user.maxHp / 2.0) {
                    user.hp -= user.maxHp / 2.0;
                    s.chanceValue += sg * 0.15;
                }
                return;
            }
            if ("clangoroussoul".equals(id)) {
                if (user.hp <= user.maxHp / 3.0) return;
                user.hp -= user.maxHp / 3.0;
            }
            applyBoosts(user, boost);
            return;
        }
        Double heal = MoveDex.HEAL.get(id);
        if (heal != null) {
            if (user.hasAbility("healblock")) return;
            double frac = heal;
            if ("moonlight".equals(id) || "morningsun".equals(id) || "synthesis".equals(id)) {
                if ("sun".equals(s.field.weather)) frac = 2.0 / 3;
                else if (s.field.weather != null) frac = 0.25;
            }
            if ("shoreup".equals(id) && "sand".equals(s.field.weather)) frac = 2.0 / 3;
            if ("rest".equals(id)) {
                if (user.hp >= user.maxHp || AbilityDex.SLEEP_IMMUNE.contains(String.valueOf(user.effectiveAbility())))
                    return;
                user.hp = user.maxHp;
                user.status = "slp";
                user.sleepTurns = 2;
                if ("chestoberry".equals(user.item) || "lumberry".equals(user.item)) {
                    user.status = null;
                    user.item = null;
                }
                return;
            }
            if ("wish".equals(id)) frac *= 0.8;
            if ("strengthsap".equals(id) && target != null) {
                double sap = target.atk * DamageCalc.stageMult(target.boosts[MoveDex.ATK]);
                user.hp = Math.min(user.maxHp, user.hp + sap);
                applyBoosts(target, new int[] {-1, 0, 0, 0, 0, 0, 0});
                return;
            }
            user.hp = Math.min(user.maxHp, user.hp + user.maxHp * frac);
            return;
        }
        String inflict = MoveDex.INFLICT_STATUS.get(id);
        if (inflict != null && target != null) {
            double hit = DamageCalc.hitChance(user, target, m, s.field) * act;
            if ("yawn".equals(id)) {
                if (target.status == null && !target.drowsy && canStatus(s, user, target, "slp", m)) {
                    target.pendingStatusValue += sg * 0.7 * Evaluator.statusValue(target, "slp");
                }
                return;
            }
            switch (inflict) {
                case "cnf" -> {
                    if (!target.confused && !target.hasAbility("owntempo") && !"misty".equals(s.field.terrain)) {
                        if ("swagger".equals(id)) applyBoosts(target, new int[] {2, 0, 0, 0, 0, 0, 0});
                        if (hit >= 0.75) target.confused = true;
                        else s.chanceValue += sg * hit * 0.08;
                    }
                }
                case "seed" -> {
                    if (!target.leechSeeded && !target.hasType("grass")) {
                        if (hit >= 0.75) target.leechSeeded = true;
                        else s.chanceValue += sg * hit * 0.08;
                    }
                }
                case "taunt" -> {
                    if (!target.taunted && !target.hasAbility("oblivious")) target.taunted = true;
                }
                case "encore" -> s.chanceValue += sg * 0.02;
                default -> {
                    if (target.status == null && canStatus(s, user, target, inflict, m)) {
                        if ("slp".equals(inflict) && hit < 0.85) {
                            target.pendingStatusValue += sg * hit * sleepWorth(s, user, target, m);
                        } else if (hit >= 0.85) {
                            setStatus(target, inflict);
                        } else {
                            target.pendingStatusValue += sg * hit * Evaluator.statusValue(target, inflict);
                        }
                    }
                }
            }
            return;
        }
        if (MoveDex.HAZARD.contains(id)) {
            switch (id) {
                case "stealthrock" -> foeSide.stealthRock = true;
                case "spikes" -> foeSide.spikes = Math.min(3, foeSide.spikes + 1);
                case "toxicspikes" -> foeSide.toxicSpikes = Math.min(2, foeSide.toxicSpikes + 1);
                case "stickyweb" -> foeSide.stickyWeb = true;
                default -> { }
            }
            return;
        }
        switch (id) {
            case "defog" -> {
                clearHazards(mySide);
                clearHazards(foeSide);
                foeSide.reflect = foeSide.lightScreen = foeSide.auroraVeil = false;
                return;
            }
            case "tidyup" -> {
                clearHazards(mySide);
                clearHazards(foeSide);
                applyBoosts(user, MoveDex.SELF_BOOST.get("tidyup"));
                return;
            }
            case "courtchange" -> {
                SideState tmp = mySide.copy();
                copyInto(foeSide, mySide);
                copyInto(tmp, foeSide);
                return;
            }
            case "reflect" -> { mySide.reflect = true; return; }
            case "lightscreen" -> { mySide.lightScreen = true; return; }
            case "auroraveil" -> {
                if ("snow".equals(s.field.weather)) mySide.auroraVeil = true;
                return;
            }
            case "trickroom" -> {
                s.field.trickRoom = !s.field.trickRoom;
                s.field.trickRoomTurns = s.field.trickRoom ? 5 : 0;
                s.speedDirty = true;
                return;
            }
            case "tailwind" -> {
                if (!mySide.tailwind) s.speedDirty = true;
                if (!mySide.tailwind) mySide.tailwindTurns = 4;
                mySide.tailwind = true;
                return;
            }
            case "safeguard" -> { mySide.safeguard = true; return; }
            case "haze" -> {
                for (int slot = 0; slot < s.slots(); slot++) {
                    Battler x = s.my(slot), y = s.opp(slot);
                    if (x != null) x.boosts = new int[7];
                    if (y != null) y.boosts = new int[7];
                }
                return;
            }
            case "painsplit" -> {
                if (target != null) {
                    double avg = (user.hp + target.hp) / 2;
                    user.hp = Math.min(user.maxHp, avg);
                    target.hp = Math.min(target.maxHp, avg);
                }
                return;
            }
            case "helpinghand" -> {
                for (int slot = 0; slot < s.slots(); slot++) {
                    Battler ally = s.active(user.mine, slot);
                    if (ally != null && ally != user && ally.alive() && !ally.acted) ally.helped = true;
                }
                return;
            }
            case "followme", "ragepowder", "spotlight" -> {
                s.redirect[user.mine ? 0 : 1] = a.slot;
                return;
            }
            case "wideguard" -> {
                // Gen 9: Wide Guard never fails for being repeated (it only raises the Protect counter).
                user.usedGuard = true;
                s.wideGuard[user.mine ? 0 : 1] = true;
                return;
            }
            case "quickguard" -> {
                user.usedGuard = true;
                s.quickGuard[user.mine ? 0 : 1] = true;
                return;
            }
            case "sleeptalk" -> {
                // Only works asleep: calls one of the other moves at random (expected damage over the pool).
                if (!"slp".equals(user.status) || target == null || !targetIsFoe) return;
                int callable = 0;
                for (MoveInfo o : user.moves) {
                    if (o.usable() && !"sleeptalk".equals(o.id) && !"rest".equals(o.id)) callable++;
                }
                if (callable == 0) return;
                double total = 0;
                for (MoveInfo o : user.moves) {
                    if (!o.usable() || !o.isDamaging() || "sleeptalk".equals(o.id)) continue;
                    DamageCalc.Result r = DamageCalc.calc(user, target, o, s.field, user.mine, s.doubles, false, user.helped);
                    if (r.immune) continue;
                    total += r.avg() * r.hitChance;
                }
                target.hp = Math.max(0, target.hp - total / callable);
                return;
            }
            default -> { }
        }
        String weather = MoveDex.WEATHER_SETTER.get(id);
        if (weather != null) {
            s.field.setWeather(weather, user.item);
            return;
        }
        String terrain = MoveDex.TERRAIN_SETTER.get(id);
        if (terrain != null) {
            s.field.terrain = terrain;
            return;
        }
        if (MoveDex.PHAZE.contains(id) && target != null && targetIsFoe) {
            int positive = 0;
            for (int b : target.boosts) if (b > 0) positive += b;
            target.boosts = new int[7];
            target.substitute = false;
            if (!s.bench(target.mine).isEmpty() || (!target.mine && s.oppUnseenReserves > 0)) {
                s.chanceValue += sg * (0.03 + 0.03 * positive + Evaluator.hazardValue(s.field.side(target.mine)) * 0.5);
            }
            return;
        }
        int[] drop = MoveDex.TARGET_DROP.get(id);
        if (drop != null) {
            List<Battler> ts = new ArrayList<>();
            if (m.isSpread()) {
                for (int slot = 0; slot < s.slots(); slot++) {
                    Battler c = s.active(!user.mine, slot);
                    if (c != null && c.alive()) ts.add(c);
                }
            } else if (target != null) {
                ts.add(target);
            }
            double hit = target == null ? 0 : DamageCalc.hitChance(user, target, m, s.field) * act;
            for (Battler t : ts) {
                if (AbilityDex.STAT_DROP_IMMUNE.contains(String.valueOf(t.effectiveAbility()))) continue;
                if (t.hasAbility("defiant") || t.hasAbility("competitive")) {
                    s.chanceValue -= sg * 0.1;
                    continue;
                }
                if (hit >= 0.75) applyBoosts(t, drop);
                else s.chanceValue += sg * hit * 0.05;
            }
            if ("partingshot".equals(id)) s.chanceValue += sg * 0.04;
            if ("memento".equals(id)) user.hp = 0;
        }
    }

    /**
     * Worth of putting {@code target} to sleep with an inaccurate move (Sleep Powder 75%). It depends on how
     * much that Pokemon threatens our side: sleeping a harmless Wide Guard Hitmontop is not worth Venusaur's
     * attack, sleeping a Calm Mind Lugia is. Unknown abilities (Insomnia, Vital Spirit, Magic Bounce, Overcoat),
     * a possible Lum / Chesto Berry and a trainer switching it out all cut the value.
     */
    static double sleepWorth(BattleState s, Battler user, Battler target, MoveInfo m) {
        double threat = 0;
        for (int k = 0; k < s.slots(); k++) {
            Battler mine = s.active(!target.mine, k);
            if (mine == null || !mine.alive()) continue;
            threat = Math.max(threat, Math.min(1, Evaluator.bestAttack(target, mine, s).dmg / Math.max(1, mine.hp)));
        }
        int boosts = 0;
        for (int b : target.boosts) if (b > 0) boosts += b;
        double worth = 0.08 + 0.32 * threat + 0.05 * Math.min(4, boosts);
        double immune = 0;
        for (String ab : new String[] {"insomnia", "vitalspirit", "sweetveil", "comatose", "purifyingsalt", "magicbounce",
            "goodasgold", "overcoat"}) {
            if ("overcoat".equals(ab) && (m == null || !MoveDex.POWDER.contains(m.id))) continue;
            immune += target.abilityChance(ab);
        }
        double stick = (1 - Math.min(1, immune)) * (target.itemUnknown ? 0.85 : 1.0) * (target.mine ? 1.0 : 0.9);
        return worth * stick * (0.5 + 0.5 * target.hpFrac());
    }

    // ------------------------------------------------------------------ helpers

    static void applyBoosts(Battler b, int[] delta) {
        if (delta == null) return;
        boolean contrary = b.hasAbility("contrary");
        boolean simple = b.hasAbility("simple");
        for (int k = 0; k < delta.length && k < b.boosts.length; k++) {
            int d = delta[k];
            if (d == 0) continue;
            if (contrary) d = -d;
            if (simple) d *= 2;
            b.boosts[k] = Math.max(-6, Math.min(6, b.boosts[k] + d));
        }
    }

    private static void clearHazards(SideState s) {
        s.stealthRock = false;
        s.spikes = 0;
        s.toxicSpikes = 0;
        s.stickyWeb = false;
    }

    private static void copyInto(SideState from, SideState to) {
        to.stealthRock = from.stealthRock;
        to.spikes = from.spikes;
        to.toxicSpikes = from.toxicSpikes;
        to.stickyWeb = from.stickyWeb;
        to.reflect = from.reflect;
        to.lightScreen = from.lightScreen;
        to.auroraVeil = from.auroraVeil;
        to.tailwind = from.tailwind;
        to.tailwindTurns = from.tailwindTurns;
    }

    static void setStatus(Battler t, String status) {
        t.status = status;
        if ("slp".equals(status)) {
            t.sleepTurns = 2;
            t.sleptThisTurn = true;
        }
        if ("tox".equals(status)) t.toxicCounter = 0;
    }

    /** Whether {@code status} can be inflicted on {@code t} (type, ability, terrain and field checks). */
    public static boolean canStatus(BattleState s, Battler user, Battler t, String status, MoveInfo m) {
        if (t.status != null) return false;
        if (s.field.side(t.mine).safeguard && user.mine != t.mine) return false;
        String ab = t.effectiveAbility();
        boolean breaker = user.effectiveAbility() != null && AbilityDex.MOLD_BREAKER.contains(user.effectiveAbility());
        if (!breaker && AbilityDex.statusImmune(ab, status)) return false;
        if (!breaker && "leafguard".equals(ab) && "sun".equals(s.field.weather)) return false;
        if (t.isGrounded(s.field)) {
            if ("misty".equals(s.field.terrain)) return false;
            if ("electric".equals(s.field.terrain) && "slp".equals(status)) return false;
        }
        if (m != null && MoveDex.POWDER.contains(m.id)
            && (t.hasType("grass") || t.hasAbility("overcoat") || "safetygoggles".equals(t.item))) return false;
        // Magic Bounce (Mega Sableye, Espeon, Hatterene) reflects the status move; Good as Gold blocks it (#22 Isidoro:
        // Sleep Powder bounced off Mega Sableye).
        if (m != null && m.category == Category.STATUS && user.mine != t.mine && !breaker
            && (t.abilityChance("magicbounce") >= 0.5 || t.abilityChance("goodasgold") >= 0.5)) return false;
        if (m != null && m.category == Category.STATUS && m.type != null && !"toxic".equals(m.id)
            && TypeChart.against(m.type, t.types) == 0) return false;
        switch (status) {
            case "par" -> { if (t.hasType("electric")) return false; }
            case "brn" -> { if (t.hasType("fire")) return false; }
            case "psn", "tox" -> {
                if ((t.hasType("poison") || t.hasType("steel")) && !user.hasAbility("corrosion")) return false;
            }
            case "frz" -> {
                if (t.hasType("ice") || "sun".equals(s.field.weather)) return false;
            }
            case "slp" -> {
                if (m != null && "darkvoid".equals(m.id) && (user.species == null || !user.species.contains("darkrai"))) return false;
            }
            default -> { }
        }
        if (m != null && m.category == Category.STATUS && user.hasAbility("prankster") && t.hasType("dark")
            && t.mine != user.mine) return false;
        return true;
    }

    static void doSwitch(BattleState s, boolean mine, int slot, int teamIdx) {
        Battler old = s.active(mine, slot);
        if (old != null) old.onSwitchOut();
        s.activeIdx(mine)[slot] = teamIdx;
        Battler n = s.team(mine).get(teamIdx);
        n.turnsActive = 0;
        n.acted = true;
        entryHazards(s, n);
        entryAbility(s, n);
    }

    static void entryHazards(BattleState s, Battler n) {
        if ("heavydutyboots".equals(n.item)) return;
        SideState side = s.field.side(n.mine);
        boolean magicGuard = n.hasAbility("magicguard");
        if (side.stealthRock && !magicGuard) {
            n.hp -= n.maxHp / 8.0 * TypeChart.against("rock", n.types);
        }
        if (n.isGrounded(s.field)) {
            if (side.spikes > 0 && !magicGuard) {
                double f = side.spikes == 1 ? 1.0 / 8 : side.spikes == 2 ? 1.0 / 6 : 1.0 / 4;
                n.hp -= n.maxHp * f;
            }
            if (side.toxicSpikes > 0) {
                if (n.hasType("poison")) side.toxicSpikes = 0;
                else if (n.status == null && !n.hasType("steel") && !AbilityDex.statusImmune(n.effectiveAbility(), "psn"))
                    setStatus(n, side.toxicSpikes >= 2 ? "tox" : "psn");
            }
            if (side.stickyWeb) applyBoosts(n, new int[] {0, 0, 0, 0, -1, 0, 0});
        }
        n.hp = Math.max(0, n.hp);
    }

    static void entryAbility(BattleState s, Battler n) {
        String ab = n.effectiveAbility();
        if (ab == null || !n.alive()) return;
        if ("intimidate".equals(ab)) {
            for (int slot = 0; slot < s.slots(); slot++) {
                Battler foe = s.active(!n.mine, slot);
                if (foe == null || !foe.alive()) continue;
                String fa = foe.effectiveAbility();
                if (fa != null && (AbilityDex.STAT_DROP_IMMUNE.contains(fa) || "innerfocus".equals(fa)
                    || "oblivious".equals(fa) || "owntempo".equals(fa) || "scrappy".equals(fa))) continue;
                if ("defiant".equals(fa)) {
                    applyBoosts(foe, new int[] {2, 0, 0, 0, 0, 0, 0});
                    continue;
                }
                if ("competitive".equals(fa)) {
                    applyBoosts(foe, new int[] {0, 0, 2, 0, 0, 0, 0});
                    continue;
                }
                applyBoosts(foe, new int[] {-1, 0, 0, 0, 0, 0, 0});
            }
        }
        String w = AbilityDex.ENTRY_WEATHER.get(ab);
        if (w != null) {
            if ("primordialsea".equals(ab) || "desolateland".equals(ab)) s.field.setPrimal(w);
            else s.field.setWeather(w, n.item);
        }
        String t = AbilityDex.ENTRY_TERRAIN.get(ab);
        if (t != null) s.field.terrain = t;
    }

    private static boolean aliveByUuid(BattleState s, java.util.UUID id) {
        if (id == null) return false;
        for (boolean side : new boolean[] {true, false})
            for (Battler x : s.team(side)) if (id.equals(x.uuid)) return x.alive();
        return false;
    }

    private static void endOfTurn(BattleState s) {
        // Status chances only pay off on Pokemon that survived the turn.
        for (boolean side : new boolean[] {true, false}) {
            for (Battler b : s.team(side)) {
                if (b.pendingStatusValue != 0 && b.alive()) s.chanceValue += b.pendingStatusValue;
                b.pendingStatusValue = 0;
                if (b.pendingChargeValue != 0 && b.alive() && aliveByUuid(s, b.chargeTarget)) s.chanceValue += b.pendingChargeValue;
                b.pendingChargeValue = 0;
                b.chargeTarget = null;
            }
        }
        Field f = s.field;
        for (boolean mine : new boolean[] {true, false}) {
            for (int slot = 0; slot < s.slots(); slot++) {
                Battler b = s.active(mine, slot);
                if (b == null) continue;
                if (b.alive()) residual(s, f, b);
                if (b.alive() && b.drowsy) {
                    // Yawned last turn and still on the field: it falls asleep now (1-3 turns).
                    b.drowsy = false;
                    if (b.status == null && !"electric".equals(f.terrain) && !"misty".equals(f.terrain)) {
                        b.status = "slp";
                        b.sleepTurns = 2;
                    }
                }
                b.protectStreak = (b.protecting || b.usedGuard) ? b.protectStreak + 1 : 0;
                b.protecting = false;
                b.usedGuard = false;
                b.flinched = false;
                b.helped = false;
                b.acted = false;
                b.sleptThisTurn = false;
                b.turnsActive++;
            }
        }
        s.redirect[0] = s.redirect[1] = -1;
        s.wideGuard[0] = s.wideGuard[1] = false;
        s.quickGuard[0] = s.quickGuard[1] = false;
        // Tailwind and Trick Room run out (an unknown count is taken as three turns left).
        for (SideState side : new SideState[] {f.mine, f.theirs}) {
            if (!side.tailwind) continue;
            if (side.tailwindTurns <= 0) side.tailwindTurns = 3;
            if (--side.tailwindTurns <= 0) {
                side.tailwind = false;
                s.speedDirty = true;
            }
        }
        if (f.trickRoom) {
            if (f.trickRoomTurns <= 0) f.trickRoomTurns = 3;
            if (--f.trickRoomTurns <= 0) f.trickRoom = false;
        }
        if (f.primal) {
            // Primal weather ends as soon as no Pokemon with the ability is on the field (Kyogre fainted).
            boolean source = false;
            for (boolean side : new boolean[] {true, false})
                for (int k = 0; k < s.slots(); k++) {
                    Battler b = s.active(side, k);
                    if (b != null && b.alive() && primalSource(b)) source = true;
                }
            if (!source) {
                f.weather = null;
                f.weatherTurns = 0;
                f.primal = false;
            }
        } else if (f.weather != null) {
            f.weatherTurns--;
            if (f.weatherTurns <= 0) {
                f.weather = null;
                f.weatherTurns = 0;
            }
        }
    }

    /**
     * The Pokemon keeping Primordial Sea / Desolate Land up. A Primal Kyogre whose ability was not read (or that is
     * listed with its base form's abilities) still counts: otherwise the rain "ended" in every simulated turn and
     * knocking Kyogre out to get the sun back looked worth nothing.
     */
    static boolean primalSource(Battler b) {
        if (b.abilityChance("primordialsea") > 0 || b.abilityChance("desolateland") > 0) return true;
        if (b.ability != null) return false;
        String sp = b.species == null ? "" : b.species.toLowerCase(java.util.Locale.ROOT);
        return sp.startsWith("kyogre") || sp.startsWith("groudon");
    }

    private static void residual(BattleState s, Field f, Battler b) {
        boolean mg = b.hasAbility("magicguard");
        double max = b.maxHp;
        if ("sand".equals(f.weather) && !mg && !b.hasType("rock") && !b.hasType("ground") && !b.hasType("steel")
            && !b.hasAbility("sandveil") && !b.hasAbility("sandrush") && !b.hasAbility("sandforce")
            && !b.hasAbility("overcoat") && !"safetygoggles".equals(b.item)) {
            b.hp -= max / 16;
        }
        String wab = b.effectiveAbility();
        if (wab != null) {
            if ("hydration".equals(wab) && "rain".equals(f.weather)) b.status = null;
            if (!mg && "sun".equals(f.weather) && ("solarpower".equals(wab) || "dryskin".equals(wab))) b.hp -= max / 8;
            if ("rain".equals(f.weather)) {
                if ("dryskin".equals(wab)) b.hp += max / 8;
                else if ("raindish".equals(wab)) b.hp += max / 16;
            }
            if ("snow".equals(f.weather) && "icebody".equals(wab)) b.hp += max / 16;
        }
        if (b.status != null && !mg) {
            switch (b.status) {
                case "brn" -> b.hp -= max / 16;
                case "psn" -> b.hp += b.hasAbility("poisonheal") ? max / 8 : -max / 8;
                case "tox" -> {
                    b.toxicCounter++;
                    b.hp += b.hasAbility("poisonheal") ? max / 8 : -max * Math.min(15, b.toxicCounter) / 16;
                }
                default -> { }
            }
        }
        if (b.leechSeeded && !mg) {
            double drained = Math.min(b.hp, max / 8);
            b.hp -= drained;
            for (int slot = 0; slot < s.slots(); slot++) {
                Battler foe = s.active(!b.mine, slot);
                if (foe != null && foe.alive()) {
                    foe.hp = Math.min(foe.maxHp, foe.hp + drained);
                    break;
                }
            }
        }
        if ("leftovers".equals(b.item)) b.hp += max / 16;
        if ("blacksludge".equals(b.item)) b.hp += b.hasType("poison") ? max / 16 : -max / 8;
        if ("grassy".equals(f.terrain) && b.isGrounded(f)) b.hp += max / 16;
        if (b.hasAbility("speedboost") && b.turnsActive > 0) applyBoosts(b, new int[] {0, 0, 0, 0, 1, 0, 0});
        b.hp = Math.max(0, Math.min(b.maxHp, b.hp));
    }
}
