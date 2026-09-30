package com.manueeh.cobbleai.bridge;

import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.ShowdownPokemon;
import com.cobblemon.mod.common.battles.ShowdownSide;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.status.PersistentStatus;
import com.manueeh.cobbleai.data.Ids;
import com.manueeh.cobbleai.data.ItemDex;
import com.manueeh.cobbleai.data.MoveDex;
import com.manueeh.cobbleai.data.TypeChart;
import com.manueeh.cobbleai.engine.Planner;
import com.manueeh.cobbleai.model.BattleState;
import com.manueeh.cobbleai.model.Battler;
import com.manueeh.cobbleai.model.Category;
import com.manueeh.cobbleai.model.MoveInfo;
import com.manueeh.cobbleai.track.BattleTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Translates Cobblemon's client battle objects into the engine model. Runs on the client thread;
 * the resulting {@link Snapshot} is immutable from Cobblemon's point of view and safe to plan on.
 */
public final class StateBuilder {
    private StateBuilder() {}

    /** Format of the battle being built: singles and doubles sets carry different support moves. */
    private static boolean buildingDoubles;

    /** Engine state plus the handles needed to turn engine actions back into Cobblemon responses. */
    public static final class Snapshot {
        public BattleState state;
        public List<Planner.SlotRequest> slotRequests = new ArrayList<>();
        /** Request per slot request, same order. */
        public List<SingleActionRequest> requests = new ArrayList<>();
        public List<ActiveClientBattlePokemon> mySlots = new ArrayList<>();
        public List<ActiveClientBattlePokemon> oppSlots = new ArrayList<>();
        /** Engine team index -> Pokémon uuid (our side). */
        public List<UUID> myTeamUuids = new ArrayList<>();
        /** Per slot request: engine move -> original in-battle move (for ids/targets). */
        public List<Map<MoveInfo, InBattleMove>> moveHandles = new ArrayList<>();
    }

    /** Our team for the build in progress (used to predict opponents' coverage moves). */
    private static List<Battler> currentMyTeam = List.of();

