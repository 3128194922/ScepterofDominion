package com.example.scepterofdominion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class ClientPlayerAccessor {
    private ClientPlayerAccessor() {
    }

    @Nullable
    public static Player getPlayer() {
        return Minecraft.getInstance().player;
    }
}
