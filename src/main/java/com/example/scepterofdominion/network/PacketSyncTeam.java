package com.example.scepterofdominion.network;

import com.example.scepterofdominion.client.ClientPacketHandlers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncTeam {

    private final CompoundTag data;

    public PacketSyncTeam(CompoundTag data) {
        this.data = data;
    }

    public PacketSyncTeam(FriendlyByteBuf buf) {
        this.data = buf.readNbt();
    }
    
    public static PacketSyncTeam decode(FriendlyByteBuf buf) {
        return new PacketSyncTeam(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleSyncTeam(data));
        });
        ctx.get().setPacketHandled(true);
    }
}
