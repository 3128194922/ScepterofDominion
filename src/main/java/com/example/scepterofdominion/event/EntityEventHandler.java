package com.example.scepterofdominion.event;

import com.example.scepterofdominion.ScepterOfDominion;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ScepterOfDominion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityEventHandler {

    @SubscribeEvent
    public static void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && entity instanceof OwnableEntity ownable) {
            // Check if it has an owner
            if (ownable.getOwner() instanceof Player player) {
                // Check if the pet is in a Scepter team
                if (isPetInScepterTeam(player, entity.getUUID())) {
                    event.setCanceled(true);
                }
            }
        }
    }

    private static boolean isPetInScepterTeam(Player player, UUID petId) {
        // Iterate over player's inventory to find ScepterOfDominionItem
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof ScepterOfDominionItem scepter) {
                List<UUID> team = scepter.getTeam(stack);
                if (team.contains(petId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
