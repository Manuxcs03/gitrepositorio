package com.manueeh.cobbleai.data;

import java.util.Locale;

/** Showdown-style id normalisation: "Choice Band" / "cobblemon:choice_band" -> "choiceband". */
public final class Ids {
    private Ids() {}

    public static String of(String raw) {
        if (raw == null) return "";
        String s = raw;
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        s = s.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.append(c);
        }
        return out.toString();
    }
}
