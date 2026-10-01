package com.manueeh.cobbleai;

import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Evaluator;
import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Offline sanity scenarios for the decision engine (no Minecraft needed).
 * Run with: gradlew engineScenarios
 */
public final class EngineScenarios {
    private static int failures = 0;

    public static void main(String[] args) {
        superEffectiveBeatsNeutral();
        avoidsLevitateGround();
        switchesOutOfLosingMatchup();
        setsUpWhenSafe();
        takesPriorityKo();
        forcedSwitchPicksCounter();
        doublesAvoidsHittingAllyWithEarthquake();
        doublesFocusesKillableTarget();
        statusOnPhysicalAttacker();
        healsWhenLowAndSafe();
        teraWhenItSavesTheGame();
        staysInWhenWinning();
        noThunderWaveOnGround();
        doublesSpreadIntoTwoWeakFoes();
        noStatusOnStatused();
        doublesPerformance();
        choiceSpecsNeverProtects();
        lockedIntoProtectSwitches();
        megaCharizardYUsesSun();
        fakeOutFirstTurnOnly();
        boostedThreatGetsRespect();
        towerReplayNoCharizardIntoRocks();
        tower4Turn1();
        tower4Turn2();
        wideGuardSpam();
        noFreeSetupTurnInSingles();
        megaTimingWithSun();
        rubenTurn1();
        borisTailwindTurn1();
        borisRememberedTurn1();
        noSleepOnDoomedTarget();
        killBeatsSleep();
        ruthSalamenceTurn3();
        perpetuaTurn3();
        melaniaFriendlyFire();
        wideGuardKnownAvoidsSpread();
        wideGuardOnCooldownSpreadsAgain();
        stalledSpreadIsPenalised();
        asleepUsesSleepTalk();
        asleepBadMatchupSwitches();
        weatherValueScalesWithTurns();
        sunTeamKeepsSunnyMove();
        Tower27Replay.scenarios();
        Tower11Replay.scenarios();
        Tower31Replay.scenarios();
        TowerAaronReplay.scenarios();
        TowerBorisReplay.scenarios();
        TowerOpeningReplay.scenarios();
        TowerHerminiaReplay.scenarios();
        TowerDeliaReplay.scenarios();
        TowerAdrianoReplay.scenarios();
        TowerCiraReplay.scenarios();
        TowerCasimiroReplay.scenarios();
        TowerNachoReplay.scenarios();
        TowerCesarReplay.scenarios();
        TowerBertaReplay.scenarios();
        TowerRuthReplay.scenarios();
        TowerCiriloReplay.scenarios();
        TowerBoris2Replay.scenarios();
        TowerTaliaReplay.scenarios();
        TowerAureoReplay.scenarios();
        TowerBoris3Replay.scenarios();
        TowerNagoreReplay.scenarios();
        TowerInesReplay.scenarios();
        FlintSinglesReplay.scenarios();
        TowerDelia2Replay.scenarios();
        TowerCira4Replay.scenarios();
        TowerEvaristoReplay.scenarios();
        MainTickSinglesReplay.scenarios();
        TowerAdanReplay.scenarios();
        TowerCira3Replay.scenarios();
        TowerRuben3Replay.scenarios();
        GeneralScenarios.scenarios();
        TowerCiriloCira5Replay.scenarios();
        TowerEvaristo2Replay.scenarios();
        FuzzScenarios.scenarios();
        System.out.println(failures == 0 ? "ALL SCENARIOS PASSED" : failures + " SCENARIO(S) FAILED");
        if (failures > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ scenarios

    static void superEffectiveBeatsNeutral() {
        Battler me = mon("Charizard", true, 50, new int[] {153, 104, 98, 129, 105, 120}, "fire", "flying");
        me.moves = List.of(mv("flamethrower", "fire", Category.SPECIAL, 90), mv("airslash", "flying", Category.SPECIAL, 75),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("slash", "normal", Category.PHYSICAL, 70));
        Battler foe = mon("Venusaur", false, 50, new int[] {155, 102, 103, 120, 120, 100}, "grass", "poison");
        foe.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75), mv("sludgebomb", "poison", Category.SPECIAL, 90));
        check("super effective", singles(me, foe, List.of()), "flamethrower");
    }

