package com.example.scepterofdominion.util;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class FormationHelper {

    public static Vec3 getFormationPos(Vec3 center, int formation, int index, int size) {
        // Formations based on ShinColle FormationHelper logic
        // 0: Line Ahead (单纵)
        // 1: Double Line (复纵)
        // 2: Diamond (轮形)
        // 3: Echelon (梯形)
        // 4: Line Abreast (单横)
        // 5: None (无)

        double x = 0;
        double z = 0;
        double spacing = 3.0; // ShinColle uses 3 blocks spacing generally
        
        switch (formation) {
            case 0: // Line Ahead
                z = index * spacing;
                break;
                
            case 1: // Double Line
                x = (index % 2 == 0 ? -1 : 1) * spacing;
                z = (index / 2) * spacing;
                break;
                
            case 2: // Diamond
                if (index == 0) { x=0; z=0; }
                else if (index == 1) { x=0; z=-spacing*1.5; } // Front
                else if (index == 2) { x=-spacing*1.5; z=0; } // Left
                else if (index == 3) { x=spacing*1.5; z=0; } // Right
                else if (index == 4) { x=0; z=spacing*1.5; } // Back
                else { 
                    // Extra overlap center for >5
                    double angle = 2 * Math.PI * (index-5) / (Math.max(1, size-5));
                    double radius = spacing * 2;
                    x = Math.cos(angle) * radius;
                    z = Math.sin(angle) * radius;
                } 
                break;
                
            case 3: // Echelon
                x = index * spacing * 0.7;
                z = index * spacing * 0.7;
                break;
                
            case 4: // Line Abreast
                x = (index - (size - 1) / 2.0) * spacing;
                break;
                
            case 5: // None (无/散开)
            default:
                // Random/Cluster but deterministic based on index
                // Use a simple circle distribution to avoid overlap
                if (index == 0) { x=0; z=0; }
                else {
                    double angle = 2 * Math.PI * index / Math.max(1, size);
                    double radius = spacing; // Keep them close but not overlapping
                    x = Math.cos(angle) * radius;
                    z = Math.sin(angle) * radius;
                }
                break;
        }
        
        return center.add(x, 0, z);
    }

    public static boolean isPetInScepterTeam(Player player, UUID petId) {
        // Iterate over player's inventory to find ScepterOfDominionItem
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractScepterItem scepter) {
                List<UUID> team = scepter.getTeam(stack);
                if (team.contains(petId)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public static ItemStack getScepterWithPet(Player player, UUID petId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractScepterItem scepter) {
                List<UUID> team = scepter.getTeam(stack);
                if (team.contains(petId)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
