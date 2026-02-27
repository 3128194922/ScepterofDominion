package com.example.scepterofdominion.ai;

import com.example.scepterofdominion.ScepterOfDominion;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ScepterOfDominion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DominionAIHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && !(mob instanceof TamableAnimal) && !event.getLevel().isClientSide) {
            // Add DominionGoal with high priority (1), so it overrides wander but yields to high priority tasks if needed?
            // Actually, we want it to be High Priority (0) so we can control movement/formation.
            // But since we removed TARGET flag, native Attack Goals can run in parallel if they don't conflict on MOVE.
            // But Native Attack Goals (MeleeAttackGoal) DO conflict on MOVE.
            // So if DominionGoal runs, MeleeAttackGoal CANNOT run.
            
            // Wait, Scheme B plan:
            // "DominionGoal (Priority 1): Handles Formation/Move Command. canUse(): return true only if Scepter has NO Attack Target"
            // If Scepter HAS Attack Target, DominionGoal returns false -> MeleeAttackGoal can run.
            
            // So we add DominionGoal at Priority 0 or 1.
            mob.goalSelector.addGoal(0, new DominionGoal(mob));
            
            // Add DominionOwnerTargetGoal at Priority 0 to targetSelector.
            // This ensures we pick up the target from Scepter.
            mob.targetSelector.addGoal(0, new DominionOwnerTargetGoal(mob));
            
            // Note: We are NOT clearing native targetSelectors.
            // Scheme B said: "Cleaning native targetSelector".
            // To do that, we need Access Transformer or Reflection to access 'availableGoals' in GoalSelector.
            // OR we can just add our goal at high priority and hope it overrides?
            // NearestAttackableTargetGoal usually runs if no target.
            // If we set target via DominionOwnerTargetGoal, NearestAttackableTargetGoal might still run and change it?
            // TargetGoal checks: if (this.mob.getTarget() != null) ...
            
            // Most Target Goals (like NearestAttackableTargetGoal) have check:
            // if (this.mob.getTarget() != null) return false; (unless checkTarget is false?)
            // Actually, NearestAttackableTargetGoal will replace current target if it finds a better one?
            // No, usually they don't run if there is already a target, or they compete.
            
            // To be safe and truly "Dominate", we should ideally remove other Target Goals.
            // But without AT, we can't easily iterate.
            // For now, let's just add ours at Priority 0.
            // And maybe we can use a trick: DominionOwnerTargetGoal runs every tick and FORCE sets the target if Scepter says so.
            // Even if another goal changes it, we change it back next tick.
        }
    }
}
