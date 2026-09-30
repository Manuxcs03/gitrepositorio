package com.manueeh.cobbleai.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.manueeh.cobbleai.CobbleBattleAI;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Full legal movepools (level-up, TM, tutor, egg...) per species.
 * Cobblemon only syncs level-up learnsets to the client, so this reads the species JSON files
 * shipped inside the installed mod jars (Cobblemon plus any mod that adds or overrides species).
 */
public final class MovepoolIndex {
    private MovepoolIndex() {}

    private static volatile Map<String, Set<String>> index;

    /** Starts loading in the background; safe to call more than once. */
    public static void preload() {
        if (index != null) return;
        Thread t = new Thread(MovepoolIndex::ensure, "CobbleBattleAI-Movepools");
        t.setDaemon(true);
        t.start();
    }

    /** All moves the species can legally know, or an empty set if unknown. */
    public static Set<String> movesOf(String speciesId) {
        Map<String, Set<String>> idx = ensure();
        Set<String> s = idx.get(Ids.of(speciesId));
        return s == null ? Collections.emptySet() : s;
    }

    private static synchronized Map<String, Set<String>> ensure() {
        if (index != null) return index;
        long t0 = System.currentTimeMillis();
        Map<String, Set<String>> map = new HashMap<>();
        int files = 0;
        // Cobblemon first, then everything else so add-ons extend/override it.
        List<ModContainer> mods = new java.util.ArrayList<>(FabricLoader.getInstance().getAllMods());
        mods.sort((a, b) -> a.getMetadata().getId().equals("cobblemon") ? -1 : b.getMetadata().getId().equals("cobblemon") ? 1 : 0);
        for (ModContainer mod : mods) {
            for (Path root : mod.getRootPaths()) {
                try {
                    files += scanRoot(root, map);
                } catch (Exception e) {
                    CobbleBattleAI.LOG.debug("Movepool scan failed for {}", mod.getMetadata().getId(), e);
                }
            }
        }
        index = map;
        CobbleBattleAI.LOG.info("Movepool index: {} species from {} files in {} ms", map.size(), files,
            System.currentTimeMillis() - t0);
        return map;
    }

    /** Reads every data/<namespace>/species/**.json under a mod root; returns the number of files read. */
    public static int scanRoot(Path root, Map<String, Set<String>> map) throws java.io.IOException {
        int files = 0;
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) return 0;
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path ns : (Iterable<Path>) namespaces::iterator) {
                Path species = ns.resolve("species");
                if (!Files.isDirectory(species)) continue;
                try (Stream<Path> walk = Files.walk(species)) {
                    for (Path p : (Iterable<Path>) walk::iterator) {
                        if (!p.toString().endsWith(".json")) continue;
                        if (read(p, map)) files++;
                    }
                }
            }
        }
        return files;
    }

    private static boolean read(Path p, Map<String, Set<String>> map) {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return false;
            JsonObject o = el.getAsJsonObject();
            if (!o.has("name") || !o.has("moves")) return false;
            String name = Ids.of(o.get("name").getAsString());
            Set<String> moves = map.computeIfAbsent(name, k -> new HashSet<>());
            addMoves(o.getAsJsonArray("moves"), moves);
            // Forms (regional variants...) can add their own moves; fold them into the species.
            if (o.has("forms") && o.get("forms").isJsonArray()) {
                for (JsonElement f : o.getAsJsonArray("forms")) {
                    if (f.isJsonObject() && f.getAsJsonObject().has("moves")) addMoves(f.getAsJsonObject().getAsJsonArray("moves"), moves);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void addMoves(JsonArray arr, Set<String> into) {
        if (arr == null) return;
        for (JsonElement e : arr) {
            if (!e.isJsonPrimitive()) continue;
            String entry = e.getAsString();
            int colon = entry.indexOf(':');
            into.add(Ids.of(colon >= 0 ? entry.substring(colon + 1) : entry));
        }
    }
}
