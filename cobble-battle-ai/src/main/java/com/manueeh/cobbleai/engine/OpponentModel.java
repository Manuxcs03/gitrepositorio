package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Field;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Predicts what a side we do not control will do this turn. Wild Pokémon in Cobblemon pick
 * moves at random; trainers and players are modelled as mostly greedy.
 */
public final class OpponentModel {
    private OpponentModel() {}

    public record Weighted(Action action, double prob, double score) {}

    /** Distribution over the actions of the battler in {@code slot} of side {@code mine}. */
    public static List<Weighted> policy(BattleState s, boolean mine, int slot, BattleState.OpponentKind kind) {
        Battler self = s.active(mine, slot);
        List<Weighted> out = new ArrayList<>();
        if (self == null || !self.alive()) return out;
        List<Weighted> scored = new ArrayList<>();
        for (MoveInfo m : self.moves) {
            if (!m.usable()) continue;
            if (self.lockedMove != null && !self.lockedMove.equals(m.id)) continue;
            if (self.taunted && m.category == Category.STATUS) continue;
            // Single-target status moves (Will-O-Wisp, Spore...) pick their target too: aimed only at slot 0 they
            // were never predicted on the Pokemon next to it.
            boolean aimedStatus = m.category == Category.STATUS && m.needsTargetChoice() && !m.isSpread()
                && MoveDex.INFLICT_STATUS.containsKey(m.id);
            if ((m.category != Category.STATUS || aimedStatus) && !m.isSpread() && s.doubles) {
                for (int t = 0; t < s.slots(); t++) {
                    Battler target = s.active(!mine, t);
                    if (target == null || !target.alive()) continue;
                    scored.add(new Weighted(Action.move(mine, slot, m, !mine, t), 0, score(s, self, m, target)));
                }
            } else {
                int t = firstAlive(s, !mine);
                Battler target = t >= 0 ? s.active(!mine, t) : null;
                double sc = 0;
                if (m.isSpread() && m.category != Category.STATUS) {
                    for (int k = 0; k < s.slots(); k++) {
                        Battler x = s.active(!mine, k);
                        if (x != null && x.alive()) sc += score(s, self, m, x) * (s.doubles ? 0.75 : 1);
                    }
                    if (m.hitsAlly()) {
                        // Friendly fire (Earthquake next to a grounded partner) is a real cost.
                        for (int k = 0; k < s.slots(); k++) {
                            Battler ally = s.active(mine, k);
                            if (ally == null || ally == self || !ally.alive()) continue;
                            sc -= 1.3 * score(s, self, m, ally) * 0.75;
                        }
                    }
                } else if (target != null) {
                    sc = score(s, self, m, target);
                }
                scored.add(new Weighted(Action.move(mine, slot, m, !mine, Math.max(t, 0)), 0, sc));
            }
        }
        if (scored.isEmpty()) {
            MoveInfo struggle = new MoveInfo("struggle");
            struggle.type = "normal";
            struggle.category = Category.PHYSICAL;
            struggle.power = 50;
            struggle.accuracy = 1;
            struggle.target = "randomNormal";
            int t = Math.max(0, firstAlive(s, !mine));
            out.add(new Weighted(Action.move(mine, slot, struggle, !mine, t), 1, 0));
            return out;
        }
        scored.sort(Comparator.comparingDouble((Weighted w) -> -w.score()));

        if (kind == BattleState.OpponentKind.WILD) {
            // Random move choice; keep each move once (best target) with equal weight.
            List<Weighted> perMove = new ArrayList<>();
            List<String> seen = new ArrayList<>();
            for (Weighted w : scored) {
                if (seen.contains(w.action().move.id)) continue;
                seen.add(w.action().move.id);
                perMove.add(w);
            }
            double p = 1.0 / perMove.size();
            for (Weighted w : perMove) out.add(new Weighted(w.action(), p, w.score()));
            return out;
        }

        // Measured on the Tower logs: trainers are predictable in WHICH move they pick (their best one they
        // really have) but loose in WHICH target (turn-1 hits: Venusaur 41, Torkoal 35 in 76 fights). So the
        // move comes from an existence cascade over the ranking, and only the target is spread by a softmax.
        double temperature = kind == BattleState.OpponentKind.PLAYER ? 0.08 : 0.2;
        double targetNoise = kind == BattleState.OpponentKind.PLAYER ? 0.2 : 0.4;
        java.util.Map<String, Double> moveMass = new java.util.HashMap<>();
        java.util.Map<String, Integer> bestEntry = new java.util.HashMap<>();
        double remaining = 1;
        for (int i = 0; i < scored.size(); i++) {
            MoveInfo mi = scored.get(i).action().move;
            if (bestEntry.containsKey(mi.id)) continue;
            bestEntry.put(mi.id, i);
            double e = existence(self, mi);
            moveMass.put(mi.id, remaining * e);
            remaining *= 1 - e;
        }
        if (remaining > 0) {
            String first = scored.get(0).action().move.id;
            moveMass.put(first, moveMass.get(first) + remaining);
        }
        java.util.Map<String, Double> targetSum = new java.util.HashMap<>();
        double[] tw = new double[scored.size()];
        for (int i = 0; i < scored.size(); i++) {
            String id = scored.get(i).action().move.id;
            double best = scored.get(bestEntry.get(id)).score();
            tw[i] = Math.exp((scored.get(i).score() - best) / temperature);
            targetSum.merge(id, tw[i], Double::sum);
        }
        double[] prob = new double[scored.size()];
        for (int i = 0; i < scored.size(); i++) {
            String id = scored.get(i).action().move.id;
            double share = (1 - targetNoise) * (bestEntry.get(id) == i ? 1 : 0) + targetNoise * tw[i] / targetSum.get(id);
            prob[i] = moveMass.get(id) * share;
        }
        // Some slack between moves too: with a fully revealed set the cascade alone is certain of one move, yet
        // #10 Casimiro's Hitmontop picked Wide Guard over a Close Combat that "should" have KO'd Garchomp.
        double moveNoise = kind == BattleState.OpponentKind.PLAYER ? 0.1 : 0.2;
        double top2 = scored.get(0).score();
        double[] mv = new double[scored.size()];
        double mvSum = 0;
        for (int i = 0; i < scored.size(); i++) {
            mv[i] = Math.exp((scored.get(i).score() - top2) / 0.35) * existence(self, scored.get(i).action().move);
            mvSum += mv[i];
        }
        if (mvSum > 0) for (int i = 0; i < scored.size(); i++) prob[i] = (1 - moveNoise) * prob[i] + moveNoise * mv[i] / mvSum;
        stickyWideGuard(s, self, scored, prob);
        openingTailwind(s, self, scored, prob);
        wispHabit(s, self, scored, prob);
        Integer[] order = new Integer[scored.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(prob[b], prob[a]));
        for (int k = 0; k < Math.min(4, order.length); k++) {
            int i = order[k];
            if (prob[i] >= 0.05) out.add(new Weighted(scored.get(i).action(), prob[i], scored.get(i).score()));
        }
        double norm = 0;
        for (Weighted x : out) norm += x.prob();
        List<Weighted> normalised = new ArrayList<>();
        for (Weighted x : out) normalised.add(new Weighted(x.action(), x.prob() / norm, x.score()));
        return withRetreat(s, self, slot, normalised);
    }

