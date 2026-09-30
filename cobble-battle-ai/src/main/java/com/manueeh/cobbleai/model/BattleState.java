package com.manueeh.cobbleai.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of a battle from the local player's perspective. Teams hold every known Pokémon;
 * active arrays hold indices into them (-1 = empty slot).
 */
public final class BattleState {
    public enum OpponentKind { WILD, NPC, PLAYER }

    public final List<Battler> myTeam = new ArrayList<>();
    public final List<Battler> oppTeam = new ArrayList<>();
    public int[] myActive;
    public int[] oppActive;
    public Field field = new Field();
    public boolean doubles;
    public OpponentKind oppKind = OpponentKind.NPC;
    /** Opponent Pokémon we have not seen yet but expect to exist. */
    public int oppUnseenReserves;
    /** Whether an ally trainer controls the other slot on our side (multi battles). */
    public boolean[] myControlled;
    /**
     * Value (from our perspective) of probabilistic side effects that a single deterministic
     * state cannot represent, e.g. a 30% burn chance. Accumulated by the simulator.
     */
    public double chanceValue;
    /** Follow Me / Rage Powder slot per side this turn (index 0 = ours, 1 = theirs), -1 if none. */
    public int[] redirect = {-1, -1};
    public boolean[] wideGuard = {false, false};
    /** Set by the simulator when speed order changed mid-turn (Tailwind, Trick Room). */
    public boolean speedDirty;
    public boolean[] quickGuard = {false, false};
    /** Consecutive decisions in which our damaging moves left the foes' HP untouched (loop detector). */
    public int stallTurns;
    /** "slot:moveId" of the damaging moves chosen last turn (used together with stallTurns). */
    public final java.util.Set<String> lastMoveIds = new java.util.HashSet<>();

    public BattleState copy() {
        BattleState s = new BattleState();
        for (Battler b : myTeam) s.myTeam.add(b.copy());
        for (Battler b : oppTeam) s.oppTeam.add(b.copy());
        s.myActive = myActive.clone();
        s.oppActive = oppActive.clone();
        s.field = field.copy();
        s.doubles = doubles;
        s.oppKind = oppKind;
        s.oppUnseenReserves = oppUnseenReserves;
        s.myControlled = myControlled == null ? null : myControlled.clone();
        s.chanceValue = chanceValue;
        s.redirect = redirect.clone();
        s.wideGuard = wideGuard.clone();
        s.speedDirty = speedDirty;
        s.quickGuard = quickGuard.clone();
        s.stallTurns = stallTurns;
        s.lastMoveIds.addAll(lastMoveIds);
        return s;
    }

    public Battler my(int slot) {
        int i = slot < myActive.length ? myActive[slot] : -1;
        return i < 0 ? null : myTeam.get(i);
    }

    public Battler opp(int slot) {
        int i = slot < oppActive.length ? oppActive[slot] : -1;
        return i < 0 ? null : oppTeam.get(i);
    }

    public Battler active(boolean mine, int slot) {
        return mine ? my(slot) : opp(slot);
    }

    public int slots() {
        return myActive.length;
    }

    public List<Battler> team(boolean mine) {
        return mine ? myTeam : oppTeam;
    }

    public int[] activeIdx(boolean mine) {
        return mine ? myActive : oppActive;
    }

    public boolean isActive(boolean mine, int teamIndex) {
        for (int i : activeIdx(mine)) if (i == teamIndex) return true;
        return false;
    }

    /** Living, non-active team members that could switch in. */
    public List<Integer> bench(boolean mine) {
        List<Integer> out = new ArrayList<>();
        List<Battler> team = team(mine);
        for (int i = 0; i < team.size(); i++) {
            if (team.get(i).alive() && !isActive(mine, i)) out.add(i);
        }
        return out;
    }

    public int aliveCount(boolean mine) {
        int n = 0;
        for (Battler b : team(mine)) if (b.alive()) n++;
        return n;
    }
}
