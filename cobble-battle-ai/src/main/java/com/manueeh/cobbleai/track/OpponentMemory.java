package com.manueeh.cobbleai.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.manueeh.cobbleai.CobbleBattleAI;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Remembers what trainers' Pokemon did in earlier battles (Battle Tower trainers repeat their teams).
 * Keyed by trainer name + species; stored in config/cobblebattleai-memory.json.
 */
public final class OpponentMemory {
    private OpponentMemory() {}

    public static final class Entry {
        public Set<String> moves = new LinkedHashSet<>();
        public String ability;
        public String item;
        public double[] powerMult = {1, 1};
        public double[] bulkMult = {1, 1};
        public double minSpe = 0;
        public double maxSpe = Double.MAX_VALUE;
        public int protectUses;
        public int wideGuardUses;
        public int battles;
        /** Calibrator version the power/bulk factors were learned with (0 = before readings were filtered). */
        public int calibVersion;
    }

    /**
     * Factors learned before the calibrator skipped mid-turn readings are not trusted: Eruption from a Torkoal hit
     * first made foes look up to twice as bulky, and that was carried into every later fight against them.
     */
    private static final int CALIB_VERSION = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    private static Map<String, Entry> data;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblebattleai-memory.json");
    }

    private static synchronized Map<String, Entry> data() {
        if (data != null) return data;
        data = new HashMap<>();
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                Map<String, Entry> loaded = GSON.fromJson(r, new TypeToken<Map<String, Entry>>() {}.getType());
                if (loaded != null) data.putAll(loaded);
            } catch (Exception e) {
                CobbleBattleAI.LOG.warn("Could not read opponent memory", e);
            }
        }
        return data;
    }

    public static String key(String trainer, String species) {
        return trainer + "|" + species;
    }

    /** Seeds fresh knowledge about an opponent from earlier battles against the same trainer. */
    public static synchronized void seed(String trainer, String species, BattleTracker.Knowledge k) {
        if (trainer == null || species == null) return;
        Entry e = data().get(key(trainer, species));
        // Sets bundled from the Battle Tower logs fill what this install has not seen yet.
        Entry bundled = TowerSets.set(trainer, species);
        if (bundled != null) {
            if (bundled.moves != null) k.moves.addAll(bundled.moves);
            if (k.ability == null) k.ability = bundled.ability;
            if (k.item == null && !k.itemGone) k.item = bundled.item;
            k.protectUses = Math.max(k.protectUses, Math.min(2, bundled.protectUses));
            k.wideGuardUses = Math.max(k.wideGuardUses, Math.min(2, bundled.wideGuardUses));
            k.remembered = true;
        }
        if (e == null) return;
        k.moves.addAll(e.moves);
        if (k.ability == null) k.ability = e.ability;
        if (k.item == null && !k.itemGone) k.item = e.item;
        // Learned factors carry over, pulled a bit towards neutral in case the set differs.
        for (int i = 0; i < 2 && e.calibVersion >= CALIB_VERSION; i++) {
            k.powerMult[i] = 1 + (e.powerMult[i] - 1) * 0.8;
            k.bulkMult[i] = 1 + (e.bulkMult[i] - 1) * 0.8;
        }
        k.minSpe = Math.max(k.minSpe, e.minSpe);
        if (e.maxSpe < k.maxSpe) k.maxSpe = e.maxSpe;
        k.protectUses = Math.max(k.protectUses, Math.min(2, e.protectUses));
        k.wideGuardUses = Math.max(k.wideGuardUses, Math.min(2, e.wideGuardUses));
        k.remembered = true;
    }

    /** Stores what was learned about an opponent this battle. */
    public static synchronized void remember(String trainer, String species, BattleTracker.Knowledge k) {
        if (trainer == null || species == null) return;
        Entry e = data().computeIfAbsent(key(trainer, species), x -> new Entry());
        e.moves.addAll(k.moves);
        if (k.ability != null) e.ability = k.ability;
        if (k.item != null) e.item = k.item;
        e.powerMult = k.powerMult.clone();
        e.bulkMult = k.bulkMult.clone();
        e.calibVersion = CALIB_VERSION;
        e.minSpe = Math.max(e.minSpe, k.minSpe);
        if (k.maxSpe < e.maxSpe) e.maxSpe = k.maxSpe;
        e.protectUses = Math.max(e.protectUses, k.protectUses);
        e.wideGuardUses = Math.max(e.wideGuardUses, k.wideGuardUses);
        e.battles++;
    }

    public static synchronized void save() {
        if (data == null) return;
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            CobbleBattleAI.LOG.warn("Could not save opponent memory", e);
        }
    }
}
