package com.example.scepterofdominion.item;

import com.example.scepterofdominion.Config;
import com.example.scepterofdominion.ai.ScepterAIHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class ScepterOfDominionItem extends AbstractScepterItem {

    public ScepterOfDominionItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canControl(LivingEntity entity, Player player) {
        // Check Blacklist
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id != null) {
            List<? extends String> blacklist = Config.COMMON.scepterBlacklist.get();
            if (blacklist.contains(id.toString())) {
                return false;
            }
        }

        if (entity instanceof OwnableEntity ownable) {
            return player.getUUID().equals(ownable.getOwnerUUID());
        }
        return false;
    }

    @Override
    protected void onEntityAdded(LivingEntity entity, Player player) {
        if (entity instanceof TamableAnimal tamable) {
            ScepterAIHandler.injectScepterAI(tamable);
        }
    }

    @Override
    protected void onEntityRemoved(LivingEntity entity, Player player) {
        // No special logic needed for removal in original code
    }

    @Override
    protected void commandEntityMove(Entity entity, Vec3 target, boolean isSprint) {
        if (entity instanceof TamableAnimal mob) {
             mob.setOrderedToSit(false); // Force stand up
             mob.getNavigation().moveTo(target.x, target.y, target.z, 1.5D);
             mob.setTarget(null);
        }
    }

    @Override
    protected void commandEntityAttack(Entity entity, LivingEntity target) {
        if (entity instanceof TamableAnimal mob) {
            mob.setTarget(target);
        }
    }
}
