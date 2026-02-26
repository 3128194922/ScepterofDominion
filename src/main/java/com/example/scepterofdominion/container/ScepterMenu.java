package com.example.scepterofdominion.container;

import com.example.scepterofdominion.ScepterOfDominion;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ScepterMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final ItemStack scepterStack;

    public ScepterMenu(int id, Inventory inventory, FriendlyByteBuf extraData) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public ScepterMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ScepterOfDominion.SCEPTER_MENU.get(), id);
        this.access = access;
        this.scepterStack = inventory.player.getMainHandItem();

        // No player inventory slots needed for this specific GUI requirement
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // No quick move needed for this GUI
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem().getItem() instanceof ScepterOfDominionItem;
    }
    
    public ItemStack getScepterStack() {
        return scepterStack;
    }
}
