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
        if (player == null) {
            return getServerMaxSquads();
        }

        CompoundTag root = getRoot(player);
        return root.contains(MAX_SQUADS_CACHE_KEY, Tag.TAG_INT) ? Math.max(1, root.getInt(MAX_SQUADS_CACHE_KEY)) : getServerMaxSquads();
    }

    public static int getCachedMaxMembers(@Nullable Player player) {
        if (player == null) {
            return getServerMaxMembers();
        }

        CompoundTag root = getRoot(player);
        return root.contains(MAX_MEMBERS_CACHE_KEY, Tag.TAG_INT) ? Math.max(1, root.getInt(MAX_MEMBERS_CACHE_KEY)) : getServerMaxMembers();
    }

    public static CompoundTag getRoot(Player player) {
        CompoundTag root;

        if (!player.level().isClientSide && player.getServer() != null) {
            ServerLevel overworld = player.getServer().overworld();
            ScepterSquadSavedData savedData = ScepterSquadSavedData.get(overworld);

            CompoundTag persistentData = player.getPersistentData();
            if (persistentData.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
                CompoundTag legacy = persistentData.getCompound(ROOT_KEY);
                CompoundTag saved = savedData.getPlayerRoot(player.getUUID());
                if (!saved.contains(SQUADS_KEY, Tag.TAG_LIST) || saved.getList(SQUADS_KEY, Tag.TAG_COMPOUND).isEmpty()) {
                    saved.merge(legacy);
                    savedData.setDirty();
                }
                persistentData.remove(ROOT_KEY);
            }

            root = savedData.getPlayerRoot(player.getUUID());
            savedData.setDirty();
        } else {
            CompoundTag persistentData = player.getPersistentData();
            if (!persistentData.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
                persistentData.put(ROOT_KEY, new CompoundTag());
            }
            root = persistentData.getCompound(ROOT_KEY);
        }

        normalizeRoot(root);
        return root;
    }

    public static int getSelectedSquadIndex(Player player) {
        CompoundTag root = getRoot(player);
        int maxSquads = getServerMaxSquads();
        int selected = clamp(root.getInt(SELECTED_SQUAD_KEY), 0, maxSquads - 1);
        root.putInt(SELECTED_SQUAD_KEY, selected);
        ensureSquadCount(root, selected + 1);
        return selected;
    }

    public static void setSelectedSquadIndex(Player player, int index) {
        CompoundTag root = getRoot(player);
        int selected = clamp(index, 0, getServerMaxSquads() - 1);
        root.putInt(SELECTED_SQUAD_KEY, selected);
        ensureSquadCount(root, selected + 1);
    }

    public static void cycleSelectedSquad(Player player, int delta) {
        int maxSquads = getServerMaxSquads();
        int current = getSelectedSquadIndex(player);
        int next = (current + delta) % maxSquads;
        if (next < 0) {
            next += maxSquads;
        }
        setSelectedSquadIndex(player, next);
    }

    public static CompoundTag getCurrentSquad(Player player) {
        return getSquad(player, getSelectedSquadIndex(player));
    }

    public static CompoundTag getSquad(Player player, int squadIndex) {
        CompoundTag root = getRoot(player);
        int selected = clamp(squadIndex, 0, getServerMaxSquads() - 1);
        ensureSquadCount(root, selected + 1);
        return root.getList(SQUADS_KEY, Tag.TAG_COMPOUND).getCompound(selected);
    }

    public static int getCurrentTeamSize(Player player) {
        return getTeam(player).size();
    }

    public static List<UUID> getTeam(Player player) {
        return getTeam(player, getSelectedSquadIndex(player));
    }

    public static List<UUID> getTeam(Player player, int squadIndex) {
        List<UUID> team = new ArrayList<>();
        ListTag members = getSquad(player, squadIndex).getList(TEAM_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (member.hasUUID("UUID")) {
                team.add(member.getUUID("UUID"));
            }
        }
        return team;
    }

    public static List<CompoundTag> getTeamInfo(Player player) {
        return getTeamInfo(player, getSelectedSquadIndex(player));
    }

    public static List<CompoundTag> getTeamInfo(Player player, int squadIndex) {
        List<CompoundTag> info = new ArrayList<>();
        ListTag members = getSquad(player, squadIndex).getList(TEAM_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            info.add(members.getCompound(i).copy());
        }
        return info;
    }

    public static int findSquadIndexContaining(Player player, UUID entityId) {
        CompoundTag root = getRoot(player);
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

    public static void addTeamMember(Player player, CompoundTag member) {
        CompoundTag squad = getCurrentSquad(player);
        ListTag members = squad.getList(TEAM_KEY, Tag.TAG_COMPOUND);
        members.add(member);
        squad.put(TEAM_KEY, members);
    }

    public static boolean removeTeamMember(Player player, UUID entityId) {
        return removeTeamMember(player, getSelectedSquadIndex(player), entityId);
    }

    public static boolean removeTeamMember(Player player, int squadIndex, UUID entityId) {
        CompoundTag squad = getSquad(player, squadIndex);
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
            UUID focus = getFocus(player, squadIndex);
            if (focus != null && focus.equals(entityId)) {
                if (members.isEmpty()) {
                    clearFocus(player, squadIndex);
                } else {
                    setFocus(player, squadIndex, members.getCompound(0).getUUID("UUID"));
                }
            }
        }

        return removed;
    }

    public static int getMode(Player player) {
        return isFormationEnabled(player) ? 1 : 0;
    }

    public static int getMode(Player player, int squadIndex) {
        return isFormationEnabled(player, squadIndex) ? 1 : 0;
    }

    public static void setMode(Player player, int mode) {
        setFormationEnabled(player, mode != 0);
    }

    public static void setMode(Player player, int squadIndex, int mode) {
        setFormationEnabled(player, squadIndex, mode != 0);
    }

    public static int getTask(Player player) {
        return getTask(player, getSelectedSquadIndex(player));
    }

    public static int getTask(Player player, int squadIndex) {
        CompoundTag squad = getSquad(player, squadIndex);
        int task = squad.getInt(TASK_KEY);
        if (task < TASK_GUARD || task > TASK_FOLLOW_PROTECT) {
            task = TASK_GUARD;
            squad.putInt(TASK_KEY, task);
        }
        return task;
    }

    public static void setTask(Player player, int task) {
        setTask(player, getSelectedSquadIndex(player), task);
    }

    public static void setTask(Player player, int squadIndex, int task) {
        getSquad(player, squadIndex).putInt(TASK_KEY, clamp(task, TASK_GUARD, TASK_FOLLOW_PROTECT));
    }

    public static int getFormation(Player player) {
        return getFormation(player, getSelectedSquadIndex(player));
    }

    public static int getFormation(Player player, int squadIndex) {
        return getSquad(player, squadIndex).getInt(FORMATION_KEY);
    }

    public static void setFormation(Player player, int formation) {
        setFormation(player, getSelectedSquadIndex(player), formation);
    }

    public static void setFormation(Player player, int squadIndex, int formation) {
        getSquad(player, squadIndex).putInt(FORMATION_KEY, formation);
    }

    public static boolean isFormationEnabled(Player player) {
        return isFormationEnabled(player, getSelectedSquadIndex(player));
    }

    public static boolean isFormationEnabled(Player player, int squadIndex) {
        CompoundTag squad = getSquad(player, squadIndex);
        if (!squad.contains(FORMATION_ENABLED_KEY, Tag.TAG_BYTE)) {
            boolean enabledFromLegacyMode = squad.getInt(MODE_KEY) != 0;
            squad.putBoolean(FORMATION_ENABLED_KEY, enabledFromLegacyMode);
        }
        return squad.getBoolean(FORMATION_ENABLED_KEY);
    }

    public static void setFormationEnabled(Player player, boolean enabled) {
        setFormationEnabled(player, getSelectedSquadIndex(player), enabled);
    }

    public static void setFormationEnabled(Player player, int squadIndex, boolean enabled) {
        CompoundTag squad = getSquad(player, squadIndex);
        squad.putBoolean(FORMATION_ENABLED_KEY, enabled);
        squad.putInt(MODE_KEY, enabled ? 1 : 0);
    }

    @Nullable
    public static UUID getFocus(Player player) {
        return getFocus(player, getSelectedSquadIndex(player));
    }

    @Nullable
    public static UUID getFocus(Player player, int squadIndex) {
        CompoundTag squad = getSquad(player, squadIndex);
        return squad.hasUUID(FOCUS_KEY) ? squad.getUUID(FOCUS_KEY) : null;
    }

    public static void setFocus(Player player, @Nullable UUID focus) {
        setFocus(player, getSelectedSquadIndex(player), focus);
    }

    public static void setFocus(Player player, int squadIndex, @Nullable UUID focus) {
        CompoundTag squad = getSquad(player, squadIndex);
        if (focus == null) {
            squad.remove(FOCUS_KEY);
        } else {
            squad.putUUID(FOCUS_KEY, focus);
        }
    }

    public static void clearFocus(Player player, int squadIndex) {
        getSquad(player, squadIndex).remove(FOCUS_KEY);
    }

    @Nullable
    public static Vec3 getCommandTarget(Player player) {
        return getCommandTarget(player, getSelectedSquadIndex(player));
    }

    @Nullable
    public static Vec3 getCommandTarget(Player player, int squadIndex) {
        CompoundTag squad = getSquad(player, squadIndex);
        if (!squad.contains(COMMAND_TARGET_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag pos = squad.getCompound(COMMAND_TARGET_KEY);
        return new Vec3(pos.getDouble("X"), pos.getDouble("Y"), pos.getDouble("Z"));
    }

    public static void setCommandTarget(Player player, @Nullable Vec3 target) {
        setCommandTarget(player, getSelectedSquadIndex(player), target);
    }

    public static void setCommandTarget(Player player, int squadIndex, @Nullable Vec3 target) {
        CompoundTag squad = getSquad(player, squadIndex);
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
    public static UUID getAttackTarget(Player player) {
        return getAttackTarget(player, getSelectedSquadIndex(player));
    }

    @Nullable
    public static UUID getAttackTarget(Player player, int squadIndex) {
        CompoundTag squad = getSquad(player, squadIndex);
        return squad.hasUUID(ATTACK_TARGET_KEY) ? squad.getUUID(ATTACK_TARGET_KEY) : null;
    }

    public static void setAttackTarget(Player player, @Nullable UUID attackTarget) {
        setAttackTarget(player, getSelectedSquadIndex(player), attackTarget);
    }

    public static void setAttackTarget(Player player, int squadIndex, @Nullable UUID attackTarget) {
        CompoundTag squad = getSquad(player, squadIndex);
        if (attackTarget == null) {
            squad.remove(ATTACK_TARGET_KEY);
            return;
        }

        squad.putUUID(ATTACK_TARGET_KEY, attackTarget);
    }

    public static List<CompoundTag> getWaypoints(Player player) {
        return getWaypoints(player, getSelectedSquadIndex(player));
    }

    public static List<CompoundTag> getWaypoints(Player player, int squadIndex) {
        List<CompoundTag> waypoints = new ArrayList<>();
        ListTag list = getSquad(player, squadIndex).getList(WAYPOINTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            waypoints.add(list.getCompound(i).copy());
        }
        return waypoints;
    }

    public static void addWaypoint(Player player, CompoundTag waypoint) {
        CompoundTag squad = getCurrentSquad(player);
        ListTag list = squad.getList(WAYPOINTS_KEY, Tag.TAG_COMPOUND);
        list.add(waypoint);
        squad.put(WAYPOINTS_KEY, list);
    }

    public static void clearWaypoints(Player player) {
        clearWaypoints(player, getSelectedSquadIndex(player));
    }

    public static void clearWaypoints(Player player, int squadIndex) {
        getSquad(player, squadIndex).put(WAYPOINTS_KEY, new ListTag());
    }

    public static void updatePetLocation(Player player, UUID entityId, Vec3 pos, String dimensionId) {
        int squadIndex = findSquadIndexContaining(player, entityId);
        if (squadIndex < 0) {
            return;
        }

        updateMember(player, squadIndex, entityId, member -> {
            member.putString("Dimension", dimensionId);
            member.putDouble("X", pos.x);
            member.putDouble("Y", pos.y);
            member.putDouble("Z", pos.z);
        });
    }

    public static CompoundTag createSyncData(Player player) {
        CompoundTag data = new CompoundTag();
        data.putInt(SELECTED_SQUAD_KEY, getSelectedSquadIndex(player));
        data.putInt("MaxSquads", getServerMaxSquads());
        data.putInt("MaxMembers", getServerMaxMembers());
        data.put("Squad", getCurrentSquad(player).copy());
        return data;
    }

    public static void applyClientSync(Player player, CompoundTag data) {
        CompoundTag root = getRoot(player);
        if (data.contains("MaxSquads", Tag.TAG_INT)) {
            root.putInt(MAX_SQUADS_CACHE_KEY, Math.max(1, data.getInt("MaxSquads")));
        }
        if (data.contains("MaxMembers", Tag.TAG_INT)) {
            root.putInt(MAX_MEMBERS_CACHE_KEY, Math.max(1, data.getInt("MaxMembers")));
        }

        int selected = clamp(data.getInt(SELECTED_SQUAD_KEY), 0, Math.max(1, getCachedMaxSquads(player)) - 1);
        root.putInt(SELECTED_SQUAD_KEY, selected);
        ensureSquadCount(root, selected + 1);

        CompoundTag syncedSquad = data.contains("Squad", Tag.TAG_COMPOUND) ? data.getCompound("Squad").copy() : createEmptySquad();
        normalizeSquad(syncedSquad);
        ListTag squads = root.getList(SQUADS_KEY, Tag.TAG_COMPOUND);
        squads.set(selected, syncedSquad);
        root.put(SQUADS_KEY, squads);
    }

    public static void migrateLegacyFromStack(Player player, ItemStack stack) {
        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || !hasLegacyData(stackTag)) {
            return;
        }

        CompoundTag squad = getCurrentSquad(player);
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

    private static void updateMember(Player player, int squadIndex, UUID entityId, Consumer<CompoundTag> updater) {
        CompoundTag squad = getSquad(player, squadIndex);
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
