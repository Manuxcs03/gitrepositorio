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
 * Lost Battle Tower fight #6 vs Musico Aaron (Chandelure Flash Fire + Air Balloon, Mienshao, Krookodile,
 * Whimsicott) and the Yawn turn of fight #5 (Slowking).
 */
public final class TowerAaronReplay {

    static Battler chandelure(double hp, boolean flashFireOn) {
        Battler b = estimated(mon("Chandelure", false, 50, new int[] {135, 86, 110, 206, 110, 145}, "ghost", "fire"));
        b.hp = b.maxHp * hp;
        b.ability = "flashfire";
        b.flashFire = flashFireOn;
        b.moves = new ArrayList<>(List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"),
            mv("shadowball", "ghost", Category.SPECIAL, 80), guess(mv("overheat", "fire", Category.SPECIAL, 130)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), mv("trickroom", "psychic", Category.STATUS, 0, "all")));
        b.moves.get(0).accuracy = 0.9;
        b.moves.get(2).accuracy = 0.9;
        return b;
    }

    static Battler krookodile(double hp) {
        Battler b = estimated(mon("Krookodile", false, 50, new int[] {170, 185, 100, 96, 90, 158}, "ground", "dark"));
        b.hp = b.maxHp * hp;
        b.ability = "moxie";
        b.boosts[MoveDex.ATK] = 1;
        MoveInfo rs = guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"));
        rs.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(mv("crunch", "dark", Category.PHYSICAL, 80), rs,
            guess(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        return b;
    }

    static Battler whimsicott() {
        Battler b = estimated(mon("Whimsicott", false, 50, new int[] {135, 98, 105, 141, 95, 184}, "grass", "fairy"));
        b.ability = "prankster";
        b.moves = new ArrayList<>(List.of(mv("taunt", "dark", Category.STATUS, 0, "normal"),
            guess(mv("moonblast", "fairy", Category.SPECIAL, 95)), guess(mv("energyball", "grass", Category.SPECIAL, 90)),
            guess(mv("tailwind", "flying", Category.STATUS, 0, "self"))));
        return b;
    }

    /** Turn 4: fresh Charizard (can Mega) + Venusaur vs Krookodile 88% +1 and Flash-Fire Chandelure 46%. */
    static Planner.Plan aaronTurn4() {
        Battler venu = Tower31Replay.venusaur();
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = 0;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = 0;
        BattleState st = EngineScenarios.state(true, List.of(venu, zard, chomp, tork),
            List.of(krookodile(0.88), chandelure(0.46, true)));
        st.field.weather = "sun";
        st.field.weatherTurns = 2;
        st.oppUnseenReserves = 2;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(), List.of()));
    }

    /** Turn 5: Mega Charizard alone vs Whimsicott 100% and Chandelure 6%, last turn of sun. */
    static Planner.Plan aaronTurn5() {
        Battler zard = Tower31Replay.charizard(true);
        zard.turnsActive = 1;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = 0;
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = 0;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = 0;
        BattleState st = EngineScenarios.state(true, List.of(zard, venu, chomp, tork),
            List.of(whimsicott(), chandelure(0.06, true)));
        st.myActive = new int[] {0, -1};
        st.field.weather = "sun";
        st.field.weatherTurns = 1;
        st.oppUnseenReserves = 1;
        Planner.SlotRequest r = EngineScenarios.request(zard, List.of());
        r.slot = 0;
        return new Planner(new Planner.Options()).decide(st, List.of(r));
    }

    /** Fight #5 turn 6: Garchomp was Yawned (drowsy) next to Torkoal; Slowking in front. */
    static Planner.Plan yawnTurn() {
        Battler chomp = Tower31Replay.garchomp();
        chomp.drowsy = true;
        chomp.turnsActive = 3;
        chomp.lockedMove = "stompingtantrum";
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.7;
        tork.turnsActive = 5;
        Battler venu = Tower31Replay.venusaur();
        Battler zard = Tower31Replay.charizard(false);
        Battler slowking = estimated(mon("Slowking", false, 50, new int[] {170, 95, 100, 120, 130, 50}, "water", "psychic"));
        slowking.item = "leftovers";
        slowking.itemUnknown = false;
        slowking.moves = new ArrayList<>(List.of(mv("yawn", "normal", Category.STATUS, 0, "normal"),
            mv("psychic", "psychic", Category.SPECIAL, 90), guess(mv("scald", "water", Category.SPECIAL, 80))));
        BattleState st = EngineScenarios.state(true, List.of(chomp, tork, venu, zard), List.of(slowking));
        st.oppActive = new int[] {0, -1};
        st.oppUnseenReserves = 0;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        reqs.get(0).moves.clear();
        reqs.get(0).moves.add(chomp.moves.get(1));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    /** Fight #3 turn 1: Ninetales (Drought) + Venusaur; only guessed moves. Torkoal walls both. */
    static Planner.Plan ninetalesTurn1() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        Battler nine = estimated(mon("Ninetales", false, 50, new int[] {149, 107, 95, 146, 120, 167}, "fire"));
        nine.ability = "drought";
        nine.moves = new ArrayList<>(List.of(guess(mv("icebeam", "ice", Category.SPECIAL, 90)),
            guess(mv("overheat", "fire", Category.SPECIAL, 130)), guess(mv("scorchingsands", "ground", Category.SPECIAL, 70)),
            guess(mv("nastyplot", "dark", Category.STATUS, 0, "self")), guess(mv("protect", "normal", Category.STATUS, 0, "self")),
            guess(mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"))));
        nine.moves.get(1).accuracy = 0.9;
        Battler fv = estimated(mon("Venusaur", false, 50, new int[] {155, 113, 103, 167, 120, 145}, "grass", "poison"));
        MoveInfo sp = guess(mv("sleeppowder", "grass", Category.STATUS, 0, "normal"));
        sp.accuracy = 0.75;
        fv.moves = new ArrayList<>(List.of(guess(mv("sludgebomb", "poison", Category.SPECIAL, 90)),
            guess(mv("petaldance", "grass", Category.SPECIAL, 120, "randomNormal")), guess(mv("earthpower", "ground", Category.SPECIAL, 90)),
            sp, guess(mv("protect", "normal", Category.STATUS, 0, "self")),
            guess(mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"))));
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, zard, chomp), List.of(nine, fv));
        last = st;
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    static BattleState last;

    // ------------------------------------------------------------------ fight #18 (2nd run) vs Atleta Bruna

    static Battler megaMawile() {
        Battler b = estimated(mon("Mawile", false, 50, new int[] {147, 258, 145, 86, 115, 81}, "steel", "fairy"));
        b.ability = "hugepower";
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(rs, guess(mv("icepunch", "ice", Category.PHYSICAL, 75)),
            guess(mv("psychicfangs", "psychic", Category.PHYSICAL, 85)), guess(mv("playrough", "fairy", Category.PHYSICAL, 90)),
            guess(mv("ironhead", "steel", Category.PHYSICAL, 80)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        b.moves.get(3).accuracy = 0.9;
        b.lastMove = "rockslide";
        b.turnsActive = 1;
        return b;
    }

    static Battler dusclops(double hp) {
        Battler b = estimated(mon("Dusclops", false, 50, new int[] {100, 134, 150, 91, 150, 56}, "ghost"));
        b.hp = b.maxHp * hp;
        b.item = "eviolite";
        b.moves = new ArrayList<>(List.of(mv("nightshade", "ghost", Category.SPECIAL, 1),
            guess(mv("trickroom", "psychic", Category.STATUS, 0, "all")), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        b.turnsActive = 1;
        return b;
    }

    /** Turn 2: Torkoal 47% (slowest) next to Venusaur; Mawile keeps firing its revealed Rock Slide. */
    static Planner.Plan brunaTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.turnsActive = 1;
        venu.protectStreak = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.47;
        tork.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.charizard(false), Tower31Replay.garchomp()),
            List.of(megaMawile(), dusclops(1.0)));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 2;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Turn 3: Venusaur 12% (faster than Mawile) + fresh Scarf Garchomp vs Mega Mawile 100% + Dusclops 74%. */
    static Planner.Plan brunaTurn3() {
        Battler venu = Tower31Replay.venusaur();
        venu.hp = venu.maxHp * 0.12;
        venu.turnsActive = 2;
        Battler chomp = Tower31Replay.garchomp();
        Battler tork = Tower31Replay.torkoal();
        tork.hp = 0;
        Battler mawile = megaMawile();
        mawile.turnsActive = 2;
        BattleState st = EngineScenarios.state(true, List.of(venu, chomp, tork, Tower31Replay.charizard(false)),
            List.of(mawile, dusclops(0.74)));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 2;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(3), List.of(3)));
    }

    /** #15 Lorena turn 3: Torkoal at 3% (moves last): Heat Wave, not a 4% Eruption. */
    static Planner.Plan lowTorkoal() {
        Battler venu = Tower31Replay.venusaur();
        venu.hp = venu.maxHp * 0.9;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.03;
        tork.protectStreak = 1;
        Battler scrafty = Tower31Replay.scrafty(0.43);
        Battler gallade = Tower31Replay.gallade(1.0);
        BattleState st = EngineScenarios.state(true, List.of(venu, tork), List.of(scrafty, gallade));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 1;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(), List.of()));
    }

    /** Rough Skin recoil is real: a 10% Garchomp that Dragon Claws a Rough Skin Garchomp faints too. */
    static boolean roughSkinRecoil() {
        Battler mine = Tower31Replay.garchomp();
        mine.hp = mine.maxHp * 0.1;
        Battler foe = Tower31Replay.foeGarchomp();
        foe.ability = "roughskin";
        foe.hp = foe.maxHp * 0.3;
        BattleState st = EngineScenarios.state(false, List.of(mine), List.of(foe));
        var outs = new com.manueeh.cobbleai.engine.TurnSimulator(false).run(st, List.of(Action.move(true, 0, mine.moves.get(2), false, 0)));
        double alive = 0;
        for (var o : outs) alive += o.prob() * (o.state().my(0).alive() ? 1 : 0);
        return alive < 0.5;
    }

    public static void scenarios() {
        Planner.Plan p0 = ninetalesTurn1();
        report("tower3 T1 Torkoal stays in vs Ninetales + Venusaur (guessed ground moves only)",
            p0.actions.get(1).kind != Action.Kind.SWITCH, p0);

        // Turns 4 and 5 are judgement calls under uncertainty (speed tie with Chandelure, a guessed Overheat that
        // one-shots Charizard with Flash Fire + sun): printed for review, not enforced.
        Planner.Plan p = aaronTurn4();
        System.out.println("  [info] aaron T4: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
        p = aaronTurn5();
        System.out.println("  [info] aaron T5: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));

        p = brunaTurn2();
        Action tork2 = p.actions.get(1), venu2 = p.actions.get(0);
        report("bruna T2 Torkoal 47% is covered from the repeated Rock Slide (Protect, switch, or Venusaur sleeps Mawile first)",
            protects(tork2) || tork2.kind == Action.Kind.SWITCH || (isMove(venu2, "sleeppowder") && venu2.targetSlot == 0), p);
        // Turn 3: double attack KOs Mawile ~78% vs Sleep Powder 75%: a close call, printed for review.
        p = brunaTurn3();
        System.out.println("  [info] bruna T3: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));

        p = lowTorkoal();
        report("lorena T3 3% Torkoal uses Heat Wave, not a 4% Eruption", !isMove(p.actions.get(1), "eruption"), p);
        if (roughSkinRecoil()) EngineScenarios.pass("rough skin recoil KOs a 10% contact attacker", "ok");
        else EngineScenarios.fail("rough skin recoil KOs a 10% contact attacker", "attacker survived in the sim");

        p = yawnTurn();
        Action chomp = p.actions.get(0);
        // Staying to hit Slowking and switching out score within 0.01 here: printed for review.
        System.out.println("  [info] tower5 yawned Garchomp: " + Tower31Replay.label(p) + " " + EngineScenarios.describe(p));
    }

    public static void main(String[] args) {
        scenarios();
    }
}
