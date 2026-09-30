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

/** Lost fight #28 vs Comandante Delia (Kaprus_ run, streak 27): Pelipper Wide Guard + Dragonite Hurricane. */
public final class TowerDelia2Replay {

    static BattleState last;
    static BattleState lastN;

    static Battler pelipper() {
        Battler b = estimated(mon("Pelipper", false, 50, new int[] {135, 81, 120, 161, 90, 96}, "water", "flying"));
        b.ability = "drizzle";
        b.moves = new ArrayList<>(List.of(mv("wideguard", "rock", Category.STATUS, 0, "allySide"),
            guess(mv("surf", "water", Category.SPECIAL, 90, "allAdjacent")), guess(mv("icebeam", "ice", Category.SPECIAL, 90)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), guess(mv("tailwind", "flying", Category.STATUS, 0, "allySide"))));
        b.wideGuardUses = 1;
        b.lastMove = "wideguard";
        b.lastMoveStreak = 1;
        b.turnsActive = 1;
        return b;
    }

    static Battler dragonite() {
        Battler b = estimated(mon("Dragonite", false, 50, new int[] {166, 155, 115, 216, 120, 167}, "dragon", "flying"));
        b.possibleAbilities = List.of("innerfocus", "multiscale");
        MoveInfo hur = mv("hurricane", "flying", Category.SPECIAL, 110);
        hur.accuracy = 0.7;
        MoveInfo draco = guess(mv("dracometeor", "dragon", Category.SPECIAL, 130));
        draco.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(hur, draco, guess(mv("protect", "normal", Category.STATUS, 0, "self")),
            guess(mv("tailwind", "flying", Category.STATUS, 0, "allySide"))));
        b.lastMove = "hurricane";
        b.turnsActive = 1;
        return b;
    }

    /** Turn 2: Venusaur 100% protected last turn and Dragonite's Hurricane went for it; sun 4 turns (Hurricane 50%). */
    static Planner.Plan delia2Turn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.protectStreak = 1;
        venu.lastMove = "protect";
        venu.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 1;
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        Battler drag = dragonite();
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, zard, chomp), List.of(pelipper(), drag));
        drag.lastTarget = venu.uuid;
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 2;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    /** #7 Simon turn 10 (same run): no sun, Scrafty 4% (Torkoal's Heat Wave finishes it) + Flash Fire Chandelure. */
    static Planner.Plan simonTurn10() {
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.6;
        tork.boosts[com.manueeh.cobbleai.data.MoveDex.ATK] = -1;
        tork.turnsActive = 5;
        Battler zard = Tower31Replay.charizard(true);
        zard.turnsActive = 3;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = 0;
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = 0;
        Battler scrafty = estimated(mon("Scrafty", false, 50, new int[] {140, 123, 135, 59, 135, 89}, "dark", "fighting"));
        scrafty.hp = scrafty.maxHp * 0.04;
        scrafty.moves = new ArrayList<>(List.of(mv("fakeout", "normal", Category.PHYSICAL, 40), mv("snarl", "dark", Category.SPECIAL, 55, "allAdjacentFoes"),
            mv("protect", "normal", Category.STATUS, 0, "self"), mv("drainpunch", "fighting", Category.PHYSICAL, 75)));
        scrafty.turnsActive = 5;
        scrafty.lastMove = "drainpunch";
        Battler chand = estimated(mon("Chandelure", false, 50, new int[] {135, 86, 110, 216, 110, 145}, "ghost", "fire"));
        chand.hp = 0;
        BattleState st = EngineScenarios.state(true, List.of(tork, zard, venu, chomp), List.of(scrafty, chand));
        st.oppUnseenReserves = 0;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(), List.of());
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    /** #11 Nagore turn 5 (same run): burned Garchomp 18% locked into Dragon Claw, Mega Charizard 88%, Whimsicott at 1%. */
    static Planner.Plan nagore2Turn5() {
        Battler zard = Tower31Replay.charizard(true);
        zard.hp = zard.maxHp * 0.88;
        zard.turnsActive = 1;
        zard.protectStreak = 1;
        zard.lastMove = "protect";
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = chomp.maxHp * 0.18;
        chomp.status = "brn";
        chomp.lockedMove = "dragonclaw";
        chomp.turnsActive = 1;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = venu.maxHp * 0.78;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = 0;
        Battler rotom = estimated(mon("Rotom", false, 50, new int[] {110, 96, 127, 172, 127, 151}, "electric", "ghost"));
        rotom.possibleAbilities = List.of("levitate");
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        MoveInfo wisp = mv("willowisp", "fire", Category.STATUS, 0);
        wisp.accuracy = 0.85;
        rotom.moves = new ArrayList<>(List.of(mv("thunderbolt", "electric", Category.SPECIAL, 90), hw, wisp,
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        rotom.lastMove = "willowisp";
        rotom.turnsActive = 2;
        Battler whim = estimated(mon("Whimsicott", false, 50, new int[] {135, 98, 105, 141, 95, 184}, "grass", "fairy"));
        whim.possibleAbilities = List.of("prankster", "infiltrator", "chlorophyll");
        whim.hp = whim.maxHp * 0.01;
        whim.moves = new ArrayList<>(List.of(mv("tailwind", "flying", Category.STATUS, 0, "allySide"),
            mv("moonblast", "fairy", Category.SPECIAL, 95), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        whim.turnsActive = 0;
        BattleState st = EngineScenarios.state(true, List.of(zard, chomp, venu, tork), List.of(rotom, whim));
        st.field.weather = "sun";
        st.field.weatherTurns = 1;
        st.oppUnseenReserves = 0;
        lastN = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2), List.of(2));
        reqs.get(1).moves.clear();
        reqs.get(1).moves.add(chomp.moves.get(2));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        Planner.Plan p = delia2Turn2();
        Action v = p.actions.get(0), t = p.actions.get(1);
        boolean venuIdle = v.kind == Action.Kind.MOVE && v.targetSlot == 0 && !v.move.id.equals("protect");
        report("delia2 T2 Venusaur does not attack Pelipper while the known Hurricane comes for it", !venuIdle, p);
        Planner.Plan ps = simonTurn10();
        report("simon T10 Charizard does not charge Solar Beam (no sun) at a foe Torkoal already finishes",
            !Tower31Replay.isMove(ps.actions.get(1), "solarbeam"), ps);
        Planner.Plan pn = nagore2Turn5();
        report("nagore2 T5 Mega Charizard stays and Heat Waves the 1% Whimsicott instead of switching out",
            Tower31Replay.isMove(pn.actions.get(0), "heatwave"), pn);
        report("delia2 T2 Torkoal does not throw a spread move into the repeated Wide Guard",
            !(t.kind == Action.Kind.MOVE && t.move.isSpread()), p);
    }

    public static void main(String[] args) {
        Planner.Plan p = delia2Turn2();
        for (int i = 0; i < 2; i++)
            for (OpponentModel.Weighted w : OpponentModel.policy(last, false, i, last.oppKind))
                System.out.println(" foe" + i + " " + EngineScenarios.label(w.action()) + " " + String.format("%.3f", w.prob()));
        System.out.println(Tower31Replay.label(p) + " " + EngineScenarios.describe(p) + " " + p.notes);
    }
}
