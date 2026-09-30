package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.OpponentModel;
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

/** Lost fight #9 vs Bailarin Adan: Hitmontop + Volcarona, then Garchomp + Aerodactyl. */
public final class TowerAdanReplay {

    static BattleState last;

    /** Turn 4: our Scarf Garchomp 10% locked into Dragon Claw outspeeds their Garchomp 8%; Mega Charizard is slower than both foes. */
    static Planner.Plan adanTurn4() {
        Battler zard = Tower31Replay.charizard(true);
        zard.hp = zard.maxHp * 0.88;
        zard.turnsActive = 2;
        Battler chomp = Tower31Replay.garchomp();
        chomp.moves = new ArrayList<>(List.of(mv("ironhead", "steel", Category.PHYSICAL, 80), mv("stompingtantrum", "ground", Category.PHYSICAL, 75),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes")));
        chomp.hp = chomp.maxHp * 0.10;
        chomp.lockedMove = "dragonclaw";
        chomp.turnsActive = 2;
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.6;
        Battler aero = estimated(mon("Aerodactyl", false, 50, new int[] {155, 159, 85, 91, 95, 200}, "rock", "flying"));
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        aero.moves = new ArrayList<>(List.of(mv("dualwingbeat", "flying", Category.PHYSICAL, 40), rs,
            guess(mv("icefang", "ice", Category.PHYSICAL, 65)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        aero.turnsActive = 0;
        Battler gar = Tower31Replay.foeGarchomp();
        gar.hp = gar.maxHp * 0.08;
        gar.moves = new ArrayList<>(List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            mv("stompingtantrum", "ground", Category.PHYSICAL, 75), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        gar.lastMove = "earthquake";
        gar.turnsActive = 3;
        BattleState st = EngineScenarios.state(true, List.of(zard, chomp, venu, tork), List.of(aero, gar));
        st.field.weather = "sun";
        st.field.weatherTurns = 2;
        st.oppUnseenReserves = 0;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        reqs.get(1).moves.clear();
        reqs.get(1).moves.add(chomp.moves.get(2));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    /** Talia #13: a known Wide Guard Hitmontop past its Fake Out turn is expected to Wide Guard. */
    static double habitualWideGuard() {
        Battler chomp = Tower31Replay.garchomp();
        Battler tork = Tower31Replay.torkoal();
        Battler top = estimated(mon("Hitmontop", false, 50, new int[] {125, 161, 115, 66, 130, 101}, "fighting"));
        MoveInfo fo = mv("fakeout", "normal", Category.PHYSICAL, 40);
        fo.priority = 3;
        top.moves = new ArrayList<>(List.of(fo, mv("wideguard", "rock", Category.STATUS, 0, "allySide"),
            mv("closecombat", "fighting", Category.PHYSICAL, 120), guess(mv("feint", "normal", Category.PHYSICAL, 30))));
        top.wideGuardUses = 2;
        top.turnsActive = 1;
        top.lastMove = "fakeout";
        Battler chand = estimated(mon("Chandelure", false, 50, new int[] {135, 86, 110, 216, 110, 145}, "ghost", "fire"));
        chand.moves = new ArrayList<>(List.of(mv("shadowball", "ghost", Category.SPECIAL, 80)));
        BattleState st = EngineScenarios.state(true, List.of(chomp, tork), List.of(top, chand));
        double p = 0;
        for (OpponentModel.Weighted w : OpponentModel.policy(st, false, 0, st.oppKind))
            if (w.action().move != null && w.action().move.id.equals("wideguard")) p += w.prob();
        return p;
    }

    public static void scenarios() {
        double wg = habitualWideGuard();
        String n = "talia2 a known Wide Guard Hitmontop past its Fake Out turn is expected to Wide Guard (" + Math.round(wg * 100) + "%)";
        if (wg >= 0.7) EngineScenarios.pass(n, "");
        else EngineScenarios.fail(n, "");
        Planner.Plan p = adanTurn4();
        Action c = p.actions.get(1);
        // Our 10% Garchomp falls to Rock Slide either way: two thirds of a full Aerodactyl beat the 8% Garchomp.
        System.out.println("  [info] adan T4: " + Tower31Replay.label(p) + " (Charizard " + (Tower31Replay.protects(p.actions.get(0)) ? "protects" : "attacks") + ")");
    }

    public static void main(String[] args) {
        Planner.Plan p = adanTurn4();
        for (int i = 0; i < 2; i++)
            for (OpponentModel.Weighted w : OpponentModel.policy(last, false, i, last.oppKind))
                System.out.println(" foe" + i + " " + EngineScenarios.label(w.action()) + " " + String.format("%.3f", w.prob()));
        scenarios();
    }
}
