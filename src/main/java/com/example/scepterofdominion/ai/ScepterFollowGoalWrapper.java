package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.item.ScepterOfDominionItem;
import com.example.scepterofdominion.util.FormationHelper;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class ScepterFollowGoalWrapper extends Goal {
    private final Goal originalGoal;
    private final TamableAnimal tamable;

    public ScepterFollowGoalWrapper(Goal originalGoal, TamableAnimal tamable) {
        this.originalGoal = originalGoal;
        this.tamable = tamable;
        this.setFlags(originalGoal.getFlags());
    }

    @Override
    public boolean canUse() {
        if (isInScepterTeam()) {
            return false; // Disable vanilla follow if in team
        }
        return originalGoal.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (isInScepterTeam()) {
            return false; // Stop vanilla follow if joined team
        }
        return originalGoal.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return originalGoal.isInterruptable();
    }

    @Override
    public void start() {
        originalGoal.start();
    }

    @Override
    public void stop() {
        originalGoal.stop();
    }

    @Override
    public void tick() {
        originalGoal.tick();
    }

    @Override
    public void setFlags(EnumSet<Flag> flags) {
        super.setFlags(flags);
        // We don't propagate setFlags to originalGoal here because we want this wrapper to respect the flags
        // and the original goal might have its own internal flag management, but usually goals set flags in constructor.
    }

    private boolean isInScepterTeam() {
        if (tamable.getOwner() instanceof Player player) {
            return FormationHelper.isPetInScepterTeam(player, tamable.getUUID());
        }
        return false;
    }
}
