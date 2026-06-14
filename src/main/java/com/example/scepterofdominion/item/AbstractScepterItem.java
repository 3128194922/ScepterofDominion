package com.example.scepterofdominion.item;

import com.example.scepterofdominion.Config;
import com.example.scepterofdominion.network.PacketHandler;
import com.example.scepterofdominion.util.ScepterSquadData;
import com.example.scepterofdominion.world.StorageDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AbstractScepterItem extends Item {
    public static final int MODE_SINGLE = 0;
    public static final int MODE_FORMATION = 1;

    public AbstractScepterItem(Properties properties) {
        super(properties);
    }

    public abstract boolean canControl(LivingEntity entity, Player player);
    protected abstract void onEntityAdded(LivingEntity entity, Player player);
    protected abstract void onEntityRemoved(LivingEntity entity, Player player);
    protected abstract void commandEntityMove(Entity entity, Vec3 target, boolean isSprint);
    protected abstract void commandEntityAttack(Entity entity, LivingEntity target);

    public int getMode(ItemStack stack) {
        return getMode(stack, getClientContextPlayer());
    }

    public int getMode(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        return player != null ? ScepterSquadData.getMode(player) : stack.getOrCreateTag().getInt(ScepterSquadData.MODE_KEY);
    }

    public void setMode(ItemStack stack, int mode) {
        setMode(stack, mode, getClientContextPlayer());
    }

    public void setMode(ItemStack stack, int mode, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            ScepterSquadData.setMode(player, mode);
        } else {
            stack.getOrCreateTag().putInt(ScepterSquadData.MODE_KEY, mode);
        }
    }

    public int getFormation(ItemStack stack) {
        return getFormation(stack, getClientContextPlayer());
    }

    public int getFormation(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        return player != null ? ScepterSquadData.getFormation(player) : stack.getOrCreateTag().getInt(ScepterSquadData.FORMATION_KEY);
    }

    public void setFormation(ItemStack stack, int formation, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            ScepterSquadData.setFormation(player, formation);
        } else {
            stack.getOrCreateTag().putInt(ScepterSquadData.FORMATION_KEY, formation);
        }
    }

    public void cycleMode(ItemStack stack, Player player) {
        boolean next = !isFormationEnabled(stack, player);
        setFormationEnabled(stack, next, player);
        syncToClient(stack, player);
        player.displayClientMessage(Component.translatable("message.scepterofdominion.formation_toggle", next ? Component.translatable("message.scepterofdominion.enabled") : Component.translatable("message.scepterofdominion.disabled")).withStyle(ChatFormatting.GREEN), true);
    }

    public int getSquadTask(ItemStack stack) {
        return getSquadTask(stack, getClientContextPlayer());
    }

    public int getSquadTask(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        return player != null ? ScepterSquadData.getTask(player) : ScepterSquadData.TASK_GUARD;
    }

    public void setSquadTask(ItemStack stack, int task, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            ScepterSquadData.setTask(player, task);
        }
    }

    public boolean isFormationEnabled(ItemStack stack) {
        return isFormationEnabled(stack, getClientContextPlayer());
    }

    public boolean isFormationEnabled(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        return player != null ? ScepterSquadData.isFormationEnabled(player) : stack.getOrCreateTag().getBoolean(ScepterSquadData.FORMATION_ENABLED_KEY);
    }

    public void setFormationEnabled(ItemStack stack, boolean enabled, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            ScepterSquadData.setFormationEnabled(player, enabled);
        } else {
            stack.getOrCreateTag().putBoolean(ScepterSquadData.FORMATION_ENABLED_KEY, enabled);
            stack.getOrCreateTag().putInt(ScepterSquadData.MODE_KEY, enabled ? MODE_FORMATION : MODE_SINGLE);
        }
    }

    public void toggleFormationEnabled(ItemStack stack, Player player) {
        boolean enabled = !isFormationEnabled(stack, player);
        setFormationEnabled(stack, enabled, player);
        syncToClient(stack, player);
        player.displayClientMessage(Component.translatable("message.scepterofdominion.formation_toggle", enabled ? Component.translatable("message.scepterofdominion.enabled") : Component.translatable("message.scepterofdominion.disabled")).withStyle(ChatFormatting.GREEN), true);
    }

    public List<UUID> getTeam(ItemStack stack) {
        return getTeam(stack, getClientContextPlayer());
    }

    public List<UUID> getTeam(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            return ScepterSquadData.getTeam(player);
        }

        return readLegacyTeam(stack);
    }

    public List<CompoundTag> getTeamInfo(ItemStack stack) {
        return getTeamInfo(stack, getClientContextPlayer());
    }

    public List<CompoundTag> getTeamInfo(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            return ScepterSquadData.getTeamInfo(player);
        }

        List<CompoundTag> info = new ArrayList<>();
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(ScepterSquadData.TEAM_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(ScepterSquadData.TEAM_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                info.add(list.getCompound(i).copy());
            }
        }
        return info;
    }

    public void syncToClient(ItemStack stack, Player player) {
        ensurePlayerData(stack, player);
        if (!player.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            PacketHandler.sendToPlayer(new com.example.scepterofdominion.network.PacketSyncTeam(ScepterSquadData.createSyncData(player)), serverPlayer);
        }
    }

    public void addToTeam(ItemStack stack, LivingEntity entity, Player player) {
        ensurePlayerData(stack, player);
        if (!canControl(entity, player)) {
            return;
        }

        if (entity.getPersistentData().contains("ScepterOwner")) {
            UUID ownerUUID = entity.getPersistentData().getUUID("ScepterOwner");
            if (!ownerUUID.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.scepterofdominion.already_controlled").withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        int currentSquad = ScepterSquadData.getSelectedSquadIndex(player);
        int existingSquad = ScepterSquadData.findSquadIndexContaining(player, entity.getUUID());
        if (existingSquad == currentSquad) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.already_in_team").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (existingSquad >= 0) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.already_in_other_squad", existingSquad + 1).withStyle(ChatFormatting.RED), true);
            return;
        }

        List<UUID> team = getTeam(stack, player);
        int maxMembers = Config.COMMON.maxSquadMembers.get();
        if (team.size() >= maxMembers) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.team_full", maxMembers).withStyle(ChatFormatting.RED), true);
            return;
        }

        CompoundTag member = new CompoundTag();
        member.putUUID("UUID", entity.getUUID());
        member.putString("Name", entity.getName().getString());
        ScepterSquadData.addTeamMember(player, member);

        entity.getPersistentData().putUUID("ScepterOwner", player.getUUID());
        if (team.isEmpty()) {
            ScepterSquadData.setFocus(player, entity.getUUID());
        }

        onEntityAdded(entity, player);
        syncToClient(stack, player);
        player.displayClientMessage(Component.translatable("message.scepterofdominion.added_to_team", entity.getName()).withStyle(ChatFormatting.GREEN), true);

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            StorageDimension.containPets(serverPlayer, stack, entity);
        }
    }

    public void removeTeamMember(ItemStack stack, UUID uuid) {
        Player player = getClientContextPlayer();
        if (player != null) {
            ensurePlayerData(stack, player);
            ScepterSquadData.removeTeamMember(player, uuid);
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(ScepterSquadData.TEAM_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(ScepterSquadData.TEAM_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag member = list.getCompound(i);
                if (member.hasUUID("UUID") && member.getUUID("UUID").equals(uuid)) {
                    list.remove(i);
                    break;
                }
            }
            tag.put(ScepterSquadData.TEAM_KEY, list);
        }
    }

    public void removeTeamMember(ItemStack stack, UUID uuid, Player player, LivingEntity entity) {
        ensurePlayerData(stack, player);
        int squadIndex = ScepterSquadData.findSquadIndexContaining(player, uuid);
        if (squadIndex < 0) {
            squadIndex = ScepterSquadData.getSelectedSquadIndex(player);
        }
        ScepterSquadData.removeTeamMember(player, squadIndex, uuid);

        LivingEntity resolvedEntity = entity;
        if (resolvedEntity == null && player.level() instanceof ServerLevel serverLevel) {
            Entity found = serverLevel.getEntity(uuid);
            if (found instanceof LivingEntity living) {
                resolvedEntity = living;
            }
        }

        if (resolvedEntity != null) {
            onEntityRemoved(resolvedEntity, player);
            resolvedEntity.getPersistentData().remove("ScepterOwner");
        }

        syncToClient(stack, player);
    }

    public void setFocus(ItemStack stack, UUID uuid) {
        Player player = getClientContextPlayer();
        if (player != null) {
            ensurePlayerData(stack, player);
            ScepterSquadData.setFocus(player, uuid);
        } else {
            stack.getOrCreateTag().putUUID(ScepterSquadData.FOCUS_KEY, uuid);
        }
    }

    public void setFocus(ItemStack stack, UUID uuid, Player player) {
        ensurePlayerData(stack, player);
        ScepterSquadData.setFocus(player, uuid);
        syncToClient(stack, player);
    }

    @Nullable
    public UUID getFocus(ItemStack stack) {
        return getFocus(stack, getClientContextPlayer());
    }

    @Nullable
    public UUID getFocus(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            return ScepterSquadData.getFocus(player);
        }

        CompoundTag tag = stack.getOrCreateTag();
        return tag.hasUUID(ScepterSquadData.FOCUS_KEY) ? tag.getUUID(ScepterSquadData.FOCUS_KEY) : null;
    }

    public void setCommandTarget(ItemStack stack, @Nullable Vec3 target) {
        Player player = getClientContextPlayer();
        if (player != null) {
            ensurePlayerData(stack, player);
            ScepterSquadData.setCommandTarget(player, target);
        } else if (target == null) {
            stack.getOrCreateTag().remove(ScepterSquadData.COMMAND_TARGET_KEY);
        } else {
            CompoundTag pos = new CompoundTag();
            pos.putDouble("X", target.x);
            pos.putDouble("Y", target.y);
            pos.putDouble("Z", target.z);
            stack.getOrCreateTag().put(ScepterSquadData.COMMAND_TARGET_KEY, pos);
        }
    }

    public void setCommandTarget(ItemStack stack, @Nullable Vec3 target, Player player) {
        ensurePlayerData(stack, player);
        ScepterSquadData.setCommandTarget(player, target);
    }

    @Nullable
    public Vec3 getCommandTarget(ItemStack stack) {
        return getCommandTarget(stack, getClientContextPlayer());
    }

    @Nullable
    public Vec3 getCommandTarget(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            return ScepterSquadData.getCommandTarget(player);
        }

        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(ScepterSquadData.COMMAND_TARGET_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag pos = tag.getCompound(ScepterSquadData.COMMAND_TARGET_KEY);
            return new Vec3(pos.getDouble("X"), pos.getDouble("Y"), pos.getDouble("Z"));
        }
        return null;
    }

    public void setAttackTarget(ItemStack stack, @Nullable UUID targetUUID) {
        Player player = getClientContextPlayer();
        if (player != null) {
            ensurePlayerData(stack, player);
            ScepterSquadData.setAttackTarget(player, targetUUID);
        } else if (targetUUID == null) {
            stack.getOrCreateTag().remove(ScepterSquadData.ATTACK_TARGET_KEY);
        } else {
            stack.getOrCreateTag().putUUID(ScepterSquadData.ATTACK_TARGET_KEY, targetUUID);
        }
    }

    public void setAttackTarget(ItemStack stack, @Nullable UUID targetUUID, Player player) {
        ensurePlayerData(stack, player);
        ScepterSquadData.setAttackTarget(player, targetUUID);
    }

    @Nullable
    public UUID getAttackTarget(ItemStack stack) {
        return getAttackTarget(stack, getClientContextPlayer());
    }

    @Nullable
    public UUID getAttackTarget(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            return ScepterSquadData.getAttackTarget(player);
        }

        CompoundTag tag = stack.getOrCreateTag();
        return tag.hasUUID(ScepterSquadData.ATTACK_TARGET_KEY) ? tag.getUUID(ScepterSquadData.ATTACK_TARGET_KEY) : null;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (player.level().isClientSide) {
            return true;
        }

        if (player.isCrouching()) {
            ScepterSquadData.cycleSelectedSquad(player, 1);
            syncToClient(stack, player);
            player.displayClientMessage(Component.translatable("message.scepterofdominion.squad_switched", ScepterSquadData.getSelectedSquadIndex(player) + 1).withStyle(ChatFormatting.AQUA), true);
            return true;
        }

        if (entity instanceof LivingEntity living && canControl(living, player)) {
            List<UUID> team = getTeam(stack, player);
            if (team.contains(living.getUUID())) {
                setFocus(stack, living.getUUID(), player);
                player.displayClientMessage(Component.translatable("message.scepterofdominion.focus_set", living.getName()).withStyle(ChatFormatting.GOLD), true);
            } else {
                addToTeam(stack, living, player);
            }
            return true;
        }

        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.scepterofdominion.client.ClientInputHandler.handleRightClick(this, stack, player, hand));
        }
        return InteractionResultHolder.success(stack);
    }

    public void serverHandleRightClick(ItemStack stack, Level level, Player player, Vec3 targetPos, @Nullable Entity targetEntity, boolean isSprint) {
        ensurePlayerData(stack, player);
        int squadTask = getSquadTask(stack, player);
        if (squadTask == ScepterSquadData.TASK_FOLLOW_PROTECT) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.follow_task_locked").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        if (targetEntity instanceof LivingEntity target) {
            List<UUID> team = getTeam(stack, player);
            if (!team.contains(target.getUUID())) {
                if (isSprint) {
                    addWaypoint(stack, target.position(), target.getUUID(), player);
                } else {
                    issueAttackCommand(stack, level, target, player);
                    player.displayClientMessage(Component.translatable("message.scepterofdominion.command_attack", target.getName()).withStyle(ChatFormatting.RED), true);
                }
            }
        } else {
            if (isSprint) {
                addWaypoint(stack, targetPos, null, player);
            } else {
                issueMoveCommand(stack, level, targetPos, false, player);
                player.displayClientMessage(Component.translatable("message.scepterofdominion.command_move").withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    public void addWaypoint(ItemStack stack, Vec3 pos, @Nullable UUID targetUUID, Player player) {
        ensurePlayerData(stack, player);
        List<CompoundTag> waypoints = getWaypoints(stack, player);
        int max = Config.COMMON.maxWaypoints.get();
        if (waypoints.size() >= max) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.waypoint_limit", max).withStyle(ChatFormatting.RED), true);
            return;
        }

        CompoundTag waypoint = new CompoundTag();
        waypoint.putDouble("X", pos.x);
        waypoint.putDouble("Y", pos.y);
        waypoint.putDouble("Z", pos.z);
        if (targetUUID != null) {
            waypoint.putUUID("Target", targetUUID);
            waypoint.putString("Type", "ATTACK");
        } else {
            waypoint.putString("Type", "MOVE");
        }

        ScepterSquadData.addWaypoint(player, waypoint);
        syncToClient(stack, player);
        player.displayClientMessage(Component.translatable("message.scepterofdominion.waypoint_added", waypoints.size() + 1).withStyle(ChatFormatting.AQUA), true);
    }

    public List<CompoundTag> getWaypoints(ItemStack stack) {
        return getWaypoints(stack, getClientContextPlayer());
    }

    public List<CompoundTag> getWaypoints(ItemStack stack, @Nullable Player player) {
        ensurePlayerData(stack, player);
        if (player != null) {
            return ScepterSquadData.getWaypoints(player);
        }

        List<CompoundTag> waypoints = new ArrayList<>();
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(ScepterSquadData.WAYPOINTS_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(ScepterSquadData.WAYPOINTS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                waypoints.add(list.getCompound(i).copy());
            }
        }
        return waypoints;
    }

    public void clearWaypoints(ItemStack stack) {
        Player player = getClientContextPlayer();
        if (player != null) {
            ensurePlayerData(stack, player);
            ScepterSquadData.clearWaypoints(player);
        } else {
            stack.getOrCreateTag().remove(ScepterSquadData.WAYPOINTS_KEY);
        }
    }

    public void clearWaypoints(ItemStack stack, Player player) {
        ensurePlayerData(stack, player);
        ScepterSquadData.clearWaypoints(player);
    }

    public void executeWaypoints(ItemStack stack, Player player, Level level) {
        ensurePlayerData(stack, player);
        List<CompoundTag> waypoints = getWaypoints(stack, player);
        if (waypoints.isEmpty()) {
            return;
        }

        boolean closed = false;
        if (waypoints.size() >= 2) {
            CompoundTag first = waypoints.get(0);
            CompoundTag last = waypoints.get(waypoints.size() - 1);
            BlockPos firstPos = new BlockPos((int) first.getDouble("X"), (int) first.getDouble("Y"), (int) first.getDouble("Z"));
            BlockPos lastPos = new BlockPos((int) last.getDouble("X"), (int) last.getDouble("Y"), (int) last.getDouble("Z"));
            closed = firstPos.equals(lastPos);
        }

        List<UUID> team = getTeam(stack, player);
        int formationId = getFormation(stack, player);
        boolean formationEnabled = isFormationEnabled(stack, player) && usesFormationForCommands(player);

        List<Entity> activeMembers = new ArrayList<>();
        if (level instanceof ServerLevel serverLevel) {
            for (UUID uuid : team) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    activeMembers.add(entity);
                }
            }
        }

        if (activeMembers.isEmpty()) {
            clearWaypoints(stack, player);
            syncToClient(stack, player);
            return;
        }

        for (int i = 0; i < activeMembers.size(); i++) {
            Entity entity = activeMembers.get(i);
            ListTag entityQueue = new ListTag();

            for (CompoundTag waypoint : waypoints) {
                String type = waypoint.getString("Type");
                CompoundTag task = new CompoundTag();
                task.putString("Type", type);

                if ("ATTACK".equals(type)) {
                    if (waypoint.hasUUID("Target")) {
                        task.putUUID("Target", waypoint.getUUID("Target"));
                    }
                } else {
                    Vec3 center = new Vec3(waypoint.getDouble("X"), waypoint.getDouble("Y"), waypoint.getDouble("Z"));
                    Vec3 targetPos = center;
                    if (formationEnabled) {
                        List<Vec3> positions = calculateFormationPositions(center, formationId, activeMembers);
                        if (i < positions.size()) {
                            targetPos = positions.get(i);
                        }
                    }

                    task.putDouble("X", targetPos.x);
                    task.putDouble("Y", targetPos.y);
                    task.putDouble("Z", targetPos.z);
                }

                entityQueue.add(task);
            }

            if (!entityQueue.isEmpty()) {
                entity.getPersistentData().put("ScepterWaypoints", entityQueue);
                entity.getPersistentData().putBoolean("ScepterClosed", closed);
                if (closed) {
                    entity.getPersistentData().put("ScepterWaypointsOriginal", entityQueue.copy());
                }
            }
        }

        clearWaypoints(stack, player);
        syncToClient(stack, player);
        player.displayClientMessage(Component.translatable("message.scepterofdominion.waypoints_executed").withStyle(ChatFormatting.GREEN), true);
    }

    public void issueMoveCommand(ItemStack stack, Level level, Vec3 target, boolean moveOnly, Player player) {
        ensurePlayerData(stack, player);
        clearWaypoints(stack, player);
        setCommandTarget(stack, target, player);

        List<UUID> team = getTeam(stack, player);
        int formationId = getFormation(stack, player);
        boolean formationEnabled = isFormationEnabled(stack, player) && usesFormationForCommands(player);

        setAttackTarget(stack, null, player);
        syncToClient(stack, player);

        List<Entity> activeMembers = new ArrayList<>();
        if (level instanceof ServerLevel serverLevel) {
            for (UUID uuid : team) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity == null) {
                    continue;
                }

                entity.getPersistentData().remove("ScepterWaypoints");
                entity.getPersistentData().remove("ScepterClosed");
                entity.getPersistentData().remove("ScepterWaypointsOriginal");
                activeMembers.add(entity);
            }
        }

        List<Vec3> positions = formationEnabled ? calculateFormationPositions(target, formationId, activeMembers) : List.of();
        for (int i = 0; i < activeMembers.size(); i++) {
            Vec3 memberTarget = formationEnabled && i < positions.size() ? positions.get(i) : target;
            commandEntityMove(activeMembers.get(i), memberTarget, moveOnly);
        }
    }

    public void issueAttackCommand(ItemStack stack, Level level, LivingEntity target, Player player) {
        ensurePlayerData(stack, player);
        clearWaypoints(stack, player);

        List<UUID> team = getTeam(stack, player);

        setAttackTarget(stack, target.getUUID(), player);
        setCommandTarget(stack, null, player);
        syncToClient(stack, player);

        if (level instanceof ServerLevel serverLevel) {
            for (UUID uuid : team) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity == null) {
                    continue;
                }

                entity.getPersistentData().remove("ScepterWaypoints");
                entity.getPersistentData().remove("ScepterClosed");
                entity.getPersistentData().remove("ScepterWaypointsOriginal");
                commandEntityAttack(entity, target);
            }
        }
    }

    public void handleLeftClickLogic(ItemStack stack, Player player, LivingEntity living) {
        if (!canControl(living, player)) {
            return;
        }

        if (getTeam(stack, player).contains(living.getUUID())) {
            setFocus(stack, living.getUUID(), player);
            player.displayClientMessage(Component.translatable("message.scepterofdominion.focus_set", living.getName()).withStyle(ChatFormatting.GOLD), true);
        } else {
            addToTeam(stack, living, player);
        }
    }

    public List<Vec3> calculateFormationPositions(Vec3 center, int formation, List<Entity> members) {
        List<Vec3> positions = new ArrayList<>();
        if (members.isEmpty()) {
            return positions;
        }

        double spacingMultiplier = Config.COMMON.formationSpacingMultiplier.get();
        double currentOffset;

        switch (formation) {
            case 0 -> {
                currentOffset = 0;
                for (Entity member : members) {
                    double radius = member.getBbWidth() / 2.0;
                    double gap = member.getBbWidth() * spacingMultiplier;
                    currentOffset += radius;
                    positions.add(center.add(0, 0, currentOffset));
                    currentOffset += radius + gap;
                }
            }
            case 1 -> {
                double leftOffset = 0;
                double rightOffset = 0;
                double maxHalfWidth = 0;
                for (Entity member : members) {
                    maxHalfWidth = Math.max(maxHalfWidth, member.getBbWidth() / 2.0);
                }
                double colSpacing = maxHalfWidth + 1.0 + (maxHalfWidth * 2.0 * spacingMultiplier * 0.5);

                for (int i = 0; i < members.size(); i++) {
                    Entity member = members.get(i);
                    double radius = member.getBbWidth() / 2.0;
                    double gap = member.getBbWidth() * spacingMultiplier;
                    if (i % 2 == 0) {
                        leftOffset += radius;
                        positions.add(center.add(-colSpacing, 0, leftOffset));
                        leftOffset += radius + gap;
                    } else {
                        rightOffset += radius;
                        positions.add(center.add(colSpacing, 0, rightOffset));
                        rightOffset += radius + gap;
                    }
                }
            }
            case 2 -> {
                positions.add(center);
                if (members.size() > 1) {
                    List<Entity> surroundings = members.subList(1, members.size());
                    double totalCircumference = 0;
                    for (Entity entity : surroundings) {
                        totalCircumference += entity.getBbWidth() + entity.getBbWidth() * spacingMultiplier;
                    }
                    double radius = Math.max(3.0, totalCircumference / (2 * Math.PI));
                    for (int i = 0; i < surroundings.size(); i++) {
                        double angle = (2 * Math.PI * i / surroundings.size()) - (Math.PI / 2);
                        positions.add(center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
                    }
                }
            }
            case 3 -> {
                currentOffset = 0;
                for (Entity member : members) {
                    double radius = member.getBbWidth() / 2.0;
                    double gap = member.getBbWidth() * spacingMultiplier;
                    currentOffset += radius;
                    double axisOffset = currentOffset * 0.707;
                    positions.add(center.add(axisOffset, 0, axisOffset));
                    currentOffset += radius + gap;
                }
            }
            case 4 -> {
                double totalWidth = 0;
                for (Entity entity : members) {
                    totalWidth += entity.getBbWidth() + entity.getBbWidth() * spacingMultiplier;
                }
                totalWidth -= members.get(members.size() - 1).getBbWidth() * spacingMultiplier;

                currentOffset = -totalWidth / 2.0;
                for (Entity member : members) {
                    double radius = member.getBbWidth() / 2.0;
                    double gap = member.getBbWidth() * spacingMultiplier;
                    currentOffset += radius;
                    positions.add(center.add(currentOffset, 0, 0));
                    currentOffset += radius + gap;
                }
            }
            case 5 -> {
                double circumference = 0;
                for (Entity entity : members) {
                    circumference += entity.getBbWidth() + entity.getBbWidth() * spacingMultiplier;
                }
                double clusterRadius = Math.max(3.0, circumference / (2 * Math.PI));
                for (int i = 0; i < members.size(); i++) {
                    double angle = 2 * Math.PI * i / members.size();
                    positions.add(center.add(Math.cos(angle) * clusterRadius, 0, Math.sin(angle) * clusterRadius));
                }
            }
            default -> {
            }
        }

        return positions;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        Player contextPlayer = getClientContextPlayer();
        int squadTask = getSquadTask(stack, contextPlayer);
        String taskKey = switch (squadTask) {
            case ScepterSquadData.TASK_HOLD -> "message.scepterofdominion.task.hold";
            case ScepterSquadData.TASK_FOLLOW_PROTECT -> "message.scepterofdominion.task.follow_protect";
            default -> "message.scepterofdominion.task.guard";
        };
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.task", Component.translatable(taskKey)).withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.formation_enabled", isFormationEnabled(stack, contextPlayer) ? Component.translatable("message.scepterofdominion.enabled") : Component.translatable("message.scepterofdominion.disabled")).withStyle(ChatFormatting.YELLOW));

        int formation = getFormation(stack, contextPlayer);
        String formationKey = switch (formation) {
            case 0 -> "message.scepterofdominion.formation.line_ahead";
            case 1 -> "message.scepterofdominion.formation.double_line";
            case 2 -> "message.scepterofdominion.formation.diamond";
            case 3 -> "message.scepterofdominion.formation.echelon";
            case 4 -> "message.scepterofdominion.formation.line_abreast";
            case 5 -> "message.scepterofdominion.formation.cluster";
            default -> "message.scepterofdominion.formation.line_ahead";
        };
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.formation", Component.translatable(formationKey)).withStyle(ChatFormatting.DARK_AQUA));

        int teamSize = contextPlayer != null ? ScepterSquadData.getCurrentTeamSize(contextPlayer) : getTeam(stack).size();
        int maxMembers = ScepterSquadData.getCachedMaxMembers(contextPlayer);
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.team_size", teamSize, maxMembers).withStyle(ChatFormatting.DARK_AQUA));

        if (contextPlayer != null) {
            tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.squad_index", ScepterSquadData.getSelectedSquadIndex(contextPlayer) + 1, ScepterSquadData.getCachedMaxSquads(contextPlayer)).withStyle(ChatFormatting.AQUA));
        }

        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.add").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.attack").withStyle(ChatFormatting.RED));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.move").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.sprint_left").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.sprint_right").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.mode").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.gui").withStyle(ChatFormatting.YELLOW));
    }

    private void ensurePlayerData(ItemStack stack, @Nullable Player player) {
        if (player != null) {
            ScepterSquadData.migrateLegacyFromStack(player, stack);
        }
    }

    @Nullable
    private Player getClientContextPlayer() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return null;
        }
        return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> com.example.scepterofdominion.client.ClientPlayerAccessor::getPlayer);
    }

    private List<UUID> readLegacyTeam(ItemStack stack) {
        List<UUID> team = new ArrayList<>();
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(ScepterSquadData.TEAM_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(ScepterSquadData.TEAM_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag member = list.getCompound(i);
                if (member.hasUUID("UUID")) {
                    team.add(member.getUUID("UUID"));
                }
            }
        }
        return team;
    }

    private boolean usesFormationForCommands(Player player) {
        int task = ScepterSquadData.getTask(player);
        return task == ScepterSquadData.TASK_GUARD || task == ScepterSquadData.TASK_HOLD;
    }
}
