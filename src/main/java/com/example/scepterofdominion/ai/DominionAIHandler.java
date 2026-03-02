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
            mob.goalSelector.addGoal(0, new ScepterWaypointGoal(mob));
            mob.goalSelector.addGoal(1, new DominionGoal(mob));
            mob.targetSelector.addGoal(0, new DominionOwnerTargetGoal(mob));
        }
    }
}
