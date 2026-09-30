package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Advisor;
import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.manueeh.cobbleai.EngineScenarios.mon;
import static com.manueeh.cobbleai.EngineScenarios.mv;

/**
 * Random teams, random boards: the engine must answer every request with a legal choice, never throw, stay fast,
 * and never pick a damaging move into a target that is immune to it when it has a move that does damage. This is
 * what keeps the engine general: none of these boards look like the logged fights.
 */
public final class FuzzScenarios {
    private FuzzScenarios() {}

    private static final String[] TYPES = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison",
        "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};

    /** id, type, category, power, target (null = single target). */
    private static final Object[][] MOVES = {
        {"flamethrower", "fire", Category.SPECIAL, 90, null}, {"heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"},
        {"flareblitz", "fire", Category.PHYSICAL, 120, null}, {"surf", "water", Category.SPECIAL, 90, "allAdjacent"},
        {"hydropump", "water", Category.SPECIAL, 110, null}, {"waterfall", "water", Category.PHYSICAL, 80, null},
        {"energyball", "grass", Category.SPECIAL, 90, null}, {"woodhammer", "grass", Category.PHYSICAL, 120, null},
        {"thunderbolt", "electric", Category.SPECIAL, 90, null}, {"discharge", "electric", Category.SPECIAL, 80, "allAdjacent"},
        {"icebeam", "ice", Category.SPECIAL, 90, null}, {"icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes"},
        {"closecombat", "fighting", Category.PHYSICAL, 120, null}, {"aurasphere", "fighting", Category.SPECIAL, 80, null},
        {"sludgebomb", "poison", Category.SPECIAL, 90, null}, {"earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"},
        {"earthpower", "ground", Category.SPECIAL, 90, null}, {"bravebird", "flying", Category.PHYSICAL, 120, null},
        {"airslash", "flying", Category.SPECIAL, 75, null}, {"psychic", "psychic", Category.SPECIAL, 90, null},
        {"zenheadbutt", "psychic", Category.PHYSICAL, 80, null}, {"bugbuzz", "bug", Category.SPECIAL, 90, null},
        {"rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"}, {"stoneedge", "rock", Category.PHYSICAL, 100, null},
        {"shadowball", "ghost", Category.SPECIAL, 80, null}, {"dracometeor", "dragon", Category.SPECIAL, 130, null},
        {"dragonclaw", "dragon", Category.PHYSICAL, 80, null}, {"darkpulse", "dark", Category.SPECIAL, 80, null},
        {"knockoff", "dark", Category.PHYSICAL, 65, null}, {"ironhead", "steel", Category.PHYSICAL, 80, null},
        {"flashcannon", "steel", Category.SPECIAL, 80, null}, {"moonblast", "fairy", Category.SPECIAL, 95, null},
        {"dazzlinggleam", "fairy", Category.SPECIAL, 80, "allAdjacentFoes"}, {"return", "normal", Category.PHYSICAL, 102, null},
        {"hypervoice", "normal", Category.SPECIAL, 90, "allAdjacentFoes"},
    };
    private static final String[][] STATUS = {
        {"protect", "normal", "self"}, {"swordsdance", "normal", "self"}, {"nastyplot", "dark", "self"},
        {"willowisp", "fire", "normal"}, {"thunderwave", "electric", "normal"}, {"recover", "normal", "self"},
        {"tailwind", "flying", "allySide"}, {"trickroom", "psychic", "all"}, {"sleeppowder", "grass", "normal"},
    };

    static MoveInfo randomMove(Random r) {
        if (r.nextDouble() < 0.25) {
            String[] s = STATUS[r.nextInt(STATUS.length)];
            MoveInfo m = mv(s[0], s[1], Category.STATUS, 0, s[2]);
            if ("sleeppowder".equals(s[0])) m.accuracy = 0.75;
            if ("thunderwave".equals(s[0])) m.accuracy = 0.9;
            return m;
        }
        Object[] d = MOVES[r.nextInt(MOVES.length)];
        String target = d[4] == null ? "normal" : (String) d[4];
        MoveInfo m = mv((String) d[0], (String) d[1], (Category) d[2], (Integer) d[3], target);
        if ("stoneedge".equals(m.id) || "dracometeor".equals(m.id)) m.accuracy = 0.85;
        return m;
    }

    static Battler randomMon(Random r, boolean mine, int n) {
        String t1 = TYPES[r.nextInt(TYPES.length)];
        String t2 = TYPES[r.nextInt(TYPES.length)];
        String[] types = t1.equals(t2) || r.nextBoolean() ? new String[] {t1} : new String[] {t1, t2};
        int[] stats = {120 + r.nextInt(100), 60 + r.nextInt(140), 60 + r.nextInt(120), 60 + r.nextInt(140),
            60 + r.nextInt(120), 30 + r.nextInt(170)};
        Battler b = mon((mine ? "Mine" : "Foe") + n, mine, 50, stats, types);
        List<MoveInfo> moves = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        while (moves.size() < 4) {
            MoveInfo m = randomMove(r);
            if (!ids.add(m.id)) continue;
            if (!mine && r.nextDouble() < 0.5) m.revealed = false;
            moves.add(m);
        }
        b.moves = moves;
        b.hp = Math.max(1, Math.round(b.maxHp * (0.1 + 0.9 * r.nextDouble())));
        b.turnsActive = r.nextInt(3);
        if (!mine) b.statsExact = r.nextBoolean();
        return b;
    }

