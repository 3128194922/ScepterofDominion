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
        containPets(player, stack, null);
    }

    public static void containPets(ServerPlayer player, ItemStack stack, @javax.annotation.Nullable Entity specificEntity) {
        if (!(stack.getItem() instanceof AbstractScepterItem item)) return;

        List<CompoundTag> teamInfo = item.getTeamInfo(stack);
        List<UUID> team = item.getTeam(stack);
        if (team.isEmpty()) {
            if (specificEntity == null) {
                player.displayClientMessage(Component.translatable("message.scepterofdominion.team_empty").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        ServerLevel currentLevel = (ServerLevel) player.level();
        ServerLevel storageLevel = player.server.getLevel(STORAGE_DIMENSION);

        if (storageLevel == null) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.dimension_missing").withStyle(ChatFormatting.RED), true);
            return;
        }

        // Force load chunk in storage dimension
        ChunkPos storageChunkPos = new ChunkPos(STORAGE_POS);
        storageLevel.getChunkSource().addRegionTicket(TicketType.FORCED, storageChunkPos, 2, storageChunkPos);

        int count = 0;
        
        // Use a Set to avoid duplicates if specificEntity is also found in scan
        Set<Entity> entitiesToContain = new HashSet<>();
        
        if (specificEntity != null) {
            if (team.contains(specificEntity.getUUID())) {
                entitiesToContain.add(specificEntity);
            }
        } else {
            // 1. Try to find entities in current level (loaded)
            AABB searchArea = player.getBoundingBox().inflate(100.0);
            entitiesToContain.addAll(currentLevel.getEntities(player, searchArea, e -> team.contains(e.getUUID())));
            
            // 2. Try to find entities in other dimensions or unloaded chunks using saved info
            for (CompoundTag member : teamInfo) {
                UUID uuid = member.getUUID("UUID");
                
                // Skip if already found
                boolean alreadyFound = false;
                for (Entity e : entitiesToContain) {
                    if (e.getUUID().equals(uuid)) {
                        alreadyFound = true;
                        break;
                    }
                }
                if (alreadyFound) continue;

                if (member.contains("Dimension") && member.contains("X")) {
                    ResourceLocation dimLoc = new ResourceLocation(member.getString("Dimension"));
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                    ServerLevel memberLevel = player.server.getLevel(dimKey);
                    
                    if (memberLevel != null) {
                        // Check if loaded first
                        Entity loadedEntity = memberLevel.getEntity(uuid);
                        if (loadedEntity != null) {
                            entitiesToContain.add(loadedEntity);
                            continue;
                        }
                        
                        // Force load chunk
                        double x = member.getDouble("X");
                        double z = member.getDouble("Z");
                        
                        // Calculate search radius (Max(4, ViewDistance))
                        int viewDistance = player.server.getPlayerList().getViewDistance();
                        int radius = Math.max(4, viewDistance);
                        
                        ChunkPos centerChunkPos = new ChunkPos(BlockPos.containing(x, 0, z));
                        
                        // Load chunks in radius using Spiral Search
                        // To avoid lag, we load one by one and check
                        
                        // Spiral logic:
                        // 0,0
                        // 1,0 -> 1,1 -> 0,1 -> -1,1 -> -1,0 -> -1,-1 -> 0,-1 -> 1,-1 -> 1,0 (loop)
                        // But easier to just iterate standard loop but break if found.
                        // Actually, standard loop is fine if we process one at a time.
                        
                        // To implement "spiral" properly:
                        int xStart = centerChunkPos.x;
                        int zStart = centerChunkPos.z;
                        
                        // Check center first
                        if (checkAndLoadChunk(memberLevel, uuid, new ChunkPos(xStart, zStart), entitiesToContain)) {
                            continue; // Found!
                        }
                        
                        // Spiral loop
                        boolean found = false;
                        for (int r = 1; r <= radius; r++) {
                            // Top row
                            for (int dx = -r; dx <= r; dx++) {
                                if (checkAndLoadChunk(memberLevel, uuid, new ChunkPos(xStart + dx, zStart - r), entitiesToContain)) {
                                    found = true; break;
                                }
                            }
                            if (found) break;
                            
                            // Bottom row
                            for (int dx = -r; dx <= r; dx++) {
                                if (checkAndLoadChunk(memberLevel, uuid, new ChunkPos(xStart + dx, zStart + r), entitiesToContain)) {
                                    found = true; break;
                                }
                            }
                            if (found) break;
                            
                            // Left column (excluding corners already checked)
                            for (int dz = -r + 1; dz <= r - 1; dz++) {
                                if (checkAndLoadChunk(memberLevel, uuid, new ChunkPos(xStart - r, zStart + dz), entitiesToContain)) {
                                    found = true; break;
                                }
                            }
                            if (found) break;
                            
                            // Right column (excluding corners already checked)
                            for (int dz = -r + 1; dz <= r - 1; dz++) {
                                if (checkAndLoadChunk(memberLevel, uuid, new ChunkPos(xStart + r, zStart + dz), entitiesToContain)) {
                                    found = true; break;
                                }
                            }
                            if (found) break;
                        }
                    }
                }
            }
        }

        for (Entity entity : entitiesToContain) {
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

    private static boolean checkAndLoadChunk(ServerLevel level, UUID uuid, ChunkPos chunkPos, Set<Entity> entitiesToContain) {
        // Add ticket
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunkPos, 2, chunkPos);
        
        // Tick to process load
        level.getChunkSource().tick(() -> true, false);
        
        // Check for entity
        Entity entity = level.getEntity(uuid);
        boolean found = false;
        if (entity != null) {
            entitiesToContain.add(entity);
            found = true;
        }
        
        // Remove ticket immediately
        level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunkPos, 2, chunkPos);
        
        return found;
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
                    
                    // Update release position in Scepter NBT
                    ItemStack scepter = com.example.scepterofdominion.util.FormationHelper.getScepterWithPet(player, living.getUUID());
                    if (!scepter.isEmpty() && scepter.getItem() instanceof AbstractScepterItem scepterItem) {
                         List<CompoundTag> teamInfo = scepterItem.getTeamInfo(scepter);
                         for (CompoundTag member : teamInfo) {
                             if (member.hasUUID("UUID") && member.getUUID("UUID").equals(living.getUUID())) {
                                 member.putString("Dimension", player.level().dimension().location().toString());
                                 member.putDouble("X", player.getX());
                                 member.putDouble("Y", player.getY());
                                 member.putDouble("Z", player.getZ());
                                 // We need to write back to NBT
                                 // Since getTeamInfo returns a copy of NBT list content? 
                                 // No, getTeamInfo implementation returns a NEW list of compound tags.
                                 // So modifying 'member' here does NOT modify the stack NBT.
                                 
                                 // We need a way to update specific member tag.
                                 // Let's use a helper method in AbstractScepterItem or do it manually.
                                 CompoundTag scepterTag = scepter.getOrCreateTag();
                                 if (scepterTag.contains("Team", 9)) {
                                     net.minecraft.nbt.ListTag list = scepterTag.getList("Team", 10);
                                     for (int i = 0; i < list.size(); i++) {
                                         CompoundTag m = list.getCompound(i);
                                         if (m.hasUUID("UUID") && m.getUUID("UUID").equals(living.getUUID())) {
                                             m.putString("Dimension", player.level().dimension().location().toString());
                                             m.putDouble("X", player.getX());
                                             m.putDouble("Y", player.getY());
                                             m.putDouble("Z", player.getZ());
                                             break; // Found and updated
                                         }
                                     }
                                 }
                                 break;
                             }
                         }
                    }

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
