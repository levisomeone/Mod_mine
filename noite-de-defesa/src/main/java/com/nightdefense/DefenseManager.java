package com.nightdefense;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


public final class DefenseManager {
	// --balance ajust--
	/** dia (0-23999) inicio (12700 = início da noite). */
	private static final long RAID_START_TIME = 12700L;
	private static final long RAID_LAST_START_TIME = 22000L;
	private static final double PLAYER_RANGE = 64.0;
	private static final double MIN_FLAG_DISTANCE = 64.0;

	private static DefenseManager instance;

	public static DefenseManager get() {
		return instance;
	}

	public static void start(MinecraftServer server) {
		instance = new DefenseManager(server);
		instance.load();
	}

	public static void stop() {
		if (instance != null) {
			instance.abortAllRaids();
			instance.save();
			instance = null;
		}
	}

	private static final class Flag {
		final BlockPos pos;
		int nights;
		long lastRaidDay = -1L;
		Raid raid;

		Flag(BlockPos pos) {
			this.pos = pos.immutable();
		}
	}

	private static final class Raid {
		int night;
		Deque<RaidComposer.Spawn> queue;
		final Set<UUID> alive = new HashSet<>();
		int total;
		int killed;
		long startTick;
		int pausedTicks;
		final java.util.Map<UUID, Integer> tntContact = new java.util.HashMap<>();
	}

	private record FlagDto(int x, int y, int z, int nights, long lastRaidDay) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<FlagDto>>() {
	}.getType();

	private final MinecraftServer server;
	private final List<Flag> flags = new ArrayList<>();
	private final Set<UUID> tracked = new HashSet<>();

	private DefenseManager(MinecraftServer server) {
		this.server = server;
	}

	public boolean isTracked(UUID uuid) {
		return tracked.contains(uuid);
	}


	private Path file() {
		return server.getWorldPath(LevelResource.ROOT).resolve("nightdefense_flags.json");
	}

