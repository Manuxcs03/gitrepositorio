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
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight #8 vs Vigilante Cira (Kaprus_ run): Arcanine with a revealed Will-O-Wisp + Hitmontop. */
public final class TowerCira4Replay {

    static BattleState last;

    /** Turn 2: Venusaur 100% (protected, -2 Atk) + Torkoal 90% vs Arcanine (Flare Blitz, Will-O-Wisp shown) + Hitmontop. */
    static Planner.Plan cira4Turn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.boosts[MoveDex.ATK] = -2;
        venu.protectStreak = 1;
        venu.lastMove = "protect";
        venu.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.9;
        tork.boosts[MoveDex.ATK] = -2;
        tork.turnsActive = 1;
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        Battler arc = estimated(mon("Arcanine", false, 50, new int[] {165, 178, 100, 131, 100, 161}, "fire"));
        arc.possibleAbilities = List.of("intimidate", "flashfire", "justified");
        arc.ability = "intimidate";
        MoveInfo wisp = mv("willowisp", "fire", Category.STATUS, 0);
        wisp.accuracy = 0.85;
        arc.moves = new ArrayList<>(List.of(mv("flareblitz", "fire", Category.PHYSICAL, 120), wisp,
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"))));
        arc.moves.get(3).accuracy = 0.9;
        arc.lastMove = "flareblitz";
        arc.turnsActive = 1;
        Battler top = estimated(mon("Hitmontop", false, 50, new int[] {125, 155, 115, 66, 130, 101}, "fighting"));
        MoveInfo fo = mv("fakeout", "normal", Category.PHYSICAL, 40);
        fo.priority = 3;
        top.moves = new ArrayList<>(List.of(fo, guess(mv("closecombat", "fighting", Category.PHYSICAL, 120)),
            guess(mv("wideguard", "rock", Category.STATUS, 0, "allySide")), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        top.lastMove = "fakeout";
        top.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, zard, chomp), List.of(arc, top));
        arc.lastTarget = venu.uuid;
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 2;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    /** A foe left on 1 HP (Focus Sash) with a known bench member is expected to retreat 40% of the time. */
    static double retreatChance() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        Battler whim = estimated(mon("Whimsicott", false, 50, new int[] {135, 98, 105, 141, 95, 184}, "grass", "fairy"));
        whim.hp = 1;
        whim.moves = new ArrayList<>(List.of(mv("moonblast", "fairy", Category.SPECIAL, 95), mv("tailwind", "flying", Category.STATUS, 0, "allySide")));
        whim.turnsActive = 1;
        Battler meta = estimated(mon("Metagross", false, 50, new int[] {155, 205, 150, 126, 110, 101}, "steel", "psychic"));
        meta.moves = new ArrayList<>(List.of(mv("zenheadbutt", "psychic", Category.PHYSICAL, 80)));
        meta.turnsActive = 1;
        Battler gar = Tower31Replay.foeGarchomp();
        BattleState st = EngineScenarios.state(true, List.of(venu, tork), List.of(whim, meta, gar));
        double p = 0;
        for (OpponentModel.Weighted w : OpponentModel.policy(st, false, 0, st.oppKind))
            if (w.action().kind == Action.Kind.SWITCH && w.action().switchTo == 2) p += w.prob();
        return p;
    }

    public static void scenarios() {
        double r = retreatChance();
        String name = "retreat: a 1-HP foe with a known bench member may switch out (" + Math.round(r * 100) + "%)";
        if (r > 0.3 && r < 0.5) EngineScenarios.pass(name, "");
        else EngineScenarios.fail(name, "");
        Planner.Plan p = cira4Turn2();
        boolean chompIn = false;
        for (Action a : p.actions) chompIn |= a.kind == Action.Kind.SWITCH && a.switchTo == 3;
        report("cira4 T2 physical Garchomp is not switched into a revealed Will-O-Wisp Arcanine", !chompIn, p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