    public static Snapshot build(ClientBattle battle, List<SingleActionRequest> pending) {
        Snapshot snap = new Snapshot();
        BattleState s = new BattleState();
        snap.state = s;
        UUID me = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;

        ClientBattleActor myActor = null;
        for (ClientBattleActor a : battle.getSide1().getActors()) if (a.getUuid().equals(me)) myActor = a;

        for (ActiveClientBattlePokemon a : battle.getSide1().getActiveClientBattlePokemon()) snap.mySlots.add(a);
        for (ActiveClientBattlePokemon a : battle.getSide2().getActiveClientBattlePokemon()) snap.oppSlots.add(a);
        int slots = Math.max(1, Math.max(snap.mySlots.size(), snap.oppSlots.size()));
        s.doubles = slots > 1;
        buildingDoubles = s.doubles;
        s.myActive = new int[slots];
        s.oppActive = new int[slots];
        java.util.Arrays.fill(s.myActive, -1);
        java.util.Arrays.fill(s.oppActive, -1);
        s.myControlled = new boolean[slots];

        s.oppKind = BattleState.OpponentKind.WILD;
        String trainerName = null;
        for (ClientBattleActor a : battle.getSide2().getActors()) {
            if (a.getType() == ActorType.PLAYER) s.oppKind = BattleState.OpponentKind.PLAYER;
            else if (a.getType() == ActorType.NPC && s.oppKind != BattleState.OpponentKind.PLAYER)
                s.oppKind = BattleState.OpponentKind.NPC;
            if (a.getType() == ActorType.NPC && trainerName == null) {
                try {
                    trainerName = a.getDisplayName().getString();
                } catch (Exception ignored) {
                }
            }
        }

        BattleTracker tracker = BattleTracker.INSTANCE;
        s.field = tracker.fieldCopy();

        // ---------------- our team (from the Showdown request side data) ----------------
        ShowdownSide side = null;
        for (SingleActionRequest r : pending) if (r.getSide() != null) side = r.getSide();
        Map<UUID, Integer> myIndex = new HashMap<>();
        if (side != null) {
            for (ShowdownPokemon sp : side.getPokemon()) {
                UUID uuid;
                try {
                    uuid = sp.getUuid();
                } catch (Exception e) {
                    continue;
                }
                Pokemon p = findOwn(myActor, uuid);
                Battler b = ownBattler(sp, p);
                BattleTracker.Knowledge own = BattleTracker.INSTANCE.knowledge(uuid);
                if (own.itemGone) b.item = null;
                if (b.item == null) {
                    // The battle copy may not carry the held item; the synced party does.
                    try {
                        Pokemon party = CobblemonClient.INSTANCE.getStorage().getParty().findByUUID(uuid);
                        if (party != null) b.item = itemId(party.heldItem());
                    } catch (Exception ignored) {
                    }
                }
                tracker.knowledge(uuid).item = b.item;
                myIndex.put(uuid, s.myTeam.size());
                s.myTeam.add(b);
                snap.myTeamUuids.add(uuid);
            }
        } else if (myActor != null) {
            // No Showdown side data: fall back to the synced party objects.
            for (Pokemon p : myActor.getPokemon()) {
                Battler b = partyBattler(p);
                myIndex.put(p.getUuid(), s.myTeam.size());
                s.myTeam.add(b);
                snap.myTeamUuids.add(p.getUuid());
            }
        }

        // ---------------- active slots ----------------
        for (int i = 0; i < snap.mySlots.size(); i++) {
            ActiveClientBattlePokemon a = snap.mySlots.get(i);
            ClientBattlePokemon cbp = a.getBattlePokemon();
            boolean controlled = myActor != null && a.getActor() == myActor;
            s.myControlled[i] = controlled;
            if (cbp == null) continue;
            Integer idx = myIndex.get(cbp.getUuid());
            if (idx == null) {
                // Partner's Pokémon in a multi battle: estimate like an opponent.
                Battler b = estimatedBattler(cbp, true, BattleState.OpponentKind.NPC, tracker);
                idx = s.myTeam.size();
                s.myTeam.add(b);
                snap.myTeamUuids.add(cbp.getUuid());
            }
            s.myActive[i] = idx;
            Battler b = s.myTeam.get(idx);
            applyBoosts(b, cbp.getStatChanges());
            applyVolatiles(b, tracker.knowledge(cbp.getUuid()));
        }

        // ---------------- benched Mega candidates ----------------
        boolean megaUsed = false;
        for (UUID u : snap.myTeamUuids) megaUsed |= tracker.knowledge(u).megaEvolved;
        if (!megaUsed) {
            for (int i = 0; i < s.myTeam.size(); i++) {
                Battler b = s.myTeam.get(i);
                if (s.isActive(true, i) || !b.alive() || b.item == null || !b.item.contains("ite")) continue;
                b.pendingMega = megaVariant(b, findOwn(myActor, snap.myTeamUuids.get(i)));
            }
        }

        // ---------------- opponents ----------------
        currentMyTeam = s.myTeam;
        Set<UUID> activeOpp = new java.util.HashSet<>();
        for (int i = 0; i < snap.oppSlots.size(); i++) {
            ClientBattlePokemon cbp = snap.oppSlots.get(i).getBattlePokemon();
            if (cbp == null) continue;
            Battler b = estimatedBattler(cbp, false, s.oppKind, tracker);
            s.oppActive[i] = s.oppTeam.size();
            s.oppTeam.add(b);
            activeOpp.add(cbp.getUuid());
        }
        int seen = activeOpp.size();
        for (var e : tracker.opponents()) {
            if (activeOpp.contains(e.getKey())) continue;
            BattleTracker.Knowledge k = e.getValue();
            if (k.species == null) continue;
            seen++;
            Battler b = rememberedOpponent(e.getKey(), k, s.oppKind);
            if (b != null) s.oppTeam.add(b);
        }
        // Battle Tower trainers always bring the same team: put the members not seen yet on their bench.
        int rosterAdded = 0;
        List<String> roster = s.oppKind == BattleState.OpponentKind.NPC
            ? com.manueeh.cobbleai.track.TowerSets.roster(trainerName) : List.of();
        if (!roster.isEmpty()) {
            Set<String> present = new java.util.HashSet<>();
            for (Battler b : s.oppTeam) present.add(b.species);
            int level = s.oppTeam.isEmpty() ? 50 : s.oppTeam.get(0).level;
            int teamSize = Math.max(roster.size(), s.doubles ? 4 : 3);
            for (String sp : roster) {
                if (present.contains(sp) || seen + rosterAdded >= teamSize) continue;
                Battler b = rosterBattler(trainerName, sp, level, s.oppKind);
                if (b != null) {
                    s.oppTeam.add(b);
                    rosterAdded++;
                }
            }
        }
        // Tower doubles teams have four Pokemon (the old guess of three hid one reserve).
        s.oppUnseenReserves = switch (s.oppKind) {
            case WILD -> 0;
            case NPC -> Math.max(0, (s.doubles ? 4 : 3) - seen - rosterAdded);
            case PLAYER -> Math.max(0, 6 - seen);
        };

        // ---------------- slot requests ----------------
        for (SingleActionRequest r : pending) {
            if (r.getResponse() != null) continue;
            int slot = snap.mySlots.indexOf(r.getActivePokemon());
            if (slot < 0) slot = 0;
            Planner.SlotRequest sr = new Planner.SlotRequest();
            sr.slot = slot;
            Map<MoveInfo, InBattleMove> handles = new HashMap<>();
            ShowdownPokemon activeSp = findShowdown(r.getSide(), r.getActivePokemon());
            if (r.getForceSwitch()) {
                sr.forceSwitch = true;
                boolean reviving = activeSp != null && activeSp.getReviving();
                sr.switchOptions.addAll(switchOptions(r.getSide(), myIndex, reviving));
            } else if (activeSp == null || activeSp.getCondition().contains("fnt") || activeSp.getCommanding()
                || r.getMoveSet() == null) {
                sr.pass = true;
            } else {
                ShowdownMoveset ms = r.getMoveSet();
                sr.trapped = ms.getTrapped();
                List<InBattleMove> moves = ms.getMoves();
                for (int mi = 0; mi < moves.size(); mi++) {
                    InBattleMove ibm = moves.get(mi);
                    MoveInfo m = moveInfo(ibm.getId());
                    m.pp = ibm.getPp();
                    m.disabled = !ibm.canBeUsed();
                    m.target = ibm.getTarget().name();
                    sr.moves.add(m);
                    handles.put(m, ibm);
                    List<InBattleGimmickMove> z = ms.getCanZMove();
                    if (z != null && mi < z.size() && z.get(mi) != null && !z.get(mi).getDisabled()) {
                        sr.zMoves.put(m.id, gimmickMove(z.get(mi), m, true));
                    }
                    List<InBattleGimmickMove> max = ms.getMaxMoves();
                    if (max != null && mi < max.size() && max.get(mi) != null && !max.get(mi).getDisabled()) {
                        sr.maxMoves.put(m.id, gimmickMove(max.get(mi), m, false));
                    }
                }
                for (ShowdownMoveset.Gimmick g : ms.getGimmicks()) {
                    switch (g) {
                        case MEGA_EVOLUTION -> sr.canMega = true;
                        case ULTRA_BURST -> sr.canUltraBurst = true;
                        case TERASTALLIZATION -> sr.canTera = true;
                        case DYNAMAX -> sr.canDynamax = true;
                        default -> { }
                    }
                }
                if (!sr.trapped) sr.switchOptions.addAll(switchOptions(r.getSide(), myIndex, false));
                Battler self = s.my(slot);
                if (self != null && (sr.canMega || sr.canUltraBurst)) {
                    sr.megaForm = megaVariant(self, findOwn(myActor, snap.myTeamUuids.get(s.myActive[slot])));
                    self.pendingMega = sr.megaForm;
                }
                if (self != null) {
                    // The request is the authority on the active Pokémon's current moves/pp.
                    self.moves = new ArrayList<>(sr.moves);
                    if (ms.getCanTerastallize() != null && self.teraType == null) self.teraType = Ids.of(ms.getCanTerastallize());
                }
            }
            snap.slotRequests.add(sr);
            snap.requests.add(r);
            snap.moveHandles.add(handles);
        }
        return snap;
    }

    // ------------------------------------------------------------------ our Pokémon

