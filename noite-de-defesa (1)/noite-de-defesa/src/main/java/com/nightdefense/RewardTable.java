package com.nightdefense;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Recompensas aleatórias por noite defendida. Itens melhores só aparecem em noites mais altas. */
public final class RewardTable {
	/**
	 * @param perNight quantidade extra por noite (ex.: 0.3 = +1 item a cada ~3 noites)
	 */
	private record Reward(Item item, int minNight, int weight, int min, int max, double perNight) {
	}

	private static final List<Reward> REWARDS = List.of(
			new Reward(Items.COOKED_BEEF, 1, 10, 4, 8, 0.2),
			new Reward(Items.ARROW, 1, 6, 8, 16, 0.3),
			new Reward(Items.IRON_INGOT, 1, 10, 2, 5, 0.3),
			new Reward(Items.GOLD_INGOT, 2, 6, 2, 4, 0.2),
			new Reward(Items.REDSTONE, 2, 5, 4, 10, 0.2),
			new Reward(Items.LAPIS_LAZULI, 2, 5, 3, 8, 0.2),
			new Reward(Items.EMERALD, 3, 6, 1, 3, 0.15),
			new Reward(Items.EXPERIENCE_BOTTLE, 3, 8, 3, 6, 0.3),
			new Reward(Items.DIAMOND, 4, 4, 1, 2, 0.1),
			new Reward(Items.ENDER_PEARL, 5, 4, 1, 2, 0.05),
			new Reward(Items.GOLDEN_APPLE, 6, 3, 1, 1, 0.05),
			new Reward(Items.OBSIDIAN, 8, 4, 2, 4, 0.1),
			new Reward(Items.NETHERITE_SCRAP, 15, 2, 1, 1, 0.03),
			new Reward(Items.ENCHANTED_GOLDEN_APPLE, 20, 1, 1, 1, 0.0),
			new Reward(Items.TOTEM_OF_UNDYING, 25, 1, 1, 1, 0.0),
			new Reward(Items.NETHERITE_INGOT, 40, 1, 1, 1, 0.0));

	private RewardTable() {
	}

	/**
	 * Sorteia as recompensas da noite {@code night} (a noite que acabou de ser vencida).
	 * Número de sorteios: 2 + noite/8 (máx. 8), com 2 sorteios extras a cada 10 noites.
	 */
	public static List<ItemStack> roll(int night) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int rolls = Math.min(8, 2 + night / 8);
		if (night % 10 == 0) {
			rolls += 2;
		}

		List<Reward> unlocked = new ArrayList<>();
		int weightSum = 0;
		for (Reward reward : REWARDS) {
			if (night >= reward.minNight) {
				unlocked.add(reward);
				weightSum += reward.weight;
			}
		}

		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < rolls; i++) {
			int pick = random.nextInt(weightSum);
			for (Reward reward : unlocked) {
				pick -= reward.weight;
				if (pick < 0) {
					int count = reward.min + random.nextInt(reward.max - reward.min + 1)
							+ (int) (night * reward.perNight);
					ItemStack stack = new ItemStack(reward.item, 1);
					count = Math.max(1, Math.min(count, stack.getMaxStackSize()));
					result.add(new ItemStack(reward.item, count));
					break;
				}
			}
		}
		return result;
	}
}
