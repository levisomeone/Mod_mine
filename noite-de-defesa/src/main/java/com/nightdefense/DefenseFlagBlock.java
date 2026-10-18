package com.nightdefense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Bandeira de Defesa. O bloco em si não guarda nada: quem controla o progresso,
 * as invasões e a persistência é o {@link DefenseManager}.
 */
public class DefenseFlagBlock extends Block {
	public static final MapCodec<DefenseFlagBlock> CODEC = simpleCodec(DefenseFlagBlock::new);
	private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

	public DefenseFlagBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		DefenseManager manager = DefenseManager.get();
		if (manager == null) {
			return;
		}
		String error = manager.tryRegister(serverLevel, pos);
		if (error != null) {
			level.removeBlock(pos, false);
			Block.popResource(level, pos, new ItemStack(this));
			if (placer instanceof ServerPlayer player) {
				player.sendSystemMessage(Component.literal("§c" + error));
			}
		} else if (placer instanceof ServerPlayer player) {
			player.sendSystemMessage(Component.literal(
					"§6Bandeira de Defesa erguida! §7Quando a noite chegar, a horda virá. Defenda-a!"));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
											   BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			DefenseManager manager = DefenseManager.get();
			if (manager != null) {
				manager.sendStatus(serverLevel, pos, serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