	private void load() {
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			List<FlagDto> data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LIST_TYPE);
			if (data != null) {
				for (FlagDto dto : data) {
					Flag flag = new Flag(new BlockPos(dto.x(), dto.y(), dto.z()));
					flag.nights = dto.nights();
					flag.lastRaidDay = dto.lastRaidDay();
					flags.add(flag);
				}
			}
		} catch (IOException | RuntimeException e) {
			NightDefenseMod.LOGGER.error("Não foi possível ler {}", path, e);
		}
	}

	private void save() {
		List<FlagDto> data = new ArrayList<>();
		for (Flag flag : flags) {
			data.add(new FlagDto(flag.pos.getX(), flag.pos.getY(), flag.pos.getZ(), flag.nights, flag.lastRaidDay));
		}
		try {
			Files.writeString(file(), GSON.toJson(data, LIST_TYPE), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NightDefenseMod.LOGGER.error("Não foi possível salvar o progresso das bandeiras", e);
		}
	}

	public String tryRegister(ServerLevel level, BlockPos pos) {
		if (level != server.overworld()) {
			return "A Bandeira de Defesa só funciona no Overworld.";
		}
		for (Flag flag : flags) {
			if (flag.pos.equals(pos)) {
				return null;
			}
			if (flag.pos.distSqr(pos) < MIN_FLAG_DISTANCE * MIN_FLAG_DISTANCE) {
				return "Já existe uma Bandeira de Defesa a menos de " + (int) MIN_FLAG_DISTANCE + " blocos daqui.";
			}
		}
		flags.add(new Flag(pos));
		save();
		return null;
	}

	public void sendStatus(ServerLevel level, BlockPos pos, ServerPlayer player) {
		Flag flag = findFlag(pos);
		if (flag == null) {
			player.sendSystemMessage(Component.literal("§7Esta bandeira ainda não está registrada."));
			return;
		}
		player.sendSystemMessage(Component.literal("§6Bandeira de Defesa §7- noites defendidas: §e" + flag.nights
				+ " §7| próxima: noite §c" + (flag.nights + 1)));
		if (flag.raid != null) {
			player.sendSystemMessage(Component.literal("§cInvasão em andamento! §7Inimigos restantes: "
					+ (flag.raid.alive.size() + flag.raid.queue.size())));
		}
	}

	private Flag findFlag(BlockPos pos) {
		for (Flag flag : flags) {
			if (flag.pos.equals(pos)) {
				return flag;
			}
		}
		return null;
	}

	private Flag nearestFlag(BlockPos pos, double maxDistance) {
		Flag best = null;
		double bestDist = maxDistance * maxDistance;
		for (Flag flag : flags) {
			double dist = flag.pos.distSqr(pos);
			if (dist <= bestDist) {
				bestDist = dist;
				best = flag;
			}
		}
		return best;
	}
)

	public String describeFlags() {
		if (flags.isEmpty()) {
			return "Nenhuma bandeira registrada neste mundo.";
		}
		StringBuilder sb = new StringBuilder("Bandeiras:");
		for (Flag flag : flags) {
			sb.append("\n- ").append(flag.pos.toShortString())
					.append(" | noites defendidas: ").append(flag.nights)
					.append(flag.raid != null ? " | INVASÃO EM ANDAMENTO" : "");
		}
		return sb.toString();
	}

	public String forceStart(ServerLevel level, BlockPos near) {
		Flag flag = nearestFlag(near, 128.0);
		if (flag == null) {
			return "Nenhuma bandeira a menos de 128 blocos de você.";
		}
		if (flag.raid != null) {
			return "Já existe uma invasão acontecendo nessa bandeira.";
		}
		ServerLevel overworld = server.overworld();
		startRaid(overworld, flag, overworld.getDayTime() / 24000L);
		return "Invasão iniciada na bandeira em " + flag.pos.toShortString() + ".";
	}

	public String setNights(BlockPos near, int nights) {
		Flag flag = nearestFlag(near, 128.0);
		if (flag == null) {
			return "Nenhuma bandeira a menos de 128 blocos de você.";
		}
		flag.nights = nights;
		save();
		return "Noites defendidas da bandeira em " + flag.pos.toShortString() + " agora: " + nights + ".";
	}


	public void tick() {
		ServerLevel level = server.overworld();
		long gameTime = level.getGameTime();
		if (gameTime % 10L == 0L) {
			tickTntZombies(level);
		}
		if (gameTime % 20L != 0L) {
			return;
		}
		for (Flag flag : new ArrayList<>(flags)) {
			try {
				tickFlag(level, flag);
			} catch (RuntimeException e) {
				NightDefenseMod.LOGGER.error("Erro ao processar bandeira {}", flag.pos, e);
			}
		}
	}

	private void tickFlag(ServerLevel level, Flag flag) {
		if (!level.hasChunkAt(flag.pos)) {
			return;
		}
		if (!level.getBlockState(flag.pos).is(ModBlocks.DEFENSE_FLAG)) {
			if (flag.raid != null) {
				broadcast(level, flag.pos, "§c" + "A bandeira foi removida. A invasão terminou.");
				discardMobs(level, flag.raid);
			}
			flags.remove(flag);
			save();
			return;
		}

		long time = level.getDayTime();
		long day = time / 24000L;
		long timeOfDay = time % 24000L;

		if (flag.raid == null) {
			if (timeOfDay >= RAID_START_TIME && timeOfDay < RAID_LAST_START_TIME
					&& flag.lastRaidDay != day
					&& !playersNear(level, flag.pos, PLAYER_RANGE).isEmpty()) {
				startRaid(level, flag, day);
			}
			return;
		}
		tickRaid(level, flag, timeOfDay);
	}

	private void startRaid(ServerLevel level, Flag flag, long day) {
		Raid raid = new Raid();
		raid.night = flag.nights + 1;
		raid.queue = RaidComposer.compose(raid.night);
		raid.total = raid.queue.size();
		raid.startTick = level.getGameTime();
		flag.raid = raid;
		flag.lastRaidDay = day;
		save();
		broadcast(level, flag.pos, "§c§lNOITE " + raid.night + " §r§c- a horda está chegando! Sobreviva até o amanhecer!");
	}

	private void tickRaid(ServerLevel level, Flag flag, long timeOfDay) {
		Raid raid = flag.raid;
		List<ServerPlayer> players = playersNear(level, flag.pos, PLAYER_RANGE);


		if (timeOfDay < 12000L) {
			finishSuccess(level, flag, true);
			return;
		}
		if (players.isEmpty()) {
			raid.pausedTicks += 20;
			if (raid.pausedTicks > 20 * 180) {
				discardMobs(level, raid);
				flag.raid = null;
			}
			return;
		}
		raid.pausedTicks = 0;


		Iterator<UUID> it = raid.alive.iterator();
		while (it.hasNext()) {
			UUID id = it.next();
			Entity entity = level.getEntity(id);
			if (entity == null || !entity.isAlive()) {
				it.remove();
				tracked.remove(id);
				raid.killed++;
			}
		}
r).
		int maxAlive = Math.min(8 + raid.night / 10, 20);
		int spawnedNow = 0;
		while (!raid.queue.isEmpty() && raid.alive.size() < maxAlive && spawnedNow < 4) {
			RaidComposer.Spawn spawn = raid.queue.poll();
			if (!spawnMob(level, flag, raid, spawn)) {
				raid.total--; 
			}
			spawnedNow++;
		}

		Vec3 center = Vec3.atBottomCenterOf(flag.pos);
		for (UUID id : raid.alive) {
			if (!(level.getEntity(id) instanceof Mob mob)) {
				continue;
			}
			if (mob.getTarget() == null
					&& mob.distanceToSqr(center.x, center.y, center.z) > 3.0 * 3.0) {
				mob.getNavigation().moveTo(center.x, center.y, center.z, 1.0);
			}
		}

