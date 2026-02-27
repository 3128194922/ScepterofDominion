package com.example.scepterofdominion.item;

import com.example.scepterofdominion.Config;
import com.example.scepterofdominion.ScepterOfDominion;
import com.example.scepterofdominion.ai.DominionGoal;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

public class DominionScepterItem extends AbstractScepterItem {

    public DominionScepterItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canControl(LivingEntity entity, Player player) {
        // Check Whitelist
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id != null) {
            List<? extends String> whitelist = Config.COMMON.dominionWhitelist.get();
            if (!whitelist.isEmpty() && !whitelist.contains(id.toString())) {
                return false;
            }
        }

        // Can control if it is a Mob and NOT a TamableAnimal (or at least not tamed, but user said "untameable")
        // User said: "scepter of dominion... mainly adds non-tameable mobs... only dominate unable to be tamed mobs"
        if (entity instanceof Mob && !(entity instanceof TamableAnimal)) {
            // Also check if already dominated by someone else?
            if (entity.getPersistentData().hasUUID("DominionOwner")) {
                return entity.getPersistentData().getUUID("DominionOwner").equals(player.getUUID());
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onEntityAdded(LivingEntity entity, Player player) {
        if (entity instanceof Mob mob) {
            // Set Dominion Owner
            mob.getPersistentData().putUUID("DominionOwner", player.getUUID());
            
            // Play Sound
            player.level().playSound(null, player.blockPosition(), ScepterOfDominion.YURI_SOUND.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            
            // Inject Goal if not present (handled by Handler usually, but we can do it here too to be safe)
            // But GoalSelector manipulation is best done in Event or via explicit method if we have access.
            // Since we don't have easy access to GoalSelector here without access transformer or helper, 
            // we rely on the EntityJoinLevelEvent handler or we can try to add it now if we cast.
            // Actually, we can just ensure the tag is set. The Goal (added on join) will wake up.
            // IF the entity is already in world, the Goal might be there but canUse() was false.
            // Now canUse() will be true.
            
            // However, if the mob was spawned BEFORE the mod was installed (unlikely for new item usage) or 
            // if we need to dynamically add the goal to mobs that don't have it yet.
            // Better to add the goal to ALL mobs on join, and let it sleep.
        }
    }

    @Override
    protected void onEntityRemoved(LivingEntity entity, Player player) {
        if (entity instanceof Mob mob) {
            // Remove Dominion Owner
            mob.getPersistentData().remove("DominionOwner");
            // The Goal will see this and stop running.
            // Original AI will resume because DominionGoal stops running and un-suppresses.
        }
    }

    @Override
    protected void commandEntityMove(Entity entity, Vec3 target, boolean isSprint) {
        // Movement is handled by DominionGoal ticking.
        // We just need to ensure the target is set in NBT (handled by AbstractScepterItem.setCommandTarget).
        // But wait, AbstractScepterItem calls this method.
        // For ScepterOfDominionItem, it uses direct navigation.
        // For DominionScepterItem, we rely on the Goal reading the NBT.
        // So this method can be empty?
        // OR, we can force an immediate update if needed.
        // But since the Goal runs every tick, it will pick up the new target from NBT.
        // AbstractScepterItem sets "CommandTarget" in NBT BEFORE calling this.
        // So we can leave this empty or use it to wake up the mob.
        
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop(); // Stop current path to force recalc
            // Force recalc path in DominionGoal by resetting timer or similar if we had access.
            // But just stopping navigation might be enough if tick logic checks distance.
        }
    }

    @Override
    protected void commandEntityAttack(Entity entity, LivingEntity target) {
        // Similarly, handled by Goal reading NBT.
        if (entity instanceof Mob mob) {
            mob.setTarget(target);
        }
    }
}
