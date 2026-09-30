package com.manueeh.cobbleai.data;

import java.util.HashMap;
import java.util.Map;

/** Held item facts used by the damage and speed model. */
public final class ItemDex {
    private ItemDex() {}

    /** Item -> type whose moves it boosts by 20%. */
    public static final Map<String, String> TYPE_BOOST = new HashMap<>();

    static {
        String[][] pairs = {
            {"charcoal", "fire"}, {"charcoalstick", "fire"}, {"flameplate", "fire"}, {"mysticwater", "water"}, {"splashplate", "water"},
            {"seaincense", "water"}, {"waveincense", "water"}, {"miracleseed", "grass"}, {"meadowplate", "grass"},
            {"roseincense", "grass"}, {"magnet", "electric"}, {"zapplate", "electric"}, {"nevermeltice", "ice"},
            {"icicleplate", "ice"}, {"blackbelt", "fighting"}, {"fistplate", "fighting"}, {"poisonbarb", "poison"},
            {"toxicplate", "poison"}, {"softsand", "ground"}, {"earthplate", "ground"}, {"sharpbeak", "flying"},
            {"skyplate", "flying"}, {"twistedspoon", "psychic"}, {"mindplate", "psychic"}, {"oddincense", "psychic"},
            {"silverpowder", "bug"}, {"insectplate", "bug"}, {"hardstone", "rock"}, {"stoneplate", "rock"},
            {"rockincense", "rock"}, {"spelltag", "ghost"}, {"spookyplate", "ghost"}, {"dragonfang", "dragon"},
            {"dracoplate", "dragon"}, {"blackglasses", "dark"}, {"dreadplate", "dark"}, {"metalcoat", "steel"},
            {"ironplate", "steel"}, {"silkscarf", "normal"}, {"fairyfeather", "fairy"}, {"pixieplate", "fairy"}
        };
        for (String[] p : pairs) TYPE_BOOST.put(p[0], p[1]);
    }

    public static boolean isChoice(String item) {
        return "choiceband".equals(item) || "choicespecs".equals(item) || "choicescarf".equals(item);
    }
}
