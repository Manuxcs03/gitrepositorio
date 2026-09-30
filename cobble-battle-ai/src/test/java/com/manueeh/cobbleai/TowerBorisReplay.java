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
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost Battle Tower fight #14 vs Estratega Boris (Gardevoir + Whimsicott Tailwind, Garchomp, Metagross). */
public final class TowerBorisReplay {

    static Battler gardevoir(double hp) {
        Battler b = estimated(mon("Gardevoir", false, 50, new int[] {143, 116, 85, 238, 135, 167}, "psychic", "fairy"));
        b.hp = b.maxHp * hp;
        b.moves = new ArrayList<>(List.of(mv("psychic", "psychic", Category.SPECIAL, 90),
            guess(mv("thunderbolt", "electric", Category.SPECIAL, 90)), guess(mv("moonblast", "fairy", Category.SPECIAL, 95)),
            guess(mv("calmmind", "psychic", Category.STATUS, 0, "self")), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        b.turnsActive = 1;
        return b;
    }

    static Battler foeGarchomp(double hp) {
        Battler b = Tower31Replay.foeGarchomp();
        b.hp = b.maxHp * hp;
        b.moves.add(0, mv("stompingtantrum", "ground", Category.PHYSICAL, 75));
        return b;
    }

    /** Turn 2: Tailwind up for them; Gardevoir 5% outspeeds all; Venusaur protected last turn. */
    static Planner.Plan borisTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.protectStreak = 1;
        venu.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.turnsActive = 1;
        Battler gard = gardevoir(0.05);
        gard.lastMove = "psychic";
        gard.lastTarget = venu.uuid;
        Battler gar = foeGarchomp(1.0);
        gar.moves.remove(0);
        gar.turnsActive = 0;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.charizard(false), Tower31Replay.garchomp()),
            List.of(gar, gard));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.field.theirs.tailwind = true;
        st.oppUnseenReserves = 1;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Turn 5: Scarf Garchomp locked into Stomping Tantrum (faster than Gardevoir 5%), Mega Charizard 88% (slower). */
    static Planner.Plan borisTurn5() {
        Battler zard = Tower31Replay.charizard(true);
        zard.hp = zard.maxHp * 0.88;
        zard.protectStreak = 1;
        zard.turnsActive = 2;
        Battler chomp = Tower31Replay.garchomp();
        chomp.lockedMove = "stompingtantrum";
        chomp.turnsActive = 1;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = 0;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = 0;
        Battler gar = foeGarchomp(0.49);
        gar.moves.get(1).revealed = true;
        Battler gard = gardevoir(0.05);
        gard.moves.add(1, guess(mv("hypervoice", "normal", Category.SPECIAL, 90, "allAdjacentFoes")));
        BattleState st = EngineScenarios.state(true, List.of(zard, chomp, venu, tork), List.of(gar, gard));
        st.field.weather = "sun";
        st.field.weatherTurns = 1;
        st.oppUnseenReserves = 1;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(), List.of());
        reqs.get(1).moves.clear();
        reqs.get(1).moves.add(chomp.moves.get(1));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    static BattleState last;

    public static void scenarios() {
        Planner.Plan p = borisTurn2();
        Action venu = p.actions.get(0);
        report("boris T2 Venusaur (no Protect, outsped by Tailwind Gardevoir) is not left in to die",
            venu.kind == Action.Kind.SWITCH, p);
        p = borisTurn5();
        Action chomp = p.actions.get(1);
        report("boris T5 faster Scarf Garchomp takes the Gardevoir 5% KO itself", chomp.targetSlot == 1, p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
