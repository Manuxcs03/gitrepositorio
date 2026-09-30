package com.manueeh.cobbleai.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Ability facts the engine needs to reason about immunities and damage modifiers. */
public final class AbilityDex {
    private AbilityDex() {}

    /** Ability -> attacking type it makes the holder immune to. */
    public static final Map<String, String> TYPE_IMMUNITY = new HashMap<>();
    public static final Set<String> MOLD_BREAKER = Set.of("moldbreaker", "teravolt", "turboblaze", "myceliummight");
    public static final Set<String> SLEEP_IMMUNE = Set.of("insomnia", "vitalspirit", "sweetveil", "comatose", "purifyingsalt");
    public static final Set<String> PAR_IMMUNE = Set.of("limber", "comatose", "purifyingsalt");
    public static final Set<String> BRN_IMMUNE = Set.of("waterveil", "waterbubble", "thermalexchange", "comatose", "purifyingsalt");
    public static final Set<String> PSN_IMMUNE = Set.of("immunity", "pastelveil", "comatose", "purifyingsalt");
    public static final Set<String> STATUS_MOVE_IMMUNE = Set.of("goodasgold", "magicbounce");
    public static final Set<String> CONFUSION_IMMUNE = Set.of("owntempo");
    public static final Set<String> STAT_DROP_IMMUNE = Set.of("clearbody", "whitesmoke", "fullmetalbody", "mirrorarmor");
    public static final Set<String> PUNISH_STAT_DROP = Set.of("defiant", "competitive", "contrary");
    public static final Set<String> NO_INDIRECT = Set.of("magicguard");
    public static final Set<String> SPEED_DOUBLERS = new HashSet<>();
    public static final Map<String, String> ENTRY_WEATHER = new HashMap<>();
    public static final Map<String, String> ENTRY_TERRAIN = new HashMap<>();

    static {
        TYPE_IMMUNITY.put("levitate", "ground");
        TYPE_IMMUNITY.put("eartheater", "ground");
        TYPE_IMMUNITY.put("flashfire", "fire");
        TYPE_IMMUNITY.put("wellbakedbody", "fire");
        TYPE_IMMUNITY.put("waterabsorb", "water");
        TYPE_IMMUNITY.put("stormdrain", "water");
        TYPE_IMMUNITY.put("dryskin", "water");
        TYPE_IMMUNITY.put("voltabsorb", "electric");
        TYPE_IMMUNITY.put("lightningrod", "electric");
        TYPE_IMMUNITY.put("motordrive", "electric");
        TYPE_IMMUNITY.put("sapsipper", "grass");

        SPEED_DOUBLERS.add("swiftswim");
        SPEED_DOUBLERS.add("chlorophyll");
        SPEED_DOUBLERS.add("sandrush");
        SPEED_DOUBLERS.add("slushrush");
        SPEED_DOUBLERS.add("surgesurfer");

        ENTRY_WEATHER.put("drizzle", "rain");
        ENTRY_WEATHER.put("drought", "sun");
        ENTRY_WEATHER.put("desolateland", "sun");
        ENTRY_WEATHER.put("primordialsea", "rain");
        ENTRY_WEATHER.put("orichalcumpulse", "sun");
        ENTRY_WEATHER.put("sandstream", "sand");
        ENTRY_WEATHER.put("snowwarning", "snow");
        ENTRY_TERRAIN.put("electricsurge", "electric");
        ENTRY_TERRAIN.put("hadronengine", "electric");
        ENTRY_TERRAIN.put("grassysurge", "grassy");
        ENTRY_TERRAIN.put("mistysurge", "misty");
        ENTRY_TERRAIN.put("psychicsurge", "psychic");
    }

    public static boolean statusImmune(String ability, String status) {
        if (ability == null) return false;
        return switch (status) {
            case "slp" -> SLEEP_IMMUNE.contains(ability);
            case "par" -> PAR_IMMUNE.contains(ability);
            case "brn" -> BRN_IMMUNE.contains(ability);
            case "psn", "tox" -> PSN_IMMUNE.contains(ability);
            case "cnf" -> CONFUSION_IMMUNE.contains(ability);
            default -> false;
        };
    }
}
