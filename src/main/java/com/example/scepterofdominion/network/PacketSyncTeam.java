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
    private final String rootKey;

    public PacketSyncTeam(CompoundTag data, String rootKey) {
        this.data = data;
        this.rootKey = rootKey;
    }

    public PacketSyncTeam(FriendlyByteBuf buf) {
        this.data = buf.readNbt();
        this.rootKey = buf.readUtf();
    }

    public static PacketSyncTeam decode(FriendlyByteBuf buf) {
        return new PacketSyncTeam(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
        buf.writeUtf(rootKey);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleSyncTeam(data, rootKey));
        });
        ctx.get().setPacketHandled(true);
    }
}
