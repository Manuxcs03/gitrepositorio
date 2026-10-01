package com.manueeh.cobbleai.bridge;

import com.manueeh.cobbleai.CobbleBattleAI;
import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.engine.DamageCalc;
import com.manueeh.cobbleai.engine.Speed;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.MoveInfo;
import com.manueeh.cobbleai.track.BattleTracker;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Learns the real strength of opponents during a battle. Between two decisions it compares the
 * damage that actually happened with what the damage model predicted (and who moved first) and
 * stores per-Pokemon correction factors in the tracker's knowledge:
 * <ul>
 *   <li>attack power per category (Choice Specs, Life Orb, max investment...)</li>
 *   <li>bulk per category (Assault Vest, defensive EVs...)</li>
 *   <li>speed bounds from turn order (Choice Scarf, max speed...)</li>
 * </ul>
 */
public final class Calibrator {
    private Calibrator() {}

    private static final double LEARN = 0.8;
    private static final double MIN_MULT = 0.5, MAX_MULT = 2.6;

    public static void learn(BattleState before, BattleState after, List<BattleTracker.Event> events, boolean trickRoomChanged) {
        if (before == null || after == null || events.isEmpty()) return;
        Map<UUID, Battler> prev = index(before);
        Map<UUID, Battler> now = index(after);
        learnDamage(before, prev, now, events);
        if (!trickRoomChanged) learnSpeed(before, prev, events);
    }

    private static Map<UUID, Battler> index(BattleState s) {
        Map<UUID, Battler> m = new HashMap<>();
        for (Battler b : s.myTeam) if (b.uuid != null) m.put(b.uuid, b);
        for (Battler b : s.oppTeam) if (b.uuid != null) m.put(b.uuid, b);
        return m;
    }

    // ------------------------------------------------------------------ damage

    private static void learnDamage(BattleState before, Map<UUID, Battler> prev, Map<UUID, Battler> now,
                                    List<BattleTracker.Event> events) {
        // Count damaging hits per target; only single-hit, clean turns are informative.
        Map<UUID, Integer> hits = new HashMap<>();
        Map<UUID, BattleTracker.Event> last = new HashMap<>();
        Map<UUID, Boolean> spreadHit = new HashMap<>();
        for (BattleTracker.Event e : events) {
            if (e.failed) continue;
            MoveInfo m = StateBuilder.moveInfo(e.move);
            if (!m.isDamaging()) continue;
            Battler actor = prev.get(e.actor);
            if (actor == null) continue;
            if (e.target != null) {
                hits.merge(e.target, 1, Integer::sum);
                last.put(e.target, e);
                spreadHit.put(e.target, false);
            } else if (m.isSpread()) {
                for (int slot = 0; slot < before.slots(); slot++) {
                    Battler t = before.active(!actor.mine, slot);
                    if (t == null || t.uuid == null) continue;
                    hits.merge(t.uuid, 1, Integer::sum);
                    last.put(t.uuid, e);
                    spreadHit.put(t.uuid, true);
                }
            } else {
                // Single target without an explicit target (singles): the opposing active.
                Battler t = before.active(!actor.mine, 0);
                if (t != null && t.uuid != null) {
                    hits.merge(t.uuid, 1, Integer::sum);
                    last.put(t.uuid, e);
                    spreadHit.put(t.uuid, false);
                }
            }
        }
        Map<Key, List<Double>> samples = new HashMap<>();
        for (var entry : hits.entrySet()) {
            if (entry.getValue() != 1) continue;
            UUID targetId = entry.getKey();
            BattleTracker.Event e = last.get(targetId);
            if (e.tainted.contains(targetId) || e.crit) continue;
            Battler attacker = prev.get(e.actor);
            Battler target0 = prev.get(targetId);
            Battler target1 = now.get(targetId);
            if (attacker == null || target0 == null || target1 == null) continue;
            if (!target1.alive() || target0.maxHp <= 1) continue;
            if (attacker.mine == target0.mine) continue;
            MoveInfo m = StateBuilder.moveInfo(e.move);
            if (m.power <= 0 || MoveDex.MULTI_HIT.containsKey(m.id) || MoveDex.FIXED_DAMAGE.containsKey(m.id)
                || MoveDex.OHKO.contains(m.id) || "seismictoss".equals(m.id) || "nightshade".equals(m.id)
                || "superfang".equals(m.id) || "ruination".equals(m.id)) continue;
            // Power or stats that changed during the turn (Eruption after being hit, Intimidate, Snarl...) would
            // teach a wrong factor, and it is remembered for the next fights (Eruption from a Torkoal hit first
            // "showed" Latios and Metagross half as frail as they are).
            if (!com.manueeh.cobbleai.engine.Readings.reliable(m, attacker, now.get(e.actor), target0, target1)) continue;
            // Residual chip (burn, poison, sand, Leftovers) muddies the reading.
            if (target0.status != null || "sand".equals(before.field.weather) || "leftovers".equals(target0.item)) continue;

            double actualFrac = target0.hpFrac() - target1.hpFrac();
            if (actualFrac <= 0.01) continue;
            boolean spread = before.doubles && Boolean.TRUE.equals(spreadHit.get(targetId)) && countFoes(before, attacker) > 1;
            Battler a = neutral(attacker);
            Battler d = neutral(target0);
            DamageCalc.Result r = DamageCalc.calc(a, d, m, before.field, a.mine, before.doubles, spread, false);
            if (r.immune || r.avg() <= 0) continue;
            double predictedFrac = r.avg() / Math.max(1, d.maxHp);
            if (predictedFrac < 0.04) continue;
            double ratio = actualFrac / predictedFrac;
            ratio = Math.max(0.4, Math.min(3.0, ratio));
            int cat = r.category == Category.PHYSICAL ? 0 : 1;
            if (!attacker.mine) samples.computeIfAbsent(new Key(attacker.uuid, attacker.name, true, cat), x -> new java.util.ArrayList<>()).add(ratio);
            else if (!target0.mine) samples.computeIfAbsent(new Key(target0.uuid, target0.name, false, cat), x -> new java.util.ArrayList<>()).add(ratio);
        }
        // One update per Pokemon and category per turn (a spread move gives several readings).
        BattleTracker tracker = BattleTracker.INSTANCE;
        for (var e : samples.entrySet()) {
            double logSum = 0;
            for (double v : e.getValue()) logSum += Math.log(v);
            double ratio = Math.exp(logSum / e.getValue().size());
            double step = Math.max(0.67, Math.min(1.5, Math.pow(ratio, LEARN)));
            Key key = e.getKey();
            BattleTracker.Knowledge k = tracker.knowledge(key.uuid());
            if (key.power()) {
                k.powerMult[key.cat()] = clamp(k.powerMult[key.cat()] * step);
                log("power", key.name(), key.cat(), ratio, k.powerMult[key.cat()]);
            } else {
                k.bulkMult[key.cat()] = clamp(k.bulkMult[key.cat()] / step);
                log("bulk", key.name(), key.cat(), ratio, k.bulkMult[key.cat()]);
            }
        }
    }

