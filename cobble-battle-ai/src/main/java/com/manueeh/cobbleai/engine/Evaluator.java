package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.Field;
import com.manueeh.cobbleai.model.MoveInfo;
import com.manueeh.cobbleai.model.SideState;

import java.util.List;

/**
 * Static evaluation of a battle state from our perspective. Positive is good for us.
 * One "unit" is roughly the worth of one healthy Pokémon.
 */
public final class Evaluator {
    private Evaluator() {}

    private static final double W_POSITION_SINGLES = 0.6;
    private static final double W_POSITION_DOUBLES = 0.4;
    private static final double WIN_BONUS = 5;
    /** Weight of a fully useful weather (all of our team loving it) in evaluation units. */
    private static final double W_WEATHER = 0.06;

    public static double evaluate(BattleState s) {
        double v = 0;

        // ---- Material + status for every known Pokémon ----
        for (Battler b : s.myTeam) v += battlerValue(s, b);
        for (Battler b : s.oppTeam) v -= battlerValue(s, b);

        boolean oppWiped = s.aliveCount(false) == 0 && s.oppUnseenReserves <= 0;
        boolean meWiped = s.aliveCount(true) == 0;
        if (oppWiped) v += WIN_BONUS;
        if (meWiped) v -= WIN_BONUS;
        if (oppWiped || meWiped) return v + s.chanceValue;

        // ---- Active-only terms ----
        for (int slot = 0; slot < s.slots(); slot++) {
            Battler m = s.my(slot);
            if (m != null && m.alive()) v += activeValue(m);
            Battler o = s.opp(slot);
            if (o != null && o.alive()) v -= activeValue(o);
        }

        // ---- Field ----
        v += teamHazardCost(s, false);
        v -= teamHazardCost(s, true);
        v += screenValue(s, true) - screenValue(s, false);
        // Speed control is worth what is left of it: a Tailwind in its last turn is nearly spent.
        if (s.field.mine.tailwind) v += (0.05 + tailwindFlips(s, true)) * turnsLeftFactor(s.field.mine.tailwindTurns);
        if (s.field.theirs.tailwind) v -= (0.05 + tailwindFlips(s, false)) * turnsLeftFactor(s.field.theirs.tailwindTurns);
        if (s.field.trickRoom) v += trickRoomValue(s) * turnsLeftFactor(s.field.trickRoomTurns);
        v += weatherValue(s);

        // ---- Position: who wins the matchups on the board ----
        BattleState view = weatherView(s);
        v += view.doubles ? positionDoubles(view) : positionSingles(view);
        v += endgame(view);

        return v + s.chanceValue;
    }

    /**
     * If one of our actives can still Mega Evolve into a weather setter (Charizard-Y's Drought) and that
     * weather is not up or ends this turn, the next turns will be played in it: judge every matchup so.
     * That is what makes "Mega Evolve now while the old sun still runs" lose to "evolve next turn".
     */
    private static BattleState weatherView(BattleState s) {
        for (int i = 0; i < s.slots(); i++) {
            Battler m = s.my(i);
            if (m == null || !m.alive() || m.pendingMega == null || m.pendingMega.ability == null) continue;
            String w = com.manueeh.cobbleai.data.AbilityDex.ENTRY_WEATHER.get(m.pendingMega.ability);
            if (w == null) continue;
            if (w.equals(s.field.weather) && s.field.weatherTurns > 1) continue;
            BattleState v = s.copy();
            v.field.weather = w;
            v.field.weatherTurns = 5;
            return v;
        }
        return s;
    }

    /**
     * When one side is down to its last Pokemon in singles, the 1v1 race decides the game. Without this
     * a one-turn search prefers stalling (Protect) over losing now, even though the loss is certain.
     */
    private static double endgame(BattleState s) {
        if (s.doubles) return endgameDoubles(s);
        Battler me = s.my(0), them = s.opp(0);
        if (me == null || them == null || !me.alive() || !them.alive()) return 0;
        boolean myLast = s.aliveCount(true) == 1;
        boolean theirLast = s.aliveCount(false) == 1 && s.oppUnseenReserves <= 0;
        if (!myLast && !theirLast) return 0;
        double r = race(me, them, s);
        if (myLast && r < 0) return -0.8 * WIN_BONUS * Math.min(1, -r / 0.4);
        if (theirLast && r > 0) return 0.8 * WIN_BONUS * Math.min(1, r / 0.4);
        return 0;
    }

    /** Doubles version: our last Pokemon against what is left of theirs (and the mirror case). */
    private static double endgameDoubles(BattleState s) {
        boolean myLast = s.aliveCount(true) == 1;
        boolean theirLast = s.aliveCount(false) == 1 && s.oppUnseenReserves <= 0;
        if (!myLast && !theirLast) return 0;
        Battler me = null, them = null;
        for (int i = 0; i < s.slots(); i++) {
            Battler m = s.my(i), o = s.opp(i);
            if (m != null && m.alive()) me = m;
            if (o != null && o.alive()) them = o;
        }
        if (me == null) return 0;
        if (myLast) {
            // It must beat every remaining foe one after another: take the worst race.
            double worst = 1;
            int foes = 0;
            for (int i = 0; i < s.slots(); i++) {
                Battler o = s.opp(i);
                if (o == null || !o.alive()) continue;
                worst = Math.min(worst, race(me, o, s));
                foes++;
            }
            if (foes > 1) worst -= 0.2; // two attackers at once
            if (worst < 0) return -0.8 * WIN_BONUS * Math.min(1, -worst / 0.4);
            return 0;
        }
        if (them != null) {
            double best = -1;
            for (int i = 0; i < s.slots(); i++) {
                Battler m = s.my(i);
                if (m != null && m.alive()) best = Math.max(best, race(m, them, s));
            }
            if (best > 0) return 0.8 * WIN_BONUS * Math.min(1, best / 0.4);
        }
        return 0;
    }

