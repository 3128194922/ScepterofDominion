package com.example.scepterofdominion.network;

import com.example.scepterofdominion.item.AbstractScepterItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketScepterRightClick {
    private final boolean isSprint;
    private final boolean hitEntity;
    private final int entityId; // Use ID for network efficiency, resolve on server
    private final double x;
    private final double y;
    private final double z;

    public PacketScepterRightClick(boolean isSprint, boolean hitEntity, int entityId, Vec3 pos) {
        this.isSprint = isSprint;
        this.hitEntity = hitEntity;
        this.entityId = entityId;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    public static void encode(PacketScepterRightClick msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.isSprint);
        buffer.writeBoolean(msg.hitEntity);
        buffer.writeInt(msg.entityId);
        buffer.writeDouble(msg.x);
        buffer.writeDouble(msg.y);
        buffer.writeDouble(msg.z);
    }

    public static PacketScepterRightClick decode(FriendlyByteBuf buffer) {
        return new PacketScepterRightClick(
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readInt(),
            new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
        );
    }

    public static void handle(PacketScepterRightClick msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof AbstractScepterItem item) {
                    net.minecraft.world.entity.Entity targetEntity = null;
                    if (msg.hitEntity) {
                        targetEntity = player.serverLevel().getEntity(msg.entityId);
                    }
                    
                    item.serverHandleRightClick(stack, player.serverLevel(), player, new Vec3(msg.x, msg.y, msg.z), targetEntity, msg.isSprint);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
