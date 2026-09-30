package com.manueeh.cobbleai;

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
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost boss fight #30 vs Centurion Aureo: Tailwind Crobat + Parental Bond Kangaskhan, then Kyogre + Gengar. */
public final class TowerAureoReplay {

    static BattleState last;

    /** Turn 2: Venusaur 100% (protected) + Torkoal 81% under the foes' Tailwind, sun 4 turns. */
    static Planner.Plan aureoTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.protectStreak = 1;
        venu.lastMove = "protect";
        venu.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.81;
        tork.turnsActive = 1;
        Battler chomp = Tower31Replay.garchomp();
        Battler zard = Tower31Replay.charizard(false);
        Battler kang = estimated(mon("Kangaskhan", false, 50, new int[] {181, 204, 120, 91, 120, 167}, "normal"));
        kang.ability = "parentalbond";
        MoveInfo fo = mv("fakeout", "normal", Category.PHYSICAL, 40);
        fo.priority = 3;
        kang.moves = new ArrayList<>(List.of(fo, guess(mv("doubleedge", "normal", Category.PHYSICAL, 120)),
            guess(mv("icepunch", "ice", Category.PHYSICAL, 75)), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        kang.moves.get(3).accuracy = 0.9;
        kang.lastMove = "fakeout";
        kang.turnsActive = 1;
        Battler crobat = estimated(mon("Crobat", false, 50, new int[] {160, 156, 100, 101, 100, 200}, "poison", "flying"));
        crobat.moves = new ArrayList<>(List.of(mv("tailwind", "flying", Category.STATUS, 0, "allySide"),
            guess(mv("bravebird", "flying", Category.PHYSICAL, 120)), guess(mv("superfang", "normal", Category.PHYSICAL, 1)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        crobat.lastMove = "tailwind";
        crobat.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, chomp, zard), List.of(kang, crobat));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.field.side(false).tailwind = true;
        st.oppUnseenReserves = 2;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    /** Turn 1: Venusaur + Torkoal (sun) vs Kangaskhan (Fake Out) + Crobat, nothing revealed yet. */
    static Planner.Plan aureoTurn1() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        Battler chomp = Tower31Replay.garchomp();
        Battler zard = Tower31Replay.charizard(false);
        Battler kang = estimated(mon("Kangaskhan", false, 50, new int[] {181, 194, 120, 91, 120, 167}, "normal"));
        kang.possibleAbilities = List.of("earlybird", "scrappy", "innerfocus");
        MoveInfo fo = guess(mv("fakeout", "normal", Category.PHYSICAL, 40));
        fo.priority = 3;
        kang.moves = new ArrayList<>(List.of(guess(mv("doubleedge", "normal", Category.PHYSICAL, 120)),
            guess(mv("icepunch", "ice", Category.PHYSICAL, 75)), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes")),
            guess(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent")), guess(mv("protect", "normal", Category.STATUS, 0, "self")), fo));
        kang.moves.get(2).accuracy = 0.9;
        Battler crobat = estimated(mon("Crobat", false, 50, new int[] {160, 156, 100, 101, 100, 200}, "poison", "flying"));
        crobat.possibleAbilities = List.of("innerfocus", "infiltrator");
        crobat.moves = new ArrayList<>(List.of(guess(mv("bravebird", "flying", Category.PHYSICAL, 120)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), guess(mv("tailwind", "flying", Category.STATUS, 0, "allySide"))));
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, chomp, zard), List.of(kang, crobat));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        Planner.Plan p1 = aureoTurn1();
        System.out.println("  [info] aureo T1 chose " + Tower31Replay.label(p1) + " " + p1.ranking.subList(0, Math.min(8, p1.ranking.size())).stream().map(x -> EngineScenarios.label(x.actions.get(0)) + " " + EngineScenarios.label(x.actions.get(1)) + " " + String.format("%.3f", x.value)).toList());
        Planner.Plan p = aureoTurn2();
        boolean chompIn = false;
        for (Action a : p.actions) chompIn |= a.kind == Action.Kind.SWITCH && a.switchTo == 2;
        // Both foes aim at Venusaur's slot and Protect was just used: someone in that slot falls either way, and
        // giving up Garchomp keeps the Venusaur the back line (Kyogre) needs. A trade, not a blunder.
        System.out.println("  [info] aureo T2 chose " + Tower31Replay.label(p) + (chompIn ? " (sacrifices Garchomp for Venusaur)" : ""));
    }

    public static void main(String[] args) {
        scenarios();
    }
}
