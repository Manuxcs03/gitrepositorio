package com.manueeh.cobbleai;

import com.manueeh.cobbleai.data.MovepoolIndex;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Checks the movepool scan against a real mod jar: gradlew movepoolCheck -Pjar=path. */
public final class MovepoolCheck {
    public static void main(String[] args) throws Exception {
        Map<String, Set<String>> map = new HashMap<>();
        int files = 0;
        for (String jar : args) {
            URI uri = URI.create("jar:" + Path.of(jar).toUri());
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                files += MovepoolIndex.scanRoot(fs.getPath("/"), map);
            }
        }
        System.out.println("species=" + map.size() + " files=" + files);
        for (String sp : new String[] {"garchomp", "dragonite", "whimsicott", "mienshao", "chandelure", "amoonguss"}) {
            Set<String> m = map.getOrDefault(sp, Set.of());
            System.out.println(sp + ": " + m.size() + " moves; rockslide=" + m.contains("rockslide") + " stoneedge=" + m.contains("stoneedge")
                + " icespinner=" + m.contains("icespinner") + " fakeout=" + m.contains("fakeout") + " spore=" + m.contains("spore")
                + " swordsdance=" + m.contains("swordsdance") + " tailwind=" + m.contains("tailwind"));
        }
    }
}
