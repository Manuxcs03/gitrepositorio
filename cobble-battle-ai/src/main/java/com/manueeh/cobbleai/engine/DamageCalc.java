package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.data.AbilityDex;
import com.manueeh.cobbleai.data.ItemDex;
import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.data.TypeChart;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.Field;
import com.manueeh.cobbleai.model.MoveInfo;
import com.manueeh.cobbleai.model.SideState;

/** Generation 9 damage formula with the modifiers that matter for decision making. */
public final class DamageCalc {
    private DamageCalc() {}

    public static final class Result {
        /** Damage range in absolute HP for a hit (all hits of a multi-hit move combined). */
        public double min;
        public double max;
        /** Chance that the move connects at all (accuracy, immunity chances). */
        public double hitChance = 1;
        /** Type effectiveness multiplier (after immunities). */
        public double effectiveness = 1;
        public boolean immune;
        /** False Swipe / Hold Back: never knocks out, whatever the damage. */
        public boolean nonLethal;
        /** Chance the target survives a would-be OHKO at full HP (unrevealed Focus Sash / Sturdy). */
        public double sashRisk;
        public String type;
        public Category category;

        public double avg() {
            return (min + max) / 2.0;
        }

        /** Expected damage including the chance to miss. */
        public double expected() {
            return avg() * hitChance;
        }

        /** Chance to knock out a target with {@code hp} remaining, given the move hits. */
        public double koChanceOnHit(double hp) {
            if (hp <= 0) return 1;
            if (nonLethal) return 0;
            if (max <= 0 || max < hp) return 0;
            double p = min >= hp ? 1 : (max - hp) / Math.max(1e-6, max - min);
            return p * (1 - sashRisk);
        }

        public double koChance(double hp) {
            return koChanceOnHit(hp) * hitChance;
        }

        static Result zero(String type, Category cat, boolean immune) {
            Result r = new Result();
            r.type = type;
            r.category = cat;
            r.immune = immune;
            r.effectiveness = immune ? 0 : 1;
            r.hitChance = immune ? 0 : 1;
            return r;
        }
    }

    public static double stageMult(int stage) {
        stage = Math.max(-6, Math.min(6, stage));
        return stage >= 0 ? (2 + stage) / 2.0 : 2.0 / (2 - stage);
    }

    private static double accStageMult(int stage) {
        stage = Math.max(-6, Math.min(6, stage));
        return stage >= 0 ? (3 + stage) / 3.0 : 3.0 / (3 - stage);
    }

    /** Type the move will actually have when used by {@code att}. */
    public static String effectiveType(Battler att, MoveInfo move, Field field) {
        String t = move.type;
        String ab = att.effectiveAbility();
        switch (move.id) {
            case "weatherball" -> {
                if (field.weather != null) {
                    t = switch (field.weather) {
                        case "rain" -> "water";
                        case "sun" -> "fire";
                        case "sand" -> "rock";
                        case "snow" -> "ice";
                        default -> t;
                    };
                }
            }
            case "terrainpulse" -> {
                if (field.terrain != null && att.isGrounded(field)) t = field.terrain.equals("grassy") ? "grass" : field.terrain;
            }
            case "terablast", "terastarstorm" -> {
                if (att.terastallized && att.teraType != null && !"stellar".equals(att.teraType)) t = att.teraType;
            }
            case "revelationdance" -> t = att.types[0];
            // Arceus / Silvally forms take the plate or memory type: Judgment follows it (#31: Arceus-Water).
            case "judgment", "multiattack" -> {
                String[] base = att.baseTypes != null && att.baseTypes.length > 0 ? att.baseTypes : att.types;
                if (base != null && base.length > 0 && base[0] != null) t = base[0];
            }
            case "technoblast" -> { }
            default -> { }
        }
        if (ab != null && "normal".equals(t)) {
            switch (ab) {
                case "aerilate" -> t = "flying";
                case "pixilate" -> t = "fairy";
                case "refrigerate" -> t = "ice";
                case "galvanize" -> t = "electric";
                default -> { }
            }
        }
        if ("normalize".equals(ab)) t = "normal";
        if ("liquidvoice".equals(ab) && MoveDex.SOUND.contains(move.id)) t = "water";
        return t;
    }

