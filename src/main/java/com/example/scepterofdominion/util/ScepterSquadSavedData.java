package com.example.scepterofdominion.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScepterSquadSavedData extends SavedData {
    private static final String DATA_NAME = "scepterofdominion_squad_data";

    private final Map<UUID, CompoundTag> playerRoots = new HashMap<>();

    public static ScepterSquadSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ScepterSquadSavedData::load,
                ScepterSquadSavedData::create,
                DATA_NAME
        );
    }

    public CompoundTag getPlayerRoot(UUID playerUUID) {
        return playerRoots.computeIfAbsent(playerUUID, k -> new CompoundTag());
    }

    public static ScepterSquadSavedData create() {
        return new ScepterSquadSavedData();
    }

    public static ScepterSquadSavedData load(CompoundTag tag) {
        ScepterSquadSavedData data = new ScepterSquadSavedData();
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                data.playerRoots.put(uuid, players.getCompound(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, CompoundTag> entry : playerRoots.entrySet()) {
            players.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put("Players", players);
        return tag;
    }
}