    private record Key(UUID uuid, String name, boolean power, int cat) {}

    private static int countFoes(BattleState s, Battler attacker) {
        int n = 0;
        for (int slot = 0; slot < s.slots(); slot++) {
            Battler t = s.active(!attacker.mine, slot);
            if (t != null && t.alive()) n++;
        }
        return n;
    }

    /** Copy without the planner's uncertainty padding so the reading reflects the raw model. */
    private static Battler neutral(Battler b) {
        Battler c = b.copy();
        c.statsExact = true;
        c.itemUnknown = false;
        return c;
    }

    // ------------------------------------------------------------------ speed

    private static void learnSpeed(BattleState before, Map<UUID, Battler> prev, List<BattleTracker.Event> events) {
        // Under Trick Room the slower Pokemon moves first, so the bounds swap sides.
        boolean tr = before.field.trickRoom;
        for (int i = 0; i + 1 < events.size(); i++) {
            BattleTracker.Event first = events.get(i), second = events.get(i + 1);
            Battler a = prev.get(first.actor), b = prev.get(second.actor);
            if (a == null || b == null || a.mine == b.mine) continue;
            MoveInfo ma = StateBuilder.moveInfo(first.move), mb = StateBuilder.moveInfo(second.move);
            if (Speed.priority(a, ma, before.field) != Speed.priority(b, mb, before.field)) continue;
            if ("quickclaw".equals(a.item) || "laggingtail".equals(b.item)) continue;
            Battler opp = a.mine ? b : a;
            Battler mine = a.mine ? a : b;
            double oppRaw = Math.max(1, opp.spe);
            double factor = Speed.effective(opp, before.field) / oppRaw;
            if (factor <= 0) continue;
            double mineEff = Speed.effective(mine, before.field);
            BattleTracker.Knowledge k = BattleTracker.INSTANCE.knowledge(opp.uuid);
            if (!a.mine != tr) {
                // Opponent moved first: its raw speed is at least our effective speed / its multipliers.
                double bound = mineEff / factor;
                if (bound > k.minSpe) {
                    k.minSpe = bound;
                    CobbleBattleAI.LOG.info("[AI-LEARN] {} outsped {} -> speed >= {}", opp.name, mine.name, Math.round(bound));
                }
            } else {
                double bound = mineEff / factor;
                if (bound < k.maxSpe) {
                    k.maxSpe = bound;
                    CobbleBattleAI.LOG.info("[AI-LEARN] {} was outsped by {} -> speed <= {}", opp.name, mine.name, Math.round(bound));
                }
            }
        }
    }

    private static double clamp(double v) {
        return Math.max(MIN_MULT, Math.min(MAX_MULT, v));
    }

    private static void log(String what, String name, int cat, double ratio, double mult) {
        CobbleBattleAI.LOG.info("[AI-LEARN] {} {} {}: observed/predicted {} -> x{}", name, what,
            cat == 0 ? "phys" : "spec", String.format(Locale.ROOT, "%.2f", ratio), String.format(Locale.ROOT, "%.2f", mult));
    }
}
