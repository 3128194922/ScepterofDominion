package com.example.scepterofdominion.event;

import com.example.scepterofdominion.ScepterOfDominion;
import com.example.scepterofdominion.item.AbstractScepterItem;
import com.example.scepterofdominion.network.PacketExecuteWaypoints;
import com.example.scepterofdominion.network.PacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ScepterOfDominion.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        // 1. Monitor Direct Attack Command (No Waypoints)
        if (mob.tickCount % 10 == 0 && !mob.getPersistentData().contains("ScepterWaypoints", Tag.TAG_LIST)) {
            checkDirectAttackTarget(mob);
        }

        // 2. Handle Waypoints
        if (!mob.getPersistentData().contains("ScepterWaypoints", Tag.TAG_LIST)) return;
        ListTag waypoints = mob.getPersistentData().getList("ScepterWaypoints", Tag.TAG_COMPOUND);
        if (waypoints.isEmpty()) {
            mob.getPersistentData().remove("ScepterWaypoints");
            return;
        }

        CompoundTag currentTask = waypoints.getCompound(0);
        String type = currentTask.getString("Type");

        if ("ATTACK".equals(type)) {
            boolean complete = false;
            LivingEntity target = null;
            
            if (currentTask.hasUUID("Target")) {
                UUID targetId = currentTask.getUUID("Target");
                target = mob.getTarget();
                
                if (target == null || !target.getUUID().equals(targetId)) {
                    if (mob.level() instanceof ServerLevel sl) {
                        net.minecraft.world.entity.Entity e = sl.getEntity(targetId);
                        if (e instanceof LivingEntity le) {
                            mob.setTarget(le);
                            target = le;
                        } else {
                            complete = true;
                        }
                    }
                }
                
                if (target != null) {
                    if (!target.isAlive()) {
                        complete = true;
                        mob.setTarget(null);
                    }
                }
            } else {
                complete = true;
            }

            if (complete) {
                waypoints.remove(0);
                mob.getPersistentData().put("ScepterWaypoints", waypoints);
                
                if (waypoints.isEmpty() && target != null) {
                    updateCommandTarget(mob, target.position());
                }
            }
        }
    }

    private static void checkDirectAttackTarget(Mob mob) {
        if (!mob.getPersistentData().hasUUID("DominionOwner")) return;
        UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
        net.minecraft.world.entity.player.Player owner = mob.level().getPlayerByUUID(ownerId);
        
        if (owner != null) {
            ItemStack scepter = com.example.scepterofdominion.util.FormationHelper.getScepterWithPet(owner, mob.getUUID());
            if (!scepter.isEmpty() && scepter.getItem() instanceof AbstractScepterItem item) {
                UUID attackTargetUUID = item.getAttackTarget(scepter);
                if (attackTargetUUID != null) {
                    if (mob.level() instanceof ServerLevel sl) {
                        net.minecraft.world.entity.Entity target = sl.getEntity(attackTargetUUID);
                        // If target is loaded but dead
                        if (target != null && !target.isAlive()) {
                            item.setCommandTarget(scepter, target.position());
                            item.setAttackTarget(scepter, null);
                            item.syncToClient(scepter, owner);
                        }
                    }
                }
            }
        }
    }

    private static void updateCommandTarget(Mob mob, net.minecraft.world.phys.Vec3 pos) {
        if (!mob.getPersistentData().hasUUID("DominionOwner")) return;
        UUID ownerId = mob.getPersistentData().getUUID("DominionOwner");
        net.minecraft.world.entity.player.Player owner = mob.level().getPlayerByUUID(ownerId);
        
        if (owner != null) {
            ItemStack scepter = com.example.scepterofdominion.util.FormationHelper.getScepterWithPet(owner, mob.getUUID());
            if (!scepter.isEmpty() && scepter.getItem() instanceof AbstractScepterItem item) {
                // For Waypoints, we always update CommandTarget if in Single Mode, 
                // but what if in Formation mode? 
                // The user request was "after path ending", but previously we only did it for Single Mode Focus.
                // The new request "after killing opponent entity... set position as CommandTarget" implies ALL modes maybe?
                // If Formation mode, updating CommandTarget (center) to the dead body position means the whole team moves there.
                // That seems correct for RTS behavior (Attack Move -> Attack -> Move to location).
                
                item.setCommandTarget(scepter, pos);
                item.syncToClient(scepter, owner);
            }
        }
    }

    // Client side events are handled in ClientEventHandler to support sprint key detection
}
