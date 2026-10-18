package com.nightdefense;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

public final class DefenseCommands {
	private static final Predicate<CommandSourceStack> DEBUG_ALLOWED =
			source -> source.getEntity() == null || source.getServer().isSingleplayer();

	private DefenseCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("defesa")
						.then(Commands.literal("status").executes(ctx -> {
							DefenseManager manager = DefenseManager.get();
							String text = manager == null ? "O mod ainda não iniciou." : manager.describeFlags();
							ctx.getSource().sendSuccess(() -> Component.literal(text), false);
							return 1;
						}))
						.then(Commands.literal("iniciar").requires(DEBUG_ALLOWED).executes(ctx -> {
							DefenseManager manager = DefenseManager.get();
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							String text = manager == null ? "O mod ainda não iniciou."
									: manager.forceStart(ctx.getSource().getLevel(), player.blockPosition());
							ctx.getSource().sendSuccess(() -> Component.literal(text), false);
							return 1;
						}))
						.then(Commands.literal("noite").requires(DEBUG_ALLOWED)
								.then(Commands.argument("n", IntegerArgumentType.integer(0, 1_000_000))
										.executes(ctx -> {
											DefenseManager manager = DefenseManager.get();
											ServerPlayer player = ctx.getSource().getPlayerOrException();
											int nights = IntegerArgumentType.getInteger(ctx, "n");
											String text = manager == null ? "O mod ainda não iniciou."
													: manager.setNights(player.blockPosition(), nights);
											ctx.getSource().sendSuccess(() -> Component.literal(text), false);
											return 1;
										})))));
	}
}
