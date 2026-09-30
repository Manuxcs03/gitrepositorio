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
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight #19 vs Estratega Boris (2nd): endgame Venusaur + Scarf Garchomp vs Garchomp 9% + Metagross. */
public final class TowerBoris2Replay {

    static BattleState last;

    static Planner.Plan boris2Turn6() {
        Battler venu = Tower31Replay.venusaur();
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = chomp.maxHp * 0.44;
        chomp.lockedMove = "dragonclaw";
        chomp.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = 0;
        Battler zard = Tower31Replay.charizard(true);
        zard.hp = 0;
        Battler gar = Tower31Replay.foeGarchomp();
        gar.hp = gar.maxHp * 0.09;
        gar.moves = new ArrayList<>(List.of(mv("stompingtantrum", "ground", Category.PHYSICAL, 75), mv("dragonclaw", "dragon", Category.PHYSICAL, 80),
            mv("protect", "normal", Category.STATUS, 0, "self"), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"))));
        gar.lastMove = "dragonclaw";
        gar.turnsActive = 3;
        gar.protectUses = 1;
        Battler meta = estimated(mon("Metagross", false, 50, new int[] {155, 205, 150, 126, 110, 101}, "steel", "psychic"));
        MoveInfo bp = mv("bulletpunch", "steel", Category.PHYSICAL, 40);
        bp.priority = 1;
        meta.moves = new ArrayList<>(List.of(mv("protect", "normal", Category.STATUS, 0, "self"), bp,
            guess(mv("zenheadbutt", "psychic", Category.PHYSICAL, 80)), guess(mv("icepunch", "ice", Category.PHYSICAL, 75))));
        meta.moves.get(2).accuracy = 0.9;
        meta.protectStreak = 1;
        meta.turnsActive = 2;
        BattleState st = EngineScenarios.state(true, List.of(venu, chomp, tork, zard), List.of(gar, meta));
        st.oppUnseenReserves = 0;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(), List.of());
        reqs.get(1).moves.clear();
        reqs.get(1).moves.add(chomp.moves.get(2));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        Planner.Plan p = boris2Turn6();
        Action venu = p.actions.get(0);
        report("boris2 T6 Venusaur does not waste Sleep Powder on the 9% Garchomp our Dragon Claw already covers",
            !(isMove(venu, "sleeppowder") && venu.targetSlot == 0), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
