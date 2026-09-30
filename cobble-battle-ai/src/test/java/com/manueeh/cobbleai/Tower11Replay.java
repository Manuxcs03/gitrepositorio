package com.manueeh.cobbleai;

import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.ArrayList;
import java.util.List;

/** Scenarios rebuilt from the lost Battle Tower fight #11 vs Escaladora Maite (Gothitelle/Kangaskhan/Crobat/Lapras). */
public final class Tower11Replay {

    static MoveInfo unrevealed(MoveInfo m) {
        m.revealed = false;
        return m;
    }

    static Battler crobat(double hp) {
        Battler b = EngineScenarios.mon("Crobat", false, 50, new int[] {155, 156, 110, 101, 110, 161}, "poison", "flying");
        b.statsExact = false;
        b.hp = b.maxHp * hp;
        b.itemUnknown = true;
        MoveInfo ph = unrevealed(EngineScenarios.mv("phantomflying", "flying", Category.PHYSICAL, 95));
        MoveInfo pp = unrevealed(EngineScenarios.mv("phantompoison", "poison", Category.PHYSICAL, 95));
        b.moves = new ArrayList<>(List.of(
            unrevealed(EngineScenarios.mv("venoshock", "poison", Category.SPECIAL, 65)),
            unrevealed(EngineScenarios.mv("bite", "dark", Category.PHYSICAL, 60)),
            unrevealed(EngineScenarios.mv("crosspoison", "poison", Category.PHYSICAL, 70)),
            unrevealed(EngineScenarios.mv("wingattack", "flying", Category.PHYSICAL, 60)), pp, ph));
        return b;
    }

    static Battler gothitelle(double hp) {
        Battler b = EngineScenarios.mon("Gothitelle", false, 50, new int[] {170, 86, 120, 161, 150, 96}, "psychic");
        b.statsExact = false;
        b.hp = b.maxHp * hp;
        b.itemUnknown = true;
        b.moves = new ArrayList<>(List.of(EngineScenarios.mv("trickroom", "psychic", Category.STATUS, 0, "all"),
            EngineScenarios.mv("protect", "normal", Category.STATUS, 0, "self"),
            unrevealed(EngineScenarios.mv("psychic", "psychic", Category.SPECIAL, 90))));
        return b;
    }

    /** Turn 4: Trick Room is up; Venusaur 90% + Garchomp (locked into Earthquake) vs Crobat 100% + Gothitelle 58%. */
    static Planner.Plan turn4() {
        Battler venu = Tower27Replay.venusaur();
        venu.hp = venu.maxHp * 0.9;
        venu.turnsActive = 3;
        Battler chomp = Tower27Replay.garchomp();
        chomp.turnsActive = 1;
        chomp.lockedMove = "earthquake";
        Battler cro = crobat(1.0);
        Battler got = gothitelle(0.58);
        BattleState st = EngineScenarios.state(true, List.of(venu, chomp, Tower27Replay.charizardMegaY(), Tower27Replay.torkoal()),
            List.of(cro, got));
        st.myTeam.get(3).hp = 0;
        st.field.trickRoom = true;
        st.oppUnseenReserves = 1;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = EngineScenarios.request(st.myTeam.get(i), i == 0 ? List.of(2) : List.of());
            r.slot = i;
            if (i == 1) {
                r.moves.clear();
                r.moves.add(chomp.moves.get(0));
            }
            reqs.add(r);
        }
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    static Battler kangaskhan(double hp) {
        Battler b = EngineScenarios.mon("Kangaskhan", false, 50, new int[] {180, 161, 105, 71, 105, 121}, "normal");
        b.statsExact = false;
        b.hp = b.maxHp * hp;
        b.itemUnknown = true;
        b.moves = new ArrayList<>(List.of(EngineScenarios.mv("doubleedge", "normal", Category.PHYSICAL, 120),
            unrevealed(EngineScenarios.mv("crunch", "dark", Category.PHYSICAL, 80)),
            unrevealed(EngineScenarios.mv("outrage", "dragon", Category.PHYSICAL, 120))));
        return b;
    }

