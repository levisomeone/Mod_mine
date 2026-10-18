package com.nightdefense;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Toda a "receita" de dificuldade do mod fica aqui: quais monstros aparecem em cada noite,
 * quantos, o quanto são mais fortes e que equipamento carregam.
 *
 * A progressão é LINEAR e lenta: cada noite acrescenta só um pouquinho (meio monstro a mais,
 * +1% de vida e dano). Para deixar mais fácil ou mais difícil, mexa nos números abaixo.
 */
public final class RaidComposer {
	public static final String TAG = "nightdefense_raider";
	public static final String BOSS_TAG = "nightdefense_boss";
	public static final String TNT_TAG = "nightdefense_tnt";

	// ------------------------------------------------------------------ ajustes
	/** Monstros por noite = BASE + noite * POR_NOITE (limitado a MAX_MONSTROS). */
	private static final int BASE_MONSTROS = 4;
	private static final double MONSTROS_POR_NOITE = 0.5;
	private static final int MAX_MONSTROS = 80;
	/** Bônus de vida e dano por noite (0.01 = +1%), com teto. */
	private static final double VIDA_POR_NOITE = 0.01;
	private static final double DANO_POR_NOITE = 0.01;
	private static final double TETO_VIDA = 3.0;
	private static final double TETO_DANO = 2.5;
	/** Zumbis-bomba (TNT) começam nessa noite. */
	public static final int TNT_A_PARTIR_DA_NOITE = 30;

	public enum Kind {NORMAL, BOSS, TNT}

	public record Spawn(EntityType<? extends Mob> type, Kind kind) {
	}

	private record Entry(EntityType<? extends Mob> type, int minNight, int weight) {
	}

	/** Monstros liberados conforme as noites passam. */
	private static final List<Entry> POOL = List.of(
			new Entry(EntityType.ZOMBIE, 1, 30),
			new Entry(EntityType.SKELETON, 3, 16),
			new Entry(EntityType.SPIDER, 5, 12),
			new Entry(EntityType.HUSK, 8, 10),
			new Entry(EntityType.CREEPER, 12, 8),
			new Entry(EntityType.STRAY, 16, 8),
			new Entry(EntityType.WITCH, 20, 5),
			new Entry(EntityType.CAVE_SPIDER, 24, 6),
			new Entry(EntityType.VINDICATOR, 28, 6),
			new Entry(EntityType.PILLAGER, 32, 6));

	/** A partir de qual noite cada nível de equipamento aparece (madeira, pedra, ferro, diamante, netherite). */
	private static final int[] SWORD_NIGHTS = {3, 15, 30, 50, 80};
	/** Idem para armadura (couro, malha, ferro, diamante, netherite). */
	private static final int[] ARMOR_NIGHTS = {10, 25, 40, 60, 90};

	private static final Item[] SWORDS = {
			Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
	private static final Item[] HELMETS = {
			Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET,
			Items.NETHERITE_HELMET};
	private static final Item[] CHESTPLATES = {
			Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE,
			Items.NETHERITE_CHESTPLATE};
	private static final Item[] LEGGINGS = {
			Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS,
			Items.NETHERITE_LEGGINGS};
	private static final Item[] BOOTS = {
			Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS,
			Items.NETHERITE_BOOTS};

	private RaidComposer() {
	}

	// ------------------------------------------------------------------ composição

	/** Lista (embaralhada) dos monstros da noite {@code night}. Sem limite de noites. */
	public static Deque<Spawn> compose(int night) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int total = Math.min(MAX_MONSTROS, BASE_MONSTROS + (int) (night * MONSTROS_POR_NOITE));

		List<Entry> unlocked = new ArrayList<>();
		int weightSum = 0;
		for (Entry entry : POOL) {
			if (night >= entry.minNight) {
				unlocked.add(entry);
				weightSum += entry.weight;
			}
		}

		List<Spawn> list = new ArrayList<>();
		for (int i = 0; i < total; i++) {
			int roll = random.nextInt(weightSum);
			for (Entry entry : unlocked) {
				roll -= entry.weight;
				if (roll < 0) {
					list.add(new Spawn(entry.type, Kind.NORMAL));
					break;
				}
			}
		}

		// Zumbis-bomba: 1 a partir da noite 30, +1 a cada 10 noites (máx. 8).
		if (night >= TNT_A_PARTIR_DA_NOITE) {
			int bombers = Math.min(8, 1 + (night - TNT_A_PARTIR_DA_NOITE) / 10);
			for (int i = 0; i < bombers; i++) {
				list.add(new Spawn(EntityType.ZOMBIE, Kind.TNT));
			}
		}
		Collections.shuffle(list, random);

		// Chefes no fim da fila: a cada 10 noites um "Capitão da Horda"; a cada 20, um Ravager.
		if (night % 10 == 0) {
			int bosses = 1 + night / 50;
			for (int i = 0; i < bosses; i++) {
				list.add(new Spawn(EntityType.ZOMBIE, Kind.BOSS));
			}
		}
		if (night % 20 == 0) {
			list.add(new Spawn(EntityType.RAVAGER, Kind.NORMAL));
		}
		return new ArrayDeque<>(list);
	}