.
		int remaining = raid.alive.size() + raid.queue.size();
		String bar = "§6Noite " + raid.night + " §8| §eInimigos restantes: " + remaining;
		for (ServerPlayer player : players) {
			player.displayClientMessage(Component.literal(bar), true);
		}

		if (raid.queue.isEmpty() && raid.alive.isEmpty()) {
			finishSuccess(level, flag, false);
		}
	}


	private void tickTntZombies(ServerLevel level) {
		for (Flag flag : flags) {
			Raid raid = flag.raid;
			if (raid == null) {
				continue;
			}
			for (UUID id : new ArrayList<>(raid.alive)) {
				if (!(level.getEntity(id) instanceof Mob mob) || !mob.getTags().contains(RaidComposer.TNT_TAG)) {
					continue;
				}
				if (!mob.horizontalCollision || !touchesConstruction(level, mob)) {
					raid.tntContact.remove(id);
					continue;
				}
				int contact = raid.tntContact.merge(id, 1, Integer::sum);
				if (contact >= 3) { // 3 verificações de 0,5 s
					raid.tntContact.remove(id);
					float radius = RaidComposer.tntRadius(raid.night);
					level.explode(mob, mob.getX(), mob.getY() + 0.5, mob.getZ(), radius,
							Level.ExplosionInteraction.MOB);
					mob.discard();
				}
			}
		}
	}

	private boolean touchesConstruction(ServerLevel level, Mob mob) {
		BlockPos feet = mob.blockPosition();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockState state = level.getBlockState(feet.relative(dir).above(dy));
				if (!state.isAir() && !RaidComposer.isNaturalBlock(state)) {
					return true;
				}
			}
		}
		return false;
	}



	private void finishSuccess(ServerLevel level, Flag flag, boolean atDawn) {
		Raid raid = flag.raid;
		discardMobs(level, raid);
		flag.raid = null;
		flag.nights = raid.night;
		save();

		broadcast(level, flag.pos, "§a§lNOITE " + raid.night + " DEFENDIDA! §r§aVocê sobreviveu"
				+ (atDawn ? " até o amanhecer." : " e a horda foi destruída.")
				+ " §7(" + raid.killed + " inimigos derrotados)");

		BlockPos dropPos = flag.pos.above();
		for (ItemStack stack : RewardTable.roll(raid.night)) {
			Block.popResource(level, dropPos, stack);
		}
		if (raid.night % 10 == 0) {
			broadcast(level, flag.pos, "§d★ Marco de " + raid.night + " noites! Recompensa extra!");
		}
		broadcast(level, flag.pos, "§7Próxima noite: §c" + (raid.night + 1)
				+ " §7- um pouco mais difícil.");
	}

	private void abortAllRaids() {
		ServerLevel level = server.overworld();
		for (Flag flag : flags) {
			if (flag.raid != null) {
				discardMobs(level, flag.raid);
				flag.raid = null;
			}
		}
	}

	private void discardMobs(ServerLevel level, Raid raid) {
		for (UUID id : raid.alive) {
			Entity entity = level.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
			tracked.remove(id);
		}
		raid.alive.clear();
	}


	private boolean spawnMob(ServerLevel level, Flag flag, Raid raid, RaidComposer.Spawn spawn) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 20.0 + random.nextDouble() * 10.0;
			int x = flag.pos.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = flag.pos.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, flag.pos.getY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos spawnPos = new BlockPos(x, y, z);
			if (!level.getBlockState(spawnPos.below()).getFluidState().isEmpty()) {
				continue; // não nascer na água
			}

			EntityType<? extends Mob> type = spawn.type();
			Mob mob = type.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				return false;
			}
			mob.setPos(x + 0.5, y, z + 0.5);
			if (!level.noCollision(mob)) {
				mob.discard();
				continue;
			}

			boolean zombieLike = type == EntityType.ZOMBIE || type == EntityType.HUSK;
			if (!zombieLike) {

				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.EVENT, null);
			}
			RaidComposer.prepare(mob, spawn, raid.night);


			raid.alive.add(mob.getUUID());
			tracked.add(mob.getUUID());
			level.addFreshEntity(mob);
			return true;
		}
		return false;
	}



	private List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double range) {
		List<ServerPlayer> result = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
					<= range * range) {
				result.add(player);
			}
		}
		return result;
	}

	private void broadcast(ServerLevel level, BlockPos pos, String message) {
		Component component = Component.literal(message);
		for (ServerPlayer player : playersNear(level, pos, 128.0)) {
			player.sendSystemMessage(component);
		}
	}
}