    public static Category effectiveCategory(Battler att, MoveInfo move) {
        boolean compare = "photongeyser".equals(move.id) || "shellsidearm".equals(move.id)
            || ("terablast".equals(move.id) && att.terastallized);
        if (compare) {
            double a = att.atk * stageMult(att.boosts[MoveDex.ATK]);
            double s = att.spa * stageMult(att.boosts[MoveDex.SPA]);
            return a > s ? Category.PHYSICAL : Category.SPECIAL;
        }
        return move.category;
    }

    /** Effectiveness of a type against a defender, honouring special cases. */
    public static double typeEffectiveness(Battler att, MoveInfo move, String type, Battler def, Field field) {
        double eff = 1;
        String ab = att.effectiveAbility();
        boolean scrappy = "scrappy".equals(ab) || "mindseye".equals(ab);
        for (String dt : def.types) {
            double m = TypeChart.single(type, dt);
            if (m == 0 && "ghost".equals(dt) && scrappy && ("normal".equals(type) || "fighting".equals(type))) m = 1;
            if (m == 0 && "flying".equals(dt) && "ground".equals(type)
                && ("thousandarrows".equals(move.id) || (field != null && field.gravity))) m = 1;
            if ("freezedry".equals(move.id) && "water".equals(dt)) m = 2;
            eff *= m;
        }
        if ("flyingpress".equals(move.id)) eff *= TypeChart.against("flying", def.types);
        if ("ground".equals(type) && eff > 0 && !"thousandarrows".equals(move.id)
            && (field == null || !field.gravity) && "airballoon".equals(def.item)) {
            eff = 0;
        }
        return eff;
    }

    private static boolean moldBreaker(Battler att) {
        String ab = att.effectiveAbility();
        return ab != null && AbilityDex.MOLD_BREAKER.contains(ab);
    }

