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

/**
 * Scenarios rebuilt from Battle Tower fight #18 (Lorena: Torkoal/Scrafty/Gallade/Porygon2, won but lost
 * three Pokemon) and the lost fight #31 (Saga Calixta: Garchomp/Lugia/Arceus/Whimsicott).
 */
public final class Tower31Replay {

    // ------------------------------------------------------------------ our team (as the log shows it)

    static Battler venusaur() {
        Battler b = mon("Venusaur", true, 50, new int[] {155, 91, 103, 167, 120, 132}, "grass", "poison");
        b.item = "lifeorb";
        b.ability = "chlorophyll";
        MoveInfo sp = mv("sleeppowder", "grass", Category.STATUS, 0, "normal");
        sp.accuracy = 0.75;
        b.moves = new ArrayList<>(List.of(mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("sludgebomb", "poison", Category.SPECIAL, 90), sp, mv("gigadrain", "grass", Category.SPECIAL, 75)));
        return b;
    }

    static Battler torkoal() {
        Battler b = mon("Torkoal", true, 50, new int[] {145, 89, 160, 147, 90, 37}, "fire");
        b.item = "charcoalstick";
        b.ability = "drought";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(hw, mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"), mv("earthpower", "ground", Category.SPECIAL, 90)));
        return b;
    }

    static Battler charizard(boolean evolved) {
        Battler b = mon("Charizard", true, 50, new int[] {153, 93, 98, 177, 105, 152}, "fire", "flying");
        b.item = "charizarditey";
        b.ability = "solarpower";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        MoveInfo fb = mv("focusblast", "fighting", Category.SPECIAL, 120);
        fb.accuracy = 0.7;
        b.moves = new ArrayList<>(List.of(mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("solarbeam", "grass", Category.SPECIAL, 120), hw, fb));
        Battler mega = b.copy();
        mega.atk = 111;
        mega.spa = 232;
        mega.spd = 135;
        mega.ability = "drought";
        if (evolved) {
            Battler.applyForm(b, mega);
        } else {
            b.pendingMega = mega;
        }
        return b;
    }

    static Battler garchomp() {
        Battler b = mon("Garchomp", true, 50, new int[] {183, 182, 115, 90, 105, 168}, "dragon", "ground");
        b.item = "choicescarf";
        b.ability = "roughskin";
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        b.moves = new ArrayList<>(List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            mv("stompingtantrum", "ground", Category.PHYSICAL, 75), mv("dragonclaw", "dragon", Category.PHYSICAL, 80), rs));
        return b;
    }

    static MoveInfo guess(MoveInfo m) {
        m.revealed = false;
        return m;
    }

    static Battler estimated(Battler b) {
        b.statsExact = false;
        b.itemUnknown = true;
        return b;
    }

    // ------------------------------------------------------------------ fight #31 foes

