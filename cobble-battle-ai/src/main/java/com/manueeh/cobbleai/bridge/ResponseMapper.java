package com.manueeh.cobbleai.bridge;

import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.manueeh.cobbleai.engine.Action;

import java.util.List;

/** Converts engine actions into the response objects Cobblemon sends to the server. */
public final class ResponseMapper {
    private ResponseMapper() {}

    public static ShowdownActionResponse toResponse(StateBuilder.Snapshot snap, int requestIndex, Action a) {
        switch (a.kind) {
            case PASS:
                return PassActionResponse.INSTANCE;
            case SWITCH:
                return new SwitchActionResponse(snap.myTeamUuids.get(a.switchTo));
            default:
                break;
        }
        InBattleMove ibm = snap.moveHandles.get(requestIndex).get(a.move);
        String moveId = ibm != null ? ibm.getId() : a.move.id;
        ActiveClientBattlePokemon user = snap.requests.get(requestIndex).getActivePokemon();

        MoveTarget target;
        if (a.gimmickMove != null && a.gimmickMove.target != null) {
            target = safeTarget(a.gimmickMove.target, ibm);
        } else {
            target = ibm != null ? ibm.getTarget() : safeTarget(a.move.target, null);
        }
        String pnx = pickTarget(snap, user, target, a);
        return new MoveActionResponse(moveId, pnx, a.gimmick);
    }

    private static MoveTarget safeTarget(String name, InBattleMove fallback) {
        try {
            return MoveTarget.valueOf(name);
        } catch (Exception e) {
            return fallback != null ? fallback.getTarget() : MoveTarget.normal;
        }
    }

    private static String pickTarget(StateBuilder.Snapshot snap, ActiveClientBattlePokemon user, MoveTarget target, Action a) {
        List<Targetable> options;
        try {
            options = target.getTargetList().invoke(user);
        } catch (Exception e) {
            options = null;
        }
        if (options == null || options.isEmpty()) return null;
        if (a.targetSlot >= 0) {
            List<ActiveClientBattlePokemon> side = a.targetMine ? snap.mySlots : snap.oppSlots;
            if (a.targetSlot < side.size()) {
                ActiveClientBattlePokemon wanted = side.get(a.targetSlot);
                for (Targetable t : options) if (t == wanted) return t.getPNX();
            }
        }
        for (Targetable t : options) {
            if (!t.isAllied(user) && t.hasPokemon()) return t.getPNX();
        }
        for (Targetable t : options) {
            if (t.hasPokemon()) return t.getPNX();
        }
        return options.get(0).getPNX();
    }
}
