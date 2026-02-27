package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.item.DominionScepterItem;
import com.example.scepterofdominion.util.FormationHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.UUID;

public class DominionOwnerTargetGoal extends TargetGoal {
    private final Mob mob;

    public DominionOwnerTargetGoal(Mob mob) {
        super(mob, false, false);
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!mob.getPersistentData().hasUUID("DominionOwner")) return false;

        UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
        Player owner = mob.level().getPlayerByUUID(ownerId);
        if (owner == null) return false;

        if (!FormationHelper.isPetInScepterTeam(owner, mob.getUUID())) return false;

        ItemStack scepter = FormationHelper.getScepterWithPet(owner, mob.getUUID());
        if (scepter.isEmpty() || !(scepter.getItem() instanceof DominionScepterItem item)) return false;

        UUID attackTargetUUID = item.getAttackTarget(scepter);
        return attackTargetUUID != null;
    }

    @Override
    public void start() {
        UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
        Player owner = mob.level().getPlayerByUUID(ownerId);
        if (owner != null) {
            ItemStack scepter = FormationHelper.getScepterWithPet(owner, mob.getUUID());
            if (!scepter.isEmpty() && scepter.getItem() instanceof DominionScepterItem item) {
                UUID attackTargetUUID = item.getAttackTarget(scepter);
                if (attackTargetUUID != null) {
                    net.minecraft.world.entity.Entity target = ((net.minecraft.server.level.ServerLevel)mob.level()).getEntity(attackTargetUUID);
                    if (target instanceof LivingEntity living) {
                        this.mob.setTarget(living);
                    }
                }
            }
        }
        super.start();
    }
}
