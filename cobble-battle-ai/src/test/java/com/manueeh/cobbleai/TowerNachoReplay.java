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

/** Lost fight vs Campista Nacho (Pelipper + Kingdra rain, Gyarados Dragon Dance, Amoonguss). */
public final class TowerNachoReplay {

    static BattleState last;

    /** Turn 3: fresh Scarf Garchomp (-1 Atk) + Torkoal 50% vs Gyarados (just in) + Kingdra 56%; sun 3. */
    static Planner.Plan nachoTurn3() {
        Battler chomp = Tower31Replay.garchomp();
        chomp.boosts[MoveDex.ATK] = -1;
        chomp.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.5;
        tork.boosts[MoveDex.ATK] = -1;
        tork.turnsActive = 2;
        Battler venu = Tower31Replay.venusaur();
        Battler zard = Tower31Replay.charizard(false);
        Battler gyara = estimated(mon("Gyarados", false, 50, new int[] {170, 194, 99, 91, 120, 126}, "water", "flying"));
        gyara.ability = "intimidate";
        gyara.moves = new ArrayList<>(List.of(mv("icefang", "ice", Category.PHYSICAL, 65), mv("waterfall", "water", Category.PHYSICAL, 80),
            guess(mv("temperflare", "fire", Category.PHYSICAL, 75)), guess(mv("dragondance", "dragon", Category.STATUS, 0, "self")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        gyara.moves.get(0).accuracy = 0.95;
        Battler kingdra = estimated(mon("Kingdra", false, 50, new int[] {150, 161, 115, 223, 115, 150}, "water", "dragon"));
        kingdra.hp = kingdra.maxHp * 0.56;
        kingdra.moves = new ArrayList<>(List.of(mv("weatherball", "normal", Category.SPECIAL, 50), mv("hurricane", "flying", Category.SPECIAL, 110),
            guess(mv("dracometeor", "dragon", Category.SPECIAL, 130)), guess(mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes"))));
        kingdra.moves.get(1).accuracy = 0.7;
        kingdra.lastMove = "weatherball";
        kingdra.turnsActive = 2;
        BattleState st = EngineScenarios.state(true, List.of(chomp, tork, venu, zard), List.of(gyara, kingdra));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.field.theirs.tailwind = true;
        st.oppUnseenReserves = 1;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    public static void scenarios() {
        Planner.Plan p = nachoTurn3();
        Action chomp = p.actions.get(0);
        report("nacho T3 fresh Scarf Garchomp KOs Kingdra 56% instead of switching out",
            isMove(chomp, "dragonclaw") && chomp.targetSlot == 1, p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
