package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.OpponentModel;
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
import static com.manueeh.cobbleai.Tower31Replay.isMove;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight #14 vs Tactica Ines: Mega Lucario + Rotom-Wash lead, Amoonguss, Hydreigon (Levitate). */
public final class TowerInesReplay {

    static BattleState last;

    /** Turn 1: both remembered foe attacks (Aura Sphere, Hydro Pump) are super effective on the slowest Torkoal. */
    static Planner.Plan inesTurn1() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        Battler luc = estimated(mon("Lucario", false, 50, new int[] {145, 131, 108, 205, 90, 223}, "fighting", "steel"));
        luc.moves = new ArrayList<>(List.of(mv("aurasphere", "fighting", Category.SPECIAL, 80), mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("flashcannon", "steel", Category.SPECIAL, 80), guess(mv("nastyplot", "dark", Category.STATUS, 0, "self"))));
        Battler rotom = estimated(mon("Rotom", false, 50, new int[] {110, 96, 127, 172, 177, 151}, "electric", "water"));
        rotom.possibleAbilities = List.of("levitate");
        MoveInfoHolder.hp(rotom);
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, zard, chomp), List.of(luc, rotom));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    static final class MoveInfoHolder {
        static void hp(Battler rotom) {
            var hp = mv("hydropump", "water", Category.SPECIAL, 110);
            hp.accuracy = 0.8;
            var bz = guess(mv("blizzard", "ice", Category.SPECIAL, 110, "allAdjacentFoes"));
            bz.accuracy = 0.7;
            rotom.moves = new ArrayList<>(List.of(hp, bz, guess(mv("thunderbolt", "electric", Category.SPECIAL, 90)),
                guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        }
    }

    public static void scenarios() {
        Planner.Plan p = inesTurn1();
        Action t = p.actions.get(1);
        report("ines T1 Torkoal (slowest, weak to both remembered attacks) does not just click Heat Wave",
            !isMove(t, "heatwave") && !isMove(t, "eruption") && !isMove(t, "earthpower"), p);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 2; i++) {
            inesTurn1();
            for (OpponentModel.Weighted w : OpponentModel.policy(last, false, i, last.oppKind))
                System.out.println(" foe" + i + " " + EngineScenarios.label(w.action()) + " " + String.format("%.3f", w.prob()));
        }
        scenarios();
    }
}
