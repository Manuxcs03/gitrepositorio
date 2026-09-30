package com.manueeh.cobbleai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One Pokémon in the engine's model. Own Pokémon carry exact stats; opponents carry
 * estimates derived from base stats + level, refined by what the tracker has seen.
 */
public final class Battler {
    public UUID uuid;
    public String name;
    public String species;
    public boolean mine;
    public int level = 50;

    public String[] types = {"normal"};
    /** Types before Terastallization (STAB keeps applying to these). */
    public String[] baseTypes = {"normal"};
    public String teraType;
    public boolean terastallized;

    public int maxHp = 1;
    public double hp = 1;
    public int atk = 1, def = 1, spa = 1, spd = 1, spe = 1;
    /** ATK, DEF, SPA, SPD, SPE, ACC, EVA stages. */
    public int[] boosts = new int[7];
    /** brn, par, psn, tox, slp, frz or null. */
    public String status;
    public int sleepTurns;

    public String ability;
    /** Candidate abilities when the exact one is unknown (opponents). */
    public List<String> possibleAbilities = new ArrayList<>();
    public String item;
    /** Opponent whose held item has not been revealed (it may be a Focus Sash, etc.). */
    public boolean itemUnknown;
    public List<MoveInfo> moves = new ArrayList<>();

    public boolean statsExact;
    /** Opponent whose speed has been bounded by watching turn order (so it is no longer a pure estimate). */
    public boolean speedKnown;
    public boolean fullyEvolved = true;
    public double weightKg = 50;

    // Volatile state (only meaningful while active)
    public boolean confused;
    public boolean leechSeeded;
    public boolean substitute;
    public boolean taunted;
    public int turnsActive;
    public int protectStreak;
    public int toxicCounter;
    public boolean mustRecharge;
    // Per-turn simulation flags (reset at end of turn)
    public boolean protecting;
    public boolean flinched;
    public boolean helped;
    public boolean acted;
    /**
     * Simulation only: outcome forced for the move being resolved when the simulator splits a doubles
     * turn on an uncertain KO (0 = not forced, 1 = this hit KOs, -1 = it survives).
     */
    public int forcedKo;
    /** Simulation flag: used Wide Guard / Quick Guard this turn (they share the Protect counter). */
    public boolean usedGuard;
    /** Times this Pokemon was seen using Wide Guard / Quick Guard (filled from the tracker). */
    public int wideGuardUses, quickGuardUses;
    public int protectUses;
    /** Choice-locked move id (tracked for opponents that revealed a choice item). */
    public String lockedMove;
    /** Our Pokemon that can still Mega Evolve: its Mega form (the evaluator assumes it will). */
    public Battler pendingMega;
    /** Last move this Pokemon used and how many times in a row (opponent habits). */
    public String lastMove;
    /**
     * Value of a status that may or may not land (Sleep Powder at 75%...), held on the target until the end of
     * the simulated turn: it only counts if the target is still standing then (no sleeping a Pokemon the partner
     * is about to KO).
     */
    public double pendingStatusValue;
    public int lastMoveStreak;
    /** UUID of the Pokemon hit by this battler's last single-target move (trainer AI habit). */
    public java.util.UUID lastTarget;
    /** Flash Fire activated: this battler's Fire moves deal 1.5x. */
    public boolean flashFire;
    /** Yawned: falls asleep at the end of next turn unless it switches out. */
    public boolean drowsy;
    /** Simulator only: fell asleep (or already rolled its wake-up) during the turn being simulated. */
    public boolean sleptThisTurn;
    /** Simulator only: credit for a charging move (Solar Beam out of sun) that lands next turn on {@link #chargeTarget}. */
    public double pendingChargeValue;
    public java.util.UUID chargeTarget;

    public boolean alive() {
        return hp > 0;
    }

    public double hpFrac() {
        return maxHp <= 0 ? 0 : Math.max(0, Math.min(1, hp / maxHp));
    }

    /** Apply Terastallization in-place (Stellar keeps the original typing). */
    public void terastallize() {
        if (teraType == null || terastallized) return;
        terastallized = true;
        if (!"stellar".equals(teraType)) types = new String[] {teraType};
    }

    /** Copy with the Mega form's stats, typing and ability applied (or this if there is none). */
    public Battler asMega() {
        if (pendingMega == null) return this;
        Battler m = copy();
        applyForm(m, pendingMega);
        m.pendingMega = null;
        return m;
    }

