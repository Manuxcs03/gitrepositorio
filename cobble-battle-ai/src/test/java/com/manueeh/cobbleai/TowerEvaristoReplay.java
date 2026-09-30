package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.ArrayList;
import java.util.List;

import static com.manueeh.cobbleai.EngineScenarios.mon;
import static com.manueeh.cobbleai.EngineScenarios.mv;
import static com.manueeh.cobbleai.Tower31Replay.estimated;
import static com.manueeh.cobbleai.Tower31Replay.guess;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight #36 vs Baron Evaristo (streak 35): Primal Kyogre (Primordial Sea) + Cresselia. */
public final class TowerEvaristoReplay {

    /** Turn 2: both protected on turn 1; Primordial Sea is up, so every Fire move fails. */
    static Planner.Plan evaristoTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.protectStreak = 1;
        venu.lastMove = "protect";
        venu.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.protectStreak = 1;
        tork.lastMove = "protect";
        tork.turnsActive = 1;
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        Battler kyo = estimated(mon("Kyogre", false, 50, new int[] {175, 181, 110, 255, 180, 156}, "water"));
        kyo.ability = "primordialsea";
        MoveInfo spout = mv("waterspout", "water", Category.SPECIAL, 150, "allAdjacentFoes");
        kyo.moves = new ArrayList<>(List.of(spout, guess(mv("icebeam", "ice", Category.SPECIAL, 90)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        kyo.lastMove = "waterspout";
        kyo.turnsActive = 1;
        Battler cres = estimated(mon("Cresselia", false, 50, new int[] {195, 101, 140, 139, 150, 150}, "psychic"));
        cres.moves = new ArrayList<>(List.of(mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes"),
            guess(mv("psychic", "psychic", Category.SPECIAL, 90)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        cres.moves.get(0).accuracy = 0.95;
        cres.lastMove = "icywind";
        cres.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, zard, chomp), List.of(kyo, cres));
        st.field.setPrimal("rain");
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        Planner.Plan p = evaristoTurn2();
        Action t = p.actions.get(1);
        boolean fire = t.kind == Action.Kind.MOVE && "fire".equals(t.move.type);
        report("evaristo T2 Torkoal does not click a Fire move under Primordial Sea (it fails)", !fire, p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
