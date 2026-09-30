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

/** Lost fight #10 vs Detective Casimiro: Hitmontop Wide Guard every turn vs our spread moves. */
public final class TowerCasimiroReplay {

    static BattleState last;

    /** Turn 6: Mega Charizard 88% + Scarf Garchomp 36% locked into Rock Slide; Hitmontop's full set is known. */
    static Planner.Plan casimiroTurn6() {
        Battler zard = Tower31Replay.charizard(true);
        zard.hp = zard.maxHp * 0.88;
        zard.boosts[MoveDex.ATK] = -1;
        zard.turnsActive = 3;
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = chomp.maxHp * 0.36;
        chomp.boosts[MoveDex.ATK] = -1;
        chomp.lockedMove = "rockslide";
        chomp.turnsActive = 4;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = 0;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = 0;
        Battler fz = estimated(mon("Charizard", false, 50, new int[] {153, 135, 98, 202, 135, 152}, "fire", "flying"));
        fz.ability = "drought";
        fz.boosts[MoveDex.SPA] = -2;
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        MoveInfo oh = mv("overheat", "fire", Category.SPECIAL, 130);
        oh.accuracy = 0.9;
        fz.moves = new ArrayList<>(List.of(hw, mv("protect", "normal", Category.STATUS, 0, "self"), oh,
            guess(mv("ancientpower", "rock", Category.SPECIAL, 60))));
        fz.turnsActive = 5;
        Battler top = estimated(mon("Hitmontop", false, 50, new int[] {125, 114, 115, 66, 130, 101}, "fighting"));
        top.hp = top.maxHp * 0.66;
        top.moves = new ArrayList<>(List.of(mv("fakeout", "normal", Category.PHYSICAL, 40), mv("wideguard", "rock", Category.STATUS, 0, "allySide"),
            mv("closecombat", "fighting", Category.PHYSICAL, 120), mv("feint", "normal", Category.PHYSICAL, 30)));
        top.moves.get(3).priority = 2;
        top.wideGuardUses = 1;
        top.lastMove = "wideguard";
        top.lastMoveStreak = 1;
        top.turnsActive = 3;
        BattleState st = EngineScenarios.state(true, List.of(zard, chomp, venu, tork), List.of(fz, top));
        st.stallTurns = 1;
        st.lastMoveIds.add("1:rockslide");
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(), List.of());
        reqs.get(1).moves.clear();
        reqs.get(1).moves.add(chomp.moves.get(3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        Planner.Plan p = casimiroTurn6();
        report("casimiro T6 Charizard does not Heat Wave into the Wide Guard shown last turn",
            !isMove(p.actions.get(0), "heatwave"), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
