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
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight #23 vs Experto Cirilo: Tailwind, Latios (Psychic into Venusaur) + Landorus (Earthquake). */
public final class TowerCiriloReplay {

    static BattleState last;

    static Planner.Plan ciriloTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.boosts[MoveDex.ATK] = -1;
        venu.protectStreak = 1;
        venu.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.boosts[MoveDex.ATK] = -1;
        tork.turnsActive = 1;
        Battler lat = estimated(mon("Latios", false, 50, new int[] {155, 121, 110, 200, 130, 178}, "dragon", "psychic"));
        lat.hp = lat.maxHp * 0.55;
        MoveInfo icy = mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes");
        icy.accuracy = 0.95;
        lat.moves = new ArrayList<>(List.of(mv("psychic", "psychic", Category.SPECIAL, 90), icy, mv("protect", "normal", Category.STATUS, 0, "self"),
            guess(mv("dracometeor", "dragon", Category.SPECIAL, 130))));
        lat.moves.get(3).accuracy = 0.9;
        lat.lastMove = "psychic";
        lat.lastTarget = venu.uuid;
        lat.turnsActive = 1;
        Battler lando = estimated(mon("Landorus", false, 50, new int[] {164, 196, 110, 136, 100, 157}, "ground", "flying"));
        lando.ability = "intimidate";
        MoveInfo rs = guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"));
        rs.accuracy = 0.9;
        lando.moves = new ArrayList<>(List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            guess(mv("outrage", "dragon", Category.PHYSICAL, 120)), guess(mv("protect", "normal", Category.STATUS, 0, "self")), rs));
        lando.moves.get(0).revealed = false;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.charizard(false), Tower31Replay.garchomp()),
            List.of(lat, lando));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.field.theirs.tailwind = true;
        st.oppUnseenReserves = 1;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    public static void scenarios() {
        Planner.Plan p = ciriloTurn2();
        boolean garchompIn = false;
        for (Action a : p.actions) garchompIn |= a.kind == Action.Kind.SWITCH && a.switchTo == 3;
        // Garchomp vs Charizard switch-in is close (Charizard fears a guessed Rock Slide); Latios' item-boosted Psychic
        // was not knowable yet. Printed for review.
        System.out.println("  [info] cirilo T2: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
    }

    public static void main(String[] args) {
        scenarios();
    }
}