    /** Turn 3: Garchomp (Choice Scarf) just came in next to Venusaur 90% vs Kangaskhan 26% + Gothitelle. */
    static Planner.Plan turn3() {
        Battler venu = Tower27Replay.venusaur();
        venu.hp = venu.maxHp * 0.9;
        venu.turnsActive = 2;
        Battler chomp = Tower27Replay.garchomp();
        chomp.turnsActive = 0;
        Battler kang = kangaskhan(0.26);
        kang.turnsActive = 2;
        Battler got = gothitelle(1.0);
        got.turnsActive = 2;
        got.moves.add(0, EngineScenarios.mv("fakeout", "normal", Category.PHYSICAL, 40));
        BattleState st = EngineScenarios.state(true, List.of(venu, chomp, Tower27Replay.charizardMegaY(), Tower27Replay.torkoal()),
            List.of(kang, got));
        st.myTeam.get(3).hp = 0;
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 1;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = EngineScenarios.request(st.myTeam.get(i), i == 0 ? List.of(2) : List.of(2));
            r.slot = i;
            reqs.add(r);
        }
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    static boolean protects(Action a) {
        return a.kind == Action.Kind.MOVE && MoveDex.PROTECT.contains(a.move.id);
    }

    /** Choice Scarf Garchomp next to a grounded partner must not lock itself into Earthquake for free. */
    static void noEarthquakeLockNextToPartner() {
        Planner.Plan p = turn3();
        Action chomp = p.actions.get(1);
        if (chomp.kind == Action.Kind.MOVE && "earthquake".equals(chomp.move.id))
            EngineScenarios.fail("tower11 T3 no Earthquake lock next to Venusaur", Tower27Replay.label(p) + EngineScenarios.describe(p));
        else EngineScenarios.pass("tower11 T3 no Earthquake lock next to Venusaur", Tower27Replay.label(p));
    }

    /** Under Trick Room an unread foe may be far slower than the estimate: the turn must split on who moves first. */
    static void trickRoomOrderIsBranched() {
        Battler venu = Tower27Replay.venusaur();
        venu.hp = venu.maxHp * 0.9;
        Battler chomp = Tower27Replay.garchomp();
        chomp.lockedMove = "earthquake";
        Battler cro = crobat(1.0);
        Battler got = gothitelle(0.58);
        BattleState st = EngineScenarios.state(true, List.of(venu, chomp, Tower27Replay.charizardMegaY(), Tower27Replay.torkoal()),
            List.of(cro, got));
        st.field.trickRoom = true;
        MoveInfo hit = unrevealed(EngineScenarios.mv("wingattack", "flying", Category.PHYSICAL, 140));
        List<Action> acts = new ArrayList<>(List.of(Action.move(true, 0, venu.moves.get(1), false, 0),
            Action.move(true, 1, chomp.moves.get(0), false, -1),
            Action.move(false, 0, hit, true, 0), Action.move(false, 1, got.moves.get(1), false, -1)));
        var outs = new com.manueeh.cobbleai.engine.TurnSimulator(false).run(st, acts);
        double lo = 1, hi = 0;
        for (var o : outs) {
            lo = Math.min(lo, o.state().opp(0).hpFrac());
            hi = Math.max(hi, o.state().opp(0).hpFrac());
        }
        if (outs.size() > 1 && hi - lo > 0.05) EngineScenarios.pass("tower11 TR order is branched", outs.size() + " outcomes, Crobat hp " + lo + ".." + hi);
        else EngineScenarios.fail("tower11 TR order is branched", outs.size() + " outcomes, Crobat hp " + lo + ".." + hi);
    }

    public static void scenarios() {
        noEarthquakeLockNextToPartner();
        trickRoomOrderIsBranched();
    }
}