	// ------------------------------------------------------------------ força e equipamento

	public static void prepare(Mob mob, Spawn spawn, int night) {
		mob.addTag(TAG);
		mob.setPersistenceRequired();

		double health = Math.min(TETO_VIDA, 1.0 + VIDA_POR_NOITE * night);
		double damage = Math.min(TETO_DANO, 1.0 + DANO_POR_NOITE * night);
		double speed = Math.min(1.15, 1.0 + 0.002 * night);

		if (spawn.kind == Kind.BOSS) {
			mob.addTag(BOSS_TAG);
			health *= 4.0 + night / 20.0;
			damage *= 1.5;
			mob.setCustomName(Component.literal("Capitão da Horda"));
			mob.setCustomNameVisible(true);
		}
		multiply(mob, Attributes.MAX_HEALTH, health);
		multiply(mob, Attributes.ATTACK_DAMAGE, damage);
		multiply(mob, Attributes.MOVEMENT_SPEED, speed);
		AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null && follow.getBaseValue() < 48.0) {
			follow.setBaseValue(48.0);
		}
		mob.setHealth(mob.getMaxHealth());

		if (spawn.kind == Kind.TNT) {
			mob.addTag(TNT_TAG);
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TNT));
			mob.setCustomName(Component.literal("Zumbi-Bomba"));
			mob.setCustomNameVisible(true);
		} else {
			equip(mob, night, spawn.kind == Kind.BOSS);
		}
	}

	private static void multiply(Mob mob, Holder<Attribute> attribute, double factor) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(instance.getBaseValue() * factor);
		}
	}

	/** Maior nível cujo limite de noite já foi atingido (-1 se nenhum). */
	private static int tierFor(int[] thresholds, int night) {
		int tier = -1;
		for (int i = 0; i < thresholds.length; i++) {
			if (night >= thresholds[i]) {
				tier = i;
			}
		}
		return tier;
	}

	private static void equip(Mob mob, int night, boolean boss) {
		EntityType<?> type = mob.getType();
		boolean zombieLike = type == EntityType.ZOMBIE || type == EntityType.HUSK;
		boolean skeletonLike = type == EntityType.SKELETON || type == EntityType.STRAY;
		if (!zombieLike && !skeletonLike) {
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int bonus = boss ? 1 : 0;

		int swordTier = tierFor(SWORD_NIGHTS, night);
		if (zombieLike && (swordTier >= 0 || boss)) {
			int tier = Math.min(4, Math.max(0, swordTier) + bonus);
			double swordChance = boss ? 1.0 : Math.min(0.8, 0.3 + night * 0.005);
			if (random.nextDouble() < swordChance) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SWORDS[tier]));
			}
		}

		int armorTier = tierFor(ARMOR_NIGHTS, night);
		if (armorTier >= 0 || boss) {
			int tier = Math.min(4, Math.max(0, armorTier) + bonus);
			double chance = boss ? 1.0 : Math.min(0.8, 0.15 + night * 0.005);
			giveArmor(mob, EquipmentSlot.HEAD, HELMETS[tier], chance, random);
			giveArmor(mob, EquipmentSlot.CHEST, CHESTPLATES[tier], chance, random);
			giveArmor(mob, EquipmentSlot.LEGS, LEGGINGS[tier], chance, random);
			giveArmor(mob, EquipmentSlot.FEET, BOOTS[tier], chance, random);
		}
	}

	private static void giveArmor(Mob mob, EquipmentSlot slot, Item item, double chance, ThreadLocalRandom random) {
		if (random.nextDouble() < chance) {
			mob.setItemSlot(slot, new ItemStack(item));
		}
	}

	// ------------------------------------------------------------------ zumbi-bomba

	/** Raio da explosão do zumbi-bomba (TNT comum = 4). Cresce bem devagar, até 5. */
	public static float tntRadius(int night) {
		return (float) Math.min(5.0, 3.0 + 0.02 * Math.max(0, night - TNT_A_PARTIR_DA_NOITE));
	}

	/**
	 * "É terreno natural?" Se o bloco contra o qual o zumbi-bomba bate NÃO for natural,
	 * ele conta como construção. É uma heurística: blocos naturais comuns ficam de fora
	 * para ele não explodir em qualquer morro.
	 */
	public static boolean isNaturalBlock(BlockState state) {
		return state.is(BlockTags.DIRT)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.SAND)
				|| state.is(BlockTags.LEAVES)
				|| state.is(BlockTags.LOGS)
				|| state.is(BlockTags.ICE)
				|| state.is(BlockTags.SNOW)
				|| state.is(Blocks.GRAVEL)
				|| state.is(Blocks.CLAY)
				|| state.is(Blocks.WATER)
				|| state.is(Blocks.SHORT_GRASS)
				|| state.is(Blocks.TALL_GRASS)
				|| state.is(Blocks.COBWEB);
	}

	// ------------------------------------------------------------------ tipos de ataque

	public static boolean isRanged(EntityType<?> type) {
		return type == EntityType.SKELETON || type == EntityType.STRAY
				|| type == EntityType.WITCH || type == EntityType.PILLAGER;
	}
}
