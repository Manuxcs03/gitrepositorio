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

/** Lost fight #20 vs Elite Adriano (Landorus + Cresselia Helping Hand / Icy Wind, Excadrill). */
public final class TowerAdrianoReplay {

    static Battler landorus(double hp) {
        Battler b = estimated(mon("Landorus", false, 50, new int[] {164, 216, 110, 136, 100, 157}, "ground", "flying"));
        b.hp = b.maxHp * hp;
        b.ability = "intimidate";
        MoveInfo rs = guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"));
        rs.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(mv("stompingtantrum", "ground", Category.PHYSICAL, 75),
            guess(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent")), guess(mv("outrage", "dragon", Category.PHYSICAL, 120)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), rs));
        return b;
    }

    static Battler cresselia(double hp) {
        Battler b = estimated(mon("Cresselia", false, 50, new int[] {195, 101, 140, 106, 150, 150}, "psychic"));
        b.hp = b.maxHp * hp;
        b.item = "sitrusberry";
        b.itemUnknown = false;
        b.ability = "levitate";
        MoveInfo icy = mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes");
        icy.accuracy = 0.95;
        b.moves = new ArrayList<>(List.of(mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), icy,
            guess(mv("psychic", "psychic", Category.SPECIAL, 90)), guess(mv("moonblast", "fairy", Category.SPECIAL, 95)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        return b;
    }

    /** Turn 1: Torkoal + Venusaur (-1 Atk) vs Landorus (Stomping Tantrum) + Cresselia (Helping Hand). */
    static Planner.Plan adrianoTurn1() {
        Battler l = landorus(1.0);
        l.moves.get(0).revealed = false;
        Battler c = cresselia(1.0);
        c.moves.get(0).revealed = false;
        c.moves.get(1).revealed = false;
        Battler venu = Tower31Replay.venusaur();
        venu.boosts[MoveDex.ATK] = -1;
        Battler tork = Tower31Replay.torkoal();
        tork.boosts[MoveDex.ATK] = -1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.charizard(false), Tower31Replay.garchomp()),
            List.of(c, l));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Turn 3: Landorus 1% asleep since last turn (may wake: 1/3), faster than Charizard -1 Spe, slower than Venusaur. */
    static Planner.Plan adrianoTurn3() {
        Battler venu = Tower31Replay.venusaur();
        venu.hp = venu.maxHp * 0.81;
        venu.boosts[MoveDex.ATK] = -1;
        venu.boosts[MoveDex.SPE] = -1;
        venu.turnsActive = 2;
        Battler zard = Tower31Replay.charizard(false);
        zard.hp = zard.maxHp * 0.76;
        zard.boosts[MoveDex.SPE] = -1;
        zard.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = 0;
        Battler c = cresselia(0.73);
        c.turnsActive = 2;
        Battler l = landorus(0.01);
        l.status = "slp";
        l.sleepTurns = 2;
        l.turnsActive = 2;
        BattleState st = EngineScenarios.state(true, List.of(venu, zard, tork, Tower31Replay.garchomp()), List.of(c, l));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(3), List.of(3)));
    }

    static BattleState last;

    public static void scenarios() {
        Planner.Plan p = adrianoTurn1();
        System.out.println("  [info] adriano T1: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
        p = adrianoTurn3();
        Action venu = p.actions.get(0), zard = p.actions.get(1);
        boolean venuFinishes = venu.kind == Action.Kind.MOVE && venu.move.isDamaging() && venu.targetSlot == 1;
        report("adriano T3 the 1% sleeping Landorus is finished (or Charizard covered) before it can wake",
            venuFinishes || protects(zard) || zard.kind == Action.Kind.SWITCH, p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
