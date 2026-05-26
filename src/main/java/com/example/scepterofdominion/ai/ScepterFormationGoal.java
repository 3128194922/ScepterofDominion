package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.util.FormationHelper;
import com.example.scepterofdominion.util.ScepterSquadData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class ScepterFormationGoal extends Goal {
    private final TamableAnimal tamable;
    private Player owner;
    private double speedModifier;
    private int timeToRecalcPath;

    public ScepterFormationGoal(TamableAnimal tamable, double speedModifier) {
        this.tamable = tamable;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (tamable.getPersistentData().contains("ScepterWaypoints", net.minecraft.nbt.Tag.TAG_LIST)) {
            if (!tamable.getPersistentData().getList("ScepterWaypoints", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()) {
                return false;
            }
        }

        // If pet has a target and it is alive, prioritize attack (stop formation logic)
        if (this.tamable.getTarget() != null && this.tamable.getTarget().isAlive()) {
            return false;
        }

        if (tamable.getOwner() instanceof Player p) {
            this.owner = p;
            
            // Check if pet is in team
            if (!FormationHelper.isPetInScepterTeam(p, tamable.getUUID())) {
                return false;
            }

            int squadIndex = FormationHelper.getSquadIndexForPet(p, tamable.getUUID());
            if (squadIndex < 0) {
                return false;
            }

            int squadTask = ScepterSquadData.getTask(p, squadIndex);
            if (squadTask == ScepterSquadData.TASK_FOLLOW_PROTECT) {
                return !tamable.isOrderedToSit();
            }

            if (squadTask == ScepterSquadData.TASK_GUARD || squadTask == ScepterSquadData.TASK_HOLD) {
                return !tamable.isOrderedToSit();
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // Continue unless interrupted by sit, death, or waypoints
        // Or if mode changed.
        return canUse(); 
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void tick() {
        if (owner == null) return;
        
        tamable.getLookControl().setLookAt(owner, 10.0F, (float)tamable.getMaxHeadXRot());

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;
            
            int squadIndex = FormationHelper.getSquadIndexForPet(owner, tamable.getUUID());
            if (squadIndex < 0) {
                return;
            }

            List<UUID> team = ScepterSquadData.getTeam(owner, squadIndex);
            int index = team.indexOf(tamable.getUUID());
            if (index == -1) return;

            int squadTask = ScepterSquadData.getTask(owner, squadIndex);
            if (squadTask == ScepterSquadData.TASK_FOLLOW_PROTECT) {
                followOwner(owner);
                return;
            }

            java.util.List<net.minecraft.world.entity.Entity> activeMembers = new java.util.ArrayList<>();
            if (tamable.level() instanceof ServerLevel serverLevel) {
                for (UUID uuid : team) {
                    net.minecraft.world.entity.Entity entity = serverLevel.getEntity(uuid);
                    if (entity != null) {
                        activeMembers.add(entity);
                    }
                }
            }

            AbstractScepterItem item = owner.getMainHandItem().getItem() instanceof AbstractScepterItem heldItem ? heldItem : null;
            if (item == null) {
                ItemStack maybeScepter = FormationHelper.getScepterWithPet(owner, tamable.getUUID());
                if (maybeScepter.getItem() instanceof AbstractScepterItem inventoryItem) {
                    item = inventoryItem;
                }
            }
            if (item == null) {
                return;
            }

            Vec3 commandTarget = ScepterSquadData.getCommandTarget(owner, squadIndex);
            boolean formationEnabled = ScepterSquadData.isFormationEnabled(owner, squadIndex);
            if (formationEnabled) {
                int formation = ScepterSquadData.getFormation(owner, squadIndex);
                Vec3 centerPos = commandTarget != null ? commandTarget : getIdleCenter(activeMembers);
                List<Vec3> positions = item.calculateFormationPositions(centerPos, formation, activeMembers);
                int myIndex = -1;
                for (int i = 0; i < activeMembers.size(); i++) {
                    if (activeMembers.get(i).getUUID().equals(tamable.getUUID())) {
                        myIndex = i;
                        break;
                    }
                }

                if (myIndex != -1 && myIndex < positions.size()) {
                    moveToTarget(positions.get(myIndex));
                }
                return;
            }

            if (commandTarget != null) {
                moveToTarget(commandTarget);
            } else if (squadTask == ScepterSquadData.TASK_HOLD) {
                tamable.getNavigation().stop();
            } else {
                Vec3 idleCenter = getIdleCenter(activeMembers);
                if (tamable.distanceToSqr(idleCenter) > 9.0D) {
                    tamable.getNavigation().moveTo(idleCenter.x, idleCenter.y, idleCenter.z, speedModifier * 0.8D);
                } else {
                    tamable.getNavigation().stop();
                }
            }
        }
    }

    private void moveToTarget(Vec3 targetPos) {
        double distSqr = tamable.distanceToSqr(targetPos.x, targetPos.y, targetPos.z);
        if (distSqr > 9.0D) {
            tamable.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speedModifier);
        } else if (distSqr > 1.5D) {
            tamable.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speedModifier * 0.5D);
        } else {
            tamable.getNavigation().stop();
        }
    }

    private void followOwner(Player owner) {
        double distSqr = tamable.distanceToSqr(owner);
        if (distSqr > 16.0D) {
            tamable.getNavigation().moveTo(owner, speedModifier);
        } else if (distSqr < 6.25D) {
            tamable.getNavigation().stop();
        }
    }

    private Vec3 getIdleCenter(List<net.minecraft.world.entity.Entity> activeMembers) {
        if (activeMembers.isEmpty()) {
            return tamable.position();
        }

        Vec3 sum = Vec3.ZERO;
        for (net.minecraft.world.entity.Entity entity : activeMembers) {
            sum = sum.add(entity.position());
        }
        return sum.scale(1.0D / activeMembers.size());
    }
}
