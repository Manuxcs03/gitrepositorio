package com.manueeh.cobbleai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, stored in config/cobblebattleai.json. */
public final class AiConfig {
    public enum Mode { OFF, SUGGEST, AUTO }

    public Mode mode = Mode.AUTO;
    /** Ticks to wait after a request arrives before thinking (lets animations/health settle). */
    public int thinkDelayTicks = 12;
    /** Extra ticks between deciding and sending in AUTO mode, so the choice is visible. */
    public int actDelayTicks = 8;
    public boolean autoVsPlayers = true;
    public boolean useTerastal = true;
    public boolean useMega = true;
    public boolean useZMoves = true;
    public boolean useDynamax = false;
    public boolean allowSwitching = true;
    /** 0 = trust the predicted enemy move, 1 = always assume the worst enemy move. */
    public double riskAversion = 0.35;
    public boolean showHud = true;
    /** HUD also shows the foes' likely moves, which of ours may fall before moving and close calls. */
    public boolean hudDetail = true;
    public boolean logToChat = false;
    /** Write every battle message to latest.log (prefix [AI-MSG]) so lost battles can be reviewed. */
    public boolean logBattleMessages = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AiConfig instance;

    public static AiConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblebattleai.json");
    }

    private static AiConfig load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                AiConfig c = GSON.fromJson(r, AiConfig.class);
                if (c != null) {
                    if (c.mode == null) c.mode = Mode.AUTO;
                    return c;
                }
            } catch (Exception e) {
                CobbleBattleAI.LOG.warn("Could not read {}, using defaults", p, e);
            }
        }
        AiConfig c = new AiConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            CobbleBattleAI.LOG.warn("Could not save config", e);
        }
    }
}