    /**
     * Tailwind is worth what it flips: each of our Pokemon that stops outspeeding a foe (#30 Aureo: Chlorophyll
     * Venusaur outsped Crobat and Kangaskhan until turn-1 Tailwind, then three of ours fell before moving).
     */
    /** Share of a speed-control effect still to come, from its turns left (0 = unknown: full value). */
    private static double turnsLeftFactor(int turns) {
        return turns <= 0 ? 1 : Math.min(1, turns / 3.0);
    }

    private static double tailwindFlips(BattleState s, boolean side) {
        // Measured over the whole opposing team (fainted too), so losing a Pokemon never looks like a way to
        // "escape" the Tailwind.
        Field calm = s.field.copy();
        calm.side(side).tailwind = false;
        List<Battler> others = s.team(!side);
        if (others.isEmpty()) return 0;
        double v = 0;
        for (int i = 0; i < s.slots(); i++) {
            Battler a = s.active(side, i);
            if (a == null || !a.alive()) continue;
            int flips = 0;
            double sa = Speed.effective(a, calm);
            for (Battler b : others) {
                double sb = Speed.effective(b, calm);
                if (sa < sb && sa * 2 > sb) flips++;
            }
            v += 0.25 * flips / others.size();
        }
        return v;
    }

    private static double battlerValue(BattleState s, Battler b) {
        if (!b.alive()) return 0;
        // A nearly-dead Pokemon is worth little: that is what makes sacrificing it for a free switch right.
        double v = 0.2 + 0.8 * b.hpFrac();
        // Eruption / Water Spout lose power with every hit taken: a healthy user is worth more than its HP
        // (Torkoal is our best attacker, 613 KOs in the logs, and usually fell before moving).
        for (MoveInfo m : b.moves) {
            if (!m.usable() || !("eruption".equals(m.id) || "waterspout".equals(m.id) || "dragonenergy".equals(m.id))) continue;
            // Not when primal weather makes the move fail (Eruption under Primordial Sea).
            if (s.field.primal && (("rain".equals(s.field.weather) && "fire".equals(m.type)) || ("sun".equals(s.field.weather) && "water".equals(m.type)))) continue;
            {
                v += 0.25 * b.hpFrac();
                break;
            }
        }
        if (b.status != null) {
            double sv = statusValue(b, b.status);
            if ("slp".equals(b.status)) sv *= b.sleepTurns >= 2 ? 1.0 : b.sleepTurns == 1 ? 0.75 : 0.4;
            v -= sv;
        } else if (b.drowsy) {
            // Yawned: asleep at the end of next turn unless it leaves the field (switching clears it).
            v -= 0.8 * statusValue(b, "slp");
        }
        return v;
    }

    private static double activeValue(Battler b) {
        double v = 0;
        boolean physical = b.atk >= b.spa;
        int off = physical ? b.boosts[MoveDex.ATK] : b.boosts[MoveDex.SPA];
        v += clampStage(off) * 0.045;
        v += clampStage(b.boosts[MoveDex.SPE]) * 0.03;
        v += clampStage(b.boosts[MoveDex.DEF]) * 0.015 + clampStage(b.boosts[MoveDex.SPD]) * 0.015;
        v += clampStage(b.boosts[MoveDex.ACC]) * 0.01 - clampStage(b.boosts[MoveDex.EVA]) * -0.02;
        if (b.confused) v -= 0.05;
        if (b.leechSeeded) v -= 0.05;
        if (b.substitute) v += 0.06;
        if (b.flashFire) {
            for (MoveInfo m : b.moves) {
                if (m.isDamaging() && "fire".equals(m.type)) {
                    v += 0.08;
                    break;
                }
            }
        }
        if (b.mustRecharge) v -= 0.1;
        if (b.taunted && hasStatusMoves(b)) v -= 0.04;
        if (b.lockedMove != null) {
            for (MoveInfo m : b.moves) {
                if (m.id.equals(b.lockedMove) && m.category == Category.STATUS) v -= 0.3;
            }
        }
        return v;
    }

    private static double clampStage(int s) {
        return Math.max(-4, Math.min(4, s));
    }

    private static boolean hasStatusMoves(Battler b) {
        for (MoveInfo m : b.moves) if (m.category == Category.STATUS) return true;
        return false;
    }

