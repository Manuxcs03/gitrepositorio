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
import static com.manueeh.cobbleai.Tower31Replay.isMove;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Fight #16 vs Veterana Ruth: Venusaur fired Sleep Powder into a Grass-type Amoonguss (immune to powders). */
public final class TowerRuthReplay {

    static BattleState last;

    static Battler amoonguss(double hp) {
        Battler b = estimated(mon("Amoonguss", false, 50, new int[] {221, 116, 90, 150, 100, 37}, "grass", "poison"));
        b.hp = b.maxHp * hp;
        b.item = "sitrusberry";
        b.itemUnknown = false;
        MoveInfo spore = mv("spore", "grass", Category.STATUS, 0, "normal");
        b.moves = new ArrayList<>(List.of(spore, mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("sludgebomb", "poison", Category.SPECIAL, 90), mv("ragepowder", "bug", Category.STATUS, 0, "self")));
        b.protectUses = 1;
        b.turnsActive = 4;
        return b;
    }

    static Planner.Plan ruthTurn11() {
        Battler venu = Tower31Replay.venusaur();
        venu.hp = venu.maxHp * 0.53;
        venu.protectStreak = 1;
        venu.turnsActive = 5;
        Battler chomp = Tower31Replay.garchomp();
        chomp.hp = chomp.maxHp * 0.73;
        chomp.status = "slp";
        chomp.sleepTurns = 1;
        chomp.boosts[MoveDex.ATK] = -1;
        chomp.lockedMove = "dragonclaw";
        chomp.turnsActive = 3;
        Battler tork = TowerCira3Replay.newTorkoal();
        tork.hp = 0;
        Battler zard = Tower31Replay.charizard(false);
        zard.hp = 0;
        Battler rotom = estimated(mon("Rotom", false, 50, new int[] {125, 96, 127, 172, 127, 151}, "electric", "water"));
        rotom.hp = 0;
        BattleState st = EngineScenarios.state(true, List.of(venu, chomp, tork, zard), List.of(rotom, amoonguss(0.56)));
        st.oppUnseenReserves = 1;
        last = st;
        List<Planner.SlotRequest> reqs = Tower31Replay.reqs(st, List.of(), List.of());
        reqs.get(1).moves.clear();
        reqs.get(1).moves.add(chomp.moves.get(2));
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    public static void scenarios() {
        Planner.Plan p = ruthTurn11();
        report("ruth T11 no Sleep Powder into a Grass-type Amoonguss", !isMove(p.actions.get(0), "sleeppowder"), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