    public static Result calc(Battler att, Battler def, MoveInfo move, Field field, boolean attackerMine,
                              boolean doubles, boolean spread, boolean helpingHand) {
        Category cat = effectiveCategory(att, move);
        String type = effectiveType(att, move, field);
        // Primordial Sea makes Fire attacks fail outright, Desolate Land does the same to Water (#36 Evaristo:
        // Primal Kyogre, and Eruption was still expected to deal 7-10%).
        if (field != null && field.primal && cat != Category.STATUS
            && (("rain".equals(field.weather) && "fire".equals(type)) || ("sun".equals(field.weather) && "water".equals(type))))
            return Result.zero(type, cat, true);
        if (cat == Category.STATUS) return Result.zero(type, cat, false);

        boolean breaker = moldBreaker(att);
        double eff = typeEffectiveness(att, move, type, def, field);
        if (eff == 0) return Result.zero(type, cat, true);

        // Ability-based immunities (probabilistic when the opponent's ability is unknown).
        double abilityBlock = 0;
        if (!breaker) {
            for (var e : AbilityDex.TYPE_IMMUNITY.entrySet()) {
                if (e.getValue().equals(type)) abilityBlock = Math.max(abilityBlock, def.abilityChance(e.getKey()));
            }
            if (eff <= 1) abilityBlock = Math.max(abilityBlock, def.abilityChance("wonderguard"));
            if (MoveDex.SOUND.contains(move.id)) abilityBlock = Math.max(abilityBlock, def.abilityChance("soundproof"));
            if (MoveDex.BALLISTIC.contains(move.id)) abilityBlock = Math.max(abilityBlock, def.abilityChance("bulletproof"));
            if (move.priority > 0 && (def.hasAbility("dazzling") || def.hasAbility("queenlymajesty") || def.hasAbility("armortail")))
                abilityBlock = 1;
        }
        if (abilityBlock >= 1) return Result.zero(type, cat, true);

        // Psychic terrain blocks priority against grounded targets.
        if (field.terrain != null && field.terrain.equals("psychic") && move.priority > 0 && def.isGrounded(field)
            && att.mine != def.mine) {
            return Result.zero(type, cat, true);
        }

        Result r = new Result();
        r.type = type;
        r.category = cat;
        r.effectiveness = eff;

        // ---- Fixed damage moves ----
        Double fixed = fixedDamage(att, def, move);
        if (fixed != null) {
            r.min = r.max = fixed;
            r.hitChance = hitChance(att, def, move, field) * (1 - abilityBlock);
            return r;
        }
        if (MoveDex.OHKO.contains(move.id)) {
            if (def.level > att.level || def.hasAbility("sturdy")) return Result.zero(type, cat, true);
            r.min = r.max = def.hp;
            r.hitChance = Math.min(1, 0.3 + (att.level - def.level) / 100.0) * (1 - abilityBlock);
            return r;
        }

        // Situational moves that simply fail outside their condition.
        switch (move.id) {
            case "synchronoise" -> {
                boolean shared = false;
                for (String t : att.types) shared |= def.hasType(t);
                if (!shared) return Result.zero(type, cat, true);
            }
            case "dreameater" -> { if (!"slp".equals(def.status)) return Result.zero(type, cat, true); }
            case "steelroller" -> { if (field.terrain == null) return Result.zero(type, cat, true); }
            case "burnup" -> { if (!att.hasType("fire")) return Result.zero(type, cat, true); }
            case "doubleshock" -> { if (!att.hasType("electric")) return Result.zero(type, cat, true); }
            case "belch", "lastresort", "spitup", "fling", "naturalgift" -> { return Result.zero(type, cat, true); }
            default -> { }
        }
        double power = power(att, def, move, field, type);
        if (power <= 0) return Result.zero(type, cat, false);
        String attAb = att.effectiveAbility();
        String defAb = breaker ? null : def.effectiveAbility();
        if ("technician".equals(attAb) && power <= 60) power *= 1.5;

        // ---- Attack / defence stats ----
        boolean ignoreAttBoosts = !breaker && def.hasAbility("unaware");
        boolean ignoreDefBoosts = att.hasAbility("unaware") || "chipaway".equals(move.id) || "sacredsword".equals(move.id)
            || "darkestlariat".equals(move.id);
        double a;
        if ("bodypress".equals(move.id)) {
            a = att.def * (ignoreAttBoosts ? 1 : stageMult(att.boosts[MoveDex.DEF]));
        } else if ("foulplay".equals(move.id)) {
            a = def.atk * stageMult(def.boosts[MoveDex.ATK]);
        } else if (cat == Category.PHYSICAL) {
            a = att.atk * (ignoreAttBoosts ? 1 : stageMult(att.boosts[MoveDex.ATK]));
        } else {
            a = att.spa * (ignoreAttBoosts ? 1 : stageMult(att.boosts[MoveDex.SPA]));
        }
        if (cat == Category.PHYSICAL) {
            if ("hugepower".equals(attAb) || "purepower".equals(attAb)) a *= 2;
            if ("gorillatactics".equals(attAb) || "hustle".equals(attAb)) a *= 1.5;
            if ("orichalcumpulse".equals(attAb) && "sun".equals(field.weather)) a *= 1.333;
            if ("choiceband".equals(att.item)) a *= 1.5;
            if ("thickclub".equals(att.item) && att.species != null && (att.species.contains("marowak") || att.species.contains("cubone"))) a *= 2;
        } else {
            if ("solarpower".equals(attAb) && "sun".equals(field.weather)) a *= 1.5;
            if ("hadronengine".equals(attAb) && "electric".equals(field.terrain)) a *= 1.333;
            if ("choicespecs".equals(att.item)) a *= 1.5;
        }
        if ("lightball".equals(att.item) && "pikachu".equals(att.species)) a *= 2;
        if (att.flashFire && "fire".equals(type)) a *= 1.5;
        if ("defeatist".equals(attAb) && att.hpFrac() <= 0.5) a *= 0.5;

        boolean targetsDef = cat == Category.PHYSICAL || "psyshock".equals(move.id) || "psystrike".equals(move.id)
            || "secretsword".equals(move.id);
        double d;
        if (targetsDef) {
            d = def.def * (ignoreDefBoosts ? 1 : stageMult(def.boosts[MoveDex.DEF]));
            if ("snow".equals(field.weather) && def.hasType("ice")) d *= 1.5;
            if ("furcoat".equals(defAb)) d *= 2;
            if ("marvelscale".equals(defAb) && def.status != null) d *= 1.5;
        } else {
            d = def.spd * (ignoreDefBoosts ? 1 : stageMult(def.boosts[MoveDex.SPD]));
            if ("sand".equals(field.weather) && def.hasType("rock")) d *= 1.5;
            if ("assaultvest".equals(def.item)) d *= 1.5;
        }
        if ("eviolite".equals(def.item) && !def.fullyEvolved) d *= 1.5;
        a = Math.max(1, a);
        d = Math.max(1, d);

        double base = Math.floor(Math.floor(Math.floor(2.0 * att.level / 5 + 2) * power * a / d) / 50) + 2;

        // ---- Modifiers ----
        double mod = 1;
        if (spread) mod *= 0.75;
        if ("rain".equals(field.weather)) {
            if ("water".equals(type)) mod *= 1.5;
            else if ("fire".equals(type)) mod *= 0.5;
        } else if ("sun".equals(field.weather)) {
            if ("fire".equals(type) || "hydrosteam".equals(move.id)) mod *= 1.5;
            else if ("water".equals(type)) mod *= 0.5;
        }
        boolean alwaysCrit = "flowertrick".equals(move.id) || "wickedblow".equals(move.id) || "surgingstrikes".equals(move.id)
            || "frostbreath".equals(move.id) || "stormthrow".equals(move.id);
        if (alwaysCrit) {
            mod *= "sniper".equals(attAb) ? 2.25 : 1.5;
        } else if (!def.hasAbility("battlearmor") && !def.hasAbility("shellarmor")) {
            mod *= 1 + 0.5 / 24.0; // expected value of a random crit
        }

        // STAB
        boolean baseStab = contains(att.baseTypes, type);
        boolean teraStab = att.terastallized && type.equals(att.teraType);
        double stab = 1;
        if ("protean".equals(attAb) || "libero".equals(attAb)) stab = 1.5;
        if (baseStab || teraStab) stab = 1.5;
        if (baseStab && teraStab) stab = 2.0;
        if (stab > 1 && "adaptability".equals(attAb)) stab = stab >= 2 ? 2.25 : 2.0;
        mod *= stab;

        mod *= eff;
        if (att.status != null && cat == Category.PHYSICAL) {
            // Guts turns any status into +50% Attack and ignores the burn cut; blend by how likely it is.
            double guts = breakerGuts(att);
            boolean burnCut = "brn".equals(att.status) && !"facade".equals(move.id);
            mod *= guts * 1.5 + (1 - guts) * (burnCut ? 0.5 : 1.0);
        }

        // Screens
        SideState defSide = field.side(def.mine);
        boolean infiltrate = "infiltrator".equals(attAb) || alwaysCrit || MoveDex.BREAKS_SCREENS.contains(move.id);
        if (!infiltrate && (defSide.auroraVeil
            || (cat == Category.PHYSICAL && defSide.reflect) || (cat == Category.SPECIAL && defSide.lightScreen))) {
            mod *= doubles ? 0.667 : 0.5;
        }

        // Defender abilities
        if (defAb != null) {
            if (("multiscale".equals(defAb) || "shadowshield".equals(defAb)) && def.hpFrac() >= 0.999) mod *= 0.5;
            if (("filter".equals(defAb) || "solidrock".equals(defAb) || "prismarmor".equals(defAb)) && eff > 1) mod *= 0.75;
            if ("thickfat".equals(defAb) && ("fire".equals(type) || "ice".equals(type))) mod *= 0.5;
            if ("heatproof".equals(defAb) && "fire".equals(type)) mod *= 0.5;
            if ("waterbubble".equals(defAb) && "fire".equals(type)) mod *= 0.5;
            if ("fluffy".equals(defAb) && "fire".equals(type)) mod *= 2;
            if ("dryskin".equals(defAb) && "fire".equals(type)) mod *= 1.25;
            if ("icescales".equals(defAb) && cat == Category.SPECIAL) mod *= 0.5;
            if ("purifyingsalt".equals(defAb) && "ghost".equals(type)) mod *= 0.5;
            if ("punkrock".equals(defAb) && MoveDex.SOUND.contains(move.id)) mod *= 0.5;
        } else if (!breaker && !def.mine) {
            // Unknown ability: blend the common damage-halving ones by their chance.
            double c = def.abilityChance("thickfat");
            if (c > 0 && ("fire".equals(type) || "ice".equals(type))) mod *= 1 - 0.5 * c;
            c = def.abilityChance("multiscale");
            if (c > 0 && def.hpFrac() >= 0.999) mod *= 1 - 0.5 * c;
        }

        // Attacker abilities
        if (attAb != null) {
            if ("tintedlens".equals(attAb) && eff < 1) mod *= 2;
            if ("sheerforce".equals(attAb) && move.hasSecondary) mod *= 1.3;
            if ("ironfist".equals(attAb) && MoveDex.PUNCH.contains(move.id)) mod *= 1.2;
            if ("strongjaw".equals(attAb) && MoveDex.BITE.contains(move.id)) mod *= 1.5;
            if ("sharpness".equals(attAb) && MoveDex.SLICE.contains(move.id)) mod *= 1.5;
            if ("megalauncher".equals(attAb) && MoveDex.PULSE.contains(move.id)) mod *= 1.5;
            if ("punkrock".equals(attAb) && MoveDex.SOUND.contains(move.id)) mod *= 1.3;
            if ("reckless".equals(attAb) && MoveDex.CONTACT_RECKLESS.contains(move.id)) mod *= 1.2;
            if ("steelworker".equals(attAb) && "steel".equals(type)) mod *= 1.5;
            if ("transistor".equals(attAb) && "electric".equals(type)) mod *= 1.3;
            if ("dragonsmaw".equals(attAb) && "dragon".equals(type)) mod *= 1.5;
            if ("rockypayload".equals(attAb) && "rock".equals(type)) mod *= 1.5;
            if ("waterbubble".equals(attAb) && "water".equals(type)) mod *= 2;
            if ("neuroforce".equals(attAb) && eff > 1) mod *= 1.25;
            if ("sandforce".equals(attAb) && "sand".equals(field.weather)
                && ("rock".equals(type) || "ground".equals(type) || "steel".equals(type))) mod *= 1.3;
            if (att.hpFrac() <= 1.0 / 3) {
                if (("blaze".equals(attAb) && "fire".equals(type)) || ("torrent".equals(attAb) && "water".equals(type))
                    || ("overgrow".equals(attAb) && "grass".equals(type)) || ("swarm".equals(attAb) && "bug".equals(type)))
                    mod *= 1.5;
            }
            if (("aerilate".equals(attAb) || "pixilate".equals(attAb) || "refrigerate".equals(attAb)
                || "galvanize".equals(attAb) || "normalize".equals(attAb)) && "normal".equals(move.type)) mod *= 1.2;
            if ("parentalbond".equals(attAb) && !MoveDex.MULTI_HIT.containsKey(move.id) && !spread) mod *= 1.25;
        }

        // Items
        if (att.item != null) {
            if ("lifeorb".equals(att.item)) mod *= 1.3;
            if ("expertbelt".equals(att.item) && eff > 1) mod *= 1.2;
            if ("muscleband".equals(att.item) && cat == Category.PHYSICAL) mod *= 1.1;
            if ("wiseglasses".equals(att.item) && cat == Category.SPECIAL) mod *= 1.1;
            String boosted = ItemDex.TYPE_BOOST.get(att.item);
            if (type.equals(boosted)) mod *= 1.2;
        }

        // Terrain
        if (field.terrain != null) {
            if (att.isGrounded(field)) {
                if (("electric".equals(field.terrain) && "electric".equals(type))
                    || ("grassy".equals(field.terrain) && "grass".equals(type))
                    || ("psychic".equals(field.terrain) && "psychic".equals(type))) mod *= 1.3;
            }
            if (def.isGrounded(field)) {
                if ("misty".equals(field.terrain) && "dragon".equals(type)) mod *= 0.5;
                if ("grassy".equals(field.terrain) && ("earthquake".equals(move.id) || "bulldoze".equals(move.id)
                    || "magnitude".equals(move.id))) mod *= 0.5;
            }
        }
        if (helpingHand) mod *= 1.5;

        double hits = MoveDex.MULTI_HIT.getOrDefault(move.id, 1.0);
        if (hits > 1) {
            if ("skilllink".equals(attAb) && hits > 3) hits = 5;
            else if ("loadeddice".equals(att.item) && hits > 3) hits = 4.5;
        }

        r.max = Math.floor(base * mod) * hits;
        r.min = Math.floor(base * mod * 0.85) * hits;
        // Estimated stats are uncertain: widen the roll so the planner does not bank on marginal KOs,
        // and treat an estimated attacker as possibly stronger than expected.
        // Symmetric around the estimate: the old one-sided padding (our hits smaller, theirs bigger) made
        // every trade look bad and pushed the planner into Protect far too often (#31: 3 Charizard Protects).
        // Padding for estimated stats (tuned on the Tower replays; a symmetric version made Venusaur stay in
        // front of a repeated Flare Blitz): our hit on an estimated foe may roll lower, theirs higher.
        if (!def.statsExact) r.min *= 0.88;
        if (!att.statsExact) r.max *= 1.12;
        if (def.itemUnknown && hits <= 1 && def.hpFrac() >= 0.999) r.sashRisk = 0.12;

        // Sturdy / Focus Sash keep a full-HP target at 1 HP against single hits.
        if (hits <= 1 && def.hpFrac() >= 0.999) {
            boolean sash = "focussash".equals(def.item) || (!breaker && def.hasAbility("sturdy"));
            if (sash) {
                r.max = Math.min(r.max, def.hp - 1);
                r.min = Math.min(r.min, def.hp - 1);
            }
        }
        r.hitChance = hitChance(att, def, move, field) * (1 - abilityBlock);
        r.nonLethal = MoveDex.NON_LETHAL.contains(move.id);
        return r;
    }

