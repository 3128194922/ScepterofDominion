package com.example.scepterofdominion;

import com.example.scepterofdominion.client.gui.ScepterScreen;
import com.example.scepterofdominion.container.ScepterMenu;
import com.example.scepterofdominion.item.ScepterOfDominionItem;
import com.example.scepterofdominion.network.PacketHandler;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(ScepterOfDominion.MODID)
public class ScepterOfDominion {
    public static final String MODID = "scepterofdominion";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> SCEPTER_OF_DOMINION = ITEMS.register("scepter_of_dominion", ScepterOfDominionItem::new);

    public static final RegistryObject<MenuType<ScepterMenu>> SCEPTER_MENU = MENU_TYPES.register("scepter_menu", () -> IForgeMenuType.create(ScepterMenu::new));

    public static final RegistryObject<CreativeModeTab> SCEPTER_TAB = CREATIVE_MODE_TABS.register("scepter_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> SCEPTER_OF_DOMINION.get().getDefaultInstance())
            .title(Component.translatable("itemGroup.scepterofdominion"))
            .displayItems((parameters, output) -> {
                output.accept(SCEPTER_OF_DOMINION.get());
            }).build());

    public ScepterOfDominion(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);
        ITEMS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            MenuScreens.register(SCEPTER_MENU.get(), ScepterScreen::new);
        }
    }
}
