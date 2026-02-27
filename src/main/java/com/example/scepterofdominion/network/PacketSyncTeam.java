package com.example.scepterofdominion.network;

import com.example.scepterofdominion.item.ScepterOfDominionItem;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
            // Client side handling
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof ScepterOfDominionItem) {
                    // Update stack NBT with synced data
                    CompoundTag tag = stack.getOrCreateTag();
                    if (data.contains("Team")) {
                        tag.put("Team", data.getList("Team", 10)); // 10 = TAG_COMPOUND
                    }
                    if (data.contains("Focus")) {
                        tag.putUUID("Focus", data.getUUID("Focus"));
                    }
                    if (data.contains("Formation")) {
                        tag.putInt("Formation", data.getInt("Formation"));
                    }
                    if (data.contains("CommandTarget")) {
                        tag.put("CommandTarget", data.getCompound("CommandTarget"));
                    }
                    if (data.hasUUID("AttackTarget")) {
                        tag.putUUID("AttackTarget", data.getUUID("AttackTarget"));
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
