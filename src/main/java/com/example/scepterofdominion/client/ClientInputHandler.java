package com.example.scepterofdominion.client;

import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.network.PacketHandler;
import com.example.scepterofdominion.network.PacketScepterRightClick;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ClientInputHandler {

    public static void handleRightClick(AbstractScepterItem item, ItemStack stack, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return;

        // Check for shift (GUI Open)
        if (Minecraft.getInstance().options.keyShift.isDown()) {
            PacketHandler.sendToServer(new com.example.scepterofdominion.network.PacketOpenScepterGui());
            return;
        }

        Level level = player.level();
        double reachDistance = 50.0;
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(viewVec.x * reachDistance, viewVec.y * reachDistance, viewVec.z * reachDistance);

        HitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(eyePos, endPos, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));

        double blockDist = reachDistance;
        if (blockHit.getType() != HitResult.Type.MISS) {
            blockDist = blockHit.getLocation().distanceTo(eyePos);
            endPos = blockHit.getLocation();
        }

        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(blockDist)).inflate(1.0D);
        net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                endPos,
                searchBox,
                (entity) -> {
                    if (entity.isSpectator() || !entity.isPickable() || !(entity instanceof LivingEntity) || entity.is(player)) return false;
                    
                    if (entity.getPersistentData().hasUUID("DominionOwner")) {
                        return !entity.getPersistentData().getUUID("DominionOwner").equals(player.getUUID());
                    }
                    if (entity instanceof net.minecraft.world.entity.OwnableEntity ownable) {
                         return ownable.getOwnerUUID() == null || !ownable.getOwnerUUID().equals(player.getUUID());
                    }
                    return true;
                },
                blockDist * blockDist
        );

        boolean isSprint = Minecraft.getInstance().options.keySprint.isDown();
        boolean hitEntity = false;
        int entityId = -1;
        Vec3 pos = Vec3.ZERO;

        if (entityHit != null) {
            hitEntity = true;
            entityId = entityHit.getEntity().getId();
            pos = entityHit.getEntity().position();
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            pos = blockHit.getLocation();
        } else {
            return; // Missed everything
        }

        PacketHandler.sendToServer(new PacketScepterRightClick(isSprint, hitEntity, entityId, pos));
    }
}
