package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.OpponentModel;
import com.manueeh.cobbleai.model.BattleState;

/** Debug dump of #31 turn 2 opponent predictions (not part of the suite). */
public final class DebugT31 {
    public static void main(String[] args) {
        Tower31Replay.t31turn2();
        BattleState st = Tower31Replay.lastState;
        for (int slot = 0; slot < st.slots(); slot++) {
            System.out.println("opp" + slot + " " + st.opp(slot) + " moves=" + st.opp(slot).moves);
            for (var w : OpponentModel.policy(st, false, slot, st.oppKind))
                System.out.println("   " + EngineScenarios.label(w.action()) + " p=" + String.format("%.2f", w.prob()) + " score=" + String.format("%.2f", w.score()));
        }
        System.out.println("mine: " + st.my(0) + " " + st.my(1));
    }
}
