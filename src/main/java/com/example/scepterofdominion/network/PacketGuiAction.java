package com.example.scepterofdominion.network;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketGuiAction {
    public static final int ACTION_SET_FORMATION = 0;
    public static final int ACTION_SELECT_PET = 1;
    public static final int ACTION_REMOVE_PET = 2;
    public static final int ACTION_LEFT_CLICK_ENTITY = 3;
    public static final int ACTION_CONTAIN = 4;
    public static final int ACTION_RELEASE = 5;
    public static final int ACTION_LEFT_CLICK_ENTITY_SHIFT = 6;
    public static final int ACTION_PREV_SQUAD = 7;
    public static final int ACTION_NEXT_SQUAD = 8;
    public static final int ACTION_SET_TASK = 9;
    public static final int ACTION_TOGGLE_FORMATION = 10;
    public static final int ACTION_SET_MOUNT = 11;
    public static final int ACTION_UNSET_MOUNT = 12;

    private final int action;
    private final int valueInt;
    private final String valueStr;

    public PacketGuiAction(int action, int valueInt, String valueStr) {
        this.action = action;
        this.valueInt = valueInt;
        this.valueStr = valueStr;
    }

    public PacketGuiAction(FriendlyByteBuf buf) {
        this.action = buf.readInt();
        this.valueInt = buf.readInt();
        this.valueStr = buf.readUtf();
    }
    
    public static PacketGuiAction decode(FriendlyByteBuf buf) {
        return new PacketGuiAction(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(action);
        buf.writeInt(valueInt);
        if (valueStr != null) {
            buf.writeUtf(valueStr);
        } else {
            buf.writeUtf("");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof AbstractScepterItem item) {
                    switch (action) {
                        case ACTION_SET_FORMATION -> {
                            item.setFormation(stack, valueInt, player);
                            item.syncToClient(stack, player);
                        }
                        case ACTION_SET_TASK -> {
                            item.setSquadTask(stack, valueInt, player);
                            item.syncToClient(stack, player);
                        }
                        case ACTION_TOGGLE_FORMATION -> item.toggleFormationEnabled(stack, player);
                        case ACTION_SELECT_PET -> {
                            try {
                                UUID uuid = UUID.fromString(valueStr);
                                item.setFocus(stack, uuid, player);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        case ACTION_REMOVE_PET -> {
                            try {
                                UUID uuid = UUID.fromString(valueStr);
                                // Try to find the entity if possible to call onEntityRemoved
                                net.minecraft.world.entity.LivingEntity entity = null;
                                if (player.serverLevel().getEntity(uuid) instanceof net.minecraft.world.entity.LivingEntity living) {
                                    entity = living;
                                }
                                item.removeTeamMember(stack, uuid, player, entity);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        case ACTION_LEFT_CLICK_ENTITY -> {
                            try {
                                UUID uuid = UUID.fromString(valueStr);
                                if (player.serverLevel().getEntity(uuid) instanceof net.minecraft.world.entity.LivingEntity living) {
                                     item.handleLeftClickLogic(stack, player, living);
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        case ACTION_LEFT_CLICK_ENTITY_SHIFT -> {
                            String rk = item.getSquadRootKey();
                            com.example.scepterofdominion.util.ScepterSquadData.cycleSelectedSquad(player, 1, rk);
                            item.syncToClient(stack, player);
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.scepterofdominion.squad_switched", com.example.scepterofdominion.util.ScepterSquadData.getSelectedSquadIndex(player, rk) + 1).withStyle(net.minecraft.ChatFormatting.AQUA), true);
                        }
                        case ACTION_CONTAIN -> {
                            com.example.scepterofdominion.world.StorageDimension.containPets(player, stack);
                        }
                        case ACTION_RELEASE -> {
                            com.example.scepterofdominion.world.StorageDimension.releasePets(player, stack);
                        }
                        case ACTION_PREV_SQUAD -> {
                            String rk = item.getSquadRootKey();
                            com.example.scepterofdominion.util.ScepterSquadData.cycleSelectedSquad(player, -1, rk);
                            item.syncToClient(stack, player);
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.scepterofdominion.squad_switched", com.example.scepterofdominion.util.ScepterSquadData.getSelectedSquadIndex(player, rk) + 1).withStyle(net.minecraft.ChatFormatting.AQUA), true);
                        }
                        case ACTION_NEXT_SQUAD -> {
                            String rk = item.getSquadRootKey();
                            com.example.scepterofdominion.util.ScepterSquadData.cycleSelectedSquad(player, 1, rk);
                            item.syncToClient(stack, player);
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.scepterofdominion.squad_switched", com.example.scepterofdominion.util.ScepterSquadData.getSelectedSquadIndex(player, rk) + 1).withStyle(net.minecraft.ChatFormatting.AQUA), true);
                        }
                        case ACTION_SET_MOUNT -> {
                            try {
                                UUID uuid = UUID.fromString(valueStr);
                                item.setMount(stack, uuid, player);
                                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.scepterofdominion.mount_set").withStyle(net.minecraft.ChatFormatting.GOLD), true);
                            } catch (Exception e) {
                            }
                        }
                        case ACTION_UNSET_MOUNT -> {
                            item.setMount(stack, null, player);
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.scepterofdominion.mount_unset").withStyle(net.minecraft.ChatFormatting.GOLD), true);
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
