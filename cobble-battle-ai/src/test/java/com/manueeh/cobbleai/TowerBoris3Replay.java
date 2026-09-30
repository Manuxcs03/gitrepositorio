package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;

import java.util.ArrayList;
import java.util.List;

import static com.manueeh.cobbleai.EngineScenarios.mon;
import static com.manueeh.cobbleai.EngineScenarios.mv;
import static com.manueeh.cobbleai.Tower31Replay.estimated;
import static com.manueeh.cobbleai.Tower31Replay.guess;
import static com.manueeh.cobbleai.Tower31Replay.isMove;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight #11 vs Estratega Boris (log 2026-09-30-7): remembered Tailwind Whimsicott, ability not shown yet. */
public final class TowerBoris3Replay {

    static Planner.Plan boris3Turn1() {
        Battler venu = Tower31Replay.venusaur();
        Battler tork = Tower31Replay.torkoal();
        Battler zard = Tower31Replay.charizard(false);
        Battler chomp = Tower31Replay.garchomp();
        Battler whim = estimated(mon("Whimsicott", false, 50, new int[] {135, 119, 105, 129, 95, 184}, "grass", "fairy"));
        whim.possibleAbilities = List.of("prankster", "infiltrator", "chlorophyll");
        whim.moves = new ArrayList<>(List.of(mv("tailwind", "flying", Category.STATUS, 0, "allySide"),
            guess(mv("moonblast", "fairy", Category.SPECIAL, 95)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        Battler gard = estimated(mon("Gardevoir", false, 50, new int[] {143, 116, 85, 199, 135, 167}, "psychic", "fairy"));
        gard.possibleAbilities = List.of("synchronize", "trace", "telepathy");
        gard.moves = new ArrayList<>(List.of(mv("psychic", "psychic", Category.SPECIAL, 90),
            mv("hypervoice", "normal", Category.SPECIAL, 90, "allAdjacentFoes"), guess(mv("thunderbolt", "electric", Category.SPECIAL, 90)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, zard, chomp), List.of(whim, gard));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        Planner.Plan p = boris3Turn1();
        report("boris3 T1 Venusaur is not left attacking into a remembered Prankster Tailwind + Psychic",
            !isMove(p.actions.get(0), "sludgebomb") && !isMove(p.actions.get(0), "gigadrain") && !isMove(p.actions.get(0), "sleeppowder"), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
