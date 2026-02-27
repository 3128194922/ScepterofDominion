package com.example.scepterofdominion.world;

import com.example.scepterofdominion.ScepterOfDominion;
import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.ChatFormatting;

import java.util.*;

public class StorageDimension {
    public static final ResourceKey<Level> STORAGE_DIMENSION = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("scepterofdominion:storage"));
    private static final BlockPos STORAGE_POS = new BlockPos(0, 100, 0); // Safe height, assuming platform or void with NoGravity

    public static void containPets(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof AbstractScepterItem item)) return;

        List<UUID> team = item.getTeam(stack);
        if (team.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.team_empty").withStyle(ChatFormatting.RED), true);
            return;
        }

        ServerLevel currentLevel = (ServerLevel) player.level();
        ServerLevel storageLevel = player.server.getLevel(STORAGE_DIMENSION);

        if (storageLevel == null) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.dimension_missing").withStyle(ChatFormatting.RED), true);
            return;
        }

        // Force load chunk in storage dimension
        ChunkPos chunkPos = new ChunkPos(STORAGE_POS);
        storageLevel.getChunkSource().addRegionTicket(TicketType.FORCED, chunkPos, 2, chunkPos);

        int count = 0;
        
        // Scan for pets in current level
        // Since we have UUIDs, we can try to find them directly if loaded
        // Or scan a large area around player
        AABB searchArea = player.getBoundingBox().inflate(100.0); // 100 blocks radius
        List<Entity> entities = currentLevel.getEntities(player, searchArea, e -> team.contains(e.getUUID()));

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living) {
                // Teleport logic from PetConnect
                try {
                    // Effects: Particles and Sound
                    currentLevel.sendParticles(ParticleTypes.PORTAL, living.getX(), living.getY() + 0.5, living.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                    currentLevel.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);

                    float health = living.getHealth();
                    float yaw = living.getYRot();
                    float pitch = living.getXRot();
                    Set<RelativeMovement> relativeMovements = EnumSet.noneOf(RelativeMovement.class);
                    
                    living.setNoGravity(true);
                    
                    CompoundTag tag = new CompoundTag();
                    living.saveWithoutId(tag);
                    tag.putBoolean("NoAI", true);
                    living.load(tag);
                    
                    // Teleport
                    living.teleportTo(storageLevel, STORAGE_POS.getX() + 0.5, STORAGE_POS.getY(), STORAGE_POS.getZ() + 0.5, relativeMovements, yaw, pitch);
                    
                    living.setHealth(health); // Restore health after teleport
                    
                    count++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (count > 0) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.contained", count).withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.no_pets_found").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    public static void releasePets(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof AbstractScepterItem item)) return;

        List<UUID> team = item.getTeam(stack);
        if (team.isEmpty()) return;

        ServerLevel storageLevel = player.server.getLevel(STORAGE_DIMENSION);
        if (storageLevel == null) return;

        // Force load chunk
        ChunkPos chunkPos = new ChunkPos(STORAGE_POS);
        storageLevel.getChunkSource().addRegionTicket(TicketType.FORCED, chunkPos, 2, chunkPos);

        // Scan storage dimension for pets
        // Since they are all at 0,100,0, small area is fine
        AABB searchArea = new AABB(STORAGE_POS).inflate(50.0);
        
        // Find all entities in storage that are in the team
        // Note: We iterate over all entities in the box and check if their UUID is in the team list
        List<Entity> entities = storageLevel.getEntities((Entity)null, searchArea, e -> team.contains(e.getUUID()));
        
        int count = 0;
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living) {
                try {
                    float health = living.getHealth();
                    float yaw = living.getYRot();
                    float pitch = living.getXRot();
                    Set<RelativeMovement> relativeMovements = EnumSet.noneOf(RelativeMovement.class);
                    
                    living.setNoGravity(false);
                    
                    CompoundTag tag = new CompoundTag();
                    living.saveWithoutId(tag);
                    tag.putBoolean("NoAI", false);
                    living.load(tag);
                    
                    // Teleport back to player
                    living.teleportTo((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), relativeMovements, yaw, pitch);
                    
                    living.setHealth(health);
                    
                    count++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        if (count > 0) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.released", count).withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.no_pets_stored").withStyle(ChatFormatting.YELLOW), true);
        }
    }
}
