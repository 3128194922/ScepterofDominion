package com.example.scepterofdominion;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Collections;

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
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> scepterBlacklist;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> dominionWhitelist;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("formation");
            formationSpacingMultiplier = builder
                    .comment("The spacing multiplier between entities in formation. Spacing = Entity Width * Multiplier.")
                    .defineInRange("spacingMultiplier", 1.0, 0.0, 100.0);
            builder.pop();

            builder.push("control");
            scepterBlacklist = builder
                    .comment("List of entities that CANNOT be controlled by the Scepter of Dominion (Tameable Scepter).",
                            "Format: modid:entity_id",
                            "Example: [\"minecraft:wolf\", \"minecraft:cat\"]")
                    .defineList("scepterBlacklist", Collections.emptyList(), o -> o instanceof String);

            dominionWhitelist = builder
                    .comment("List of entities that CAN be controlled by the Dominion Scepter (Untameable Scepter).",
                             "If empty, ALL non-tameable mobs are allowed (default behavior).",
                             "If not empty, ONLY entities in this list can be controlled.",
                             "Format: modid:entity_id")
                    .defineList("dominionWhitelist", Collections.emptyList(), o -> o instanceof String);
            builder.pop();
        }
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
    }
}
