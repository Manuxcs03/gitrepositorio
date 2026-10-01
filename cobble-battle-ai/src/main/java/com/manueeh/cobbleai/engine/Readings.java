package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.MoveInfo;

import java.util.Set;

/**
 * Which observed hits can teach the calibrator a Pokemon's real power or bulk. The damage model is run on the
 * board as it was at the start of the turn, so any hit whose power or stats changed during the turn would teach
 * a wrong factor, and that factor is then remembered for the next fights against the same trainer.
 */
public final class Readings {
    private Readings() {}

    /** Power falls with the user's HP: a user hit earlier in the turn deals less than the start-of-turn model says. */
    public static final Set<String> HP_SCALED = Set.of("eruption", "waterspout", "dragonenergy");

    /** Power depends on what else happened this turn or on hidden state (who moved first, a failed move...). */
    public static final Set<String> CONDITIONAL_POWER = Set.of(
        "avalanche", "revenge", "payback", "assurance", "stompingtantrum", "temperflare", "hex", "acrobatics",
        "facade", "knockoff", "brine", "venoshock", "boltbeak", "fishiousrend", "storedpower", "powertrip",
        "punishment", "reversal", "flail", "gyroball", "electroball", "heavyslam", "heatcrash", "grassknot",
        "lowkick", "crushgrip", "wringout", "hardpress", "terablast", "weatherball", "risingvoltage",
        "expandingforce", "grassyglide", "mistyexplosion", "fusionbolt", "fusionflare", "smellingsalts");

    /**
     * True when a hit of {@code m} from {@code attacker} on {@code target} measures their stats: the user still had
     * its start-of-turn HP for HP-scaled moves, the power does not hinge on the turn's events, and no stat stage of
     * either side changed during the turn (Intimidate, Snarl, Icy Wind... land between our snapshot and the hit).
     */
    public static boolean reliable(MoveInfo m, Battler attackerBefore, Battler attackerAfter,
                                   Battler targetBefore, Battler targetAfter) {
        if (m == null || CONDITIONAL_POWER.contains(m.id) || MoveDex.SITUATIONAL.contains(m.id)) return false;
        if (HP_SCALED.contains(m.id)) {
            // We cannot tell whether the user was hit before or after it moved: only an untouched user is sure.
            if (attackerAfter == null || attackerAfter.hp < attackerBefore.hp - 0.5) return false;
        }
        boolean physical = m.category == com.manueeh.cobbleai.model.Category.PHYSICAL;
        int off = physical ? MoveDex.ATK : MoveDex.SPA;
        int def = physical ? MoveDex.DEF : MoveDex.SPD;
        if (attackerAfter != null && attackerAfter.alive() && attackerAfter.boosts[off] != attackerBefore.boosts[off]
            // Draco Meteor, Overheat... lower the user's stat after the hit: that drop is not a mid-turn change.
            && !(SELF_DROP.contains(m.id) && attackerAfter.boosts[off] < attackerBefore.boosts[off])) return false;
        if (targetAfter != null && targetAfter.boosts[def] != targetBefore.boosts[def]) return false;
        return true;
    }

    /** Attacks that lower the user's own attacking stat after they hit. */
    private static final Set<String> SELF_DROP = Set.of("dracometeor", "overheat", "leafstorm", "psychoboost",
        "fleurcannon", "makeitrain", "superpower", "spinout");
}
