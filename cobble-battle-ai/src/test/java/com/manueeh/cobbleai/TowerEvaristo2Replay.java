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

/**
 * Log 24: lost #33 vs Baron Evaristo (Primal Kyogre + Cresselia, Crobat in the back). Turn 2: Kyogre at 38% and a
 * Giga Drain knocks it out, which ends Primordial Sea and gives the sun back. The engine protected Venusaur and
 * sent Garchomp into Origin Pulse instead.
 */
public final class TowerEvaristo2Replay {
    private TowerEvaristo2Replay() {}

    static BattleState last;

    static Battler kyogre(double hp) {
        Battler kyo = estimated(mon("Kyogre", false, 50, new int[] {175, 181, 110, 255, 180, 132}, "water"));
        kyo.hp = kyo.maxHp * hp;
        kyo.ability = "primordialsea";
        MoveInfo spout = mv("waterspout", "water", Category.SPECIAL, 150, "allAdjacentFoes");
        MoveInfo origin = mv("originpulse", "water", Category.SPECIAL, 110, "allAdjacentFoes");
        origin.accuracy = 0.85;
        kyo.moves = new ArrayList<>(List.of(mv("icebeam", "ice", Category.SPECIAL, 90), spout, origin,
            guess(mv("calmmind", "psychic", Category.STATUS, 0, "self")), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        return kyo;
    }

    static Planner.Plan turn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.hp = venu.maxHp * 0.49;
        venu.boosts[4] = -1;
        venu.turnsActive = 1;
        venu.lastMove = "gigadrain";
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 1;
        tork.lastMove = "protect";
        tork.protectStreak = 1;
        Battler kyo = kyogre(0.38);
        kyo.moves.remove(2);
        // What the state builder now guesses: Water Spout no longer stands for the steady Water STAB.
        MoveInfo ph = guess(mv("phantomwater", "water", Category.SPECIAL, 95));
        kyo.moves.add(ph);
        kyo.lastMove = "waterspout";
        kyo.turnsActive = 1;
        Battler cres = estimated(mon("Cresselia", false, 50, new int[] {195, 101, 140, 139, 150, 101}, "psychic"));
        MoveInfo iw = mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes");
        iw.accuracy = 0.95;
        cres.moves = new ArrayList<>(List.of(iw, guess(mv("psychic", "psychic", Category.SPECIAL, 90)),
            guess(mv("powergem", "rock", Category.SPECIAL, 80)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        cres.lastMove = "icywind";
        cres.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.garchomp(), Tower31Replay.charizard(false)),
            List.of(kyo, cres));
        st.field.setPrimal("rain");
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    public static void scenarios() {
        // Nobody on our side outspeeds Kyogre this turn (Venusaur -1 from Icy Wind, Torkoal, Cresselia faster too), so
        // no line knocks it out now: the decision is who takes the hit. Printed for review.
        Planner.Plan p = turn2();
        System.out.println("  [info] evaristo2 T2: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));

        // A Primal Kyogre whose ability was not read keeps Primordial Sea up in the simulation, and knocking it out
        // ends the rain (so the sun can come back with the next Drought).
        BattleState st = last.copy();
        Battler kyo = st.opp(0);
        kyo.ability = null;
        kyo.possibleAbilities = List.of("drizzle");
        Battler tork = st.my(1);
        List<Action> calm = List.of(Action.move(true, 0, st.my(0).moves.get(0), false, -1),
            Action.move(true, 1, tork.moves.get(1), false, -1));
        String stays = "", ends = "";
        for (var o : new com.manueeh.cobbleai.engine.TurnSimulator(false).run(st, calm)) stays = String.valueOf(o.state().field.weather);
        BattleState dead = st.copy();
        dead.opp(0).hp = 0;
        for (var o : new com.manueeh.cobbleai.engine.TurnSimulator(false).run(dead, calm)) ends = String.valueOf(o.state().field.weather);
        String d = "Kyogre alive -> " + stays + ", Kyogre fainted -> " + ends;
        if ("rain".equals(stays) && "null".equals(ends)) EngineScenarios.pass("primal rain lasts while Kyogre stays and ends when it falls", d);
        else EngineScenarios.fail("primal rain lasts while Kyogre stays and ends when it falls", d);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
