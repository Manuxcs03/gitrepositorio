package com.manueeh.cobbleai.track;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.manueeh.cobbleai.CobbleBattleAI;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Battle Tower trainers bundled with the mod: the sets (moves, ability, item) and the full roster of every
 * trainer seen in the logs. Tower trainers always bring the same four Pokemon, so a new install (or another
 * account) starts knowing that Delia's Pelipper has Wide Guard or that Cira's Arcanine carries Will-O-Wisp.
 */
public final class TowerSets {
    private TowerSets() {}

    private static final class Data {
        Map<String, OpponentMemory.Entry> sets = new HashMap<>();
        Map<String, List<String>> rosters = new HashMap<>();
    }

    private static Data data;

    private static synchronized Data data() {
        if (data != null) return data;
        data = new Data();
        try (InputStream in = TowerSets.class.getResourceAsStream("/cobblebattleai-towersets.json")) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Data loaded = new Gson().fromJson(r, new TypeToken<Data>() {}.getType());
                    if (loaded != null) {
                        if (loaded.sets != null) data.sets.putAll(loaded.sets);
                        if (loaded.rosters != null) data.rosters.putAll(loaded.rosters);
                    }
                }
            }
        } catch (Exception e) {
            CobbleBattleAI.LOG.warn("Could not read bundled Battle Tower sets", e);
        }
        return data;
    }

    /** Bundled set for a trainer's Pokemon, or null. */
    public static OpponentMemory.Entry set(String trainer, String species) {
        if (trainer == null || species == null) return null;
        return data().sets.get(OpponentMemory.key(trainer, species));
    }

    /** Species ids of the trainer's whole team (most seen first), or an empty list. */
    public static List<String> roster(String trainer) {
        if (trainer == null) return List.of();
        List<String> r = data().rosters.get(trainer);
        return r == null ? List.of() : r;
    }
}
