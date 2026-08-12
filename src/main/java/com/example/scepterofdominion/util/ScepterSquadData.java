package com.example.scepterofdominion.util;

import com.example.scepterofdominion.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class ScepterSquadData {
    public static final String ROOT_KEY = "ScepterSquadData";
    public static final String ROOT_KEY_DOMINION = "DominionSquadData";
    public static final String SQUADS_KEY = "Squads";
    public static final String SELECTED_SQUAD_KEY = "SelectedSquad";
    public static final String TEAM_KEY = "Team";
    public static final String FOCUS_KEY = "Focus";
    public static final String FORMATION_KEY = "Formation";
    public static final String FORMATION_ENABLED_KEY = "FormationEnabled";
    public static final String MODE_KEY = "Mode";
    public static final String TASK_KEY = "Task";
    public static final String COMMAND_TARGET_KEY = "CommandTarget";
    public static final String ATTACK_TARGET_KEY = "AttackTarget";
    public static final String WAYPOINTS_KEY = "Waypoints";
    public static final String MOUNT_KEY = "Mount";
    public static final String MAX_SQUADS_CACHE_KEY = "MaxSquadsCache";
    public static final String MAX_MEMBERS_CACHE_KEY = "MaxMembersCache";
    public static final int TASK_GUARD = 0;
    public static final int TASK_HOLD = 1;
    public static final int TASK_FOLLOW_PROTECT = 2;

    private ScepterSquadData() {
    }

    public static int getServerMaxSquads() {
        return Math.max(1, Config.COMMON.maxSquads.get());
    }

    public static int getServerMaxMembers() {
        return Math.max(1, Config.COMMON.maxSquadMembers.get());
    }

    public static int getCachedMaxSquads(@Nullable Player player) {
        return getCachedMaxSquads(player, ROOT_KEY);
    }

    public static int getCachedMaxSquads(@Nullable Player player, String rootKey) {
        if (player == null) {
            return getServerMaxSquads();
        }
        CompoundTag root = getRoot(player, rootKey);
        return root.contains(MAX_SQUADS_CACHE_KEY, Tag.TAG_INT) ? Math.max(1, root.getInt(MAX_SQUADS_CACHE_KEY)) : getServerMaxSquads();
    }

    public static int getCachedMaxMembers(@Nullable Player player) {
        return getCachedMaxMembers(player, ROOT_KEY);
    }

    public static int getCachedMaxMembers(@Nullable Player player, String rootKey) {
        if (player == null) {
            return getServerMaxMembers();
        }
        CompoundTag root = getRoot(player, rootKey);
        return root.contains(MAX_MEMBERS_CACHE_KEY, Tag.TAG_INT) ? Math.max(1, root.getInt(MAX_MEMBERS_CACHE_KEY)) : getServerMaxMembers();
    }

    public static CompoundTag getRoot(Player player) {
        return getRoot(player, ROOT_KEY);
    }

    public static CompoundTag getRoot(Player player, String rootKey) {
        CompoundTag root;

        if (!player.level().isClientSide && player.getServer() != null) {
            ServerLevel overworld = player.getServer().overworld();
            ScepterSquadSavedData savedData = ScepterSquadSavedData.get(overworld);

            CompoundTag persistentData = player.getPersistentData();
            if (persistentData.contains(rootKey, Tag.TAG_COMPOUND)) {
                CompoundTag legacy = persistentData.getCompound(rootKey);
                CompoundTag saved = savedData.getPlayerRoot(player.getUUID(), rootKey);
                if (!saved.contains(SQUADS_KEY, Tag.TAG_LIST) || saved.getList(SQUADS_KEY, Tag.TAG_COMPOUND).isEmpty()) {
                    saved.merge(legacy);
                    savedData.setDirty();
                }
                persistentData.remove(rootKey);
            }

            root = savedData.getPlayerRoot(player.getUUID(), rootKey);
            savedData.setDirty();
        } else {
            CompoundTag persistentData = player.getPersistentData();
            if (!persistentData.contains(rootKey, Tag.TAG_COMPOUND)) {
                persistentData.put(rootKey, new CompoundTag());
            }
            root = persistentData.getCompound(rootKey);
        }

        normalizeRoot(root);
        return root;
    }

    public static int getSelectedSquadIndex(Player player) {
        return getSelectedSquadIndex(player, ROOT_KEY);
    }

    public static int getSelectedSquadIndex(Player player, String rootKey) {
        CompoundTag root = getRoot(player, rootKey);
        int maxSquads = getServerMaxSquads();
        int selected = clamp(root.getInt(SELECTED_SQUAD_KEY), 0, maxSquads - 1);
        root.putInt(SELECTED_SQUAD_KEY, selected);
        ensureSquadCount(root, selected + 1);
        return selected;
    }

    public static void setSelectedSquadIndex(Player player, int index) {
        setSelectedSquadIndex(player, index, ROOT_KEY);
    }

    public static void setSelectedSquadIndex(Player player, int index, String rootKey) {
        CompoundTag root = getRoot(player, rootKey);
        int selected = clamp(index, 0, getServerMaxSquads() - 1);
        root.putInt(SELECTED_SQUAD_KEY, selected);
        ensureSquadCount(root, selected + 1);
    }

    public static void cycleSelectedSquad(Player player, int delta) {
        cycleSelectedSquad(player, delta, ROOT_KEY);
    }

    public static void cycleSelectedSquad(Player player, int delta, String rootKey) {
        int maxSquads = getServerMaxSquads();
        int current = getSelectedSquadIndex(player, rootKey);
        int next = (current + delta) % maxSquads;
        if (next < 0) {
            next += maxSquads;
        }
        setSelectedSquadIndex(player, next, rootKey);
    }

    public static CompoundTag getCurrentSquad(Player player) {
        return getCurrentSquad(player, ROOT_KEY);
    }

    public static CompoundTag getCurrentSquad(Player player, String rootKey) {
        return getSquad(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    public static CompoundTag getSquad(Player player, int squadIndex) {
        return getSquad(player, squadIndex, ROOT_KEY);
    }

    public static CompoundTag getSquad(Player player, int squadIndex, String rootKey) {
        CompoundTag root = getRoot(player, rootKey);
        int selected = clamp(squadIndex, 0, getServerMaxSquads() - 1);
        ensureSquadCount(root, selected + 1);
        return root.getList(SQUADS_KEY, Tag.TAG_COMPOUND).getCompound(selected);
    }

    public static int getCurrentTeamSize(Player player) {
        return getTeam(player).size();
    }

    public static int getCurrentTeamSize(Player player, String rootKey) {
        return getTeam(player, rootKey).size();
    }

    public static List<UUID> getTeam(Player player) {
        return getTeam(player, ROOT_KEY);
    }

    public static List<UUID> getTeam(Player player, String rootKey) {
        return getTeam(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    public static List<UUID> getTeam(Player player, int squadIndex) {
        return getTeam(player, squadIndex, ROOT_KEY);
    }

    public static List<UUID> getTeam(Player player, int squadIndex, String rootKey) {
        List<UUID> team = new ArrayList<>();
        ListTag members = getSquad(player, squadIndex, rootKey).getList(TEAM_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (member.hasUUID("UUID")) {
                team.add(member.getUUID("UUID"));
            }
        }
        return team;
    }

    public static List<CompoundTag> getTeamInfo(Player player) {
        return getTeamInfo(player, ROOT_KEY);
    }

    public static List<CompoundTag> getTeamInfo(Player player, String rootKey) {
        return getTeamInfo(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    public static List<CompoundTag> getTeamInfo(Player player, int squadIndex) {
        return getTeamInfo(player, squadIndex, ROOT_KEY);
    }

    public static List<CompoundTag> getTeamInfo(Player player, int squadIndex, String rootKey) {
        List<CompoundTag> info = new ArrayList<>();
        ListTag members = getSquad(player, squadIndex, rootKey).getList(TEAM_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            info.add(members.getCompound(i).copy());
        }
        return info;
    }

    public static int findSquadIndexContaining(Player player, UUID entityId) {
        return findSquadIndexContaining(player, entityId, ROOT_KEY);
    }

    public static int findSquadIndexContaining(Player player, UUID entityId, String rootKey) {
        CompoundTag root = getRoot(player, rootKey);
        ListTag squads = root.getList(SQUADS_KEY, Tag.TAG_COMPOUND);
        int maxSquads = Math.min(Math.max(1, squads.size()), getServerMaxSquads());
        for (int i = 0; i < maxSquads; i++) {
            ListTag team = squads.getCompound(i).getList(TEAM_KEY, Tag.TAG_COMPOUND);
            for (int j = 0; j < team.size(); j++) {
                CompoundTag member = team.getCompound(j);
                if (member.hasUUID("UUID") && member.getUUID("UUID").equals(entityId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static boolean isPetInAnySquad(Player player, UUID entityId) {
        return findSquadIndexContaining(player, entityId) >= 0;
    }

    public static boolean isPetInAnySquad(Player player, UUID entityId, String rootKey) {
        return findSquadIndexContaining(player, entityId, rootKey) >= 0;
    }

    public static void addTeamMember(Player player, CompoundTag member) {
        addTeamMember(player, member, ROOT_KEY);
    }

    public static void addTeamMember(Player player, CompoundTag member, String rootKey) {
        CompoundTag squad = getCurrentSquad(player, rootKey);
        ListTag members = squad.getList(TEAM_KEY, Tag.TAG_COMPOUND);
        members.add(member);
        squad.put(TEAM_KEY, members);
    }

    public static boolean removeTeamMember(Player player, UUID entityId) {
        return removeTeamMember(player, entityId, ROOT_KEY);
    }

    public static boolean removeTeamMember(Player player, UUID entityId, String rootKey) {
        return removeTeamMember(player, getSelectedSquadIndex(player, rootKey), entityId, rootKey);
    }

    public static boolean removeTeamMember(Player player, int squadIndex, UUID entityId) {
        return removeTeamMember(player, squadIndex, entityId, ROOT_KEY);
    }

    public static boolean removeTeamMember(Player player, int squadIndex, UUID entityId, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        ListTag members = squad.getList(TEAM_KEY, Tag.TAG_COMPOUND);
        boolean removed = false;
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (member.hasUUID("UUID") && member.getUUID("UUID").equals(entityId)) {
                members.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            squad.put(TEAM_KEY, members);
            UUID focus = getFocus(player, squadIndex, rootKey);
            if (focus != null && focus.equals(entityId)) {
                if (members.isEmpty()) {
                    clearFocus(player, squadIndex, rootKey);
                } else {
                    setFocus(player, squadIndex, members.getCompound(0).getUUID("UUID"), rootKey);
                }
            }
        }

        return removed;
    }

    public static int getMode(Player player) {
        return getMode(player, ROOT_KEY);
    }

    public static int getMode(Player player, String rootKey) {
        return isFormationEnabled(player, rootKey) ? 1 : 0;
    }

    public static int getMode(Player player, int squadIndex) {
        return getMode(player, squadIndex, ROOT_KEY);
    }

    public static int getMode(Player player, int squadIndex, String rootKey) {
        return isFormationEnabled(player, squadIndex, rootKey) ? 1 : 0;
    }

    public static void setMode(Player player, int mode) {
        setMode(player, mode, ROOT_KEY);
    }

    public static void setMode(Player player, int mode, String rootKey) {
        setFormationEnabled(player, mode != 0, rootKey);
    }

    public static void setMode(Player player, int squadIndex, int mode) {
        setMode(player, squadIndex, mode, ROOT_KEY);
    }

    public static void setMode(Player player, int squadIndex, int mode, String rootKey) {
        setFormationEnabled(player, squadIndex, mode != 0, rootKey);
    }

    public static int getTask(Player player) {
        return getTask(player, ROOT_KEY);
    }

    public static int getTask(Player player, String rootKey) {
        return getTask(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    public static int getTask(Player player, int squadIndex) {
        return getTask(player, squadIndex, ROOT_KEY);
    }

    public static int getTask(Player player, int squadIndex, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        int task = squad.getInt(TASK_KEY);
        if (task < TASK_GUARD || task > TASK_FOLLOW_PROTECT) {
            task = TASK_GUARD;
            squad.putInt(TASK_KEY, task);
        }
        return task;
    }

    public static void setTask(Player player, int task) {
        setTask(player, task, ROOT_KEY);
    }

    public static void setTask(Player player, int task, String rootKey) {
        setTask(player, getSelectedSquadIndex(player, rootKey), task, rootKey);
    }

    public static void setTask(Player player, int squadIndex, int task) {
        setTask(player, squadIndex, task, ROOT_KEY);
    }

    public static void setTask(Player player, int squadIndex, int task, String rootKey) {
        getSquad(player, squadIndex, rootKey).putInt(TASK_KEY, clamp(task, TASK_GUARD, TASK_FOLLOW_PROTECT));
    }

    public static int getFormation(Player player) {
        return getFormation(player, ROOT_KEY);
    }

    public static int getFormation(Player player, String rootKey) {
        return getFormation(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    public static int getFormation(Player player, int squadIndex) {
        return getFormation(player, squadIndex, ROOT_KEY);
    }

    public static int getFormation(Player player, int squadIndex, String rootKey) {
        return getSquad(player, squadIndex, rootKey).getInt(FORMATION_KEY);
    }

    public static void setFormation(Player player, int formation) {
        setFormation(player, formation, ROOT_KEY);
    }

    public static void setFormation(Player player, int formation, String rootKey) {
        setFormation(player, getSelectedSquadIndex(player, rootKey), formation, rootKey);
    }

    public static void setFormation(Player player, int squadIndex, int formation) {
        setFormation(player, squadIndex, formation, ROOT_KEY);
    }

    public static void setFormation(Player player, int squadIndex, int formation, String rootKey) {
        getSquad(player, squadIndex, rootKey).putInt(FORMATION_KEY, formation);
    }

    public static boolean isFormationEnabled(Player player) {
        return isFormationEnabled(player, ROOT_KEY);
    }

    public static boolean isFormationEnabled(Player player, String rootKey) {
        return isFormationEnabled(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    public static boolean isFormationEnabled(Player player, int squadIndex) {
        return isFormationEnabled(player, squadIndex, ROOT_KEY);
    }

    public static boolean isFormationEnabled(Player player, int squadIndex, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        if (!squad.contains(FORMATION_ENABLED_KEY, Tag.TAG_BYTE)) {
            boolean enabledFromLegacyMode = squad.getInt(MODE_KEY) != 0;
            squad.putBoolean(FORMATION_ENABLED_KEY, enabledFromLegacyMode);
        }
        return squad.getBoolean(FORMATION_ENABLED_KEY);
    }

    public static void setFormationEnabled(Player player, boolean enabled) {
        setFormationEnabled(player, enabled, ROOT_KEY);
    }

    public static void setFormationEnabled(Player player, boolean enabled, String rootKey) {
        setFormationEnabled(player, getSelectedSquadIndex(player, rootKey), enabled, rootKey);
    }

    public static void setFormationEnabled(Player player, int squadIndex, boolean enabled) {
        setFormationEnabled(player, squadIndex, enabled, ROOT_KEY);
    }

    public static void setFormationEnabled(Player player, int squadIndex, boolean enabled, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        squad.putBoolean(FORMATION_ENABLED_KEY, enabled);
        squad.putInt(MODE_KEY, enabled ? 1 : 0);
    }

    @Nullable
    public static UUID getFocus(Player player) {
        return getFocus(player, ROOT_KEY);
    }

    @Nullable
    public static UUID getFocus(Player player, String rootKey) {
        return getFocus(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    @Nullable
    public static UUID getFocus(Player player, int squadIndex) {
        return getFocus(player, squadIndex, ROOT_KEY);
    }

    @Nullable
    public static UUID getFocus(Player player, int squadIndex, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        return squad.hasUUID(FOCUS_KEY) ? squad.getUUID(FOCUS_KEY) : null;
    }

    public static void setFocus(Player player, @Nullable UUID focus) {
        setFocus(player, focus, ROOT_KEY);
    }

    public static void setFocus(Player player, @Nullable UUID focus, String rootKey) {
        setFocus(player, getSelectedSquadIndex(player, rootKey), focus, rootKey);
    }

    public static void setFocus(Player player, int squadIndex, @Nullable UUID focus) {
        setFocus(player, squadIndex, focus, ROOT_KEY);
    }

    public static void setFocus(Player player, int squadIndex, @Nullable UUID focus, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        if (focus == null) {
            squad.remove(FOCUS_KEY);
        } else {
            squad.putUUID(FOCUS_KEY, focus);
        }
    }

    public static void clearFocus(Player player, int squadIndex) {
        clearFocus(player, squadIndex, ROOT_KEY);
    }

    public static void clearFocus(Player player, int squadIndex, String rootKey) {
        getSquad(player, squadIndex, rootKey).remove(FOCUS_KEY);
    }

    @Nullable
    public static Vec3 getCommandTarget(Player player) {
        return getCommandTarget(player, ROOT_KEY);
    }

    @Nullable
    public static Vec3 getCommandTarget(Player player, String rootKey) {
        return getCommandTarget(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    @Nullable
    public static Vec3 getCommandTarget(Player player, int squadIndex) {
        return getCommandTarget(player, squadIndex, ROOT_KEY);
    }

    @Nullable
    public static Vec3 getCommandTarget(Player player, int squadIndex, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        if (!squad.contains(COMMAND_TARGET_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag pos = squad.getCompound(COMMAND_TARGET_KEY);
        return new Vec3(pos.getDouble("X"), pos.getDouble("Y"), pos.getDouble("Z"));
    }

    public static void setCommandTarget(Player player, @Nullable Vec3 target) {
        setCommandTarget(player, target, ROOT_KEY);
    }

    public static void setCommandTarget(Player player, @Nullable Vec3 target, String rootKey) {
        setCommandTarget(player, getSelectedSquadIndex(player, rootKey), target, rootKey);
    }

    public static void setCommandTarget(Player player, int squadIndex, @Nullable Vec3 target) {
        setCommandTarget(player, squadIndex, target, ROOT_KEY);
    }

    public static void setCommandTarget(Player player, int squadIndex, @Nullable Vec3 target, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        if (target == null) {
            squad.remove(COMMAND_TARGET_KEY);
            return;
        }
        CompoundTag pos = new CompoundTag();
        pos.putDouble("X", target.x);
        pos.putDouble("Y", target.y);
        pos.putDouble("Z", target.z);
        squad.put(COMMAND_TARGET_KEY, pos);
    }

    @Nullable
    public static UUID getMount(Player player) {
        return getMount(player, ROOT_KEY);
    }

    @Nullable
    public static UUID getMount(Player player, String rootKey) {
        return getMount(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    @Nullable
    public static UUID getMount(Player player, int squadIndex) {
        return getMount(player, squadIndex, ROOT_KEY);
    }

    @Nullable
    public static UUID getMount(Player player, int squadIndex, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        return squad.hasUUID(MOUNT_KEY) ? squad.getUUID(MOUNT_KEY) : null;
    }

    public static void setMount(Player player, @Nullable UUID mount) {
        setMount(player, mount, ROOT_KEY);
    }

    public static void setMount(Player player, @Nullable UUID mount, String rootKey) {
        setMount(player, getSelectedSquadIndex(player, rootKey), mount, rootKey);
    }

    public static void setMount(Player player, int squadIndex, @Nullable UUID mount) {
        setMount(player, squadIndex, mount, ROOT_KEY);
    }

    public static void setMount(Player player, int squadIndex, @Nullable UUID mount, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        if (mount == null) {
            squad.remove(MOUNT_KEY);
        } else {
            squad.putUUID(MOUNT_KEY, mount);
        }
    }

    @Nullable
    public static UUID getAttackTarget(Player player) {
        return getAttackTarget(player, ROOT_KEY);
    }

    @Nullable
    public static UUID getAttackTarget(Player player, String rootKey) {
        return getAttackTarget(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    @Nullable
    public static UUID getAttackTarget(Player player, int squadIndex) {
        return getAttackTarget(player, squadIndex, ROOT_KEY);
    }

    @Nullable
    public static UUID getAttackTarget(Player player, int squadIndex, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        return squad.hasUUID(ATTACK_TARGET_KEY) ? squad.getUUID(ATTACK_TARGET_KEY) : null;
    }

    public static void setAttackTarget(Player player, @Nullable UUID attackTarget) {
        setAttackTarget(player, attackTarget, ROOT_KEY);
    }

    public static void setAttackTarget(Player player, @Nullable UUID attackTarget, String rootKey) {
        setAttackTarget(player, getSelectedSquadIndex(player, rootKey), attackTarget, rootKey);
    }

    public static void setAttackTarget(Player player, int squadIndex, @Nullable UUID attackTarget) {
        setAttackTarget(player, squadIndex, attackTarget, ROOT_KEY);
    }

    public static void setAttackTarget(Player player, int squadIndex, @Nullable UUID attackTarget, String rootKey) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        if (attackTarget == null) {
            squad.remove(ATTACK_TARGET_KEY);
            return;
        }
        squad.putUUID(ATTACK_TARGET_KEY, attackTarget);
    }

    public static List<CompoundTag> getWaypoints(Player player) {
        return getWaypoints(player, ROOT_KEY);
    }

    public static List<CompoundTag> getWaypoints(Player player, String rootKey) {
        return getWaypoints(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    public static List<CompoundTag> getWaypoints(Player player, int squadIndex) {
        return getWaypoints(player, squadIndex, ROOT_KEY);
    }

    public static List<CompoundTag> getWaypoints(Player player, int squadIndex, String rootKey) {
        List<CompoundTag> waypoints = new ArrayList<>();
        ListTag list = getSquad(player, squadIndex, rootKey).getList(WAYPOINTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            waypoints.add(list.getCompound(i).copy());
        }
        return waypoints;
    }

    public static void addWaypoint(Player player, CompoundTag waypoint) {
        addWaypoint(player, waypoint, ROOT_KEY);
    }

    public static void addWaypoint(Player player, CompoundTag waypoint, String rootKey) {
        CompoundTag squad = getCurrentSquad(player, rootKey);
        ListTag list = squad.getList(WAYPOINTS_KEY, Tag.TAG_COMPOUND);
        list.add(waypoint);
        squad.put(WAYPOINTS_KEY, list);
    }

    public static void clearWaypoints(Player player) {
        clearWaypoints(player, ROOT_KEY);
    }

    public static void clearWaypoints(Player player, String rootKey) {
        clearWaypoints(player, getSelectedSquadIndex(player, rootKey), rootKey);
    }

    public static void clearWaypoints(Player player, int squadIndex) {
        clearWaypoints(player, squadIndex, ROOT_KEY);
    }

    public static void clearWaypoints(Player player, int squadIndex, String rootKey) {
        getSquad(player, squadIndex, rootKey).put(WAYPOINTS_KEY, new ListTag());
    }

    public static void updatePetLocation(Player player, UUID entityId, Vec3 pos, String dimensionId) {
        String key = resolveRootKeyForEntity(player, entityId);
        if (key == null) return;
        updatePetLocation(player, entityId, pos, dimensionId, key);
    }

    public static void updatePetLocation(Player player, UUID entityId, Vec3 pos, String dimensionId, String rootKey) {
        int squadIndex = findSquadIndexContaining(player, entityId, rootKey);
        if (squadIndex < 0) {
            return;
        }
        updateMember(player, squadIndex, entityId, rootKey, member -> {
            member.putString("Dimension", dimensionId);
            member.putDouble("X", pos.x);
            member.putDouble("Y", pos.y);
            member.putDouble("Z", pos.z);
        });
    }

    @Nullable
    public static String resolveRootKeyForEntity(Player player, UUID entityId) {
        if (findSquadIndexContaining(player, entityId, ROOT_KEY) >= 0) return ROOT_KEY;
        if (findSquadIndexContaining(player, entityId, ROOT_KEY_DOMINION) >= 0) return ROOT_KEY_DOMINION;
        return null;
    }

    public static CompoundTag createSyncData(Player player, String rootKey) {
        CompoundTag data = new CompoundTag();
        data.putInt(SELECTED_SQUAD_KEY, getSelectedSquadIndex(player, rootKey));
        data.putInt("MaxSquads", getServerMaxSquads());
        data.putInt("MaxMembers", getServerMaxMembers());
        CompoundTag squad = getCurrentSquad(player, rootKey).copy();
        UUID mount = getMount(player, rootKey);
        if (mount != null) {
            squad.putUUID(MOUNT_KEY, mount);
        }
        data.put("Squad", squad);
        return data;
    }

    public static void applyClientSync(Player player, CompoundTag data) {
        applyClientSync(player, data, ROOT_KEY);
    }

    public static void applyClientSync(Player player, CompoundTag data, String rootKey) {
        CompoundTag root = getRoot(player, rootKey);
        if (data.contains("MaxSquads", Tag.TAG_INT)) {
            root.putInt(MAX_SQUADS_CACHE_KEY, Math.max(1, data.getInt("MaxSquads")));
        }
        if (data.contains("MaxMembers", Tag.TAG_INT)) {
            root.putInt(MAX_MEMBERS_CACHE_KEY, Math.max(1, data.getInt("MaxMembers")));
        }

        int selected = clamp(data.getInt(SELECTED_SQUAD_KEY), 0, Math.max(1, getCachedMaxSquads(player, rootKey)) - 1);
        root.putInt(SELECTED_SQUAD_KEY, selected);
        ensureSquadCount(root, selected + 1);

        CompoundTag syncedSquad = data.contains("Squad", Tag.TAG_COMPOUND) ? data.getCompound("Squad").copy() : createEmptySquad();
        normalizeSquad(syncedSquad);
        ListTag squads = root.getList(SQUADS_KEY, Tag.TAG_COMPOUND);
        squads.set(selected, syncedSquad);
        root.put(SQUADS_KEY, squads);
    }

    public static void migrateLegacyFromStack(Player player, ItemStack stack) {
        migrateLegacyFromStack(player, stack, ROOT_KEY);
    }

    public static void migrateLegacyFromStack(Player player, ItemStack stack, String rootKey) {
        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || !hasLegacyData(stackTag)) {
            return;
        }

        CompoundTag squad = getCurrentSquad(player, rootKey);
        if (isSquadStateEmpty(squad)) {
            copyIfPresent(stackTag, squad, TEAM_KEY, Tag.TAG_LIST);
            copyIfPresent(stackTag, squad, FOCUS_KEY, Tag.TAG_INT_ARRAY);
            copyIfPresent(stackTag, squad, COMMAND_TARGET_KEY, Tag.TAG_COMPOUND);
            copyIfPresent(stackTag, squad, ATTACK_TARGET_KEY, Tag.TAG_INT_ARRAY);
            copyIfPresent(stackTag, squad, WAYPOINTS_KEY, Tag.TAG_LIST);
            if (stackTag.contains(FORMATION_KEY, Tag.TAG_INT)) {
                squad.putInt(FORMATION_KEY, stackTag.getInt(FORMATION_KEY));
            }
            if (stackTag.contains(MODE_KEY, Tag.TAG_INT)) {
                squad.putInt(MODE_KEY, stackTag.getInt(MODE_KEY));
                squad.putBoolean(FORMATION_ENABLED_KEY, stackTag.getInt(MODE_KEY) != 0);
            }
        }

        stackTag.remove(TEAM_KEY);
        stackTag.remove(FOCUS_KEY);
        stackTag.remove(FORMATION_KEY);
        stackTag.remove(MODE_KEY);
        stackTag.remove(COMMAND_TARGET_KEY);
        stackTag.remove(ATTACK_TARGET_KEY);
        stackTag.remove(WAYPOINTS_KEY);
    }

    private static void updateMember(Player player, int squadIndex, UUID entityId, String rootKey, Consumer<CompoundTag> updater) {
        CompoundTag squad = getSquad(player, squadIndex, rootKey);
        ListTag members = squad.getList(TEAM_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (member.hasUUID("UUID") && member.getUUID("UUID").equals(entityId)) {
                updater.accept(member);
                members.set(i, member);
                squad.put(TEAM_KEY, members);
                return;
            }
        }
    }

    private static CompoundTag createRootTag() {
        CompoundTag root = new CompoundTag();
        root.putInt(SELECTED_SQUAD_KEY, 0);
        root.putInt(MAX_SQUADS_CACHE_KEY, getServerMaxSquads());
        root.putInt(MAX_MEMBERS_CACHE_KEY, getServerMaxMembers());
        root.put(SQUADS_KEY, new ListTag());
        normalizeRoot(root);
        return root;
    }

    private static void normalizeRoot(CompoundTag root) {
        if (!root.contains(SQUADS_KEY, Tag.TAG_LIST)) {
            root.put(SQUADS_KEY, new ListTag());
        }
        if (!root.contains(SELECTED_SQUAD_KEY, Tag.TAG_INT)) {
            root.putInt(SELECTED_SQUAD_KEY, 0);
        }
        if (!root.contains(MAX_SQUADS_CACHE_KEY, Tag.TAG_INT)) {
            root.putInt(MAX_SQUADS_CACHE_KEY, getServerMaxSquads());
        }
        if (!root.contains(MAX_MEMBERS_CACHE_KEY, Tag.TAG_INT)) {
            root.putInt(MAX_MEMBERS_CACHE_KEY, getServerMaxMembers());
        }

        ensureSquadCount(root, clamp(root.getInt(SELECTED_SQUAD_KEY), 0, getServerMaxSquads() - 1) + 1);
    }

    private static void ensureSquadCount(CompoundTag root, int required) {
        ListTag squads = root.getList(SQUADS_KEY, Tag.TAG_COMPOUND);
        while (squads.size() < required) {
            squads.add(createEmptySquad());
        }
        for (int i = 0; i < squads.size(); i++) {
            CompoundTag squad = squads.getCompound(i);
            normalizeSquad(squad);
            squads.set(i, squad);
        }
        root.put(SQUADS_KEY, squads);
    }

    private static CompoundTag createEmptySquad() {
        CompoundTag squad = new CompoundTag();
        squad.put(TEAM_KEY, new ListTag());
        squad.put(WAYPOINTS_KEY, new ListTag());
        squad.putInt(FORMATION_KEY, 0);
        squad.putBoolean(FORMATION_ENABLED_KEY, false);
        squad.putInt(MODE_KEY, 0);
        squad.putInt(TASK_KEY, TASK_GUARD);
        return squad;
    }

    private static void normalizeSquad(CompoundTag squad) {
        if (!squad.contains(TEAM_KEY, Tag.TAG_LIST)) {
            squad.put(TEAM_KEY, new ListTag());
        }
        if (!squad.contains(WAYPOINTS_KEY, Tag.TAG_LIST)) {
            squad.put(WAYPOINTS_KEY, new ListTag());
        }
        if (!squad.contains(FORMATION_KEY, Tag.TAG_INT)) {
            squad.putInt(FORMATION_KEY, 0);
        }
        if (!squad.contains(TASK_KEY, Tag.TAG_INT)) {
            squad.putInt(TASK_KEY, TASK_GUARD);
        }
        if (!squad.contains(FORMATION_ENABLED_KEY, Tag.TAG_BYTE)) {
            squad.putBoolean(FORMATION_ENABLED_KEY, squad.getInt(MODE_KEY) != 0);
        }
        squad.putInt(MODE_KEY, squad.getBoolean(FORMATION_ENABLED_KEY) ? 1 : 0);
    }

    private static boolean hasLegacyData(CompoundTag tag) {
        return tag.contains(TEAM_KEY, Tag.TAG_LIST)
                || tag.hasUUID(FOCUS_KEY)
                || tag.contains(FORMATION_KEY, Tag.TAG_INT)
                || tag.contains(MODE_KEY, Tag.TAG_INT)
                || tag.contains(COMMAND_TARGET_KEY, Tag.TAG_COMPOUND)
                || tag.hasUUID(ATTACK_TARGET_KEY)
                || tag.contains(WAYPOINTS_KEY, Tag.TAG_LIST);
    }

    private static boolean isSquadStateEmpty(CompoundTag squad) {
        return squad.getList(TEAM_KEY, Tag.TAG_COMPOUND).isEmpty()
                && !squad.hasUUID(FOCUS_KEY)
                && !squad.contains(COMMAND_TARGET_KEY, Tag.TAG_COMPOUND)
                && !squad.hasUUID(ATTACK_TARGET_KEY)
                && squad.getList(WAYPOINTS_KEY, Tag.TAG_COMPOUND).isEmpty()
                && squad.getInt(FORMATION_KEY) == 0
                && !squad.getBoolean(FORMATION_ENABLED_KEY)
                && squad.getInt(TASK_KEY) == TASK_GUARD;
    }

    private static void copyIfPresent(CompoundTag source, CompoundTag target, String key, int tagType) {
        if (source.contains(key, tagType)) {
            target.put(key, source.get(key).copy());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
