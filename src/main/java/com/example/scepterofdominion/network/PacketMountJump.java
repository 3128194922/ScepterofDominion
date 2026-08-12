package com.example.scepterofdominion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

public class PacketMountJump {
    public PacketMountJump() {
    }

    public PacketMountJump(FriendlyByteBuf buf) {
    }

    public static PacketMountJump decode(FriendlyByteBuf buf) {
        return new PacketMountJump(buf);
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity vehicle = player.getVehicle();
            if (vehicle instanceof LivingEntity living) {
                if (living.getPersistentData().contains("ScepterIsMount") && living.onGround()) {
                    double jumpPower = 0.42D;
                    living.setDeltaMovement(living.getDeltaMovement().x, jumpPower, living.getDeltaMovement().z);
                    living.hasImpulse = true;
                    living.getPersistentData().putBoolean("ScepterMountJumped", true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
