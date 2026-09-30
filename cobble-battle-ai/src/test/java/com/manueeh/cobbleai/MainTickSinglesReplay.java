package com.manueeh.cobbleai;

import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Evaluator;
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

/** Singles loss vs the player MainTick: Smeargle (Sticky Web + Stealth Rock) then Speed Boost Mega Blaziken. */
public final class MainTickSinglesReplay {

    static BattleState last;

    static Battler blaziken(double hp, int atk, int spe) {
        Battler b = estimated(mon("Blaziken", false, 100, new int[] {301, 460, 196, 319, 196, 328}, "fire", "fighting"));
        b.ability = "speedboost";
        b.hp = b.maxHp * hp;
        b.boosts[MoveDex.ATK] = atk;
        b.boosts[MoveDex.SPE] = spe;
        MoveInfo fb = guess(mv("flareblitz", "fire", Category.PHYSICAL, 120));
        b.moves = new ArrayList<>(List.of(mv("swordsdance", "normal", Category.STATUS, 0, "self"), guess(mv("closecombat", "fighting", Category.PHYSICAL, 120)),
            fb, guess(mv("stoneedge", "rock", Category.PHYSICAL, 100)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        b.moves.get(3).accuracy = 0.8;
        b.turnsActive = 1;
        return b;
    }

    static List<Battler> bench(Battler... xs) {
        return new ArrayList<>(List.of(xs));
    }

    /** Turn 8: Scarf Garchomp 94% (-1 Spe from Sticky Web) vs Mega Blaziken 77% at +2 Atk +1 Spe; web and rocks on our side. */
    static Planner.Plan mainTickTurn8() {
        Battler chomp = FlintSinglesReplay.garchompScarf();
        chomp.moves = new ArrayList<>(List.of(mv("ironhead", "steel", Category.PHYSICAL, 80), mv("stompingtantrum", "ground", Category.PHYSICAL, 75),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75)));
        chomp.hp = chomp.maxHp * 0.94;
        chomp.boosts[MoveDex.SPE] = -1;
        chomp.turnsActive = 1;
        Battler venu = mon("Venusaur", true, 100, new int[] {301, 180, 203, 328, 236, 259}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.item = "lifeorb";
        venu.hp = venu.maxHp * 0.9;
        MoveInfo sp = mv("sleeppowder", "grass", Category.STATUS, 0, "normal");
        sp.accuracy = 0.75;
        venu.moves = new ArrayList<>(List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("sludgebomb", "poison", Category.SPECIAL, 90), sp,
            mv("gigadrain", "grass", Category.SPECIAL, 75)));
        Battler weav = FlintSinglesReplay.weavile();
        weav.hp = weav.maxHp * 0.75;
        List<Battler> mine = bench(chomp, venu, weav, FlintSinglesReplay.flygon());
        BattleState st = EngineScenarios.state(false, mine, new ArrayList<>(List.of(blaziken(0.77, 2, 1))));
        st.oppKind = BattleState.OpponentKind.PLAYER;
        st.field.mine.stealthRock = true;
        st.field.mine.stickyWeb = true;
        st.oppUnseenReserves = 2;
        last = st;
        List<Integer> sw = List.of(1, 2, 3);
        Planner.SlotRequest r = EngineScenarios.request(chomp, sw);
        return new Planner(new Planner.Options()).decide(st, List.of(r));
    }

    public static void scenarios() {
        mainTickTurn8();
        BattleState st = last;
        double r = Evaluator.race(st.my(0), st.opp(0), st);
        String n1 = "mainTick Speed Boost Mega Blaziken +2 beats the Web-slowed Scarf Garchomp in the 1v1 projection (race " + String.format("%.2f", r) + ")";
        if (r < 0) EngineScenarios.pass(n1, "");
        else EngineScenarios.fail(n1, "");
        // A player's Dragonite facing our faster Scarf Weavile (Ice Shard / Triple Axel, 4x) with Blaziken known in the back.
        Battler weav = FlintSinglesReplay.weavile();
        Battler gard = estimated(mon("Dragonite", false, 100, new int[] {323, 366, 226, 236, 236, 259}, "dragon", "flying"));
        gard.possibleAbilities = List.of("innerfocus");
        gard.moves = new ArrayList<>(List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), guess(mv("earthquake", "ground", Category.PHYSICAL, 100))));
        gard.turnsActive = 2;
        Battler blaz = blaziken(1.0, 0, 0);
        BattleState st2 = EngineScenarios.state(false, new ArrayList<>(List.of(weav)), new ArrayList<>(List.of(gard, blaz)));
        st2.oppKind = BattleState.OpponentKind.PLAYER;
        double p = 0;
        for (OpponentModel.Weighted w : OpponentModel.policy(st2, false, 0, st2.oppKind))
            if (w.action().kind == Action.Kind.SWITCH) p += w.prob();
        String n2 = "mainTick a player's outmatched Dragonite may switch to Blaziken (" + Math.round(p * 100) + "%)";
        if (p > 0.2) EngineScenarios.pass(n2, "");
        else EngineScenarios.fail(n2, "");
    }

    public static void main(String[] args) {
        Planner.Plan p = mainTickTurn8();
        for (OpponentModel.Weighted w : OpponentModel.policy(last, false, 0, last.oppKind))
            System.out.println(" blaziken " + EngineScenarios.label(w.action()) + " " + String.format("%.3f", w.prob()));
        System.out.println(Tower31Replay.label(p) + " " + EngineScenarios.describe(p) + " " + p.notes);
    }
}
