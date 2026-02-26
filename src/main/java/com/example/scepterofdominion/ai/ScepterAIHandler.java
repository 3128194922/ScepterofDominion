package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.ScepterOfDominion;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ScepterOfDominion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ScepterAIHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof TamableAnimal tamable && !event.getLevel().isClientSide) {
            injectScepterAI(tamable);
        }
    }

    public static void injectScepterAI(TamableAnimal tamable) {
        if (tamable.level().isClientSide) return;
        
        // We need to modify the goal selector
        Set<WrappedGoal> availableGoals = tamable.goalSelector.getAvailableGoals();
        
        // Find FollowOwnerGoal
        List<WrappedGoal> goalsToRemove = new ArrayList<>();
        List<Goal> originalGoals = new ArrayList<>();
        List<Integer> priorities = new ArrayList<>();
        
        for (WrappedGoal wrapped : availableGoals) {
            Goal goal = wrapped.getGoal();
            if (goal instanceof FollowOwnerGoal && !(goal instanceof ScepterFollowGoalWrapper)) {
                goalsToRemove.add(wrapped);
                originalGoals.add(goal);
                priorities.add(wrapped.getPriority());
            }
        }
        
        // Remove original goals and add wrappers
        for (int i = 0; i < goalsToRemove.size(); i++) {
            Goal original = originalGoals.get(i);
            int priority = priorities.get(i);
            
            tamable.goalSelector.removeGoal(original);
            
            // Add Wrapper
            ScepterFollowGoalWrapper wrapper = new ScepterFollowGoalWrapper(original, tamable);
            tamable.goalSelector.addGoal(priority, wrapper);
        }
        
        // Add Formation Goal
        // Check if already added?
        boolean hasFormationGoal = false;
        for (WrappedGoal wrapped : tamable.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ScepterFormationGoal) {
                hasFormationGoal = true;
                break;
            }
        }
        
        if (!hasFormationGoal) {
            // Priority 2 is usually high (FollowOwner is often 6 or 2 depending on mob)
            // We want FormationGoal to execute when in team.
            // Since Wrapper returns false when in team, FormationGoal will take over if priority allows.
            // If Wrapper returns false, it doesn't run.
            // FormationGoal should have a priority that allows it to run.
            tamable.goalSelector.addGoal(2, new ScepterFormationGoal(tamable, 1.0D));
        }
    }
}