    private static double breakerGuts(Battler att) {
        if (att.ability != null) return "guts".equals(att.ability) ? 1 : 0;
        return att.abilityChance("guts");
    }

    private static boolean contains(String[] arr, String v) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }

    private static Double fixedDamage(Battler att, Battler def, MoveInfo move) {
        return switch (move.id) {
            case "seismictoss", "nightshade" -> (double) att.level;
            case "superfang", "naturesmadness", "ruination" -> Math.max(1, Math.floor(def.hp / 2));
            case "finalgambit" -> att.hp;
            case "endeavor" -> Math.max(0, def.hp - att.hp);
            case "guardianofalola" -> Math.max(1, Math.floor(def.hp * 0.75));
            default -> {
                Integer f = MoveDex.FIXED_DAMAGE.get(move.id);
                yield f == null ? null : f.doubleValue();
            }
        };
    }

    public static double hitChance(Battler att, Battler def, MoveInfo move, Field field) {
        if (att.hasAbility("noguard") || def.hasAbility("noguard")) return 1;
        double acc = move.accuracy;
        if (acc >= 1) return 1;
        if ("snow".equals(field.weather) && "blizzard".equals(move.id)) return 1;
        if (("thunder".equals(move.id) || "hurricane".equals(move.id) || "bleakwindstorm".equals(move.id)
            || "wildboltstorm".equals(move.id) || "sandsearstorm".equals(move.id))) {
            if ("rain".equals(field.weather)) return 1;
            if ("sun".equals(field.weather) && !"bleakwindstorm".equals(move.id)) acc = 0.5;
        }
        if ("toxic".equals(move.id) && att.hasType("poison")) return 1;
        int stage = att.boosts[MoveDex.ACC] - def.boosts[MoveDex.EVA];
        acc *= accStageMult(stage);
        if (att.hasAbility("compoundeyes")) acc *= 1.3;
        if (att.hasAbility("hustle") && move.category == Category.PHYSICAL) acc *= 0.8;
        if ("widelens".equals(att.item)) acc *= 1.1;
        if ("brightpowder".equals(def.item)) acc *= 0.9;
        return Math.max(0, Math.min(1, acc));
    }

    private static double power(Battler att, Battler def, MoveInfo move, Field field, String type) {
        double p = move.power;
        double attSpe = Speed.effective(att, field);
        double defSpe = Speed.effective(def, field);
        switch (move.id) {
            case "eruption", "waterspout", "dragonenergy" -> p = Math.max(1, 150 * att.hpFrac());
            case "reversal", "flail" -> {
                double f = att.hpFrac();
                p = f < 0.042 ? 200 : f < 0.104 ? 150 : f < 0.208 ? 100 : f < 0.354 ? 80 : f < 0.688 ? 40 : 20;
            }
            case "gyroball" -> p = Math.min(150, 25 * defSpe / Math.max(1, attSpe) + 1);
            case "electroball" -> {
                double r = attSpe / Math.max(1, defSpe);
                p = r >= 4 ? 150 : r >= 3 ? 120 : r >= 2 ? 80 : r >= 1 ? 60 : 40;
            }
            case "heavyslam", "heatcrash" -> {
                double r = att.weightKg / Math.max(0.1, def.weightKg);
                p = r >= 5 ? 120 : r >= 4 ? 100 : r >= 3 ? 80 : r >= 2 ? 60 : 40;
            }
            case "grassknot", "lowkick" -> {
                double w = def.weightKg;
                p = w >= 200 ? 120 : w >= 100 ? 100 : w >= 50 ? 80 : w >= 25 ? 60 : w >= 10 ? 40 : 20;
            }
            case "acrobatics" -> { if (att.item == null) p *= 2; }
            case "facade" -> { if (att.status != null && !"slp".equals(att.status) && !"frz".equals(att.status)) p *= 2; }
            case "hex", "infernalparade", "bittermalice" -> { if (def.status != null) p *= 2; }
            case "venoshock", "barbbarrage" -> { if ("psn".equals(def.status) || "tox".equals(def.status)) p *= 2; }
            case "brine" -> { if (def.hpFrac() <= 0.5) p *= 2; }
            case "knockoff" -> {
                if (def.item != null) p *= 1.5;
                else if (!def.mine) p *= 1.25;
            }
            case "storedpower", "powertrip" -> {
                int pos = 0;
                for (int s : att.boosts) if (s > 0) pos += s;
                p = 20 + 20 * pos;
            }
            case "return", "frustration" -> p = 102;
            case "weatherball" -> { if (field.weather != null) p = 100; }
            case "terrainpulse" -> { if (field.terrain != null && att.isGrounded(field)) p = 100; }
            case "solarbeam", "solarblade" -> {
                if (field.weather != null && !"sun".equals(field.weather)) p *= 0.5;
            }
            case "expandingforce" -> { if ("psychic".equals(field.terrain) && att.isGrounded(field)) p *= 1.5; }
            case "risingvoltage" -> { if ("electric".equals(field.terrain) && def.isGrounded(field)) p *= 2; }
            case "mistyexplosion" -> { if ("misty".equals(field.terrain) && att.isGrounded(field)) p *= 1.5; }
            case "psyblade" -> { if ("electric".equals(field.terrain)) p *= 1.5; }
            case "collisioncourse", "electrodrift" -> {
                if (TypeChart.against(type, def.types) > 1) p *= 1.333;
            }
            case "lastrespects", "ragefist" -> p = Math.max(p, 50);
            case "boltbeak", "fishiousrend" -> { if (attSpe > defSpe) p *= 2; }
            case "payback" -> { if (attSpe < defSpe) p *= 2; }
            case "avalanche", "revenge" -> { if (attSpe < defSpe) p *= 1.5; }
            default -> { }
        }
        if (p <= 0 && move.category != Category.STATUS) p = 60;
        return p;
    }
}