    /** Chance that a foe on its last HP retreats: 24 of 55 Focus Sash survivors in the logs switched out next turn. */
    private static final double RETREAT_LOW_HP = 0.4;
    /** Chance that a human player switches out of a clearly lost matchup when the bench has an answer. */
    private static final double RETREAT_OUTMATCHED = 0.3;

    /**
     * Tower trainers pull a Pokemon that is about to faint (Whimsicott after holding on with its Sash): our
     * attack into that slot then hits the switch-in. Only when a known bench member can come in.
     */
    private static List<Weighted> withRetreat(BattleState s, Battler self, int slot, List<Weighted> moves) {
        if (self.mine || s.oppKind == BattleState.OpponentKind.WILD || moves.isEmpty()) return moves;
        boolean low = self.hpFrac() <= 0.1;
        // A human also pulls a Pokemon that plainly loses the matchup (MainTick's Gardevoir left as soon as our
        // Weavile came in, and Blaziken took the Knock Off).
        boolean outmatched = false;
        if (s.oppKind == BattleState.OpponentKind.PLAYER && !low) {
            double r = 0;
            int m = 0;
            for (int k = 0; k < s.slots(); k++) {
                Battler foe = s.active(!self.mine, k);
                if (foe == null || !foe.alive()) continue;
                r += Evaluator.race(self, foe, s);
                m++;
            }
            outmatched = m > 0 && r / m < -0.5;
        }
        if (!low && !outmatched) return moves;
        int best = -1;
        double bestScore = -9;
        for (int idx : s.bench(self.mine)) {
            Battler cand = s.team(self.mine).get(idx);
            double sc = 0;
            int n = 0;
            for (int k = 0; k < s.slots(); k++) {
                Battler foe = s.active(!self.mine, k);
                if (foe == null || !foe.alive()) continue;
                sc += Evaluator.race(cand, foe, s);
                n++;
            }
            sc = n == 0 ? 0 : sc / n;
            if (sc > bestScore) {
                bestScore = sc;
                best = idx;
            }
        }
        if (best < 0 || (outmatched && !low && bestScore < 0.1)) return moves;
        double p = low ? RETREAT_LOW_HP : RETREAT_OUTMATCHED;
        List<Weighted> out = new ArrayList<>();
        for (Weighted w : moves) out.add(new Weighted(w.action(), w.prob() * (1 - p), w.score()));
        out.add(new Weighted(Action.switchTo(self.mine, slot, best), p, 0));
        return out;
    }

