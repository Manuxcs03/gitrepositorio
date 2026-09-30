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

/** Lost fight vs Patinador Cesar: Hitmontop (Fake Out) + Dragonite (Ice Spinner known), Life Orb Garchomp set. */
public final class TowerCesarReplay {

    static BattleState last;

    static Battler lifeOrbGarchomp() {
        Battler b = mon("Garchomp", true, 50, new int[] {183, 182, 115, 90, 105, 168}, "dragon", "ground");
        b.item = "lifeorb";
        b.ability = "roughskin";
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("stompingtantrum", "ground", Category.PHYSICAL, 75), mv("dragonclaw", "dragon", Category.PHYSICAL, 80), rs));
        return b;
    }

    static Planner.Plan cesarTurn1() {
        Battler venu = Tower31Replay.venusaur();
        venu.boosts[MoveDex.ATK] = -1;
        Battler tork = Tower31Replay.torkoal();
        tork.boosts[MoveDex.ATK] = -1;
        Battler dnite = estimated(mon("Dragonite", false, 50, new int[] {166, 204, 115, 131, 120, 145}, "dragon", "flying"));
        dnite.possibleAbilities = List.of("innerfocus", "multiscale");
        dnite.moves = new ArrayList<>(List.of(mv("icespinner", "ice", Category.PHYSICAL, 80),
            guess(mv("stoneedge", "rock", Category.PHYSICAL, 100)), guess(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent")),
            guess(mv("dragonclaw", "dragon", Category.PHYSICAL, 80)), guess(mv("dragondance", "dragon", Category.STATUS, 0, "self")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        dnite.moves.get(1).accuracy = 0.8;
        Battler top = estimated(mon("Hitmontop", false, 50, new int[] {125, 152, 115, 66, 130, 101}, "fighting"));
        top.moves = new ArrayList<>(List.of(mv("fakeout", "normal", Category.PHYSICAL, 40), mv("closecombat", "fighting", Category.PHYSICAL, 120),
            mv("suckerpunch", "dark", Category.PHYSICAL, 70), guess(mv("wideguard", "rock", Category.STATUS, 0, "allySide"))));
        top.moves.get(2).priority = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.charizard(false), lifeOrbGarchomp()),
            List.of(dnite, top));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    public static void scenarios() {
        Planner.Plan p = cesarTurn1();
        boolean chompIn = false;
        for (Action a : p.actions) chompIn |= a.kind == Action.Kind.SWITCH && a.switchTo == 3;
        report("cesar T1 no Garchomp switch-in against a revealed Ice Spinner", !chompIn, p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
