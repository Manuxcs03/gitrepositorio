package com.manueeh.cobbleai;

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
import static com.manueeh.cobbleai.Tower31Replay.report;

/**
 * Principle checks on teams that have nothing to do with the sun team the replays were logged with (rain,
 * Trick Room, sand, singles). The replays pin down fights that were lost; these make sure the rules behind the
 * fixes hold for any team and any trainer.
 */
public final class GeneralScenarios {
    private GeneralScenarios() {}

    static BattleState last;

    static Planner.Plan decide(BattleState st, List<Planner.SlotRequest> reqs) {
        last = st;
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    static Planner.SlotRequest forced(int slot, List<Integer> switches) {
        Planner.SlotRequest r = new Planner.SlotRequest();
        r.slot = slot;
        r.forceSwitch = true;
        r.switchOptions = new ArrayList<>(switches);
        return r;
    }

    static Planner.SlotRequest pass(int slot) {
        Planner.SlotRequest r = new Planner.SlotRequest();
        r.slot = slot;
        r.pass = true;
        return r;
    }

    static Planner.SlotRequest moves(BattleState st, int slot, List<Integer> switches) {
        Planner.SlotRequest r = EngineScenarios.request(st.my(slot), switches);
        r.slot = slot;
        return r;
    }

    // ------------------------------------------------------------------ Trick Room

    /**
     * Under Trick Room the SLOWER foe moves first. A replacement that a slow foe one-shots with a shown move is a
     * free KO for it, even though that foe has a lower Speed stat. (Before, "faster" was a raw Speed compare.)
     */
    static Planner.Plan trickRoomReplacement() {
        Battler dead = mon("Hatterene", true, 50, new int[] {132, 110, 115, 188, 123, 49}, "psychic", "fairy");
        dead.hp = 0;
        Battler partner = mon("Porygon2", true, 50, new int[] {192, 100, 110, 125, 115, 80}, "normal");
        partner.moves = new ArrayList<>(List.of(mv("triattack", "normal", Category.SPECIAL, 80),
            mv("icebeam", "ice", Category.SPECIAL, 90), mv("trickroom", "psychic", Category.STATUS, 0, "all"),
            mv("recover", "normal", Category.STATUS, 0, "self")));
        // Heatran: 4x weak to the foe's shown Earth Power. Gyarados: immune to it and neutral to Heat Wave.
        Battler heatran = mon("Heatran", true, 50, new int[] {166, 110, 126, 182, 126, 97}, "fire", "steel");
        heatran.moves = new ArrayList<>(List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"),
            mv("earthpower", "ground", Category.SPECIAL, 90), mv("flashcannon", "steel", Category.SPECIAL, 80),
            mv("protect", "normal", Category.STATUS, 0, "self")));
        Battler gyara = mon("Gyarados", true, 50, new int[] {170, 177, 99, 80, 120, 133}, "water", "flying");
        gyara.moves = new ArrayList<>(List.of(mv("waterfall", "water", Category.PHYSICAL, 80),
            mv("bounce", "flying", Category.PHYSICAL, 85), mv("icefang", "ice", Category.PHYSICAL, 65),
            mv("protect", "normal", Category.STATUS, 0, "self")));

        Battler camerupt = estimated(mon("Camerupt", false, 50, new int[] {145, 120, 90, 172, 95, 45}, "fire", "ground"));
        camerupt.moves = new ArrayList<>(List.of(mv("earthpower", "ground", Category.SPECIAL, 90),
            mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        camerupt.turnsActive = 2;
        Battler dusk = estimated(mon("Dusclops", false, 50, new int[] {147, 90, 150, 60, 150, 30}, "ghost"));
        dusk.moves = new ArrayList<>(List.of(mv("nightshade", "ghost", Category.SPECIAL, 50),
            guess(mv("trickroom", "psychic", Category.STATUS, 0, "all")), guess(mv("painsplit", "normal", Category.STATUS, 0))));
        dusk.turnsActive = 2;

        BattleState st = EngineScenarios.state(true, List.of(dead, partner, heatran, gyara), List.of(camerupt, dusk));
        st.field.trickRoom = true;
        st.field.trickRoomTurns = 3;
        st.oppUnseenReserves = 2;
        return decide(st, List.of(forced(0, List.of(2, 3)), pass(1)));
    }

    // ------------------------------------------------------------------ hidden coverage

    /**
     * A foe that has shown nothing yet still carries likely coverage. Bringing a 4x-weak Pokemon in front of a
     * FASTER fresh foe whose likely attacks one-shot it (Mamoswine vs a Dragon/Ground) is a free KO; the
     * neutral replacement is the play. Before, only shown moves counted as danger.
     */
    static Planner.Plan freshFoeHiddenCoverage() {
        Battler dead = mon("Tyranitar", true, 50, new int[] {175, 186, 130, 115, 120, 81}, "rock", "dark");
        dead.hp = 0;
        Battler partner = mon("Excadrill", true, 50, new int[] {185, 205, 80, 70, 85, 140}, "ground", "steel");
        partner.moves = new ArrayList<>(List.of(mv("highhorsepower", "ground", Category.PHYSICAL, 95),
            mv("ironhead", "steel", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"),
            mv("protect", "normal", Category.STATUS, 0, "self")));
        Battler garchomp = mon("Garchomp", true, 50, new int[] {183, 182, 115, 90, 105, 154}, "dragon", "ground");
        garchomp.hp = garchomp.maxHp * 0.55;
        garchomp.moves = new ArrayList<>(List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80),
            mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"),
            mv("protect", "normal", Category.STATUS, 0, "self")));
        Battler cresselia = mon("Cresselia", true, 50, new int[] {227, 90, 140, 95, 150, 105}, "psychic");
        cresselia.moves = new ArrayList<>(List.of(mv("psychic", "psychic", Category.SPECIAL, 90),
            mv("icebeam", "ice", Category.SPECIAL, 90), mv("moonlight", "fairy", Category.STATUS, 0, "self"),
            mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly")));

        // Two fresh special attackers, nothing shown. Both likely carry Ice coverage and outspeed Garchomp.
        Battler latios = estimated(mon("Latios", false, 50, new int[] {155, 100, 100, 182, 130, 178}, "dragon", "psychic"));
        MoveInfo draco = guess(mv("dracometeor", "dragon", Category.SPECIAL, 130));
        draco.accuracy = 0.9;
        latios.moves = new ArrayList<>(List.of(draco, guess(mv("psyshock", "psychic", Category.SPECIAL, 80)),
            guess(mv("icebeam", "ice", Category.SPECIAL, 90)), guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        Battler weavile = estimated(mon("Weavile", false, 50, new int[] {145, 172, 85, 65, 105, 177}, "dark", "ice"));
        weavile.moves = new ArrayList<>(List.of(guess(mv("iciclecrash", "ice", Category.PHYSICAL, 85)),
            guess(mv("knockoff", "dark", Category.PHYSICAL, 65)), guess(mv("iceshard", "ice", Category.PHYSICAL, 40)),
            guess(mv("protect", "normal", Category.STATUS, 0, "self"))));
        weavile.moves.get(2).priority = 1;

        BattleState st = EngineScenarios.state(true, List.of(dead, partner, garchomp, cresselia), List.of(latios, weavile));
        st.oppUnseenReserves = 2;
        return decide(st, List.of(forced(0, List.of(2, 3)), pass(1)));
    }

    // ------------------------------------------------------------------ singles basics on another team

    /** Rain singles: the Water attack with rain behind it beats a neutral one, whatever team runs it. */
    static Planner.Plan rainSinglesUsesStab() {
        Battler barra = mon("Barraskewda", true, 50, new int[] {136, 175, 80, 72, 70, 188}, "water");
        barra.ability = "swiftswim";
        barra.moves = new ArrayList<>(List.of(mv("liquidation", "water", Category.PHYSICAL, 85),
            mv("closecombat", "fighting", Category.PHYSICAL, 120), mv("psychicfangs", "psychic", Category.PHYSICAL, 85),
            mv("aquajet", "water", Category.PHYSICAL, 40)));
        barra.moves.get(3).priority = 1;
        Battler foe = estimated(mon("Arcanine", false, 50, new int[] {165, 130, 100, 120, 100, 115}, "fire"));
        foe.moves = new ArrayList<>(List.of(mv("flareblitz", "fire", Category.PHYSICAL, 120),
            guess(mv("extremespeed", "normal", Category.PHYSICAL, 80))));
        foe.moves.get(1).priority = 2;
        BattleState st = EngineScenarios.state(false, List.of(barra), List.of(foe));
        st.field.weather = "rain";
        st.field.weatherTurns = 4;
        return decide(st, List.of(moves(st, 0, List.of())));
    }

    /**
     * Sand singles: a Pokemon that a faster foe has SHOWN it one-shots should leave when the bench has a safe
     * answer, even though the foe never targeted it before (general version of "do not stay in to die").
     */
    static Planner.Plan singlesLeavesShownOhko() {
        Battler lando = mon("Landorus", true, 50, new int[] {165, 197, 110, 125, 100, 143}, "ground", "flying");
        lando.hp = lando.maxHp * 0.7;
        lando.moves = new ArrayList<>(List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            mv("stoneedge", "rock", Category.PHYSICAL, 100), mv("uturn", "bug", Category.PHYSICAL, 70),
            mv("stealthrock", "rock", Category.STATUS, 0, "foeSide")));
        Battler ferro = mon("Ferrothorn", true, 50, new int[] {181, 125, 151, 74, 136, 22}, "grass", "steel");
        ferro.moves = new ArrayList<>(List.of(mv("powerwhip", "grass", Category.PHYSICAL, 120),
            mv("gyroball", "steel", Category.PHYSICAL, 60), mv("leechseed", "grass", Category.STATUS, 0),
            mv("protect", "normal", Category.STATUS, 0, "self")));
        Battler foe = estimated(mon("Greninja", false, 50, new int[] {147, 115, 87, 155, 91, 174}, "water", "dark"));
        foe.moves = new ArrayList<>(List.of(mv("icebeam", "ice", Category.SPECIAL, 90),
            guess(mv("hydropump", "water", Category.SPECIAL, 110)), guess(mv("darkpulse", "dark", Category.SPECIAL, 80))));
        foe.lastMove = "icebeam";
        foe.turnsActive = 1;
        BattleState st = EngineScenarios.state(false, List.of(lando, ferro), List.of(foe));
        st.oppUnseenReserves = 2;
        return decide(st, List.of(moves(st, 0, List.of(1))));
    }

    static boolean switchesTo(Planner.Plan p, int slot, int idx) {
        for (Action a : p.actions) if (a.slot == slot && a.kind == Action.Kind.SWITCH && a.switchTo == idx) return true;
        return false;
    }

    public static void scenarios() {
        Planner.Plan p = trickRoomReplacement();
        report("general TR: the replacement is not the one the slow foe one-shots under Trick Room", switchesTo(p, 0, 3), p);
        p = freshFoeHiddenCoverage();
        report("general coverage: no 4x-weak switch-in in front of fresh faster foes with likely coverage", switchesTo(p, 0, 3), p);
        p = rainSinglesUsesStab();
        Action a = p.actions.get(0);
        report("general rain singles: rain-boosted STAB", a.kind == Action.Kind.MOVE && "liquidation".equals(a.move.id), p);
        p = singlesLeavesShownOhko();
        report("general singles: leave a shown faster one-shot for the safe answer", switchesTo(p, 0, 1), p);
    }

    public static void main(String[] args) {
        scenarios();
    }
}