    private static Pokemon findOwn(ClientBattleActor actor, UUID uuid) {
        if (actor != null) {
            for (Pokemon p : actor.getPokemon()) if (p.getUuid().equals(uuid)) return p;
        }
        try {
            return CobblemonClient.INSTANCE.getStorage().getParty().findByUUID(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    private static ShowdownPokemon findShowdown(ShowdownSide side, ActiveClientBattlePokemon active) {
        if (side == null || active.getBattlePokemon() == null) return null;
        UUID uuid = active.getBattlePokemon().getUuid();
        for (ShowdownPokemon sp : side.getPokemon()) {
            try {
                if (sp.getUuid().equals(uuid)) return sp;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static List<Integer> switchOptions(ShowdownSide side, Map<UUID, Integer> myIndex, boolean reviving) {
        List<Integer> out = new ArrayList<>();
        if (side == null) return out;
        for (ShowdownPokemon sp : side.getPokemon()) {
            if (sp.getActive()) continue;
            boolean fainted = sp.getCondition().contains("fnt") || sp.getCondition().startsWith("0");
            if (fainted != reviving) continue;
            try {
                Integer idx = myIndex.get(sp.getUuid());
                if (idx != null) out.add(idx);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static Battler ownBattler(ShowdownPokemon sp, Pokemon p) {
        Battler b = new Battler();
        b.mine = true;
        b.statsExact = p != null;
        try {
            b.uuid = sp.getUuid();
        } catch (Exception ignored) {
        }
        String[] cond = sp.getCondition().trim().split(" ");
        double cur = 0, max = 1;
        if (cond.length > 0) {
            String[] hp = cond[0].split("/");
            try {
                cur = Double.parseDouble(hp[0]);
                max = hp.length > 1 ? Double.parseDouble(hp[1]) : Math.max(1, cur);
            } catch (NumberFormatException ignored) {
            }
        }
        if (cond.length > 1) {
            String st = cond[1];
            if (!"fnt".equals(st)) b.status = st;
        }
        if (sp.getCondition().contains("fnt")) cur = 0;

        List<String> types = new ArrayList<>();
        for (String t : sp.getTypes()) types.add(Ids.of(t));
        List<String> baseTypes = new ArrayList<>();
        for (String t : sp.getBaseTypes()) baseTypes.add(Ids.of(t));
        if (baseTypes.isEmpty()) baseTypes = types;
        if (!baseTypes.isEmpty()) b.baseTypes = baseTypes.toArray(new String[0]);
        b.types = types.isEmpty() ? b.baseTypes : types.toArray(new String[0]);
        boolean typesKnown = !baseTypes.isEmpty();
        b.ability = emptyToNull(Ids.of(sp.getAbility()));

        if (p != null) {
            b.name = p.getDisplayName(false).getString();
            b.species = Ids.of(p.getSpecies().showdownId());
            b.level = p.getLevel();
            b.maxHp = p.getMaxHealth();
            b.atk = p.getStat(Stats.ATTACK);
            b.def = p.getStat(Stats.DEFENCE);
            b.spa = p.getStat(Stats.SPECIAL_ATTACK);
            b.spd = p.getStat(Stats.SPECIAL_DEFENCE);
            b.spe = p.getStat(Stats.SPEED);
            b.item = itemId(p.heldItem());
            try {
                if (p.getTeraType() != null) b.teraType = Ids.of(p.getTeraType().showdownId());
            } catch (Exception ignored) {
            }
            FormData form = p.getForm();
            b.fullyEvolved = form.getEvolutions().isEmpty();
            b.weightKg = form.getWeight() / 10.0;
            if (!typesKnown) {
                b.baseTypes = typesOf(form);
                b.types = b.baseTypes;
            }
            for (Move mv : p.getMoveSet().getMoves()) {
                MoveInfo mi = moveInfo(mv.getTemplate().getName());
                mi.pp = mv.getCurrentPp();
                b.moves.add(mi);
            }
        } else {
            b.name = sp.getDetails().split(",")[0];
        }
        if (b.moves.isEmpty()) {
            for (String id : sp.getMoves()) b.moves.add(moveInfo(id));
        }
        if (max > 2 && b.maxHp <= 1) b.maxHp = (int) max;
        if (b.maxHp <= 1) b.maxHp = (int) Math.max(1, max);
        b.hp = b.maxHp == (int) max ? cur : cur / Math.max(1, max) * b.maxHp;
        if ("slp".equals(b.status)) b.sleepTurns = sleepLeft(b.uuid);
        return b;
    }

    private static Battler partyBattler(Pokemon p) {
        Battler b = new Battler();
        b.mine = true;
        b.statsExact = true;
        b.uuid = p.getUuid();
        b.name = p.getDisplayName(false).getString();
        b.species = Ids.of(p.getSpecies().showdownId());
        b.level = p.getLevel();
        b.maxHp = p.getMaxHealth();
        b.hp = p.getCurrentHealth();
        b.atk = p.getStat(Stats.ATTACK);
        b.def = p.getStat(Stats.DEFENCE);
        b.spa = p.getStat(Stats.SPECIAL_ATTACK);
        b.spd = p.getStat(Stats.SPECIAL_DEFENCE);
        b.spe = p.getStat(Stats.SPEED);
        b.item = itemId(p.heldItem());
        b.ability = emptyToNull(Ids.of(p.getAbility().getName()));
        if (p.getStatus() != null) b.status = p.getStatus().getStatus().getShowdownName();
        FormData form = p.getForm();
        b.baseTypes = typesOf(form);
        b.types = b.baseTypes;
        b.fullyEvolved = form.getEvolutions().isEmpty();
        b.weightKg = form.getWeight() / 10.0;
        for (Move mv : p.getMoveSet().getMoves()) {
            MoveInfo mi = moveInfo(mv.getTemplate().getName());
            mi.pp = mv.getCurrentPp();
            b.moves.add(mi);
        }
        return b;
    }

    /**
     * Builds the Mega (or Ultra Burst) version of an active Pokemon. Stats shift by the base stat
     * difference; the held stone picks between X and Y forms.
     */
    static Battler megaVariant(Battler current, Pokemon p) {
        if (p == null) return null;
        try {
            return megaFrom(current, p.getSpecies(), p.getForm());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A human opponent's Mega that has not happened yet (MainTick's Blaziken Mega Evolved on switch-in and
     * gained Speed Boost). Only species with a single Mega, while the item can still be its stone and that
     * side has not used its Mega.
     */
    private static Battler foeMega(Battler current, Species species, FormData now, BattleTracker tracker) {
        try {
            if (current.item != null && !current.item.endsWith("ite")) return null;
            for (var e : tracker.opponents()) if (e.getValue().megaEvolved) return null;
            int megas = 0;
            for (FormData f : species.getForms()) if (Ids.of(f.getName()).startsWith("mega")) megas++;
            if (megas != 1) return null;
            return megaFrom(current, species, now);
        } catch (Exception e) {
            return null;
        }
    }

    private static Battler megaFrom(Battler current, Species species, FormData now) {
        try {
            String stone = current.item == null ? "" : current.item;
            FormData best = null;
            for (FormData f : species.getForms()) {
                String n = Ids.of(f.getName());
                if (!n.startsWith("mega") && !n.startsWith("ultra")) continue;
                if (best == null) best = f;
                String suffix = n.replace("mega", "");
                if (!suffix.isEmpty() && stone.endsWith(suffix)) best = f;
            }
            if (best == null || best == now) return null;
            Battler m = current.copy();
            Map<Stat, Integer> from = now.getBaseStats(), to = best.getBaseStats();
            int lvl = current.level;
            m.atk = shifted(current.atk, from, to, Stats.ATTACK, lvl);
            m.def = shifted(current.def, from, to, Stats.DEFENCE, lvl);
            m.spa = shifted(current.spa, from, to, Stats.SPECIAL_ATTACK, lvl);
            m.spd = shifted(current.spd, from, to, Stats.SPECIAL_DEFENCE, lvl);
            m.spe = shifted(current.spe, from, to, Stats.SPEED, lvl);
            m.baseTypes = typesOf(best);
            m.types = m.baseTypes;
            m.weightKg = best.getWeight() / 10.0;
            for (PotentialAbility pa : best.getAbilities()) {
                m.ability = Ids.of(pa.getTemplate().getName());
                break;
            }
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    private static int shifted(int stat, Map<Stat, Integer> from, Map<Stat, Integer> to, Stat key, int level) {
        int diff = to.getOrDefault(key, 0) - from.getOrDefault(key, 0);
        return Math.max(1, (int) Math.round(stat + 2.0 * diff * level / 100.0));
    }

    // ------------------------------------------------------------------ opponents

    private static Battler estimatedBattler(ClientBattlePokemon cbp, boolean allySide, BattleState.OpponentKind kind,
                                            BattleTracker tracker) {
        Battler b = new Battler();
        b.mine = allySide;
        b.uuid = cbp.getUuid();
        b.name = cbp.getDisplayName().getString();
        Species species = cbp.getSpecies();
        FormData form = formOf(species, cbp);
        b.species = Ids.of(species.showdownId());
        b.level = Math.max(1, cbp.getLevel());
        BattleTracker.Knowledge k = tracker.knowledge(cbp.getUuid());
        fillEstimate(b, form, kind, k);

        b.hp = BattleTracker.hpFraction(cbp) * b.maxHp;
        PersistentStatus st = cbp.getStatus();
        if (st != null) b.status = st.getShowdownName();
        if ("slp".equals(b.status)) b.sleepTurns = sleepLeft(b.uuid);
        applyBoosts(b, cbp.getStatChanges());
        applyVolatiles(b, k);
        if (!allySide && kind == BattleState.OpponentKind.PLAYER && !k.megaEvolved) b.pendingMega = foeMega(b, species, form, tracker);
        return b;
    }

    /** A roster member of a known Tower trainer that has not come out yet, built from its bundled set. */
    private static Battler rosterBattler(String trainer, String speciesId, int level, BattleState.OpponentKind kind) {
        try {
            // Looked up by reflection so a renamed Cobblemon API only disables this, never the build.
            Class<?> c = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Object registry = c.getField("INSTANCE").get(null);
            Object found = c.getMethod("getByName", String.class).invoke(registry, speciesId);
            if (!(found instanceof Species species)) return null;
            BattleTracker.Knowledge k = new BattleTracker.Knowledge();
            k.opponent = true;
            k.trainer = trainer;
            k.species = speciesId;
            k.speciesRef = species;
            k.level = level;
            k.name = speciesId;
            com.manueeh.cobbleai.track.OpponentMemory.seed(trainer, speciesId, k);
            UUID id = UUID.nameUUIDFromBytes((trainer + "|" + speciesId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return rememberedOpponent(id, k, kind);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Battler rememberedOpponent(UUID uuid, BattleTracker.Knowledge k, BattleState.OpponentKind kind) {
        try {
            Species species = k.speciesRef;
            if (species == null) return null;
            Battler b = new Battler();
            b.uuid = uuid;
            b.name = k.name;
            b.species = k.species;
            b.level = Math.max(1, k.level);
            fillEstimate(b, formOf(species, k.aspects), kind, k);
            b.hp = k.fainted ? 0 : k.lastHpFrac * b.maxHp;
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static void fillEstimate(Battler b, FormData form, BattleState.OpponentKind kind, BattleTracker.Knowledge k) {
        int iv, ev;
        switch (kind) {
            case WILD -> { iv = 15; ev = 0; }
            case NPC -> { iv = 31; ev = 85; }
            default -> { iv = 31; ev = 84; }
        }
        Map<Stat, Integer> base = form.getBaseStats();
        b.maxHp = hpStat(base.getOrDefault(Stats.HP, 70), iv, ev, b.level);
        b.atk = stat(base.getOrDefault(Stats.ATTACK, 70), iv, ev, b.level);
        b.def = stat(base.getOrDefault(Stats.DEFENCE, 70), iv, ev, b.level);
        b.spa = stat(base.getOrDefault(Stats.SPECIAL_ATTACK, 70), iv, ev, b.level);
        b.spd = stat(base.getOrDefault(Stats.SPECIAL_DEFENCE, 70), iv, ev, b.level);
        b.spe = stat(base.getOrDefault(Stats.SPEED, 70), iv, ev, b.level);
        if (kind != BattleState.OpponentKind.WILD && base.getOrDefault(Stats.SPEED, 70) >= 80) {
            // Fast trained Pokemon invest fully in Speed (252 EVs + nature): do not assume we outspeed them.
            b.spe = (int) Math.floor(stat(base.getOrDefault(Stats.SPEED, 70), 31, 252, b.level) * 1.1);
        }
        if (kind != BattleState.OpponentKind.WILD) {
            // Trained sets max their main attacking stat (252 EVs + boosting nature).
            int baseAtk = base.getOrDefault(Stats.ATTACK, 70), baseSpa = base.getOrDefault(Stats.SPECIAL_ATTACK, 70);
            int phys = 0, spec = 0;
            if (k != null) {
                for (String id : k.moves) {
                    MoveInfo seen = moveInfo(id);
                    if (seen.category == Category.PHYSICAL) phys++;
                    else if (seen.category == Category.SPECIAL) spec++;
                }
            }
            boolean physical;
            if (phys != spec) physical = phys > spec;
            else if (Math.abs(baseAtk - baseSpa) <= 10) physical = false;
            else physical = baseAtk > baseSpa;
            if (phys == 0 && spec == 0 && Math.abs(baseAtk - baseSpa) <= 10) {
                // Even split (Lugia, Dragonite-likes): assume either side can hit hard until moves show which.
                b.atk = stat(baseAtk, 31, 252, b.level);
                b.spa = stat(baseSpa, 31, 252, b.level);
            } else if (physical) {
                b.atk = (int) Math.floor(stat(baseAtk, 31, 252, b.level) * 1.1);
            } else {
                b.spa = (int) Math.floor(stat(baseSpa, 31, 252, b.level) * 1.1);
            }
        }
        if ("shedinja".equals(b.species)) b.maxHp = 1;
        b.statsExact = false;
        b.baseTypes = typesOf(form);
        b.types = b.baseTypes;
        b.fullyEvolved = form.getEvolutions().isEmpty();
        b.weightKg = form.getWeight() / 10.0;

        List<String> abilities = new ArrayList<>();
        try {
            for (PotentialAbility pa : form.getAbilities()) abilities.add(Ids.of(pa.getTemplate().getName()));
        } catch (Exception ignored) {
        }
        if (kind != BattleState.OpponentKind.WILD) {
            // Trained sets pick the ability that matters: weight immunity abilities up.
            List<String> weighted = new ArrayList<>(abilities);
            for (String a : abilities) if (com.manueeh.cobbleai.data.AbilityDex.TYPE_IMMUNITY.containsKey(a)
                || "levitate".equals(a) || "intimidate".equals(a)) weighted.add(a);
            abilities = weighted;
        }
        b.possibleAbilities = abilities;
        if (k != null) {
            if (k.ability == null && !k.immuneTo.isEmpty()) {
                // An immunity its typing does not explain reveals the ability.
                for (String a : abilities) {
                    String immune = com.manueeh.cobbleai.data.AbilityDex.TYPE_IMMUNITY.get(a);
                    if (immune != null && k.immuneTo.contains(immune)) k.ability = a;
                }
            }
            b.lastMove = k.lastMove;
            b.lastMoveStreak = k.lastMoveStreak;
            b.lastTarget = k.lastTarget;
            if (k.ability != null) b.ability = k.ability;
            if (k.item != null && !k.itemGone) b.item = k.item;
            if (k.terastallized && k.teraType != null) {
                b.teraType = k.teraType;
                b.terastallize();
            }
        }
        if (k != null) {
            b.atk = (int) Math.round(b.atk * k.powerMult[0]);
            b.spa = (int) Math.round(b.spa * k.powerMult[1]);
            b.def = (int) Math.round(b.def * k.bulkMult[0]);
            b.spd = (int) Math.round(b.spd * k.bulkMult[1]);
            b.speedKnown = k.minSpe > 0 || k.maxSpe < Double.MAX_VALUE;
            if (k.minSpe > b.spe) b.spe = (int) Math.ceil(k.minSpe);
            else if (k.maxSpe < b.spe && k.maxSpe >= k.minSpe) b.spe = (int) Math.floor(k.maxSpe);
        }
        b.moves = predictMoves(form, b, kind, k);
        if (kind != BattleState.OpponentKind.WILD) addStatusThreats(form, b, k);
        if (kind != BattleState.OpponentKind.WILD && form.getBaseStats().getOrDefault(Stats.SPEED, 100) <= 60
            && learnsByLevel(form, "trickroom")) {
            boolean has = false;
            for (MoveInfo m : b.moves) has |= "trickroom".equals(m.id);
            if (!has) {
                MoveInfo tr = moveInfo("trickroom");
                tr.revealed = false;
                b.moves = new ArrayList<>(b.moves);
                b.moves.add(tr);
            }
        }
        if (kind != BattleState.OpponentKind.WILD && (k == null || k.turnsOnField == 0) && canLearn(form, b.species, "fakeout")) {
            boolean has = false;
            for (MoveInfo m : b.moves) has |= "fakeout".equals(m.id);
            if (!has) {
                MoveInfo fo = moveInfo("fakeout");
                fo.revealed = false;
                b.moves = new ArrayList<>(b.moves);
                b.moves.add(fo);
            }
        }
        // Rock Slide is the Tower trainers' favourite attack (Krookodile, Mawile, Excadrill, Garchomp...): keep it
        // in view for any physical trainer Pokemon that can learn it, unless its full set is already known.
        if (kind != BattleState.OpponentKind.WILD && b.atk >= b.spa && (k == null || k.moves.size() < 4)
            && canLearn(form, b.species, "rockslide")) {
            boolean has = false;
            for (MoveInfo m : b.moves) has |= "rockslide".equals(m.id);
            if (!has) {
                MoveInfo rs = moveInfo("rockslide");
                rs.revealed = false;
                b.moves = new ArrayList<>(b.moves);
                b.moves.add(rs);
            }
        }
        b.itemUnknown = kind != BattleState.OpponentKind.WILD && b.item == null && (k == null || !k.itemGone);
    }

    private static int hpStat(int base, int iv, int ev, int level) {
        return (int) Math.floor((2.0 * base + iv + ev / 4.0) * level / 100.0) + level + 10;
    }

    private static int stat(int base, int iv, int ev, int level) {
        return (int) Math.floor((2.0 * base + iv + ev / 4.0) * level / 100.0) + 5;
    }

    private static List<MoveInfo> predictMoves(FormData form, Battler b, BattleState.OpponentKind kind,
                                               BattleTracker.Knowledge k) {
        List<MoveInfo> out = new ArrayList<>();
        Set<String> have = new LinkedHashSet<>();
        if (k != null) {
            for (String id : k.moves) {
                if (have.add(id)) {
                    MoveInfo m = moveInfo(id);
                    m.revealed = true;
                    out.add(m);
                }
            }
        }
        if (out.size() >= 4) return out;
        // Cobblemon gives wild Pokémon the latest level-up moves they could know.
        List<Map.Entry<Integer, List<MoveTemplate>>> levels = new ArrayList<>(form.getMoves().getLevelUpMoves().entrySet());
        levels.sort(Comparator.comparingInt(Map.Entry::getKey));
        List<MoveTemplate> learned = new ArrayList<>();
        for (var e : levels) {
            if (e.getKey() > b.level) break;
            learned.addAll(e.getValue());
        }
        List<MoveInfo> predicted = new ArrayList<>();
        for (int i = learned.size() - 1; i >= 0 && predicted.size() < 4; i--) {
            String id = Ids.of(learned.get(i).getName());
            if (have.contains(id)) continue;
            have.add(id);
            MoveInfo m = moveInfo(id);
            m.revealed = false;
            predicted.add(m);
        }
        if (kind != BattleState.OpponentKind.WILD) {
            predicted.removeIf(m -> !m.isDamaging() || m.power < 60);
            predicted.sort(Comparator.comparingDouble((MoveInfo m) -> -m.power));
        }
        int room = kind == BattleState.OpponentKind.WILD ? 4 - out.size() : 0;
        for (int i = 0; i < predicted.size() && i < room; i++) out.add(predicted.get(i));

        if (kind != BattleState.OpponentKind.WILD && out.size() < 4) {
            // Trained Pokemon run real sets: fill the unknown slots with their best STAB and the
            // coverage moves from their legal movepool that hurt our team the most.
            List<MoveInfo> coverage = coverageMoves(form, b, out, 4 - out.size());
            for (MoveInfo m : coverage) {
                if (have.add(m.id)) out.add(m);
            }
        }
        if (kind != BattleState.OpponentKind.WILD) {
            // Trained Pokémon usually carry strong STAB: add a placeholder for each uncovered type.
            boolean physical = b.atk >= b.spa;
            for (String t : b.baseTypes) {
                boolean covered = false;
                // A weak guessed STAB (Luster Purge, Dragon Pulse...) must not hide the premium one
                // (Psychic, Draco Meteor...) that trained Pokemon actually run.
                for (MoveInfo m : out) if (isPremiumStab(m, t)) covered = true;
                if (covered) continue;
                MoveInfo ph = new MoveInfo("phantom" + t);
                ph.displayName = "?" + t;
                ph.type = t;
                ph.category = physical ? Category.PHYSICAL : Category.SPECIAL;
                ph.power = kind == BattleState.OpponentKind.PLAYER ? 100 : 95;
                ph.accuracy = 1;
                ph.target = "normal";
                ph.revealed = false;
                out.add(ph);
            }
        }
        return out;
    }

    /**
     * Picks likely moves for a trained opponent from its full legal movepool: the strongest STAB
     * first, then the move that hits the most dangerous super-effective angle on our team.
     */
    private static List<MoveInfo> coverageMoves(FormData form, Battler b, List<MoveInfo> already, int slots) {
        List<MoveInfo> out = new ArrayList<>();
        if (slots <= 0) return out;
        Set<String> pool = legalPool(form, b.species);
        boolean physical = b.atk >= b.spa * 1.1;
        boolean special = b.spa >= b.atk * 1.1;
        List<MoveInfo> cands = new ArrayList<>();
        for (String id : pool) {
            MoveInfo m = moveInfo(id);
            if (!m.isDamaging() || m.power < 60 || m.accuracy < 0.7) continue;
            if (MoveDex.SITUATIONAL.contains(m.id) || MoveDex.AVOID.contains(m.id)) continue;
            if (MoveDex.CHARGE.contains(m.id) || MoveDex.RECHARGE.contains(m.id) || MoveDex.SELF_KO.contains(m.id)
                || MoveDex.FIRST_TURN_ONLY.contains(m.id) || MoveDex.OHKO.contains(m.id)) continue;
            if (physical && m.category != Category.PHYSICAL) continue;
            if (special && m.category != Category.SPECIAL) continue;
            m.revealed = false;
            cands.add(m);
        }
        if (cands.isEmpty()) return out;
        List<MoveInfo> chosen = new ArrayList<>(already);
        // Greedy by threat: every pick is the move that adds the most damage against our team,
        // so a strong STAB comes first and the 4x coverage (Ice Spinner vs Garchomp) is not skipped.
        while (out.size() < slots) {
            MoveInfo bestMove = null;
            double bestGain = 0;
            for (MoveInfo m : cands) {
                if (chosen.contains(m)) continue;
                double gain = 0;
                for (Battler target : currentMyTeam) {
                    if (!target.alive()) continue;
                    double current = 0;
                    for (MoveInfo c : chosen) if (c.isDamaging()) current = Math.max(current, moveValue(c, b, target));
                    gain += Math.max(0, moveValue(m, b, target) - current);
                }
                if (gain > bestGain) {
                    bestGain = gain;
                    bestMove = m;
                }
            }
            if (bestMove == null) break;
            out.add(bestMove);
            chosen.add(bestMove);
        }
        return out;
    }

    /** Damaging move of {@code type} strong enough (power x accuracy) to be a trained Pokemon's main STAB. */
    private static boolean isPremiumStab(MoveInfo m, String type) {
        return m.isDamaging() && type.equals(m.type) && m.power * Math.min(1.0, m.accuracy) >= 88;
    }

    private static final String[] SLEEP_MOVES = {"spore", "sleeppowder", "darkvoid", "lovelykiss", "hypnosis", "sing", "yawn"};
    private static final String[] SETUP_PHYS = {"swordsdance", "dragondance", "bulkup", "shellsmash", "shiftgear", "victorydance", "coil"};
    private static final String[] SETUP_SPEC = {"nastyplot", "quiverdance", "calmmind", "shellsmash", "tailglow", "takeheart"};

    /** Adds the sleep move and the setup move a trained opponent would realistically carry. */
    private static void addStatusThreats(FormData form, Battler b, BattleTracker.Knowledge k) {
        Set<String> pool = legalPool(form, b.species);
        if (pool.isEmpty()) return;
        List<MoveInfo> moves = new ArrayList<>(b.moves);
        boolean revealedFull = k != null && k.moves.size() >= 4;
        if (revealedFull) return;
        for (String id : SLEEP_MOVES) {
            if (!pool.contains(id)) continue;
            MoveInfo m = moveInfo(id);
            if (m.accuracy < 0.7 && !"yawn".equals(id)) continue;
            boolean has = false;
            for (MoveInfo x : moves) has |= x.id.equals(id);
            if (!has) {
                m.revealed = false;
                moves.add(m);
            }
            break;
        }
        String[] setups = b.atk >= b.spa ? SETUP_PHYS : SETUP_SPEC;
        for (String id : setups) {
            if (!pool.contains(id)) continue;
            boolean has = false;
            for (MoveInfo x : moves) has |= x.id.equals(id);
            if (!has) {
                MoveInfo m = moveInfo(id);
                m.revealed = false;
                moves.add(m);
            }
            break;
        }
        // Tower sets are full of Protect and speed control: add what the pool has (a couple at most).
        int support = 0;
        if (pool.contains("protect") || pool.contains("detect")) {
            String id = pool.contains("protect") ? "protect" : "detect";
            boolean has = false;
            for (MoveInfo x : moves) has |= x.id.equals(id);
            if (!has) {
                MoveInfo m = moveInfo(id);
                m.revealed = false;
                moves.add(m);
            }
        }
        // Singles sets carry Will-O-Wisp / Thunder Wave, not Helping Hand or Follow Me (Flint's Drifblim burned
        // our Garchomp while we expected Tailwind).
        for (String id : buildingDoubles ? MoveDex.SUPPORT_PRIORITY : MoveDex.SUPPORT_SINGLES) {
            if (support >= 1) break;
            if (!pool.contains(id)) continue;
            boolean prankster = b.possibleAbilities.contains("prankster");
            // Speed control only from Pokemon that would use it (Prankster users, slow Trick Room setters).
            if ("tailwind".equals(id) && !prankster && b.spe < 90) continue;
            if ("trickroom".equals(id) && b.spe > 70) continue;
            boolean has = false;
            for (MoveInfo x : moves) has |= x.id.equals(id);
            if (!has) {
                MoveInfo m = moveInfo(id);
                m.revealed = false;
                moves.add(m);
            }
            support++;
        }
        b.moves = moves;
    }

    private static boolean learnsByLevel(FormData form, String moveId) {
        try {
            for (List<MoveTemplate> l : form.getMoves().getLevelUpMoves().values()) {
                for (MoveTemplate t : l) if (Ids.of(t.getName()).equals(moveId)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean canLearn(FormData form, String species, String moveId) {
        return legalPool(form, species).contains(moveId);
    }

    /**
     * Every move the species can legally know. The client only has level-up learnsets synced, so the
     * full pool (TM, tutor, egg) comes from the species files inside the installed mod jars.
     */
    private static Set<String> legalPool(FormData form, String species) {
        Set<String> pool = new java.util.HashSet<>(com.manueeh.cobbleai.data.MovepoolIndex.movesOf(species));
        try {
            for (MoveTemplate t : form.getMoves().getAllLegalMoves()) pool.add(Ids.of(t.getName()));
        } catch (Exception ignored) {
        }
        return pool;
    }

    private static double moveValue(MoveInfo m, Battler user, Battler target) {
        // Aerilate / Pixilate / Refrigerate / Galvanize turn Normal moves into strong STAB-like attacks.
        String type = com.manueeh.cobbleai.engine.DamageCalc.effectiveType(user, m, new com.manueeh.cobbleai.model.Field());
        double skin = !type.equals(m.type) && "normal".equals(m.type) ? 1.2 : 1.0;
        double spreadBonus = m.isSpread() ? 1.15 : 1.0;
        double eff = TypeChart.against(type, target.types) * skin * spreadBonus;
        if ("ground".equals(m.type) && (target.hasType("flying") || target.hasAbility("levitate"))) eff = 0;
        double stab = 1;
        for (String t : user.baseTypes) if (t.equals(type)) stab = 1.5;
        double atk = m.category == Category.PHYSICAL ? user.atk : user.spa;
        double def = m.category == Category.PHYSICAL ? target.def : target.spd;
        // Fraction of the target's HP, roughly: enough to rank moves against each other.
        return Math.min(1.5, m.power * m.accuracy * eff * stab * atk / Math.max(1, def) / Math.max(1, target.maxHp) * 0.45);
    }

    // ------------------------------------------------------------------ shared helpers

    public static MoveInfo moveInfo(String rawId) {
        String id = Ids.of(rawId);
        MoveInfo m = new MoveInfo(id);
        MoveTemplate t = null;
        try {
            t = Moves.getByName(id);
        } catch (Exception ignored) {
        }
        if (t == null) {
            m.displayName = rawId;
            m.type = "normal";
            m.category = "recharge".equals(id) ? Category.STATUS : Category.PHYSICAL;
            m.power = "struggle".equals(id) ? 50 : 0;
            m.accuracy = 1;
            m.target = "recharge".equals(id) ? "self" : "normal";
            return m;
        }
        m.displayName = t.getDisplayName().getString();
        m.type = Ids.of(t.getElementalType().getName());
        if (!TypeChart.isType(m.type)) m.type = "normal";
        String cat = Ids.of(t.getDamageCategory().getName());
        m.category = switch (cat) {
            case "physical" -> Category.PHYSICAL;
            case "special" -> Category.SPECIAL;
            default -> Category.STATUS;
        };
        m.power = t.getPower();
        double acc = t.getAccuracy();
        m.accuracy = acc <= 0 ? 1 : Math.min(1, acc > 1 ? acc / 100.0 : acc);
        m.priority = t.getPriority();
        m.target = t.getTarget().name();
        m.pp = t.getPp();
        Double[] chances = t.getEffectChances();
        if (chances != null && chances.length > 0 && chances[0] != null && chances[0] > 0) {
            m.hasSecondary = true;
            m.effectChance = chances[0] > 1 ? chances[0] / 100.0 : chances[0];
        }
        return m;
    }

    /** Z-move / Max-move stand-in built from the base move. */
    private static MoveInfo gimmickMove(InBattleGimmickMove g, MoveInfo base, boolean zMove) {
        MoveInfo m = moveInfo(g.getMove());
        m.target = g.getTarget().name();
        if (base.isDamaging()) {
            m.type = base.type;
            m.category = base.category;
            m.accuracy = 1;
            double p = base.power;
            if (zMove) {
                m.power = p <= 55 ? 100 : p <= 65 ? 120 : p <= 75 ? 140 : p <= 85 ? 160 : p <= 95 ? 175
                    : p <= 100 ? 180 : p <= 110 ? 185 : p <= 125 ? 190 : p <= 130 ? 195 : 200;
            } else {
                boolean weak = "fighting".equals(base.type) || "poison".equals(base.type);
                m.power = p <= 40 ? (weak ? 70 : 90) : p <= 50 ? (weak ? 75 : 100) : p <= 60 ? (weak ? 80 : 110)
                    : p <= 70 ? (weak ? 85 : 120) : p <= 100 ? (weak ? 90 : 130) : p <= 140 ? (weak ? 95 : 140) : (weak ? 100 : 150);
            }
            if (m.power < 1) m.power = 100;
        }
        return m;
    }

    private static FormData formOf(Species species, Set<String> aspects) {
        try {
            if (aspects != null && !aspects.isEmpty()) return species.getForm(aspects);
        } catch (Exception ignored) {
        }
        return species.getStandardForm();
    }

    /**
     * The real form of a battle Pokemon (Rotom-Wash, Mega Salamence, Alolan...). The live aspects sit in a
     * private field that Cobblemon updates on Mega Evolution / form changes; the properties only carry the
     * form name. Both are tried before falling back to the base form.
     */
    static FormData formOf(Species species, ClientBattlePokemon cbp) {
        Set<String> aspects = aspectsOf(cbp);
        if (aspects != null && !aspects.isEmpty()) {
            try {
                FormData f = species.getForm(aspects);
                if (f != null && f != species.getStandardForm()) return f;
            } catch (Exception ignored) {
            }
        }
        try {
            String name = cbp.getProperties().getForm();
            if (name != null && !name.isEmpty()) {
                FormData f = species.getFormByName(name);
                if (f == null) f = species.getFormByShowdownId(Ids.of(name));
                if (f != null) return f;
            }
        } catch (Exception ignored) {
        }
        return formOf(species, aspects);
    }

    @SuppressWarnings("unchecked")
    public static Set<String> aspectsOf(ClientBattlePokemon cbp) {
        try {
            java.lang.reflect.Field f = ClientBattlePokemon.class.getDeclaredField("aspects");
            f.setAccessible(true);
            Object v = f.get(cbp);
            if (v instanceof Set<?> set && !set.isEmpty()) return (Set<String>) set;
        } catch (Exception ignored) {
        }
        try {
            return cbp.getProperties().getAspects();
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] typesOf(FormData form) {
        List<String> t = new ArrayList<>();
        for (ElementalType e : form.getTypes()) t.add(Ids.of(e.getName()));
        if (t.isEmpty()) t.add("normal");
        return t.toArray(new String[0]);
    }

    private static void applyBoosts(Battler b, Map<Stat, Integer> changes) {
        if (changes == null) return;
        for (var e : changes.entrySet()) {
            int v = e.getValue() == null ? 0 : e.getValue();
            Stat st = e.getKey();
            if (st == Stats.ATTACK) b.boosts[MoveDex.ATK] = v;
            else if (st == Stats.DEFENCE) b.boosts[MoveDex.DEF] = v;
            else if (st == Stats.SPECIAL_ATTACK) b.boosts[MoveDex.SPA] = v;
            else if (st == Stats.SPECIAL_DEFENCE) b.boosts[MoveDex.SPD] = v;
            else if (st == Stats.SPEED) b.boosts[MoveDex.SPE] = v;
            else if (st == Stats.ACCURACY) b.boosts[MoveDex.ACC] = v;
            else if (st == Stats.EVASION) b.boosts[MoveDex.EVA] = v;
        }
    }

    private static void applyVolatiles(Battler b, BattleTracker.Knowledge k) {
        if (k == null) return;
        for (int i = 0; i < b.boosts.length && i < k.boosts.length; i++) {
            b.boosts[i] = Math.max(-6, Math.min(6, b.boosts[i] + k.boosts[i]));
        }
        b.turnsActive = k.turnsOnField;
        b.substitute = k.substitute;
        b.confused = k.confused;
        b.leechSeeded = k.leechSeeded;
        b.taunted = k.taunted;
        b.flashFire = k.flashFireActive;
        b.drowsy = k.drowsy && b.status == null;
        b.protectStreak = k.protectStreak;
        b.protectUses = k.protectUses;
        b.wideGuardUses = k.wideGuardUses;
        b.quickGuardUses = k.quickGuardUses;
        if ("slp".equals(b.status)) {
            // Sleep lasts 1-3 turns and its counter is hidden: estimate what is left from how long it has slept.
            int now = BattleTracker.INSTANCE.turn();
            if (k.asleepSeenTurn != now) {
                k.asleepSeenTurn = now;
                k.asleepTurns++;
            }
            int slept = Math.max(0, k.asleepTurns - 1);
            b.sleepTurns = slept == 0 ? 2 : slept == 1 ? 1 : slept == 2 ? (b.mine ? 1 : 0) : 0;
        } else {
            k.asleepTurns = 0;
            k.asleepSeenTurn = -1;
        }
        if (!b.mine && ItemDex.isChoice(b.item) && k.lastMove != null) b.lockedMove = k.lastMove;
        if (k.terastallized && k.teraType != null && !b.terastallized) {
            b.teraType = k.teraType;
            b.terastallize();
        }
    }

    /** Expected sleep turns left: 2 when it just fell asleep, 1 after it already slept through turns. */
    private static int sleepLeft(UUID uuid) {
        if (uuid == null) return 2;
        BattleTracker.Knowledge k = BattleTracker.INSTANCE.knowledge(uuid);
        int skips = k.sleepSkips;
        if (skips <= 0) return 2;
        // After a turn asleep it wakes 1/3 of the time (1-3 turns). For a foe, plan as if it can act:
        // #28 (Enelsitio02) a 1% Landorus "still asleep" woke up and Rock Slid Mega Charizard.
        return k.opponent ? 0 : 1;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return emptyToNull(Ids.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
