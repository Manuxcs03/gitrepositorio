package com.manueeh.cobbleai;

import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.manueeh.cobbleai.bridge.ResponseMapper;
import com.manueeh.cobbleai.bridge.StateBuilder;
import com.manueeh.cobbleai.engine.Action;
import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.track.BattleTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Watches Cobblemon's client battle for pending action requests, runs the planner off-thread
 * and submits the chosen responses exactly like the battle GUI would.
 */
public final class BattleDriver {
    public static final BattleDriver INSTANCE = new BattleDriver();

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CobbleBattleAI-Planner");
        t.setDaemon(true);
        return t;
    });

    public enum Phase { IDLE, WAITING, THINKING, READY, SENT, ERROR }

    private Object requestKey;
    private int ticksSinceRequest;
    private int ticksReady;
    private boolean prevMustChoose;
    private int invalidRetries;
    private boolean executeRequested;

    private StateBuilder.Snapshot snapshot;
    /** State at the previous decision, to compare against what actually happened since. */
    private BattleState lastDecisionState;
    /** Loop breaker: damaging moves we chose last turn and how many turns in a row they left the foes untouched. */
    private final java.util.Set<String> lastMoveIds = new java.util.HashSet<>();
    private int stallTurns;
    private boolean lastWasAttack;
    private Future<Planner.Plan> future;
    private volatile Planner.Plan plan;
    private volatile Phase phase = Phase.IDLE;
    private volatile String lastError;

    private BattleDriver() {}

    public Phase phase() {
        return phase;
    }

    public Planner.Plan plan() {
        return plan;
    }

    public StateBuilder.Snapshot snapshot() {
        return snapshot;
    }

    public String lastError() {
        return lastError;
    }

    /** Execute the current suggestion (SUGGEST mode) or re-think (other modes). */
    public void requestExecute() {
        executeRequested = true;
    }

    private void reset() {
        try {
            BattleTracker.INSTANCE.persist();
        } catch (Exception e) {
            CobbleBattleAI.LOG.warn("Could not persist opponent memory", e);
        }
        requestKey = null;
        snapshot = null;
        future = null;
        plan = null;
        phase = Phase.IDLE;
        prevMustChoose = false;
        invalidRetries = 0;
        executeRequested = false;
        lastDecisionState = null;
        stallTurns = 0;
        lastWasAttack = false;
        lastMoveIds.clear();
    }

    public void tick(Minecraft mc) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) {
            if (phase != Phase.IDLE) reset();
            return;
        }
        if (battle.getSpectating()) return;
        try {
            BattleTracker.INSTANCE.observe(battle);
            step(mc, battle);
        } catch (Throwable t) {
            phase = Phase.ERROR;
            lastError = t.toString();
            CobbleBattleAI.LOG.error("Battle AI tick failed", t);
        }
    }

    private void step(Minecraft mc, ClientBattle battle) {
        AiConfig cfg = AiConfig.get();
        boolean must = battle.getMustChoose();
        List<SingleActionRequest> reqs = battle.getPendingActionRequests();

        // The server rejected our choice: Cobblemon flips mustChoose back on for the same request.
        if (must && !prevMustChoose && reqs == requestKey && phase == Phase.SENT) {
            invalidRetries++;
            CobbleBattleAI.LOG.warn("Choice rejected by server (attempt {})", invalidRetries);
            for (SingleActionRequest r : reqs) r.setResponse(null);
            plan = null;
            future = null;
            phase = invalidRetries <= 2 ? Phase.WAITING : Phase.ERROR;
            if (phase == Phase.ERROR) lastError = "server rejected the AI choice; pick manually";
        }
        prevMustChoose = must;

        if (!must || reqs.isEmpty()) {
            if (phase == Phase.READY || phase == Phase.THINKING || phase == Phase.WAITING) {
                // The player answered manually or the request vanished.
                phase = Phase.IDLE;
            }
            return;
        }
        if (reqs != requestKey) {
            requestKey = reqs;
            ticksSinceRequest = 0;
            ticksReady = 0;
            invalidRetries = 0;
            plan = null;
            future = null;
            snapshot = null;
            phase = Phase.WAITING;
        }
        if (cfg.mode == AiConfig.Mode.OFF) {
            executeRequested = false;
            return;
        }
        if (phase == Phase.SENT || phase == Phase.ERROR) return;
        ticksSinceRequest++;

        if (phase == Phase.WAITING) {
            boolean settled = animationsSettled(battle);
            if (ticksSinceRequest < cfg.thinkDelayTicks || (!settled && ticksSinceRequest < 100)) return;
            boolean anyOpen = false;
            for (SingleActionRequest r : reqs) anyOpen |= r.getResponse() == null;
            if (!anyOpen) return;
            snapshot = StateBuilder.build(battle, reqs);
            if (snapshot.slotRequests.isEmpty()) return;
            if (invalidRetries == 0) {
                try {
                    com.manueeh.cobbleai.bridge.Calibrator.learn(lastDecisionState, snapshot.state,
                        BattleTracker.INSTANCE.drainEvents(), BattleTracker.INSTANCE.drainTrickRoomChanged());
                } catch (Exception e) {
                    CobbleBattleAI.LOG.warn("Calibration failed", e);
                }
                // Rebuild so the freshly learned corrections apply to this decision.
                snapshot = StateBuilder.build(battle, reqs);
                if (lastDecisionState != null && lastWasAttack && sameOpponents(lastDecisionState, snapshot.state)
                    && !opponentLostHp(lastDecisionState, snapshot.state)) stallTurns++;
                else stallTurns = 0;
                lastDecisionState = snapshot.state.copy();
            }
            snapshot.state.stallTurns = stallTurns;
            snapshot.state.lastMoveIds.addAll(lastMoveIds);
            boolean moveTurn = false;
            for (var sr : snapshot.slotRequests) moveTurn |= !sr.forceSwitch && !sr.pass;
            if (moveTurn && invalidRetries == 0) {
                java.util.List<java.util.UUID> onField = new java.util.ArrayList<>();
                for (var side : new com.cobblemon.mod.common.client.battle.ClientBattleSide[] {battle.getSide1(), battle.getSide2()}) {
                    for (ActiveClientBattlePokemon a : side.getActiveClientBattlePokemon()) {
                        if (a.getBattlePokemon() != null) onField.add(a.getBattlePokemon().getUuid());
                    }
                }
                BattleTracker.INSTANCE.markTurn(onField);
            }
            Planner.Options opt = options(cfg, invalidRetries > 0);
            final BattleState st = snapshot.state;
            final List<Planner.SlotRequest> srs = snapshot.slotRequests;
            future = EXEC.submit(() -> {
                Planner.Plan p = new Planner(opt).decide(st, srs);
                try {
                    p.advice = com.manueeh.cobbleai.engine.Advisor.advise(st, p);
                } catch (Exception e) {
                    CobbleBattleAI.LOG.warn("Advice failed", e);
                }
                return p;
            });
            phase = Phase.THINKING;
            return;
        }
        if (phase == Phase.THINKING) {
            if (future == null || !future.isDone()) return;
            try {
                plan = future.get();
            } catch (Exception e) {
                phase = Phase.ERROR;
                lastError = String.valueOf(e.getCause() != null ? e.getCause() : e);
                CobbleBattleAI.LOG.error("Planner failed", e);
                return;
            }
            future = null;
            phase = Phase.READY;
            ticksReady = 0;
            logPlan(mc, cfg);
        }
        if (phase == Phase.READY) {
            ticksReady++;
            boolean auto = cfg.mode == AiConfig.Mode.AUTO
                && (snapshot.state.oppKind != BattleState.OpponentKind.PLAYER || cfg.autoVsPlayers);
            if ((auto && ticksReady >= cfg.actDelayTicks) || executeRequested) {
                executeRequested = false;
                apply(mc, battle);
            }
        }
    }

    /** True when the same opponents are on the field as at the previous decision. */
    private static boolean sameOpponents(BattleState before, BattleState now) {
        java.util.Set<java.util.UUID> a = new java.util.HashSet<>();
        java.util.Set<java.util.UUID> b = new java.util.HashSet<>();
        for (int i = 0; i < before.slots(); i++) {
            com.manueeh.cobbleai.model.Battler x = before.opp(i);
            if (x != null && x.uuid != null) a.add(x.uuid);
        }
        for (int i = 0; i < now.slots(); i++) {
            com.manueeh.cobbleai.model.Battler x = now.opp(i);
            if (x != null && x.uuid != null) b.add(x.uuid);
        }
        return !a.isEmpty() && a.equals(b);
    }

    /** True when any opponent lost HP between two decisions. */
    private static boolean opponentLostHp(BattleState before, BattleState now) {
        for (com.manueeh.cobbleai.model.Battler b : before.oppTeam) {
            if (b.uuid == null) continue;
            for (com.manueeh.cobbleai.model.Battler n : now.oppTeam) {
                if (b.uuid.equals(n.uuid) && n.hpFrac() < b.hpFrac() - 0.005) return true;
            }
        }
        return false;
    }

    private static Planner.Options options(AiConfig cfg, boolean safe) {
        Planner.Options o = new Planner.Options();
        o.useTera = cfg.useTerastal && !safe;
        o.useMega = cfg.useMega && !safe;
        o.useZ = cfg.useZMoves && !safe;
        o.useDynamax = cfg.useDynamax && !safe;
        o.allowSwitching = cfg.allowSwitching;
        o.riskAversion = Math.max(0, Math.min(1, cfg.riskAversion));
        return o;
    }

    private static boolean animationsSettled(ClientBattle battle) {
        for (ClientBattleSide side : new ClientBattleSide[] {battle.getSide1(), battle.getSide2()}) {
            for (ActiveClientBattlePokemon a : side.getActiveClientBattlePokemon()) {
                if (!a.getAnimations().isEmpty()) return false;
            }
        }
        return true;
    }

    private void apply(Minecraft mc, ClientBattle battle) {
        Planner.Plan p = plan;
        StateBuilder.Snapshot snap = snapshot;
        if (p == null || snap == null) return;
        if (battle.getPendingActionRequests() != requestKey) return;
        List<ShowdownActionResponse> responses = new ArrayList<>();
        for (int i = 0; i < snap.requests.size() && i < p.actions.size(); i++) {
            responses.add(ResponseMapper.toResponse(snap, i, p.actions.get(i)));
        }
        lastMoveIds.clear();
        lastWasAttack = false;
        for (Action a : p.actions) {
            if (a.kind == Action.Kind.MOVE && a.executed().category != com.manueeh.cobbleai.model.Category.STATUS) {
                lastWasAttack = true;
                lastMoveIds.add(a.slot + ":" + a.executed().id);
            }
        }
        BattleGUI gui = mc.screen instanceof BattleGUI g ? g : null;
        for (int i = 0; i < responses.size(); i++) {
            SingleActionRequest r = snap.requests.get(i);
            if (r.getResponse() != null) continue;
            if (gui != null) gui.selectAction(r, responses.get(i));
            else r.setResponse(responses.get(i));
        }
        // Any request the GUI did not route (or when no GUI is open) is flushed here.
        if (battle.getMustChoose()) battle.checkForFinishedChoosing();
        phase = Phase.SENT;
    }

    private static String describeBoard(BattleState s) {
        StringBuilder sb = new StringBuilder();
        sb.append("weather=").append(s.field.weather).append(s.field.weather != null ? "(" + s.field.weatherTurns + "t)" : "").append(" terrain=").append(s.field.terrain)
            .append(s.field.trickRoom ? " TR" : "").append(s.field.mine.tailwind ? " myTW" : "")
            .append(s.field.theirs.tailwind ? " oppTW" : "").append(" | mine:");
        for (int i = 0; i < s.slots(); i++) sb.append(' ').append(brief(s.my(i)));
        sb.append(" | opp:");
        for (int i = 0; i < s.slots(); i++) sb.append(' ').append(brief(s.opp(i)));
        return sb.toString();
    }

    private static String brief(com.manueeh.cobbleai.model.Battler b) {
        if (b == null) return "-";
        StringBuilder sb = new StringBuilder(b.name).append(' ').append(Math.round(b.hpFrac() * 100)).append('%');
        if (b.status != null) sb.append(' ').append(b.status);
        int[] bo = b.boosts;
        String[] names = {"atk", "def", "spa", "spd", "spe", "acc", "eva"};
        for (int i = 0; i < bo.length; i++) if (bo[i] != 0) sb.append(' ').append(bo[i] > 0 ? "+" : "").append(bo[i]).append(names[i]);
        if (b.item != null) sb.append(" @").append(b.item);
        sb.append(" atk=").append(b.atk).append(" spa=").append(b.spa).append(" spe=").append(b.spe).append(" [");
        for (int i = 0; i < b.moves.size(); i++) {
            if (i > 0) sb.append(',');
            com.manueeh.cobbleai.model.MoveInfo m = b.moves.get(i);
            sb.append(m.id).append(m.revealed ? "" : "?");
        }
        return sb.append(']').toString();
    }

    private void logPlan(Minecraft mc, AiConfig cfg) {
        Planner.Plan p = plan;
        if (p == null) return;
        StringBuilder sb = new StringBuilder();
        for (Action a : p.actions) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(a.describe(snapshot.state));
        }
        CobbleBattleAI.LOG.info("[AI] {} (eval {}, {} us) {}", sb, String.format("%.3f", p.value), p.micros, p.notes);
        CobbleBattleAI.LOG.info("[AI-STATE] {}", describeBoard(snapshot.state));
        if (p.advice != null) CobbleBattleAI.LOG.info("[AI-ADVICE] {}", p.advice);
        if (cfg.logToChat && mc.player != null) {
            mc.gui.getChat().addMessage(Component.literal("[AI] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE)));
        }
    }
}
