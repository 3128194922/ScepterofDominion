package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.item.DominionScepterItem;
import com.example.scepterofdominion.util.FormationHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;

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
        // Only run if the entity is dominated
        if (!mob.getPersistentData().hasUUID("DominionOwner")) return false;

        UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
        Player owner = mob.level().getPlayerByUUID(ownerId);
        if (owner == null) return false;

        // Check if mob is in team
        if (!FormationHelper.isPetInScepterTeam(owner, mob.getUUID())) return false;

        ItemStack scepter = FormationHelper.getScepterWithPet(owner, mob.getUUID());
        if (scepter.isEmpty() || !(scepter.getItem() instanceof DominionScepterItem item)) return false;

        // If we have an Attack Target, we yield control to the native Attack Goal (which usually handles movement to target)
        // UNLESS we are in a formation that requires holding position?
        // No, standard behavior: Attack Target > Formation Position.
        // So if Attack Target is set, this Goal should NOT run.
        UUID attackTargetUUID = item.getAttackTarget(scepter);
        if (attackTargetUUID != null) {
            // But wait, we need to set the mob's target so the native AI picks it up.
            // We can't do that in canUse easily.
            // Actually, we can do it in tick() if we run.
            // BUT if we return false here, tick() won't run.
            
            // Strategy:
            // This Goal runs if:
            // 1. No Attack Target is set via Scepter.
            // OR
            // 2. Attack Target IS set, but we are just setting it and yielding?
            
            // Better Strategy for Scheme B:
            // This Goal handles MOVEMENT/FORMATION when NOT attacking.
            // If Scepter has Attack Target, we let Native AI handle it.
            // So return false if Attack Target is present?
            
            // Let's check if the mob has a target.
            // If Scepter says Attack, we need to ensure Mob.setTarget is called.
            // Who calls it? We can have a separate "DominionTargetGoal" for that?
            // Or we can do it here but return false for "using navigation".
            
            // Scheme B (Hybrid):
            // 1. DominionTargetGoal (Priority 0): Sets mob.target based on Scepter.
            // 2. DominionGoal (Priority 1): Handles Formation/Move Command.
            //    canUse(): return true only if Scepter has NO Attack Target (or target is dead).
            
            // So here (DominionGoal), we return false if Scepter has Attack Target.
             return false;
        }

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

        ItemStack scepter = FormationHelper.getScepterWithPet(owner, mob.getUUID());
        if (scepter.isEmpty() || !(scepter.getItem() instanceof DominionScepterItem item)) {
            mob.getNavigation().stop();
            return;
        }

        // We only run if NO Attack Target (checked in canUse/canContinueToUse).
        // So we just handle Movement / Formation.

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;

            int mode = item.getMode(scepter);
            List<UUID> team = item.getTeam(scepter);
            
            // Single Mode
            if (mode == AbstractScepterItem.MODE_SINGLE) {
                UUID focus = item.getFocus(scepter);
                if (mob.getUUID().equals(focus)) {
                    Vec3 commandTarget = item.getCommandTarget(scepter);
                    if (commandTarget != null) {
                        mob.getNavigation().moveTo(commandTarget.x, commandTarget.y, commandTarget.z, 1.2);
                    } else {
                        // Follow player if no target? Or stand still?
                        // Existing scepter: "issueMoveCommand" sets target. If no target set, it doesn't move.
                        // But wait, ScepterFormationGoal follows player if no target.
                        // Let's check ScepterFormationGoal again.
                        // ScepterFormationGoal: centerPos = commandTarget != null ? commandTarget : owner.position();
                        // So it defaults to following owner.
                        
                        // But for Single Mode, ScepterFormationGoal returns false (canUse).
                        // So in Single Mode, existing logic relies on issueMoveCommand being called once.
                        // But here we are the ONLY goal running.
                        // So we must handle Single Mode movement too if we want "AI Takeover".
                        // If no command target, stick to owner?
                        
                        // If I just stand still, I can't follow owner.
                        // Let's default to following owner if no command target.
                        
                         mob.getNavigation().moveTo(owner, 1.0);
                    }
                } else {
                    // Not focused in Single Mode -> Stand still / Idle
                     mob.getNavigation().stop();
                }
                return;
            }

            // Formation Mode
            int index = team.indexOf(mob.getUUID());
            int size = team.size();
            int formation = scepter.getOrCreateTag().getInt("Formation");

            Vec3 centerPos;
            Vec3 commandTarget = item.getCommandTarget(scepter);
            if (commandTarget != null) {
                centerPos = commandTarget;
            } else {
                centerPos = owner.position();
            }

            Vec3 targetPos = FormationHelper.getFormationPos(centerPos, formation, index, size);

            if (mob.distanceToSqr(targetPos.x, targetPos.y, targetPos.z) > 4.0D) {
                mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.2);
            } else {
                if (mob.distanceToSqr(targetPos.x, targetPos.y, targetPos.z) < 2.0D) {
                    mob.getNavigation().stop();
                }
            }
            
            // Look at owner or target
            if (commandTarget != null) {
                // Look at where they are going
                // mob.getLookControl().setLookAt(commandTarget.x, commandTarget.y, commandTarget.z, 10.0F, (float)mob.getMaxHeadXRot());
            } else {
                // Only look at owner if idle and close
                if (mob.distanceToSqr(owner) < 100) {
                     mob.getLookControl().setLookAt(owner, 10.0F, (float)mob.getMaxHeadXRot());
                }
            }
        }
    }
}
