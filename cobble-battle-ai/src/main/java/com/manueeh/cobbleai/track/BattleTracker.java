package com.manueeh.cobbleai.track;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.manueeh.cobbleai.data.AbilityDex;
import com.manueeh.cobbleai.data.Ids;
import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.model.Field;
import com.manueeh.cobbleai.model.SideState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds up knowledge Cobblemon's client does not keep in structured form: weather, terrain,
 * hazards, screens, volatile conditions and what the opponent has revealed (moves, ability, item).
 * Fed by the raw battle message components (see BattleMessageHandlerMixin).
 */
public final class BattleTracker {
    public static final BattleTracker INSTANCE = new BattleTracker();

    /** Everything learned about one Pokémon during the current battle. */
    public static final class Knowledge {
        public final Set<String> moves = new LinkedHashSet<>();
        public String ability;
        public String item;
        public boolean itemGone;
        public String teraType;
        public boolean terastallized;
        public boolean substitute;
        public boolean confused;
        public boolean leechSeeded;
        public boolean taunted;
        public int protectStreak;
        public String lastMove;
        public int lastMoveStreak;
        /** Pokemon targeted by the last single-target move (null for spread/self moves). */
        public UUID lastTarget;
        /** Flash Fire has absorbed a Fire move: its own Fire moves are 1.5x until it leaves the field. */
        public boolean flashFireActive;
        /** Hit by Yawn: falls asleep at the end of the next turn unless it switches out. */
        public boolean drowsy;
        /** Attacking types this Pokemon was immune to (reveals Levitate, Flash Fire...). */
        public final Set<String> immuneTo = new java.util.HashSet<>();
        public double lastHpFrac = 1;
        public boolean fainted;
        /** Stat stage changes seen since this Pokemon entered the field (ATK..EVA). */
        public int[] boosts = new int[7];
        /** Move requests answered while this Pokemon was on the field (0 = just came in). */
        public int turnsOnField;
        public boolean megaEvolved;
        /** Learned correction of attack power (physical, special). */
        public double[] powerMult = {1, 1};
        /** Learned correction of bulk (physical, special); >1 means tankier than estimated. */
        public double[] bulkMult = {1, 1};
        /** Speed bounds learned from turn order (raw stat). */
        public double minSpe = 0;
        public double maxSpe = Double.MAX_VALUE;
        public String name;
        public String species;
        public com.cobblemon.mod.common.pokemon.Species speciesRef;
        public Set<String> aspects;
        public int level;
        public boolean opponent;
        /** Times seen using Wide Guard / Quick Guard. */
        public int wideGuardUses, quickGuardUses;
        /** Times seen using Protect / Detect / King's Shield... (habit). */
        public int protectUses;
        /** Turns already spent asleep in the current sleep. */
        public int sleepSkips;
        /** Trainer that owns this Pokemon (for the cross-battle memory) and whether memory was applied. */
        public String trainer;
        public boolean remembered;
        public boolean persisted;
        /** Decisions taken while asleep on the field (the sleep length is hidden, so this estimates it). */
        public int asleepTurns;
        public int asleepSeenTurn = -1;
    }

    /** One move used during the current turn window, in execution order. */
    public static final class Event {
        public final UUID actor;
        public final String move;
        public final UUID target;
        public boolean crit;
        public boolean failed;
        public final Set<UUID> tainted = new java.util.HashSet<>();

        Event(UUID actor, String move, UUID target) {
            this.actor = actor;
            this.move = move;
            this.target = target;
        }
    }

    private final List<Event> events = new ArrayList<>();
    private final Set<UUID> taintedThisTurn = new java.util.HashSet<>();
    private boolean trickRoomChanged;

    /** Hands over the events since the last decision and starts a new window. */
    public synchronized List<Event> drainEvents() {
        for (Event e : events) e.tainted.addAll(taintedThisTurn);
        List<Event> out = new ArrayList<>(events);
        events.clear();
        taintedThisTurn.clear();
        return out;
    }

    public synchronized boolean drainTrickRoomChanged() {
        boolean v = trickRoomChanged;
        trickRoomChanged = false;
        return v;
    }

