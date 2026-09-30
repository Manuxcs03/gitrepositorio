package com.manueeh.cobbleai.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Behavioural knowledge about moves that Cobblemon's MoveTemplate does not expose
 * (type/power/accuracy/priority come from the template; everything else lives here).
 * All keys are Showdown move ids.
 */
public final class MoveDex {
    private MoveDex() {}

    /** Boost array layout used everywhere: ATK, DEF, SPA, SPD, SPE, ACC, EVA. */
    public static final int ATK = 0, DEF = 1, SPA = 2, SPD = 3, SPE = 4, ACC = 5, EVA = 6;

    public static final Map<String, Double> MULTI_HIT = new HashMap<>();
    public static final Map<String, Double> RECOIL = new HashMap<>();
    public static final Map<String, Double> DRAIN = new HashMap<>();
    public static final Map<String, Double> HEAL = new HashMap<>();
    public static final Map<String, int[]> SELF_BOOST = new HashMap<>();
    public static final Map<String, int[]> SELF_DROP = new HashMap<>();
    public static final Map<String, int[]> TARGET_DROP = new HashMap<>();
    public static final Map<String, String> INFLICT_STATUS = new HashMap<>();
    public static final Map<String, String> SECONDARY_STATUS = new HashMap<>();
    public static final Map<String, String> WEATHER_SETTER = new HashMap<>();
    public static final Map<String, String> TERRAIN_SETTER = new HashMap<>();
    public static final Map<String, Integer> FIXED_DAMAGE = new HashMap<>();

    public static final Set<String> SELF_KO = new HashSet<>();
    /** Attacks that always leave the target with at least 1 HP (False Swipe, Hold Back). */
    public static final Set<String> NON_LETHAL = Set.of("falseswipe", "holdback");
    public static final Set<String> CHARGE = new HashSet<>();
    public static final Set<String> RECHARGE = new HashSet<>();
    public static final Set<String> PIVOT = new HashSet<>();
    public static final Set<String> PROTECT = new HashSet<>();
    public static final Set<String> FIRST_TURN_ONLY = new HashSet<>();
    public static final Set<String> FLINCH_ALWAYS = new HashSet<>();
    public static final Set<String> HAZARD = new HashSet<>();
    public static final Set<String> HAZARD_REMOVAL = new HashSet<>();
    public static final Set<String> SCREEN = new HashSet<>();
    public static final Set<String> PHAZE = new HashSet<>();
    public static final Set<String> REDIRECT = new HashSet<>();
    public static final Set<String> OHKO = new HashSet<>();
    public static final Set<String> SOUND = new HashSet<>();
    public static final Set<String> PUNCH = new HashSet<>();
    public static final Set<String> BITE = new HashSet<>();
    public static final Set<String> SLICE = new HashSet<>();
    public static final Set<String> PULSE = new HashSet<>();
    public static final Set<String> POWDER = new HashSet<>();
    public static final Set<String> BALLISTIC = new HashSet<>();
    public static final Set<String> CONTACT_RECKLESS = new HashSet<>();
    /** Physical moves that do NOT make contact (everything else physical does). */
    public static final Set<String> NON_CONTACT_PHYSICAL = new HashSet<>();
    /** Special moves that DO make contact. */
    public static final Set<String> CONTACT_SPECIAL = new HashSet<>();
    public static final Set<String> IGNORE_PROTECT = new HashSet<>();
    public static final Set<String> BREAKS_SCREENS = new HashSet<>();
    /**
     * Moves that only work in special situations (shared type, sleeping target, unknown type...).
     * They must never fill a predicted opponent slot, and the damage model checks their conditions.
     */
    public static final Set<String> SITUATIONAL = new HashSet<>(Set.of(
        "synchronoise", "dreameater", "hiddenpower", "naturalgift", "focuspunch", "lastresort", "belch",
        "spitup", "fling", "steelroller", "burnup", "doubleshock", "snore", "sleeptalk", "wakeupslap",
        "futuresight", "doomdesire", "beatup", "present", "magnitude", "round", "echoedvoice", "furycutter",
        "rollout", "iceball", "retaliate", "lashout", "ragefist", "lastrespects", "shelltrap", "beakblast", "counter", "mirrorcoat",
        "metalburst", "comeuppance", "bide", "skydrop", "geomancy"));
    /** Support moves trained opponents often carry (predicted when in the movepool). */
    public static final String[] SUPPORT_PRIORITY = {"tailwind", "trickroom", "followme", "ragepowder",
        "wideguard", "helpinghand", "icywind", "electroweb", "willowisp", "thunderwave", "taunt", "partingshot"};

