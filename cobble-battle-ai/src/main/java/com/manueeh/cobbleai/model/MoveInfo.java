package com.manueeh.cobbleai.model;

/** A move as the engine sees it: static template data plus per-battle state (pp, disabled, reveal). */
public final class MoveInfo {
    public final String id;
    public String displayName;
    public String type;
    public Category category;
    public double power;
    /** Hit chance 0..1; 1 for moves that never miss. */
    public double accuracy;
    public int priority;
    /** Cobblemon MoveTarget enum name (normal, allAdjacentFoes, self, ...). */
    public String target;
    public int pp = 1;
    public boolean disabled;
    /** For opponents: true once seen in battle; predictions are false. */
    public boolean revealed = true;
    /** Chance of any secondary effect from the template (used for Sheer Force etc). */
    public boolean hasSecondary;
    /** First secondary effect chance, 0..1 (0 when the move has none). */
    public double effectChance;

    public MoveInfo(String id) {
        this.id = id;
    }

    public MoveInfo copy() {
        MoveInfo m = new MoveInfo(id);
        m.displayName = displayName;
        m.type = type;
        m.category = category;
        m.power = power;
        m.accuracy = accuracy;
        m.priority = priority;
        m.target = target;
        m.pp = pp;
        m.disabled = disabled;
        m.revealed = revealed;
        m.hasSecondary = hasSecondary;
        m.effectChance = effectChance;
        return m;
    }

    public boolean isDamaging() {
        return category != Category.STATUS;
    }

    public boolean usable() {
        return !disabled && pp > 0;
    }

    /** Hits both foes (and maybe the ally) in doubles. */
    public boolean isSpread() {
        return "allAdjacentFoes".equals(target) || "allAdjacent".equals(target) || "all".equals(target);
    }

    public boolean hitsAlly() {
        return "allAdjacent".equals(target);
    }

    public boolean needsTargetChoice() {
        return "normal".equals(target) || "any".equals(target) || "adjacentFoe".equals(target);
    }

    @Override
    public String toString() {
        return displayName != null ? displayName : id;
    }
}
