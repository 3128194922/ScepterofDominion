package com.example.scepterofdominion.network;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.util.ScepterSquadData;
import com.example.scepterofdominion.world.StorageDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketSummonMount {
    public PacketSummonMount() {
    }

    public PacketSummonMount(FriendlyByteBuf buf) {
    }

    public static PacketSummonMount decode(FriendlyByteBuf buf) {
        return new PacketSummonMount(buf);
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            String rootKey = null;

            if (stack.getItem() instanceof AbstractScepterItem item) {
                rootKey = item.getSquadRootKey();
            }

            UUID mountUUID = null;

            if (rootKey != null) {
                mountUUID = ScepterSquadData.getMount(player, rootKey);
            }

            if (mountUUID == null) {
                for (String rk : new String[]{ScepterSquadData.ROOT_KEY, ScepterSquadData.ROOT_KEY_DOMINION}) {
                    UUID candidate = ScepterSquadData.getMount(player, rk);
                    if (candidate != null) {
                        mountUUID = candidate;
                        rootKey = rk;
                        break;
                    }
                }
            }

            if (mountUUID == null) {
                player.displayClientMessage(Component.translatable("message.scepterofdominion.no_mount_set").withStyle(ChatFormatting.RED), true);
                return;
            }

            if (player.isPassenger() && player.getVehicle().getUUID().equals(mountUUID)) {
                containMount(player);
                return;
            }

            LivingEntity mount = summonMountToPlayer(player, mountUUID, rootKey, stack);
            if (mount == null) {
                player.displayClientMessage(Component.translatable("message.scepterofdominion.mount_not_found").withStyle(ChatFormatting.RED), true);
                return;
            }

            if (player.isPassenger()) {
                player.stopRiding();
            }

            for (Entity passenger : new ArrayList<>(mount.getPassengers())) {
                passenger.stopRiding();
            }

            mount.setPos(player.getX(), player.getY() + 0.5, player.getZ());
            mount.setYRot(player.getYRot());
            mount.setXRot(0.0F);
            mount.yRotO = mount.getYRot();
            mount.setDeltaMovement(0, 0, 0);

            if (mount instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
                mob.getPersistentData().remove("ScepterWaypoints");
                mob.getPersistentData().remove("ScepterClosed");
                mob.getPersistentData().remove("ScepterWaypointsOriginal");
                mob.getPersistentData().putBoolean("ScepterIsMount", true);
                mob.getPersistentData().putUUID("ScepterMountOwner", player.getUUID());
            }

            player.startRiding(mount, true);
            player.displayClientMessage(Component.translatable("message.scepterofdominion.mount_summoned", mount.getName()).withStyle(ChatFormatting.GREEN), true);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void containMount(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        player.stopRiding();
        if (vehicle instanceof LivingEntity living) {
            StorageDimension.containPetDirect((ServerLevel) player.level(), living);
            living.getPersistentData().remove("ScepterIsMount");
            living.getPersistentData().remove("ScepterMountOwner");
            player.displayClientMessage(Component.translatable("message.scepterofdominion.mount_contained", living.getName()).withStyle(ChatFormatting.GREEN), true);
        }
    }

    private static LivingEntity summonMountToPlayer(ServerPlayer player, UUID mountUUID, String rootKey,
                                                      @javax.annotation.Nullable ItemStack stack) {
        ServerLevel targetLevel = (ServerLevel) player.level();

        AbstractScepterItem scepterItem = null;
        if (stack != null && stack.getItem() instanceof AbstractScepterItem item) {
            scepterItem = item;
        }

        Entity alreadyHere = findEntityInLevel(targetLevel, mountUUID, player, scepterItem, stack, rootKey);
        if (alreadyHere instanceof LivingEntity living) {
            float health = living.getHealth();
            if (alreadyHere.isVehicle()) {
                for (Entity p : new ArrayList<>(alreadyHere.getPassengers())) {
                    p.stopRiding();
                }
            }
            living.getPersistentData().remove("ScepterIsMount");
            living.getPersistentData().remove("ScepterMountOwner");
            StorageDimension.containPetDirect(targetLevel, living);
            return tryReleaseFromStorage(player, mountUUID, targetLevel, health);
        }

        LivingEntity fromStorage = tryReleaseFromStorage(player, mountUUID, targetLevel, -1);
        if (fromStorage != null) {
            return fromStorage;
        }

        LivingEntity fromOtherDim = tryFetchFromOtherDimension(player, mountUUID, targetLevel);
        if (fromOtherDim != null) {
            return fromOtherDim;
        }

        return null;
    }

    private static Entity findEntityInLevel(ServerLevel level, UUID mountUUID, ServerPlayer player,
                                             @javax.annotation.Nullable AbstractScepterItem item,
                                             @javax.annotation.Nullable ItemStack stack, String rootKey) {
        Entity entity = level.getEntity(mountUUID);
        if (entity != null) {
            return entity;
        }

        if (item != null && stack != null) {
            List<CompoundTag> teamInfo = item.getTeamInfo(stack, player);
            if (tryLoadFromTeamInfo(level, mountUUID, teamInfo)) {
                return level.getEntity(mountUUID);
            }
        } else {
            for (String rk : new String[]{ScepterSquadData.ROOT_KEY, ScepterSquadData.ROOT_KEY_DOMINION}) {
                List<CompoundTag> teamInfo = ScepterSquadData.getTeamInfo(player, rk);
                if (tryLoadFromTeamInfo(level, mountUUID, teamInfo)) {
                    return level.getEntity(mountUUID);
                }
            }
        }

        return null;
    }

    private static boolean tryLoadFromTeamInfo(ServerLevel level, UUID mountUUID, List<CompoundTag> teamInfo) {
        for (CompoundTag member : teamInfo) {
            if (member.hasUUID("UUID") && member.getUUID("UUID").equals(mountUUID)) {
                if (member.contains("X") && member.contains("Z")) {
                    int x = (int) member.getDouble("X");
                    int z = (int) member.getDouble("Z");
                    ChunkPos cp = new ChunkPos(new BlockPos(x, 0, z));
                    level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 2, cp);
                    LevelChunk chunk = level.getChunkSource().getChunk(cp.x, cp.z, false);
                    if (chunk != null && !chunk.isEmpty()) {
                        Entity entity = level.getEntity(mountUUID);
                        if (entity != null) {
                            return true;
                        }
                    }
                    level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 2, cp);
                }
                return false;
            }
        }
        return false;
    }

    private static LivingEntity tryReleaseFromStorage(ServerPlayer player, UUID mountUUID, ServerLevel targetLevel) {
        return tryReleaseFromStorage(player, mountUUID, targetLevel, -1);
    }

    private static LivingEntity tryReleaseFromStorage(ServerPlayer player, UUID mountUUID, ServerLevel targetLevel, float preserveHealth) {
        ServerLevel storageLevel = player.server.getLevel(StorageDimension.STORAGE_DIMENSION);
        if (storageLevel == null) return null;

        ChunkPos chunkPos = new ChunkPos(StorageDimension.STORAGE_POS);
        storageLevel.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunkPos, 2, chunkPos);

        Entity entity = storageLevel.getEntity(mountUUID);
        if (!(entity instanceof LivingEntity living)) return null;

        for (Entity p : new ArrayList<>(living.getPassengers())) {
            p.stopRiding();
        }

        releaseFromStorage(living);

        float health = preserveHealth > 0 ? preserveHealth : living.getHealth();
        float yaw = living.getYRot();
        float pitch = living.getXRot();

        living.teleportTo(targetLevel, player.getX(), player.getY() + 0.5, player.getZ(),
                EnumSet.noneOf(RelativeMovement.class), yaw, pitch);

        Entity relocated = targetLevel.getEntity(living.getUUID());
        if (relocated instanceof LivingEntity result) {
            result.setHealth(health);
            ScepterSquadData.updatePetLocation(player, result.getUUID(), player.position(),
                    player.level().dimension().location().toString());
            return result;
        }

        return null;
    }

    private static LivingEntity tryFetchFromOtherDimension(ServerPlayer player, UUID mountUUID, ServerLevel targetLevel) {
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level == targetLevel || level.dimension().equals(StorageDimension.STORAGE_DIMENSION)) {
                continue;
            }
            ChunkPos chunkPos = new ChunkPos(level.getSharedSpawnPos());
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunkPos, 2, chunkPos);
            Entity entity = level.getEntity(mountUUID);
            if (entity instanceof LivingEntity living) {
                releaseFromStorage(living);

                float health = living.getHealth();
                float yaw = living.getYRot();
                float pitch = living.getXRot();

                living.teleportTo(targetLevel, player.getX(), player.getY() + 0.5, player.getZ(),
                        EnumSet.noneOf(RelativeMovement.class), yaw, pitch);

                Entity relocated = targetLevel.getEntity(living.getUUID());
                if (relocated instanceof LivingEntity result) {
                    result.setHealth(health);
                    ScepterSquadData.updatePetLocation(player, result.getUUID(), player.position(),
                            player.level().dimension().location().toString());
                    return result;
                }
            }
        }

        return null;
    }

    private static void releaseFromStorage(LivingEntity living) {
        living.setNoGravity(false);
        if (living instanceof Mob mob) {
            mob.setNoAi(false);
        }
    }
}
