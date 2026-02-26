package com.example.scepterofdominion.item;

import com.example.scepterofdominion.ScepterOfDominion;
import com.example.scepterofdominion.ai.ScepterAIHandler;
import com.example.scepterofdominion.network.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScepterOfDominionItem extends Item {

    public static final int MODE_SINGLE = 0;
    public static final int MODE_FORMATION = 1;
    // public static final int MODE_FORMATION = 2; // Deprecated, merged into 1

    public ScepterOfDominionItem() {
        super(new Item.Properties().stacksTo(1));
    }

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
            case MODE_FORMATION -> "message.scepterofdominion.mode.formation"; // Now covers both Team and Formation
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
            
            PacketHandler.sendToPlayer(new com.example.scepterofdominion.network.PacketSyncTeam(data), serverPlayer);
        }
    }

    public void addToTeam(ItemStack stack, LivingEntity entity, Player player) {
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
        
        addTeamMemberTag(stack, member);
        player.displayClientMessage(Component.translatable("message.scepterofdominion.added_to_team", entity.getName()).withStyle(ChatFormatting.GREEN), true);
        
        // Auto focus if it's the first member
        if (team.isEmpty()) {
            setFocus(stack, entity.getUUID());
        }
        
        // Inject AI for existing entity
        if (entity instanceof TamableAnimal tamable) {
            ScepterAIHandler.injectScepterAI(tamable);
        }
        
        syncToClient(stack, player);
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
    
    // Override removeTeamMember to include player for sync? Or overloaded.
    public void removeTeamMember(ItemStack stack, UUID uuid, Player player) {
        removeTeamMember(stack, uuid);
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

    // --- Interactions ---

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        // IMPORTANT: Return true on CLIENT too, to prevent the "attack" animation/packet from desyncing or causing ghost attacks.
        // However, the actual logic is executed on SERVER.
        
        if (player.level().isClientSide) {
            return true; // Consume event on client side
        }

        // Prevent attack when sneaking (Mode Switch / Remove Team)
        if (player.isCrouching()) {
            // We still need to handle the "Remove from Team" logic if it's an owner, 
            // but that is also handled in onEntitySwing (Client) -> Packet -> Server.
            // However, onLeftClickEntity logic below handles it on Server directly for consistency if triggered.
            // But if we return true here, we must ensure the logic runs.
            
            if (entity instanceof LivingEntity living && isOwner(player, living)) {
                 List<UUID> team = getTeam(stack);
                 if (team.contains(living.getUUID())) {
                     removeTeamMember(stack, living.getUUID());
                     player.displayClientMessage(Component.translatable("message.scepterofdominion.removed_from_team", living.getName()).withStyle(ChatFormatting.RED), true);
                 }
            }
            return true;
        }
        
        if (entity instanceof LivingEntity living) {
            if (isOwner(player, living)) {
                // Server side logic: Add to team or Focus
                List<UUID> team = getTeam(stack);
                if (team.contains(living.getUUID())) {
                    // Check Sneak for Remove vs Focus
                    if (player.isCrouching()) {
                        removeTeamMember(stack, living.getUUID());
                        player.displayClientMessage(Component.translatable("message.scepterofdominion.removed_from_team", living.getName()).withStyle(ChatFormatting.RED), true);
                    } else {
                        setFocus(stack, living.getUUID());
                        player.displayClientMessage(Component.translatable("message.scepterofdominion.focus_set", living.getName()).withStyle(ChatFormatting.GOLD), true);
                    }
                } else {
                    // Not in team -> Add
                    addToTeam(stack, living, player);
                }
                return true; // Cancel attack
            }
        }
        
        return false; // Allow default attack if not owner or not LivingEntity
    }

    public void handleLeftClickLogic(ItemStack stack, Player player, LivingEntity living) {
        if (!isOwner(player, living)) return;
        
        List<UUID> team = getTeam(stack);
        if (team.contains(living.getUUID())) {
            setFocus(stack, living.getUUID());
            player.displayClientMessage(Component.translatable("message.scepterofdominion.focus_set", living.getName()).withStyle(ChatFormatting.GOLD), true);
        } else {
            addToTeam(stack, living, player);
        }
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return super.onEntitySwing(stack, entity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (player.isCrouching()) {
            if (level.isClientSide) {
                // Open GUI logic handled on client side if needed, but usually server opens container
            } else {
                // Server side open GUI
                NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, new com.example.scepterofdominion.container.ScepterContainerProvider(stack), buffer -> {
                    buffer.writeItem(stack);
                });
            }
            return InteractionResultHolder.success(stack);
        }
        
        // Command Logic (Right Click)
        // 2. Right click enemy -> Attack (Long range)
        // 3. Right click ground -> Move
        // 4. Sprint + Right click -> Move Only
        
        if (!level.isClientSide) {
            double reachDistance = 50.0;
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 viewVec = player.getViewVector(1.0F);
            Vec3 endPos = eyePos.add(viewVec.x * reachDistance, viewVec.y * reachDistance, viewVec.z * reachDistance);
            
            // Raytrace blocks first
            net.minecraft.world.phys.HitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(eyePos, endPos, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            
            double blockDist = reachDistance;
            if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                blockDist = blockHit.getLocation().distanceTo(eyePos);
                endPos = blockHit.getLocation();
            }

            // Raytrace entities
            AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(blockDist)).inflate(1.0D);
            net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    player,
                    eyePos,
                    endPos,
                    searchBox,
                    (entity) -> !entity.isSpectator() && entity.isPickable() && entity instanceof LivingEntity && !isOwner(player, (LivingEntity) entity),
                    blockDist * blockDist
            );

            if (entityHit != null) {
                // Hit an entity (Enemy) -> Attack
                LivingEntity target = (LivingEntity) entityHit.getEntity();
                issueAttackCommand(stack, level, target);
                player.displayClientMessage(Component.translatable("message.scepterofdominion.command_attack", target.getName()).withStyle(ChatFormatting.RED), true);
            } else if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                // Hit a block -> Move
                Vec3 targetPos = blockHit.getLocation();
                boolean isSprint = player.isSprinting();
                
                // Set Command Target for persistence
                setCommandTarget(stack, targetPos);
                
                issueMoveCommand(stack, level, targetPos, isSprint);
                player.displayClientMessage(Component.translatable("message.scepterofdominion.command_move").withStyle(ChatFormatting.GREEN), true);
            }
        }
        
        return InteractionResultHolder.success(stack);
    }
    
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        // Interaction is now handled by raytrace in use() for consistent long range behavior.
        // However, standard interaction range might still trigger this.
        // If we want to prevent double firing, we can return PASS or SUCCESS.
        // But since we handle logic in use(), we should probably return SUCCESS to consume the action if it's an owner interaction, 
        // but for attacks, use() handles it.
        
        if (isOwner(player, interactionTarget)) {
            // Owner interaction logic (Add/Focus is Left Click)
            // Right click owner could be Sit/Stand (optional)
            return InteractionResult.PASS;
        }
        
        return InteractionResult.PASS; // Delegate to use()
    }
    
    private void issueMoveCommand(ItemStack stack, Level level, Vec3 target, boolean moveOnly) {
        List<UUID> team = getTeam(stack);
        int mode = getMode(stack);
        UUID focus = getFocus(stack);
        int formationId = stack.getOrCreateTag().getInt("Formation");
        
        // If in SINGLE mode, only move the focused entity
        if (mode == MODE_SINGLE) {
            if (focus == null) return;
            
            if (level instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(focus);
                if (entity instanceof TamableAnimal mob && team.contains(focus)) {
                     mob.setOrderedToSit(false); // Force stand up
                     mob.getNavigation().moveTo(target.x, target.y, target.z, 1.5D);
                     // Clear attack target when moving
                     mob.setTarget(null);
                }
            }
            return;
        }

        // If in FORMATION mode (which now includes Team behavior), move everyone according to formation
        for (int i = 0; i < team.size(); i++) {
            UUID uuid = team.get(i);
            if (level instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity instanceof TamableAnimal mob) {
                    mob.setOrderedToSit(false); // Force stand up
                    
                    // Use formation logic for all
                    Vec3 pos = getFormationPos(target, formationId, i, team.size());
                    
                    mob.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.5D);
                    // Clear attack target when moving
                    mob.setTarget(null); 
                }
            }
        }
    }
    
    private Vec3 getFormationPos(Vec3 center, int formation, int index, int size) {
        // Formations based on ShinColle FormationHelper logic
        // ShinColle formations: 
        // 1: Line Ahead (Single Column)
        // 2: Double Line
        // 3: Diamond (Wheel)
        // 4: Echelon
        // 5: Line Abreast (Horizontal)
        
        // Our GUI IDs: 0:单纵, 1:复纵, 2:轮形, 3:梯形, 4:单横, 5:无
        // Map our IDs to ShinColle Logic (ShinColle uses 1-based IDs internally, but logic is positional)
        
        double x = 0;
        double z = 0;
        double spacing = 3.0; // ShinColle uses 3 blocks spacing generally
        
        switch (formation) {
            case 0: // Line Ahead (单纵)
                // ShinColle: nextLineAheadPos -> x - 3 (face positive)
                // We assume facing North (negative Z) for simplicity relative to center?
                // Or just arrange them in a line behind leader.
                z = index * spacing;
                break;
                
            case 1: // Double Line (复纵)
                // ShinColle: 
                // 0,1
                // 2,3
                // 4,5
                // index 0 is left, 1 is right?
                // ShinColle logic:
                // formatPos 1 (index 0?): x+3 (along Z)
                // formatPos 2 (index 1?): x-3 (along Z)
                // Actually ShinColle uses explicit positions per ship slot.
                // Simplified: 2 columns.
                x = (index % 2 == 0 ? -1 : 1) * spacing;
                z = (index / 2) * spacing;
                break;
                
            case 2: // Diamond (轮形)
                // ShinColle Diamond:
                //       1
                //    2  5  3
                //       0
                //       4
                // (Indices seem mixed in ShinColle comments)
                // Let's implement a standard Diamond shape around center.
                // 0: Center (Leader) -> Offset 0,0
                // 1: Front
                // 2: Left
                // 3: Right
                // 4: Back
                // 5: Center-Back?
                
                if (index == 0) { x=0; z=0; }
                else if (index == 1) { x=0; z=-spacing*1.5; } // Front
                else if (index == 2) { x=-spacing*1.5; z=0; } // Left
                else if (index == 3) { x=spacing*1.5; z=0; } // Right
                else if (index == 4) { x=0; z=spacing*1.5; } // Back
                else { x=0; z=0; } // Extra overlap center
                break;
                
            case 3: // Echelon (梯形)
                // ShinColle Echelon: Diagonal
                // x - 2, z - 2
                x = index * spacing * 0.7; // 0.7 approx 1/sqrt(2)
                z = index * spacing * 0.7;
                break;
                
            case 4: // Line Abreast (单横)
                // ShinColle: Horizontal line
                // x + 3 or x - 3
                // Center the line?
                x = (index - (size - 1) / 2.0) * spacing;
                break;
                
            case 5: // None (无)
            default:
                // Random/Cluster
                double angle = 2 * Math.PI * index / size;
                double radius = 3.0;
                x = Math.cos(angle) * radius;
                z = Math.sin(angle) * radius;
                break;
        }
        
        return center.add(x, 0, z);
    }
    
    private void issueAttackCommand(ItemStack stack, Level level, LivingEntity target) {
        List<UUID> team = getTeam(stack);
        int mode = getMode(stack);
        UUID focus = getFocus(stack);
        
        for (UUID uuid : team) {
            if (mode == MODE_SINGLE && !uuid.equals(focus)) continue;
            
            if (level instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity instanceof TamableAnimal mob) {
                    mob.setTarget(target);
                    // Maybe teleport if too far?
                }
            }
        }
    }

    private boolean isOwner(Player player, LivingEntity entity) {
        if (entity instanceof OwnableEntity ownable) {
            return player.getUUID().equals(ownable.getOwnerUUID());
        }
        return false;
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
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.move_only").withStyle(ChatFormatting.DARK_GREEN));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.mode").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.translatable("tooltip.scepterofdominion.usage.gui").withStyle(ChatFormatting.YELLOW));
    }
}
