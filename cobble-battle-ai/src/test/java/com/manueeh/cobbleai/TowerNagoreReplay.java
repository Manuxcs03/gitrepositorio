package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;

import java.util.ArrayList;
import java.util.List;

import static com.manueeh.cobbleai.EngineScenarios.mon;
import static com.manueeh.cobbleai.EngineScenarios.mv;
import static com.manueeh.cobbleai.Tower31Replay.estimated;
import static com.manueeh.cobbleai.Tower31Replay.guess;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight #15 vs Boxeadora Nagore: Whimsicott Tailwind + Metagross, then Garchomp + Rotom. */
public final class TowerNagoreReplay {

    static BattleState last;

    /** Turn 3: Scarf Garchomp 39% + Torkoal 100% vs Rotom 100% + Garchomp 43%, their Tailwind up, sun 3 turns. */
    static Planner.Plan nagoreTurn3() {
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = chomp.maxHp * 0.39;
        chomp.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 2;
        Battler venu = Tower31Replay.venusaur();
        Battler zard = Tower31Replay.charizard(false);
        Battler rotom = estimated(mon("Rotom", false, 50, new int[] {110, 96, 127, 172, 127, 126}, "electric", "ghost"));
        rotom.possibleAbilities = List.of("levitate");
        rotom.moves = new ArrayList<>(List.of(mv("thunderbolt", "electric", Category.SPECIAL, 90),
            mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), guess(mv("blizzard", "ice", Category.SPECIAL, 110, "allAdjacentFoes")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        rotom.moves.get(1).accuracy = 0.9;
        rotom.moves.get(2).accuracy = 0.7;
        Battler gar = Tower31Replay.foeGarchomp();
        gar.hp = gar.maxHp * 0.43;
        gar.moves = new ArrayList<>(List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        gar.moves.get(2).accuracy = 0.9;
        gar.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(chomp, tork, venu, zard), List.of(rotom, gar));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.field.side(false).tailwind = true;
        st.oppUnseenReserves = 1;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        Planner.Plan p = nagoreTurn3();
        Action c = p.actions.get(0);
        // Charizard is no safe landing either: that Garchomp alternated Earthquake and Rock Slide all fight long
        // (EQ, EQ, RS, RS, EQ in the log) and Rotom's shown Thunderbolt outspeeds and one-shots it too. Losing the
        // 39% Garchomp beats handing them the full Charizard (log 17: Ruben's Rock Slide took it twice).
        boolean zardIn = false;
        for (Action a : p.actions) zardIn |= a.kind == Action.Kind.SWITCH && a.switchTo == 3;
        report("nagore T3 no Charizard into the Rock Slide Garchomp + Thunderbolt Rotom", !zardIn, p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
