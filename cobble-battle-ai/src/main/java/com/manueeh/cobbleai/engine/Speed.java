package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.Field;
import com.manueeh.cobbleai.model.MoveInfo;

/** Turn-order helpers: effective speed and move priority. */
public final class Speed {
    private Speed() {}

    public static double effective(Battler b, Field field) {
        double s = b.spe * DamageCalc.stageMult(b.boosts[MoveDex.SPE]);
        String ab = b.effectiveAbility();
        if ("par".equals(b.status) && !"quickfeet".equals(ab)) s *= 0.5;
        if ("quickfeet".equals(ab) && b.status != null) s *= 1.5;
        if ("choicescarf".equals(b.item)) s *= 1.5;
        if ("ironball".equals(b.item)) s *= 0.5;
        if (ab != null && field != null) {
            if ("swiftswim".equals(ab) && "rain".equals(field.weather)) s *= 2;
            if ("chlorophyll".equals(ab) && "sun".equals(field.weather)) s *= 2;
            if ("sandrush".equals(ab) && "sand".equals(field.weather)) s *= 2;
            if ("slushrush".equals(ab) && "snow".equals(field.weather)) s *= 2;
            if ("surgesurfer".equals(ab) && "electric".equals(field.terrain)) s *= 2;
        }
        if (field != null && field.side(b.mine).tailwind) s *= 2;
        return s;
    }

    public static int priority(Battler user, MoveInfo move, Field field) {
        int p = move.priority;
        // Safety net when the template carries no priority (the real values are +4 and +3).
        if (MoveDex.PROTECT.contains(move.id)) p = Math.max(p, 4);
        if ("wideguard".equals(move.id) || "quickguard".equals(move.id)) p = Math.max(p, 3);
        String ab = user.effectiveAbility();
        // An unconfirmed Prankster still counts on a foe: Tower Whimsicott always had it (#11 Boris: its Tailwind
        // went first, Gardevoir then outsped and KO'd the Venusaur we expected to move before it).
        if (move.category == Category.STATUS && ("prankster".equals(ab)
            || ab == null && !user.mine && user.abilityChance("prankster") > 0)) p += 1;
        if ("galewings".equals(ab) && "flying".equals(move.type) && user.hpFrac() >= 0.999) p += 1;
        if ("triage".equals(ab) && (MoveDex.HEAL.containsKey(move.id) || MoveDex.DRAIN.containsKey(move.id))) p += 3;
        if ("grassyglide".equals(move.id) && field != null && "grassy".equals(field.terrain) && user.isGrounded(field)) p += 1;
        return p;
    }

    /**
     * True when {@code a} surely moves before {@code b} with moves of the same priority: faster, or slower
     * under Trick Room. A speed tie counts as not first.
     */
    public static boolean movesFirst(Battler a, Battler b, Field field) {
        double sa = effective(a, field), sb = effective(b, field);
        if (Math.abs(sa - sb) < 0.5) return false;
        return field != null && field.trickRoom ? sa < sb : sa > sb;
    }

    /**
     * Probability that {@code a} acts before {@code b} given their chosen move priorities.
     * Speed ties are a coin flip.
     */
    public static double firstChance(Battler a, int prioA, Battler b, int prioB, Field field) {
        if (prioA != prioB) return prioA > prioB ? 1 : 0;
        double sa = effective(a, field), sb = effective(b, field);
        if (Math.abs(sa - sb) < 0.5) return 0.5;
        boolean aFaster = sa > sb;
        if (field != null && field.trickRoom) aFaster = !aFaster;
        return aFaster ? 1 : 0;
    }
}
