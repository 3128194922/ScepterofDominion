package com.example.scepterofdominion.item;

import com.example.scepterofdominion.Config;
import com.example.scepterofdominion.network.PacketHandler;
import com.example.scepterofdominion.world.StorageDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AbstractScepterItem extends Item {

    public static final int MODE_SINGLE = 0;
    public static final int MODE_FORMATION = 1;

    public AbstractScepterItem(Properties properties) {
        super(properties);
    }

    // --- Abstract Methods ---
    public abstract boolean canControl(LivingEntity entity, Player player);
    protected abstract void onEntityAdded(LivingEntity entity, Player player);
    protected abstract void onEntityRemoved(LivingEntity entity, Player player);
    protected abstract void commandEntityMove(Entity entity, Vec3 target, boolean isSprint);
    protected abstract void commandEntityAttack(Entity entity, LivingEntity target);

    // --- Mode Management ---

    public int getMode(ItemStack stack) {
        return stack.getOrCreateTag().getInt("Mode");
    }

    public void setMode(ItemStack stack, int mode) {
        stack.getOrCreateTag().putInt("Mode", mode);
    }

    public void cycleMode(ItemStack stack, Player player) {
        int current = getMode(stack);
        int next = (current + 1) % 2; // Cycle between 0 and 1
        setMode(stack, next);

        String modeKey = switch (next) {
            case MODE_SINGLE -> "message.scepterofdominion.mode.single";
            case MODE_FORMATION -> "message.scepterofdominion.mode.formation";
            default -> "Unknown";
        };
        player.displayClientMessage(Component.translatable("message.scepterofdominion.mode_switch", Component.translatable(modeKey)).withStyle(ChatFormatting.GREEN), true);
    }

    // --- Team Management ---

    public List<UUID> getTeam(ItemStack stack) {
        List<UUID> team = new ArrayList<>();
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains("Team", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Team", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                try {
                    CompoundTag member = list.getCompound(i);
                    team.add(member.getUUID("UUID"));
                } catch (Exception e) {
                    // Ignore invalid
                }
            }
        }
        return team;
    }

    public List<CompoundTag> getTeamInfo(ItemStack stack) {
        List<CompoundTag> info = new ArrayList<>();
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains("Team", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Team", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                info.add(list.getCompound(i));
            }
        }
        return info;
    }

    public void syncToClient(ItemStack stack, Player player) {
        if (!player.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            CompoundTag data = new CompoundTag();
            CompoundTag tag = stack.getOrCreateTag();
            if (tag.contains("Team")) data.put("Team", tag.getList("Team", 10));
            if (tag.hasUUID("Focus")) data.putUUID("Focus", tag.getUUID("Focus"));
            if (tag.contains("Formation")) data.putInt("Formation", tag.getInt("Formation"));
            if (tag.contains("CommandTarget")) data.put("CommandTarget", tag.getCompound("CommandTarget"));
            if (tag.hasUUID("AttackTarget")) data.putUUID("AttackTarget", tag.getUUID("AttackTarget"));

            PacketHandler.sendToPlayer(new com.example.scepterofdominion.network.PacketSyncTeam(data), serverPlayer);
        }
    }

    public void addToTeam(ItemStack stack, LivingEntity entity, Player player) {
        if (!canControl(entity, player)) {
            // Optional: Message why? Usually handled by caller or ignored
            return;
        }

        // Check if entity is already controlled by ANY scepter (ScepterOwner tag)
        if (entity.getPersistentData().contains("ScepterOwner")) {
             UUID ownerUUID = entity.getPersistentData().getUUID("ScepterOwner");
             // Even if it's the same player, they might be using a different scepter.
             // We prevent adding to a NEW team if it's already in ONE team.
             // But wait, if it's in THIS scepter's team, getTeam().contains() check below handles it.
             // If it's in ANOTHER scepter's team (even same player), we should block it.
             
             // However, getTeam() only checks the ItemStack NBT.
             // So if I have two scepters, I could add the same mob to both if I didn't have this check.
             
             // If it is already owned, we should tell the player.
             player.displayClientMessage(Component.translatable("message.scepterofdominion.already_controlled").withStyle(ChatFormatting.RED), true);
             return;
        }

        List<UUID> team = getTeam(stack);
        if (team.size() >= 6) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.team_full").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (team.contains(entity.getUUID())) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.already_in_team").withStyle(ChatFormatting.RED), true);
            return;
        }

        CompoundTag member = new CompoundTag();
        member.putUUID("UUID", entity.getUUID());
        member.putString("Name", entity.getName().getString());
        // Dimension and Pos are not needed on Add because it's auto-contained.
        // But we can initialize them if we want, though Release will overwrite.
        // Let's keep it clean and remove them here as requested.

        addTeamMemberTag(stack, member);
        
        // Mark entity as controlled
        entity.getPersistentData().putUUID("ScepterOwner", player.getUUID());
        
        player.displayClientMessage(Component.translatable("message.scepterofdominion.added_to_team", entity.getName()).withStyle(ChatFormatting.GREEN), true);

        // Auto focus if it's the first member
        if (team.isEmpty()) {
            setFocus(stack, entity.getUUID());
        }

        onEntityAdded(entity, player);

        syncToClient(stack, player);

        // Auto-contain the entity
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            StorageDimension.containPets(serverPlayer, stack, entity);
        }
    }

    private void addTeamMemberTag(ItemStack stack, CompoundTag member) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list;
        if (tag.contains("Team", Tag.TAG_LIST)) {
            list = tag.getList("Team", Tag.TAG_COMPOUND);
        } else {
            list = new ListTag();
        }
        list.add(member);
        tag.put("Team", list);
    }

    public void removeTeamMember(ItemStack stack, UUID uuid) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains("Team", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Team", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag member = list.getCompound(i);
                if (member.hasUUID("UUID") && member.getUUID("UUID").equals(uuid)) {
                    list.remove(i);
                    break;
                }
            }
            tag.put("Team", list);
        }
    }

    public void removeTeamMember(ItemStack stack, UUID uuid, Player player, LivingEntity entity) {
        removeTeamMember(stack, uuid);
        if (entity != null) {
             onEntityRemoved(entity, player);
             // Remove control tag
             entity.getPersistentData().remove("ScepterOwner");
        } else {
            // If entity is null (e.g. unloaded or dead), we can't remove the tag from the entity.
            // This might be an issue if the entity is loaded later.
            // But usually we can't access it if it's not loaded.
            // If it IS loaded but we just passed null (e.g. from GUI), we should try to find it.
            if (player.level() instanceof ServerLevel serverLevel) {
                Entity e = serverLevel.getEntity(uuid);
                if (e instanceof LivingEntity living) {
                    onEntityRemoved(living, player);
                    living.getPersistentData().remove("ScepterOwner");
                }
            }
        }
        syncToClient(stack, player);
    }

    public void setFocus(ItemStack stack, UUID uuid) {
        stack.getOrCreateTag().putUUID("Focus", uuid);
    }

    public void setFocus(ItemStack stack, UUID uuid, Player player) {
        setFocus(stack, uuid);
        syncToClient(stack, player);
    }

    public UUID getFocus(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.hasUUID("Focus") ? tag.getUUID("Focus") : null;
    }

    // --- Command State ---

    public void setCommandTarget(ItemStack stack, @Nullable Vec3 target) {
        if (target == null) {
            stack.getOrCreateTag().remove("CommandTarget");
        } else {
            CompoundTag pos = new CompoundTag();
            pos.putDouble("X", target.x);
            pos.putDouble("Y", target.y);
            pos.putDouble("Z", target.z);
            stack.getOrCreateTag().put("CommandTarget", pos);
        }
    }

    @Nullable
    public Vec3 getCommandTarget(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains("CommandTarget")) {
            CompoundTag pos = tag.getCompound("CommandTarget");
            return new Vec3(pos.getDouble("X"), pos.getDouble("Y"), pos.getDouble("Z"));
        }
        return null;
    }

    public void setAttackTarget(ItemStack stack, @Nullable UUID targetUUID) {
        if (targetUUID == null) {
            stack.getOrCreateTag().remove("AttackTarget");
        } else {
            stack.getOrCreateTag().putUUID("AttackTarget", targetUUID);
        }
    }

    @Nullable
    public UUID getAttackTarget(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.hasUUID("AttackTarget") ? tag.getUUID("AttackTarget") : null;
    }

    // --- Interactions ---

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (player.level().isClientSide) {
            return true;
        }
        
        // Use isShiftKeyDown() for better compatibility with flying, though on server side isCrouching is usually synced.
        // However, onLeftClickEntity is called on Server. 
        // If player is flying and pressing shift, they are descending, but maybe not "crouching" in the pose sense?
        // Actually, player.isCrouching() checks the pose.
        // When flying, pressing shift makes you go down, but doesn't necessarily set the CROUCHING flag/pose unless you are on ground.
        // So we should check if the player is shifting.
        // But on server, we don't have direct key access.
        // We rely on the player's state.
        // ServerPlayer.isShiftKeyDown() is not a thing.
        // But the client packet sends the sneaking state.
        // Let's trust isCrouching() or check if we can get the shift state.
        // Wait, for InteractionResultHolder use(), we have access to client side input via packet if we handle it there.
        // But onLeftClickEntity is a standard Item method.
        
        // If the user says "Flying state fails", it means isCrouching() returns false when flying + shift.
        // We might need to handle this on Client side (ClientEvents) and send a packet for Entity Interaction too?
        // ClientEvents.java already handles "Long Range Interaction" via PacketGuiAction.
        // But for "Close Range" (Left Click Entity), it triggers the standard attack/interact.
        // If we want to support "Shift+Left Click" while flying, we must handle it in ClientEvents and cancel the attack.
        
        // Let's look at ClientEvents.java again.
        // It has a "Handle Left Click (Attack Key)" event.
        // And it checks `mc.player.isCrouching()`. We changed that to `mc.options.keyShift.isDown()`.
        // So Client side detection is now fixed.
        
        // But what about the actual action?
        // If ClientEvents detects Shift+LeftClick on Entity, it sends PacketGuiAction(ACTION_LEFT_CLICK_ENTITY).
        // This packet is handled on Server.
        
        // So we need to ensure PacketGuiAction handler uses the "Shift" logic correctly.
        // PacketGuiAction usually calls something?
        // Let's check PacketGuiAction.
        
        // And for the Item.onLeftClickEntity method, it runs on Server when standard attack happens.
        // If ClientEvents cancels the event, this method might NOT be called?
        // Yes, `event.setCanceled(true)` in ClientEvents prevents the standard attack packet.
        // So the logic in AbstractScepterItem.onLeftClickEntity is skipped if ClientEvents handles it.
        
        // So the fix in ClientEvents.java should be enough for the "Remote" or "Client-initiated" interactions.
        // But wait, standard Left Click (Attack) sends a packet.
        // If we cancel it in ClientEvents, we MUST send our own packet.
        // ClientEvents DOES send PacketGuiAction for "Long Range".
        // Does it cover "Close Range" (standard reach)?
        // `getHitResult` with 64.0 reach covers close range too.
        
        // So if ClientEvents logic is correct, it supersedes standard attack.
        // Let's verify ClientEvents.java change.
        
        if (player.isCrouching()) {
            if (entity instanceof LivingEntity living) {
                 List<UUID> team = getTeam(stack);
                 if (team.contains(living.getUUID())) {
                     // Need to call remove with entity for hook
                     removeTeamMember(stack, living.getUUID(), player, living);
                     player.displayClientMessage(Component.translatable("message.scepterofdominion.removed_from_team", living.getName()).withStyle(ChatFormatting.RED), true);
                 }
            }
            return true;
        }

        if (entity instanceof LivingEntity living) {
            if (canControl(living, player)) {
                List<UUID> team = getTeam(stack);
                if (team.contains(living.getUUID())) {
                    if (player.isCrouching()) {
                        removeTeamMember(stack, living.getUUID(), player, living);
                        player.displayClientMessage(Component.translatable("message.scepterofdominion.removed_from_team", living.getName()).withStyle(ChatFormatting.RED), true);
                    } else {
                        setFocus(stack, living.getUUID());
                        player.displayClientMessage(Component.translatable("message.scepterofdominion.focus_set", living.getName()).withStyle(ChatFormatting.GOLD), true);
                    }
                } else {
                    addToTeam(stack, living, player);
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Check for shift key on client side, or rely on client packet logic
        if (level.isClientSide) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.options.keyShift.isDown()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                     NetworkHooks.openScreen(serverPlayer, new com.example.scepterofdominion.container.ScepterContainerProvider(stack), buffer -> {
                        buffer.writeItem(stack);
                    });
                } else {
                    // Client side, we should send packet if not already handled by logic below
                    // But wait, NetworkHooks.openScreen is SERVER ONLY.
                    // This block is inside "if (level.isClientSide)".
                    // So calling NetworkHooks.openScreen here is WRONG and will crash or do nothing (as LocalPlayer cannot be cast to ServerPlayer).
                    
                    // The intention of previous edit was:
                    // "In AbstractScepterItem.use's client branch... rely on ClientInputHandler sending packet".
                    
                    // So we should NOT call openScreen here.
                    // We should just return success and let ClientInputHandler do the work?
                    // Or call ClientInputHandler directly.
                    
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> 
                        com.example.scepterofdominion.client.ClientInputHandler.handleRightClick(this, stack, player, hand)
                    );
                    return InteractionResultHolder.success(stack);
                }
                return InteractionResultHolder.success(stack);
            }
            
            // Standard right click logic
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> 
                com.example.scepterofdominion.client.ClientInputHandler.handleRightClick(this, stack, player, hand)
            );
            return InteractionResultHolder.success(stack);
        } else {
            // Server Side
            // We can't easily detect key presses here for "Shift" if flying.
            // But if we want to open GUI, we usually rely on isCrouching().
            // If the user is flying + shift, isCrouching() is false.
            // So we need the CLIENT to tell us "I want to open GUI".
            // The client block above won't work because NetworkHooks.openScreen must be called on SERVER.
            
            // So we need a packet from Client saying "Open GUI".
            // Or we handle it in ClientInputHandler?
            // ClientInputHandler handles "Right Click" packet (PacketScepterRightClick).
            // Maybe we should add a flag "isShift" to PacketScepterRightClick?
            // Or add a separate packet/action "Open GUI".
            
            // Let's modify PacketScepterRightClick to include "isShift".
            // If isShift is true, we open GUI on server.
            
            // BUT wait, standard Item.use() is called on both sides.
            // If we return success on Client, we prevent standard usage?
            // Yes.
            
            // Strategy:
            // 1. In ClientInputHandler.handleRightClick, check for Shift.
            // 2. If Shift is down, send a packet "Open GUI" instead of "Right Click Action".
            // 3. On Server, handle "Open GUI".
            
            // AbstractScepterItem.use() server side just returns success/pass.
            // It relies on packets for actions.
            // So we just need to ensure Client sends the right packet.
            
            // Existing logic in use():
            // if (player.isCrouching()) -> Open GUI. (This works for ground sneak)
            // if (level.isClientSide) -> ClientInputHandler.
            
            // So on Client, we check Shift. If Shift -> Send Open GUI Packet.
            // On Server, we check isCrouching (fallback) OR wait for packet?
            // If we return success on Server without doing anything, we are fine.
            
            return InteractionResultHolder.success(stack);
        }
    }

    public void serverHandleRightClick(ItemStack stack, Level level, Player player, Vec3 targetPos, @Nullable Entity targetEntity, boolean isSprint) {
        if (targetEntity != null && targetEntity instanceof LivingEntity target) {
            List<UUID> team = getTeam(stack);
            if (!team.contains(target.getUUID())) {
                 if (isSprint) {
                     addWaypoint(stack, target.position(), target.getUUID(), player);
                 } else {
                     issueAttackCommand(stack, level, target, player);
                     player.displayClientMessage(Component.translatable("message.scepterofdominion.command_attack", target.getName()).withStyle(ChatFormatting.RED), true);
                 }
            }
        } else if (targetEntity == null) {
            // Block hit
            if (isSprint) {
                addWaypoint(stack, targetPos, null, player);
            } else {
                setCommandTarget(stack, targetPos);
                issueMoveCommand(stack, level, targetPos, isSprint, player);
                player.displayClientMessage(Component.translatable("message.scepterofdominion.command_move").withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    public void addWaypoint(ItemStack stack, Vec3 pos, @Nullable UUID targetUUID, Player player) {
        List<CompoundTag> waypoints = getWaypoints(stack);
        int max = Config.COMMON.maxWaypoints.get();
        if (waypoints.size() >= max) {
            player.displayClientMessage(Component.translatable("message.scepterofdominion.waypoint_limit", max).withStyle(ChatFormatting.RED), true);
            return;
        }

        CompoundTag wp = new CompoundTag();
        wp.putDouble("X", pos.x);
        wp.putDouble("Y", pos.y);
        wp.putDouble("Z", pos.z);
        if (targetUUID != null) {
            wp.putUUID("Target", targetUUID);
            wp.putString("Type", "ATTACK");
        } else {
            wp.putString("Type", "MOVE");
        }

        CompoundTag tag = stack.getOrCreateTag();
        ListTag list;
        if (tag.contains("Waypoints", Tag.TAG_LIST)) {
            list = tag.getList("Waypoints", Tag.TAG_COMPOUND);
        } else {
            list = new ListTag();
        }
        list.add(wp);
        tag.put("Waypoints", list);

        player.displayClientMessage(Component.translatable("message.scepterofdominion.waypoint_added", list.size()).withStyle(ChatFormatting.AQUA), true);
    }

    public List<CompoundTag> getWaypoints(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        List<CompoundTag> waypoints = new ArrayList<>();
        if (tag.contains("Waypoints", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Waypoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                waypoints.add(list.getCompound(i));
            }
        }
        return waypoints;
    }

    public void clearWaypoints(ItemStack stack) {
        stack.getOrCreateTag().remove("Waypoints");
    }

    public void executeWaypoints(ItemStack stack, Player player, Level level) {
        List<CompoundTag> waypoints = getWaypoints(stack);
        if (waypoints.isEmpty()) return;

        List<UUID> team = getTeam(stack);
        int mode = getMode(stack);
        int formationId = stack.getOrCreateTag().getInt("Formation");

        List<Entity> activeMembers = new ArrayList<>();
        if (level instanceof ServerLevel serverLevel) {
            for (UUID uuid : team) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    activeMembers.add(entity);
                }
            }
        }

        if (activeMembers.isEmpty()) {
             clearWaypoints(stack);
             return;
        }

        for (int i = 0; i < activeMembers.size(); i++) {
            Entity entity = activeMembers.get(i);
            ListTag entityQueue = new ListTag();
            
            for (CompoundTag wp : waypoints) {
                String type = wp.getString("Type");
                CompoundTag task = new CompoundTag();
                task.putString("Type", type);
                
                if (type.equals("ATTACK")) {
                    if (wp.hasUUID("Target")) {
                        task.putUUID("Target", wp.getUUID("Target"));
                    }
                } else {
                    Vec3 center = new Vec3(wp.getDouble("X"), wp.getDouble("Y"), wp.getDouble("Z"));
                    Vec3 targetPos = center;
                    
                    if (mode == MODE_FORMATION) {
                         List<Vec3> positions = calculateFormationPositions(center, formationId, activeMembers);
                         if (i < positions.size()) {
                             targetPos = positions.get(i);
                         }
                    } else {
                         UUID focus = getFocus(stack);
                         if (focus != null && !entity.getUUID().equals(focus)) {
                             continue;
                         }
                         targetPos = center;
                    }
                    
                    task.putDouble("X", targetPos.x);
                    task.putDouble("Y", targetPos.y);
                    task.putDouble("Z", targetPos.z);
                }
                entityQueue.add(task);
            }
            
            if (!entityQueue.isEmpty()) {
                entity.getPersistentData().put("ScepterWaypoints", entityQueue);
            }
        }

        clearWaypoints(stack);
        player.displayClientMessage(Component.translatable("message.scepterofdominion.waypoints_executed").withStyle(ChatFormatting.GREEN), true);
    }

    public void issueMoveCommand(ItemStack stack, Level level, Vec3 target, boolean moveOnly, Player player) {
        // Clear Waypoints on new direct command
        clearWaypoints(stack);
        
        List<UUID> team = getTeam(stack);
        int mode = getMode(stack);
        UUID focus = getFocus(stack);
        int formationId = stack.getOrCreateTag().getInt("Formation");

        setAttackTarget(stack, null);
        syncToClient(stack, player);

        List<Entity> activeMembers = new ArrayList<>();
        if (level instanceof ServerLevel serverLevel) {
            for (UUID uuid : team) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    // Clear entity waypoint queue
                    entity.getPersistentData().remove("ScepterWaypoints");
                    
                    if (mode == MODE_SINGLE) {
                         if (focus != null && uuid.equals(focus)) {
                             commandEntityMove(entity, target, moveOnly);
                         }
                    } else {
                        activeMembers.add(entity);
                    }
                }
            }
        }
        
        if (mode == MODE_SINGLE) return;

        List<Vec3> positions = calculateFormationPositions(target, formationId, activeMembers);

        for (int i = 0; i < activeMembers.size(); i++) {
            Entity entity = activeMembers.get(i);
            if (i < positions.size()) {
                commandEntityMove(entity, positions.get(i), moveOnly);
            }
        }
    }

    public void issueAttackCommand(ItemStack stack, Level level, LivingEntity target, Player player) {
        // Clear Waypoints on new direct command
        clearWaypoints(stack);

        List<UUID> team = getTeam(stack);
        int mode = getMode(stack);
        UUID focus = getFocus(stack);

        setAttackTarget(stack, target.getUUID());
        setCommandTarget(stack, null);
        syncToClient(stack, player);

        if (level instanceof ServerLevel serverLevel) {
            for (UUID uuid : team) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    // Clear entity waypoint queue
                    entity.getPersistentData().remove("ScepterWaypoints");
                    
                    if (mode == MODE_SINGLE && !uuid.equals(focus)) continue;
                    
                    commandEntityAttack(entity, target);
                }
            }
        }
    }

    public void handleLeftClickLogic(ItemStack stack, Player player, LivingEntity living) {
        if (!canControl(living, player)) return;

        List<UUID> team = getTeam(stack);
        if (team.contains(living.getUUID())) {
            setFocus(stack, living.getUUID());
            player.displayClientMessage(Component.translatable("message.scepterofdominion.focus_set", living.getName()).withStyle(ChatFormatting.GOLD), true);
        } else {
            addToTeam(stack, living, player);
        }
    }

    // Copied from ScepterOfDominionItem, could be in FormationHelper but simpler here for now or shared util
    public List<Vec3> calculateFormationPositions(Vec3 center, int formation, List<Entity> members) {
        List<Vec3> positions = new ArrayList<>();
        if (members.isEmpty()) return positions;

        double spacingMultiplier = Config.COMMON.formationSpacingMultiplier.get();
        double currentOffset = 0;

        switch (formation) {
            case 0: // Line Ahead (单纵)
                currentOffset = 0;
                for (Entity member : members) {
                    double radius = member.getBbWidth() / 2.0;
                    double gap = member.getBbWidth() * spacingMultiplier;
                    currentOffset += radius;
                    positions.add(center.add(0, 0, currentOffset));
                    currentOffset += radius + gap;
                }
                break;

            case 1: // Double Line (复纵)
                double leftOffset = 0;
                double rightOffset = 0;
                double maxHalfWidth = 0;
                for (Entity member : members) maxHalfWidth = Math.max(maxHalfWidth, member.getBbWidth() / 2.0);
                double colSpacing = maxHalfWidth + 1.0 + (maxHalfWidth * 2.0 * spacingMultiplier * 0.5);

                for (int i = 0; i < members.size(); i++) {
                    Entity member = members.get(i);
                    double radius = member.getBbWidth() / 2.0;
                    double gap = member.getBbWidth() * spacingMultiplier;

                    if (i % 2 == 0) { // Left
                        leftOffset += radius;
                        positions.add(center.add(-colSpacing, 0, leftOffset));
                        leftOffset += radius + gap;
                    } else { // Right
                        rightOffset += radius;
                        positions.add(center.add(colSpacing, 0, rightOffset));
                        rightOffset += radius + gap;
                    }
                }
                break;

            case 2: // Diamond (轮形)
                positions.add(center);
                if (members.size() > 1) {
                    List<Entity> surroundings = members.subList(1, members.size());
                    double totalCircumference = 0;
                    for (Entity e : surroundings) {
                        double gap = e.getBbWidth() * spacingMultiplier;
                        totalCircumference += e.getBbWidth() + gap;
                    }

                    double radius = Math.max(3.0, totalCircumference / (2 * Math.PI));

                    for (int i = 0; i < surroundings.size(); i++) {
                        double angle = 2 * Math.PI * i / surroundings.size();
                        angle -= Math.PI / 2;

                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        positions.add(center.add(x, 0, z));
                    }
                }
                break;

            case 3: // Echelon (梯形)
                currentOffset = 0;
                for (Entity member : members) {
                    double radius = member.getBbWidth() / 2.0;
                    double gap = member.getBbWidth() * spacingMultiplier;
                    currentOffset += radius;
                    double axisOffset = currentOffset * 0.707;
                    positions.add(center.add(axisOffset, 0, axisOffset));
                    currentOffset += radius + gap;
                }
                break;

            case 4: // Line Abreast (单横)
                double totalWidth = 0;
                for (Entity e : members) {
                    double gap = e.getBbWidth() * spacingMultiplier;
                    totalWidth += e.getBbWidth() + gap;
                }
                if (!members.isEmpty()) {
                    Entity last = members.get(members.size() - 1);
                    totalWidth -= last.getBbWidth() * spacingMultiplier;
                }

                currentOffset = -totalWidth / 2.0;
                for (Entity member : members) {
                    double radius = member.getBbWidth() / 2.0;
                    double gap = member.getBbWidth() * spacingMultiplier;
                    currentOffset += radius;
                    positions.add(center.add(currentOffset, 0, 0));
                    currentOffset += radius + gap;
                }
                break;

            case 5: // None (无)
            default:
                double circumference = 0;
                for (Entity e : members) {
                    double gap = e.getBbWidth() * spacingMultiplier;
                    circumference += e.getBbWidth() + gap;
                }
                double clusterRadius = Math.max(3.0, circumference / (2 * Math.PI));

                for (int i = 0; i < members.size(); i++) {
                    double angle = 2 * Math.PI * i / members.size();
                    double x = Math.cos(angle) * clusterRadius;
                    double z = Math.sin(angle) * clusterRadius;
                    positions.add(center.add(x, 0, z));
                }
                break;
        }

        return positions;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        int mode = getMode(stack);
        String modeKey = switch (mode) {
            case MODE_SINGLE -> "message.scepterofdominion.mode.single";
            case MODE_FORMATION -> "message.scepterofdominion.mode.formation";
            default -> "Unknown";
        };
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.mode", Component.translatable(modeKey)).withStyle(ChatFormatting.GOLD));

        List<UUID> team = getTeam(stack);
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.team_size", team.size()).withStyle(ChatFormatting.DARK_AQUA));

        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.add").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.attack").withStyle(ChatFormatting.RED));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.move").withStyle(ChatFormatting.GREEN));
        //tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.move_only").withStyle(ChatFormatting.DARK_GREEN));
        
        // Sprint usage
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.sprint_left").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.sprint_right").withStyle(ChatFormatting.LIGHT_PURPLE));
        
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.mode").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.gui").withStyle(ChatFormatting.YELLOW));
    }
}