    /** How much a status condition hurts {@code t} (positive number). */
    public static double statusValue(Battler t, String status) {
        if (status == null) return 0;
        String ab = t.effectiveAbility();
        double scale = 0.5 + 0.5 * t.hpFrac();
        boolean physical = t.atk >= t.spa;
        double v = switch (status) {
            case "slp" -> 0.22;
            case "frz" -> 0.25;
            case "par" -> 0.12;
            case "brn" -> physical ? 0.18 : 0.06;
            case "tox" -> 0.14;
            case "psn" -> 0.07;
            default -> 0;
        };
        if (ab != null) {
            if ("guts".equals(ab) || "quickfeet".equals(ab) || "marvelscale".equals(ab)) v = -0.05;
            if ("poisonheal".equals(ab) && ("psn".equals(status) || "tox".equals(status))) v = -0.08;
            if ("magicguard".equals(ab) && !"slp".equals(status) && !"par".equals(status) && !"frz".equals(status)) v *= 0.3;
            if ("toxicboost".equals(ab) && ("psn".equals(status) || "tox".equals(status))) v = -0.05;
            if ("flareboost".equals(ab) && "brn".equals(status)) v = -0.05;
            if ("earlybird".equals(ab) && "slp".equals(status)) v *= 0.5;
        }
        return v * scale;
    }

    /**
     * What the hazards on a side will cost the Pokemon that still have to come in: Stealth Rock takes 25% from
     * a Charizard but 12.5% from a Garchomp, Sticky Web only slows grounded ones. Unknown reserves use the flat
     * value. (MainTick singles: Sticky Web + Stealth Rock taxed eight switch-ins.)
     */
    private static double teamHazardCost(BattleState s, boolean side) {
        SideState h = s.field.side(side);
        if (!h.anyHazard()) return 0;
        double sum = 0;
        int n = 0;
        for (int i : s.bench(side)) {
            Battler b = s.team(side).get(i);
            double c = 0.8 * hazardDamageFrac(s, b);
            if (h.stickyWeb && b.isGrounded(s.field) && !"heavydutyboots".equals(b.item)) c += 0.04;
            if (h.toxicSpikes > 0 && b.isGrounded(s.field) && !b.hasType("poison") && !b.hasType("steel") && b.status == null) c += 0.05;
            sum += c;
            n++;
        }
        int unseen = side ? 0 : Math.max(0, s.oppUnseenReserves);
        sum += unseen * hazardValue(h);
        int total = n + unseen;
        if (total == 0) return 0;
        return sum / total * Math.min(2, total) * 0.7;
    }

    /** Value of the hazards on a side, per expected switch-in. */
    public static double hazardValue(SideState side) {
        double v = 0;
        if (side.stealthRock) v += 0.08;
        if (side.spikes > 0) v += side.spikes == 1 ? 0.05 : side.spikes == 2 ? 0.08 : 0.11;
        if (side.toxicSpikes > 0) v += side.toxicSpikes == 1 ? 0.05 : 0.08;
        if (side.stickyWeb) v += 0.03;
        return v;
    }

    private static double screenValue(BattleState s, boolean mine) {
        SideState side = s.field.side(mine);
        double v = 0;
        boolean foePhys = false, foeSpec = false;
        for (int slot = 0; slot < s.slots(); slot++) {
            Battler f = s.active(!mine, slot);
            if (f == null || !f.alive()) continue;
            if (f.atk >= f.spa) foePhys = true;
            else foeSpec = true;
        }
        if (side.auroraVeil) v += 0.12;
        else {
            if (side.reflect && foePhys) v += 0.07;
            if (side.lightScreen && foeSpec) v += 0.07;
        }
        return v;
    }

    private static double trickRoomValue(BattleState s) {
        double mine = 0, theirs = 0;
        int nm = 0, nt = 0;
        for (int slot = 0; slot < s.slots(); slot++) {
            Battler m = s.my(slot), o = s.opp(slot);
            if (m != null && m.alive()) { mine += m.spe; nm++; }
            if (o != null && o.alive()) { theirs += o.spe; nt++; }
        }
        if (nm == 0 || nt == 0) return 0;
        return (mine / nm) < (theirs / nt) ? 0.08 : -0.08;
    }

    // ------------------------------------------------------------------ weather

