package com.manueeh.cobbleai.model;

public final class Field {
    /** rain, sun, sand, snow or null. */
    public String weather;
    /** Turns of weather left including the current one (1 = it ends at the end of this turn). */
    public int weatherTurns;
    /** electric, grassy, misty, psychic or null. */
    public String terrain;
    public boolean trickRoom;
    /** Turns of Trick Room left including the current one (0 = unknown). */
    public int trickRoomTurns;
    public boolean gravity;
    /** Primordial Sea / Desolate Land: Fire (or Water) moves fail and no other weather can replace it. */
    public boolean primal;
    public final SideState mine;
    public final SideState theirs;

    public Field() {
        this(new SideState(), new SideState());
    }

    private Field(SideState mine, SideState theirs) {
        this.mine = mine;
        this.theirs = theirs;
    }

    public Field copy() {
        Field f = new Field(mine.copy(), theirs.copy());
        f.weather = weather;
        f.weatherTurns = weatherTurns;
        f.terrain = terrain;
        f.trickRoom = trickRoom;
        f.trickRoomTurns = trickRoomTurns;
        f.gravity = gravity;
        f.primal = primal;
        return f;
    }

    /**
     * Starts a weather like Showdown does: an identical weather that is already active is not
     * refreshed (Drought into existing sun does nothing). Returns true if the weather changed.
     */
    public boolean setWeather(String w, String setterItem) {
        if (w == null || w.equals(weather) || primal) return false;
        weather = w;
        boolean rock = ("sun".equals(w) && "heatrock".equals(setterItem)) || ("rain".equals(w) && "damprock".equals(setterItem))
            || ("sand".equals(w) && "smoothrock".equals(setterItem)) || ("snow".equals(w) && "icyrock".equals(setterItem));
        weatherTurns = rock ? 8 : 5;
        return true;
    }

    /** Primal weather from Primal Kyogre / Groudon: lasts while its source stays on the field. */
    public void setPrimal(String w) {
        weather = w;
        weatherTurns = 99;
        primal = true;
    }

    public SideState side(boolean mySide) {
        return mySide ? mine : theirs;
    }
}
