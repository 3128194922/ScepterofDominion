package com.example.scepterofdominion.client.gui;

import com.example.scepterofdominion.container.ScepterMenu;
import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.network.PacketGuiAction;
import com.example.scepterofdominion.network.PacketHandler;
import com.example.scepterofdominion.util.ScepterSquadData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScepterScreen extends AbstractContainerScreen<ScepterMenu> {
    private final List<SimpleButton> petButtons = new ArrayList<>();
    private final List<SimpleButton> removeButtons = new ArrayList<>();
    private SimpleButton guardTaskButton;
    private SimpleButton holdTaskButton;
    private SimpleButton followTaskButton;
    private SimpleButton formationToggleButton;
    private SimpleButton mountButton;
    private int selectedPetIndex = -1;

    public ScepterScreen(ScepterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 0;
        this.imageHeight = 0;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        petButtons.clear();
        removeButtons.clear();

        int maxMembers;
        {
            Player player = Minecraft.getInstance().player;
            ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
            maxMembers = (stack.getItem() instanceof AbstractScepterItem item)
                    ? ScepterSquadData.getCachedMaxMembers(player, item.getSquadRootKey())
                    : ScepterSquadData.getServerMaxMembers();
        }
        ScreenLayout layout = ScreenLayout.create(this.width, this.height, maxMembers);

        guardTaskButton = this.addRenderableWidget(new SimpleButton(layout.taskLeftX, layout.taskTopY, layout.taskButtonWidth, 20, Component.translatable("gui.scepterofdominion.task.guard"), press -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_SET_TASK, ScepterSquadData.TASK_GUARD, ""));
        }));
        holdTaskButton = this.addRenderableWidget(new SimpleButton(layout.taskRightX, layout.taskTopY, layout.taskButtonWidth, 20, Component.translatable("gui.scepterofdominion.task.hold"), press -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_SET_TASK, ScepterSquadData.TASK_HOLD, ""));
        }));
        followTaskButton = this.addRenderableWidget(new SimpleButton(layout.taskWideX, layout.taskSecondRowY, layout.taskWideWidth, 20, Component.translatable("gui.scepterofdominion.task.follow"), press -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_SET_TASK, ScepterSquadData.TASK_FOLLOW_PROTECT, ""));
        }));
        formationToggleButton = this.addRenderableWidget(new SimpleButton(layout.toggleX, layout.toggleY, layout.toggleWidth, 20, Component.empty(), press -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_TOGGLE_FORMATION, 0, ""));
        }));
        this.addRenderableWidget(new SimpleButton(layout.formationPrevX, layout.toggleY, 20, 20, Component.literal("<"), press -> cycleFormation(-1)));
        this.addRenderableWidget(new SimpleButton(layout.formationNextX, layout.toggleY, 20, 20, Component.literal(">"), press -> cycleFormation(1)));
        this.addRenderableWidget(new SimpleButton(layout.containX, layout.actionButtonY, layout.actionButtonWidth, 20, Component.translatable("gui.scepterofdominion.contain"), press -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_CONTAIN, 0, ""));
        }));
        this.addRenderableWidget(new SimpleButton(layout.releaseX, layout.actionButtonY, layout.actionButtonWidth, 20, Component.translatable("gui.scepterofdominion.release"), press -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_RELEASE, 0, ""));
        }));

        mountButton = this.addRenderableWidget(new SimpleButton(layout.mountButtonX, layout.mountButtonY, layout.mountButtonWidth, 20, Component.empty(), press -> {
            handleMountToggle();
        }));

        this.addRenderableWidget(new SimpleButton(layout.headerLeftArrowX, layout.headerButtonY, 20, 20, Component.literal("<"), press -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_PREV_SQUAD, 0, ""));
        }));
        this.addRenderableWidget(new SimpleButton(layout.headerRightArrowX, layout.headerButtonY, 20, 20, Component.literal(">"), press -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_NEXT_SQUAD, 0, ""));
        }));

        for (int i = 0; i < maxMembers; i++) {
            final int index = i;
            int col = i % 2;
            int row = i / 2;
            int x = layout.teamListX + col * (layout.petButtonWidth + layout.removeButtonWidth + layout.teamColumnGap);
            int y = layout.teamListY + row * layout.teamRowHeight;

            SimpleButton petButton = new SimpleButton(x, y, layout.petButtonWidth, 20, Component.literal("Empty"), press -> selectPet(index));
            SimpleButton removeButton = new SimpleButton(x + layout.petButtonWidth + 4, y, layout.removeButtonWidth, 20, Component.literal("X"), press -> removePet(index));
            this.addRenderableWidget(petButton);
            this.addRenderableWidget(removeButton);
            petButtons.add(petButton);
            removeButtons.add(removeButton);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        Player player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (player != null && stack.getItem() instanceof AbstractScepterItem item) {
            List<CompoundTag> team = item.getTeamInfo(stack, player);
            syncSelection(item, stack, player, team);
            updateButtons(item, stack, player, team);
            drawPanels(guiGraphics, item, stack, player, team, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void selectPet(int index) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof AbstractScepterItem item) {
            List<CompoundTag> team = item.getTeamInfo(stack, player);
            if (index < team.size()) {
                UUID uuid = team.get(index).getUUID("UUID");
                PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_SELECT_PET, 0, uuid.toString()));
                this.selectedPetIndex = index;
            }
        }
    }

    private void removePet(int index) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof AbstractScepterItem item) {
            List<CompoundTag> team = item.getTeamInfo(stack, player);
            if (index < team.size()) {
                UUID uuid = team.get(index).getUUID("UUID");
                PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_REMOVE_PET, 0, uuid.toString()));
                if (this.selectedPetIndex == index) {
                    this.selectedPetIndex = -1;
                }
            }
        }
    }

    private void syncSelection(AbstractScepterItem item, ItemStack stack, Player player, List<CompoundTag> team) {
        UUID focus = item.getFocus(stack, player);
        if (focus != null) {
            for (int i = 0; i < team.size(); i++) {
                if (team.get(i).hasUUID("UUID") && team.get(i).getUUID("UUID").equals(focus)) {
                    this.selectedPetIndex = i;
                    return;
                }
            }
        }

        if (!team.isEmpty() && (this.selectedPetIndex < 0 || this.selectedPetIndex >= team.size())) {
            this.selectedPetIndex = 0;
        } else if (team.isEmpty()) {
            this.selectedPetIndex = -1;
        }
    }

    private void updateButtons(AbstractScepterItem item, ItemStack stack, Player player, List<CompoundTag> team) {
        int squadTask = item.getSquadTask(stack, player);
        boolean formationEnabled = item.isFormationEnabled(stack, player);
        guardTaskButton.setSelected(squadTask == ScepterSquadData.TASK_GUARD);
        holdTaskButton.setSelected(squadTask == ScepterSquadData.TASK_HOLD);
        followTaskButton.setSelected(squadTask == ScepterSquadData.TASK_FOLLOW_PROTECT);
        formationToggleButton.setSelected(formationEnabled);
        formationToggleButton.setMessage(Component.translatable(formationEnabled ? "gui.scepterofdominion.formation_on" : "gui.scepterofdominion.formation_off"));

        UUID mountUUID = item.getMount(stack, player);
        CompoundTag selectedMember = getSelectedMember(team);
        boolean isMountSet = mountUUID != null;
        boolean isSelectedMount = selectedMember != null && mountUUID != null && mountUUID.equals(selectedMember.getUUID("UUID"));
        if (isMountSet) {
            mountButton.setMessage(Component.translatable("gui.scepterofdominion.mount_unset"));
            mountButton.setSelected(isSelectedMount);
        } else {
            mountButton.setMessage(Component.translatable("gui.scepterofdominion.mount_set"));
            mountButton.setSelected(false);
        }
        mountButton.visible = true;

        for (int i = 0; i < petButtons.size(); i++) {
            SimpleButton petButton = petButtons.get(i);
            SimpleButton removeButton = removeButtons.get(i);
            if (i < team.size()) {
                petButton.visible = true;
                removeButton.visible = true;
                petButton.setMessage(Component.literal(team.get(i).getString("Name")));
                petButton.setSelected(i == this.selectedPetIndex);
            } else {
                petButton.visible = false;
                removeButton.visible = false;
            }
        }
    }

    private void handleMountToggle() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof AbstractScepterItem item) {
            UUID currentMount = item.getMount(stack, player);
            if (currentMount != null) {
                PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_UNSET_MOUNT, 0, ""));
            } else if (this.selectedPetIndex >= 0) {
                List<CompoundTag> team = item.getTeamInfo(stack, player);
                if (this.selectedPetIndex < team.size()) {
                    String uuidStr = team.get(this.selectedPetIndex).getUUID("UUID").toString();
                    PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_SET_MOUNT, 0, uuidStr));
                }
            }
        }
    }

    private void drawPanels(GuiGraphics guiGraphics, AbstractScepterItem item, ItemStack stack, Player player, List<CompoundTag> team, int mouseX, int mouseY) {
        String rootKey = item.getSquadRootKey();
        ScreenLayout layout = ScreenLayout.create(this.width, this.height, ScepterSquadData.getCachedMaxMembers(player, rootKey));
        int titleColor = 0xFFFFFFFF;

        guiGraphics.fill(layout.canvasX - 6, layout.canvasY - 6, layout.canvasX + layout.canvasWidth + 6, layout.canvasY + layout.canvasHeight + 6, 0x50000000);
        drawPanel(guiGraphics, layout.headerX, layout.headerY, layout.headerWidth, layout.headerHeight);
        drawPanel(guiGraphics, layout.leftPanelX, layout.mainPanelY, layout.leftPanelWidth, layout.mainPanelHeight);
        drawPanel(guiGraphics, layout.rightPanelX, layout.mainPanelY, layout.rightPanelWidth, layout.mainPanelHeight);
        drawPanel(guiGraphics, layout.bottomLeftX, layout.bottomPanelY, layout.bottomLeftWidth, layout.bottomPanelHeight);
        drawPanel(guiGraphics, layout.bottomRightX, layout.bottomPanelY, layout.bottomRightWidth, layout.bottomPanelHeight);

        int squadIndex = ScepterSquadData.getSelectedSquadIndex(player, rootKey) + 1;
        int squadCount = ScepterSquadData.getCachedMaxSquads(player, rootKey);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.squad_title", squadIndex, squadCount), layout.headerCenterX, layout.headerY + 8, titleColor);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.pet_profile"), layout.leftPanelX + layout.leftPanelWidth / 2, layout.mainPanelY + 7, titleColor);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.pet_stats"), layout.rightPanelX + layout.rightPanelWidth / 2, layout.mainPanelY + 7, titleColor);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.squad_task"), layout.bottomLeftX + layout.bottomLeftWidth / 2, layout.bottomPanelY + 7, titleColor);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.team_members"), layout.bottomRightX + layout.bottomRightWidth / 2, layout.bottomPanelY + 7, titleColor);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.formation"), layout.bottomLeftX + layout.bottomLeftWidth / 2, layout.formationSectionTitleY, 0xFFB8C6FF);
        Component formationLabel = Component.translatable("gui.scepterofdominion.formation_current", getFormationName(item.getFormation(stack, player)));
        guiGraphics.drawCenteredString(this.font, fitText(formationLabel, layout.bottomLeftWidth - 12), layout.bottomLeftX + layout.bottomLeftWidth / 2, layout.formationLabelY, 0xFFD0E0FF);

        CompoundTag selectedMember = getSelectedMember(team);
        LivingEntity selectedEntity = getSelectedEntity(player, selectedMember);
        Component name = selectedMember != null ? Component.literal(selectedMember.getString("Name")) : Component.translatable("gui.scepterofdominion.no_pet_selected");
        renderMarqueeText(guiGraphics, name, layout.nameClipX, layout.nameClipY, layout.nameClipWidth, 0xFFFFE082, true);
        guiGraphics.fill(layout.previewBoxX, layout.previewBoxY, layout.previewBoxX + layout.previewBoxWidth, layout.previewBoxY + layout.previewBoxHeight, 0x30000000);
        guiGraphics.fill(layout.previewBoxX, layout.previewBoxY, layout.previewBoxX + layout.previewBoxWidth, layout.previewBoxY + 1, 0x50FFFFFF);
        guiGraphics.fill(layout.previewBoxX, layout.previewBoxY, layout.previewBoxX + 1, layout.previewBoxY + layout.previewBoxHeight, 0x30FFFFFF);

        if (selectedEntity != null) {
            int previewScale = getPreviewScale(selectedEntity, layout.previewBoxWidth, layout.previewBoxHeight);
            InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, layout.previewCenterX, layout.previewBottomY, previewScale, (float) layout.previewCenterX - mouseX, (float) (layout.previewBottomY - layout.previewMouseAnchorOffsetY) - mouseY, selectedEntity);
        } else {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.entity_unavailable"), layout.previewCenterX, layout.previewBoxY + layout.previewBoxHeight / 2 - 4, 0xFFB0BEC5);
        }

        renderStats(guiGraphics, layout.rightPanelX + 14, layout.mainPanelY + 30, layout.rightPanelWidth - 28, selectedEntity);
    }

    private void renderStats(GuiGraphics guiGraphics, int x, int y, int width, @Nullable LivingEntity entity) {
        float currentHealth = entity != null ? entity.getHealth() : 0.0F;
        float maxHealth = entity != null ? entity.getMaxHealth() : 0.0F;
        renderHealthBar(guiGraphics, x, y, width, currentHealth, maxHealth, 0xFFE57373);
        renderValueRow(guiGraphics, x, y + 34, width, Component.translatable("gui.scepterofdominion.stat.speed"), entity != null ? entity.getAttributeValue(Attributes.MOVEMENT_SPEED) : 0.0, 0xFF64B5F6);
        renderValueRow(guiGraphics, x, y + 58, width, Component.translatable("gui.scepterofdominion.stat.armor"), entity != null ? entity.getAttributeValue(Attributes.ARMOR) : 0.0, 0xFFA1887F);
        renderValueRow(guiGraphics, x, y + 82, width, Component.translatable("gui.scepterofdominion.stat.toughness"), entity != null ? entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0.0, 0xFF9575CD);
    }

    private void renderHealthBar(GuiGraphics guiGraphics, int x, int y, int width, float currentHealth, float maxHealth, int fillColor) {
        int barY = y + 14;
        int barHeight = 12;
        float safeMaxHealth = Math.max(0.0001F, maxHealth);
        int barWidth = Math.max(0, Math.min(width, Math.round((currentHealth / safeMaxHealth) * width)));
        String valueText = String.format("%.1f / %.1f", currentHealth, maxHealth);
        guiGraphics.drawString(this.font, Component.translatable("gui.scepterofdominion.stat.health"), x, y, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, valueText, x + width - this.font.width(valueText), y, 0xFFE0E0E0);
        guiGraphics.fill(x, barY, x + width, barY + barHeight, 0xFF202020);
        guiGraphics.fill(x, barY, x + barWidth, barY + barHeight, fillColor);
        guiGraphics.fill(x, barY, x + width, barY + 1, 0x60FFFFFF);
    }

    private void renderValueRow(GuiGraphics guiGraphics, int x, int y, int width, Component label, double value, int accentColor) {
        String valueText = String.format("%.2f", value);
        guiGraphics.fill(x, y - 2, x + width, y + 12, 0x40101010);
        guiGraphics.fill(x, y - 2, x + 3, y + 12, accentColor);
        guiGraphics.drawString(this.font, label, x + 8, y + 1, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, valueText, x + width - this.font.width(valueText) - 4, y + 1, 0xFFE0E0E0);
    }

    private int getPreviewScale(LivingEntity entity, int previewWidth, int previewHeight) {
        double safeWidth = Math.max(0.35D, entity.getBbWidth());
        double safeHeight = Math.max(0.8D, entity.getBbHeight());
        int widthScale = (int) Math.floor(previewWidth / (safeWidth * 2.6D));
        int heightScale = (int) Math.floor(previewHeight / (safeHeight * 1.25D));
        return Math.max(18, Math.min(36, Math.min(widthScale, heightScale)));
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0x86000000);
        guiGraphics.fill(x, y, x + width, y + 20, 0xAA202020);
        guiGraphics.fill(x, y, x + width, y + 1, 0x60FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + height, 0x40FFFFFF);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0x30000000);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0x30000000);
    }

    private Component fitText(Component text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        String raw = text.getString();
        while (!raw.isEmpty() && this.font.width(raw + "...") > maxWidth) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return Component.literal(raw + "...");
    }

    private void renderMarqueeText(GuiGraphics guiGraphics, Component text, int clipX, int clipY, int clipWidth, int color, boolean centeredWhenFit) {
        var sequence = text.getVisualOrderText();
        int textWidth = this.font.width(sequence);
        int textY = clipY + 1;

        if (textWidth <= clipWidth) {
            if (centeredWhenFit) {
                guiGraphics.drawCenteredString(this.font, text, clipX + clipWidth / 2, textY, color);
            } else {
                guiGraphics.drawString(this.font, text, clipX, textY, color);
            }
            return;
        }

        int gap = 16;
        int loopWidth = textWidth + gap;
        int scroll = (int) ((System.currentTimeMillis() / 55L) % loopWidth);
        int drawX = clipX - scroll;

        guiGraphics.enableScissor(clipX, clipY, clipX + clipWidth, clipY + this.font.lineHeight + 2);
        guiGraphics.drawString(this.font, text, drawX, textY, color);
        guiGraphics.drawString(this.font, text, drawX + loopWidth, textY, color);
        guiGraphics.disableScissor();
    }

    @Nullable
    private CompoundTag getSelectedMember(List<CompoundTag> team) {
        if (this.selectedPetIndex >= 0 && this.selectedPetIndex < team.size()) {
            return team.get(this.selectedPetIndex);
        }
        return null;
    }

    @Nullable
    private LivingEntity getSelectedEntity(Player player, @Nullable CompoundTag member) {
        if (member == null || !member.hasUUID("UUID")) {
            return null;
        }

        UUID uuid = member.getUUID("UUID");
        if (Minecraft.getInstance().level == null) {
            return null;
        }

        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid) && entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private void cycleFormation(int delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof AbstractScepterItem item) {
            int current = item.getFormation(stack, player);
            int next = (current + delta) % 6;
            if (next < 0) {
                next += 6;
            }
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_SET_FORMATION, next, ""));
        }
    }

    private Component getFormationName(int formationId) {
        return switch (formationId) {
            case 1 -> Component.translatable("message.scepterofdominion.formation.double_line");
            case 2 -> Component.translatable("message.scepterofdominion.formation.diamond");
            case 3 -> Component.translatable("message.scepterofdominion.formation.echelon");
            case 4 -> Component.translatable("message.scepterofdominion.formation.line_abreast");
            case 5 -> Component.translatable("message.scepterofdominion.formation.cluster");
            default -> Component.translatable("message.scepterofdominion.formation.line_ahead");
        };
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    class SimpleButton extends Button {
        private boolean selected = false;

        public SimpleButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int bgColor = 0x80000000;
            if (isHoveredOrFocused()) {
                bgColor = 0xA0000000;
            }
            if (selected) {
                bgColor = 0xC0404040;
                guiGraphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() + height + 2, 0xFFFFFF00);
            }
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            renderMarqueeText(guiGraphics, getMessage(), getX() + 4, getY() + 5, width - 8, selected ? 0xFFFFFF00 : 0xFFE0E0E0, true);
        }
    }

    private static final class ScreenLayout {
        private final int canvasX;
        private final int canvasY;
        private final int canvasWidth;
        private final int canvasHeight;
        private final int headerX;
        private final int headerY;
        private final int headerWidth;
        private final int headerHeight;
        private final int headerCenterX;
        private final int headerButtonY;
        private final int headerLeftArrowX;
        private final int headerRightArrowX;
        private final int mainPanelY;
        private final int mainPanelHeight;
        private final int leftPanelX;
        private final int leftPanelWidth;
        private final int previewBoxX;
        private final int previewBoxY;
        private final int previewBoxWidth;
        private final int previewBoxHeight;
        private final int previewCenterX;
        private final int previewBottomY;
        private final int previewMouseAnchorOffsetY;
        private final int nameClipX;
        private final int nameClipY;
        private final int nameClipWidth;
        private final int rightPanelX;
        private final int rightPanelWidth;
        private final int bottomPanelY;
        private final int bottomPanelHeight;
        private final int bottomLeftX;
        private final int bottomLeftWidth;
        private final int bottomRightX;
        private final int bottomRightWidth;
        private final int taskTopY;
        private final int taskSecondRowY;
        private final int taskLeftX;
        private final int taskRightX;
        private final int taskButtonWidth;
        private final int taskWideX;
        private final int taskWideWidth;
        private final int toggleX;
        private final int toggleY;
        private final int toggleWidth;
        private final int formationPrevX;
        private final int formationNextX;
        private final int formationSectionTitleY;
        private final int formationLabelY;
        private final int actionButtonY;
        private final int actionButtonWidth;
        private final int containX;
        private final int releaseX;
        private final int mountButtonX;
        private final int mountButtonY;
        private final int mountButtonWidth;
        private final int teamListX;
        private final int teamListY;
        private final int petButtonWidth;
        private final int removeButtonWidth;
        private final int teamColumnGap;
        private final int teamRowHeight;

        private ScreenLayout(int screenWidth, int screenHeight, int maxMembers) {
            this.canvasWidth = 404;
            this.canvasHeight = 286;
            this.canvasX = (screenWidth - this.canvasWidth) / 2;
            this.canvasY = Math.max(8, (screenHeight - this.canvasHeight) / 2);

            this.headerX = this.canvasX;
            this.headerY = this.canvasY;
            this.headerWidth = this.canvasWidth;
            this.headerHeight = 28;
            this.headerCenterX = this.headerX + this.headerWidth / 2;
            this.headerButtonY = this.headerY + 4;
            this.headerLeftArrowX = this.headerCenterX - 84;
            this.headerRightArrowX = this.headerCenterX + 64;

            this.mainPanelY = this.headerY + this.headerHeight + 8;
            this.mainPanelHeight = 126;
            this.leftPanelX = this.canvasX;
            this.leftPanelWidth = 190;
            this.previewBoxX = this.leftPanelX + 14;
            this.previewBoxY = this.mainPanelY + 44;
            this.previewBoxWidth = this.leftPanelWidth - 28;
            this.previewBoxHeight = this.mainPanelHeight - 58;
            this.previewCenterX = this.previewBoxX + this.previewBoxWidth / 2;
            this.previewBottomY = this.previewBoxY + this.previewBoxHeight - 8;
            this.previewMouseAnchorOffsetY = 26;
            this.nameClipX = this.leftPanelX + 12;
            this.nameClipY = this.mainPanelY + 29;
            this.nameClipWidth = this.leftPanelWidth - 24;
            this.rightPanelX = this.leftPanelX + this.leftPanelWidth + 10;
            this.rightPanelWidth = this.canvasWidth - this.leftPanelWidth - 10;

            this.bottomPanelY = this.mainPanelY + this.mainPanelHeight + 10;
            this.bottomPanelHeight = this.canvasHeight - (this.bottomPanelY - this.canvasY);
            this.bottomLeftX = this.canvasX;
            this.bottomLeftWidth = 184;
            this.bottomRightX = this.bottomLeftX + this.bottomLeftWidth + 10;
            this.bottomRightWidth = this.canvasWidth - this.bottomLeftWidth - 10;

            this.taskButtonWidth = 78;
            this.taskTopY = this.bottomPanelY + 24;
            this.taskSecondRowY = this.taskTopY + 24;
            this.taskLeftX = this.bottomLeftX + 10;
            this.taskRightX = this.bottomLeftX + this.bottomLeftWidth - this.taskButtonWidth - 10;
            this.taskWideX = this.bottomLeftX + 10;
            this.taskWideWidth = this.bottomLeftWidth - 20;
            this.formationSectionTitleY = this.taskSecondRowY + 24;
            this.toggleY = this.formationSectionTitleY + 10;
            this.toggleWidth = 74;
            this.toggleX = this.bottomLeftX + (this.bottomLeftWidth - this.toggleWidth) / 2;
            this.formationPrevX = this.toggleX - 24;
            this.formationNextX = this.toggleX + this.toggleWidth + 4;
            this.formationLabelY = this.toggleY + 24;
            this.actionButtonWidth = 62;
            this.actionButtonY = this.bottomPanelY + this.bottomPanelHeight - 24;
            this.containX = this.bottomRightX + 10;
            this.releaseX = this.bottomRightX + this.bottomRightWidth - this.actionButtonWidth - 10;
            this.mountButtonWidth = 54;
            this.mountButtonX = this.containX + this.actionButtonWidth + 6;
            this.mountButtonY = this.actionButtonY;

            this.petButtonWidth = 80;
            this.removeButtonWidth = 16;
            this.teamColumnGap = 4;
            this.teamRowHeight = 22;
            int teamGridWidth = (2 * this.petButtonWidth) + (2 * this.removeButtonWidth) + this.teamColumnGap + 4;
            this.teamListX = this.bottomRightX + Math.max(4, (this.bottomRightWidth - teamGridWidth) / 2);
            this.teamListY = this.bottomPanelY + 28;
        }

        private static ScreenLayout create(int screenWidth, int screenHeight, int maxMembers) {
            return new ScreenLayout(screenWidth, screenHeight, maxMembers);
        }
    }
}