    /**
     * How much {@code w} (rain, sun, sand, snow) helps {@code b}: about -1 (hurts) .. +1 (it lives for it).
     * Looks at typing, moves, ability and item, so it works for any weather team, not only sun.
     */
    static double weatherBenefit(Battler b, String w) {
        if (w == null || !b.alive()) return 0;
        String ab = b.effectiveAbility();
        boolean fire = false, water = false, solar = false, thunder = false, blizzard = false, heal = false;
        boolean growth = false, veil = false;
        for (MoveInfo m : b.moves) {
            if (m.category != Category.STATUS && m.power > 0) {
                if ("fire".equals(m.type)) fire = true;
                if ("water".equals(m.type)) water = true;
            }
            switch (m.id) {
                case "solarbeam", "solarblade" -> solar = true;
                case "thunder", "hurricane" -> thunder = true;
                case "blizzard" -> blizzard = true;
                case "moonlight", "synthesis", "morningsun" -> heal = true;
                case "growth" -> growth = true;
                case "auroraveil" -> veil = true;
                default -> { }
            }
        }
        double v = 0;
        switch (w) {
            case "sun" -> {
                if (fire) v += b.hasType("fire") ? 0.55 : 0.45;
                if (water) v -= 0.35;
                if (solar && !"powerherb".equals(b.item)) v += 0.3;
                if (heal) v += 0.1;
                if (growth) v += 0.1;
                if (thunder) v -= 0.1;
                if (ab != null) {
                    switch (ab) {
                        case "chlorophyll" -> v += 0.6;
                        case "solarpower" -> v += 0.35;
                        case "protosynthesis" -> v += 0.4;
                        case "orichalcumpulse" -> v += 0.2;
                        case "flowergift", "harvest" -> v += 0.25;
                        case "leafguard" -> v += 0.15;
                        case "forecast" -> v += 0.2;
                        case "dryskin" -> v -= 0.4;
                        default -> { }
                    }
                }
            }
            case "rain" -> {
                if (water) v += b.hasType("water") ? 0.55 : 0.45;
                if (fire) v -= 0.35;
                if (thunder) v += 0.2;
                if (solar) v -= 0.2;
                if (heal) v -= 0.1;
                if (ab != null) {
                    switch (ab) {
                        case "swiftswim" -> v += 0.6;
                        case "raindish", "hydration" -> v += 0.15;
                        case "dryskin" -> v += 0.2;
                        case "forecast" -> v += 0.2;
                        default -> { }
                    }
                }
            }
            case "sand" -> {
                if (b.hasType("rock")) v += 0.3;
                boolean immune = b.hasType("rock") || b.hasType("ground") || b.hasType("steel")
                    || "safetygoggles".equals(b.item);
                if (ab != null) {
                    switch (ab) {
                        case "sandrush" -> { v += 0.6; immune = true; }
                        case "sandforce" -> { v += 0.35; immune = true; }
                        case "sandveil" -> { v += 0.2; immune = true; }
                        case "overcoat", "magicguard" -> immune = true;
                        default -> { }
                    }
                }
                if (!immune) v -= 0.25;
                if (solar) v -= 0.2;
                if (heal) v -= 0.1;
            }
            case "snow" -> {
                if (b.hasType("ice")) v += 0.3;
                if (blizzard) v += 0.25;
                if (veil) v += 0.3;
                if (solar) v -= 0.2;
                if (heal) v -= 0.1;
                if (ab != null) {
                    switch (ab) {
                        case "slushrush" -> v += 0.6;
                        case "icebody" -> v += 0.15;
                        case "snowcloak" -> v += 0.2;
                        default -> { }
                    }
                }
            }
            default -> { }
        }
        return Math.max(-1, Math.min(1, v));
    }

    /** Value (our point of view) of the weather that is up now, scaled by how long it will still last. */
    public static double weatherValue(BattleState s) {
        return weatherValue(s, s.field.weather, s.field.weatherTurns);
    }

    /**
     * Value (our point of view) of {@code weather} lasting {@code turns} more turns: what it does for
     * every living Pokemon on both teams (actives count fully, the bench partly).
     */
    public static double weatherValue(BattleState s, String weather, int turns) {
        if (weather == null || turns <= 0) return 0;
        double tf = Math.min(1.0, turns / 3.0);
        double mine = 0, theirs = 0;
        for (int i = 0; i < s.myTeam.size(); i++) {
            Battler b = s.myTeam.get(i);
            if (b.alive()) mine += (s.isActive(true, i) ? 1.0 : 0.4) * weatherBenefit(b, weather);
        }
        for (int i = 0; i < s.oppTeam.size(); i++) {
            Battler b = s.oppTeam.get(i);
            if (b.alive()) theirs += (s.isActive(false, i) ? 1.0 : 0.4) * weatherBenefit(b, weather);
        }
        return W_WEATHER * tf * (mine - theirs);
    }

    // ------------------------------------------------------------------ position

    private static double positionSingles(BattleState s) {
        Battler me = s.my(0);
        Battler them = s.opp(0);
        if (them == null || !them.alive()) {
            // They must bring in a replacement: judge against what we know of their bench.
            List<Integer> bench = s.bench(false);
            if (bench.isEmpty() || s.oppUnseenReserves > 0 || me == null || !me.alive()) return 0;
            double worst = Double.MAX_VALUE;
            for (int i : bench) {
                Battler next = s.oppTeam.get(i);
                worst = Math.min(worst, staked(s, W_POSITION_SINGLES * 0.7 * raceSwing(me, next, s), me, next));
            }
            return worst;
        }
        if (me == null || !me.alive()) {
            // We get a free switch into our best answer.
            double best = -W_POSITION_SINGLES;
            for (int i : s.bench(true)) {
                Battler cand = s.myTeam.get(i).copy();
                double hazard = hazardDamageFrac(s, cand);
                cand.hp = Math.max(0, cand.hp - hazard * cand.maxHp);
                if (!cand.alive()) continue;
                best = Math.max(best, staked(s, W_POSITION_SINGLES * raceSwing(cand, them, s), cand, them));
            }
            return best;
        }
        return staked(s, W_POSITION_SINGLES * raceSwing(me, them, s), me, them);
    }

    /**
     * A won matchup is worth at most most of what the beaten Pokemon is worth, a lost one costs at most most
     * of ours: the rest only comes when the KO really happens. Without the cap an Emboar left at 48% that our
     * bench "beats" scored higher than knocking it out (Flint: Kingdra 8% chose a resisted Ice Beam over a
     * Scald KO, then Weavile switched out instead of Low Kicking the 42% Emboar).
     */
    private static double staked(BattleState s, double v, Battler mine, Battler foe) {
        if (v > 0) return Math.min(v, STAKE * battlerValue(s, foe));
        return Math.max(v, -STAKE * battlerValue(s, mine));
    }

    /** Share of a Pokemon's value that winning (or losing) its matchup can be worth before the KO happens. */
    private static final double STAKE = 0.8;

