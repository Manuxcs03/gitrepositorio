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
import static com.manueeh.cobbleai.Tower31Replay.protects;
import static com.manueeh.cobbleai.Tower31Replay.report;

/** Lost fight vs Mecanica Berta: Musharna (Helping Hand, Trick Room) + burned Guts Conkeldurr. */
public final class TowerBertaReplay {

    static BattleState last;

    /** Turn 2: Torkoal 49% (slowest) was Drain Punched last turn; Conkeldurr burned (Guts), Musharna Helping Hand. */
    static Planner.Plan bertaTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.protectStreak = 1;
        venu.turnsActive = 1;
        Battler tork = TowerCira3Replay.newTorkoal();
        tork.hp = tork.maxHp * 0.49;
        tork.turnsActive = 1;
        Battler mush = estimated(mon("Musharna", false, 50, new int[] {191, 86, 105, 150, 115, 37}, "psychic"));
        mush.hp = mush.maxHp * 0.70;
        mush.moves = new ArrayList<>(List.of(mv("trickroom", "psychic", Category.STATUS, 0, "all"),
            mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), mv("psychic", "psychic", Category.SPECIAL, 90),
            guess(mv("yawn", "normal", Category.STATUS, 0, "normal"))));
        mush.lastMove = "helpinghand";
        mush.turnsActive = 1;
        Battler conk = estimated(mon("Conkeldurr", false, 50, new int[] {180, 225, 115, 86, 85, 76}, "fighting"));
        conk.hp = conk.maxHp * 0.47;
        conk.status = "brn";
        conk.ability = "guts";
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        MoveInfo mp = mv("machpunch", "fighting", Category.PHYSICAL, 40);
        mp.priority = 1;
        conk.moves = new ArrayList<>(List.of(mp, rs, mv("drainpunch", "fighting", Category.PHYSICAL, 75),
            guess(mv("icepunch", "ice", Category.PHYSICAL, 75)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        conk.lastMove = "drainpunch";
        conk.lastTarget = tork.uuid;
        conk.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.garchomp(), Tower31Replay.charizard(false)),
            List.of(mush, conk));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 2;
        last = st;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    public static void scenarios() {
        Planner.Plan p = bertaTurn2();
        Action tork = p.actions.get(1);
        report("berta T2 Torkoal 49% (slowest, Drain Punched last turn) is covered: Protect or switch",
            protects(tork) || tork.kind == Action.Kind.SWITCH, p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
