package com.nightdefense;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Identifier FLAG_ID =
			Identifier.fromNamespaceAndPath(NightDefenseMod.MOD_ID, "defense_flag");

	public static final Block DEFENSE_FLAG;
	public static final Item DEFENSE_FLAG_ITEM;

	static {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, FLAG_ID);
		DEFENSE_FLAG = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new DefenseFlagBlock(BlockBehaviour.Properties.of()
						.setId(blockKey)
						.mapColor(MapColor.COLOR_RED)
						.strength(1.5F, 1200.0F) // resistente a explosões de creeper
						.sound(SoundType.WOOD)
						.noOcclusion()
						.lightLevel(state -> 6)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, FLAG_ID);
		DEFENSE_FLAG_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(DEFENSE_FLAG, new Item.Properties()
						.setId(itemKey)
						.useBlockDescriptionPrefix()));
	}

	private ModBlocks() {
	}

	/** Só serve para forçar o carregamento da classe (e portanto o registro). */
	public static void init() {
	}
}
