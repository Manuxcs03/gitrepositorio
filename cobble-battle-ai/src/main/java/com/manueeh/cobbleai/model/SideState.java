package com.manueeh.cobbleai.model;

public final class SideState {
    public boolean stealthRock;
    public int spikes;
    public int toxicSpikes;
    public boolean stickyWeb;
    public boolean reflect;
    public boolean lightScreen;
    public boolean auroraVeil;
    public boolean tailwind;
    /** Turns of Tailwind left including the current one (0 = unknown). */
    public int tailwindTurns;
    public boolean safeguard;

    public SideState copy() {
        SideState s = new SideState();
        s.stealthRock = stealthRock;
        s.spikes = spikes;
        s.toxicSpikes = toxicSpikes;
        s.stickyWeb = stickyWeb;
        s.reflect = reflect;
        s.lightScreen = lightScreen;
        s.auroraVeil = auroraVeil;
        s.tailwind = tailwind;
        s.tailwindTurns = tailwindTurns;
        s.safeguard = safeguard;
        return s;
    }

    public boolean anyHazard() {
        return stealthRock || spikes > 0 || toxicSpikes > 0 || stickyWeb;
    }
}
