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
import static com.manueeh.cobbleai.Tower31Replay.protects;
import static com.manueeh.cobbleai.Tower31Replay.report;

/**
 * Turn-1 openings of the log where the new version regressed (Simon, Noa, Cira): Torkoal protected on a fresh
 * sun instead of Eruption, and Venusaur went unprotected into Fake Out + Flare Blitz.
 */
public final class TowerOpeningReplay {

    static MoveInfo g(String id, String type, Category c, double pow) {
        return guess(mv(id, type, c, pow));
    }

    static MoveInfo gs(String id, String type, Category c, double pow, String target) {
        return guess(mv(id, type, c, pow, target));
    }

    static MoveInfo phantom(String type, Category c) {
        MoveInfo m = guess(mv("phantom" + type, type, c, 95));
        return m;
    }

    static Planner.Plan opening(Battler f0, Battler f1) {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.charizard(false), Tower31Replay.garchomp()),
            List.of(f0, f1));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    static BattleState last;

    /** Fight 1: Ninetales (Heat Wave/Protect known) + Venusaur (Sludge Bomb/Sleep Powder known). */
    static Planner.Plan simon() {
        Battler nine = estimated(mon("Ninetales", false, 50, new int[] {149, 107, 95, 146, 120, 152}, "fire"));
        nine.ability = "drought";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        nine.moves = new ArrayList<>(List.of(hw, mv("protect", "normal", Category.STATUS, 0, "self"),
            g("icebeam", "ice", Category.SPECIAL, 90), g("scorchingsands", "ground", Category.SPECIAL, 70),
            phantom("fire", Category.SPECIAL), gs("nastyplot", "dark", Category.STATUS, 0, "self"),
            gs("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly")));
        Battler fv = estimated(mon("Venusaur", false, 50, new int[] {155, 113, 103, 172, 120, 152}, "grass", "poison"));
        MoveInfo sp = mv("sleeppowder", "grass", Category.STATUS, 0, "normal");
        sp.accuracy = 0.75;
        fv.moves = new ArrayList<>(List.of(mv("sludgebomb", "poison", Category.SPECIAL, 90), sp,
            gs("petaldance", "grass", Category.SPECIAL, 120, "randomNormal"), g("earthpower", "ground", Category.SPECIAL, 90),
            gs("protect", "normal", Category.STATUS, 0, "self"), gs("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly")));
        return opening(nine, fv);
    }

    /** Fight 2: Tyranitar (Air Balloon) + Excadrill, all guessed. */
    static Planner.Plan noa() {
        Battler ttar = estimated(mon("Tyranitar", false, 50, new int[] {175, 204, 130, 126, 120, 92}, "rock", "dark"));
        ttar.item = "airballoon";
        ttar.itemUnknown = false;
        ttar.ability = "sandstream";
        MoveInfo rs1 = gs("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs1.accuracy = 0.9;
        ttar.moves = new ArrayList<>(List.of(g("icepunch", "ice", Category.PHYSICAL, 75), phantom("rock", Category.PHYSICAL),
            phantom("dark", Category.PHYSICAL), gs("dragondance", "dragon", Category.STATUS, 0, "self"),
            gs("protect", "normal", Category.STATUS, 0, "self"), gs("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), rs1));
        Battler exca = estimated(mon("Excadrill", false, 50, new int[] {185, 205, 80, 81, 85, 154}, "ground", "steel"));
        MoveInfo rs2 = gs("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs2.accuracy = 0.9;
        exca.moves = new ArrayList<>(List.of(gs("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            phantom("steel", Category.PHYSICAL), gs("swordsdance", "normal", Category.STATUS, 0, "self"),
            gs("protect", "normal", Category.STATUS, 0, "self"), gs("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), rs2));
        return opening(ttar, exca);
    }

    /** Fight 3: Arcanine + Hitmontop (Fake Out guessed), both Intimidate: our side at -2 Atk. */
    static Planner.Plan cira() {
        Battler arc = estimated(mon("Arcanine", false, 50, new int[] {165, 178, 100, 131, 100, 161}, "fire"));
        MoveInfo rs = gs("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        arc.moves = new ArrayList<>(List.of(g("headsmash", "rock", Category.PHYSICAL, 150), g("ragingfury", "fire", Category.PHYSICAL, 120),
            g("outrage", "dragon", Category.PHYSICAL, 120), gs("protect", "normal", Category.STATUS, 0, "self"),
            gs("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), rs));
        arc.moves.get(0).accuracy = 0.8;
        Battler top = estimated(mon("Hitmontop", false, 50, new int[] {125, 161, 115, 66, 130, 101}, "fighting"));
        MoveInfo rs2 = gs("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs2.accuracy = 0.9;
        top.moves = new ArrayList<>(List.of(g("stoneedge", "rock", Category.PHYSICAL, 100), g("icespinner", "ice", Category.PHYSICAL, 80),
            gs("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"), phantom("fighting", Category.PHYSICAL),
            gs("bulkup", "fighting", Category.STATUS, 0, "self"), gs("protect", "normal", Category.STATUS, 0, "self"),
            gs("wideguard", "rock", Category.STATUS, 0, "allySide"), g("fakeout", "normal", Category.PHYSICAL, 40), rs2));
        top.moves.get(0).accuracy = 0.8;
        Planner.Plan p = opening(arc, top);
        return p;
    }

    public static void scenarios() {
        for (String name : List.of("simon", "noa", "cira")) {
            Planner.Plan p = switch (name) {
                case "simon" -> simon();
                case "noa" -> noa();
                default -> cira();
            };
            Action venu = p.actions.get(0), tork = p.actions.get(1);
            if (!name.equals("cira")) {
                // Both foes threaten the slow Torkoal here; the original engine protects it too. Printed for review.
                System.out.println("  [info] opening " + name + ": " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
                continue;
            }
            // Cira (the lost fight): Fake Out + a fast fire attacker. The unprotected Sleep Powder gamble lost Venusaur.
            report("opening cira: Venusaur protects from Fake Out + fire", protects(venu), p);
            report("opening cira: Torkoal does not waste the turn with Protect (attack or switch)", !protects(tork), p);
        }
    }

    public static void main(String[] args) {
        scenarios();
    }
}