    /**
     * Share of Tower appearances that used Tailwind, per species (Whimsicott 17/28, Crobat 6/12, Tornadus 4/7,
     * Pelipper 5/12, Aerodactyl 3/8). Other flyers (Charizard 0/34, Salamence 0/17, Togekiss 0/11) never did.
     */
    private static final java.util.Map<String, Double> TAILWIND_USERS = java.util.Map.of(
        "whimsicott", 0.6, "crobat", 0.5, "tornadus", 0.55, "pelipper", 0.45, "aerodactyl", 0.4,
        "suicune", 0.4, "zapdos", 0.4, "mandibuzz", 0.2, "talonflame", 0.4, "murkrow", 0.4);

    /**
     * A Pokemon that carries Tailwind opens with it: 31 of 39 Tower Tailwind users made it their first move
     * (#30 Aureo: Crobat set it on turn 1 while the ranking expected Brave Bird, and the fast foes then took
     * three of our Pokemon). The score ranking alone put it near 5%.
     */
    private static void openingTailwind(BattleState s, Battler self, List<Weighted> scored, double[] prob) {
        if (s.field.side(self.mine).tailwind || self.turnsActive > 0 || s.field.trickRoom) return;
        // A nearly fainted one goes for damage instead (Kaprus_ #11: a 1% Whimsicott Moonblasted our Garchomp).
        if (self.hpFrac() < 0.3) return;
        int tw = -1;
        for (int i = 0; i < scored.size(); i++) if ("tailwind".equals(scored.get(i).action().move.id)) { tw = i; break; }
        if (tw < 0) return;
        double target = 0.8 * existence(self, scored.get(tw).action().move);
        double cur = prob[tw];
        if (cur >= target || cur >= 1) return;
        double scale = (1 - target) / (1 - cur);
        for (int i = 0; i < scored.size(); i++) prob[i] = i == tw ? target : prob[i] * scale;
    }

    /**
     * A foe that carries Will-O-Wisp used it 36 times out of 76 while our Garchomp stood unburned in front of it
     * (Kaprus_ #8 Cira: Arcanine burned the Garchomp that had just switched in). The score ranking gave it ~0%.
     */
    private static void wispHabit(BattleState s, Battler self, List<Weighted> scored, double[] prob) {
        if (self.mine) return;
        int best = -1;
        double bestAtk = 0, cur = 0;
        for (int i = 0; i < scored.size(); i++) {
            Action a = scored.get(i).action();
            if (!"willowisp".equals(a.move.id)) continue;
            cur += prob[i];
            if (!a.move.revealed) continue;
            Battler t = a.targetSlot >= 0 ? s.active(a.targetMine, a.targetSlot) : null;
            if (t == null || !t.alive() || t.atk <= t.spa || t.status != null) continue;
            if (!TurnSimulator.canStatus(s, self, t, "brn", a.move)) continue;
            if (t.atk > bestAtk) {
                bestAtk = t.atk;
                best = i;
            }
        }
        double target = 0.45;
        if (best < 0 || cur >= target) return;
        double scale = (1 - target) / Math.max(1e-9, 1 - cur);
        for (int i = 0; i < scored.size(); i++)
            if ("willowisp".equals(scored.get(i).action().move.id)) prob[i] = i == best ? target : 0;
            else prob[i] *= scale;
    }

