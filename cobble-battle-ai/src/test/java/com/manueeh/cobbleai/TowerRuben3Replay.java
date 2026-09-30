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

/**
 * Log 17 losses: Nacho, Ezequiel and Ruben (twice). Each time a switch dropped a Pokemon in front of a foe
 * that had shown a move one-shotting it (Ice Beam / Icy Wind into Garchomp, Rock Slide into Charizard).
 */
public final class TowerRuben3Replay {

    static BattleState last;

    /** The False Swipe Garchomp that went to the Tower in this log. */
    static Battler swipeChomp() {
        Battler b = mon("Garchomp", true, 50, new int[] {183, 178, 115, 98, 105, 150}, "dragon", "ground");
        b.item = "choicescarf";
        b.ability = "roughskin";
        b.moves = new ArrayList<>(List.of(mv("sandstorm", "rock", Category.STATUS, 0, "all"),
            mv("falseswipe", "normal", Category.PHYSICAL, 40), mv("swordsdance", "normal", Category.STATUS, 0, "self"),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80)));
        return b;
    }

    static boolean switchesIn(Planner.Plan p, int teamIdx) {
        for (Action a : p.actions) if (a.kind == Action.Kind.SWITCH && a.switchTo == teamIdx) return true;
        return false;
    }

    static String policy(BattleState st) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 2; k++)
            for (OpponentModel.Weighted w : OpponentModel.policy(st, false, k, st.oppKind))
                if (w.prob() >= 0.05) sb.append(" | opp").append(k).append(' ').append(EngineScenarios.label(w.action())).append(String.format(" %.2f", w.prob()));
        return sb.toString();
    }

    /** Nacho T2: Kingdra (Ice Beam shown on Venusaur) under Tailwind; old engine brought Garchomp in, Ice Beam 4x. */
    static Planner.Plan nachoTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.turnsActive = 1;
        venu.lastMove = "protect";
        venu.protectStreak = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 1;
        tork.lastMove = "eruption";
        Battler chomp = swipeChomp();
        Battler zard = Tower31Replay.charizard(false);
        Battler peli = estimated(mon("Pelipper", false, 50, new int[] {135, 81, 120, 150, 90, 96}, "water", "flying"));
        peli.hp = peli.maxHp * 0.36;
        peli.moves = new ArrayList<>(List.of(mv("hurricane", "flying", Category.SPECIAL, 110), mv("tailwind", "flying", Category.STATUS, 0, "allySide"),
            mv("weatherball", "normal", Category.SPECIAL, 50), guess(mv("surf", "water", Category.SPECIAL, 90, "allAdjacent")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        peli.moves.get(0).accuracy = 0.7;
        peli.turnsActive = 1;
        peli.lastMove = "tailwind";
        Battler king = estimated(mon("Kingdra", false, 50, new int[] {150, 126, 115, 192, 115, 150}, "water", "dragon"));
        king.hp = king.maxHp * 0.72;
        king.moves = new ArrayList<>(List.of(mv("dracometeor", "dragon", Category.SPECIAL, 130), mv("hurricane", "flying", Category.SPECIAL, 110),
            mv("icebeam", "ice", Category.SPECIAL, 90), mv("weatherball", "normal", Category.SPECIAL, 50)));
        king.moves.get(0).accuracy = 0.9;
        king.moves.get(1).accuracy = 0.7;
        king.turnsActive = 1;
        king.lastMove = "icebeam";
        king.lastTarget = venu.uuid;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, chomp, zard), List.of(peli, king));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.field.theirs.tailwind = true;
        st.field.theirs.tailwindTurns = 3;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Ezequiel T2: Thundurus 1% (sash gone) + fresh Cresselia with Icy Wind; old engine swapped Venusaur for Garchomp. */
    static Planner.Plan ezequielTurn2() {
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 1;
        tork.lastMove = "eruption";
        Battler venu = Tower31Replay.venusaur();
        venu.turnsActive = 1;
        venu.lastMove = "protect";
        venu.protectStreak = 1;
        Battler chomp = Tower31Replay.garchomp();
        chomp.moves = new ArrayList<>(List.of(mv("ironhead", "steel", Category.PHYSICAL, 80), mv("stompingtantrum", "ground", Category.PHYSICAL, 75),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes")));
        Battler zard = Tower31Replay.charizard(false);
        Battler thund = estimated(mon("Thundurus", false, 50, new int[] {154, 167, 90, 177, 100, 264}, "electric", "flying"));
        thund.hp = 2;
        thund.item = "focussash";
        thund.itemUnknown = false;
        MoveInfo tw = mv("thunderwave", "electric", Category.STATUS, 0);
        tw.priority = 1;
        thund.moves = new ArrayList<>(List.of(tw, guess(mv("wildboltstorm", "electric", Category.SPECIAL, 100, "allAdjacentFoes")),
            guess(mv("psychic", "psychic", Category.SPECIAL, 90)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        thund.possibleAbilities = List.of("prankster");
        thund.turnsActive = 1;
        thund.lastMove = "thunderwave";
        Battler cress = estimated(mon("Cresselia", false, 50, new int[] {195, 101, 140, 109, 150, 150}, "psychic"));
        MoveInfo iw = mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes");
        iw.accuracy = 0.95;
        cress.moves = new ArrayList<>(List.of(iw, mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), guess(mv("powergem", "rock", Category.SPECIAL, 80)),
            guess(mv("calmmind", "psychic", Category.STATUS, 0, "self"))));
        cress.turnsActive = 0;
        BattleState st = EngineScenarios.state(true, List.of(tork, venu, chomp, zard), List.of(thund, cress));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 1;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Ruben (2nd) T3: their Garchomp 16% has Rock Slide; old engine swapped Torkoal for Charizard, Rock Slide 4x. */
    static Planner.Plan rubenTurn3() {
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.46;
        tork.turnsActive = 2;
        tork.lastMove = "protect";
        tork.protectStreak = 1;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = venu.maxHp * 0.46;
        venu.turnsActive = 2;
        venu.lastMove = "gigadrain";
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        chomp.moves = new ArrayList<>(List.of(mv("ironhead", "steel", Category.PHYSICAL, 80), mv("stompingtantrum", "ground", Category.PHYSICAL, 75),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes")));
        Battler whim = estimated(mon("Whimsicott", false, 50, new int[] {135, 98, 105, 107, 95, 264}, "grass", "fairy"));
        whim.hp = whim.maxHp * 0.77;
        whim.item = "focussash";
        whim.itemUnknown = false;
        whim.possibleAbilities = List.of("prankster");
        whim.moves = new ArrayList<>(List.of(mv("encore", "normal", Category.STATUS, 0), mv("moonblast", "fairy", Category.SPECIAL, 95),
            mv("protect", "normal", Category.STATUS, 0, "self"), mv("tailwind", "flying", Category.STATUS, 0, "allySide")));
        whim.turnsActive = 2;
        whim.lastMove = "protect";
        Battler gar = estimated(mon("Garchomp", false, 50, new int[] {183, 163, 115, 111, 105, 169}, "dragon", "ground"));
        gar.hp = gar.maxHp * 0.16;
        gar.item = "lumberry";
        gar.itemUnknown = false;
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        gar.moves = new ArrayList<>(List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            rs, mv("protect", "normal", Category.STATUS, 0, "self")));
        gar.turnsActive = 2;
        gar.lastMove = "earthquake";
        gar.lastMoveStreak = 2;
        BattleState st = EngineScenarios.state(true, List.of(tork, venu, zard, chomp), List.of(whim, gar));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Log 19 Nacho T1: Kingdra's first move in 20 logged fights: Hurricane 8, Weather Ball 5, Ice Beam 3 (all into
     *  Venusaur), Draco Meteor into Torkoal 4. Venusaur outspeeds it in sun: Sleep Powder or a Torkoal Protect beats
     *  Protect on Venusaur + Eruption (Draco Meteor one-shot Torkoal). */
    static Planner.Plan nachoTurn1() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        Battler chomp = Tower31Replay.garchomp();
        Battler zard = Tower31Replay.charizard(false);
        Battler peli = estimated(mon("Pelipper", false, 50, new int[] {135, 81, 120, 117, 90, 76}, "water", "flying"));
        peli.possibleAbilities = List.of("drizzle");
        MoveInfo hur = mv("hurricane", "flying", Category.SPECIAL, 110);
        hur.accuracy = 0.7;
        peli.moves = new ArrayList<>(List.of(hur, mv("tailwind", "flying", Category.STATUS, 0, "allySide"),
            mv("weatherball", "normal", Category.SPECIAL, 50), mv("wideguard", "rock", Category.STATUS, 0, "allySide")));
        Battler king = estimated(mon("Kingdra", false, 50, new int[] {150, 126, 115, 174, 115, 132}, "water", "dragon"));
        king.moves = new ArrayList<>(List.of(mv("dracometeor", "dragon", Category.SPECIAL, 130), mv("hurricane", "flying", Category.SPECIAL, 110),
            mv("icebeam", "ice", Category.SPECIAL, 90), mv("weatherball", "normal", Category.SPECIAL, 50)));
        king.moves.get(0).accuracy = 0.9;
        king.moves.get(1).accuracy = 0.7;
        BattleState st = EngineScenarios.state(true, List.of(tork, venu, chomp, zard), List.of(peli, king));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Log 20 Rosamunda T3: fresh Mewtwo + Terrakion under their Tailwind, nothing revealed yet. Old engine left the
     *  49% Garchomp in front of Mewtwo (Ice Beam 4x) and pulled the full-HP Torkoal. */
    static Planner.Plan rosamundaTurn3() {
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 2;
        tork.lastMove = "eruption";
        Battler chomp = Tower31Replay.garchomp();
        chomp.moves = new ArrayList<>(List.of(mv("ironhead", "steel", Category.PHYSICAL, 80), mv("stompingtantrum", "ground", Category.PHYSICAL, 75),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes")));
        chomp.hp = chomp.maxHp * 0.49;
        chomp.turnsActive = 1;
        Battler venu = Tower31Replay.venusaur();
        Battler zard = Tower31Replay.charizard(false);
        Battler mew = estimated(mon("Mewtwo", false, 50, new int[] {181, 141, 110, 226, 110, 200}, "psychic"));
        mew.possibleAbilities = List.of("pressure", "unnerve");
        MoveInfo bliz = mv("blizzard", "ice", Category.SPECIAL, 110, "allAdjacentFoes");
        bliz.accuracy = 0.7;
        mew.moves = new ArrayList<>(List.of(guess(mv("psystrike", "psychic", Category.SPECIAL, 100)), guess(bliz),
            guess(mv("powergem", "rock", Category.SPECIAL, 80)), guess(mv("earthpower", "ground", Category.SPECIAL, 90)),
            guess(mv("nastyplot", "dark", Category.STATUS, 0, "self")), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        mew.turnsActive = 0;
        Battler terra = estimated(mon("Terrakion", false, 50, new int[] {166, 199, 110, 103, 110, 176}, "rock", "fighting"));
        MoveInfo se = mv("stoneedge", "rock", Category.PHYSICAL, 100);
        se.accuracy = 0.8;
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        terra.moves = new ArrayList<>(List.of(guess(se), guess(mv("closecombat", "fighting", Category.PHYSICAL, 120)),
            guess(mv("zenheadbutt", "psychic", Category.PHYSICAL, 80)), guess(rs), guess(mv("swordsdance", "normal", Category.STATUS, 0, "self")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        terra.turnsActive = 0;
        BattleState st = EngineScenarios.state(true, List.of(tork, chomp, venu, zard), List.of(mew, terra));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.field.theirs.tailwind = true;
        st.field.theirs.tailwindTurns = 3;
        st.oppUnseenReserves = 0;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    static void check(String name, Planner.Plan p, int badSwitch) {
        String line = Tower31Replay.label(p) + " " + EngineScenarios.describe(p) + policy(last);
        if (switchesIn(p, badSwitch)) EngineScenarios.fail(name, line);
        else EngineScenarios.pass(name, line);
    }

    public static void scenarios() {
        check("nacho2 T2: no Garchomp into a shown Ice Beam under Tailwind", nachoTurn2(), 2);
        check("ezequiel T2: no Garchomp into a fresh Icy Wind Cresselia", ezequielTurn2(), 2);
        check("ruben3 T3: no Charizard into a Rock Slide Garchomp", rubenTurn3(), 2);
        Planner.Plan n1 = nachoTurn1();
        String l1 = Tower31Replay.label(n1) + " " + EngineScenarios.describe(n1) + policy(last);
        boolean exposed = Tower31Replay.isMove(n1.actions.get(0), "eruption") && Tower31Replay.protects(n1.actions.get(1));
        Planner.Plan r3 = rosamundaTurn3();
        String l3 = Tower31Replay.label(r3) + " " + EngineScenarios.describe(r3) + policy(last);
        boolean chompStays = r3.actions.get(1).kind != Action.Kind.SWITCH;
        boolean torkOut = r3.actions.get(0).kind == Action.Kind.SWITCH;
        System.out.println("  [info] rosamunda T3: " + l3);
        if (chompStays && torkOut) EngineScenarios.fail("rosamunda T3: not the full Torkoal out while the 49% Garchomp stays", l3);
        else EngineScenarios.pass("rosamunda T3: not the full Torkoal out while the 49% Garchomp stays", l3);
        if (exposed) EngineScenarios.fail("nacho3 T1: not Eruption + Protect Venusaur into a Draco Meteor Kingdra", l1);
        else EngineScenarios.pass("nacho3 T1: not Eruption + Protect Venusaur into a Draco Meteor Kingdra", l1);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
