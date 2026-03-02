package com.example.scepterofdominion.network;

import com.example.scepterofdominion.container.ScepterContainerProvider;
import com.example.scepterofdominion.item.AbstractScepterItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class PacketOpenScepterGui {

    public PacketOpenScepterGui() {}

    public PacketOpenScepterGui(FriendlyByteBuf buf) {}

    public static PacketOpenScepterGui decode(FriendlyByteBuf buf) {
        return new PacketOpenScepterGui(buf);
    }

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof AbstractScepterItem) {
                    NetworkHooks.openScreen(player, new ScepterContainerProvider(stack), buffer -> {
                        buffer.writeItem(stack);
                    });
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
