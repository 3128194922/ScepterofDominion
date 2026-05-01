package com.example.scepterofdominion.client;

import com.example.scepterofdominion.item.AbstractScepterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleSyncTeam(CompoundTag data) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AbstractScepterItem)) {
            return;
        }

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
