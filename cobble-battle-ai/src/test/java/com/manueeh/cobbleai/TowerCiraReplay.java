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

/** Lost fight vs Vigilante Cira (2nd time): Hitmontop + Arcanine, then Garchomp + Togekiss. */
public final class TowerCiraReplay {

    static Battler arcanine(double hp) {
        Battler b = estimated(mon("Arcanine", false, 50, new int[] {165, 178, 100, 131, 100, 161}, "fire"));
        b.hp = b.maxHp * hp;
        b.ability = "intimidate";
        b.item = "sitrusberry";
        b.itemUnknown = false;
        MoveInfo fb = mv("flareblitz", "fire", Category.PHYSICAL, 120);
        MoveInfo rs = guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"));
        rs.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(fb, mv("protect", "normal", Category.STATUS, 0, "self"),
            guess(mv("headsmash", "rock", Category.PHYSICAL, 150)), guess(mv("outrage", "dragon", Category.PHYSICAL, 120)), rs));
        b.moves.get(2).accuracy = 0.8;
        return b;
    }

    static Battler hitmontop(double hp) {
        Battler b = estimated(mon("Hitmontop", false, 50, new int[] {125, 161, 115, 66, 130, 101}, "fighting"));
        b.hp = b.maxHp * hp;
        b.moves = new ArrayList<>(List.of(mv("fakeout", "normal", Category.PHYSICAL, 40),
            guess(mv("closecombat", "fighting", Category.PHYSICAL, 120)), guess(mv("stoneedge", "rock", Category.PHYSICAL, 100)),
            guess(mv("icespinner", "ice", Category.PHYSICAL, 80)), guess(mv("wideguard", "rock", Category.STATUS, 0, "allySide"))));
        b.moves.get(2).accuracy = 0.8;
        return b;
    }

    /** Turn 2: Arcanine Flare Blitzed into Venusaur's Protect; Venusaur cannot Protect again. */
    static Planner.Plan ciraTurn2() {
        Battler venu = Tower31Replay.venusaur();
        venu.boosts[MoveDex.ATK] = -2;
        venu.protectStreak = 1;
        venu.turnsActive = 1;
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.9;
        tork.boosts[MoveDex.ATK] = -2;
        tork.turnsActive = 1;
        Battler arc = arcanine(1.0);
        arc.lastMove = "flareblitz";
        arc.lastTarget = venu.uuid;
        arc.turnsActive = 1;
        Battler top = hitmontop(1.0);
        top.lastMove = "fakeout";
        top.lastTarget = tork.uuid;
        top.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, Tower31Replay.charizard(false), Tower31Replay.garchomp()),
            List.of(arc, top));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 2;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** Turn 4: fresh Scarf Garchomp + Torkoal 49% vs foe Garchomp (Rock Slide shown) + Togekiss; Charizard in the back. */
    static Planner.Plan ciraTurn4() {
        Battler chomp = Tower31Replay.garchomp();
        Battler tork = Tower31Replay.torkoal();
        tork.hp = tork.maxHp * 0.49;
        tork.boosts[MoveDex.ATK] = -2;
        tork.turnsActive = 3;
        Battler venu = Tower31Replay.venusaur();
        venu.hp = 0;
        Battler zard = Tower31Replay.charizard(false);
        Battler gar = Tower31Replay.foeGarchomp();
        for (MoveInfo m : gar.moves) if (m.id.equals("rockslide") || m.id.equals("earthquake")) m.revealed = true;
        Battler kiss = estimated(mon("Togekiss", false, 50, new int[] {160, 81, 115, 189, 135, 145}, "fairy", "flying"));
        MoveInfo as = mv("airslash", "flying", Category.SPECIAL, 75);
        as.accuracy = 0.95;
        kiss.moves = new ArrayList<>(List.of(as, guess(mv("dazzlinggleam", "fairy", Category.SPECIAL, 80, "allAdjacentFoes")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), guess(mv("tailwind", "flying", Category.STATUS, 0, "allySide"))));
        BattleState st = EngineScenarios.state(true, List.of(chomp, tork, venu, zard), List.of(gar, kiss));
        st.field.weather = "sun";
        st.field.weatherTurns = 2;
        st.oppUnseenReserves = 0;
        return new Planner(new Planner.Options()).decide(st, Tower31Replay.reqs(st, List.of(3), List.of(3)));
    }

    public static void scenarios() {
        Planner.Plan p = ciraTurn2();
        Action venu = p.actions.get(0);
        report("cira2 T2 Venusaur (no Protect) leaves before the repeated Flare Blitz", venu.kind == Action.Kind.SWITCH, p);
        p = ciraTurn4();
        Action chomp = p.actions.get(0);
        report("cira2 T4 fresh Scarf Garchomp is not swapped for Charizard into a shown Rock Slide",
            !(chomp.kind == Action.Kind.SWITCH && chomp.switchTo == 3), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