    public static void applyForm(Battler target, Battler form) {
        target.types = form.types.clone();
        target.baseTypes = form.baseTypes;
        target.atk = form.atk;
        target.def = form.def;
        target.spa = form.spa;
        target.spd = form.spd;
        target.spe = form.spe;
        target.ability = form.ability;
        target.weightKg = form.weightKg;
    }

    public boolean hasType(String t) {
        for (String s : types) if (s.equals(t)) return true;
        return false;
    }

    /** True when the ability is known or is the only candidate. */
    public boolean hasAbility(String id) {
        if (ability != null) return ability.equals(id);
        return possibleAbilities.size() == 1 && possibleAbilities.get(0).equals(id);
    }

    /** Probability the battler has this ability given current knowledge. */
    public double abilityChance(String id) {
        if (ability != null) return ability.equals(id) ? 1 : 0;
        if (possibleAbilities.isEmpty()) return 0;
        int n = 0;
        for (String a : possibleAbilities) if (a.equals(id)) n++;
        return (double) n / possibleAbilities.size();
    }

    public String effectiveAbility() {
        if (ability != null) return ability;
        return possibleAbilities.size() == 1 ? possibleAbilities.get(0) : null;
    }

    public boolean isGrounded(Field field) {
        if (field != null && field.gravity) return true;
        if (hasType("flying")) return false;
        if (hasAbility("levitate")) return false;
        return !"airballoon".equals(item);
    }

    public Battler copy() {
        Battler b = new Battler();
        b.uuid = uuid;
        b.name = name;
        b.species = species;
        b.mine = mine;
        b.level = level;
        b.types = types.clone();
        b.baseTypes = baseTypes;
        b.teraType = teraType;
        b.terastallized = terastallized;
        b.maxHp = maxHp;
        b.hp = hp;
        b.atk = atk;
        b.def = def;
        b.spa = spa;
        b.spd = spd;
        b.spe = spe;
        b.boosts = boosts.clone();
        b.status = status;
        b.sleepTurns = sleepTurns;
        b.ability = ability;
        b.possibleAbilities = possibleAbilities; // immutable after build
        b.item = item;
        b.itemUnknown = itemUnknown;
        b.moves = moves; // move list is not mutated during simulation
        b.statsExact = statsExact;
        b.speedKnown = speedKnown;
        b.fullyEvolved = fullyEvolved;
        b.weightKg = weightKg;
        b.confused = confused;
        b.leechSeeded = leechSeeded;
        b.substitute = substitute;
        b.taunted = taunted;
        b.turnsActive = turnsActive;
        b.protectStreak = protectStreak;
        b.toxicCounter = toxicCounter;
        b.mustRecharge = mustRecharge;
        b.lockedMove = lockedMove;
        b.pendingMega = pendingMega;
        b.lastMove = lastMove;
        b.lastMoveStreak = lastMoveStreak;
        b.pendingStatusValue = pendingStatusValue;
        b.lastTarget = lastTarget;
        b.flashFire = flashFire;
        b.drowsy = drowsy;
        b.sleptThisTurn = sleptThisTurn;
        b.pendingChargeValue = pendingChargeValue;
        b.chargeTarget = chargeTarget;
        b.protecting = protecting;
        b.flinched = flinched;
        b.helped = helped;
        b.acted = acted;
        b.usedGuard = usedGuard;
        b.wideGuardUses = wideGuardUses;
        b.protectUses = protectUses;
        b.quickGuardUses = quickGuardUses;
        return b;
    }

    /** Clear volatile state when leaving the field. */
    public void onSwitchOut() {
        boosts = new int[7];
        confused = false;
        leechSeeded = false;
        substitute = false;
        taunted = false;
        turnsActive = 0;
        protectStreak = 0;
        lockedMove = null;
        mustRecharge = false;
        flashFire = false;
        drowsy = false;
        if ("tox".equals(status)) toxicCounter = 0;
        if (hasAbility("regenerator") && alive()) hp = Math.min(maxHp, hp + maxHp / 3.0);
        if (hasAbility("naturalcure")) status = null;
    }

    @Override
    public String toString() {
        return name + "(" + Math.round(hpFrac() * 100) + "%)";
    }
}
