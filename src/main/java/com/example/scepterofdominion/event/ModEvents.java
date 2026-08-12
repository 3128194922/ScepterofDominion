package com.example.scepterofdominion.event;

import com.example.scepterofdominion.ScepterOfDominion;
import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.util.FormationHelper;
import com.example.scepterofdominion.util.ScepterSquadData;
import com.example.scepterofdominion.world.StorageDimension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ScepterOfDominion.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        Level level = chunk.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension().location().equals(StorageDimension.STORAGE_DIMENSION.location())) return;

        net.minecraft.world.level.ChunkPos chunkPos = chunk.getPos();
        AABB area = new AABB(
                chunkPos.getMinBlockX(), serverLevel.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1, serverLevel.getMaxBuildHeight(), chunkPos.getMaxBlockZ() + 1
        );

        for (Entity entity : serverLevel.getEntities((Entity) null, area, e ->
                e instanceof LivingEntity
                        && (e.getPersistentData().hasUUID("ScepterOwner") || e.getPersistentData().hasUUID("DominionOwner"))
        )) {
            LivingEntity living = (LivingEntity) entity;

            if (living.getPersistentData().contains("ScepterIsMount") && living.isVehicle()) {
                continue;
            }

            if (living.getPersistentData().contains("ScepterDesignatedMount")) {
                continue;
            }

            UUID ownerUUID = living.getPersistentData().hasUUID("ScepterOwner")
                    ? living.getPersistentData().getUUID("ScepterOwner")
                    : living.getPersistentData().getUUID("DominionOwner");

            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                String rk = living.getPersistentData().hasUUID("DominionOwner")
                        ? ScepterSquadData.ROOT_KEY_DOMINION : ScepterSquadData.ROOT_KEY;
                UUID mountUUID = ScepterSquadData.getMount(owner, rk);
                if (mountUUID != null && mountUUID.equals(living.getUUID())) {
                    living.getPersistentData().putBoolean("ScepterDesignatedMount", true);
                    continue;
                }
            }

            if (StorageDimension.containPetDirect(serverLevel, living)) {
                if (owner != null) {
                    ScepterSquadData.updatePetLocation(owner, living.getUUID(),
                            new Vec3(StorageDimension.STORAGE_POS.getX(), StorageDimension.STORAGE_POS.getY(), StorageDimension.STORAGE_POS.getZ()),
                            "scepterofdominion:storage");
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        if (mob.getPersistentData().contains("ScepterIsMount") && mob.isVehicle()
                && mob.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player rider) {
            handleMountRiding(mob, rider);
        } else if (mob.getPersistentData().contains("ScepterIsMount") && !mob.isVehicle()) {
            mob.getPersistentData().remove("ScepterIsMount");
            mob.getPersistentData().remove("ScepterMountOwner");
        }

        if (mob.tickCount % 10 == 0 && !mob.getPersistentData().contains("ScepterWaypoints", Tag.TAG_LIST)) {
            checkDirectAttackTarget(mob);
        }

        if (mob.tickCount % 20 == 0) {
            handleAutoSquadTask(mob);
        }

        if (!mob.getPersistentData().contains("ScepterWaypoints", Tag.TAG_LIST)) return;
        ListTag waypoints = mob.getPersistentData().getList("ScepterWaypoints", Tag.TAG_COMPOUND);
        if (waypoints.isEmpty()) {
            mob.getPersistentData().remove("ScepterWaypoints");
            return;
        }

        CompoundTag currentTask = waypoints.getCompound(0);
        String type = currentTask.getString("Type");

        if ("ATTACK".equals(type)) {
            boolean complete = false;
            LivingEntity target = null;
            
            if (currentTask.hasUUID("Target")) {
                UUID targetId = currentTask.getUUID("Target");
                target = mob.getTarget();
                
                if (target == null || !target.getUUID().equals(targetId)) {
                    if (mob.level() instanceof ServerLevel sl) {
                        net.minecraft.world.entity.Entity e = sl.getEntity(targetId);
                        if (e instanceof LivingEntity le) {
                            mob.setTarget(le);
                            target = le;
                        } else {
                            complete = true;
                        }
                    }
                }
                
                if (target != null) {
                    if (!target.isAlive()) {
                        complete = true;
                        mob.setTarget(null);
                    }
                }
            } else {
                complete = true;
            }

            if (complete) {
                waypoints.remove(0);
                mob.getPersistentData().put("ScepterWaypoints", waypoints);

                if (waypoints.isEmpty()) {
                    if (mob.getPersistentData().getBoolean("ScepterClosed")
                            && mob.getPersistentData().contains("ScepterWaypointsOriginal", Tag.TAG_LIST)) {
                        ListTag original = mob.getPersistentData().getList("ScepterWaypointsOriginal", Tag.TAG_COMPOUND);
                        mob.getPersistentData().put("ScepterWaypoints", original.copy());
                    } else if (target != null) {
                        updateCommandTarget(mob, target.position());
                    }
                }
            }
        }
    }

    private static void checkDirectAttackTarget(Mob mob) {
        net.minecraft.world.entity.player.Player owner = resolveOwner(mob);
        if (owner != null) {
            int squadIndex = FormationHelper.getSquadIndexForPet(owner, mob.getUUID());
            if (squadIndex >= 0) {
                String rootKey = getRootKeyForMob(mob);
                UUID attackTargetUUID = ScepterSquadData.getAttackTarget(owner, squadIndex, rootKey);
                if (attackTargetUUID != null && mob.level() instanceof ServerLevel sl) {
                    Entity target = sl.getEntity(attackTargetUUID);
                    if (target instanceof LivingEntity living && living.isAlive()) {
                        if (mob.getTarget() == null || !mob.getTarget().getUUID().equals(attackTargetUUID)) {
                            mob.setTarget(living);
                        }
                    } else if (target != null) {
                        ScepterSquadData.setCommandTarget(owner, squadIndex, target.position(), rootKey);
                        ScepterSquadData.setAttackTarget(owner, squadIndex, null, rootKey);
                        ItemStack scepter = FormationHelper.getScepterWithPet(owner, mob.getUUID());
                        if (scepter.getItem() instanceof AbstractScepterItem item) {
                            item.syncToClient(scepter, owner);
                        }
                    }
                }
            }
        }
    }

    private static void handleAutoSquadTask(Mob mob) {
        net.minecraft.world.entity.player.Player owner = resolveOwner(mob);
        if (owner == null) {
            return;
        }

        int squadIndex = FormationHelper.getSquadIndexForPet(owner, mob.getUUID());
        if (squadIndex < 0) {
            return;
        }

        String rootKey = getRootKeyForMob(mob);
        List<UUID> team = ScepterSquadData.getTeam(owner, squadIndex, rootKey);
        if (team.isEmpty() || !team.get(0).equals(mob.getUUID())) {
            return;
        }

        int squadTask = ScepterSquadData.getTask(owner, squadIndex, rootKey);
        if (squadTask == ScepterSquadData.TASK_FOLLOW_PROTECT) {
            ScepterSquadData.setCommandTarget(owner, squadIndex, owner.position(), rootKey);
        }

        if (ScepterSquadData.getAttackTarget(owner, squadIndex, rootKey) != null) {
            return;
        }

        if (squadTask != ScepterSquadData.TASK_GUARD && squadTask != ScepterSquadData.TASK_FOLLOW_PROTECT) {
            return;
        }

        List<Mob> members = getActiveSquadMembers(owner, team);
        if (members.isEmpty()) {
            return;
        }

        Vec3 center = squadTask == ScepterSquadData.TASK_FOLLOW_PROTECT
                ? owner.position()
                : getSquadCenter(owner, squadIndex, members, rootKey);

        AABB searchBox = new AABB(center, center).inflate(32.0D);
        List<LivingEntity> enemies = owner.level().getEntitiesOfClass(LivingEntity.class, searchBox, entity -> isEnemy(owner, team, entity));
        if (enemies.isEmpty()) {
            return;
        }

        distributeTargets(members, enemies);
    }

    private static void updateCommandTarget(Mob mob, net.minecraft.world.phys.Vec3 pos) {
        net.minecraft.world.entity.player.Player owner = resolveOwner(mob);
        if (owner != null) {
            int squadIndex = FormationHelper.getSquadIndexForPet(owner, mob.getUUID());
            if (squadIndex >= 0) {
                String rootKey = getRootKeyForMob(mob);
                ScepterSquadData.setCommandTarget(owner, squadIndex, pos, rootKey);
                ItemStack scepter = FormationHelper.getScepterWithPet(owner, mob.getUUID());
                if (scepter.getItem() instanceof AbstractScepterItem item) {
                    item.syncToClient(scepter, owner);
                }
            }
        }
    }

    private static net.minecraft.world.entity.player.Player resolveOwner(Mob mob) {
        if (mob.getPersistentData().hasUUID("DominionOwner")) {
            UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
            return mob.level().getPlayerByUUID(ownerId);
        }
        if (mob instanceof TamableAnimal tamable && tamable.getOwner() instanceof net.minecraft.world.entity.player.Player player) {
            return player;
        }
        return null;
    }

    private static String getRootKeyForMob(Mob mob) {
        if (mob.getPersistentData().hasUUID("DominionOwner")) {
            return ScepterSquadData.ROOT_KEY_DOMINION;
        }
        return ScepterSquadData.ROOT_KEY;
    }

    private static List<Mob> getActiveSquadMembers(net.minecraft.world.entity.player.Player owner, List<UUID> team) {
        List<Mob> members = new ArrayList<>();
        if (owner.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : team) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity instanceof Mob mob && mob.isAlive()) {
                    members.add(mob);
                }
            }
        }
        return members;
    }

    private static Vec3 getSquadCenter(net.minecraft.world.entity.player.Player owner, int squadIndex, List<Mob> members, String rootKey) {
        Vec3 commandTarget = ScepterSquadData.getCommandTarget(owner, squadIndex, rootKey);
        if (commandTarget != null) {
            return commandTarget;
        }

        Vec3 sum = Vec3.ZERO;
        for (Mob member : members) {
            sum = sum.add(member.position());
        }
        return members.isEmpty() ? owner.position() : sum.scale(1.0D / members.size());
    }

    private static boolean isEnemy(net.minecraft.world.entity.player.Player owner, List<UUID> team, LivingEntity entity) {
        if (!entity.isAlive() || entity == owner || team.contains(entity.getUUID())) {
            return false;
        }
        if (entity instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID())) {
            return false;
        }
        if (entity instanceof Enemy) {
            return true;
        }
        return entity instanceof Mob hostileMob
                && hostileMob.getTarget() != null
                && (hostileMob.getTarget() == owner || team.contains(hostileMob.getTarget().getUUID()));
    }

    private static void distributeTargets(List<Mob> members, List<LivingEntity> enemies) {
        Map<UUID, Integer> engageCounts = new HashMap<>();
        for (LivingEntity enemy : enemies) {
            engageCounts.put(enemy.getUUID(), 0);
        }

        List<Mob> availableMembers = new ArrayList<>();
        for (Mob member : members) {
            LivingEntity target = member.getTarget();
            if (target == null || !engageCounts.containsKey(target.getUUID()) || !target.isAlive()) {
                availableMembers.add(member);
            } else {
                engageCounts.put(target.getUUID(), engageCounts.get(target.getUUID()) + 1);
            }
        }

        int idealCount = Math.max(1, (int) Math.ceil((double) members.size() / (double) enemies.size()));
        for (Mob member : members) {
            LivingEntity target = member.getTarget();
            if (target != null && engageCounts.containsKey(target.getUUID())) {
                int currentCount = engageCounts.get(target.getUUID());
                if (currentCount > idealCount) {
                    availableMembers.add(member);
                    engageCounts.put(target.getUUID(), currentCount - 1);
                }
            }
        }

        for (Mob member : availableMembers) {
            LivingEntity bestTarget = enemies.stream()
                    .filter(LivingEntity::isAlive)
                    .min(Comparator
                            .comparingInt((LivingEntity enemy) -> engageCounts.getOrDefault(enemy.getUUID(), 0))
                            .thenComparingDouble(member::distanceToSqr))
                    .orElse(null);
            if (bestTarget != null) {
                member.setTarget(bestTarget);
                engageCounts.put(bestTarget.getUUID(), engageCounts.getOrDefault(bestTarget.getUUID(), 0) + 1);
            }
        }
    }

    private static void handleMountRiding(Mob mount, net.minecraft.world.entity.player.Player rider) {
        mount.getNavigation().stop();
        mount.setTarget(null);

        mount.setYRot(rider.getYRot());
        mount.yRotO = mount.getYRot();
        mount.setXRot(rider.getXRot() * 0.5F);
        mount.yBodyRot = mount.getYRot();
        mount.yHeadRot = mount.yBodyRot;

        float forward = rider.zza;
        float strafe = rider.xxa;
        if (forward <= 0.0F) {
            forward *= 0.25F;
        }
        strafe *= 0.5F;

        mount.xxa = strafe;
        mount.zza = forward;
        mount.setSpeed((float) mount.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
    }
}
