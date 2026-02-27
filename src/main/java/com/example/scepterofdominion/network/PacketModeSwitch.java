package com.example.scepterofdominion.network;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketModeSwitch {

    public PacketModeSwitch() {}

    public PacketModeSwitch(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static PacketModeSwitch decode(FriendlyByteBuf buf) {
        return new PacketModeSwitch(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof AbstractScepterItem item) {
                    item.cycleMode(stack, player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
