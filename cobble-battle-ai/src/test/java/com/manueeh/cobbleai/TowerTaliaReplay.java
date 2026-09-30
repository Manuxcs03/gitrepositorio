package com.manueeh.cobbleai;

import com.manueeh.cobbleai.data.MoveDex;
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
import static com.manueeh.cobbleai.Tower31Replay.isMove;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Close fight #12 vs Ranger Talia: Mega Charizard Y + Hitmontop repeating Wide Guard. */
public final class TowerTaliaReplay {

    static BattleState last;

    /** Turn 8: Venusaur 90% (protected last turn) + burned Garchomp 20% vs Charizard 100% + Hitmontop (Wide Guard x2). */
    static Planner.Plan taliaTurn8() {
        Battler venu = Tower31Replay.venusaur();
        venu.hp = venu.maxHp * 0.9;
        venu.boosts[MoveDex.ATK] = -1;
        venu.protectStreak = 1;
        venu.lastMove = "protect";
        venu.turnsActive = 5;
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = chomp.maxHp * 0.2;
        chomp.status = "brn";
        chomp.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.status = "slp";
        tork.sleepTurns = 3;
        Battler zard = Tower31Replay.charizard(true);
        zard.status = "slp";
        zard.sleepTurns = 2;
        Battler fz = estimated(mon("Charizard", false, 50, new int[] {153, 135, 98, 213, 135, 167}, "fire", "flying"));
        fz.ability = "drought";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        MoveInfo as = mv("airslash", "flying", Category.SPECIAL, 75);
        as.accuracy = 0.95;
        fz.moves = new ArrayList<>(List.of(hw, as, guess(mv("dragonpulse", "dragon", Category.SPECIAL, 85)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        fz.lastMove = "heatwave";
        fz.turnsActive = 1;
        Battler top = estimated(mon("Hitmontop", false, 50, new int[] {125, 111, 115, 66, 130, 101}, "fighting"));
        top.hp = top.maxHp * 0.7;
        top.item = null;
        top.itemUnknown = false;
        top.moves = new ArrayList<>(List.of(mv("wideguard", "rock", Category.STATUS, 0, "allySide"),
            mv("closecombat", "fighting", Category.PHYSICAL, 120), mv("fakeout", "normal", Category.PHYSICAL, 40),
            guess(mv("stoneedge", "rock", Category.PHYSICAL, 100))));
        top.moves.get(3).accuracy = 0.8;
        top.wideGuardUses = 2;
        top.lastMove = "wideguard";
        top.lastMoveStreak = 2;
        top.turnsActive = 3;
        BattleState st = EngineScenarios.state(true, List.of(venu, chomp, tork, zard), List.of(fz, top));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 0;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    /** Turn 1 of #12 Talia and #16 Casimiro: Venusaur + Torkoal vs Mega Charizard Y + Chlorophyll Venusaur in sun. */
    static Planner.Plan sunMirrorTurn1() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        Battler fz = estimated(mon("Charizard", false, 50, new int[] {153, 135, 98, 198, 135, 152}, "fire", "flying"));
        fz.ability = "drought";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        MoveInfo oh = mv("overheat", "fire", Category.SPECIAL, 130);
        oh.accuracy = 0.9;
        fz.moves = new ArrayList<>(List.of(hw, mv("protect", "normal", Category.STATUS, 0, "self"), oh,
            guess(mv("ancientpower", "rock", Category.SPECIAL, 60)), guess(mv("tailwind", "flying", Category.STATUS, 0, "allySide"))));
        Battler fv = estimated(mon("Venusaur", false, 50, new int[] {155, 113, 103, 159, 120, 168}, "grass", "poison"));
        fv.ability = "chlorophyll";
        MoveInfo sp = mv("sleeppowder", "grass", Category.STATUS, 0, "normal");
        sp.accuracy = 0.75;
        fv.moves = new ArrayList<>(List.of(sp, mv("sludgebomb", "poison", Category.SPECIAL, 90), mv("gigadrain", "grass", Category.SPECIAL, 75),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, zard, chomp), List.of(fz, fv));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        sunMirror();
        Planner.Plan p = taliaTurn8();
        double wg = 0;
        for (OpponentModel.Weighted w : OpponentModel.policy(last, false, 1, last.oppKind))
            if (w.action().move.id.equals("wideguard")) wg += w.prob();
        report("talia T8 a Hitmontop that used Wide Guard twice in a row is expected to use it again (" + Math.round(wg * 100) + "%)",
            wg >= 0.6, p);
        // Rock Slide stays a fair gamble here: Chlorophyll Venusaur outspeeds and Sludge Bomb + Rock Slide KO the
        // Charizard if Hitmontop attacks instead; a Dragon Claw only saves 0.25 in the Wide Guard case.
        System.out.println("  [info] talia T8 chose " + Tower31Replay.label(p) + " (" + (isMove(p.actions.get(1), "rockslide") ? "gamble on no Wide Guard" : "plays around Wide Guard") + ")");
    }

    static void sunMirror() {
        Planner.Plan p = sunMirrorTurn1();
        System.out.println("  [info] sun mirror T1 (#12/#16) chose " + Tower31Replay.label(p) + " (Sleep Powder hit in #12, missed in #16)");
    }

    public static void main(String[] args) {
        scenarios();
    }
}
