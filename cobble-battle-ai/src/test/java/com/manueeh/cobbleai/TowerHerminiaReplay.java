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
import static com.manueeh.cobbleai.Tower31Replay.isMove;
import static com.manueeh.cobbleai.Tower31Replay.protects;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Fight #28 vs Veterana Herminia (Gengar + Deoxys lead, Hitmontop Wide Guard spam, Suicune). */
public final class TowerHerminiaReplay {

    static Battler deoxys(double hp) {
        Battler b = estimated(mon("Deoxys", false, 50, new int[] {125, 222, 70, 181, 70, 222}, "psychic"));
        b.hp = b.maxHp * hp;
        b.moves = new ArrayList<>(List.of(guess(mv("psychoboost", "psychic", Category.SPECIAL, 140)),
            guess(mv("icepunch", "ice", Category.PHYSICAL, 75)), guess(mv("protect", "normal", Category.STATUS, 0, "self")),
            guess(mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes")), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"))));
        b.moves.get(0).accuracy = 0.9;
        return b;
    }

    /** Turn 1: Gengar + Deoxys (both very fast special attackers); Venusaur must not gamble unprotected. */
    static Planner.Plan herminiaTurn1() {
        Battler gengar = estimated(mon("Gengar", false, 50, new int[] {135, 96, 80, 244, 95, 200}, "ghost", "poison"));
        gengar.moves = new ArrayList<>(List.of(guess(mv("sludgewave", "poison", Category.SPECIAL, 95, "allAdjacent")),
            guess(mv("dazzlinggleam", "fairy", Category.SPECIAL, 80, "allAdjacentFoes")), guess(mv("thunderbolt", "electric", Category.SPECIAL, 90)),
            guess(mv("shadowball", "ghost", Category.SPECIAL, 80)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        BattleState st = EngineScenarios.state(true, List.of(Tower31Replay.venusaur(), Tower31Replay.torkoal(),
            Tower31Replay.charizard(false), Tower31Replay.garchomp()), List.of(gengar, deoxys(1.0)));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Turn 7: Hitmontop has used Wide Guard every turn; Deoxys at 1%. Torkoal: single-target Earth Power, not Heat Wave. */
    static Planner.Plan herminiaTurn7() {
        Battler zard = Tower31Replay.charizard(true);
        zard.hp = zard.maxHp * 0.65;
        zard.protectStreak = 1;
        zard.turnsActive = 3;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.47;
        tork.turnsActive = 6;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = 0;
        Battler chomp = Tower31Replay.garchomp();
        Battler dx = deoxys(0.01);
        dx.moves.get(0).revealed = true;
        Battler top = estimated(mon("Hitmontop", false, 50, new int[] {125, 161, 115, 66, 130, 101}, "fighting"));
        top.hp = top.maxHp * 0.33;
        top.moves = new ArrayList<>(List.of(mv("fakeout", "normal", Category.PHYSICAL, 40), mv("wideguard", "rock", Category.STATUS, 0, "allySide"),
            guess(mv("stoneedge", "rock", Category.PHYSICAL, 100)), guess(mv("closecombat", "fighting", Category.PHYSICAL, 120)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"))));
        top.moves.get(2).accuracy = 0.8;
        top.wideGuardUses = 3;
        top.lastMove = "wideguard";
        top.lastMoveStreak = 3;
        top.turnsActive = 4;
        BattleState st = EngineScenarios.state(true, List.of(zard, tork, venu, chomp), List.of(dx, top));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 0;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(3), List.of(3)));
    }

    static BattleState last;

    public static void scenarios() {
        Planner.Plan p = herminiaTurn1();
        Action venu = p.actions.get(0);
        report("herminia T1 Venusaur does not gamble unprotected vs Gengar + Deoxys", !isMove(venu, "sleeppowder"), p);
        p = herminiaTurn7();
        Action tork = p.actions.get(1), zard = p.actions.get(0);
        boolean spreadIntoGuard = isMove(tork, "heatwave") || isMove(tork, "eruption");
        report("herminia T7 no spread move into a Wide Guard used every turn", !spreadIntoGuard && !isMove(zard, "heatwave"), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
