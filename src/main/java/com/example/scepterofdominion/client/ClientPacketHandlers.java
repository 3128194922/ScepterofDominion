package com.example.scepterofdominion.client;

import com.example.scepterofdominion.util.ScepterSquadData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleSyncTeam(CompoundTag data, String rootKey) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ScepterSquadData.applyClientSync(player, data, rootKey);
    }
}
