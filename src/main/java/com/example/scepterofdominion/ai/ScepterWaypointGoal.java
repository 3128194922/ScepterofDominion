package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import com.example.scepterofdominion.util.FormationHelper;
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
            
            // If this was the last waypoint, set CommandTarget to this position to maintain position
            if (waypoints.isEmpty()) {
                updateCommandTarget(new Vec3(x, y, z));
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
            ItemStack scepter = FormationHelper.getScepterWithPet(owner, mob.getUUID());
            if (!scepter.isEmpty() && scepter.getItem() instanceof AbstractScepterItem item) {
                // If in SINGLE mode and this mob is focus, update global command target
                int mode = item.getMode(scepter);
                if (mode == AbstractScepterItem.MODE_SINGLE) {
                    UUID focus = item.getFocus(scepter);
                    if (focus != null && focus.equals(mob.getUUID())) {
                        item.setCommandTarget(scepter, pos);
                        item.syncToClient(scepter, owner);
                    }
                } else {
                    // In Formation/Team mode, we MUST update the global CommandTarget to the last waypoint position.
                    // However, 'pos' here is the INDIVIDUAL unit's target position, which includes formation offsets.
                    // If we set CommandTarget to this offset position, the whole formation will shift again relative to it.
                    // We need to recover the ORIGINAL center position of the last waypoint.
                    
                    // The ScepterWaypoints NBT structure on the entity stores the calculated individual position.
                    // It does NOT store the original center.
                    // BUT, if we want the formation to HOLD at the end, we need the center.
                    
                    // Actually, let's look at how executeWaypoints works.
                    // It calculates individual positions and stores them.
                    
                    // If we want to support "Move to end and stay there in formation", 
                    // we need to know where the "Formation Center" should be.
                    
                    // Hacky solution: 
                    // When executeWaypoints is called, we could store the "Final Center" in the Entity's NBT as well?
                    // Or, we just update CommandTarget based on this unit's position minus its formation offset?
                    // That's hard because offset depends on index and formation type.
                    
                    // Better solution:
                    // Just set CommandTarget to 'pos'. 
                    // This effectively makes the "End Position" of this specific unit the new "Center" of the formation.
                    // This will cause a slight shift (regrouping around the leader/unit that finished last), 
                    // but it's better than running back to start.
                    // AND, if we only let the "Leader" (index 0) update it?
                    // We don't easily know who is index 0 here without checking scepter team list.
                    
                    List<UUID> team = item.getTeam(scepter);
                    if (!team.isEmpty() && team.get(0).equals(mob.getUUID())) {
                         // Only the first member updates the global target to avoid race conditions
                         // But we need to reverse the offset? 
                         // Or we simply accept that the Leader's position becomes the new Center.
                         // For most formations, Leader is at center or front.
                         item.setCommandTarget(scepter, pos);
                         item.syncToClient(scepter, owner);
                    }
                }
            }
        }
    }
}
