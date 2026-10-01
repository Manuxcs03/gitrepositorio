package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Advisor;
import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.engine.Readings;
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

/**
 * Log 21: lost #22 vs Experto Cirilo (Tornadus Tailwind, Latios, Metagross, Landorus) and #4 vs Vigilante Cira.
 * Cirilo T2: Venusaur out for a full Charizard in front of Metagross' Psychic Fangs; hit plus Solar Power left it
 * on 1%. The same turn, Eruption from a Torkoal hit first taught the calibrator that Latios and Metagross were
 * twice as bulky. Cira T2: a 75% Sleep Powder missed and Venusaur fell; the HUD gave no warning of the gamble.
 */
public final class TowerCiriloCira5Replay {
    private TowerCiriloCira5Replay() {}

    static BattleState last;

    static Planner.Plan ciriloTurn2() {
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 1;
        tork.lastMove = "eruption";
        Battler venu = Tower31Replay.venusaur();
        venu.turnsActive = 1;
        venu.lastMove = "protect";
        venu.protectStreak = 1;
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        chomp.moves = new ArrayList<>(List.of(mv("ironhead", "steel", Category.PHYSICAL, 80),
            mv("stompingtantrum", "ground", Category.PHYSICAL, 75), mv("dragonclaw", "dragon", Category.PHYSICAL, 80),
            chomp.moves.get(3)));
        Battler latios = estimated(mon("Latios", false, 50, new int[] {155, 121, 110, 174, 130, 178}, "dragon", "psychic"));
        latios.hp = latios.maxHp * 0.55;
        MoveInfo draco = mv("dracometeor", "dragon", Category.SPECIAL, 130);
        draco.accuracy = 0.9;
        MoveInfo iw = mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes");
        iw.accuracy = 0.95;
        latios.moves = new ArrayList<>(List.of(draco, iw, mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("psychic", "psychic", Category.SPECIAL, 90)));
        latios.turnsActive = 1;
        latios.lastMove = "psychic";
        latios.lastTarget = venu.uuid;
        Battler meta = estimated(mon("Metagross", false, 50, new int[] {155, 237, 150, 136, 110, 178}, "steel", "psychic"));
        MoveInfo bp = mv("bulletpunch", "steel", Category.PHYSICAL, 40);
        bp.priority = 1;
        meta.moves = new ArrayList<>(List.of(bp, mv("highhorsepower", "ground", Category.PHYSICAL, 95),
            mv("psychicfangs", "psychic", Category.PHYSICAL, 85), guess(mv("icepunch", "ice", Category.PHYSICAL, 75)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        meta.turnsActive = 0;
        BattleState st = EngineScenarios.state(true, List.of(tork, venu, zard, chomp), List.of(latios, meta));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.field.theirs.tailwind = true;
        st.field.theirs.tailwindTurns = 3;
        st.oppUnseenReserves = 1;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    static Planner.Plan ciraTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.boosts[0] = -2;
        venu.turnsActive = 1;
        venu.lastMove = "protect";
        venu.protectStreak = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.boosts[0] = -2;
        tork.hp = tork.maxHp * 0.9;
        tork.turnsActive = 1;
        Battler arc = estimated(mon("Arcanine", false, 50, new int[] {165, 141, 100, 167, 100, 161}, "fire"));
        arc.item = "sitrusberry";
        arc.itemUnknown = false;
        arc.moves = new ArrayList<>(List.of(mv("flareblitz", "fire", Category.PHYSICAL, 120),
            mv("protect", "normal", Category.STATUS, 0, "self"), mv("snarl", "dark", Category.SPECIAL, 55, "allAdjacentFoes"),
            mv("willowisp", "fire", Category.STATUS, 0)));
        arc.moves.get(3).accuracy = 0.85;
        arc.turnsActive = 1;
        arc.lastMove = "willowisp";
        Battler top = estimated(mon("Hitmontop", false, 50, new int[] {125, 151, 115, 66, 130, 101}, "fighting"));
        MoveInfo fo = mv("fakeout", "normal", Category.PHYSICAL, 40);
        fo.priority = 3;
        top.moves = new ArrayList<>(List.of(mv("closecombat", "fighting", Category.PHYSICAL, 120), fo,
            mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), mv("wideguard", "rock", Category.STATUS, 0, "allySide")));
        top.turnsActive = 1;
        top.lastMove = "fakeout";
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.garchomp(), Tower31Replay.charizard(false)),
            List.of(arc, top));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Log 22 #10 Talia T1: their Chlorophyll Venusaur (Sleep Powder) + Drought Charizard. Ours slept Charizard, missed. */
    static Planner.Plan taliaTurn1() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        Battler zard = estimated(mon("Charizard", false, 50, new int[] {153, 135, 98, 232, 105, 167}, "fire", "flying"));
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        zard.moves = new ArrayList<>(List.of(mv("airslash", "flying", Category.SPECIAL, 75), hw,
            mv("protect", "normal", Category.STATUS, 0, "self"), guess(mv("ancientpower", "rock", Category.SPECIAL, 60))));
        zard.possibleAbilities = List.of("drought");
        Battler fv = estimated(mon("Venusaur", false, 50, new int[] {155, 113, 103, 167, 120, 132}, "grass", "poison"));
        fv.ability = "chlorophyll";
        fv.item = "leftovers";
        fv.itemUnknown = false;
        MoveInfo sp = mv("sleeppowder", "grass", Category.STATUS, 0, "normal");
        sp.accuracy = 0.75;
        fv.moves = new ArrayList<>(List.of(mv("gigadrain", "grass", Category.SPECIAL, 75),
            mv("protect", "normal", Category.STATUS, 0, "self"), sp, mv("sludgebomb", "poison", Category.SPECIAL, 90)));
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.garchomp(), Tower31Replay.charizard(false)),
            List.of(zard, fv));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Log 23 #26 Cirilo T4: Venusaur fresh in, Torkoal 37%, foes Latios 37% (-2 SpA) and Metagross 22%, Tailwind 1 turn
     *  left. Venusaur Giga Drained and fell to Psychic Fangs first; a Protect saved it while Heat Wave KO'd Metagross. */
    static Planner.Plan cirilo26Turn4() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.37;
        tork.turnsActive = 3;
        tork.lastMove = "protect";
        tork.protectStreak = 1;
        Battler zard = Tower31Replay.charizard(false);
        zard.hp = 0;
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = 0;
        Battler latios = estimated(mon("Latios", false, 50, new int[] {155, 121, 110, 180, 130, 178}, "dragon", "psychic"));
        latios.hp = latios.maxHp * 0.37;
        latios.boosts[2] = -2;
        MoveInfo draco = mv("dracometeor", "dragon", Category.SPECIAL, 130);
        draco.accuracy = 0.9;
        MoveInfo iw = mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes");
        iw.accuracy = 0.95;
        latios.moves = new ArrayList<>(List.of(draco, iw, mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("psychic", "psychic", Category.SPECIAL, 90)));
        latios.turnsActive = 3;
        latios.lastMove = "dracometeor";
        latios.lastTarget = chomp.uuid;
        Battler meta = estimated(mon("Metagross", false, 50, new int[] {155, 216, 150, 136, 110, 178}, "steel", "psychic"));
        meta.hp = meta.maxHp * 0.22;
        MoveInfo bp = mv("bulletpunch", "steel", Category.PHYSICAL, 40);
        bp.priority = 1;
        meta.moves = new ArrayList<>(List.of(bp, mv("highhorsepower", "ground", Category.PHYSICAL, 95),
            mv("psychicfangs", "psychic", Category.PHYSICAL, 85), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        meta.turnsActive = 2;
        meta.lastMove = "highhorsepower";
        meta.lastTarget = tork.uuid;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, zard, chomp), List.of(latios, meta));
        st.field.weather = "sun";
        st.field.weatherTurns = 2;
        st.field.theirs.tailwind = true;
        st.field.theirs.tailwindTurns = 1;
        st.oppUnseenReserves = 1;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(), List.of()));
    }

    static boolean switchesIn(Planner.Plan p, int idx) {
        for (Action a : p.actions) if (a.kind == Action.Kind.SWITCH && a.switchTo == idx) return true;
        return false;
    }

    public static void scenarios() {
        // Both switch-ins die here if both foes fire into the incoming slot (what the model predicts), so which one
        // comes in is a judgement call; printed for review. What must hold: Solar Power's chip counts as danger.
        Planner.Plan p = ciriloTurn2();
        System.out.println("  [info] cirilo T2: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
        BattleState st = last;
        Battler zard = st.myTeam.get(2);
        double chipHp = Planner.turnHpForTest(st, zard);
        String detail = String.format(java.util.Locale.ROOT, "Charizard %.0f HP, %.0f after the end-of-turn chip", zard.hp, chipHp);
        if (chipHp < zard.hp - 15) EngineScenarios.pass("cirilo T2: Solar Power chip counts against a sun switch-in", detail);
        else EngineScenarios.fail("cirilo T2: Solar Power chip counts against a sun switch-in", detail);

        // Calibrator: Eruption from a Torkoal hit earlier in the turn says nothing about the target's bulk.
        Battler before = Tower31Replay.torkoal();
        Battler after = before.copy();
        after.hp = before.maxHp * 0.4;
        Battler tgt = mon("Latios", false, 50, new int[] {155, 121, 110, 174, 130, 178}, "dragon", "psychic");
        MoveInfo eruption = before.moves.get(2);
        boolean skipHit = !Readings.reliable(eruption, before, after, tgt, tgt.copy());
        boolean keepClean = Readings.reliable(eruption, before, before.copy(), tgt, tgt.copy());
        Battler snarled = before.copy();
        snarled.boosts[2] = -1;
        boolean skipSnarl = !Readings.reliable(before.moves.get(0), before, snarled, tgt, tgt.copy());
        Battler draco = mon("Latios", false, 50, new int[] {155, 121, 110, 174, 130, 178}, "dragon", "psychic");
        Battler dracoAfter = draco.copy();
        dracoAfter.boosts[2] = -2;
        MoveInfo dm = mv("dracometeor", "dragon", Category.SPECIAL, 130);
        boolean keepDraco = Readings.reliable(dm, draco, dracoAfter, before, before.copy());
        String d = "hitFirst=" + skipHit + " clean=" + keepClean + " snarl=" + skipSnarl + " draco=" + keepDraco;
        if (skipHit && keepClean && skipSnarl && keepDraco) EngineScenarios.pass("calibrator ignores readings changed mid-turn", d);
        else EngineScenarios.fail("calibrator ignores readings changed mid-turn", d);

        p = ciraTurn2();
        Advisor.Advice adv = Advisor.advise(last, p);
        boolean sleeps = false;
        for (Action a : p.actions) sleeps |= a.kind == Action.Kind.MOVE && "sleeppowder".equals(a.move.id);
        System.out.println("  [info] cira T2: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p) + " || " + adv);
        // If the line still gambles on the 75% Sleep Powder, the player must at least see what a miss costs.
        boolean warned = !sleeps || !adv.gambles.isEmpty();
        report("cira T2: a Sleep Powder whose miss loses Venusaur is not played silently", warned, p);
        talia();
        Planner.Plan c4 = cirilo26Turn4();
        System.out.println("  [info] cirilo26 T4: " + Tower31Replay.label(c4) + " " + EngineScenarios.describe(c4) + TowerRuben3Replay.policy(last));
    }

    static void talia() {
        Planner.Plan p = taliaTurn1();
        Advisor.Advice adv = Advisor.advise(last, p);
        boolean sleeps = false;
        for (Action a : p.actions) sleeps |= a.kind == Action.Kind.MOVE && "sleeppowder".equals(a.move.id);
        System.out.println("  [info] talia22 T1: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p) + " || " + adv);
        report("talia22 T1: a Sleep Powder whose miss loses Venusaur is not played silently", !sleeps || !adv.gambles.isEmpty(), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
