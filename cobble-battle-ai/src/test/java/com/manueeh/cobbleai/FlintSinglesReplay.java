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

/** Singles win vs Flint (hard NPC, level 100) with Kaprus_'s team; several turns refused a free KO on Emboar. */
public final class FlintSinglesReplay {

    static BattleState last;

    static Battler swampert() {
        Battler b = mon("Swampert", true, 100, new int[] {341, 429, 256, 196, 256, 166}, "water", "ground");
        b.item = "swampertite";
        b.ability = "swiftswim";

        b.moves = new ArrayList<>(List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            mv("waterfall", "water", Category.PHYSICAL, 80), mv("icepunch", "ice", Category.PHYSICAL, 75),
            mv("protect", "normal", Category.STATUS, 0, "self")));
        return b;
    }

    static Battler weavile() {
        Battler b = mon("Weavile", true, 100, new int[] {281, 330, 166, 100, 206, 377}, "dark", "ice");
        b.item = "choicescarf";
        b.ability = "pressure";
        MoveInfo shard = mv("iceshard", "ice", Category.PHYSICAL, 40);
        shard.priority = 1;
        MoveInfo axel = mv("tripleaxel", "ice", Category.PHYSICAL, 20);
        axel.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(axel, mv("lowkick", "fighting", Category.PHYSICAL, 0), shard,
            mv("knockoff", "dark", Category.PHYSICAL, 65)));
        return b;
    }

    static Battler kingdra() {
        Battler b = mon("Kingdra", true, 100, new int[] {291, 198, 226, 310, 226, 255}, "water", "dragon");
        b.item = "lifeorb";
        b.ability = "swiftswim";
        MoveInfo draco = mv("dracometeor", "dragon", Category.SPECIAL, 130);
        draco.accuracy = 0.9;
        MoveInfo hp = mv("hydropump", "water", Category.SPECIAL, 110);
        hp.accuracy = 0.8;
        b.moves = new ArrayList<>(List.of(draco, mv("scald", "water", Category.SPECIAL, 80),
            mv("icebeam", "ice", Category.SPECIAL, 90), hp));
        return b;
    }

    static Battler garchompLo() {
        Battler b = mon("Garchomp", true, 100, new int[] {357, 347, 226, 155, 206, 324}, "dragon", "ground");
        b.item = "lifeorb";
        b.ability = "roughskin";
        b.moves = new ArrayList<>(List.of(mv("swordsdance", "normal", Category.STATUS, 0, "self"),
            mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"), mv("dragonclaw", "dragon", Category.PHYSICAL, 80),
            mv("stealthrock", "rock", Category.STATUS, 0, "foeSide")));
        return b;
    }

    static Battler garchompScarf() {
        Battler b = mon("Garchomp", true, 100, new int[] {357, 350, 226, 192, 206, 295}, "dragon", "ground");
        b.item = "choicescarf";
        b.ability = "roughskin";
        b.moves = new ArrayList<>(List.of(mv("sandstorm", "rock", Category.STATUS, 0, "all"),
            mv("falseswipe", "normal", Category.PHYSICAL, 40), mv("swordsdance", "normal", Category.STATUS, 0, "self"),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80)));
        return b;
    }

    static Battler flygon() {
        Battler b = mon("Flygon", true, 100, new int[] {301, 328, 196, 176, 196, 299}, "ground", "dragon");
        b.item = "widelens";
        b.ability = "levitate";
        MoveInfo fly = mv("fly", "flying", Category.PHYSICAL, 90);
        fly.accuracy = 0.95;
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80),
            mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"), fly, rs));
        return b;
    }

    static Battler emboar(double hp, int atkStage, int defStage) {
        Battler b = estimated(mon("Emboar", false, 100, new int[] {382, 314, 187, 257, 187, 187}, "fire", "fighting"));
        b.possibleAbilities = List.of("blaze", "reckless");
        b.weightKg = 150;
        b.hp = b.maxHp * hp;
        b.boosts[MoveDex.ATK] = atkStage;
        b.boosts[MoveDex.DEF] = defStage;
        b.item = null;
        b.itemUnknown = false;
        b.moves = new ArrayList<>(List.of(mv("superpower", "fighting", Category.PHYSICAL, 120),
            guess(mv("flareblitz", "fire", Category.PHYSICAL, 120)), guess(mv("bulkup", "fighting", Category.STATUS, 0, "self")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"))));
        b.turnsActive = 1;
        return b;
    }

    static Battler drifblim() {
        Battler b = estimated(mon("Drifblim", false, 100, new int[] {462, 259, 145, 279, 165, 284}, "ghost", "flying"));
        b.possibleAbilities = List.of("aftermath", "unburden", "flareboost");
        b.weightKg = 15;
        b.moves = new ArrayList<>(List.of(mv("willowisp", "fire", Category.STATUS, 0), mv("calmmind", "psychic", Category.STATUS, 0, "self"),
            guess(mv("shadowball", "ghost", Category.SPECIAL, 80)), guess(mv("airslash", "flying", Category.SPECIAL, 75))));
        return b;
    }

    static Battler fainted(Battler b) {
        b.hp = 0;
        return b;
    }

    static BattleState team(Battler active, List<Battler> bench, Battler foe) {
        List<Battler> mine = new ArrayList<>();
        mine.add(active);
        mine.addAll(bench);
        List<Battler> foes = new ArrayList<>(List.of(foe, drifblim()));
        BattleState st = EngineScenarios.state(false, mine, foes);
        st.oppUnseenReserves = 1;
        return st;
    }

    static List<Integer> switches(BattleState st) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i < st.myTeam.size(); i++) if (st.myTeam.get(i).alive()) out.add(i);
        return out;
    }

    static Planner.Plan decide(BattleState st) {
        last = st;
        Planner.SlotRequest r = EngineScenarios.request(st.my(0), switches(st));
        return new Planner(new Planner.Options()).decide(st, List.of(r));
    }

    /** Turn 10: Kingdra 8% (Life Orb recoil will finish it anyway) vs Emboar 67% -1/-1: Scald/Hydro Pump/Draco KO it. */
    static Planner.Plan flintTurn10() {
        Battler k = kingdra();
        k.hp = k.maxHp * 0.08;
        k.turnsActive = 1;
        Battler sw = swampert();
        sw.hp = sw.maxHp * 0.72;
        Battler g = garchompLo();
        g.hp = g.maxHp * 0.94;
        g.status = "brn";
        return decide(team(k, List.of(sw, weavile(), g, garchompScarf(), flygon()), emboar(0.67, -1, -1)));
    }

    /** Turn 11: fresh Weavile (Scarf, not locked) vs Emboar 42% -1/-1: Low Kick outspeeds and KOs. */
    static Planner.Plan flintTurn11() {
        Battler w = weavile();
        Battler sw = swampert();
        sw.hp = sw.maxHp * 0.72;
        Battler g = garchompLo();
        g.hp = g.maxHp * 0.94;
        g.status = "brn";
        return decide(team(w, List.of(sw, fainted(kingdra()), g, garchompScarf(), flygon()), emboar(0.42, -1, -1)));
    }

    /** Turn 12: Scarf Garchomp 63% (just switched in) vs Emboar 42% -2/-2: Dragon Claw KOs, False Swipe cannot. */
    static Planner.Plan flintTurn12() {
        Battler gs = garchompScarf();
        gs.hp = gs.maxHp * 0.63;
        gs.turnsActive = 1;
        Battler sw = swampert();
        sw.hp = sw.maxHp * 0.72;
        Battler g = garchompLo();
        g.hp = g.maxHp * 0.94;
        g.status = "brn";
        return decide(team(gs, List.of(sw, weavile(), fainted(kingdra()), g, flygon()), emboar(0.42, -2, -2)));
    }

    /** Turn 9: Scarf Weavile locked into Knock Off vs a fresh Emboar 67% (item knocked off, nothing revealed yet). */
    static Planner.Plan flintTurn9() {
        Battler w = weavile();
        w.lockedMove = "knockoff";
        w.turnsActive = 2;
        Battler sw = swampert();
        sw.hp = sw.maxHp * 0.72;
        Battler k = kingdra();
        k.hp = k.maxHp * 0.77;
        Battler g = garchompLo();
        g.hp = g.maxHp * 0.94;
        g.status = "brn";
        Battler e = emboar(0.67, 0, 0);
        e.turnsActive = 0;
        e.moves = new ArrayList<>(List.of(guess(mv("closecombat", "fighting", Category.PHYSICAL, 120)),
            guess(mv("flareblitz", "fire", Category.PHYSICAL, 120)), guess(mv("bulkup", "fighting", Category.STATUS, 0, "self")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"))));
        BattleState st = team(w, List.of(sw, k, g, garchompScarf(), flygon()), e);
        last = st;
        Planner.SlotRequest r = EngineScenarios.request(w, switches(st));
        r.moves.removeIf(m -> !m.id.equals("knockoff"));
        return new Planner(new Planner.Options()).decide(st, List.of(r));
    }

    /** Turn 6: Kingdra 77% vs a fresh Drifblim (nothing revealed; singles guesses include Will-O-Wisp). */
    static Planner.Plan flintTurn6() {
        Battler k = kingdra();
        k.hp = k.maxHp * 0.77;
        k.turnsActive = 2;
        Battler sw = swampert();
        sw.hp = sw.maxHp * 0.72;
        Battler d = drifblim();
        d.moves = new ArrayList<>(List.of(guess(mv("shadowball", "ghost", Category.SPECIAL, 80)), guess(mv("airslash", "flying", Category.SPECIAL, 75)),
            guess(mv("calmmind", "psychic", Category.STATUS, 0, "self")), guess(mv("willowisp", "fire", Category.STATUS, 0)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        d.moves.get(3).accuracy = 0.85;
        d.turnsActive = 0;
        List<Battler> mine = new ArrayList<>(List.of(k, sw, weavile(), garchompLo(), garchompScarf(), flygon()));
        BattleState st = EngineScenarios.state(false, mine, new ArrayList<>(List.of(d)));
        st.oppUnseenReserves = 2;
        return decide(st);
    }

    public static void scenarios() {
        Planner.Plan p10 = flintTurn10();
        Action a = p10.actions.get(0);
        report("flint T10 Kingdra 8% takes the Emboar KO (Scald/Hydro Pump/Draco), not a resisted Ice Beam",
            isMove(a, "scald") || isMove(a, "hydropump") || isMove(a, "dracometeor"), p10);
        Planner.Plan p11 = flintTurn11();
        report("flint T11 fresh Scarf Weavile Low Kicks the 42% Emboar instead of switching", isMove(p11.actions.get(0), "lowkick"), p11);
        Planner.Plan p12 = flintTurn12();
        report("flint T12 Scarf Garchomp KOs with Dragon Claw instead of False Swipe", isMove(p12.actions.get(0), "dragonclaw"), p12);
    }

    public static void main(String[] args) {
        Planner.Plan p6 = flintTurn6();
        System.out.println("  [info] flint T6 Kingdra 77% vs fresh Drifblim: " + Tower31Replay.label(p6) + " " + EngineScenarios.describe(p6));
        Planner.Plan p9 = flintTurn9();
        System.out.println("  [info] flint T9 locked Weavile vs fresh Emboar: " + Tower31Replay.label(p9) + " " + EngineScenarios.describe(p9));
        scenarios();
    }
}