    /**
     * Measured on the Tower logs: after a Wide Guard the same foe used it again 71% of the time (74% after two
     * in a row, 92% after three). The score ranking alone gave it 6% (#12 Talia: Rock Slide into the third
     * Wide Guard in a row, then Venusaur and Garchomp fell to Heat Wave).
     */
    private static void stickyWideGuard(BattleState s, Battler self, List<Weighted> scored, double[] prob) {
        if (!s.doubles) return;
        boolean justUsed = "wideguard".equals(self.lastMove);
        // A known Wide Guard user (memory / bundled Tower sets) reaches for it 66 times out of 78 on its second
        // action; only its Fake Out turn comes first (Talia #13: Rock Slide locked into five Wide Guards).
        boolean fakeOutTurn = false;
        if (self.turnsActive == 0)
            for (MoveInfo m : self.moves) fakeOutTurn |= MoveDex.FIRST_TURN_ONLY.contains(m.id) && m.usable();
        boolean habitual = self.wideGuardUses > 0 && !fakeOutTurn;
        if (!justUsed && !habitual) return;
        boolean spreadThreat = false;
        for (int k = 0; k < s.slots(); k++) {
            Battler foe = s.active(!self.mine, k);
            if (foe == null || !foe.alive()) continue;
            for (MoveInfo fm : foe.moves) spreadThreat |= fm.usable() && fm.isDamaging() && fm.isSpread();
        }
        if (!spreadThreat) return;
        int wg = -1;
        double cur = 0;
        for (int i = 0; i < scored.size(); i++) {
            if (!"wideguard".equals(scored.get(i).action().move.id)) continue;
            if (wg < 0) wg = i;
            cur += prob[i];
        }
        double target = justUsed ? 0.65 + 0.07 * Math.min(3, Math.max(0, self.lastMoveStreak - 1)) : 0.75;
        if (wg < 0 || cur >= target || cur >= 1) return;
        double scale = (1 - target) / (1 - cur);
        for (int i = 0; i < scored.size(); i++)
            if ("wideguard".equals(scored.get(i).action().move.id)) prob[i] = i == wg ? target : 0;
            else prob[i] *= scale;
    }

    private static int firstAlive(BattleState s, boolean mine) {
        for (int i = 0; i < s.slots(); i++) {
            Battler b = s.active(mine, i);
            if (b != null && b.alive()) return i;
        }
        return -1;
    }

    /**
     * Wide Guard is worth it when the other side has spread moves that hurt this side. A trainer that
     * already used it keeps doing it: unlike Protect it does not fail when repeated (seen in the logs:
     * a Hitmontop kept it up for 7 turns in a row).
     */
    private static double wideGuardScore(BattleState s, Battler self) {
        if (!s.doubles) return 0;
        double threat = 0;
        for (int k = 0; k < s.slots(); k++) {
            Battler foe = s.active(!self.mine, k);
            if (foe == null || !foe.alive()) continue;
            for (MoveInfo fm : foe.moves) {
                if (!fm.usable() || !fm.isDamaging() || !fm.isSpread()) continue;
                double hurt = 0;
                for (int j = 0; j < s.slots(); j++) {
                    Battler mate = s.active(self.mine, j);
                    if (mate == null || !mate.alive()) continue;
                    DamageCalc.Result r = DamageCalc.calc(foe, mate, fm, s.field, foe.mine, true, true, false);
                    hurt += Math.min(1, r.expected() / Math.max(1, mate.hp));
                }
                threat = Math.max(threat, hurt);
            }
        }
        double sc = 0.05 + 0.4 * Math.min(1, threat / 1.2);
        // A foe locked into a spread move (Choice Scarf Rock Slide) makes Wide Guard an obvious answer.
        for (int k = 0; k < s.slots(); k++) {
            Battler foe = s.active(!self.mine, k);
            if (foe == null || !foe.alive() || foe.lockedMove == null) continue;
            for (MoveInfo fm : foe.moves) if (fm.id.equals(foe.lockedMove) && fm.isDamaging() && fm.isSpread()) sc += 0.5;
        }
        // A trainer that already showed the move keeps reaching for it.
        if (self.wideGuardUses > 0) sc += 0.3 + 0.1 * Math.min(3, self.wideGuardUses);
        if ("wideguard".equals(self.lastMove)) sc += 0.15 * Math.min(3, self.lastMoveStreak);
        return sc;
    }

