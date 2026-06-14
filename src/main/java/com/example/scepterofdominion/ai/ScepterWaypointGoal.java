package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.util.FormationHelper;
import com.example.scepterofdominion.util.ScepterSquadData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class ScepterWaypointGoal extends Goal {
    private final Mob mob;
    private int checkPathTimer;

    public ScepterWaypointGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.getPersistentData().contains("ScepterWaypoints", Tag.TAG_LIST)) return false;
        ListTag waypoints = mob.getPersistentData().getList("ScepterWaypoints", Tag.TAG_COMPOUND);
        if (waypoints.isEmpty()) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;

        CompoundTag currentTask = waypoints.getCompound(0);
        return "MOVE".equals(currentTask.getString("Type"));
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.checkPathTimer = 0;
        this.mob.setTarget(null); // Ensure we don't get distracted by old targets when moving
    }

    @Override
    public void tick() {
        if (--this.checkPathTimer > 0) return;
        this.checkPathTimer = 10;

        ListTag waypoints = mob.getPersistentData().getList("ScepterWaypoints", Tag.TAG_COMPOUND);
        if (waypoints.isEmpty()) return;

        CompoundTag currentTask = waypoints.getCompound(0);
        // We know it is MOVE because of canUse
        
        double x = currentTask.getDouble("X");
        double y = currentTask.getDouble("Y");
        double z = currentTask.getDouble("Z");

        if (mob.distanceToSqr(x, y, z) < 2.0 * 2.0) {
            // Reached
            waypoints.remove(0);
            mob.getPersistentData().put("ScepterWaypoints", waypoints);
            
            if (waypoints.isEmpty()) {
                if (mob.getPersistentData().getBoolean("ScepterClosed")
                        && mob.getPersistentData().contains("ScepterWaypointsOriginal", Tag.TAG_LIST)) {
                    ListTag original = mob.getPersistentData().getList("ScepterWaypointsOriginal", Tag.TAG_COMPOUND);
                    mob.getPersistentData().put("ScepterWaypoints", original.copy());
                } else {
                    updateCommandTarget(new Vec3(x, y, z));
                }
            }
            
            mob.getNavigation().stop();
            this.checkPathTimer = 0; // Force re-check next tick for next task
        } else {
            mob.getNavigation().moveTo(x, y, z, 1.2);
        }
    }

    private void updateCommandTarget(Vec3 pos) {
        if (!mob.getPersistentData().hasUUID("DominionOwner")) return;
        UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
        Player owner = mob.level().getPlayerByUUID(ownerId);
        
        if (owner != null) {
            int squadIndex = FormationHelper.getSquadIndexForPet(owner, mob.getUUID());
            if (squadIndex < 0) {
                return;
            }

            List<UUID> team = ScepterSquadData.getTeam(owner, squadIndex);
            if (!team.isEmpty() && team.get(0).equals(mob.getUUID())) {
                ScepterSquadData.setCommandTarget(owner, squadIndex, pos);
                ScepterSquadData.setAttackTarget(owner, squadIndex, null);
            }

            ItemStack scepter = FormationHelper.getScepterWithPet(owner, mob.getUUID());
            if (scepter.getItem() instanceof AbstractScepterItem item) {
                item.syncToClient(scepter, owner);
            }
        }
    }
}
