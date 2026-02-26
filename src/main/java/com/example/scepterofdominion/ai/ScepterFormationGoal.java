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
            if (scepter.isEmpty() || !(scepter.getItem() instanceof ScepterOfDominionItem item)) {
                return false;
            }
            
            // Only run formation logic if mode is FORMATION (1)
            // SINGLE mode (0) should not trigger this Goal
            int mode = item.getMode(scepter);
            if (mode != ScepterOfDominionItem.MODE_FORMATION) {
                return false;
            }

            // Removed !tamable.isOrderedToSit() check here because we force stand up in command
            // But we should respect sit if user manually ordered sit AFTER command.
            // However, our issueMoveCommand sets sit to false.
            // If we check isOrderedToSit() here, manual sit will stop formation. That is desired.
            return !tamable.isOrderedToSit();
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && !tamable.getNavigation().isDone();
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
            if (!scepter.isEmpty() && scepter.getItem() instanceof ScepterOfDominionItem item) {
                List<UUID> team = item.getTeam(scepter);
                int index = team.indexOf(tamable.getUUID());
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
                Vec3 targetPos = FormationHelper.getFormationPos(centerPos, formation, index, size);
                
                // Move to position
                if (tamable.distanceToSqr(targetPos.x, targetPos.y, targetPos.z) > 4.0D) {
                     tamable.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speedModifier);
                } else {
                     // Close enough, stop or maintain
                     if (tamable.distanceToSqr(targetPos.x, targetPos.y, targetPos.z) < 2.0D) {
                         tamable.getNavigation().stop();
                     }
                }
            }
        }
    }
}
