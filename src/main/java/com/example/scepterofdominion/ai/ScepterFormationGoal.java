package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.item.ScepterOfDominionItem;
import com.example.scepterofdominion.util.FormationHelper;
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
            
            // Get Scepter to check mode
            ItemStack scepter = FormationHelper.getScepterWithPet(p, tamable.getUUID());
            if (scepter.isEmpty() || !(scepter.getItem() instanceof net.minecraft.world.item.Item)) { // Relaxed check to AbstractScepterItem via inheritance or just instance check
                 // The import was specific to ScepterOfDominionItem, but DominionScepterItem also uses this.
                 // We should check AbstractScepterItem instead.
                 // But wait, FormationHelper returns ItemStack.
                 if (!(scepter.getItem() instanceof com.example.scepterofdominion.item.AbstractScepterItem)) return false;
            }
            com.example.scepterofdominion.item.AbstractScepterItem item = (com.example.scepterofdominion.item.AbstractScepterItem) scepter.getItem();
            
            // Only run formation logic if mode is FORMATION (1)
            // SINGLE mode (0) should not trigger this Goal
            int mode = item.getMode(scepter);
            if (mode != com.example.scepterofdominion.item.AbstractScepterItem.MODE_FORMATION) {
                return false;
            }

            return !tamable.isOrderedToSit();
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
            
            ItemStack scepter = FormationHelper.getScepterWithPet(owner, tamable.getUUID());
            if (!scepter.isEmpty() && scepter.getItem() instanceof com.example.scepterofdominion.item.AbstractScepterItem item) {
                List<UUID> team = item.getTeam(scepter);
                int index = team.indexOf(tamable.getUUID());
                if (index == -1) return; // Should not happen if canUse passed, but safety first
                
                int size = team.size();
                int formation = scepter.getOrCreateTag().getInt("Formation");
                
                // Calculate target position
                Vec3 centerPos;
                Vec3 commandTarget = item.getCommandTarget(scepter);
                if (commandTarget != null) {
                    centerPos = commandTarget;
                } else {
                    centerPos = owner.position();
                }
                
                // Note: The FormationHelper.getFormationPos assumes a "center". 
                // But it doesn't rotate based on facing.
                // Let's assume standard orientation for now.
                // WAIT. FormationHelper.getFormationPos does not exist in the snippets provided before, 
                // but AbstractScepterItem has calculateFormationPositions.
                // FormationHelper is likely a utility class.
                // But previously AbstractScepterItem was calculating positions!
                // Let's check where calculateFormationPositions is. It's in AbstractScepterItem.
                // And it returns a LIST of positions.
                
                // We should use AbstractScepterItem's method to be consistent!
                // But we need the list of ENTITIES, not just UUIDs, to calculate proper spacing (width based).
                // AbstractScepterItem.calculateFormationPositions takes List<Entity>.
                
                // So we need to reconstruct the active members list here to get accurate positions.
                // This is expensive to do every tick per mob.
                // Ideally, the "Leader" calculates and sets "TargetPos" for everyone?
                // Or we just approximate using average width?
                
                // Let's try to find the entities.
                java.util.List<net.minecraft.world.entity.Entity> activeMembers = new java.util.ArrayList<>();
                if (tamable.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    for (UUID uuid : team) {
                        net.minecraft.world.entity.Entity entity = serverLevel.getEntity(uuid);
                        if (entity != null) {
                            activeMembers.add(entity);
                        }
                    }
                }
                
                // If we can't find everyone, the formation might be wonky.
                // But we should try to find our position in the calculated list.
                // The list order matches team order (filtered by active).
                
                List<Vec3> positions = item.calculateFormationPositions(centerPos, formation, activeMembers);
                
                // Find our index in activeMembers
                int myIndex = -1;
                for (int i = 0; i < activeMembers.size(); i++) {
                    if (activeMembers.get(i).getUUID().equals(tamable.getUUID())) {
                        myIndex = i;
                        break;
                    }
                }
                
                if (myIndex != -1 && myIndex < positions.size()) {
                    Vec3 targetPos = positions.get(myIndex);
                    
                    // Move to position
                    double distSqr = tamable.distanceToSqr(targetPos.x, targetPos.y, targetPos.z);
                    if (distSqr > 4.0D) {
                         tamable.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speedModifier);
                    } else if (distSqr > 1.0D) {
                         // Fine adjustment
                         tamable.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speedModifier * 0.5);
                    } else {
                         // Close enough, stop
                         tamable.getNavigation().stop();
                    }
                }
            }
        }
    }
}
