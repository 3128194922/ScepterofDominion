package com.example.scepterofdominion.network;

import com.example.scepterofdominion.ScepterOfDominion;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ScepterOfDominion.MODID + ":" + "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, PacketModeSwitch.class, PacketModeSwitch::encode, PacketModeSwitch::decode, PacketModeSwitch::handle);
        INSTANCE.registerMessage(id++, PacketGuiAction.class, PacketGuiAction::encode, PacketGuiAction::decode, PacketGuiAction::handle);
        INSTANCE.registerMessage(id++, PacketSyncTeam.class, PacketSyncTeam::encode, PacketSyncTeam::decode, PacketSyncTeam::handle);
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }
    
    public static void sendToPlayer(Object msg, net.minecraft.server.level.ServerPlayer player) {
        INSTANCE.sendTo(msg, player.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }
}