    /** Quick Guard: worth it against priority attacks (Fake Out on the first turn, Extreme Speed...). */
    private static double quickGuardScore(BattleState s, Battler self) {
        if (!s.doubles) return 0;
        double threat = 0;
        for (int k = 0; k < s.slots(); k++) {
            Battler foe = s.active(!self.mine, k);
            if (foe == null || !foe.alive()) continue;
            for (MoveInfo fm : foe.moves) {
                if (!fm.usable() || !fm.isDamaging()) continue;
                if (MoveDex.FIRST_TURN_ONLY.contains(fm.id) && foe.turnsActive > 0) continue;
                if (Speed.priority(foe, fm, s.field) <= 0) continue;
                double hurt = 0;
                for (int j = 0; j < s.slots(); j++) {
                    Battler mate = s.active(self.mine, j);
                    if (mate == null || !mate.alive()) continue;
                    DamageCalc.Result r = DamageCalc.calc(foe, mate, fm, s.field, foe.mine, true, false, false);
                    hurt = Math.max(hurt, Math.min(1, r.expected() / Math.max(1, mate.hp)));
                }
                threat = Math.max(threat, hurt + (MoveDex.FLINCH_ALWAYS.contains(fm.id) ? 0.3 : 0));
            }
        }
        double sc = 0.05 + 0.5 * Math.min(1, threat);
        if (self.quickGuardUses > 0) sc += 0.25;
        return sc;
    }

    /**
     * Trainer AI habit seen in the Battle Tower logs: a foe keeps firing the same attack at the same
     * target, and above all right after that target blocked it with Protect (#31: Lugia Psychic and
     * Garchomp Dragon Claw both went into Venusaur's Protect, then straight into Venusaur again).
     */
    static double repeatTargetBonus(Battler self, MoveInfo m, Battler target) {
        if (self.mine || target == null) return 0;
        // Spread attacks have no single target: a trainer that fired one keeps firing it (#18 Bruna: Mega
        // Mawile Rock Slide on turns 1 and 2).
        if (m.isSpread() && m.id.equals(self.lastMove)) return 0.15;
        if (self.lastTarget == null || target.uuid == null) return 0;
        if (!self.lastTarget.equals(target.uuid)) return 0;
        double bonus = m.id.equals(self.lastMove) ? 0.2 : 0.08;
        if (target.protectStreak > 0) bonus += 0.1;
        return bonus;
    }