    static void avoidsLevitateGround() {
        Battler me = mon("Garchomp", true, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        me.moves = List.of(mv("earthquake", "ground", Category.PHYSICAL, 100), mv("dragonclaw", "dragon", Category.PHYSICAL, 80));
        Battler foe = mon("Gengar", false, 50, new int[] {135, 70, 80, 150, 95, 130}, "ghost", "poison");
        foe.possibleAbilities = List.of("levitate");
        foe.moves = List.of(mv("shadowball", "ghost", Category.SPECIAL, 80));
        check("levitate", singles(me, foe, List.of()), "dragonclaw");
    }

    static void switchesOutOfLosingMatchup() {
        Battler me = mon("Venusaur", true, 50, new int[] {155, 102, 103, 120, 120, 80}, "grass", "poison");
        me.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75), mv("sludgebomb", "poison", Category.SPECIAL, 90));
        Battler bench = mon("Blastoise", true, 50, new int[] {154, 103, 120, 105, 125, 98}, "water");
        bench.moves = List.of(mv("surf", "water", Category.SPECIAL, 90), mv("icebeam", "ice", Category.SPECIAL, 90));
        Battler foe = mon("Arcanine", false, 50, new int[] {165, 130, 100, 120, 100, 115}, "fire");
        foe.moves = List.of(mv("flareblitz", "fire", Category.PHYSICAL, 120), mv("extremespeed", "normal", Category.PHYSICAL, 80));
        foe.moves.get(1).priority = 2;
        Planner.Plan p = singles(me, foe, List.of(bench));
        check("switch out of bad matchup", p, "switch:1");
    }

    static void setsUpWhenSafe() {
        Battler me = mon("Dragonite", true, 50, new int[] {166, 154, 115, 120, 120, 100}, "dragon", "flying");
        me.moves = List.of(mv("dragondance", "dragon", Category.STATUS, 0), mv("dragonclaw", "dragon", Category.PHYSICAL, 80),
            mv("extremespeed", "normal", Category.PHYSICAL, 80));
        Battler foe = mon("Chansey", false, 50, new int[] {325, 25, 30, 55, 125, 70}, "normal");
        foe.moves = List.of(mv("seismictoss", "fighting", Category.PHYSICAL, 1), mv("softboiled", "normal", Category.STATUS, 0));
        // Seismic Toss hits for exactly level damage.
        Planner.Plan p = singles(me, foe, List.of());
        String got = label(p.actions.get(0));
        System.out.println("  [info] setup scenario chose " + got + " (dragondance or an attack are both acceptable)");
    }

    static void takesPriorityKo() {
        Battler me = mon("Lucario", true, 50, new int[] {145, 130, 90, 135, 90, 110}, "fighting", "steel");
        MoveInfo punch = mv("bulletpunch", "steel", Category.PHYSICAL, 40);
        punch.priority = 1;
        me.moves = List.of(mv("closecombat", "fighting", Category.PHYSICAL, 120), punch);
        me.hp = me.maxHp * 0.2;
        Battler foe = mon("Sylveon", false, 50, new int[] {170, 85, 85, 130, 150, 80}, "fairy");
        foe.spe = 200; // faster than us
        foe.hp = foe.maxHp * 0.08;
        foe.moves = List.of(mv("hypervoice", "normal", Category.SPECIAL, 90));
        check("priority KO", singles(me, foe, List.of()), "bulletpunch");
    }

    static void forcedSwitchPicksCounter() {
        Battler fainted = mon("Pikachu", true, 50, new int[] {110, 75, 60, 70, 70, 110}, "electric");
        fainted.hp = 0;
        Battler a = mon("Onix", true, 50, new int[] {95, 65, 180, 50, 65, 90}, "rock", "ground");
        a.moves = List.of(mv("rockslide", "rock", Category.PHYSICAL, 75));
        Battler b = mon("Gyarados", true, 50, new int[] {170, 145, 99, 80, 120, 101}, "water", "flying");
        b.moves = List.of(mv("waterfall", "water", Category.PHYSICAL, 80), mv("icefang", "ice", Category.PHYSICAL, 65));
        Battler foe = mon("Venusaur", false, 50, new int[] {155, 102, 103, 120, 120, 100}, "grass", "poison");
        foe.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75), mv("earthpower", "ground", Category.SPECIAL, 90));
        BattleState s = state(false, List.of(fainted, a, b), List.of(foe));
        s.myActive = new int[] {0};
        Planner.SlotRequest r = new Planner.SlotRequest();
        r.forceSwitch = true;
        r.switchOptions = new ArrayList<>(List.of(1, 2));
        Planner.Plan p = new Planner(new Planner.Options()).decide(s, List.of(r));
        check("forced switch", p, "switch:2");
    }

    static void doublesAvoidsHittingAllyWithEarthquake() {
        Battler me = mon("Garchomp", true, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        me.moves = List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"));
        Battler ally = mon("Heatran", true, 50, new int[] {166, 110, 126, 150, 126, 97}, "fire", "steel");
        ally.moves = List.of(mv("flamethrower", "fire", Category.SPECIAL, 90));
        Battler f1 = mon("Dragonite", false, 50, new int[] {166, 154, 115, 120, 120, 100}, "dragon", "flying");
        f1.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80));
        Battler f2 = mon("Togekiss", false, 50, new int[] {160, 70, 115, 140, 135, 100}, "fairy", "flying");
        f2.moves = List.of(mv("airslash", "flying", Category.SPECIAL, 75));
        Planner.Plan p = doubles(List.of(me, ally), List.of(f1, f2));
        String got = label(p.actions.get(0));
        if (got.startsWith("earthquake")) fail("doubles EQ", "used Earthquake into two flyers + own Heatran: " + got);
        else pass("doubles EQ", got);
    }

    static void doublesFocusesKillableTarget() {
        Battler me = mon("Garchomp", true, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        me.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("stoneedge", "rock", Category.PHYSICAL, 100));
        me.moves.get(1).accuracy = 0.8;
        Battler ally = mon("Gardevoir", true, 50, new int[] {143, 85, 85, 145, 135, 100}, "psychic", "fairy");
        ally.moves = List.of(mv("moonblast", "fairy", Category.SPECIAL, 95), mv("psychic", "psychic", Category.SPECIAL, 90));
        Battler f1 = mon("Salamence", false, 50, new int[] {170, 155, 100, 130, 100, 120}, "dragon", "flying");
        f1.hp = f1.maxHp * 0.3;
        f1.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80));
        Battler f2 = mon("Snorlax", false, 50, new int[] {235, 130, 85, 85, 130, 50}, "normal");
        f2.moves = List.of(mv("bodyslam", "normal", Category.PHYSICAL, 85));
        Planner.Plan p = doubles(List.of(me, ally), List.of(f1, f2));
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        boolean ok = p.actions.get(0).targetSlot == 0 || p.actions.get(1).targetSlot == 0;
        if (ok) pass("doubles focus", a0 + " + " + a1);
        else fail("doubles focus", "nobody attacked the weakened Salamence: " + a0 + " + " + a1);
    }

    static void statusOnPhysicalAttacker() {
        Battler me = mon("Rotom-Wash", true, 50, new int[] {110, 70, 127, 125, 127, 106}, "electric", "water");
        me.moves = List.of(mv("willowisp", "fire", Category.STATUS, 0), mv("hydropump", "water", Category.SPECIAL, 110),
            mv("voltswitch", "electric", Category.SPECIAL, 70));
        me.moves.get(0).accuracy = 0.85;
        me.moves.get(1).accuracy = 0.8;
        Battler foe = mon("Scizor", false, 50, new int[] {145, 150, 120, 75, 100, 85}, "bug", "steel");
        foe.moves = List.of(mv("xscissor", "bug", Category.PHYSICAL, 80), mv("bulletpunch", "steel", Category.PHYSICAL, 40));
        Planner.Plan p = singles(me, foe, List.of());
        System.out.println("  [info] Rotom-W vs Scizor chose " + label(p.actions.get(0)) + "  notes=" + p.notes);
    }

    static void healsWhenLowAndSafe() {
        Battler me = mon("Slowbro", true, 50, new int[] {170, 95, 130, 120, 100, 50}, "water", "psychic");
        me.moves = List.of(mv("slackoff", "normal", Category.STATUS, 0), mv("scald", "water", Category.SPECIAL, 80));
        me.hp = me.maxHp * 0.3;
        Battler foe = mon("Blissey", false, 50, new int[] {330, 30, 30, 95, 155, 75}, "normal");
        foe.moves = List.of(mv("seismictoss", "fighting", Category.PHYSICAL, 1));
        check("heal when low", singles(me, foe, List.of()), "slackoff");
    }

    static void teraWhenItSavesTheGame() {
        Battler me = mon("Dragonite", true, 50, new int[] {166, 154, 115, 120, 120, 100}, "dragon", "flying");
        me.teraType = "normal";
        me.moves = List.of(mv("extremespeed", "normal", Category.PHYSICAL, 80), mv("dragonclaw", "dragon", Category.PHYSICAL, 80));
        me.moves.get(0).priority = 2;
        Battler foe = mon("Weavile", false, 50, new int[] {145, 140, 85, 65, 105, 145}, "dark", "ice");
        foe.moves = List.of(mv("iceshard", "ice", Category.PHYSICAL, 40), mv("tripleaxel", "ice", Category.PHYSICAL, 20));
        foe.moves.get(0).priority = 1;
        Battler bench = mon("Snorlax", true, 50, new int[] {235, 130, 85, 85, 130, 50}, "normal");
        bench.moves = List.of(mv("bodyslam", "normal", Category.PHYSICAL, 85));
        BattleState s = state(false, List.of(me, bench), List.of(foe));
        Planner.SlotRequest r = request(me, List.of(1));
        r.canTera = true;
        Planner.Plan p = new Planner(new Planner.Options()).decide(s, List.of(r));
        System.out.println("  [info] Dragonite vs Weavile chose " + label(p.actions.get(0)) + " notes=" + p.notes);
    }

    static void staysInWhenWinning() {
        Battler me = mon("Blastoise", true, 50, new int[] {154, 103, 120, 105, 125, 98}, "water");
        me.moves = List.of(mv("surf", "water", Category.SPECIAL, 90), mv("icebeam", "ice", Category.SPECIAL, 90));
        Battler bench = mon("Pikachu", true, 50, new int[] {110, 75, 60, 70, 70, 110}, "electric");
        bench.moves = List.of(mv("thunderbolt", "electric", Category.SPECIAL, 90));
        Battler foe = mon("Arcanine", false, 50, new int[] {165, 130, 100, 120, 100, 115}, "fire");
        foe.moves = List.of(mv("flareblitz", "fire", Category.PHYSICAL, 120));
        check("stay in winning matchup", singles(me, foe, List.of(bench)), "surf");
    }

    static void noThunderWaveOnGround() {
        Battler me = mon("Jolteon", true, 50, new int[] {140, 85, 80, 130, 115, 150}, "electric");
        MoveInfo twave = mv("thunderwave", "electric", Category.STATUS, 0, "normal");
        twave.accuracy = 0.9;
        me.moves = List.of(twave, mv("thunderbolt", "electric", Category.SPECIAL, 90), mv("shadowball", "ghost", Category.SPECIAL, 80));
        Battler foe = mon("Golem", false, 50, new int[] {155, 140, 150, 75, 85, 65}, "rock", "ground");
        foe.moves = List.of(mv("earthquake", "ground", Category.PHYSICAL, 100));
        Planner.Plan p = singles(me, foe, List.of());
        String got = label(p.actions.get(0));
        if (got.startsWith("thunderwave") || got.startsWith("thunderbolt")) fail("ground immunity", got);
        else pass("ground immunity", got);
    }

    static void doublesSpreadIntoTwoWeakFoes() {
        Battler me = mon("Tyranitar", true, 50, new int[] {175, 154, 130, 95, 120, 81}, "rock", "dark");
        me.moves = List.of(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"), mv("crunch", "dark", Category.PHYSICAL, 80));
        me.moves.get(0).accuracy = 0.9;
        Battler ally = mon("Amoonguss", true, 50, new int[] {189, 90, 90, 105, 100, 50}, "grass", "poison");
        ally.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75));
        Battler f1 = mon("Charizard", false, 50, new int[] {153, 104, 98, 129, 105, 120}, "fire", "flying");
        f1.moves = List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"));
        Battler f2 = mon("Talonflame", false, 50, new int[] {153, 101, 91, 94, 89, 146}, "fire", "flying");
        f2.moves = List.of(mv("bravebird", "flying", Category.PHYSICAL, 120));
        Planner.Plan p = doubles(List.of(me, ally), List.of(f1, f2));
        String got = label(p.actions.get(0));
        if (got.startsWith("rockslide")) pass("doubles spread", got + " + " + label(p.actions.get(1)) + " " + p.micros + "us");
        else fail("doubles spread", got);
    }

    static void noStatusOnStatused() {
        Battler me = mon("Sableye", true, 50, new int[] {125, 95, 95, 85, 85, 70}, "dark", "ghost");
        MoveInfo wisp = mv("willowisp", "fire", Category.STATUS, 0, "normal");
        wisp.accuracy = 0.85;
        me.moves = List.of(wisp, mv("foulplay", "dark", Category.PHYSICAL, 95));
        Battler foe = mon("Machamp", false, 50, new int[] {165, 150, 100, 85, 105, 75}, "fighting");
        foe.status = "brn";
        foe.moves = List.of(mv("knockoff", "dark", Category.PHYSICAL, 65));
        check("no status on statused", singles(me, foe, List.of()), "foulplay");
    }

    static void doublesPerformance() {
        Battler a = mon("Garchomp", true, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        a.moves = List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"), mv("dragonclaw", "dragon", Category.PHYSICAL, 80),
            mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"), mv("protect", "normal", Category.STATUS, 0, "self"));
        Battler b = mon("Rotom", true, 50, new int[] {110, 70, 127, 125, 127, 106}, "electric", "water");
        b.moves = List.of(mv("hydropump", "water", Category.SPECIAL, 110), mv("thunderbolt", "electric", Category.SPECIAL, 90),
            mv("willowisp", "fire", Category.STATUS, 0, "normal"), mv("protect", "normal", Category.STATUS, 0, "self"));
        Battler c = mon("Snorlax", true, 50, new int[] {235, 130, 85, 85, 130, 50}, "normal");
        c.moves = List.of(mv("bodyslam", "normal", Category.PHYSICAL, 85));
        Battler d = mon("Heatran", true, 50, new int[] {166, 110, 126, 150, 126, 97}, "fire", "steel");
        d.moves = List.of(mv("flamethrower", "fire", Category.SPECIAL, 90));
        Battler f1 = mon("Dragonite", false, 50, new int[] {166, 154, 115, 120, 120, 100}, "dragon", "flying");
        f1.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("extremespeed", "normal", Category.PHYSICAL, 80));
        Battler f2 = mon("Gardevoir", false, 50, new int[] {143, 85, 85, 145, 135, 100}, "psychic", "fairy");
        f2.moves = List.of(mv("moonblast", "fairy", Category.SPECIAL, 95), mv("dazzlinggleam", "fairy", Category.SPECIAL, 80, "allAdjacentFoes"));
        BattleState s = state(true, List.of(a, b, c, d), List.of(f1, f2));
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(s.myTeam.get(i), List.of(2, 3));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(s, reqs);
        pass("doubles full turn", label(p.actions.get(0)) + " + " + label(p.actions.get(1)) + " in " + p.micros / 1000 + "ms, notes=" + p.notes);
    }

    static Battler specsTorkoal() {
        Battler me = mon("Torkoal", true, 50, new int[] {145, 105, 160, 105, 90, 40}, "fire");
        me.item = "choicespecs";
        me.ability = "drought";
        me.moves = new ArrayList<>(List.of(mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"),
            mv("earthpower", "ground", Category.SPECIAL, 90), mv("solarbeam", "grass", Category.SPECIAL, 120)));
        return me;
    }

    static void choiceSpecsNeverProtects() {
        Battler me = specsTorkoal();
        Battler foe = mon("Garchomp", false, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        foe.moves = List.of(mv("earthquake", "ground", Category.PHYSICAL, 100));
        Planner.Plan p = singles(me, foe, List.of());
        String got = label(p.actions.get(0));
        if (got.startsWith("protect")) fail("choice specs no protect", got);
        else pass("choice specs no protect", got);
        // Doubles as well
        Battler ally = mon("Venusaur", true, 50, new int[] {155, 102, 103, 120, 120, 100}, "grass", "poison");
        ally.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75));
        Battler f2 = mon("Blastoise", false, 50, new int[] {154, 103, 120, 105, 125, 98}, "water");
        f2.moves = List.of(mv("surf", "water", Category.SPECIAL, 90, "allAdjacent"));
        Planner.Plan d = doubles(List.of(specsTorkoal(), ally), List.of(foe, f2));
        String g0 = label(d.actions.get(0));
        if (g0.startsWith("protect")) fail("choice specs no protect (doubles)", g0);
        else pass("choice specs no protect (doubles)", g0 + " + " + label(d.actions.get(1)));
    }

    static void lockedIntoProtectSwitches() {
        Battler me = specsTorkoal();
        me.lockedMove = "protect";
        me.protectStreak = 1;
        for (MoveInfo m : me.moves) if (!m.id.equals("protect")) m.disabled = true;
        Battler bench = mon("Venusaur", true, 50, new int[] {155, 102, 103, 120, 120, 100}, "grass", "poison");
        bench.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75), mv("sludgebomb", "poison", Category.SPECIAL, 90));
        Battler foe = mon("Blastoise", false, 50, new int[] {154, 103, 120, 105, 125, 98}, "water");
        foe.moves = List.of(mv("surf", "water", Category.SPECIAL, 90));
        check("locked into protect -> switch", singles(me, foe, List.of(bench)), "switch:1");
    }

    static void megaCharizardYUsesSun() {
        Battler me = mon("Charizard", true, 50, new int[] {153, 104, 98, 129, 105, 120}, "fire", "flying");
        me.item = "charizarditey";
        me.moves = List.of(mv("solarbeam", "grass", Category.SPECIAL, 120), mv("flamethrower", "fire", Category.SPECIAL, 90),
            mv("airslash", "flying", Category.SPECIAL, 75));
        Battler mega = me.copy();
        mega.spa = 129 + 60;
        mega.spd = 105 + 10;
        mega.ability = "drought";
        Battler foe = mon("Swampert", false, 50, new int[] {175, 130, 110, 105, 110, 80}, "water", "ground");
        foe.moves = List.of(mv("waterfall", "water", Category.PHYSICAL, 80));
        BattleState st = state(false, List.of(me), List.of(foe));
        Planner.SlotRequest r = request(me, List.of());
        r.canMega = true;
        r.megaForm = mega;
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, List.of(r));
        String got = label(p.actions.get(0));
        if (got.startsWith("solarbeam[mega]")) pass("mega Y solar beam", got + " notes=" + p.notes);
        else fail("mega Y solar beam", got + " " + describe(p));
    }

    static void fakeOutFirstTurnOnly() {
        Battler me = mon("Incineroar", true, 50, new int[] {170, 135, 110, 100, 110, 80}, "fire", "dark");
        MoveInfo fo = mv("fakeout", "normal", Category.PHYSICAL, 40);
        fo.priority = 3;
        me.moves = List.of(fo, mv("flareblitz", "fire", Category.PHYSICAL, 120), mv("knockoff", "dark", Category.PHYSICAL, 65));
        Battler ally = mon("Garchomp", true, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        ally.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"));
        Battler f1 = mon("Weavile", false, 50, new int[] {145, 140, 85, 65, 105, 145}, "dark", "ice");
        f1.moves = List.of(mv("iciclecrash", "ice", Category.PHYSICAL, 85));
        Battler f2 = mon("Dragonite", false, 50, new int[] {166, 154, 115, 120, 120, 100}, "dragon", "flying");
        f2.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80));
        me.turnsActive = 0;
        Planner.Plan first = doubles(List.of(me, ally), List.of(f1, f2));
        me.turnsActive = 2;
        Planner.Plan later = doubles(List.of(me, ally), List.of(f1, f2));
        String a = label(first.actions.get(0)), b = label(later.actions.get(0));
        System.out.println("  [info] turn 1: " + a + " + " + label(first.actions.get(1)) + " | later: " + b);
        if (b.startsWith("fakeout")) fail("fake out only first turn", b);
        else pass("fake out only first turn", "later=" + b);
    }

    static void boostedThreatGetsRespect() {
        // +2 Dragonite outspeeds and OHKOs; our Venusaur should get out rather than click an attack.
        Battler me = mon("Venusaur", true, 50, new int[] {155, 102, 103, 120, 120, 100}, "grass", "poison");
        me.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75), mv("sludgebomb", "poison", Category.SPECIAL, 90));
        Battler bench = mon("Garchomp", true, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        bench.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("earthquake", "ground", Category.PHYSICAL, 100));
        Battler foe = mon("Dragonite", false, 50, new int[] {166, 154, 115, 120, 120, 100}, "dragon", "flying");
        foe.boosts[0] = 2;
        foe.boosts[4] = 2;
        foe.moves = List.of(mv("fly", "flying", Category.PHYSICAL, 90), mv("firepunch", "fire", Category.PHYSICAL, 75));
        foe.moves.get(0).id.length();
        Planner.Plan p = singles(me, foe, List.of(bench));
        System.out.println("  [info] vs +2 Dragonite chose " + label(p.actions.get(0)) + " " + describe(p));
    }

    /**
     * Replay of Battle Tower #17 turn 2: our Venusaur + Torkoal (sun) against Garchomp + Gardevoir.
     * The old engine switched Torkoal into Charizard, which is 4x weak to Garchomp's Stone Edge.
     */
    static void towerReplayNoCharizardIntoRocks() {
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 102, 103, 120, 120, 100}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.moves = List.of(mv("sludgebomb", "poison", Category.SPECIAL, 90), mv("gigadrain", "grass", Category.SPECIAL, 75),
            mv("sleeppowder", "grass", Category.STATUS, 0, "normal"), mv("protect", "normal", Category.STATUS, 0, "self"));
        venu.moves.get(2).accuracy = 0.75;
        Battler tork = specsTorkoal();
        tork.hp = tork.maxHp * 0.9;
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 129, 105, 120}, "fire", "flying");
        zard.item = "charizarditey";
        zard.moves = List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), mv("solarbeam", "grass", Category.SPECIAL, 120),
            mv("airslash", "flying", Category.SPECIAL, 75));
        Battler chomp = mon("Garchomp", true, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        chomp.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"),
            mv("firefang", "fire", Category.PHYSICAL, 65));

        Battler foeChomp = mon("Garchomp", false, 50, new int[] {183, 150, 115, 100, 105, 122}, "dragon", "ground");
        foeChomp.statsExact = false;
        foeChomp.itemUnknown = true;
        MoveInfo stoneEdge = mv("stoneedge", "rock", Category.PHYSICAL, 100);
        stoneEdge.accuracy = 0.8;
        stoneEdge.revealed = false;
        MoveInfo eq = mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent");
        eq.revealed = false;
        foeChomp.moves = List.of(eq, mv("dragonclaw", "dragon", Category.PHYSICAL, 80), stoneEdge);
        Battler gard = mon("Gardevoir", false, 50, new int[] {143, 85, 85, 145, 135, 100}, "psychic", "fairy");
        gard.statsExact = false;
        gard.hp = gard.maxHp * 0.5;
        gard.moves = List.of(mv("moonblast", "fairy", Category.SPECIAL, 95), mv("psychic", "psychic", Category.SPECIAL, 90));

        BattleState st = state(true, List.of(venu, tork, zard, chomp), List.of(foeChomp, gard));
        st.field.weather = "sun";
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of(2, 3));
            r.slot = i;
            reqs.add(r);
        }
        Planner planner = new Planner(new Planner.Options());
        Planner.Plan p = planner.decide(st, reqs);
        if (System.getenv("CBAI_DEBUG") != null) {
            MoveInfo sludge = venu.moves.get(0), erupt = tork.moves.get(1), solar = tork.moves.get(3);
            System.out.println("    sludge@gard + eruption  = " + planner.scoreJoint(st, reqs, List.of(Action.move(true, 0, sludge, false, 1), Action.move(true, 1, erupt, false, -1))));
            System.out.println("    sludge@gard + solar@chomp = " + planner.scoreJoint(st, reqs, List.of(Action.move(true, 0, sludge, false, 1), Action.move(true, 1, solar, false, 0))));
            System.out.println("    protect + solar@chomp   = " + planner.scoreJoint(st, reqs, List.of(Action.move(true, 0, venu.moves.get(3), false, -1), Action.move(true, 1, solar, false, 0))));
            var sim = new com.manueeh.cobbleai.engine.TurnSimulator(false);
            Action eqA = Action.move(false, 0, foeChomp.moves.get(0), true, 0);
            Action psy = Action.move(false, 1, gard.moves.get(1), true, 0);
            for (List<Action> mine : List.of(
                List.of(Action.move(true, 0, sludge, false, 1), Action.move(true, 1, solar, false, 0)),
                List.of(Action.move(true, 0, venu.moves.get(3), false, -1), Action.move(true, 1, solar, false, 0)))) {
                List<Action> all = new ArrayList<>(mine);
                all.add(eqA);
                all.add(psy);
                for (var o : sim.run(st, all)) {
                    BattleState r = o.state();
                    System.out.println("    " + label(mine.get(0)) + "+" + label(mine.get(1)) + " eval=" + String.format("%.3f", com.manueeh.cobbleai.engine.Evaluator.evaluate(r))
                        + " chance=" + String.format("%.3f", r.chanceValue) + " mine=" + r.my(0) + "," + r.my(1) + " opp=" + r.opp(0) + "," + r.opp(1));
                }
            }
            for (int slot = 0; slot < 2; slot++) {
                for (var w : com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, slot, st.oppKind)) {
                    System.out.println("    opp" + slot + " " + label(w.action()) + (w.action().targetSlot >= 0 ? "" : "") + " p=" + String.format("%.2f", w.prob()) + " score=" + String.format("%.2f", w.score()));
                }
            }
        }
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + "  " + describe(p) + " notes=" + p.notes;
        if (a0.equals("switch:2") || a1.equals("switch:2")) fail("tower replay: no Charizard into Stone Edge", line);
        else pass("tower replay: no Charizard into Stone Edge", line);
    }

    static void tower4Turn1() {
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 102, 103, 140, 120, 132}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("sludgebomb", "poison", Category.SPECIAL, 90),
            mv("sleeppowder", "grass", Category.STATUS, 0, "normal"), mv("gigadrain", "grass", Category.SPECIAL, 75));
        venu.moves.get(2).accuracy = 0.75;
        Battler tork = mon("Torkoal", true, 50, new int[] {145, 105, 160, 150, 90, 37}, "fire");
        tork.ability = "drought";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        tork.moves = List.of(hw, mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"), mv("earthpower", "ground", Category.SPECIAL, 90));
        Battler mien = mon("Mienshao", false, 50, new int[] {140, 145, 80, 115, 80, 136}, "fighting");
        mien.statsExact = false;
        mien.itemUnknown = true;
        MoveInfo fakeout = mv("fakeout", "normal", Category.PHYSICAL, 40);
        fakeout.priority = 3;
        mien.moves = List.of(mv("aurasphere", "fighting", Category.SPECIAL, 80), mv("drainpunch", "fighting", Category.PHYSICAL, 75),
            mv("highjumpkick", "fighting", Category.PHYSICAL, 130), fakeout);
        for (MoveInfo m : mien.moves) m.revealed = false;
        Battler chand = mon("Chandelure", false, 50, new int[] {135, 75, 115, 170, 115, 111}, "ghost", "fire");
        chand.statsExact = false;
        chand.item = "airballoon";
        chand.moves = List.of(mv("shadowball", "ghost", Category.SPECIAL, 80), mv("overheat", "fire", Category.SPECIAL, 130));
        for (MoveInfo m : chand.moves) m.revealed = false;
        BattleState st = state(true, List.of(venu, tork), List.of(mien, chand));
        st.field.weather = "sun";
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of());
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        var sim = new com.manueeh.cobbleai.engine.TurnSimulator(false);
        for (int slot = 0; slot < 2; slot++)
            for (var w : com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, slot, st.oppKind))
                System.out.println("    opp" + slot + " " + label(w.action()) + " p=" + String.format("%.2f", w.prob()));
        for (MoveInfo tm : List.of(tork.moves.get(0), tork.moves.get(2))) {
            List<Action> all = new ArrayList<>(List.of(Action.move(true, 0, venu.moves.get(0), false, -1), Action.move(true, 1, tm, false, -1)));
            all.add(com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, 0, st.oppKind).get(0).action());
            all.add(com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, 1, st.oppKind).get(0).action());
            for (var o : sim.run(st, all)) {
                BattleState r = o.state();
                System.out.println("    " + tm.id + " eval=" + String.format("%.3f", com.manueeh.cobbleai.engine.Evaluator.evaluate(r))
                    + " chance=" + String.format("%.3f", r.chanceValue) + " mine=" + r.my(0) + "," + r.my(1) + " opp=" + r.opp(0) + "," + r.opp(1)
                    + " weather=" + r.field.weather);
            }
        }
        System.out.println("  [info] tower4 T1: " + label(p.actions.get(0)) + " + " + label(p.actions.get(1)) + " " + describe(p) + " notes=" + p.notes);
    }

    static void tower4Turn2() {
        // After Fake Out: Torkoal 42%, both foes revealed. Old engine double-switched Charizard into Chandelure.
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 102, 103, 140, 120, 132}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("sludgebomb", "poison", Category.SPECIAL, 90),
            mv("sleeppowder", "grass", Category.STATUS, 0, "normal"), mv("gigadrain", "grass", Category.SPECIAL, 75));
        venu.moves.get(2).accuracy = 0.75;
        venu.turnsActive = 1;
        Battler tork = mon("Torkoal", true, 50, new int[] {145, 105, 160, 150, 90, 37}, "fire");
        tork.ability = "drought";
        tork.hp = tork.maxHp * 0.42;
        tork.turnsActive = 1;
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        tork.moves = List.of(hw, mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"), mv("earthpower", "ground", Category.SPECIAL, 90));
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 129, 105, 152}, "fire", "flying");
        zard.moves = List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), mv("solarbeam", "grass", Category.SPECIAL, 120));
        Battler chomp = mon("Garchomp", true, 50, new int[] {183, 150, 115, 100, 105, 168}, "dragon", "ground");
        chomp.item = "choicescarf";
        chomp.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"),
            mv("stompingtantrum", "ground", Category.PHYSICAL, 75));
        Battler mien = mon("Mienshao", false, 50, new int[] {140, 178, 80, 115, 80, 136}, "fighting");
        mien.statsExact = false;
        mien.itemUnknown = true;
        mien.turnsActive = 1;
        MoveInfo fakeout = mv("fakeout", "normal", Category.PHYSICAL, 40);
        fakeout.priority = 3;
        MoveInfo cc = mv("closecombat", "fighting", Category.PHYSICAL, 120);
        cc.revealed = false;
        mien.moves = List.of(fakeout, cc, mv("aurasphere", "fighting", Category.SPECIAL, 80));
        Battler chand = mon("Chandelure", false, 50, new int[] {135, 75, 115, 216, 115, 111}, "ghost", "fire");
        chand.statsExact = false;
        chand.item = "airballoon";
        chand.turnsActive = 1;
        MoveInfo sb = mv("shadowball", "ghost", Category.SPECIAL, 80);
        sb.revealed = false;
        chand.moves = List.of(hw, sb);
        BattleState st = state(true, List.of(venu, tork, zard, chomp), List.of(mien, chand));
        st.field.weather = "sun";
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of(2, 3));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + " " + describe(p);
        if (a0.equals("switch:2") || a1.equals("switch:2")) fail("tower4 T2 no Charizard into Chandelure", line);
        else pass("tower4 T2 no Charizard into Chandelure", line);
    }

    // ------------------------------------------------------------------ Wide Guard / sleep / weather

    private static Battler[] spreadDuo(boolean foeHasWideGuard) {
        Battler me = mon("Tyranitar", true, 50, new int[] {175, 154, 130, 95, 120, 81}, "rock", "dark");
        me.moves = List.of(mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"), mv("crunch", "dark", Category.PHYSICAL, 80));
        me.moves.get(0).accuracy = 0.9;
        Battler ally = mon("Amoonguss", true, 50, new int[] {189, 90, 90, 105, 100, 50}, "grass", "poison");
        ally.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75));
        Battler f1 = mon("Charizard", false, 50, new int[] {260, 104, 190, 129, 190, 120}, "fire", "flying");
        MoveInfo wg = mv("wideguard", "rock", Category.STATUS, 0, "allyTeam");
        wg.priority = 3;
        f1.moves = foeHasWideGuard ? List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), wg)
            : List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"));
        Battler f2 = mon("Talonflame", false, 50, new int[] {240, 101, 170, 94, 170, 146}, "fire", "flying");
        f2.moves = List.of(mv("bravebird", "flying", Category.PHYSICAL, 120));
        return new Battler[] {me, ally, f1, f2};
    }

    static void wideGuardKnownAvoidsSpread() {
        Battler[] b = spreadDuo(true);
        b[2].wideGuardUses = 2; // it already used Wide Guard in this battle
        Planner.Plan p = doubles(List.of(b[0], b[1]), List.of(b[2], b[3]));
        String got = label(p.actions.get(0));
        if (got.startsWith("rockslide")) fail("wide guard known -> no spread loop", got + " " + describe(p));
        else pass("wide guard known -> no spread loop", got + " + " + label(p.actions.get(1)));
    }

    static void wideGuardOnCooldownSpreadsAgain() {
        Battler[] b = spreadDuo(true);
        b[2].wideGuardUses = 1;
        b[2].protectStreak = 1;
        b[2].lastMove = "wideguard";
        b[2].lastMoveStreak = 1;
        // Wide Guard does not fail when repeated (a Tower Hitmontop kept it up 7 turns): keep avoiding spread.
        Planner.Plan p = doubles(List.of(b[0], b[1]), List.of(b[2], b[3]));
        String got = label(p.actions.get(0));
        if (!got.startsWith("rockslide")) pass("wide guard repeated -> still no spread", got);
        else fail("wide guard repeated -> still no spread", got + " " + describe(p));
    }

    static void stalledSpreadIsPenalised() {
        // No Wide Guard known at all, but the spread move already whiffed three turns in a row.
        Battler[] b = spreadDuo(false);
        BattleState s = state(true, List.of(b[0], b[1]), List.of(b[2], b[3]));
        s.stallTurns = 3;
        s.lastMoveIds.add("0:rockslide");
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(s.myTeam.get(i), List.of());
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(s, reqs);
        String got = label(p.actions.get(0));
        if (got.startsWith("rockslide")) fail("stalled spread move is penalised", got + " " + describe(p));
        else pass("stalled spread move is penalised", got);
    }

    static void asleepUsesSleepTalk() {
        Battler me = mon("Snorlax", true, 50, new int[] {235, 130, 85, 85, 130, 50}, "normal");
        me.moves = List.of(mv("sleeptalk", "normal", Category.STATUS, 0, "self"), mv("bodyslam", "normal", Category.PHYSICAL, 85),
            mv("earthquake", "ground", Category.PHYSICAL, 100), mv("crunch", "dark", Category.PHYSICAL, 80));
        me.status = "slp";
        me.sleepTurns = 2;
        Battler foe = mon("Blissey", false, 50, new int[] {325, 25, 30, 55, 125, 70}, "normal");
        foe.moves = List.of(mv("seismictoss", "fighting", Category.PHYSICAL, 1));
        check("asleep -> Sleep Talk", singles(me, foe, List.of()), "sleeptalk");
    }

    static void asleepBadMatchupSwitches() {
        Battler me = mon("Venusaur", true, 50, new int[] {155, 102, 103, 120, 120, 80}, "grass", "poison");
        me.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75), mv("sludgebomb", "poison", Category.SPECIAL, 90));
        me.status = "slp";
        me.sleepTurns = 2;
        Battler bench = mon("Blastoise", true, 50, new int[] {154, 103, 120, 105, 125, 98}, "water");
        bench.moves = List.of(mv("surf", "water", Category.SPECIAL, 90), mv("icebeam", "ice", Category.SPECIAL, 90));
        Battler foe = mon("Arcanine", false, 50, new int[] {165, 130, 100, 120, 100, 115}, "fire");
        foe.moves = List.of(mv("flareblitz", "fire", Category.PHYSICAL, 120));
        check("asleep in bad matchup -> switch", singles(me, foe, List.of(bench)), "switch:1");
    }

    static void weatherValueScalesWithTurns() {
        Battler charizard = mon("Charizard", true, 50, new int[] {153, 104, 98, 129, 105, 120}, "fire", "flying");
        charizard.moves = List.of(mv("flamethrower", "fire", Category.SPECIAL, 90), mv("solarbeam", "grass", Category.SPECIAL, 120));
        Battler venusaur = mon("Venusaur", true, 50, new int[] {155, 102, 103, 120, 120, 100}, "grass", "poison");
        venusaur.ability = "chlorophyll";
        venusaur.moves = List.of(mv("gigadrain", "grass", Category.SPECIAL, 75));
        Battler swampert = mon("Swampert", false, 50, new int[] {175, 130, 110, 105, 110, 80}, "water", "ground");
        swampert.moves = List.of(mv("surf", "water", Category.SPECIAL, 90));
        BattleState s = state(true, List.of(charizard, venusaur), List.of(swampert));
        s.field.weather = "sun";
        s.field.weatherTurns = 4;
        double sun4 = Evaluator.weatherValue(s);
        s.field.weatherTurns = 1;
        double sun1 = Evaluator.weatherValue(s);
        s.field.weather = "rain";
        s.field.weatherTurns = 4;
        double rain4 = Evaluator.weatherValue(s);
        String d = String.format(java.util.Locale.ROOT, "sun4=%.3f sun1=%.3f rain4=%.3f", sun4, sun1, rain4);
        if (sun4 > sun1 && sun1 > 0 && rain4 < 0) pass("weather value follows the team and the turns left", d);
        else fail("weather value follows the team and the turns left", d);
    }

    static void sunTeamKeepsSunnyMove() {
        // Rain is up and the sun team has Sunny Day: taking the weather back beats a neutral poke.
        Battler me = mon("Ninetales", true, 50, new int[] {143, 76, 88, 100, 120, 145}, "fire");
        me.moves = List.of(mv("sunnyday", "fire", Category.STATUS, 0, "all"), mv("flamethrower", "fire", Category.SPECIAL, 90));
        Battler ally = mon("Venusaur", true, 50, new int[] {155, 102, 103, 120, 120, 100}, "grass", "poison");
        ally.ability = "chlorophyll";
        ally.moves = List.of(mv("solarbeam", "grass", Category.SPECIAL, 120), mv("gigadrain", "grass", Category.SPECIAL, 75));
        Battler foe = mon("Blissey", false, 50, new int[] {325, 25, 30, 55, 125, 70}, "normal");
        foe.moves = List.of(mv("seismictoss", "fighting", Category.PHYSICAL, 1));
        BattleState st = state(false, List.of(me, ally), List.of(foe));
        st.field.weather = "rain";
        st.field.weatherTurns = 4;
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, List.of(request(me, List.of(1))));
        String got = label(p.actions.get(0));
        if (got.startsWith("sunnyday") || got.startsWith("switch:1")) pass("sun team retakes the weather", got + " " + describe(p));
        else fail("sun team retakes the weather", got + " " + describe(p));
    }

    static void wideGuardSpam() {
        // Ninja Tsubaki: Hitmontop keeps using Wide Guard, Dragonite sits at 3%.
        Battler zard = mon("Charizard", true, 50, new int[] {153, 111, 98, 232, 135, 152}, "fire", "flying");
        zard.ability = "drought";
        MoveInfo fb = mv("focusblast", "fighting", Category.SPECIAL, 120);
        fb.accuracy = 0.7;
        zard.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("solarbeam", "grass", Category.SPECIAL, 120),
            mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), fb);
        zard.moves.get(2).accuracy = 0.9;
        zard.turnsActive = 1;
        Battler tork = specsTorkoal();
        tork.lockedMove = "heatwave";
        tork.moves = new ArrayList<>(List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"),
            mv("protect", "normal", Category.STATUS, 0, "self"), mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"),
            mv("earthpower", "ground", Category.SPECIAL, 90)));
        for (int i = 1; i < 4; i++) tork.moves.get(i).disabled = true;
        tork.turnsActive = 3;
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 102, 103, 167, 120, 132}, "grass", "poison");
        venu.moves = List.of(mv("sludgebomb", "poison", Category.SPECIAL, 90), mv("gigadrain", "grass", Category.SPECIAL, 75));
        Battler top = mon("Hitmontop", false, 50, new int[] {125, 161, 115, 55, 130, 101}, "fighting");
        top.statsExact = false;
        top.turnsActive = 3;
        top.lastMove = "wideguard";
        top.lastMoveStreak = 2;
        MoveInfo wg = mv("wideguard", "rock", Category.STATUS, 0, "allySide");
        wg.priority = 3;
        top.moves = List.of(wg, mv("closecombat", "fighting", Category.PHYSICAL, 120), mv("fakeout", "normal", Category.PHYSICAL, 40));
        Battler nite = mon("Dragonite", false, 50, new int[] {166, 204, 115, 120, 120, 111}, "dragon", "flying");
        nite.statsExact = false;
        nite.hp = nite.maxHp * 0.03;
        nite.turnsActive = 3;
        nite.moves = List.of(mv("icespinner", "ice", Category.PHYSICAL, 80), mv("outrage", "dragon", Category.PHYSICAL, 120));
        BattleState st = state(true, List.of(zard, tork, venu), List.of(top, nite));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of(2));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        if (System.getenv("CBAI_DEBUG") != null) {
            for (int slot = 0; slot < 2; slot++)
                for (var w : com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, slot, st.oppKind))
                    System.out.println("    opp" + slot + " " + label(w.action()) + " p=" + String.format("%.2f", w.prob()));
            var sim = new com.manueeh.cobbleai.engine.TurnSimulator(false);
            for (Action mine0 : List.of(Action.move(true, 0, zard.moves.get(1), false, 1), Action.move(true, 0, zard.moves.get(0), false, -1))) {
                List<Action> all = new ArrayList<>(List.of(mine0, Action.move(true, 1, tork.moves.get(0), false, -1)));
                all.add(com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, 0, st.oppKind).get(0).action());
                all.add(com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, 1, st.oppKind).get(0).action());
                for (var o : sim.run(st, all)) {
                    BattleState r = o.state();
                    System.out.println("    " + label(mine0) + " eval=" + String.format("%.3f", com.manueeh.cobbleai.engine.Evaluator.evaluate(r))
                        + " chance=" + String.format("%.3f", r.chanceValue) + " mine=" + r.my(0) + "," + r.my(1) + " opp=" + r.opp(0) + "," + r.opp(1));
                }
            }
        }
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + " " + describe(p);
        if (a0.startsWith("heatwave")) fail("wide guard: no spread spam", line);
        else pass("wide guard: no spread spam", line);
    }

    static void noFreeSetupTurnInSingles() {
        // Estudiante Lucia: Protect on turn 1 handed Garchomp a free Swords Dance.
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 177, 105, 152}, "fire", "flying");
        zard.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("solarbeam", "grass", Category.SPECIAL, 120),
            mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), mv("airslash", "flying", Category.SPECIAL, 75));
        zard.item = "charizarditey";
        Battler mega = zard.copy();
        mega.spa = 232;
        mega.spd = 135;
        mega.ability = "drought";
        Battler chomp = mon("Garchomp", false, 50, new int[] {183, 182, 115, 90, 105, 122}, "dragon", "ground");
        chomp.statsExact = false;
        chomp.itemUnknown = true;
        MoveInfo sd = mv("swordsdance", "normal", Category.STATUS, 0, "self");
        sd.revealed = false;
        MoveInfo scale = mv("scaleshot", "dragon", Category.PHYSICAL, 25);
        scale.revealed = false;
        MoveInfo eq = mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent");
        eq.revealed = false;
        MoveInfo se = mv("stoneedge", "rock", Category.PHYSICAL, 100);
        se.accuracy = 0.8;
        se.revealed = false;
        chomp.moves = List.of(eq, scale, se, sd);
        BattleState st = state(false, List.of(zard), List.of(chomp));
        Planner.SlotRequest r = request(zard, List.of());
        r.canMega = true;
        r.megaForm = mega;
        zard.pendingMega = mega;
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, List.of(r));
        String got = label(p.actions.get(0));
        if (got.startsWith("protect")) fail("singles: no free setup turn", got + " " + describe(p));
        else pass("singles: no free setup turn", got + " " + describe(p));
    }

    static Planner.Plan megaTimingCase(String weather, int turns) {
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 177, 105, 152}, "fire", "flying");
        zard.item = "charizarditey";
        zard.ability = "solarpower";
        zard.moves = List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), mv("solarbeam", "grass", Category.SPECIAL, 120),
            mv("airslash", "flying", Category.SPECIAL, 75));
        zard.turnsActive = 1;
        Battler mega = zard.copy();
        mega.spa = 232;
        mega.spd = 135;
        mega.ability = "drought";
        zard.pendingMega = mega;
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 91, 103, 167, 120, 132}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.moves = List.of(mv("sludgebomb", "poison", Category.SPECIAL, 90), mv("gigadrain", "grass", Category.SPECIAL, 75));
        venu.turnsActive = 1;
        Battler f1 = mon("Snorlax", false, 50, new int[] {235, 130, 85, 85, 130, 50}, "normal");
        f1.moves = List.of(mv("bodyslam", "normal", Category.PHYSICAL, 85));
        Battler f2 = mon("Swampert", false, 50, new int[] {175, 130, 110, 105, 110, 80}, "water", "ground");
        f2.moves = List.of(mv("waterfall", "water", Category.PHYSICAL, 80));
        BattleState st = state(true, List.of(zard, venu), List.of(f1, f2));
        st.field.weather = weather;
        st.field.weatherTurns = turns;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of());
            r.slot = i;
            if (i == 0) {
                r.canMega = true;
                r.megaForm = mega;
            }
            reqs.add(r);
        }
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    static void megaTimingWithSun() {
        Planner.Plan noSun = megaTimingCase(null, 0);
        Planner.Plan sunLong = megaTimingCase("sun", 4);
        String a = label(noSun.actions.get(0)), b = label(sunLong.actions.get(0));
        Planner.Plan sunEnding = megaTimingCase("sun", 1);
        String c = label(sunEnding.actions.get(0));
        System.out.println("  [info] mega timing: no sun -> " + a + " | sun 4 turns left -> " + b + " | sun ends this turn -> " + c
            + " " + describe(sunEnding));
        if (a.contains("[mega]")) pass("mega at once when there is no sun (Drought sets it)", a);
        else fail("mega at once when there is no sun (Drought sets it)", a + " " + describe(noSun));
    }

    static void rubenTurn1() {
        // Jardinero Ruben: Garchomp + Whimsicott. Old engine swapped Torkoal for Charizard (Rock Slide 4x).
        Battler tork = specsTorkoal();
        tork.turnsActive = 0;
        tork.moves = new ArrayList<>(List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"),
            mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"), mv("earthpower", "ground", Category.SPECIAL, 90)));
        tork.moves.get(0).accuracy = 0.9;
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 91, 103, 167, 120, 132}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.item = "lifeorb";
        venu.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("sludgebomb", "poison", Category.SPECIAL, 90),
            mv("gigadrain", "grass", Category.SPECIAL, 75));
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 177, 105, 152}, "fire", "flying");
        zard.moves = List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), mv("solarbeam", "grass", Category.SPECIAL, 120));
        Battler chomp = mon("Garchomp", true, 50, new int[] {183, 182, 115, 90, 105, 168}, "dragon", "ground");
        chomp.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80));
        Battler whim = mon("Whimsicott", false, 50, new int[] {135, 98, 105, 141, 95, 147}, "grass", "fairy");
        whim.statsExact = false;
        whim.itemUnknown = true;
        whim.possibleAbilities = List.of("prankster", "infiltrator", "chlorophyll");
        MoveInfo tw = mv("tailwind", "flying", Category.STATUS, 0, "allySide");
        tw.revealed = false;
        MoveInfo moon = mv("moonblast", "fairy", Category.SPECIAL, 95);
        moon.revealed = false;
        whim.moves = List.of(moon, tw, mv("protect", "normal", Category.STATUS, 0, "self"));
        Battler gchomp = mon("Garchomp", false, 50, new int[] {183, 200, 115, 111, 105, 133}, "dragon", "ground");
        gchomp.statsExact = false;
        gchomp.itemUnknown = true;
        MoveInfo eq = mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent");
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        MoveInfo dc = mv("dragonclaw", "dragon", Category.PHYSICAL, 80);
        MoveInfo ff = mv("firefang", "fire", Category.PHYSICAL, 65);
        // Rock Slide was shown on an earlier turn of that fight: switching Charizard into it is the blunder.
        for (MoveInfo m : List.of(eq, dc, ff)) m.revealed = false;
        gchomp.moves = List.of(eq, rs, dc, ff);
        BattleState st = state(true, List.of(venu, tork, zard, chomp), List.of(whim, gchomp));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of(2, 3));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + " " + describe(p);
        for (int k = 0; k < 2; k++) for (var w : com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, k, st.oppKind)) line += " | opp" + k + " " + label(w.action()) + String.format(" %.2f", w.prob());
        if (a0.equals("switch:2") || a1.equals("switch:2")) fail("ruben T1: no Charizard into Rock Slide", line);
        else pass("ruben T1: no Charizard into Rock Slide", line);
    }

    static void borisTailwindTurn1() {
        // Estratega Boris: Prankster Tailwind lets Gardevoir outspeed sun Venusaur and Psychic it.
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 91, 103, 167, 120, 132}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.item = "lifeorb";
        venu.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("sludgebomb", "poison", Category.SPECIAL, 90),
            mv("gigadrain", "grass", Category.SPECIAL, 75));
        Battler tork = mon("Torkoal", true, 50, new int[] {145, 89, 160, 147, 90, 37}, "fire");
        tork.ability = "drought";
        tork.item = "charcoal";
        tork.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"),
            mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), mv("earthpower", "ground", Category.SPECIAL, 90));
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 177, 105, 152}, "fire", "flying");
        zard.moves = List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"));
        Battler whim = mon("Whimsicott", false, 50, new int[] {135, 98, 105, 141, 95, 184}, "grass", "fairy");
        whim.statsExact = false;
        whim.possibleAbilities = List.of("prankster");
        MoveInfo tw = mv("tailwind", "flying", Category.STATUS, 0, "allySide");
        tw.revealed = false;
        MoveInfo moon = mv("moonblast", "fairy", Category.SPECIAL, 95);
        moon.revealed = false;
        whim.moves = List.of(moon, tw, mv("protect", "normal", Category.STATUS, 0, "self"));
        Battler gard = mon("Gardevoir", false, 50, new int[] {143, 85, 85, 216, 135, 145}, "psychic", "fairy");
        gard.statsExact = false;
        MoveInfo psy = mv("psychic", "psychic", Category.SPECIAL, 90);
        psy.revealed = false;
        MoveInfo mb = mv("moonblast", "fairy", Category.SPECIAL, 95);
        mb.revealed = false;
        gard.moves = List.of(psy, mb);
        BattleState st = state(true, List.of(venu, tork, zard), List.of(gard, whim));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of(2));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + " " + describe(p);
        boolean bad = a1.startsWith("protect");
        if (bad) fail("boris T1: Torkoal attacks with fresh sun", line);
        else pass("boris T1: Torkoal attacks with fresh sun", line);
    }

    static void borisRememberedTurn1() {
        // Same lead, but the moves are remembered from earlier battles (Mega Gardevoir, Tailwind Whimsicott).
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 91, 103, 167, 120, 132}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.item = "lifeorb";
        venu.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("sludgebomb", "poison", Category.SPECIAL, 90),
            mv("gigadrain", "grass", Category.SPECIAL, 75), mv("sleeppowder", "grass", Category.STATUS, 0, "normal"));
        Battler tork = mon("Torkoal", true, 50, new int[] {145, 89, 160, 147, 90, 37}, "fire");
        tork.ability = "drought";
        tork.item = "charcoal";
        tork.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("eruption", "fire", Category.SPECIAL, 150, "allAdjacentFoes"),
            mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"), mv("earthpower", "ground", Category.SPECIAL, 90));
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 177, 105, 152}, "fire", "flying");
        zard.moves = List.of(mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes"));
        Battler chomp = mon("Garchomp", true, 50, new int[] {183, 182, 115, 90, 105, 168}, "dragon", "ground");
        chomp.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80));
        Battler whim = mon("Whimsicott", false, 50, new int[] {135, 98, 105, 141, 95, 184}, "grass", "fairy");
        whim.statsExact = false;
        whim.ability = "prankster";
        whim.protectUses = 2;
        whim.moves = List.of(mv("tailwind", "flying", Category.STATUS, 0, "allySide"), mv("protect", "normal", Category.STATUS, 0, "self"),
            mv("moonblast", "fairy", Category.SPECIAL, 95));
        Battler gard = mon("Gardevoir", false, 50, new int[] {143, 85, 85, 238, 135, 167}, "psychic", "fairy");
        gard.statsExact = false;
        gard.ability = "pixilate";
        gard.moves = List.of(mv("psychic", "psychic", Category.SPECIAL, 90), mv("hypervoice", "normal", Category.SPECIAL, 90, "allAdjacentFoes"),
            mv("protect", "normal", Category.STATUS, 0, "self"));
        BattleState st = state(true, List.of(venu, tork, zard, chomp), List.of(gard, whim));
        st.field.weather = "sun";
        st.field.weatherTurns = 5;
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of(2, 3));
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + " " + describe(p);
        for (int slot = 0; slot < 2; slot++)
            for (var w : com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, slot, st.oppKind))
                line += " | opp" + slot + " " + label(w.action()) + " " + String.format("%.2f", w.prob());
        if (System.getenv("CBAI_DEBUG") != null) {
            var sim = new com.manueeh.cobbleai.engine.TurnSimulator(false);
            Action tw = com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, 1, st.oppKind).get(0).action();
            Action psy = Action.move(false, 0, gard.moves.get(0), true, 0);
            for (MoveInfo vm : List.of(venu.moves.get(1), venu.moves.get(0))) {
                List<Action> all = new ArrayList<>(List.of(Action.move(true, 0, vm, false, vm.id.equals("protect") ? -1 : 0),
                    Action.move(true, 1, tork.moves.get(1), false, -1), tw, psy));
                for (var o : sim.run(st, all)) {
                    BattleState r = o.state();
                    System.out.println("    " + vm.id + " p=" + String.format("%.2f", o.prob()) + " eval=" + String.format("%.3f", com.manueeh.cobbleai.engine.Evaluator.evaluate(r))
                        + " chance=" + String.format("%.3f", r.chanceValue) + " mine=" + r.my(0) + "," + r.my(1) + " opp=" + r.opp(0) + "," + r.opp(1) + " tw=" + r.field.theirs.tailwind);
                }
            }
        }
        // Venusaur attacking into Tailwind + Psychic just dies: it must protect or leave.
        if (a0.startsWith("sludgebomb") || a0.startsWith("gigadrain") || a0.startsWith("sleeppowder")) fail("boris remembered T1: Venusaur not fed", line);
        else pass("boris remembered T1: Venusaur not fed", line);
    }

    static Battler sleepVenusaur() {
        Battler venu = mon("Venusaur", true, 50, new int[] {155, 91, 103, 167, 120, 132}, "grass", "poison");
        venu.ability = "chlorophyll";
        venu.item = "lifeorb";
        MoveInfo sp = mv("sleeppowder", "grass", Category.STATUS, 0, "normal");
        sp.accuracy = 0.75;
        venu.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("sludgebomb", "poison", Category.SPECIAL, 90),
            sp, mv("gigadrain", "grass", Category.SPECIAL, 75));
        venu.turnsActive = 1;
        return venu;
    }

    static void noSleepOnDoomedTarget() {
        // Log: "Somnifero -> Dusclops (12%) + Garra Dragon -> Dusclops". The partner already finishes it.
        Battler venu = sleepVenusaur();
        Battler chomp = mon("Garchomp", true, 50, new int[] {183, 182, 115, 90, 105, 168}, "dragon", "ground");
        chomp.item = "choicescarf";
        chomp.moves = List.of(mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"));
        chomp.turnsActive = 1;
        Battler dusk = mon("Dusclops", false, 50, new int[] {115, 90, 150, 80, 150, 45}, "ghost");
        dusk.statsExact = false;
        dusk.hp = dusk.maxHp * 0.12;
        dusk.turnsActive = 1;
        dusk.moves = List.of(mv("shadowball", "ghost", Category.SPECIAL, 80), mv("trickroom", "psychic", Category.STATUS, 0, "all"));
        Battler kang = mon("Kangaskhan", false, 50, new int[] {180, 161, 100, 60, 100, 142}, "normal");
        kang.statsExact = false;
        kang.turnsActive = 1;
        kang.moves = List.of(mv("doubleedge", "normal", Category.PHYSICAL, 120), mv("suckerpunch", "dark", Category.PHYSICAL, 70));
        BattleState st = state(true, List.of(venu, chomp), List.of(dusk, kang));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of());
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + " " + describe(p);
        if (a0.equals("sleeppowder@0")) fail("no Sleep Powder on a foe the partner finishes", line);
        else pass("no Sleep Powder on a foe the partner finishes", line);
    }

    static void killBeatsSleep() {
        // Venusaur outspeeds and Sludge Bomb KOs Whimsicott: sleeping it instead wastes the KO.
        Battler venu = sleepVenusaur();
        Battler tork = mon("Torkoal", true, 50, new int[] {145, 89, 160, 147, 90, 37}, "fire");
        tork.moves = List.of(mv("earthpower", "ground", Category.SPECIAL, 90), mv("protect", "normal", Category.STATUS, 0, "self"));
        tork.turnsActive = 1;
        Battler whim = mon("Whimsicott", false, 50, new int[] {135, 98, 105, 141, 95, 184}, "grass", "fairy");
        whim.statsExact = false;
        whim.hp = whim.maxHp * 0.7;
        whim.turnsActive = 1;
        whim.moves = List.of(mv("moonblast", "fairy", Category.SPECIAL, 95), mv("energyball", "grass", Category.SPECIAL, 90));
        Battler rot = mon("Rotom", false, 50, new int[] {125, 76, 127, 172, 127, 106}, "electric", "water");
        rot.statsExact = false;
        rot.turnsActive = 1;
        rot.moves = List.of(mv("hydropump", "water", Category.SPECIAL, 110), mv("voltswitch", "electric", Category.SPECIAL, 70));
        BattleState st = state(true, List.of(venu, tork), List.of(whim, rot));
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of());
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        String a0 = label(p.actions.get(0));
        String line = a0 + " + " + label(p.actions.get(1)) + " " + describe(p);
        if (a0.startsWith("sleeppowder")) fail("Sludge Bomb KO beats Sleep Powder", line);
        else pass("Sludge Bomb KO beats Sleep Powder", line);
    }

    static Planner.Plan ruthCase(double venuHp) {
        Battler venu = sleepVenusaur();
        venu.hp = venu.maxHp * venuHp;
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 177, 105, 152}, "fire", "flying");
        zard.hp = zard.maxHp * 0.42;
        zard.ability = "solarpower";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        zard.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), mv("solarbeam", "grass", Category.SPECIAL, 120), hw);
        zard.turnsActive = 1;
        Battler sala = mon("Salamence", false, 50, new int[] {170, 205, 150, 211, 110, 189}, "dragon", "flying");
        sala.statsExact = false;
        sala.ability = "aerilate";
        sala.hp = sala.maxHp * 0.36;
        sala.turnsActive = 2;
        sala.moves = List.of(mv("hypervoice", "normal", Category.SPECIAL, 90, "allAdjacentFoes"));
        Battler moon = mon("Amoonguss", false, 50, new int[] {221, 105, 90, 105, 100, 61}, "grass", "poison");
        moon.statsExact = false;
        moon.turnsActive = 2;
        MoveInfo spore = mv("spore", "grass", Category.STATUS, 0, "normal");
        moon.moves = List.of(spore, mv("sludgebomb", "poison", Category.SPECIAL, 90), mv("protect", "normal", Category.STATUS, 0, "self"));
        BattleState st = state(true, List.of(venu, zard), List.of(sala, moon));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of());
            r.slot = i;
            reqs.add(r);
        }
        if (System.getenv("CBAI_DEBUG") != null && venuHp > 0.5) {
            var sim = new com.manueeh.cobbleai.engine.TurnSimulator(false);
            for (int slot = 0; slot < 2; slot++)
                for (var w : com.manueeh.cobbleai.engine.OpponentModel.policy(st, false, slot, st.oppKind))
                    System.out.println("    opp" + slot + " " + label(w.action()) + " p=" + String.format("%.2f", w.prob()));
            Action hv = Action.move(false, 0, sala.moves.get(0), true, -1);
            Action sb = Action.move(false, 1, moon.moves.get(1), true, 0);
            for (MoveInfo vm : List.of(venu.moves.get(1), venu.moves.get(0))) {
                List<Action> all = new ArrayList<>(List.of(Action.move(true, 0, vm, false, vm.id.equals("protect") ? -1 : 0),
                    Action.move(true, 1, hw, false, -1), hv, sb));
                for (var o : sim.run(st, all)) {
                    BattleState r = o.state();
                    System.out.println("    " + vm.id + " p=" + String.format("%.2f", o.prob()) + " eval=" + String.format("%.3f", com.manueeh.cobbleai.engine.Evaluator.evaluate(r))
                        + " mine=" + r.my(0) + "," + r.my(1) + " opp=" + r.opp(0) + "," + r.opp(1));
                }
            }
        }
        return new Planner(new Planner.Options()).decide(st, reqs);
    }

    static void ruthSalamenceTurn3() {
        Planner.Plan low = ruthCase(0.10);
        Planner.Plan healthy = ruthCase(0.60);
        String a = label(low.actions.get(0)) + " + " + label(low.actions.get(1));
        String b = label(healthy.actions.get(0)) + " + " + label(healthy.actions.get(1));
        System.out.println("  [info] Ruth T3 Venusaur 10% (Life Orb recoil kills it): " + a + " " + describe(low));
        System.out.println("  [info] Ruth T3 Venusaur 60%: " + b + " " + describe(healthy));
        if (label(healthy.actions.get(0)).startsWith("sludgebomb@0")) pass("healthy sun Venusaur KOs Salamence with Sludge Bomb", b);
        else fail("healthy sun Venusaur KOs Salamence with Sludge Bomb", b + " " + describe(healthy));
    }

    static void perpetuaTurn3() {
        // #28: Landorus 1% asleep for one turn (can wake), Cresselia 74%. Old engine slept Cresselia, Landorus woke
        // and Rock Slid Mega Charizard. Venusaur (faster) must finish Landorus.
        Battler venu = sleepVenusaur();
        venu.hp = venu.maxHp * 0.82;
        venu.boosts[4] = -1;
        venu.ability = "chlorophyll";
        Battler zard = mon("Charizard", true, 50, new int[] {153, 104, 98, 177, 105, 152}, "fire", "flying");
        zard.hp = zard.maxHp * 0.77;
        zard.boosts[4] = -1;
        zard.item = "charizarditey";
        MoveInfo hw = mv("heatwave", "fire", Category.SPECIAL, 95, "allAdjacentFoes");
        hw.accuracy = 0.9;
        zard.moves = List.of(mv("protect", "normal", Category.STATUS, 0, "self"), hw, mv("solarbeam", "grass", Category.SPECIAL, 120));
        zard.turnsActive = 1;
        Battler lando = mon("Landorus", false, 50, new int[] {165, 216, 110, 136, 100, 157}, "ground", "flying");
        lando.statsExact = false;
        lando.hp = lando.maxHp * 0.01;
        lando.status = "slp";
        lando.sleepTurns = 0; // one turn slept already: StateBuilder now plans as if it can wake
        lando.turnsActive = 2;
        MoveInfo rs = mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes");
        rs.accuracy = 0.9;
        rs.revealed = false;
        lando.moves = List.of(mv("stompingtantrum", "ground", Category.PHYSICAL, 75), rs);
        Battler cress = mon("Cresselia", false, 50, new int[] {227, 101, 140, 100, 150, 150}, "psychic");
        cress.statsExact = false;
        cress.hp = cress.maxHp * 0.74;
        cress.turnsActive = 2;
        cress.moves = List.of(mv("helpinghand", "normal", Category.STATUS, 0, "adjacentAlly"), mv("icywind", "ice", Category.SPECIAL, 55, "allAdjacentFoes"));
        BattleState st = state(true, List.of(venu, zard), List.of(lando, cress));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 2;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of());
            r.slot = i;
            if (i == 1) r.canMega = true;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + " " + describe(p);
        boolean venuKills = (a0.startsWith("sludgebomb@0") || a0.startsWith("gigadrain@0"));
        if (venuKills) pass("perpetua T3: Venusaur finishes the 1% Landorus that can wake", line);
        else fail("perpetua T3: Venusaur finishes the 1% Landorus that can wake", line);
    }

    static void melaniaFriendlyFire() {
        // #27 T3: Venusaur 25%, Absol 8%, Amoonguss 100%. Earthquake would finish our own Venusaur.
        Battler venu = sleepVenusaur();
        venu.hp = venu.maxHp * 0.25;
        Battler chomp = mon("Garchomp", true, 50, new int[] {183, 182, 115, 90, 105, 168}, "dragon", "ground");
        chomp.item = "choicescarf";
        chomp.turnsActive = 1;
        chomp.moves = List.of(mv("earthquake", "ground", Category.PHYSICAL, 100, "allAdjacent"), mv("stompingtantrum", "ground", Category.PHYSICAL, 75),
            mv("dragonclaw", "dragon", Category.PHYSICAL, 80), mv("rockslide", "rock", Category.PHYSICAL, 75, "allAdjacentFoes"));
        Battler absol = mon("Absol", false, 50, new int[] {140, 226, 80, 106, 80, 223}, "dark");
        absol.statsExact = false;
        absol.hp = absol.maxHp * 0.08;
        absol.turnsActive = 2;
        absol.moves = List.of(mv("shadowclaw", "ghost", Category.PHYSICAL, 70), mv("xscissor", "bug", Category.PHYSICAL, 80));
        Battler moon = mon("Amoonguss", false, 50, new int[] {221, 105, 90, 105, 100, 61}, "grass", "poison");
        moon.statsExact = false;
        moon.turnsActive = 0;
        moon.moves = List.of(mv("spore", "grass", Category.STATUS, 0, "normal"), mv("sludgebomb", "poison", Category.SPECIAL, 90));
        BattleState st = state(true, List.of(venu, chomp), List.of(absol, moon));
        st.field.weather = "sun";
        st.field.weatherTurns = 3;
        st.oppUnseenReserves = 1;
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Planner.SlotRequest r = request(st.myTeam.get(i), List.of());
            r.slot = i;
            reqs.add(r);
        }
        Planner.Plan p = new Planner(new Planner.Options()).decide(st, reqs);
        String a0 = label(p.actions.get(0)), a1 = label(p.actions.get(1));
        String line = a0 + " + " + a1 + " " + describe(p);
        boolean eqWithoutProtect = a1.startsWith("earthquake") && !a0.startsWith("protect");
        if (eqWithoutProtect) fail("melania T3: no Earthquake through our own 25% Venusaur", line);
        else pass("melania T3: no Earthquake through our own 25% Venusaur", line);
    }

    // ------------------------------------------------------------------ helpers

    static Planner.Plan singles(Battler me, Battler foe, List<Battler> bench) {
        List<Battler> team = new ArrayList<>();
        team.add(me);
        team.addAll(bench);
        BattleState s = state(false, team, List.of(foe));
        List<Integer> sw = new ArrayList<>();
        for (int i = 1; i < team.size(); i++) sw.add(i);
        Planner.Plan p = new Planner(new Planner.Options()).decide(s, List.of(request(me, sw)));
        return p;
    }

    static Planner.Plan doubles(List<Battler> mine, List<Battler> foes) {
        BattleState s = state(true, mine, foes);
        List<Planner.SlotRequest> reqs = new ArrayList<>();
        for (int i = 0; i < mine.size(); i++) {
            Planner.SlotRequest r = request(mine.get(i), List.of());
            r.slot = i;
            reqs.add(r);
        }
        return new Planner(new Planner.Options()).decide(s, reqs);
    }

    static Planner.SlotRequest request(Battler me, List<Integer> switches) {
        Planner.SlotRequest r = new Planner.SlotRequest();
        r.slot = 0;
        for (MoveInfo m : me.moves) r.moves.add(m);
        r.switchOptions = new ArrayList<>(switches);
        return r;
    }

    static BattleState state(boolean doubles, List<Battler> mine, List<Battler> foes) {
        BattleState s = new BattleState();
        s.doubles = doubles;
        s.myTeam.addAll(mine);
        s.oppTeam.addAll(foes);
        int slots = doubles ? 2 : 1;
        s.myActive = new int[slots];
        s.oppActive = new int[slots];
        for (int i = 0; i < slots; i++) {
            s.myActive[i] = i < mine.size() ? i : -1;
            s.oppActive[i] = i < foes.size() ? i : -1;
        }
        s.myControlled = new boolean[slots];
        Arrays.fill(s.myControlled, true);
        s.oppKind = BattleState.OpponentKind.NPC;
        return s;
    }

    static Battler mon(String name, boolean mine, int level, int[] stats, String... types) {
        Battler b = new Battler();
        b.uuid = UUID.randomUUID();
        b.name = name;
        b.species = name.toLowerCase();
        b.mine = mine;
        b.level = level;
        b.maxHp = stats[0];
        b.hp = stats[0];
        b.atk = stats[1];
        b.def = stats[2];
        b.spa = stats[3];
        b.spd = stats[4];
        b.spe = stats[5];
        b.types = types;
        b.baseTypes = types;
        b.statsExact = true;
        b.possibleAbilities = List.of();
        return b;
    }

    static MoveInfo mv(String id, String type, Category cat, double power) {
        String target = cat == Category.STATUS && !id.equals("willowisp") && !id.equals("thunderwave") ? "self" : "normal";
        return mv(id, type, cat, power, target);
    }

    static MoveInfo mv(String id, String type, Category cat, double power, String target) {
        MoveInfo m = new MoveInfo(id);
        m.displayName = id;
        m.type = type;
        m.category = cat;
        m.power = power;
        m.accuracy = 1;
        m.target = target;
        m.pp = 10;
        return m;
    }

    static String label(Action a) {
        return switch (a.kind) {
            case SWITCH -> "switch:" + a.switchTo;
            case PASS -> "pass";
            case MOVE -> a.move.id + (a.gimmick != null ? "[" + a.gimmick + "]" : "") + (a.targetSlot >= 0 ? "@" + a.targetSlot : "");
        };
    }

    static void check(String name, Planner.Plan p, String expected) {
        Action a = p.actions.get(0);
        String got = label(a);
        boolean ok;
        if (expected.startsWith("switch:")) {
            ok = got.equals(expected);
        } else {
            ok = a.kind == Action.Kind.MOVE && a.move.id.equals(expected);
        }
        if (ok) pass(name, got + " (eval " + String.format("%.3f", p.value) + ", " + p.micros + "us)");
        else fail(name, "expected " + expected + " got " + got + " ranking=" + describe(p));
    }

    static String describe(Planner.Plan p) {
        StringBuilder sb = new StringBuilder();
        for (Planner.Scored s : p.ranking) {
            sb.append('[');
            for (Action a : s.actions) sb.append(label(a)).append(' ');
            sb.append(String.format("%.3f", s.value)).append(']');
        }
        return sb.toString();
    }

    static void pass(String name, String detail) {
        System.out.println("PASS " + name + ": " + detail);
    }

    static void fail(String name, String detail) {
        failures++;
        System.out.println("FAIL " + name + ": " + detail);
    }
}
