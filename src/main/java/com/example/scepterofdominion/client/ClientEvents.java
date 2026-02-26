package com.example.scepterofdominion.client;

import com.example.scepterofdominion.ScepterOfDominion;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ScepterOfDominion.MODID, value = Dist.CLIENT)
public class ClientEvents {

    private static long lastInputTime = 0;

    @SubscribeEvent
    public static void onKeyInput(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Handle Left Click (Attack Key) with Scepter
        if (event.getKeyMapping() == mc.options.keyAttack) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof ScepterOfDominionItem) {
                // 1. Mode Switch (Sneak + Left Click)
                if (mc.player.isCrouching()) {
                    if (System.currentTimeMillis() - lastInputTime > 300) {
                        lastInputTime = System.currentTimeMillis();
                        com.example.scepterofdominion.network.PacketHandler.sendToServer(new com.example.scepterofdominion.network.PacketModeSwitch());
                        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                    event.setCanceled(true);
                    event.setSwingHand(false);
                    return;
                }

                // 2. Long Range Interaction (Add/Focus/Remove)
                double reach = 64.0;
                HitResult hitResult = getHitResult(mc.player, reach);
                
                if (hitResult instanceof EntityHitResult entityHit) {
                    Entity target = entityHit.getEntity();
                    if (target instanceof LivingEntity living) {
                        boolean isOwner = (living instanceof OwnableEntity ownable && mc.player.getUUID().equals(ownable.getOwnerUUID()));
                        
                        if (isOwner) {
                            if (System.currentTimeMillis() - lastInputTime > 300) {
                                lastInputTime = System.currentTimeMillis();
                                // Send Packet to Server to Add/Focus/Remove
                                com.example.scepterofdominion.network.PacketHandler.sendToServer(new com.example.scepterofdominion.network.PacketGuiAction(com.example.scepterofdominion.network.PacketGuiAction.ACTION_LEFT_CLICK_ENTITY, 0, target.getUUID().toString()));
                                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            }
                            event.setCanceled(true);
                            event.setSwingHand(false);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof ScepterOfDominionItem scepter)) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // 1. Always Render Focus Pet (Gold Box)
        // Or if in FORMATION mode, render ALL team members as Gold
        UUID focusUUID = scepter.getFocus(stack);
        List<UUID> teamUUIDs = scepter.getTeam(stack);
        int mode = scepter.getMode(stack);
        
        if (mode == ScepterOfDominionItem.MODE_FORMATION) {
            // Render ALL team members as Gold (Focus)
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (teamUUIDs.contains(entity.getUUID()) && entity instanceof LivingEntity living) {
                    AABB box = living.getBoundingBox();
                    LevelRenderer.renderLineBox(poseStack, buffer, box, 1.0f, 0.84f, 0.0f, 1.0f);
                }
            }
        } else if (focusUUID != null) {
            // Render ONLY focus as Gold
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.getUUID().equals(focusUUID) && entity instanceof LivingEntity living) {
                    AABB box = living.getBoundingBox();
                    LevelRenderer.renderLineBox(poseStack, buffer, box, 1.0f, 0.84f, 0.0f, 1.0f);
                    break;
                }
            }
        }

        // 2. Render Raytrace Result (Long Range)
        double reachDistance = 50.0;
        HitResult hitResult = getHitResult(mc.player, reachDistance);

        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            if (hitResult instanceof EntityHitResult entityHit) {
                Entity target = entityHit.getEntity();
                if (target instanceof LivingEntity living) {
                    // Determine color based on relationship
                    float r = 1.0f, g = 1.0f, b = 1.0f, a = 1.0f;
                    
                    List<UUID> team = scepter.getTeam(stack);
                    UUID focus = scepter.getFocus(stack);
                    
                    boolean isTeam = team.contains(living.getUUID());
                    boolean isFocus = false;
                    
                    if (scepter.getMode(stack) == ScepterOfDominionItem.MODE_FORMATION) {
                        isFocus = isTeam; // All team members are focus in Formation mode
                    } else {
                        isFocus = isTeam && living.getUUID().equals(focus);
                    }
                    
                    boolean isOwner = (living instanceof OwnableEntity ownable && mc.player.getUUID().equals(ownable.getOwnerUUID()));

                    if (isFocus) {
                        // Gold (Already rendered, but reinforce or skip?)
                        // Skip if already rendered above
                        r = 1.0f; g = 0.84f; b = 0.0f;
                    } else if (isTeam) {
                        // Green for Team Member
                        r = 0.0f; g = 1.0f; b = 0.0f;
                    } else if (isOwner) {
                        // Blue/Cyan for Tamed but not in team
                        r = 0.0f; g = 1.0f; b = 1.0f;
                    } else {
                        // Red for Enemy / Attack Target
                        r = 1.0f; g = 0.0f; b = 0.0f;
                    }

                    // Only render if not already rendered as focus (to avoid Z-fighting or double draw, though lines are fine)
                    if (!isFocus) {
                        AABB box = living.getBoundingBox();
                        LevelRenderer.renderLineBox(poseStack, buffer, box, r, g, b, a);
                    }
                }
            } else if (hitResult instanceof BlockHitResult blockHit) {
                // Blue for Move Target
                BlockPos pos = blockHit.getBlockPos();
                AABB box = new AABB(pos);
                LevelRenderer.renderLineBox(poseStack, buffer, box, 0.0f, 0.0f, 1.0f, 1.0f);
            }
        }

        poseStack.popPose();
    }

    private static HitResult getHitResult(Player player, double distance) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(viewVec.x * distance, viewVec.y * distance, viewVec.z * distance);
        
        // Raytrace blocks first
        HitResult blockHit = level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        
        double blockDist = distance;
        if (blockHit.getType() != HitResult.Type.MISS) {
            blockDist = blockHit.getLocation().distanceTo(eyePos);
            endPos = blockHit.getLocation(); // Limit entity search to block hit
        }

        // Raytrace entities
        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(blockDist)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                endPos,
                searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable(),
                blockDist * blockDist
        );

        if (entityHit != null) {
            return entityHit;
        }
        return blockHit;
    }
}
