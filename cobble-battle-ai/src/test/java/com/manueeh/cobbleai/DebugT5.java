package com.manueeh.cobbleai;

import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Evaluator;
import com.manueeh.cobbleai.engine.OpponentModel;
import com.manueeh.cobbleai.engine.TurnSimulator;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.ArrayList;
import java.util.List;

/** Debug dump of Tower27 turn 5 (not part of the scenario suite). */
public final class DebugT5 {
    public static void main(String[] args) {
        Battler tork = Tower27Replay.torkoal();
        tork.turnsActive = 0;
        Battler zard = Tower27Replay.charizardMegaY();
        zard.hp = zard.maxHp * 0.63;
        zard.boosts[MoveDex.SPE] = -1;
        zard.turnsActive = 2;
        Battler venu = Tower27Replay.venusaur();
        venu.hp = 0;
        Battler cress = Tower27Replay.cresselia(0.70);
        Battler lat = Tower27Replay.latios(0.64, true);
        lat.turnsActive = 2;
        Battler chomp = Tower27Replay.garchomp();
        BattleState st = EngineScenarios.state(true, List.of(tork, zard, chomp, venu), List.of(cress, lat));
        st.field.weather = "sun";
        st.field.weatherTurns = 1;
        st.oppUnseenReserves = 2;
        for (int slot = 0; slot < 2; slot++)
            for (var w : OpponentModel.policy(st, false, slot, st.oppKind))
                System.out.println("opp" + slot + " " + EngineScenarios.label(w.action()) + " p=" + String.format("%.2f", w.prob()));
        MoveInfo erupt = null, hw = null, prot = null;
        for (MoveInfo m : tork.moves) if (m.id.equals("eruption")) erupt = m;
        for (MoveInfo m : zard.moves) {
            if (m.id.equals("heatwave")) hw = m;
            if (m.id.equals("protect")) prot = m;
        }
        TurnSimulator sim = new TurnSimulator(false);
        for (MoveInfo zm : List.of(hw, prot)) {
            List<Action> all = new ArrayList<>(List.of(Action.move(true, 0, erupt, false, -1), Action.move(true, 1, zm, false, -1)));
            all.add(OpponentModel.policy(st, false, 0, st.oppKind).get(0).action());
            all.add(OpponentModel.policy(st, false, 1, st.oppKind).get(0).action());
            for (var o : sim.run(st, all)) {
                BattleState r = o.state();
                System.out.println(zm.id + " eval=" + String.format("%.3f", Evaluator.evaluate(r)) + " chance=" + String.format("%.3f", r.chanceValue)
                    + " mine=" + r.my(0) + "," + r.my(1) + " opp=" + r.opp(0) + "," + r.opp(1) + " w=" + r.field.weather);
            }
        }
    }
}
