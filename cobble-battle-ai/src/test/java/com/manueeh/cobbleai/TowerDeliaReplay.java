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

/** Lost Battle Tower fight #29 vs Comandante Delia (Pelipper Wide Guard + Dragonite, Landorus, Rotom-Wash). */
public final class TowerDeliaReplay {

    static Battler pelipper() {
        Battler b = estimated(mon("Pelipper", false, 50, new int[] {135, 81, 120, 161, 90, 96}, "water", "flying"));
        b.ability = "drizzle";
        b.moves = new ArrayList<>(List.of(mv("wideguard", "rock", Category.STATUS, 0, "allySide"),
            guess(mv("surf", "water", Category.SPECIAL, 90, "allAdjacent")), guess(mv("icebeam", "ice", Category.SPECIAL, 90)),
            guess(mv("hurricane", "flying", Category.SPECIAL, 110)), guess(mv("protect", "normal", Category.STATUS, 0, "self")),
            guess(mv("tailwind", "flying", Category.STATUS, 0, "allySide"))));
        b.moves.get(3).accuracy = 0.7;
        b.wideGuardUses = 1;
        b.lastMove = "wideguard";
        b.lastMoveStreak = 1;
        b.protectStreak = 1;
        b.turnsActive = 1;
        return b;
    }

    static Battler dragonite(double hp) {
        Battler b = estimated(mon("Dragonite", false, 50, new int[] {166, 155, 115, 216, 120, 167}, "dragon", "flying"));
        b.hp = b.maxHp * hp;
        b.possibleAbilities = List.of("innerfocus", "multiscale");
        MoveInfo hur = mv("hurricane", "flying", Category.SPECIAL, 110);
        hur.accuracy = 0.7;
        MoveInfo draco = guess(mv("dracometeor", "dragon", Category.SPECIAL, 130));
        draco.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(hur, draco, guess(mv("surf", "water", Category.SPECIAL, 90, "allAdjacent")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), guess(mv("tailwind", "flying", Category.STATUS, 0, "allySide"))));
        b.lastMove = "hurricane";
        b.turnsActive = 1;
        return b;
    }

    /** Turn 2: Venusaur protected last turn; Dragonite fired Hurricane into it; Wide Guard shown (sun: Hurricane 50%). */
    static Planner.Plan deliaTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.protectStreak = 1;
        venu.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 1;
        Battler dnite = dragonite(1.0);
        dnite.lastTarget = venu.uuid;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.charizard(false), Tower31Replay.garchomp()),
            List.of(pelipper(), dnite));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Turn 4: fresh Charizard (can Mega) + Torkoal vs Rotom-Wash and Dragonite 40% -2 SpA; nothing in the back. */
    static Planner.Plan deliaTurn4() {
        Battler zard = Tower31Replay.charizard(false);
        Battler tork = Tower31Replay.torkoal();
        tork.boosts[MoveDex.ATK] = -1;
        tork.turnsActive = 3;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = 0;
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = 0;
        Battler rotom = estimated(mon("Rotom", false, 50, new int[] {125, 96, 127, 172, 127, 151}, "electric", "water"));
        rotom.ability = "levitate";
        MoveInfo hp = guess(mv("hydropump", "water", Category.SPECIAL, 110));
        hp.accuracy = 0.8;
        rotom.moves = new ArrayList<>(List.of(hp, guess(mv("thunderbolt", "electric", Category.SPECIAL, 90)),
            guess(mv("willowisp", "fire", Category.STATUS, 0, "normal")), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        Battler dnite = dragonite(0.40);
        dnite.boosts[MoveDex.SPA] = -2;
        dnite.moves.get(1).revealed = true;
        dnite.lastMove = "dracometeor";
        BattleState st = EngineScenarios.state(true, List.of(zard, tork, venu, chomp), List.of(rotom, dnite));
        st.field.weather = "sun";
        st.field.weatherTurns = 2;
        st.oppUnseenReserves = 0;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(), List.of()));
    }

    static BattleState last;

    public static void scenarios() {
        Planner.Plan p = deliaTurn2();
        System.out.println("  [info] delia T2: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
        p = deliaTurn4();
        System.out.println("  [info] delia T4: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
    }

    public static void main(String[] args) {
        scenarios();
    }
}
