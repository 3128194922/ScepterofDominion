package com.example.scepterofdominion;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

@Mod.EventBusSubscriber(modid = ScepterOfDominion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    public static final CommonConfig COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class CommonConfig {
        public final ForgeConfigSpec.DoubleValue formationSpacingMultiplier;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("formation");
            formationSpacingMultiplier = builder
                    .comment("The spacing multiplier between entities in formation. Spacing = Entity Width * Multiplier.")
                    .defineInRange("spacingMultiplier", 1.0, 0.0, 100.0);
            builder.pop();
        }
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
    }
}
