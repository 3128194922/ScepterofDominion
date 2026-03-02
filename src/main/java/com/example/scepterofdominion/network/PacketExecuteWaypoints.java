package com.example.scepterofdominion.network;

import com.example.scepterofdominion.item.AbstractScepterItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketExecuteWaypoints {

    public PacketExecuteWaypoints() {
    }

    public static void encode(PacketExecuteWaypoints msg, FriendlyByteBuf buffer) {
    }

    public static PacketExecuteWaypoints decode(FriendlyByteBuf buffer) {
        return new PacketExecuteWaypoints();
    }

    public static void handle(PacketExecuteWaypoints msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof AbstractScepterItem item) {
                    item.executeWaypoints(stack, player, player.level());
                }
            }
        });
        context.setPacketHandled(true);
    }
}