    /**
     * How much a move we only guessed should count. Measured on 37 Battle Tower fights: just 6% of the
     * guessed moves were ever used, and 84% of the moves trainers really used had not been guessed. A
     * guessed STAB (or the generic STAB placeholder) is still very likely; guessed coverage, setup and
     * support moves (Helping Hand, Tailwind, Ice Punch...) mostly are not there.
     */
    public static double existence(Battler self, MoveInfo m) {
        if (m.revealed) return 1;
        if (m.id.startsWith("phantom")) return 0.85;
        boolean stab = m.isDamaging() && self.hasType(m.type);
        // Priors measured on the Battle Tower logs (guessed -> later seen): Fake Out 5/9, Protect 16/125,
        // Tailwind 2/22, Helping Hand 0/49, Swords Dance 0/27, coverage moves (Ice Beam, Stone Edge...) ~1/20.
        double e;
        // Rock Slide is the damaging move Tower trainers used most (10 times in 51 fights), and it is 4x on
        // Charizard: a guessed one is far likelier than generic coverage (#7 Cesar: Krookodile).
        if ("rockslide".equals(m.id)) e = 0.5;
        else if (MoveDex.FIRST_TURN_ONLY.contains(m.id)) e = 0.55;
        else if (stab) e = 0.6;
        else if ("slp".equals(MoveDex.INFLICT_STATUS.get(m.id))) e = 0.4;
        else if (m.isDamaging()) e = 0.35;
        else if (MoveDex.PROTECT.contains(m.id)) e = 0.35;
        else if ("helpinghand".equals(m.id) || MoveDex.SELF_BOOST.containsKey(m.id)) e = 0.1;
        else if ("tailwind".equals(m.id)) {
            e = TAILWIND_USERS.getOrDefault(self.species == null ? "" : self.species.toLowerCase(), 0.1);
            // Any Prankster support Pokemon, not just the species seen so far, is a likely Tailwind setter.
            if (self.abilityChance("prankster") > 0) e = Math.max(e, 0.45);
        }
        else if ("wideguard".equals(m.id) || "quickguard".equals(m.id)) e = 0.15;
        else e = 0.25;
        int revealed = 0;
        for (MoveInfo x : self.moves) if (x.revealed) revealed++;
        if (revealed >= 3) e *= 0.5;
        else if (revealed == 2) e *= 0.8;
        // Sets rarely carry two attacks of the same type and category: a shown Dragon Claw makes a guessed Outrage
        // unlikely. A spread + single-target pair (Rock Slide + Stone Edge) is the common exception.
        if (m.isDamaging()) {
            for (MoveInfo x : self.moves) {
                if (!x.revealed || !x.isDamaging() || x.id.equals(m.id)) continue;
                if (java.util.Objects.equals(x.type, m.type) && x.category == m.category && x.isSpread() == m.isSpread()) {
                    e *= 0.5;
                    break;
                }
            }
        }
        // A trainer repeating the same known move turn after turn (#28 Herminia: Hitmontop Wide Guard 5 turns
        // in a row) is not hiding a better attack: its guessed moves are unlikely.
        if (self.lastMove != null && self.lastMoveStreak >= 2) e *= 0.4;
        // Revealed preference: it already attacked with a shown move instead of this guessed one (#31 Garchomp
        // chose Dragon Claw into Venusaur with Torkoal on the field, so the feared 4x Rock Slide was never there).
        if (m.isDamaging() && self.lastMove != null && !self.lastMove.equals(m.id)) {
            for (MoveInfo x : self.moves) {
                if (x.id.equals(self.lastMove) && x.revealed && x.isDamaging()) {
                    e *= 0.6;
                    break;
                }
            }
        }
        return e;
    }

    public static double guessFactor(Battler self, MoveInfo m) {
        if (m.revealed) return 1;
        if (m.id.startsWith("phantom")) return 0.9;
        if (MoveDex.FIRST_TURN_ONLY.contains(m.id)) return 0.9;
        boolean stab = m.isDamaging() && self.hasType(m.type);
        double f;
        if (stab) f = 0.85;
        else if (MoveDex.INFLICT_STATUS.containsKey(m.id) && "slp".equals(MoveDex.INFLICT_STATUS.get(m.id))) f = 0.75;
        else if (m.isDamaging()) f = 0.75;
        else f = 0.5;
        int revealed = 0;
        for (MoveInfo x : self.moves) if (x.revealed) revealed++;
        // With most of the set already shown, there is little room left for each guess.
        if (revealed >= 3) f *= 0.75;
        else if (revealed == 2) f *= 0.9;
        return f;
    }

    /**
     * A guessed move that would clearly have beaten the attack the foe actually chose, on the very target
     * it chose, is probably not in its set: the trainer AI goes for its strongest hit. (#31: Lugia used
     * Psychic on Venusaur twice, so the guessed Brave Bird it "should" have used is not there.)
     */
    static boolean skippedGuess(BattleState s, Battler self, MoveInfo m) {
        if (self.mine || self.lastMove == null || self.lastTarget == null || m.id.equals(self.lastMove)) return false;
        MoveInfo used = null;
        for (MoveInfo x : self.moves) if (x.id.equals(self.lastMove)) used = x;
        if (used == null || !used.revealed || !used.isDamaging() || used.isSpread()) return false;
        Battler target = null;
        for (Battler b : s.myTeam) if (self.lastTarget.equals(b.uuid)) target = b;
        if (target == null || !target.alive()) return false;
        DamageCalc.Result mine = DamageCalc.calc(self, target, m, s.field, false, s.doubles, false, false);
        DamageCalc.Result chosen = DamageCalc.calc(self, target, used, s.field, false, s.doubles, false, false);
        return mine.expected() * (m.isSpread() && s.doubles ? 0.75 : 1) > chosen.expected() * 1.15;
    }

