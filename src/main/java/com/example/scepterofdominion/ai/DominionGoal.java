package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.util.FormationHelper;
import com.example.scepterofdominion.util.ScepterSquadData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DominionGoal extends Goal {
    private final Mob mob;
    private int timeToRecalcPath;

    public DominionGoal(Mob mob) {
        this.mob = mob;
        // Only inhibit MOVE and LOOK, allowing TARGET (and thus attack goals) to run if we are not moving
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.getPersistentData().contains("ScepterWaypoints", net.minecraft.nbt.Tag.TAG_LIST)) {
            if (!mob.getPersistentData().getList("ScepterWaypoints", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()) {
                return false;
            }
        }

        if (!mob.getPersistentData().hasUUID("DominionOwner")) return false;

        UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
        Player owner = mob.level().getPlayerByUUID(ownerId);
        if (owner == null) return false;

        // Check if mob is in team
        if (!FormationHelper.isPetInScepterTeam(owner, mob.getUUID())) return false;

        int squadIndex = FormationHelper.getSquadIndexForPet(owner, mob.getUUID());
        if (squadIndex < 0) return false;

        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;
        if (ScepterSquadData.getAttackTarget(owner, squadIndex) != null) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // Continue using as long as dominated.
        // If "DominionOwner" tag is removed, we stop.
        return canUse();
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
        this.mob.setTarget(null);
        this.timeToRecalcPath = 0;
    }

    @Override
    public void tick() {
        UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
        Player owner = mob.level().getPlayerByUUID(ownerId);

        if (owner == null) {
            mob.getNavigation().stop();
            return;
        }

        int squadIndex = FormationHelper.getSquadIndexForPet(owner, mob.getUUID());
        if (squadIndex < 0) {
            mob.getNavigation().stop();
            return;
        }

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;

            List<UUID> team = ScepterSquadData.getTeam(owner, squadIndex);
            int squadTask = ScepterSquadData.getTask(owner, squadIndex);
            if (squadTask == ScepterSquadData.TASK_FOLLOW_PROTECT) {
                double ownerDist = mob.distanceToSqr(owner);
                if (ownerDist > 16.0D) {
                    mob.getNavigation().moveTo(owner, 1.15D);
                } else if (ownerDist < 6.25D) {
                    mob.getNavigation().stop();
                }
                return;
            }

            Vec3 commandTarget = ScepterSquadData.getCommandTarget(owner, squadIndex);
            boolean formationEnabled = ScepterSquadData.isFormationEnabled(owner, squadIndex);
            if (formationEnabled) {
                int index = team.indexOf(mob.getUUID());
                List<net.minecraft.world.entity.Entity> activeMembers = getActiveMembers(team);
                Vec3 centerPos = commandTarget != null ? commandTarget : getIdleCenter(activeMembers);
                Vec3 targetPos = FormationHelper.getFormationPos(centerPos, ScepterSquadData.getFormation(owner, squadIndex), index, Math.max(activeMembers.size(), team.size()));
                moveToTarget(targetPos, 1.15D);
                return;
            }

            if (commandTarget != null) {
                moveToTarget(commandTarget, 1.15D);
            } else {
                if (squadTask == ScepterSquadData.TASK_HOLD) {
                    mob.getNavigation().stop();
                } else {
                    Vec3 idleCenter = getIdleCenter(getActiveMembers(team));
                    if (mob.distanceToSqr(idleCenter) > 9.0D) {
                        mob.getNavigation().moveTo(idleCenter.x, idleCenter.y, idleCenter.z, 1.0D);
                    } else {
                        mob.getNavigation().stop();
                    }
                }
            }
        }
    }

    private void moveToTarget(Vec3 targetPos, double speed) {
        double distSqr = mob.distanceToSqr(targetPos.x, targetPos.y, targetPos.z);
        if (distSqr > 9.0D) {
            mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speed);
        } else if (distSqr > 1.5D) {
            mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speed * 0.5D);
        } else {
            mob.getNavigation().stop();
        }
    }

    private List<net.minecraft.world.entity.Entity> getActiveMembers(List<UUID> team) {
        List<net.minecraft.world.entity.Entity> members = new ArrayList<>();
        if (mob.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : team) {
                net.minecraft.world.entity.Entity entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    members.add(entity);
                }
            }
        }
        return members;
    }

    private Vec3 getIdleCenter(List<net.minecraft.world.entity.Entity> activeMembers) {
        if (activeMembers.isEmpty()) {
            return mob.position();
        }

        Vec3 sum = Vec3.ZERO;
        for (net.minecraft.world.entity.Entity entity : activeMembers) {
            sum = sum.add(entity.position());
        }
        return sum.scale(1.0D / activeMembers.size());
    }
}
