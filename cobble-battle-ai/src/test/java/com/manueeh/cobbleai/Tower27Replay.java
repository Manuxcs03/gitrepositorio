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

/** Scenarios rebuilt from the lost Battle Tower fight #27 vs Elite Adriano (Latios/Cresselia/Excadrill). */
public final class Tower27Replay {

    static Battler venusaur() {
        Battler b = EngineScenarios.mon("Venusaur", true, 50, new int[] {155, 91, 103, 167, 120, 132}, "grass", "poison");
        b.item = "lifeorb";
        b.ability = "chlorophyll";
        b.moves = new ArrayList<>(List.of(EngineScenarios.mv("protect", "normal", Category.STATUS, 0, "self"),
            EngineScenarios.mv("sludgebomb", "poison", Category.SPECIAL, 90),
            EngineScenarios.mv("sleeppowder", "grass", Category.STATUS, 0, "normal"),
            EngineScenarios.mv("gigadrain", "grass", Category.SPECIAL, 75)));
        b.moves.get(2).accuracy = 0.75;
        return b;
    }

    static Battler torkoal() {
        Battler b = EngineScenarios.mon("Torkoal", true, 50, new int[] {145, 89, 160, 147, 90, 37}, "fire");
        b.item = "choicespecs";
        b.ability = "drought";
        MoveInfo hw = EngineScenarios.mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(hw, EngineScenarios.mv("protect", "normal", Category.STATUS, 0, "self"),
            EngineScenarios.mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"),
            EngineScenarios.mv("earthpower", "ground", Category.SPECIAL, 90)));
        return b;
    }

    static Battler charizardMegaY() {
        Battler b = EngineScenarios.mon("Charizard", true, 50, new int[] {153, 111, 100, 232, 135, 152}, "fire", "flying");
        b.ability = "drought";
        MoveInfo hw = EngineScenarios.mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        MoveInfo fb = EngineScenarios.mv("focusblast", "fighting", Category.SPECIAL, 120);
        fb.accuracy = 0.7;
        b.moves = new ArrayList<>(List.of(EngineScenarios.mv("protect", "normal", Category.STATUS, 0, "self"),
            EngineScenarios.mv("solarbeam", "grass", Category.SPECIAL, 120), hw, fb));
        return b;
    }

    static Battler garchomp() {
        Battler b = EngineScenarios.mon("Garchomp", true, 50, new int[] {183, 182, 115, 90, 105, 168}, "dragon", "ground");
        b.item = "choicescarf";
        MoveInfo rs = EngineScenarios.mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(EngineScenarios.mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            EngineScenarios.mv("stompingtantrum", "ground", Category.PHYSICAL, 75),
            EngineScenarios.mv("dragonclaw", "dragon", Category.PHYSICAL, 80), rs));
        return b;
    }

    static Battler cresselia(double hp) {
        Battler b = EngineScenarios.mon("Cresselia", false, 50, new int[] {230, 101, 140, 139, 175, 101}, "psychic");
        b.statsExact = false;
        b.hp = b.maxHp * hp;
        b.protectStreak = 1;
        MoveInfo icy = EngineScenarios.mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes");
        icy.accuracy = 0.95;
        b.moves = new ArrayList<>(List.of(EngineScenarios.mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), icy,
            EngineScenarios.mv("protect", "normal", Category.STATUS, 0, "self"),
            revealedOff(EngineScenarios.mv("psychocut", "psychic", Category.PHYSICAL, 70)),
            revealedOff(EngineScenarios.mv("slash", "normal", Category.PHYSICAL, 70))));
        return b;
    }

    static MoveInfo revealedOff(MoveInfo m) {
        m.revealed = false;
        return m;
    }

    /** Latios exactly as the mod modelled it (only weak guessed moves) or with the real premium STAB threats. */
    static Battler latios(double hp, boolean premiumGuess) {
        Battler b = EngineScenarios.mon("Latios", false, 50, new int[] {150, 121, 100, 200, 135, 141}, "dragon", "psychic");
        b.statsExact = false;
        b.hp = b.maxHp * hp;
        b.itemUnknown = true;
        List<MoveInfo> mv = new ArrayList<>();
        mv.add(EngineScenarios.mv("psychic", "psychic", Category.SPECIAL, 90));
        if (premiumGuess) {
            mv.add(revealedOff(EngineScenarios.mv("dracometeor", "dragon", Category.SPECIAL, 130)));
            mv.get(1).accuracy = 0.9;
        } else {
            mv.add(revealedOff(EngineScenarios.mv("lusterpurge", "psychic", Category.SPECIAL, 70)));
            mv.add(revealedOff(EngineScenarios.mv("dragonpulse", "dragon", Category.SPECIAL, 85)));
        }
        b.moves = mv;
        return b;
    }

    static Planner.Plan turn4(boolean premium) {
        Battler venu = venusaur();
        venu.hp = venu.maxHp * 0.71;
        venu.boosts[MoveDex.ATK] = -1;
        venu.boosts[MoveDex.SPE] = -1;
        venu.protectStreak = 1;
        venu.turnsActive = 3;
        Battler zard = charizardMegaY();
        zard.hp = zard.maxHp * 0.63;
        zard.boosts[MoveDex.SPE] = -1;
        zard.protectStreak = 1;
        zard.turnsActive = 3;
        Battler tork = torkoal();
        Battler chomp = garchomp();
        Battler cress = cresselia(0.70);
        Battler lat = latios(1.0, premium);
        lat.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, zard, tork, chomp), List.of(cress, lat));
        st.field.weather = "sun";
        st.field.weatherTurns = 2;
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = EngineScenarios.request(st.myTeam.get(i), List.of(2, 3));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        return p;
    }

    static String label(Planner.Plan p) {
        StringBuilder sb = new StringBuilder();
        for (Action a : p.actions) sb.append(EngineScenarios.label(a)).append(' ');
        return sb.toString();
    }

    /** Turn 6: Torkoal (Eruption locked) + Garchomp (Choice Scarf) vs Cresselia 5% + Latios 5%. */
    static Planner.Plan turn6(boolean premium) {
        Battler tork = torkoal();
        tork.turnsActive = 1;
        tork.lockedMove = null;
        Battler chomp = garchomp();
        chomp.turnsActive = 0;
        Battler venu = venusaur();
        venu.hp = 0;
        Battler zard = charizardMegaY();
        zard.hp = 0;
        Battler cress = cresselia(0.05);
        Battler lat = latios(0.05, premium);
        lat.boosts[MoveDex.SPA] = -2;
        BattleState st = EngineScenarios.state(true, List.of(tork, chomp, venu, zard), List.of(cress, lat));
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = EngineScenarios.request(st.myTeam.get(i), List.of());
            r.slot = i;
            if (i == 0) {
                r.moves.clear();
                r.moves.add(tork.moves.get(2)); // locked into Eruption
            }
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        return p;
    }

    /** Turn 3: Venusaur 71% -1/-1, Charizard (mega this turn) 63% vs Cresselia 70% + fresh Latios. */
    static Planner.Plan turn3(boolean premium) {
        Battler venu = venusaur();
        venu.hp = venu.maxHp * 0.71;
        venu.boosts[MoveDex.ATK] = -1;
        venu.boosts[MoveDex.SPE] = -1;
        venu.turnsActive = 2;
        Battler zard = charizardMegaY();
        zard.hp = zard.maxHp * 0.63;
        zard.boosts[MoveDex.SPE] = -1;
        zard.turnsActive = 1;
        Battler cress = cresselia(0.70);
        cress.protectStreak = 0;
        cress.moves = new ArrayList<>(cress.moves);
        Battler lat = latios(1.0, premium);
        lat.moves = new ArrayList<>();
        lat.moves.add(revealedOff(EngineScenarios.mv("lusterpurge", "psychic", Category.SPECIAL, 70)));
        lat.moves.add(revealedOff(EngineScenarios.mv("dragonpulse", "dragon", Category.SPECIAL, 85)));
        if (premium) {
            MoveInfo dm = revealedOff(EngineScenarios.mv("dracometeor", "dragon", Category.SPECIAL, 130));
            dm.accuracy = 0.9;
            lat.moves.add(dm);
            lat.moves.add(revealedOff(EngineScenarios.mv("psychic", "psychic", Category.SPECIAL, 90)));
        }
        lat.turnsActive = 0;
        Battler tork = torkoal();
        Battler chomp = garchomp();
        BattleState st = EngineScenarios.state(true, List.of(venu, zard, tork, chomp), List.of(cress, lat));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = EngineScenarios.request(st.myTeam.get(i), List.of(2, 3));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        return p;
    }

    /** Turn 5: Torkoal just came in (100%), Charizard 63% -1spe, Latios 64%, Cresselia 70%. */
    static Planner.Plan turn5(boolean premium) {
        Battler tork = torkoal();
        tork.turnsActive = 0;
        Battler zard = charizardMegaY();
        zard.hp = zard.maxHp * 0.63;
        zard.boosts[MoveDex.SPE] = -1;
        zard.turnsActive = 2;
        Battler venu = venusaur();
        venu.hp = 0;
        Battler cress = cresselia(0.70);
        Battler lat = latios(0.64, premium);
        lat.moves = new ArrayList<>(lat.moves);
        lat.turnsActive = 2;
        Battler chomp = garchomp();
        BattleState st = EngineScenarios.state(true, List.of(tork, zard, chomp, venu), List.of(cress, lat));
        st.field.weather = "sun";
        st.field.weatherTurns = 1;
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = EngineScenarios.request(st.myTeam.get(i), List.of(2));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        return p;
    }

    static boolean protects(Action a) {
        return a.kind == Action.Kind.MOVE && MoveDex.PROTECT.contains(a.move.id);
    }

    /** Latios is a Dragon/Psychic special sweeper: its premium STAB (Draco Meteor) must be respected. */
    static void protectsAgainstPremiumStab() {
        Planner.Plan p = turn5(true);
        Action zard = p.actions.get(1);
        // Draco Meteor KOs either Torkoal or Charizard, so Latios' target is a coin flip. Attacking on the
        // last sun turn is then about as good as protecting: accept either as long as Protect stays close.
        double protectBest = Double.NEGATIVE_INFINITY;
        for (Planner.Scored sc : p.ranking) if (protects(sc.actions.get(1))) protectBest = Math.max(protectBest, sc.value);
        boolean close = p.value - protectBest < 0.1;
        if (protects(zard) || close) EngineScenarios.pass("tower27 T5 weakened Charizard protects vs Latios (or close)", label(p));
        else EngineScenarios.fail("tower27 T5 weakened Charizard protects vs Latios",
            label(p) + EngineScenarios.describe(p));
    }

    /** Both slots protecting wastes the turn: it must not be the pick when attacking is fine. */
    static void noDoubleProtectFromStrongPosition() {
        Planner.Plan p = turn3(true);
        if (protects(p.actions.get(0)) && protects(p.actions.get(1)))
            EngineScenarios.fail("tower27 T3 no double Protect", label(p) + EngineScenarios.describe(p));
        else EngineScenarios.pass("tower27 T3 no double Protect", label(p));
    }

    /** A 90% spread KO on a foe that still acts this turn must leave a 'it missed' outcome behind. */
    static void uncertainKoSplitsTheTurn() {
        Battler tork = torkoal();
        Battler chomp = garchomp();
        Battler cress = cresselia(0.05);
        Battler lat = latios(0.05, true);
        BattleState st = EngineScenarios.state(true, List.of(tork, chomp, venusaur(), charizardMegaY()), List.of(cress, lat));
        st.myTeam.get(2).hp = 0;
        st.myTeam.get(3).hp = 0;
        MoveInfo rs = chomp.moves.get(3);
        List<Action> acts = new ArrayList<>(List.of(Action.move(true, 0, tork.moves.get(2), false, -1),
            Action.move(true, 1, rs, false, -1)));
        acts.add(com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, 0, st.oppKind).get(0).action());
        acts.add(com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, 1, st.oppKind).get(0).action());
        var outs = new com.manueeh.cobbleai.engine.TurnSimulator(false).run(st, acts);
        double garchompLoss = 0;
        for (var o : outs)
            garchompLoss += o.prob() * ((1 - o.state().my(0).hpFrac()) + (1 - o.state().my(1).hpFrac()));
        boolean split = outs.size() > 1 && garchompLoss > 0.01;
        if (split) EngineScenarios.pass("tower27 uncertain KO is branched",
            outs.size() + " outcomes, expected HP loss of our two actives " + String.format("%.3f", garchompLoss));
        else EngineScenarios.fail("tower27 uncertain KO is branched", outs.size() + " outcomes, loss " + garchompLoss);
    }

    public static void scenarios() {
        protectsAgainstPremiumStab();
        noDoubleProtectFromStrongPosition();
        uncertainKoSplitsTheTurn();
    }

    public static void main(String[] args) {
        EngineScenarios.main(args);
    }
}