    private UUID battleId;
    private final Field field = new Field();
    private final Map<UUID, Knowledge> known = new HashMap<>();
    private final Map<String, UUID> slotOccupant = new HashMap<>();
    private final Set<UUID> protectedThisTurn = new java.util.HashSet<>();
    private int turn;
    /** Who most likely set the weather that is about to start (weather move or weather ability). */
    private UUID weatherSetter;

    private BattleTracker() {}

    /** Saves what was learned about trainers' Pokemon into the cross-battle memory. */
    public synchronized void persist() {
        boolean any = false;
        for (Knowledge k : known.values()) {
            if (!k.opponent || k.persisted || k.trainer == null || k.species == null) continue;
            OpponentMemory.remember(k.trainer, k.species, k);
            k.persisted = true;
            any = true;
        }
        if (any) OpponentMemory.save();
    }

    public synchronized void reset(UUID id) {
        persist();
        battleId = id;
        known.clear();
        slotOccupant.clear();
        events.clear();
        taintedThisTurn.clear();
        trickRoomChanged = false;
        protectedThisTurn.clear();
        weatherSetter = null;
        turn = 0;
        Field fresh = new Field();
        copyField(fresh, field);
    }

    public synchronized UUID battleId() {
        return battleId;
    }

    public synchronized Field fieldCopy() {
        return field.copy();
    }

    public synchronized Knowledge knowledge(UUID uuid) {
        return known.computeIfAbsent(uuid, k -> new Knowledge());
    }

    public synchronized List<Map.Entry<UUID, Knowledge>> opponents() {
        List<Map.Entry<UUID, Knowledge>> out = new ArrayList<>();
        for (var e : known.entrySet()) if (e.getValue().opponent) out.add(e);
        return out;
    }

    /** Called each client tick while in battle: notices switches and remembers opponents. */
    public synchronized void observe(ClientBattle battle) {
        if (battle == null) return;
        if (!battle.getBattleId().equals(battleId)) reset(battle.getBattleId());
        observeSide(battle.getSide1(), false);
        observeSide(battle.getSide2(), true);
    }

    private void observeSide(ClientBattleSide side, boolean opponent) {
        for (ActiveClientBattlePokemon active : side.getActiveClientBattlePokemon()) {
            ClientBattlePokemon p = active.getBattlePokemon();
            String pnx;
            try {
                pnx = active.getPNX();
            } catch (Exception e) {
                continue;
            }
            UUID now = p == null ? null : p.getUuid();
            UUID before = slotOccupant.get(pnx);
            if (before != null && !before.equals(now)) {
                Knowledge k = known.get(before);
                if (k != null) clearVolatiles(k);
            }
            if (now != null) {
                if (!now.equals(before)) {
                    Knowledge fresh = known.computeIfAbsent(now, x -> new Knowledge());
                    fresh.turnsOnField = 0;
                }
                slotOccupant.put(pnx, now);
            } else {
                slotOccupant.remove(pnx);
            }
            if (p == null) continue;
            Knowledge k = known.computeIfAbsent(p.getUuid(), x -> new Knowledge());
            k.opponent = opponent;
            k.name = p.getDisplayName().getString();
            try {
                k.speciesRef = p.getSpecies();
                k.species = Ids.of(k.speciesRef.showdownId());
                k.aspects = com.manueeh.cobbleai.bridge.StateBuilder.aspectsOf(p);
            } catch (Exception ignored) {
            }
            k.level = p.getLevel();
            if (opponent && !k.remembered && k.species != null) {
                try {
                    var actor = active.getActor();
                    if (actor.getType() != com.cobblemon.mod.common.api.battles.model.actor.ActorType.WILD) {
                        k.trainer = actor.getDisplayName().getString();
                        OpponentMemory.seed(k.trainer, k.species, k);
                    }
                } catch (Exception ignored) {
                }
                k.remembered = true;
            }
            k.lastHpFrac = hpFraction(p);
            if (k.lastHpFrac <= 0) k.fainted = true;
        }
    }

    /**
     * HP fraction of a client battle Pokemon. Cobblemon sends allies as absolute HP ("flat") and
     * opponents as a 0..1 ratio, while maxHp is always the real maximum.
     */
    public static double hpFraction(ClientBattlePokemon p) {
        double frac;
        if (p.isHpFlat()) frac = p.getMaxHp() > 0 ? p.getHpValue() / p.getMaxHp() : 0;
        else frac = p.getHpValue();
        if (frac > 1.0001) frac = p.getMaxHp() > 0 ? p.getHpValue() / p.getMaxHp() : 1;
        return Math.max(0, Math.min(1, frac));
    }

