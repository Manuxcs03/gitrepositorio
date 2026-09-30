package com.manueeh.cobbleai.data;

import java.util.HashMap;
import java.util.Map;

/** Gen 6+ type effectiveness chart keyed by lowercase type id. */
public final class TypeChart {
    private TypeChart() {}

    public static final String[] TYPES = {
        "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
        "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"
    };

    private static final Map<String, Integer> INDEX = new HashMap<>();
    private static final double[][] CHART = new double[18][18];

    static {
        for (int i = 0; i < TYPES.length; i++) INDEX.put(TYPES[i], i);
        for (double[] row : CHART) java.util.Arrays.fill(row, 1.0);
        set("normal", "rock steel", "ghost", "");
        set("fire", "fire water rock dragon", "", "grass ice bug steel");
        set("water", "water grass dragon", "", "fire ground rock");
        set("electric", "electric grass dragon", "ground", "water flying");
        set("grass", "fire grass poison flying bug dragon steel", "", "water ground rock");
        set("ice", "fire water ice steel", "", "grass ground flying dragon");
        set("fighting", "poison flying psychic bug fairy", "ghost", "normal ice rock dark steel");
        set("poison", "poison ground rock ghost", "steel", "grass fairy");
        set("ground", "grass bug", "flying", "fire electric poison rock steel");
        set("flying", "electric rock steel", "", "grass fighting bug");
        set("psychic", "psychic steel", "dark", "fighting poison");
        set("bug", "fire fighting poison flying ghost steel fairy", "", "grass psychic dark");
        set("rock", "fighting ground steel", "", "fire ice flying bug");
        set("ghost", "dark", "normal", "psychic ghost");
        set("dragon", "steel", "fairy", "dragon");
        set("dark", "fighting dark fairy", "", "psychic ghost");
        set("steel", "fire water electric steel", "", "ice rock fairy");
        set("fairy", "fire poison steel", "", "fighting dragon dark");
    }

    private static void set(String atk, String half, String zero, String dbl) {
        int a = INDEX.get(atk);
        for (String d : half.split(" ")) if (!d.isEmpty()) CHART[a][INDEX.get(d)] = 0.5;
        for (String d : zero.split(" ")) if (!d.isEmpty()) CHART[a][INDEX.get(d)] = 0.0;
        for (String d : dbl.split(" ")) if (!d.isEmpty()) CHART[a][INDEX.get(d)] = 2.0;
    }

    public static boolean isType(String t) {
        return t != null && INDEX.containsKey(t);
    }

    /** Multiplier of one attacking type against one defending type. Unknown types are neutral. */
    public static double single(String atk, String def) {
        Integer a = INDEX.get(atk), d = INDEX.get(def);
        if (a == null || d == null) return 1.0;
        return CHART[a][d];
    }

    public static double against(String atk, String[] defTypes) {
        double m = 1.0;
        for (String d : defTypes) m *= single(atk, d);
        return m;
    }
}