    /** Quick heuristic desirability of a move from the user's own perspective. */
    static double score(BattleState s, Battler self, MoveInfo m, Battler target) {
        if (m.category != Category.STATUS) {
            if (target == null) return 0;
            if (MoveDex.FIRST_TURN_ONLY.contains(m.id) && self.turnsActive > 0) return 0;
            // Tower trainers rank hits by plain power: Nacho's Kingdra opened with Hurricane in the sun (50% to hit)
            // 8 times in 20 fights, more than the sun-boosted Weather Ball (5). Judge their hits as if they land,
            // without the weather (primal weather still makes moves fail).
            boolean npc = !self.mine && s.oppKind == BattleState.OpponentKind.NPC;
            Field fld = s.field;
            if (npc && fld.weather != null && !fld.primal) {
                fld = fld.copy();
                fld.weather = null;
            }
            DamageCalc.Result r = DamageCalc.calc(self, target, m, fld, self.mine, s.doubles, false, false);
            double exp = npc ? r.avg() * (r.hitChance > 0 ? 1 : 0) : r.expected();
            double frac = Math.min(1, exp / Math.max(1, target.hp));
            double ko = npc ? r.koChanceOnHit(target.hp) * (r.hitChance > 0 ? 1 : 0) : r.koChance(target.hp);
            double sc = frac * 0.7 + ko * 0.5;
            // Strong NPC AI goes for the biggest hit, not just any KO: reward overkill and super effectiveness
            // (Mega Gardevoir picks Psychic on Venusaur over an equally lethal Psychic on Torkoal).
            double raw = Math.min(2, exp / Math.max(1, target.hp));
            sc += 0.15 * Math.max(0, raw - 1);
            if (r.effectiveness > 1) sc += 0.15 * (Math.log(r.effectiveness) / Math.log(2));
            else if (r.effectiveness < 1 && r.effectiveness > 0) sc -= 0.1;
            if (MoveDex.FLINCH_ALWAYS.contains(m.id) && r.hitChance > 0 && !target.hasAbility("innerfocus")) {
                // Fake Out: worth the target's whole turn. Aimed at the foe whose move matters most.
                double threat = 0;
                for (int k = 0; k < s.slots(); k++) {
                    Battler mate = s.active(self.mine, k);
                    if (mate == null || !mate.alive()) continue;
                    Evaluator.Attack a = Evaluator.bestAttack(target, mate, s);
                    threat = Math.max(threat, Math.min(1, a.dmg / Math.max(1, mate.hp)));
                }
                sc += s.doubles ? 0.55 + 0.4 * threat : 0.25 + 0.3 * threat;
            }
            if (MoveDex.RECHARGE.contains(m.id) && ko < 0.5) sc *= 0.6;
            if (MoveDex.SELF_KO.contains(m.id)) sc *= 0.4;
            if (!m.revealed && skippedGuess(s, self, m)) sc *= 0.55;
            sc += repeatTargetBonus(self, m, target);
            return sc;
        }
        String id = m.id;
        double hp = self.hpFrac();
        double habit = id.equals(self.lastMove) ? 0.15 + 0.1 * Math.min(3, self.lastMoveStreak) : 0;
        if (MoveDex.SELF_BOOST.containsKey(id)) {
            // Set up when our side cannot punish it hard.
            double threat = 0;
            for (int k = 0; k < s.slots(); k++) {
                Battler foe = s.active(!self.mine, k);
                if (foe == null || !foe.alive()) continue;
                threat += Evaluator.bestAttack(foe, self, s).dmg / Math.max(1, self.hp);
            }
            if (hp < 0.5) return 0.05;
            return Math.max(0.05, 0.6 - 0.8 * Math.min(1, threat)) + habit * 0.3;
        }
        if (MoveDex.HEAL.containsKey(id)) return hp < 0.55 ? 0.6 * (1 - hp) + 0.2 : 0.05;
        if (MoveDex.INFLICT_STATUS.containsKey(id)) {
            String st = MoveDex.INFLICT_STATUS.get(id);
            if (target == null) return 0;
            if (st.length() == 3 && target.status != null) return 0.02;
            if (st.length() == 3 && !TurnSimulator.canStatus(s, self, target, st, m)) return 0.01;
            if ("slp".equals(st)) {
                // Sleep is the strongest status: a trained AI fires it at our biggest threat.
                double threat = 0;
                for (int k = 0; k < s.slots(); k++) {
                    Battler mate = s.active(self.mine, k);
                    if (mate != null && mate.alive())
                        threat = Math.max(threat, Math.min(1, Evaluator.bestAttack(target, mate, s).dmg / Math.max(1, mate.hp)));
                }
                return 0.45 + 0.35 * threat + habit * 0.3;
            }
            // Burn cripples a physical attacker, paralysis a faster one (Flint's Drifblim burned our Garchomp
            // as it switched in; the flat score had it at the bottom of the list).
            if ("brn".equals(st)) return 0.3 + (target.atk >= target.spa ? 0.35 : 0) + habit * 0.3;
            if ("par".equals(st))
                return 0.3 + (Speed.effective(target, s.field) > Speed.effective(self, s.field) ? 0.25 : 0.05) + habit * 0.3;
            if ("tox".equals(st)) return 0.35 + habit * 0.3;
            return 0.3 + habit * 0.3;
        }
        if (MoveDex.PROTECT.contains(id)) {
            // Habit: a trainer that keeps protecting (seen: Whimsicott 3 times in 4 turns) will try again,
            // even right after a Protect (1/3 success), so double-targeting it wastes attacks.
            double habitual = 0.12 * Math.min(3, self.protectUses);
            if (self.protectStreak > 0) return self.protectUses >= 2 ? 0.1 + habitual * 0.5 : 0;
            return (s.doubles ? 0.25 : 0.1) + habitual;
        }
        if ("wideguard".equals(id)) return wideGuardScore(s, self);
        if ("quickguard".equals(id)) return quickGuardScore(s, self);
        String setWeather = MoveDex.WEATHER_SETTER.get(id);
        if (setWeather != null) {
            if (setWeather.equals(s.field.weather)) return 0;
            // Weather value is measured from our side; flip it for the foe.
            double gain = Evaluator.weatherValue(s, setWeather, 5) - Evaluator.weatherValue(s, s.field.weather, s.field.weatherTurns);
            if (!self.mine) gain = -gain;
            return Math.max(0.02, Math.min(0.7, 0.1 + gain * 6));
        }
        if ("trickroom".equals(id)) {
            double ours = 0, theirs = 0;
            for (int k = 0; k < s.slots(); k++) {
                Battler x = s.active(self.mine, k), y = s.active(!self.mine, k);
                if (x != null && x.alive()) ours += Speed.effective(x, s.field);
                if (y != null && y.alive()) theirs += Speed.effective(y, s.field);
            }
            boolean helpsUs = ours < theirs;
            return s.field.trickRoom ? (helpsUs ? 0 : 0.5) : (helpsUs ? 0.7 : 0.05);
        }
        if ("tailwind".equals(id)) {
            if (s.field.side(self.mine).tailwind) return 0;
            // Cobblemon's strong NPC AI opens with Tailwind whenever its side is slower.
            double ours = 0, theirs = 0;
            for (int k = 0; k < s.slots(); k++) {
                Battler x = s.active(self.mine, k), y = s.active(!self.mine, k);
                if (x != null && x.alive()) ours += Speed.effective(x, s.field);
                if (y != null && y.alive()) theirs += Speed.effective(y, s.field);
            }
            double sc = 0.5;
            if (ours < theirs * 1.1) sc += 0.3;
            if (self.abilityChance("prankster") > 0) sc += 0.15;
            if (self.turnsActive == 0) sc += 0.15;
            return sc + habit;
        }
        if (MoveDex.HAZARD.contains(id)) return 0.2;
        if (MoveDex.SCREEN.contains(id)) return 0.25;
        return 0.08;
    }
}
