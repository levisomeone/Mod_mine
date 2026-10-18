package com.nightdefense;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NightDefenseMod implements ModInitializer {
	public static final String MOD_ID = "nightdefense";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.init();

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(entries -> entries.accept(ModBlocks.DEFENSE_FLAG_ITEM));

		DefenseCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(DefenseManager::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> DefenseManager.stop());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DefenseManager manager = DefenseManager.get();
			if (manager != null) {
				manager.tick();
			}
		});

		// Monstros de invasão que sobraram de uma sessão anterior (servidor fechado no meio
		// de uma noite) são removidos ao carregar, para não ficarem "órfãos" no mundo.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getTags().contains(RaidComposer.TAG)) {
				DefenseManager manager = DefenseManager.get();
				if (manager == null || !manager.isTracked(entity.getUUID())) {
					level.getServer().execute(entity::discard);
				}
			}
		});

		LOGGER.info("Noite de Defesa carregado!");
	}
}