    static Battler lugia() {
        Battler b = estimated(mon("Lugia", false, 50, new int[] {213, 156, 150, 121, 174, 178}, "psychic", "flying"));
        b.possibleAbilities = List.of("pressure", "multiscale");
        MoveInfo bb = guess(mv("bravebird", "flying", Category.PHYSICAL, 120));
        b.moves = new ArrayList<>(List.of(mv("psychic", "psychic", Category.SPECIAL, 90), bb,
            guess(mv("avalanche", "ice", Category.PHYSICAL, 60)), guess(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent")),
            guess(mv("protect", "normal", Category.STATUS, 0, "self")),
            guess(mv("tailwind", "flying", Category.STATUS, 0, "self"))));
        return b;
    }

    static Battler foeGarchomp() {
        Battler b = estimated(mon("Garchomp", false, 50, new int[] {215, 200, 115, 111, 105, 169}, "dragon", "ground"));
        b.moves = new ArrayList<>(List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80),
            guess(mv("outrage", "dragon", Category.PHYSICAL, 120, "randomNormal")),
            guess(mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly")),
            guess(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent")),
            guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes")),
            guess(mv("swordsdance", "normal", Category.STATUS, 0, "self")), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        b.moves.get(4).accuracy = 0.9;
        return b;
    }

    static Battler arceus() {
        Battler b = estimated(mon("Arceus", false, 50, new int[] {227, 189, 140, 151, 140, 189}, "normal"));
        b.moves = new ArrayList<>(List.of(guess(mv("judgment", "normal", Category.SPECIAL, 100)),
            guess(mv("liquidation", "water", Category.PHYSICAL, 85)), guess(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes")),
            guess(mv("swordsdance", "normal", Category.STATUS, 0, "self"))));
        return b;
    }

    static List<Planner.SlotRequest> reqs(BattleState st, List<Integer> sw0, List<Integer> sw1) {
        List<Planner.SlotRequest> out = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = EngineScenarios.request(st.my(i), i == 0 ? sw0 : sw1);
            r.slot = i;
            if (st.my(i).pendingMega != null) {
                r.canMega = true;
                r.megaForm = st.my(i).pendingMega;
            }
            out.add(r);
        }
        return out;
    }

    static String label(Planner.Plan p) {
        StringBuilder sb = new StringBuilder();
        for (Action a : p.actions) sb.append(EngineScenarios.label(a)).append(' ');
        return sb.toString().trim();
    }

    static boolean isMove(Action a, String id) {
        return a.kind == Action.Kind.MOVE && a.move.id.equals(id);
    }

    static boolean protects(Action a) {
        return a.kind == Action.Kind.MOVE && MoveDex.PROTECT.contains(a.move.id);
    }

    /**
     * #31 turn 2: both foes fired into Venusaur's Protect on turn 1 (Psychic + Dragon Claw). Venusaur cannot
     * Protect again. The mod picked Giga Drain + Charizard Protect[mega], and Venusaur died for nothing.
     */
    static Planner.Plan t31turn2() {
        Battler venu = venusaur();
        venu.protectStreak = 1;
        venu.protectUses = 1;
        venu.turnsActive = 1;
        Battler zard = charizard(false);
        zard.hp = zard.maxHp * 0.88;
        Battler tork = torkoal();
        Battler chomp = garchomp();
        Battler lug = lugia();
        Battler gar = foeGarchomp();
        lug.turnsActive = 1;
        gar.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(venu, zard, tork, chomp), List.of(lug, gar));
        st.field.weather = "sun";
        st.field.weatherTurns = 4;
        st.oppUnseenReserves = 2;
        lug.lastMove = "psychic";
        lug.lastTarget = venu.uuid;
        gar.lastMove = "dragonclaw";
        gar.lastTarget = venu.uuid;
        lastState = st;
        return new Planner(new Planner.Options()).decide(st, reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    static BattleState lastState;

    /** #31 turn 3: our Garchomp was just sent in; foe Garchomp is at 41% and slower than our Scarf. */
    static Planner.Plan t31turn3() {
        Battler chomp = garchomp();
        Battler zard = charizard(true);
        zard.hp = zard.maxHp * 0.88;
        zard.turnsActive = 2;
        zard.protectStreak = 1;
        Battler tork = torkoal();
        Battler venu = venusaur();
        venu.hp = 0;
        Battler lug = lugia();
        lug.turnsActive = 2;
        Battler gar = foeGarchomp();
        gar.turnsActive = 2;
        gar.hp = gar.maxHp * 0.41;
        BattleState st = EngineScenarios.state(true, List.of(chomp, zard, tork, venu), List.of(lug, gar));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 2;
        return new Planner(new Planner.Options()).decide(st, reqs(st, List.of(2), List.of(2)));
    }

    /** #31 turn 5: Scarf Garchomp comes in next to Charizard; Rock Slide is super effective on Lugia. */
    static Planner.Plan t31turn5() {
        Battler chomp = garchomp();
        Battler zard = charizard(true);
        zard.hp = zard.maxHp * 0.88;
        zard.turnsActive = 4;
        zard.protectStreak = 1;
        Battler tork = torkoal();
        tork.hp = 0;
        Battler venu = venusaur();
        venu.hp = 0;
        Battler lug = lugia();
        lug.hp = lug.maxHp * 0.88;
        lug.boosts[MoveDex.SPA] = 1;
        lug.boosts[MoveDex.SPD] = 1;
        lug.item = "leftovers";
        lug.itemUnknown = false;
        lug.moves.add(0, mv("calmmind", "psychic", Category.STATUS, 0, "self"));
        Battler arc = arceus();
        arc.hp = arc.maxHp * 0.64;
        arc.moves.get(0).revealed = true;
        arc.types = new String[] {"water"};
        arc.baseTypes = arc.types;
        Battler gar = foeGarchomp();
        gar.hp = gar.maxHp * 0.41;
        BattleState st = EngineScenarios.state(true, List.of(chomp, zard, tork, venu), List.of(lug, arc, gar));
        st.field.weather = "sun";
        st.field.weatherTurns = 1;
        st.oppUnseenReserves = 1;
        return new Planner(new Planner.Options()).decide(st, reqs(st, List.of(), List.of()));
    }

    // ------------------------------------------------------------------ fight #18 foes

    static Battler scrafty(double hp) {
        Battler b = estimated(mon("Scrafty", false, 50, new int[] {140, 200, 135, 86, 135, 99}, "dark", "fighting"));
        b.hp = b.maxHp * hp;
        b.ability = "intimidate";
        b.moves = new ArrayList<>(List.of(mv("closecombat", "fighting", Category.PHYSICAL, 120),
            mv("fakeout", "normal", Category.PHYSICAL, 40), mv("protect", "normal", Category.STATUS, 0, "self"),
            guess(mv("knockoff", "dark", Category.PHYSICAL, 65))));
        return b;
    }

    static Battler gallade(double hp) {
        Battler b = estimated(mon("Gallade", false, 50, new int[] {143, 194, 85, 96, 135, 145}, "psychic", "fighting"));
        b.hp = b.maxHp * hp;
        b.moves = new ArrayList<>(List.of(guess(mv("psychocut", "psychic", Category.PHYSICAL, 70)),
            guess(mv("closecombat", "fighting", Category.PHYSICAL, 120)), guess(mv("leafblade", "grass", Category.PHYSICAL, 90)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        return b;
    }

    static Battler porygon2() {
        Battler b = estimated(mon("Porygon2", false, 50, new int[] {160, 111, 110, 172, 115, 91}, "normal"));
        b.item = "eviolite";
        b.itemUnknown = false;
        b.ability = "trace";
        b.moves = new ArrayList<>(List.of(mv("icebeam", "ice", Category.SPECIAL, 90),
            mv("trickroom", "psychic", Category.STATUS, 0, "all"), guess(mv("triattack", "normal", Category.SPECIAL, 80))));
        return b;
    }

    /** #18 turn 3: Scrafty 27% (Heat Wave KOs it 90%), fresh Gallade. Venusaur Sludge Bombed Scrafty and died. */
    static Planner.Plan t18turn3() {
        Battler venu = venusaur();
        venu.hp = venu.maxHp * 0.90;
        venu.boosts[MoveDex.ATK] = -1;
        venu.turnsActive = 2;
        Battler tork = torkoal();
        tork.hp = tork.maxHp * 0.42;
        tork.boosts[MoveDex.ATK] = -1;
        tork.turnsActive = 2;
        Battler chomp = garchomp();
        Battler zard = charizard(false);
        Battler scr = scrafty(0.27);
        scr.boosts[MoveDex.DEF] = -1;
        scr.boosts[MoveDex.SPD] = -1;
        scr.turnsActive = 2;
        Battler gal = gallade(1.0);
        BattleState st = EngineScenarios.state(true, List.of(venu, tork, chomp, zard), List.of(scr, gal));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 1;
        return new Planner(new Planner.Options()).decide(st, reqs(st, List.of(2, 3), List.of(2, 3)));
    }

    /** #18 turn 4: Scarf Garchomp + Torkoal 42% vs fresh Porygon2 and Gallade 35% (faster than Torkoal). */
    static Planner.Plan t18turn4() {
        Battler chomp = garchomp();
        Battler tork = torkoal();
        tork.hp = tork.maxHp * 0.42;
        tork.boosts[MoveDex.ATK] = -1;
        tork.turnsActive = 3;
        Battler venu = venusaur();
        venu.hp = 0;
        Battler zard = charizard(false);
        Battler p2 = porygon2();
        Battler gal = gallade(0.35);
        gal.moves.get(0).revealed = true;
        gal.turnsActive = 1;
        BattleState st = EngineScenarios.state(true, List.of(chomp, tork, venu, zard), List.of(p2, gal));
        st.field.weather = "sun";
        st.field.weatherTurns = 2;
        st.oppUnseenReserves = 0;
        return new Planner(new Planner.Options()).decide(st, reqs(st, List.of(3), List.of(3)));
    }

    // ------------------------------------------------------------------ checks

    static void report(String name, boolean ok, Planner.Plan p) {
        String detail = label(p) + "  " + EngineScenarios.describe(p) + " notes=" + p.notes;
        if (ok) EngineScenarios.pass(name, detail);
        else EngineScenarios.fail(name, detail);
    }

    public static void scenarios() {
        Planner.Plan p = t31turn2();
        // Both foes went into Venusaur's Protect on turn 1: the model must expect them to go at it again, with
        // the move they showed (Psychic), not a guessed Brave Bird the trainer AI would already have used.
        BattleState st2 = lastState;
        var lugiaPolicy = com.manueeh.cobbleai.engine.OpponentModel.policy(st2, false, 0, st2.oppKind);
        Action top = lugiaPolicy.get(0).action();
        report("tower31 T2 Lugia predicted to repeat Psychic into Venusaur", isMove(top, "psychic") && top.targetSlot == 0, p);
        Action venu;

        p = t31turn3();
        Action chomp = p.actions.get(0);
        report("tower31 T3 fresh Scarf Garchomp finishes the 41% Garchomp", chomp.kind == Action.Kind.MOVE
            && (chomp.targetSlot == 1 || chomp.move.isSpread()), p);

        p = t31turn5();
        Action chomp5 = p.actions.get(0);
        report("tower31 T5 Scarf lock: Rock Slide (SE on Lugia, hits both) over Dragon Claw", isMove(chomp5, "rockslide"), p);

        p = t18turn3();
        venu = p.actions.get(0);
        report("tower18 T3 no double-up on the 27% Scrafty with Venusaur", !(isMove(venu, "sludgebomb") && venu.targetSlot == 0), p);

        p = t18turn4();
        chomp = p.actions.get(0);
        report("tower18 T4 Garchomp takes the Gallade KO itself (Torkoal moves after Gallade)",
            chomp.kind == Action.Kind.MOVE && (chomp.targetSlot == 1 || isMove(chomp, "rockslide")), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