    /** Singles counterpart: the status moves a singles set realistically carries. */
    public static final String[] SUPPORT_SINGLES = {"willowisp", "thunderwave"};

    /** Moves that are pointless/harmful for this AI to pick deliberately. */
    public static final Set<String> AVOID = new HashSet<>();

    private static int[] b(int atk, int def, int spa, int spd, int spe) {
        return new int[] {atk, def, spa, spd, spe, 0, 0};
    }

    /** Whether {@code m} makes contact (Rough Skin, Iron Barbs, Rocky Helmet, Flame Body...). */
    public static boolean makesContact(com.manueeh.cobbleai.model.MoveInfo m) {
        if (m == null || m.category == null) return false;
        if (m.category == com.manueeh.cobbleai.model.Category.PHYSICAL) return !NON_CONTACT_PHYSICAL.contains(m.id);
        return CONTACT_SPECIAL.contains(m.id);
    }

    private static void all(Set<String> set, String ids) {
        for (String s : ids.split(" ")) if (!s.isEmpty()) set.add(s);
    }

    static {
        // Multi-hit: expected number of hits (2-5 distribution = 3.1).
        for (String s : ("bulletseed rockblast iciclespear pinmissile tailslap scaleshot armthrust furyswipes "
            + "furyattack doubleslap cometpunch spikecannon barrage watershuriken bonerush").split(" ")) MULTI_HIT.put(s, 3.1);
        for (String s : ("doublekick dualwingbeat dragondarts twineedle doublehit geargrind bonemerang dualchop "
            + "doubleironbash twinbeam tachyoncutter").split(" ")) MULTI_HIT.put(s, 2.0);
        MULTI_HIT.put("surgingstrikes", 3.0);
        MULTI_HIT.put("tripledive", 3.0);
        MULTI_HIT.put("tripleaxel", 2.6);
        MULTI_HIT.put("triplekick", 2.6);
        MULTI_HIT.put("populationbomb", 6.0);

        RECOIL.put("bravebird", 0.33); RECOIL.put("flareblitz", 0.33); RECOIL.put("woodhammer", 0.33);
        RECOIL.put("doubleedge", 0.33); RECOIL.put("volttackle", 0.33); RECOIL.put("wavecrash", 0.33);
        RECOIL.put("headsmash", 0.5); RECOIL.put("lightofruin", 0.5); RECOIL.put("takedown", 0.25);
        RECOIL.put("submission", 0.25); RECOIL.put("wildcharge", 0.25); RECOIL.put("headcharge", 0.25);
        all(CONTACT_RECKLESS, "bravebird flareblitz woodhammer doubleedge volttackle wavecrash headsmash takedown "
            + "submission wildcharge headcharge highjumpkick jumpkick axekick supercellslam");
        all(NON_CONTACT_PHYSICAL, "earthquake bulldoze magnitude rockslide stoneedge rocktomb rockblast rockwrecker "
            + "iciclecrash iciclespear iceshard bulletseed pinmissile poisonsting gunkshot seedbomb bonemerang boneclub "
            + "bonerush precipiceblades thousandarrows thousandwaves explosion selfdestruct mistyexplosion fling beatup "
            + "sacredfire scaleshot diamondstorm dragondarts pyroball gravapple eggbomb barrage spikecannon twineedle "
            + "attackorder magnetbomb smackdown freezeshock iceburn skyattack aeroblast drumbeating glaciallance "
            + "headlongrush saltcure bitterblade tachyoncutter stoneaxe ceaselessedge mountaingale esperwing "
            + "shadowbone hyperspacefury powergem leafage ragingbull");
        all(CONTACT_SPECIAL, "drainingkiss grassknot petaldance trumpcard wringout infestation electrodrift");

        DRAIN.put("gigadrain", 0.5); DRAIN.put("drainpunch", 0.5); DRAIN.put("hornleech", 0.5);
        DRAIN.put("leechlife", 0.5); DRAIN.put("paraboliccharge", 0.5); DRAIN.put("drainingkiss", 0.75);
        DRAIN.put("oblivionwing", 0.75); DRAIN.put("bitterblade", 0.5); DRAIN.put("absorb", 0.5);
        DRAIN.put("megadrain", 0.5); DRAIN.put("matchagotcha", 0.5);

        HEAL.put("recover", 0.5); HEAL.put("roost", 0.5); HEAL.put("softboiled", 0.5); HEAL.put("slackoff", 0.5);
        HEAL.put("milkdrink", 0.5); HEAL.put("healorder", 0.5); HEAL.put("shoreup", 0.5); HEAL.put("moonlight", 0.5);
        HEAL.put("morningsun", 0.5); HEAL.put("synthesis", 0.5); HEAL.put("lifedew", 0.25); HEAL.put("junglehealing", 0.25);
        HEAL.put("lunarblessing", 0.25); HEAL.put("wish", 0.5); HEAL.put("rest", 1.0); HEAL.put("strengthsap", 0.4);

        SELF_BOOST.put("swordsdance", b(2, 0, 0, 0, 0));
        SELF_BOOST.put("nastyplot", b(0, 0, 2, 0, 0));
        SELF_BOOST.put("tailglow", b(0, 0, 3, 0, 0));
        SELF_BOOST.put("dragondance", b(1, 0, 0, 0, 1));
        SELF_BOOST.put("calmmind", b(0, 0, 1, 1, 0));
        SELF_BOOST.put("bulkup", b(1, 1, 0, 0, 0));
        SELF_BOOST.put("coil", b(1, 1, 0, 0, 0));
        SELF_BOOST.put("quiverdance", b(0, 0, 1, 1, 1));
        SELF_BOOST.put("shellsmash", b(2, -1, 2, -1, 2));
        SELF_BOOST.put("irondefense", b(0, 2, 0, 0, 0));
        SELF_BOOST.put("acidarmor", b(0, 2, 0, 0, 0));
        SELF_BOOST.put("barrier", b(0, 2, 0, 0, 0));
        SELF_BOOST.put("cottonguard", b(0, 3, 0, 0, 0));
        SELF_BOOST.put("amnesia", b(0, 0, 0, 2, 0));
        SELF_BOOST.put("agility", b(0, 0, 0, 0, 2));
        SELF_BOOST.put("rockpolish", b(0, 0, 0, 0, 2));
        SELF_BOOST.put("autotomize", b(0, 0, 0, 0, 2));
        SELF_BOOST.put("workup", b(1, 0, 1, 0, 0));
        SELF_BOOST.put("growth", b(1, 0, 1, 0, 0));
        SELF_BOOST.put("shiftgear", b(1, 0, 0, 0, 2));
        SELF_BOOST.put("victorydance", b(1, 1, 0, 0, 1));
        SELF_BOOST.put("tidyup", b(1, 0, 0, 0, 1));
        SELF_BOOST.put("howl", b(1, 0, 0, 0, 0));
        SELF_BOOST.put("honeclaws", b(1, 0, 0, 0, 0));
        SELF_BOOST.put("meditate", b(1, 0, 0, 0, 0));
        SELF_BOOST.put("sharpen", b(1, 0, 0, 0, 0));
        SELF_BOOST.put("defensecurl", b(0, 1, 0, 0, 0));
        SELF_BOOST.put("harden", b(0, 1, 0, 0, 0));
        SELF_BOOST.put("withdraw", b(0, 1, 0, 0, 0));
        SELF_BOOST.put("cosmicpower", b(0, 1, 0, 1, 0));
        SELF_BOOST.put("defendorder", b(0, 1, 0, 1, 0));
        SELF_BOOST.put("stockpile", b(0, 1, 0, 1, 0));
        SELF_BOOST.put("curse", b(1, 1, 0, 0, -1));
        SELF_BOOST.put("bellydrum", b(6, 0, 0, 0, 0));
        SELF_BOOST.put("filletaway", b(2, 0, 2, 0, 2));
        SELF_BOOST.put("clangoroussoul", b(1, 1, 1, 1, 1));
        SELF_BOOST.put("noretreat", b(1, 1, 1, 1, 1));
        SELF_BOOST.put("geomancy", b(0, 0, 2, 2, 2));
        SELF_BOOST.put("takeheart", b(0, 0, 1, 1, 0));
        // Damaging moves with guaranteed self boosts.
        SELF_BOOST.put("flamecharge", b(0, 0, 0, 0, 1));
        SELF_BOOST.put("trailblaze", b(0, 0, 0, 0, 1));
        SELF_BOOST.put("aquastep", b(0, 0, 0, 0, 1));
        SELF_BOOST.put("rapidspin", b(0, 0, 0, 0, 1));
        SELF_BOOST.put("poweruppunch", b(1, 0, 0, 0, 0));
        SELF_BOOST.put("torchsong", b(0, 0, 1, 0, 0));
        SELF_BOOST.put("chargebeam", b(0, 0, 1, 0, 0));
        SELF_BOOST.put("esperwing", b(0, 0, 0, 0, 1));
        SELF_BOOST.put("meteorbeam", b(0, 0, 1, 0, 0));
        SELF_BOOST.put("electroshot", b(0, 0, 1, 0, 0));

        SELF_DROP.put("closecombat", b(0, -1, 0, -1, 0));
        SELF_DROP.put("superpower", b(-1, -1, 0, 0, 0));
        SELF_DROP.put("overheat", b(0, 0, -2, 0, 0));
        SELF_DROP.put("dracometeor", b(0, 0, -2, 0, 0));
        SELF_DROP.put("leafstorm", b(0, 0, -2, 0, 0));
        SELF_DROP.put("fleurcannon", b(0, 0, -2, 0, 0));
        SELF_DROP.put("psychoboost", b(0, 0, -2, 0, 0));
        SELF_DROP.put("makeitrain", b(0, 0, -1, 0, 0));
        SELF_DROP.put("hammerarm", b(0, 0, 0, 0, -1));
        SELF_DROP.put("icehammer", b(0, 0, 0, 0, -1));
        SELF_DROP.put("vcreate", b(0, -1, 0, -1, -1));
        SELF_DROP.put("headlongrush", b(0, -1, 0, -1, 0));
        SELF_DROP.put("armorcannon", b(0, -1, 0, -1, 0));
        SELF_DROP.put("dragonascent", b(0, -1, 0, -1, 0));
        SELF_DROP.put("spinout", b(0, 0, 0, 0, -2));
        SELF_DROP.put("clangingscales", b(0, -1, 0, 0, 0));

        TARGET_DROP.put("icywind", b(0, 0, 0, 0, -1));
        TARGET_DROP.put("electroweb", b(0, 0, 0, 0, -1));
        TARGET_DROP.put("rocktomb", b(0, 0, 0, 0, -1));
        TARGET_DROP.put("bulldoze", b(0, 0, 0, 0, -1));
        TARGET_DROP.put("mudshot", b(0, 0, 0, 0, -1));
        TARGET_DROP.put("lowsweep", b(0, 0, 0, 0, -1));
        TARGET_DROP.put("snarl", b(0, 0, -1, 0, 0));
        TARGET_DROP.put("strugglebug", b(0, 0, -1, 0, 0));
        TARGET_DROP.put("mysticalfire", b(0, 0, -1, 0, 0));
        TARGET_DROP.put("spiritbreak", b(0, 0, -1, 0, 0));
        TARGET_DROP.put("breakingswipe", b(-1, 0, 0, 0, 0));
        TARGET_DROP.put("lunge", b(-1, 0, 0, 0, 0));
        TARGET_DROP.put("tropkick", b(-1, 0, 0, 0, 0));
        TARGET_DROP.put("chillingwater", b(-1, 0, 0, 0, 0));
        TARGET_DROP.put("skittersmack", b(0, 0, -1, 0, 0));
        TARGET_DROP.put("partingshot", b(-1, 0, -1, 0, 0));
        TARGET_DROP.put("nobleroar", b(-1, 0, -1, 0, 0));
        TARGET_DROP.put("growl", b(-1, 0, 0, 0, 0));
        TARGET_DROP.put("charm", b(-2, 0, 0, 0, 0));
        TARGET_DROP.put("featherdance", b(-2, 0, 0, 0, 0));
        TARGET_DROP.put("screech", b(0, -2, 0, 0, 0));
        TARGET_DROP.put("metalsound", b(0, 0, 0, -2, 0));
        TARGET_DROP.put("faketears", b(0, 0, 0, -2, 0));
        TARGET_DROP.put("scaryface", b(0, 0, 0, 0, -2));
        TARGET_DROP.put("cottonspore", b(0, 0, 0, 0, -2));
        TARGET_DROP.put("stringshot", b(0, 0, 0, 0, -2));
        TARGET_DROP.put("eerieimpulse", b(0, 0, -2, 0, 0));
        TARGET_DROP.put("confide", b(0, 0, -1, 0, 0));
        TARGET_DROP.put("tickle", b(-1, -1, 0, 0, 0));
        TARGET_DROP.put("memento", b(-2, 0, -2, 0, 0));
        TARGET_DROP.put("acidspray", b(0, 0, 0, -2, 0));
        TARGET_DROP.put("firelash", b(0, -1, 0, 0, 0));
        TARGET_DROP.put("thunderouskick", b(0, -1, 0, 0, 0));

        INFLICT_STATUS.put("thunderwave", "par");
        INFLICT_STATUS.put("stunspore", "par");
        INFLICT_STATUS.put("glare", "par");
        INFLICT_STATUS.put("nuzzle", "par");
        INFLICT_STATUS.put("zapcannon", "par");
        INFLICT_STATUS.put("willowisp", "brn");
        INFLICT_STATUS.put("toxic", "tox");
        INFLICT_STATUS.put("poisonpowder", "psn");
        INFLICT_STATUS.put("poisongas", "psn");
        INFLICT_STATUS.put("toxicthread", "psn");
        INFLICT_STATUS.put("spore", "slp");
        INFLICT_STATUS.put("sleeppowder", "slp");
        INFLICT_STATUS.put("hypnosis", "slp");
        INFLICT_STATUS.put("sing", "slp");
        INFLICT_STATUS.put("grasswhistle", "slp");
        INFLICT_STATUS.put("lovelykiss", "slp");
        INFLICT_STATUS.put("darkvoid", "slp");
        INFLICT_STATUS.put("yawn", "slp");
        INFLICT_STATUS.put("confuseray", "cnf");
        INFLICT_STATUS.put("supersonic", "cnf");
        INFLICT_STATUS.put("swagger", "cnf");
        INFLICT_STATUS.put("sweetkiss", "cnf");
        INFLICT_STATUS.put("teeterdance", "cnf");
        INFLICT_STATUS.put("leechseed", "seed");
        INFLICT_STATUS.put("taunt", "taunt");
        INFLICT_STATUS.put("encore", "encore");

        SECONDARY_STATUS.put("scald", "brn");
        SECONDARY_STATUS.put("lavaplume", "brn");
        SECONDARY_STATUS.put("flamethrower", "brn");
        SECONDARY_STATUS.put("fireblast", "brn");
        SECONDARY_STATUS.put("sacredfire", "brn");
        SECONDARY_STATUS.put("inferno", "brn");
        SECONDARY_STATUS.put("steameruption", "brn");
        SECONDARY_STATUS.put("scorchingsands", "brn");
        SECONDARY_STATUS.put("thunderbolt", "par");
        SECONDARY_STATUS.put("thunder", "par");
        SECONDARY_STATUS.put("discharge", "par");
        SECONDARY_STATUS.put("bodyslam", "par");
        SECONDARY_STATUS.put("forcepalm", "par");
        SECONDARY_STATUS.put("dragonbreath", "par");
        SECONDARY_STATUS.put("sludgebomb", "psn");
        SECONDARY_STATUS.put("sludgewave", "psn");
        SECONDARY_STATUS.put("gunkshot", "psn");
        SECONDARY_STATUS.put("poisonjab", "psn");
        SECONDARY_STATUS.put("crosspoison", "psn");
        SECONDARY_STATUS.put("direclaw", "psn");
        SECONDARY_STATUS.put("malignantchain", "tox");
        SECONDARY_STATUS.put("icebeam", "frz");
        SECONDARY_STATUS.put("blizzard", "frz");
        SECONDARY_STATUS.put("freezedry", "frz");
        SECONDARY_STATUS.put("icepunch", "frz");
        SECONDARY_STATUS.put("triattack", "par");

        WEATHER_SETTER.put("raindance", "rain");
        WEATHER_SETTER.put("sunnyday", "sun");
        WEATHER_SETTER.put("sandstorm", "sand");
        WEATHER_SETTER.put("snowscape", "snow");
        WEATHER_SETTER.put("hail", "snow");
        WEATHER_SETTER.put("chillyreception", "snow");
        TERRAIN_SETTER.put("electricterrain", "electric");
        TERRAIN_SETTER.put("grassyterrain", "grassy");
        TERRAIN_SETTER.put("mistyterrain", "misty");
        TERRAIN_SETTER.put("psychicterrain", "psychic");

        FIXED_DAMAGE.put("dragonrage", 40);
        FIXED_DAMAGE.put("sonicboom", 20);

        all(SELF_KO, "explosion selfdestruct mistyexplosion memento finalgambit healingwish lunardance");
        all(CHARGE, "solarbeam solarblade skyattack razorwind skullbash freezeshock iceburn dig fly dive bounce "
            + "phantomforce shadowforce skydrop");
        all(RECHARGE, "hyperbeam gigaimpact blastburn frenzyplant hydrocannon rockwrecker roaroftime "
            + "prismaticlaser eternabeam meteorassault");
        all(PIVOT, "uturn voltswitch flipturn partingshot teleport shedtail chillyreception batonpass");
        all(PROTECT, "protect detect kingsshield spikyshield banefulbunker silktrap obstruct burningbulwark maxguard");
        all(FIRST_TURN_ONLY, "fakeout firstimpression matblock");
        all(FLINCH_ALWAYS, "fakeout");
        all(HAZARD, "stealthrock spikes toxicspikes stickyweb stoneaxe ceaselessedge");
        all(HAZARD_REMOVAL, "rapidspin defog mortalspin tidyup courtchange");
        all(SCREEN, "reflect lightscreen auroraveil");
        all(PHAZE, "roar whirlwind dragontail circlethrow");
        all(REDIRECT, "followme ragepowder spotlight");
        all(OHKO, "fissure guillotine horndrill sheercold");
        all(IGNORE_PROTECT, "feint shadowforce phantomforce hyperspacefury hyperspacehole");
        all(BREAKS_SCREENS, "brickbreak psychicfangs ragingbull");
        all(SOUND, "boomburst bugbuzz chatter clangingscales clangoroussoul disarmingvoice echoedvoice hypervoice "
            + "overdrive relicsong round snarl sparklingaria torchsong uproar perishsong growl roar sing supersonic "
            + "screech metalsound nobleroar partingshot psychicnoise alluringvoice");
        all(PUNCH, "bulletpunch cometpunch dizzypunch doubleironbash drainpunch dynamicpunch firepunch focuspunch "
            + "hammerarm icehammer icepunch jetpunch machpunch megapunch meteormash poweruppunch shadowpunch "
            + "skyuppercut surgingstrikes thunderpunch wickedblow ragefist headlongrush plasmafists");
        all(BITE, "bite crunch firefang hyperfang icefang jawlock poisonfang psychicfangs thunderfang fishiousrend");
        all(SLICE, "aerialace airslash aquacutter behemothblade bitterblade ceaselessedge crosspoison cut furycutter "
            + "kowtowcleave leafblade nightslash populationbomb psychocut razorleaf razorshell sacredsword "
            + "slash solarblade stoneaxe xscissor tachyoncutter mightycleave");
        all(PULSE, "aurasphere darkpulse dragonpulse healpulse originpulse terrainpulse waterpulse");
        all(POWDER, "cottonspore poisonpowder ragepowder sleeppowder spore stunspore powder magicpowder");
        all(BALLISTIC, "acidspray aurasphere barrage beakblast bulletseed eggbomb electroball energyball focusblast "
            + "gyroball iceball magnetbomb mistball mudbomb octazooka pollenpuff pyroball rockblast rockwrecker "
            + "searingshot seedbomb shadowball sludgebomb weatherball zapcannon syrupbomb");
        all(AVOID, "splash celebrate holdhands happyhour teleport struggle afteryou quash "
            + "allyswitch transform sketch mimic metronome naturepower assist copycat mirrormove sleeptalk "
            + "snore focuspunch lastresort belch spitup swallow bide counter mirrorcoat metalburst "
            + "destinybond grudge magiccoat snatch trick switcheroo bestow fling recycle");
    }

    public static boolean isStatusCategoryUseful(String id) {
        return SELF_BOOST.containsKey(id) || HEAL.containsKey(id) || INFLICT_STATUS.containsKey(id)
            || HAZARD.contains(id) || HAZARD_REMOVAL.contains(id) || SCREEN.contains(id) || PROTECT.contains(id)
            || WEATHER_SETTER.containsKey(id) || TERRAIN_SETTER.containsKey(id) || TARGET_DROP.containsKey(id)
            || PHAZE.contains(id) || REDIRECT.contains(id) || "trickroom".equals(id) || "tailwind".equals(id)
            || "substitute".equals(id) || "haze".equals(id) || "painsplit".equals(id) || "helpinghand".equals(id)
            || "wideguard".equals(id) || "clearsmog".equals(id);
    }
}
