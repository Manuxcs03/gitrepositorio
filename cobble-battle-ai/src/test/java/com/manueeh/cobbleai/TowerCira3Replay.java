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

import static com.manueeh.cobbleai.EngineScenarios.mon;
import static com.manueeh.cobbleai.EngineScenarios.mv;
import static com.manueeh.cobbleai.Tower31Replay.estimated;
import static com.manueeh.cobbleai.Tower31Replay.guess;
import static com.manueeh.cobbleai.Tower31Replay.protects;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight vs Vigilante Cira (3rd): endgame Torkoal + Charizard vs Garchomp 46% + Togekiss 30%, last sun turn. */
public final class TowerCira3Replay {

    static BattleState last;

    static Battler newTorkoal() {
        Battler b = mon("Torkoal", true, 50, new int[] {145, 115, 160, 137, 90, 25}, "fire");
        b.item = "charcoalstick";
        b.ability = "drought";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"),
            mv("protect", "normal", Category.STATUS, 0, "self"), mv("ember", "fire", Category.SPECIAL, 40), hw));
        return b;
    }

    static Planner.Plan cira3Turn5() {
        Battler tork = newTorkoal();
        Battler zard = Tower31Replay.charizard(false);
        Battler venu = Tower31Replay.venusaur();
        venu.hp = 0;
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = 0;
        Battler gar = Tower31Replay.foeGarchomp();
        gar.hp = gar.maxHp * 0.46;
        gar.moves = new ArrayList<>(List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"),
            mv("protect", "normal", Category.STATUS, 0, "self")));
        gar.moves.get(2).accuracy = 0.9;
        gar.turnsActive = 2;
        Battler kiss = estimated(mon("Togekiss", false, 50, new int[] {160, 81, 115, 139, 135, 145}, "fairy", "flying"));
        kiss.hp = kiss.maxHp * 0.30;
        kiss.item = "leftovers";
        kiss.itemUnknown = false;
        MoveInfo as = mv("airslash", "flying", Category.SPECIAL, 75);
        as.accuracy = 0.95;
        kiss.moves = new ArrayList<>(List.of(as, mv("followme", "normal", Category.STATUS, 0, "self"),
            mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), guess(mv("dazzlinggleam", "fairy", Category.SPECIAL, 80, "allAdjacentFoes")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        kiss.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(tork, zard, venu, chomp), List.of(gar, kiss));
        st.field.weather = "sun";
        st.field.weatherTurns = 1;
        st.oppUnseenReserves = 0;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(), List.of()));
    }

    public static void scenarios() {
        Planner.Plan p = cira3Turn5();
        // Faster Garchomp with Rock Slide KOs Charizard (4x) or Torkoal before either acts: the position is lost
        // here whatever we pick. Printed for review.
        System.out.println("  [info] cira3 T5: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
    }

    public static void main(String[] args) {
        scenarios();
    }
}
