package com.manueeh.cobbleai.engine;

import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.MoveInfo;

/** One slot's choice for a turn. */
public final class Action {
    public enum Kind { MOVE, SWITCH, PASS }

    public final Kind kind;
    public final boolean mine;
    public final int slot;
    public final MoveInfo move;
    /** Side of the chosen target (true = our side). Only meaningful when targetSlot >= 0. */
    public final boolean targetMine;
    public final int targetSlot;
    /** Team index to switch to. */
    public final int switchTo;
    /** Showdown gimmick id: mega, ultra, zmove, max, terastal or null. */
    public final String gimmick;
    /** Replacement move used with a gimmick (Z-move / Max move); null otherwise. */
    public final MoveInfo gimmickMove;
    /** Stats/typing/ability after Mega Evolution or Ultra Burst (null if unknown or not applicable). */
    public final Battler megaForm;

    private Action(Kind kind, boolean mine, int slot, MoveInfo move, boolean targetMine, int targetSlot, int switchTo,
                   String gimmick, MoveInfo gimmickMove) {
        this(kind, mine, slot, move, targetMine, targetSlot, switchTo, gimmick, gimmickMove, null);
    }

    private Action(Kind kind, boolean mine, int slot, MoveInfo move, boolean targetMine, int targetSlot, int switchTo,
                   String gimmick, MoveInfo gimmickMove, Battler megaForm) {
        this.kind = kind;
        this.mine = mine;
        this.slot = slot;
        this.move = move;
        this.targetMine = targetMine;
        this.targetSlot = targetSlot;
        this.switchTo = switchTo;
        this.gimmick = gimmick;
        this.gimmickMove = gimmickMove;
        this.megaForm = megaForm;
    }

    public static Action move(boolean mine, int slot, MoveInfo move, boolean targetMine, int targetSlot) {
        return new Action(Kind.MOVE, mine, slot, move, targetMine, targetSlot, -1, null, null);
    }

    public static Action switchTo(boolean mine, int slot, int teamIndex) {
        return new Action(Kind.SWITCH, mine, slot, null, false, -1, teamIndex, null, null);
    }

    public static Action pass(boolean mine, int slot) {
        return new Action(Kind.PASS, mine, slot, null, false, -1, -1, null, null);
    }

    public Action withGimmick(String gimmick, MoveInfo gimmickMove) {
        return new Action(kind, mine, slot, move, targetMine, targetSlot, switchTo, gimmick, gimmickMove, megaForm);
    }

    public Action withMegaForm(Battler form) {
        return new Action(kind, mine, slot, move, targetMine, targetSlot, switchTo, gimmick, gimmickMove, form);
    }

    /** The move that actually executes (gimmick replacement if any). */
    public MoveInfo executed() {
        return gimmickMove != null ? gimmickMove : move;
    }

    public String describe(BattleState s) {
        return switch (kind) {
            case PASS -> "Pass";
            case SWITCH -> "Switch -> " + s.team(mine).get(switchTo).name;
            case MOVE -> {
                StringBuilder sb = new StringBuilder(executed().toString());
                if (gimmick != null && gimmickMove == null) sb.append(" [").append(gimmick).append(']');
                if (targetSlot >= 0) {
                    Battler t = s.active(targetMine, targetSlot);
                    if (t != null) sb.append(" -> ").append(t.name);
                }
                yield sb.toString();
            }
        };
    }
}