    /** Called once per move request after planning: everyone on the field has spent a turn there. */
    public synchronized void markTurn(java.util.Collection<UUID> onField) {
        for (UUID u : onField) knowledge(u).turnsOnField++;
    }

    private static void clearVolatiles(Knowledge k) {
        k.boosts = new int[7];
        k.substitute = false;
        k.confused = false;
        k.leechSeeded = false;
        k.taunted = false;
        k.protectStreak = 0;
        k.lastTarget = null;
        k.flashFireActive = false;
        k.drowsy = false;
    }

    // ------------------------------------------------------------------ message parsing

    public synchronized void onMessages(List<Component> messages) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        if (!battle.getBattleId().equals(battleId)) reset(battle.getBattleId());
        for (Component c : messages) {
            if (com.manueeh.cobbleai.AiConfig.get().logBattleMessages) {
                com.manueeh.cobbleai.CobbleBattleAI.LOG.info("[AI-MSG] {}", c.getString());
            }
            try {
                handle(battle, c);
            } catch (Exception ignored) {
                // Unknown message shapes are simply skipped.
            }
        }
    }

    private void handle(ClientBattle battle, Component c) {
        TranslatableContents tc = findTranslatable(c);
        if (tc == null) return;
        String key = tc.getKey();
        if (key.startsWith("cobblemon.status.sleep.")) {
            // Sleep lasts 1-3 turns: count the turns it has already slept through.
            UUID who = resolve(battle, arg(tc.getArgs(), 0), null);
            if (who != null) {
                Knowledge kn = knowledge(who);
                switch (key.substring("cobblemon.status.sleep.".length())) {
                    case "apply", "cure" -> {
                        kn.sleepSkips = 0;
                        kn.drowsy = false;
                    }
                    case "is" -> kn.sleepSkips++;
                    default -> { }
                }
            }
            return;
        }
        if (!key.startsWith("cobblemon.battle.")) return;
        String k = key.substring("cobblemon.battle.".length());
        Object[] args = tc.getArgs();

        if (k.equals("used_move") || k.equals("used_move_on")) {
            String move = moveId(arg(args, 1));
            UUID who = resolve(battle, arg(args, 0), move);
            if (who != null && move != null && !move.isEmpty()) {
                Knowledge kn = knowledge(who);
                kn.moves.add(move);
                kn.lastMoveStreak = move.equals(kn.lastMove) ? kn.lastMoveStreak + 1 : 1;
                kn.lastMove = move;
                if (MoveDex.WEATHER_SETTER.containsKey(move)) weatherSetter = who;
                if (MoveDex.PROTECT.contains(move)) kn.protectUses++;
                // Wide Guard / Quick Guard share the Protect counter and are learned per Pokemon.
                if (move.equals("wideguard")) {
                    kn.wideGuardUses++;
                    kn.protectStreak++;
                    protectedThisTurn.add(who);
                } else if (move.equals("quickguard")) {
                    kn.quickGuardUses++;
                    kn.protectStreak++;
                    protectedThisTurn.add(who);
                }
                UUID target = k.equals("used_move_on") ? resolve(battle, arg(args, 2), null) : null;
                kn.lastTarget = target != null && !target.equals(who) ? target : null;
                events.add(new Event(who, move, target));
            }
            return;
        }
        if (k.startsWith("hit_count") || k.startsWith("hitcount")) {
            // "Hit 2 times!" after a single-hit move is Parental Bond (#30 Aureo: a Kangaskhan that never showed
            // its Mega message doubled Fake Out and Double-Edge, and Garchomp was switched into it).
            if (!events.isEmpty()) {
                Event e = events.get(events.size() - 1);
                if (e.actor != null && e.move != null && !MoveDex.MULTI_HIT.containsKey(e.move)) knowledge(e.actor).ability = "parentalbond";
            }
            return;
        }
        if (k.equals("turn")) {
            turnPassed();
            return;
        }
        if (k.equals("immune")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null && !events.isEmpty()) {
                Event last = events.get(events.size() - 1);
                try {
                    var t = com.cobblemon.mod.common.api.moves.Moves.getByName(last.move);
                    if (t != null) knowledge(who).immuneTo.add(Ids.of(t.getElementalType().getName()));
                } catch (Exception ignored) {
                }
            }
            return;
        }
        if (k.equals("start.flashfire")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) {
                knowledge(who).ability = "flashfire";
                knowledge(who).flashFireActive = true;
            }
            return;
        }
        if (k.equals("start.yawn")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) knowledge(who).drowsy = true;
            return;
        }
        if (k.equals("heal.waterabsorb") || k.equals("heal.voltabsorb")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) knowledge(who).ability = k.substring("heal.".length());
            // fall through to the chip taint below
        }
        if (k.equals("crit")) {
            if (!events.isEmpty()) events.get(events.size() - 1).crit = true;
            return;
        }
        if (k.equals("crit_spread")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) taintedThisTurn.add(who);
            return;
        }
        if (k.equals("fail") || k.startsWith("cant.")) {
            if (!events.isEmpty() && k.equals("fail")) events.get(events.size() - 1).failed = true;
            return;
        }
        if (k.startsWith("damage.") || k.startsWith("heal.") || k.startsWith("enditem.") || k.startsWith("activate.")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) taintedThisTurn.add(who);
            // fall through: some of these also carry tracked state below
        }
        if (k.startsWith("boost.") || k.startsWith("unboost.")) {
            if (k.contains(".cap.")) return;
            UUID who = resolve(battle, arg(args, 0), null);
            int stat = statIndex(arg(args, 1));
            if (who == null || stat < 0) return;
            int amount = k.contains(".severe") ? 3 : k.contains(".sharp") ? 2 : 1;
            if (k.startsWith("unboost.")) amount = -amount;
            int[] b = knowledge(who).boosts;
            b[stat] = Math.max(-6, Math.min(6, b[stat] + amount));
            return;
        }
        if (k.startsWith("setboost.")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) knowledge(who).boosts[0] = 6;
            return;
        }
        if (k.equals("clearboost") || k.startsWith("clearallnegativeboost")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who == null) return;
            int[] b = knowledge(who).boosts;
            for (int i = 0; i < b.length; i++) if (k.equals("clearboost") || b[i] < 0) b[i] = 0;
            return;
        }
        if (k.equals("clearallboost")) {
            for (Knowledge kn : known.values()) kn.boosts = new int[7];
            return;
        }
        if (k.equals("mega") || k.equals("formechange.mega")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) knowledge(who).megaEvolved = true;
            return;
        }
        if (k.startsWith("weather.")) {
            String[] parts = k.split("\\.");
            if (parts.length >= 3) {
                String w = switch (parts[1]) {
                    case "raindance", "primordialsea" -> "rain";
                    case "sunnyday", "desolateland" -> "sun";
                    case "sandstorm" -> "sand";
                    case "hail", "snow" -> "snow";
                    default -> null;
                };
                switch (parts[2]) {
                    case "end" -> {
                        field.weather = null;
                        field.weatherTurns = 0;
                        field.primal = false;
                    }
                    case "start" -> {
                        if ("primordialsea".equals(parts[1]) || "desolateland".equals(parts[1])) {
                            field.setPrimal(w);
                        } else {
                            field.primal = false;
                            field.weather = w;
                            field.weatherTurns = weatherDuration(w);
                        }
                    }
                    default -> {
                        // upkeep: one more turn went by
                        if ("primordialsea".equals(parts[1]) || "desolateland".equals(parts[1])) {
                            field.setPrimal(w);
                        } else if (!java.util.Objects.equals(field.weather, w)) {
                            field.weather = w;
                            field.weatherTurns = 3;
                        } else {
                            field.weatherTurns--;
                            if (field.weatherTurns < 1) field.weatherTurns = 3; // weather rock extension
                        }
                    }
                }
            }
            return;
        }
        if (k.startsWith("fieldstart.") || k.startsWith("fieldend.")) {
            boolean start = k.startsWith("fieldstart.");
            String what = k.substring(k.indexOf('.') + 1);
            switch (what) {
                case "electricterrain" -> field.terrain = start ? "electric" : null;
                case "grassyterrain" -> field.terrain = start ? "grassy" : null;
                case "mistyterrain" -> field.terrain = start ? "misty" : null;
                case "psychicterrain" -> field.terrain = start ? "psychic" : null;
                case "trickroom" -> {
                    field.trickRoom = start;
                    field.trickRoomTurns = start ? 5 : 0;
                    trickRoomChanged = true;
                }
                case "gravity" -> field.gravity = start;
                default -> { }
            }
            return;
        }
        if (k.startsWith("activate.") && k.endsWith("terrain")) {
            String t = k.substring("activate.".length(), k.length() - "terrain".length());
            if (!t.isEmpty()) field.terrain = t;
            return;
        }
        if (k.startsWith("sidestart.") || k.startsWith("sideend.")) {
            boolean start = k.startsWith("sidestart.");
            String[] parts = k.split("\\.");
            if (parts.length < 3) return;
            SideState side = parts[1].equals("ally") ? field.mine : field.theirs;
            applySide(side, parts[2], start);
            return;
        }
        if (k.startsWith("start.") || k.startsWith("end.") || k.startsWith("activate.")) {
            String what = k.substring(k.indexOf('.') + 1);
            boolean start = !k.startsWith("end.");
            UUID who = resolve(battle, arg(args, 0), null);
            if (who == null) return;
            Knowledge kn = knowledge(who);
            switch (what) {
                case "substitute" -> kn.substitute = start;
                case "confusion" -> kn.confused = start;
                case "leechseed" -> kn.leechSeeded = start;
                case "taunt" -> kn.taunted = start;
                case "protect" -> {
                    kn.protectStreak++;
                    protectedThisTurn.add(who);
                }
                default -> { }
            }
            return;
        }
        if (k.equals("singleturn.protect")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) {
                knowledge(who).protectStreak++;
                protectedThisTurn.add(who);
            }
            return;
        }
        if (k.equals("terastallize")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) {
                Knowledge kn = knowledge(who);
                kn.terastallized = true;
                kn.teraType = typeId(arg(args, 1));
            }
            return;
        }
        if (k.startsWith("ability.")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who == null) return;
            String sub = k.substring("ability.".length());
            String ability = switch (sub) {
                case "generic", "replace" -> abilityId(arg(args, 1));
                case "trace" -> abilityId(arg(args, 2));
                default -> sub;
            };
            if (ability != null && !ability.isEmpty()) knowledge(who).ability = ability;
            if (ability != null && AbilityDex.ENTRY_WEATHER.containsKey(ability)) weatherSetter = who;
            return;
        }
        if (k.startsWith("damage.") && args != null && args.length >= 3) {
            // "X was hurt by the Rocky Helmet of Y": the item belongs to the third argument, not to the victim
            // (42 Rocky Helmet hits in the logs were credited to our own attacker; Perpetua's Amoonguss
            // then finished a Garchomp nobody knew was at risk).
            String item = itemId(arg(args, 1));
            UUID holder = resolve(battle, arg(args, 2), null);
            if (holder != null && ("rockyhelmet".equals(item) || "jabocaberry".equals(item) || "rowapberry".equals(item))) {
                Knowledge kh = knowledge(holder);
                kh.item = item;
                kh.itemGone = item.endsWith("berry");
                return;
            }
        }
        // "X lost some of its HP!" is Life Orb recoil (Showdown's own wording): the foe's item is revealed (log 20:
        // Rosamunda's Mewtwo lost 10% after every attack, yet the engine never counted the recoil on it).
        if (k.equals("damage.lifeorb")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who != null) {
                knowledge(who).item = "lifeorb";
                knowledge(who).itemGone = false;
            }
            return;
        }
        if (k.startsWith("item.") || k.startsWith("enditem.") || k.equals("damage.item") || k.equals("heal.item")) {
            UUID who = resolve(battle, arg(args, 0), null);
            if (who == null) return;
            Knowledge kn = knowledge(who);
            String item = itemId(arg(args, 1));
            if (k.startsWith("enditem.")) {
                String sub = k.substring("enditem.".length());
                if (item == null || item.isEmpty()) item = sub.endsWith("berry") ? sub : item;
                kn.item = null;
                kn.itemGone = true;
            } else if (item != null && !item.isEmpty()) {
                kn.item = item;
                kn.itemGone = false;
            }
        }
    }

    /** 5 turns, or 8 when the Pokemon that set the weather holds the matching weather rock. */
    private int weatherDuration(String w) {
        UUID setter = weatherSetter;
        weatherSetter = null;
        if (setter == null || w == null) return 5;
        String item = knowledge(setter).item;
        boolean rock = ("sun".equals(w) && "heatrock".equals(item)) || ("rain".equals(w) && "damprock".equals(item))
            || ("sand".equals(w) && "smoothrock".equals(item)) || ("snow".equals(w) && "icyrock".equals(item));
        return rock ? 8 : 5;
    }

    private void applySide(SideState side, String what, boolean start) {
        switch (what) {
            case "stealthrock" -> side.stealthRock = start;
            case "spikes" -> side.spikes = start ? Math.min(3, side.spikes + 1) : 0;
            case "toxicspikes" -> side.toxicSpikes = start ? Math.min(2, side.toxicSpikes + 1) : 0;
            case "stickyweb" -> side.stickyWeb = start;
            case "reflect" -> side.reflect = start;
            case "lightscreen" -> side.lightScreen = start;
            case "auroraveil" -> side.auroraVeil = start;
            case "tailwind" -> {
                side.tailwind = start;
                side.tailwindTurns = start ? 4 : 0;
                trickRoomChanged = true; // speed order changed mid-turn: no speed learning this turn
            }
            case "safeguard" -> side.safeguard = start;
            default -> { }
        }
    }

    /** Called when a new action request arrives: protect streaks of Pokémon that did not protect reset. */
    public synchronized void turnPassed() {
        turn++;
        // Tailwind (4 turns) and Trick Room (5) count the turn they were set in.
        for (SideState side : new SideState[] {field.mine, field.theirs})
            if (side.tailwind && side.tailwindTurns > 1) side.tailwindTurns--;
        if (field.trickRoom && field.trickRoomTurns > 1) field.trickRoomTurns--;
        weatherSetter = null;
        for (var e : known.entrySet()) {
            if (!protectedThisTurn.contains(e.getKey())) e.getValue().protectStreak = 0;
        }
        protectedThisTurn.clear();
    }

    public synchronized int turn() {
        return turn;
    }

    // ------------------------------------------------------------------ argument helpers

    private static Object arg(Object[] args, int i) {
        return args != null && i < args.length ? args[i] : null;
    }

    private static String text(Object o) {
        if (o == null) return "";
        if (o instanceof Component c) return c.getString();
        return String.valueOf(o);
    }

    private static TranslatableContents findTranslatable(Component c) {
        if (c.getContents() instanceof TranslatableContents tc) return tc;
        for (Component sib : c.getSiblings()) {
            TranslatableContents t = findTranslatable(sib);
            if (t != null) return t;
        }
        return null;
    }

    private static String keySuffix(Object o, String prefix) {
        if (o instanceof Component c) {
            TranslatableContents tc = findTranslatable(c);
            if (tc != null && tc.getKey().startsWith(prefix)) {
                String rest = tc.getKey().substring(prefix.length());
                int dot = rest.indexOf('.');
                return Ids.of(dot >= 0 ? rest.substring(0, dot) : rest);
            }
        }
        return null;
    }

    /** Stage index (ATK=0 .. EVA=6) for a stat name argument, -1 if unknown. */
    private static int statIndex(Object o) {
        String key = keySuffix(o, "cobblemon.stat.");
        String s = key != null ? key : Ids.of(text(o));
        return switch (s) {
            case "attack", "atk", "ataque" -> 0;
            case "defence", "defense", "def", "defensa" -> 1;
            case "specialattack", "spatk", "spa", "ataqueespecial" -> 2;
            case "specialdefence", "specialdefense", "spdef", "spd", "defensaespecial" -> 3;
            case "speed", "spe", "velocidad" -> 4;
            case "accuracy", "precision" -> 5;
            case "evasion", "evasiveness" -> 6;
            default -> -1;
        };
    }

    private static String moveId(Object o) {
        String k = keySuffix(o, "cobblemon.move.");
        return k != null ? k : Ids.of(text(o));
    }

    private static String abilityId(Object o) {
        String k = keySuffix(o, "cobblemon.ability.");
        return k != null ? k : Ids.of(text(o));
    }

    private static String typeId(Object o) {
        String k = keySuffix(o, "cobblemon.type.");
        return k != null ? k : Ids.of(text(o));
    }

    private static String itemId(Object o) {
        if (o instanceof Component c) {
            TranslatableContents tc = findTranslatable(c);
            if (tc != null && tc.getKey().startsWith("item.")) {
                String key = tc.getKey();
                return Ids.of(key.substring(key.lastIndexOf('.') + 1));
            }
        }
        String t = Ids.of(text(o));
        return t.isEmpty() ? null : t;
    }

    /**
     * Maps a Pokémon name from a message to the active battler it refers to. When both sides
     * have a Pokémon with the same name, the move (if known) disambiguates.
     */
    private UUID resolve(ClientBattle battle, Object nameArg, String move) {
        String name = text(nameArg);
        if (name.isEmpty()) return null;
        List<ClientBattlePokemon> matches = new ArrayList<>();
        for (ClientBattleSide side : new ClientBattleSide[] {battle.getSide1(), battle.getSide2()}) {
            for (ActiveClientBattlePokemon a : side.getActiveClientBattlePokemon()) {
                List<ClientBattlePokemon> candidates = new ArrayList<>();
                // A Pokemon that was just sent out may still be waiting on its slide-in animation.
                for (Object anim : a.getAnimations()) {
                    if (anim instanceof com.cobblemon.mod.common.client.battle.animations.MoveTileOnscreenAnimation on
                        && on.getSwappedPokemon() != null) candidates.add(on.getSwappedPokemon());
                }
                if (a.getBattlePokemon() != null) candidates.add(a.getBattlePokemon());
                for (ClientBattlePokemon p : candidates) {
                    String dn = p.getDisplayName().getString();
                    if (name.equals(dn) || name.endsWith(" " + dn) || name.contains(dn)) {
                        matches.add(p);
                        break;
                    }
                }
            }
        }
        if (matches.isEmpty()) return null;
        if (matches.size() == 1) return matches.get(0).getUuid();
        // Mirror match (our Garchomp vs theirs): the message names the owner ("Garchomp de Kaprus_").
        String owner = localPlayerName();
        if (owner != null && !owner.isEmpty()) {
            boolean ours = name.contains(owner);
            boolean hasOwner = ours;
            for (ClientBattlePokemon p : matches) hasOwner |= name.length() > p.getDisplayName().getString().length() + 1;
            if (hasOwner) {
                for (ClientBattlePokemon p : matches) {
                    boolean isOpp = sideHas(battle.getSide2(), p);
                    if (ours != isOpp) return p.getUuid();
                }
            }
        }
        if (move == null) return matches.get(0).getUuid();
        // Prefer the opponent unless the move is one of our own known moves.
        ClientBattlePokemon opp = null, mine = null;
        for (ClientBattlePokemon p : matches) {
            boolean isOpp = battle.getSide2().getActiveClientBattlePokemon() != null && sideHas(battle.getSide2(), p);
            if (isOpp && opp == null) opp = p;
            if (!isOpp && mine == null) mine = p;
        }
        if (mine != null) {
            Knowledge km = known.get(mine.getUuid());
            if (km != null && km.moves.contains(move)) return mine.getUuid();
        }
        return (opp != null ? opp : matches.get(0)).getUuid();
    }

    private static String localPlayerName() {
        try {
            return net.minecraft.client.Minecraft.getInstance().getUser().getName();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sideHas(ClientBattleSide side, ClientBattlePokemon p) {
        for (ActiveClientBattlePokemon a : side.getActiveClientBattlePokemon()) {
            if (a.getBattlePokemon() == p) return true;
        }
        return false;
    }

    private static void copyField(Field from, Field to) {
        to.weather = from.weather;
        to.weatherTurns = from.weatherTurns;
        to.terrain = from.terrain;
        to.trickRoom = from.trickRoom;
        to.trickRoomTurns = from.trickRoomTurns;
        to.gravity = from.gravity;
        copySide(from.mine, to.mine);
        copySide(from.theirs, to.theirs);
    }

    private static void copySide(SideState from, SideState to) {
        to.stealthRock = from.stealthRock;
        to.spikes = from.spikes;
        to.toxicSpikes = from.toxicSpikes;
        to.stickyWeb = from.stickyWeb;
        to.reflect = from.reflect;
        to.lightScreen = from.lightScreen;
        to.auroraVeil = from.auroraVeil;
        to.tailwind = from.tailwind;
        to.tailwindTurns = from.tailwindTurns;
        to.safeguard = from.safeguard;
    }
}
