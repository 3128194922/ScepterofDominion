package com.example.scepterofdominion.client.gui;

import com.example.scepterofdominion.container.ScepterMenu;
import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import com.example.scepterofdominion.network.PacketGuiAction;
import com.example.scepterofdominion.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScepterScreen extends AbstractContainerScreen<ScepterMenu> {

    private final List<SimpleButton> petButtons = new ArrayList<>();
    private final List<SimpleButton> removeButtons = new ArrayList<>();
    private final List<FormationButton> formationButtons = new ArrayList<>();
    private int selectedPetIndex = -1; // Track selected pet for mapping

    public ScepterScreen(ScepterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 0; // No background image
        this.imageHeight = 0;
    }

    @Override
    protected void init() {
        // Clear children
        this.clearWidgets();
        petButtons.clear();
        removeButtons.clear();
        formationButtons.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // --- Formation Selection (Grid Layout) ---
        // Arrange 6 formations in a 2x3 grid on the left side
        int numFormations = 6;
        int startX = centerX - 160;
        int startY = centerY - 50; // Adjusted for title bar
        int gapX = 50;
        int gapY = 50;

        for (int i = 0; i < numFormations; i++) {
            final int formationId = i;
            int col = i % 3;
            int row = i / 3;
            int x = startX + col * gapX;
            int y = startY + row * gapY;

            // Button with no text, focused on icon
            FormationButton btn = new FormationButton(x, y, 40, 40, Component.empty(), button -> {
                PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_SET_FORMATION, formationId, ""));
            }, formationId);
            
            this.addRenderableWidget(btn);
            formationButtons.add(btn);
        }

        // --- Contain / Release Buttons ---
        int actionBtnY = startY + 2 * gapY + 10;
        
        SimpleButton containBtn = new SimpleButton(startX, actionBtnY, 65, 20, Component.translatable("gui.scepterofdominion.contain"), button -> {
            PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_CONTAIN, 0, ""));
        });
        this.addRenderableWidget(containBtn);
        
        SimpleButton releaseBtn = new SimpleButton(startX + 75, actionBtnY, 65, 20, Component.translatable("gui.scepterofdominion.release"), button -> {
             PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_RELEASE, 0, ""));
        });
        this.addRenderableWidget(releaseBtn);

        // --- Pet List (Right Side) ---
        // Vertical list
        int listLeft = centerX + 40;
        int listTop = centerY - 50; // Adjusted for title bar

        for (int i = 0; i < 6; i++) {
            final int index = i;
            // Select Button (Name)
            SimpleButton petBtn = new SimpleButton(listLeft, listTop + i * 24, 80, 20, Component.literal("Empty"), button -> {
                ItemStack stack = Minecraft.getInstance().player.getMainHandItem();
                if (stack.getItem() instanceof AbstractScepterItem item) {
                     List<CompoundTag> team = item.getTeamInfo(stack);
                     if (index < team.size()) {
                         UUID uuid = team.get(index).getUUID("UUID");
                         PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_SELECT_PET, 0, uuid.toString()));
                         this.selectedPetIndex = index; // Update local selection for preview
                     }
                }
            });
            this.addRenderableWidget(petBtn);
            petButtons.add(petBtn);
            
            // Remove Button (X)
            SimpleButton removeBtn = new SimpleButton(listLeft + 85, listTop + i * 24, 20, 20, Component.literal("X"), button -> {
                ItemStack stack = Minecraft.getInstance().player.getMainHandItem();
                if (stack.getItem() instanceof AbstractScepterItem item) {
                     List<CompoundTag> team = item.getTeamInfo(stack);
                     if (index < team.size()) {
                         UUID uuid = team.get(index).getUUID("UUID");
                         PacketHandler.sendToServer(new PacketGuiAction(PacketGuiAction.ACTION_REMOVE_PET, 0, uuid.toString()));
                         if (this.selectedPetIndex == index) this.selectedPetIndex = -1; // Deselect if removed
                     }
                }
            });
            this.addRenderableWidget(removeBtn);
            removeButtons.add(removeBtn);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Darken background
        this.renderBackground(guiGraphics);

        ItemStack stack = Minecraft.getInstance().player.getMainHandItem();
        if (stack.getItem() instanceof AbstractScepterItem item) {
            // Update Formation Buttons Selection State
            int currentFormation = stack.getOrCreateTag().getInt("Formation");
            for (FormationButton btn : formationButtons) {
                btn.setSelected(btn.formationId == currentFormation);
            }

            // Update Pet List Visibility and Text
            List<CompoundTag> team = item.getTeamInfo(stack);
            
            // Sync selectedPetIndex with server Focus if not manually set in this session
            UUID focus = item.getFocus(stack);
            if (focus != null) {
                for(int i=0; i<team.size(); i++) {
                    if (team.get(i).getUUID("UUID").equals(focus)) {
                        this.selectedPetIndex = i;
                        break;
                    }
                }
            }

            for (int i = 0; i < 6; i++) {
                if (i < team.size()) {
                    petButtons.get(i).visible = true;
                    petButtons.get(i).setMessage(Component.literal(team.get(i).getString("Name")));
                    // Highlight selected pet button
                    petButtons.get(i).setSelected(i == this.selectedPetIndex);
                    
                    removeButtons.get(i).visible = true;
                } else {
                    petButtons.get(i).visible = false;
                    removeButtons.get(i).visible = false;
                }
            }
        }
        
        // --- Draw Title Bars ---
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // Formation Title Bar
        int formTitleX = centerX - 160;
        int formTitleY = centerY - 80;
        int formTitleW = 140; // 3 cols * 50 - 10 gap
        int formTitleH = 20;
        guiGraphics.fill(formTitleX, formTitleY, formTitleX + formTitleW, formTitleY + formTitleH, 0x80000000);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.formation"), formTitleX + formTitleW / 2, formTitleY + 6, 0xFFFFFF);

        // Team Title Bar
        int teamTitleX = centerX + 40;
        int teamTitleY = centerY - 80;
        int teamTitleW = 105; // 80 + 5 + 20
        int teamTitleH = 20;
        guiGraphics.fill(teamTitleX, teamTitleY, teamTitleX + teamTitleW, teamTitleY + teamTitleH, 0x80000000);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.scepterofdominion.team_members"), teamTitleX + teamTitleW / 2, teamTitleY + 6, 0xFFFFFF);


        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw Formation Previews (Icons) on buttons
        for (FormationButton btn : formationButtons) {
             if (btn.visible) {
                 // Render icon centered in the button (40x40)
                 renderFormationIcon(guiGraphics, btn.getX() + 20, btn.getY() + 20, btn.formationId);
             }
        }
        
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    
    private void renderFormationIcon(GuiGraphics guiGraphics, int x, int y, int formationId) {
        // Draw dots representing the formation
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        
        int color = 0xFF00FF00; // Green dots
        int centerColor = 0xFFFFD700; // Gold center
        int selectedColor = 0xFFFF0000; // Red for selected pet
        
        // Calculate center of the formation to center it in the button
        float minX = 0, maxX = 0, minY = 0, maxY = 0;
        List<Vec2> positions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Vec2 pos = getFormationOffset(formationId, i);
            positions.add(pos);
            if (i == 0) {
                 minX = pos.x; maxX = pos.x;
                 minY = pos.y; maxY = pos.y;
            } else {
                if (pos.x < minX) minX = pos.x;
                if (pos.x > maxX) maxX = pos.x;
                if (pos.y < minY) minY = pos.y;
                if (pos.y > maxY) maxY = pos.y;
            }
        }
        
        float centerX = (minX + maxX) / 2;
        float centerY = (minY + maxY) / 2;
        int scale = 6; // Adjusted scale to fit 40x40
        
        for (int i = 0; i < 6; i++) {
             Vec2 pos = positions.get(i);
             // Offset by center and scale
             int dx = (int)((pos.x - centerX) * scale);
             int dy = (int)((pos.y - centerY) * scale);
             
             // Determine dot color
             int dotColor = color;
             if (i == this.selectedPetIndex) {
                 dotColor = selectedColor; // Selected pet
             } else if (i == 0) {
                 dotColor = centerColor; // Leader (if not selected)
             }
             
             // Center the dot (4x4) at the position
             guiGraphics.fill(dx - 2, dy - 2, dx + 2, dy + 2, dotColor);
        }
        
        guiGraphics.pose().popPose();
    }
    
    private Vec2 getFormationOffset(int formation, int index) {
        // Reuse logic roughly, match ScepterOfDominionItem.getFormationPos
        double x = 0;
        double z = 0; // z maps to y on screen
        double spacing = 1.0;
        
        switch (formation) {
            case 0: // Single Line (Line Ahead)
                z = index * spacing;
                break;
                
            case 1: // Double Line
                x = (index % 2 == 0 ? -1 : 1) * spacing;
                z = (index / 2) * spacing;
                break;
                
            case 2: // Diamond (Standard Shape)
                if (index == 0) { x=0; z=0; }
                else if (index == 1) { x=0; z=-spacing*1.5; } // Front
                else if (index == 2) { x=-spacing*1.5; z=0; } // Left
                else if (index == 3) { x=spacing*1.5; z=0; } // Right
                else if (index == 4) { x=0; z=spacing*1.5; } // Back
                else { 
                    // Extra overlap center for >5
                    double angle = 2 * Math.PI * (index-5) / 1.0;
                    double radius = spacing * 2;
                    x = Math.cos(angle) * radius;
                    z = Math.sin(angle) * radius;
                }
                break;
                
            case 3: // Echelon
                x = index * spacing * 0.7;
                z = index * spacing * 0.7;
                break;
                
            case 4: // Line Abreast
                int size = 6;
                x = (index - (size - 1) / 2.0) * spacing;
                break;
                
            case 5: // None
            default:
                if (index == 0) { x=0; z=0; }
                else {
                    double angle = 2 * Math.PI * index / 6.0;
                    double radius = spacing; // Keep them close but not overlapping
                    x = Math.cos(angle) * radius;
                    z = Math.sin(angle) * radius;
                }
                break;
        }
        return new Vec2((float)x, (float)z);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Do not render default labels (Inventory title, etc.)
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // No default background
    }
    
    // Simple dark button
    class SimpleButton extends Button {
        boolean selected = false;
        
        public SimpleButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Match FormationButton style
            int bgColor = 0x80000000; // 50% Alpha Black
            if (isHoveredOrFocused()) {
                bgColor = 0xA0000000; // Slightly lighter on hover
            }
            
            if (selected) {
                bgColor = 0xC0404040; // Selected color
                // Yellow border with padding
                guiGraphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() + height + 2, 0xFFFFFF00); 
            }
            
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            
            int textColor = 0xFFE0E0E0;
            if (selected) textColor = 0xFFFFFF00; // Yellow text if selected
            
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
        }
    }

    // Custom Button Class to handle selection state
    class FormationButton extends Button {
        final int formationId;
        boolean selected = false;

        public FormationButton(int x, int y, int width, int height, Component message, OnPress onPress, int formationId) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.formationId = formationId;
        }
        
        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Render custom background (Translucent Black)
            int bgColor = 0x80000000; // 50% Alpha Black
            if (isHoveredOrFocused()) {
                bgColor = 0xA0000000; // Slightly lighter on hover
            }
            if (selected) {
                bgColor = 0xC0404040; // Selected color
                guiGraphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() + height + 2, 0xFFFFFF00); // Yellow border
            }
            
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            
            // Text rendering removed to focus on formation icon
        }
    }
}