    private static double positionDoubles(BattleState s) {
        // Empty slots on our side get their best free replacement (fainted -> free switch-in).
        Battler[] mine = new Battler[s.slots()];
        boolean[] replaced = new boolean[s.slots()];
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int i = 0; i < s.slots(); i++) {
            Battler m = s.my(i);
            if (m != null && m.alive()) mine[i] = m;
        }
        for (int i = 0; i < s.slots(); i++) {
            if (mine[i] != null) continue;
            double best = -9;
            int bestIdx = -1;
            for (int idx : s.bench(true)) {
                if (used.contains(idx)) continue;
                Battler cand = s.myTeam.get(idx);
                double sc = 0;
                int n = 0;
                for (int j = 0; j < s.slots(); j++) {
                    Battler o = s.opp(j);
                    if (o == null || !o.alive()) continue;
                    sc += race(cand, o, s);
                    n++;
                }
                sc = n == 0 ? 0 : sc / n;
                if (sc > best) {
                    best = sc;
                    bestIdx = idx;
                }
            }
            if (bestIdx >= 0) {
                used.add(bestIdx);
                mine[i] = s.myTeam.get(bestIdx);
                replaced[i] = true;
            }
        }
        // Every pair counts with a fixed weight (not an average over the pairs still alive): averaging made a
        // knocked-out foe raise the weight of the remaining matchups, so finishing a weak foe could look bad.
        // Gains are grouped by the foe that would lose, losses by our Pokemon, each capped by what it is worth.
        int n = s.slots();
        double[] gain = new double[n], loss = new double[n];
        for (int i = 0; i < n; i++) {
            Battler m = mine[i];
            if (m == null || !m.alive()) continue;
            // A replacement still has to come in: it counts, but less than a Pokemon already there.
            double w = replaced[i] ? 0.4 : 1.0;
            for (int j = 0; j < n; j++) {
                Battler o = s.opp(j);
                if (o == null || !o.alive()) continue;
                double c = W_POSITION_DOUBLES * w * raceSwing(m, o, s) / (n * n);
                if (c > 0) gain[j] += c;
                else loss[i] += c;
            }
        }
        // Focus-fire threat: can our actives together remove a foe next turn (and vice versa)?
        for (int j = 0; j < n; j++) {
            Battler o = s.opp(j);
            if (o == null || !o.alive()) continue;
            double combined = 0;
            for (int i = 0; i < n; i++) {
                Battler m = mine[i];
                if (m != null && m.alive()) combined += bestAttack(m, o, s).dmg;
            }
            if (combined >= o.hp) gain[j] += 0.08;
        }
        for (int i = 0; i < n; i++) {
            Battler m = s.my(i);
            if (m == null || !m.alive()) continue;
            double combined = 0;
            for (int j = 0; j < n; j++) {
                Battler o = s.opp(j);
                if (o != null && o.alive()) combined += bestAttack(o, m, s).dmg;
            }
            if (combined >= m.hp) loss[i] -= 0.08;
        }
        double v = 0;
        for (int j = 0; j < n; j++) {
            Battler o = s.opp(j);
            if (o != null && o.alive()) v += Math.min(gain[j], STAKE * battlerValue(s, o));
        }
        for (int i = 0; i < n; i++) {
            Battler m = mine[i];
            if (m != null && m.alive()) v += Math.max(loss[i], -STAKE * battlerValue(s, m));
        }
        return v;
    }

    static double hazardDamageFrac(BattleState s, Battler n) {
        if ("heavydutyboots".equals(n.item) || n.hasAbility("magicguard")) return 0;
        SideState side = s.field.side(n.mine);
        double f = 0;
        if (side.stealthRock) f += com.manueeh.cobbleai.data.TypeChart.against("rock", n.types) / 8.0;
        if (n.isGrounded(s.field) && side.spikes > 0) f += side.spikes == 1 ? 1.0 / 8 : side.spikes == 2 ? 1.0 / 6 : 0.25;
        return f;
    }

    // ------------------------------------------------------------------ race projection

    /** Best damaging option of {@code att} against {@code def}. */
    public static final class Attack {
        public double dmg;
        public int prio;
        public MoveInfo move;
        /** Strongest positive-priority option (dmg 0 if none). */
        public double prioDmg;
        public int prioPrio;
        /** The best move needs a charging turn (Solar Beam out of sun): it lands every other turn. */
        public boolean charge;
        /** The best move loses power with the user's HP (Eruption, Water Spout, Dragon Energy). */
        public boolean hpScaled;
    }

    public static Attack bestAttack(Battler att, Battler def, BattleState s) {
        Attack best = new Attack();
        Field f = s.field;
        for (MoveInfo m : att.moves) {
            if (!m.usable() || m.category == Category.STATUS) continue;
            if (att.lockedMove != null && !att.lockedMove.equals(m.id)) continue;
            if (MoveDex.SELF_KO.contains(m.id) || MoveDex.FIRST_TURN_ONLY.contains(m.id)) continue;
            if (MoveDex.NON_LETHAL.contains(m.id)) continue;
            DamageCalc.Result r = DamageCalc.calc(att, def, m, f, att.mine, s.doubles, false, false);
            double d = r.avg() * r.hitChance;
            if (MoveDex.RECHARGE.contains(m.id)) d *= 0.6;
            boolean charging = MoveDex.CHARGE.contains(m.id) && !"powerherb".equals(att.item)
                && !(("solarbeam".equals(m.id) || "solarblade".equals(m.id)) && "sun".equals(f.weather));
            if (charging) d *= 0.5;
            if (!m.revealed) d *= Math.max(0.75, OpponentModel.guessFactor(att, m));
            int prio = Speed.priority(att, m, f);
            if (d > best.dmg) {
                best.dmg = d;
                best.prio = prio;
                best.move = m;
                best.charge = charging;
                best.hpScaled = "eruption".equals(m.id) || "waterspout".equals(m.id) || "dragonenergy".equals(m.id);
            }
            if (prio > 0 && d > best.prioDmg) {
                best.prioDmg = d;
                best.prioPrio = prio;
            }
        }
        return best;
    }

    private static double actionRate(Battler b) {
        double r = 1;
        if ("par".equals(b.status) && !b.hasAbility("quickfeet")) r *= 0.75;
        if (b.confused) r *= 0.67;
        return r;
    }

    /** Asleep, but holding Sleep Talk: it still acts (with a random move) while it sleeps. */
    private static boolean talksInSleep(Battler b) {
        if (!"slp".equals(b.status) || b.sleepTurns <= 0) return false;
        for (MoveInfo m : b.moves) if ("sleeptalk".equals(m.id) && m.usable()) return true;
        return false;
    }

    /** Fraction of a normal attack that Sleep Talk delivers on average. */
    private static double sleepTalkFactor(Battler b) {
        int callable = 0, damaging = 0;
        for (MoveInfo m : b.moves) {
            if (!m.usable() || "sleeptalk".equals(m.id) || "rest".equals(m.id)) continue;
            callable++;
            if (m.category != Category.STATUS) damaging++;
        }
        return callable == 0 ? 0 : 0.7 * damaging / callable;
    }

    private static int initialDelay(Battler b) {
        int d = 0;
        if ("slp".equals(b.status) && !talksInSleep(b)) d += Math.max(1, b.sleepTurns);
        if ("frz".equals(b.status)) d += 3;
        if (b.mustRecharge) d += 1;
        return d;
    }

    static double residualPerTurn(Battler b, Field f) {
        if (b.hasAbility("magicguard")) {
            return "leftovers".equals(b.item) ? -b.maxHp / 16.0 : 0;
        }
        double d = 0;
        if (b.status != null) {
            switch (b.status) {
                case "brn" -> d += b.maxHp / 16.0;
                case "psn" -> d += b.hasAbility("poisonheal") ? -b.maxHp / 8.0 : b.maxHp / 8.0;
                case "tox" -> d += b.hasAbility("poisonheal") ? -b.maxHp / 8.0 : b.maxHp * (b.toxicCounter + 2) / 16.0;
                default -> { }
            }
        }
        if (b.leechSeeded) d += b.maxHp / 8.0;
        if ("sun".equals(f.weather) && (b.hasAbility("solarpower") || b.hasAbility("dryskin"))) d += b.maxHp / 8.0;
        if ("rain".equals(f.weather) && b.hasAbility("dryskin")) d -= b.maxHp / 8.0;
        if ("sand".equals(f.weather) && !b.hasType("rock") && !b.hasType("ground") && !b.hasType("steel")) d += b.maxHp / 16.0;
        if ("leftovers".equals(b.item)) d -= b.maxHp / 16.0;
        if ("blacksludge".equals(b.item) && b.hasType("poison")) d -= b.maxHp / 16.0;
        if ("grassy".equals(f.terrain) && b.isGrounded(f)) d -= b.maxHp / 16.0;
        return d;
    }

    /**
     * Projects a 1v1 slug-fest where both sides spam their best attack.
     * Returns a score in [-1, 1] from {@code a}'s point of view.
     */
    public static double race(Battler a, Battler b, BattleState s) {
        Battler ra = a.asMega(), rb = b.asMega();
        String w = s.field.weather;
        // A pending Mega with a weather ability will (re)start its weather when it evolves.
        String megaWeather = null;
        for (Battler x : new Battler[] {a, b}) {
            if (x.pendingMega != null && x.pendingMega.ability != null) {
                String mw = com.manueeh.cobbleai.data.AbilityDex.ENTRY_WEATHER.get(x.pendingMega.ability);
                if (mw != null && (!mw.equals(w) || s.field.weatherTurns <= 1)) megaWeather = mw;
            }
        }
        if (megaWeather != null) return raceIn(ra, rb, s, megaWeather);
        if (w != null && s.field.weatherTurns <= 2) {
            // Weather is about to end: part of the fight happens in it, the rest without it.
            double pIn = s.field.weatherTurns <= 1 ? 0.5 : 0.75;
            return pIn * raceIn(ra, rb, s, w) + (1 - pIn) * raceIn(ra, rb, s, null);
        }
        return raceCore(ra, rb, s);
    }

    /**
     * Same projection as {@link #race}, measured as the material it moves: what the winner keeps minus what
     * each side loses, valued like the evaluation values Pokemon. A trade of our 7% Kingdra for their 67%
     * Emboar scores positive here, while {@code race} calls every double KO a draw.
     */
    public static double raceSwing(Battler a, Battler b, BattleState s) {
        boolean prev = swingMode;
        swingMode = true;
        try {
            return race(a, b, s);
        } finally {
            swingMode = prev;
        }
    }

    private static boolean swingMode;

    private static double finish(double score, double hpA, double hpB, Battler a, Battler b, double vA0, double vB0) {
        if (!swingMode) return score;
        return (hpValue(hpA, a.maxHp) - vA0) - (hpValue(hpB, b.maxHp) - vB0);
    }

    private static double speedBoostFactor(Battler b, int turn) {
        if (!b.hasAbility("speedboost")) return 1;
        int now = b.boosts[MoveDex.SPE];
        int later = Math.min(6, now + turn - 1);
        return DamageCalc.stageMult(later) / DamageCalc.stageMult(now);
    }

    private static double hpValue(double hp, double maxHp) {
        return hp <= 0 ? 0 : 0.2 + 0.8 * Math.min(1, hp / Math.max(1, maxHp));
    }

    private static double raceIn(Battler a, Battler b, BattleState s, String weather) {
        if (java.util.Objects.equals(weather, s.field.weather)) return raceCore(a, b, s);
        BattleState v = new BattleState();
        v.field = s.field.copy();
        v.field.weather = weather;
        v.field.weatherTurns = weather == null ? 0 : 5;
        v.doubles = s.doubles;
        v.myActive = s.myActive;
        v.oppActive = s.oppActive;
        return raceCore(a, b, v);
    }

    private static double raceCore(Battler a, Battler b, BattleState s) {
        Attack atkA = bestAttack(a, b, s);
        Attack atkB = bestAttack(b, a, s);
        double first = Speed.firstChance(a, atkA.prio, b, atkB.prio, s.field);
        if (first == 0.5) return 0.5 * (raceOnce(a, b, atkA, atkB, true, s) + raceOnce(a, b, atkA, atkB, false, s));
        return raceOnce(a, b, atkA, atkB, first > 0.5, s);
    }

    private static double raceOnce(Battler a, Battler b, Attack atkA, Attack atkB, boolean aFirstBase, BattleState s) {
        double hpA = a.hp, hpB = b.hp;
        double vA0 = hpValue(hpA, a.maxHp), vB0 = hpValue(hpB, b.maxHp);
        double dA = atkA.dmg * actionRate(a), dB = atkB.dmg * actionRate(b);
        double pA = atkA.prioDmg * actionRate(a), pB = atkB.prioDmg * actionRate(b);
        double chipA = residualPerTurn(a, s.field), chipB = residualPerTurn(b, s.field);
        int delayA = initialDelay(a), delayB = initialDelay(b);
        int talkA = talksInSleep(a) ? Math.max(1, a.sleepTurns) : 0, talkB = talksInSleep(b) ? Math.max(1, b.sleepTurns) : 0;
        // A charging move lands at full power every other turn (charge, hit, charge...), not half every turn:
        // that matters when one hit would finish the foe (Solar Beam out of sun vs a 6% Chandelure).
        boolean chargeA = atkA.charge, chargeB = atkB.charge;
        if (chargeA) dA *= 2;
        if (chargeB) dB *= 2;
        boolean readyA = false, readyB = false;
        double talkFA = sleepTalkFactor(a), talkFB = sleepTalkFactor(b);
        if (dA <= 0 && dB <= 0 && chipA <= 0 && chipB <= 0) return 0;
        // Sustain and self-damage: recovery moves, draining attacks, Life Orb and recoil moves.
        double healA = healPerUse(a, s.field), healB = healPerUse(b, s.field);
        double drainA = drainFrac(atkA), drainB = drainFrac(atkB);
        double recoilA = recoilFrac(a, atkA), recoilB = recoilFrac(b, atkB);
        double orbA = orbRecoil(a), orbB = orbRecoil(b);
        for (int turn = 1; turn <= 10; turn++) {
            double curA = turn <= talkA ? dA * talkFA : dA;
            double curB = turn <= talkB ? dB * talkFB : dB;
            // Eruption shrinks as its user is worn down: a slow Torkoal hit first does not Erupt at full power.
            if (atkA.hpScaled && a.hp > 0) curA *= Math.max(0, hpA) / a.hp;
            if (atkB.hpScaled && b.hp > 0) curB *= Math.max(0, hpB) / b.hp;
            boolean aFirst = aFirstBase;
            // Speed Boost gains a stage every turn: a Mega Blaziken that starts slower outruns everything after a
            // turn or two (MainTick: Scarf Garchomp -1 from Sticky Web was expected to stay faster).
            if (turn > 1 && atkA.prio == atkB.prio && (a.hasAbility("speedboost") || b.hasAbility("speedboost"))) {
                double sa = Speed.effective(a, s.field) * speedBoostFactor(a, turn);
                double sb = Speed.effective(b, s.field) * speedBoostFactor(b, turn);
                if (Math.abs(sa - sb) >= 0.5) aFirst = s.field.trickRoom ? sa < sb : sa > sb;
            }
            // Priority moves to finish off a weakened target.
            boolean aPrioKo = pA >= hpB && atkA.prioPrio > atkB.prio;
            boolean bPrioKo = pB >= hpA && atkB.prioPrio > atkA.prio;
            if (aPrioKo && !bPrioKo) aFirst = true;
            else if (bPrioKo && !aPrioKo) aFirst = false;
            for (int k = 0; k < 2; k++) {
                boolean aActs = (k == 0) == aFirst;
                if (aActs) {
                    if (delayA > 0) { delayA--; continue; }
                    // Recover when the next hit would finish it and the heal outweighs that hit.
                    double nextB = bPrioKo ? Math.max(curB, pB) : curB;
                    if (healA > nextB && hpA <= nextB && hpA < a.maxHp) {
                        hpA = Math.min(a.maxHp, hpA + healA);
                        continue;
                    }
                    if (chargeA && !aPrioKo) {
                        readyA = !readyA;
                        if (readyA) continue;
                    }
                    double hit = aPrioKo ? Math.max(curA, pA) : curA;
                    double dealt = Math.min(Math.max(0, hpB), hit);
                    hpB -= hit;
                    if (dealt > 0) hpA = Math.min(a.maxHp, hpA + drainA * dealt) - recoilA * dealt - orbA;
                    if (hpB <= 0) return finish(hpA > 0 ? winScore(hpA, a.maxHp, turn) : 0, hpA, hpB, a, b, vA0, vB0);
                    if (hpA <= 0) return finish(-winScore(hpB, b.maxHp, turn), hpA, hpB, a, b, vA0, vB0);
                } else {
                    if (delayB > 0) { delayB--; continue; }
                    double nextA = aPrioKo ? Math.max(curA, pA) : curA;
                    if (healB > nextA && hpB <= nextA && hpB < b.maxHp) {
                        hpB = Math.min(b.maxHp, hpB + healB);
                        continue;
                    }
                    if (chargeB && !bPrioKo) {
                        readyB = !readyB;
                        if (readyB) continue;
                    }
                    double hit = bPrioKo ? Math.max(curB, pB) : curB;
                    double dealt = Math.min(Math.max(0, hpA), hit);
                    hpA -= hit;
                    if (dealt > 0) hpB = Math.min(b.maxHp, hpB + drainB * dealt) - recoilB * dealt - orbB;
                    if (hpA <= 0) return finish(hpB > 0 ? -winScore(hpB, b.maxHp, turn) : 0, hpA, hpB, a, b, vA0, vB0);
                    if (hpB <= 0) return finish(winScore(hpA, a.maxHp, turn), hpA, hpB, a, b, vA0, vB0);
                }
            }
            hpA = Math.min(a.maxHp, hpA - chipA);
            hpB = Math.min(b.maxHp, hpB - chipB);
            if (hpB <= 0 && hpA > 0) return finish(winScore(hpA, a.maxHp, turn), hpA, hpB, a, b, vA0, vB0);
            if (hpA <= 0 && hpB > 0) return finish(-winScore(hpB, b.maxHp, turn), hpA, hpB, a, b, vA0, vB0);
            if (hpA <= 0 && hpB <= 0) return finish(0, hpA, hpB, a, b, vA0, vB0);
        }
        // Nobody fell in 10 turns: compare the damage rates, net of what each side can recover.
        double perA = chargeA ? dA / 2 : dA, perB = chargeB ? dB / 2 : dB;
        double busyA = healA > perB && perB > 0 ? perB / healA : 0, busyB = healB > perA && perA > 0 ? perA / healB : 0;
        double outA = perA * (1 - busyA), outB = perB * (1 - busyB);
        double rateA = healB > outA ? 0 : outA / Math.max(1, b.maxHp), rateB = healA > outB ? 0 : outB / Math.max(1, a.maxHp);
        return Math.max(-0.2, Math.min(0.2, (rateA - rateB) * 2));
    }

    /** HP one use of its best usable recovery move restores (0 when it has none or is Taunted). */
    private static double healPerUse(Battler b, Field f) {
        if (b.taunted || b.maxHp <= 0) return 0;
        double best = 0;
        for (MoveInfo m : b.moves) {
            if (!m.usable() || (b.lockedMove != null && !b.lockedMove.equals(m.id))) continue;
            Double frac = MoveDex.HEAL.get(m.id);
            if (frac == null || "wish".equals(m.id) || "rest".equals(m.id) || "strengthsap".equals(m.id)) continue;
            double h = frac;
            if ("moonlight".equals(m.id) || "morningsun".equals(m.id) || "synthesis".equals(m.id)) {
                h = f.weather == null ? 0.5 : "sun".equals(f.weather) ? 2.0 / 3 : 0.25;
            } else if ("shoreup".equals(m.id) && "sand".equals(f.weather)) {
                h = 2.0 / 3;
            }
            if (!m.revealed && !b.mine) h *= 0.5;
            best = Math.max(best, h * b.maxHp);
        }
        return best;
    }

    private static double drainFrac(Attack atk) {
        return atk.move == null ? 0 : MoveDex.DRAIN.getOrDefault(atk.move.id, 0.0);
    }

    private static double recoilFrac(Battler b, Attack atk) {
        if (atk.move == null || b.hasAbility("rockhead") || b.hasAbility("magicguard")) return 0;
        return MoveDex.RECOIL.getOrDefault(atk.move.id, 0.0);
    }

    private static double orbRecoil(Battler b) {
        return "lifeorb".equals(b.item) && !b.hasAbility("magicguard") ? b.maxHp / 10.0 : 0;
    }

    private static double winScore(double hpLeft, double maxHp, int turns) {
        double frac = Math.max(0, hpLeft) / Math.max(1, maxHp);
        return 0.25 + 0.7 * frac + (turns <= 1 ? 0.05 : 0);
    }
}