    static int failures;

    static void fail(String msg) {
        failures++;
        EngineScenarios.fail("fuzz", msg);
    }

    public static void scenarios() {
        Random r = new Random(20260930L);
        int boards = 120;
        long worstMicros = 0, totalMicros = 0;
        failures = 0;
        for (int i = 0; i < boards; i++) {
            boolean doubles = i % 3 != 0;
            int slots = doubles ? 2 : 1;
            List<Battler> mine = new ArrayList<>(), foes = new ArrayList<>();
            int teamSize = slots + 1 + r.nextInt(3);
            for (int k = 0; k < teamSize; k++) mine.add(randomMon(r, true, k));
            for (int k = 0; k < slots + r.nextInt(2); k++) foes.add(randomMon(r, false, k));
            BattleState st = EngineScenarios.state(doubles, mine, foes);
            if (r.nextDouble() < 0.2) {
                st.field.weather = new String[] {"sun", "rain", "sand", "snow"}[r.nextInt(4)];
                st.field.weatherTurns = 1 + r.nextInt(5);
            }
            if (r.nextDouble() < 0.15) {
                st.field.trickRoom = true;
                st.field.trickRoomTurns = 1 + r.nextInt(4);
            }
            if (r.nextDouble() < 0.15) st.field.theirs.tailwind = true;
            st.oppUnseenReserves = r.nextInt(3);
            List<Integer> bench = new ArrayList<>();
            for (int k = slots; k < mine.size(); k++) bench.add(k);
            List<Planner.SlotRequest> reqs = new ArrayList<>();
            for (int k = 0; k < slots; k++) {
                Planner.SlotRequest q = EngineScenarios.request(st.my(k), bench);
                q.slot = k;
                reqs.add(q);
            }
            Planner.Plan p;
            try {
                p = new Planner(new Planner.Options()).decide(st, reqs);
            } catch (Throwable t) {
                fail("board " + i + " threw " + t);
                continue;
            }
            worstMicros = Math.max(worstMicros, p.micros);
            totalMicros += p.micros;
            if (p.actions.size() != reqs.size()) {
                fail("board " + i + " answered " + p.actions.size() + " of " + reqs.size() + " slots");
                continue;
            }
            java.util.Set<Integer> switchedTo = new java.util.HashSet<>();
            for (int k = 0; k < reqs.size(); k++) {
                Action a = p.actions.get(k);
                Planner.SlotRequest q = reqs.get(k);
                if (a.slot != q.slot) fail("board " + i + " slot mismatch");
                if (a.kind == Action.Kind.SWITCH) {
                    if (!q.switchOptions.contains(a.switchTo)) fail("board " + i + " illegal switch " + a.switchTo);
                    if (!switchedTo.add(a.switchTo)) fail("board " + i + " two slots switch to " + a.switchTo);
                } else if (a.kind == Action.Kind.MOVE) {
                    if (!q.moves.contains(a.move)) fail("board " + i + " move not in request: " + a.move.id);
                    if (a.move.isDamaging() && a.targetSlot >= 0 && !a.targetMine && !a.move.isSpread()) {
                        Battler user = st.my(a.slot), t = st.opp(a.targetSlot);
                        if (t != null && t.alive() && immune(a.move, t) && hasUsefulAttack(st, user, q)) {
                            fail("board " + i + " attacks an immune target: " + a.move.id + " -> " + String.join("/", t.types));
                        }
                    }
                }
            }
            // The live advisor must describe every board without throwing.
            try {
                Advisor.Advice adv = Advisor.advise(st, p);
                if (adv.options.isEmpty()) fail("board " + i + " advisor listed no option");
            } catch (Throwable t) {
                fail("board " + i + " advisor threw " + t);
            }
        }
        String detail = boards + " random boards, avg " + (totalMicros / boards / 1000) + " ms, worst " + (worstMicros / 1000) + " ms";
        if (failures == 0) EngineScenarios.pass("fuzz: legal, stable and sensible on random teams", detail);
        if (worstMicros > 2_500_000) EngineScenarios.fail("fuzz: a decision took too long", detail);
    }

    static boolean immune(MoveInfo m, Battler t) {
        return com.manueeh.cobbleai.data.TypeChart.against(m.type, t.types) == 0;
    }

    /** The user has some attack that hurts some foe on the field. */
    static boolean hasUsefulAttack(BattleState st, Battler user, Planner.SlotRequest q) {
        for (MoveInfo m : q.moves) {
            if (!m.isDamaging()) continue;
            for (int k = 0; k < st.slots(); k++) {
                Battler t = st.opp(k);
                if (t != null && t.alive() && !immune(m, t)) return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        scenarios();
    }
}
