package sigf.xo.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import sigf.xo.Xo;

/**
 * The Minecraft life around the flight: floating grass islands with trees, fences and creatures beside the route (the
 * creepers light their fuses when Steve flies close and blow up in the sky as GTA explosions and fireworks), block
 * pillars along the avenue, and after the landing a mob invasion of the street with a house built block by block.
 */
final class Scene {
	private static final class Island {
		final Vec3 center;
		boolean populated;
		boolean lit;

		Island(final Vec3 center) {
			this.center = center;
		}
	}

	private static final List<Island> islands = new ArrayList<>();
	private static final List<BlockPos> pillars = new ArrayList<>();
	private static boolean pillarsBuilt;
	private static boolean invaded;

	private Scene() {
	}

	/** Builds the islands beside three points of the path (called once the path exists). */
	static void buildWorld(final List<Vec3> path, final int[] at, final double[] sides) {
		islands.clear();
		pillarsBuilt = false;
		invaded = false;
		ServerLevel level = Xo.level();
		for (int k = 0; k < at.length; k++) {
			int i = at[k];
			Vec3 dir = path.get(Math.min(path.size() - 1, i + 3)).subtract(path.get(Math.max(0, i - 3))).normalize();
			Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
			Vec3 c = path.get(i).add(side.scale(sides[k])).add(0, -11, 0);
			island(level, BlockPos.containing(c));
			islands.add(new Island(c));
		}
	}

	private static void put(final ServerLevel level, final int x, final int y, final int z, final Block b) {
		level.setBlockAndUpdate(new BlockPos(x, y, z), b.defaultBlockState());
	}

	/** An 11x11 grass island with a dirt underside, a fence round the edge and an oak tree. */
	private static void island(final ServerLevel level, final BlockPos o) {
		final int r = 7;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				put(level, o.getX() + dx, o.getY(), o.getZ() + dz, Blocks.GRASS_BLOCK);
				if (m <= r - 1) {
					put(level, o.getX() + dx, o.getY() - 1, o.getZ() + dz, Blocks.DIRT);
				}

				if (m <= r - 3) {
					put(level, o.getX() + dx, o.getY() - 2, o.getZ() + dz, Blocks.DIRT);
				}

				if (m <= r - 5) {
					put(level, o.getX() + dx, o.getY() - 3, o.getZ() + dz, Blocks.STONE);
				}

				if (m <= 1) {
					put(level, o.getX() + dx, o.getY() - 4, o.getZ() + dz, Blocks.STONE);
				}

				if (m == r) {
					put(level, o.getX() + dx, o.getY() + 1, o.getZ() + dz, Blocks.OAK_FENCE);
				}
			}
		}

		// an oak tree in one corner
		int tx = o.getX() - 4, tz = o.getZ() - 4;
		for (int h = 1; h <= 8; h++) {
			put(level, tx, o.getY() + h, tz, Blocks.OAK_LOG);
		}

		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = 5; dy <= 11; dy++) {
					if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 8) <= 4 && !(dx == 0 && dz == 0 && dy < 9)) {
						put(level, tx + dx, o.getY() + dy, tz + dz, Blocks.OAK_LEAVES);
					}
				}
			}
		}

		// a little hut on the other corner
		for (int x = 0; x <= 3; x++) {
			for (int z = 0; z <= 3; z++) {
				for (int y = 1; y <= 3; y++) {
					boolean wall = x == 0 || x == 3 || z == 0 || z == 3;
					if (wall && !(z == 0 && x == 1 && y < 3)) {
						put(level, o.getX() + 2 + x, o.getY() + y, o.getZ() + 2 + z, y == 2 && x == 3 ? Blocks.GLASS : Blocks.OAK_PLANKS);
					}
				}

				put(level, o.getX() + 2 + x, o.getY() + 4, o.getZ() + 2 + z, Blocks.BRICKS);
			}
		}
	}

	/** Every tick of the flight: wakes up the islands Steve flies near. */
	static void onFlight(final ServerPlayer p) {
		Vec3 pos = p.position();
		for (Island is : islands) {
			double d = is.center.distanceTo(pos);
			if (!is.populated && d < 150) {
				is.populated = true;
				populate(is);
			}

			if (!is.lit && is.populated && d < 42) {
				is.lit = true;
				Xo.sound(SoundEvents.CREEPER_PRIMED, is.center, 2f, 1f);
				Xo.command(String.format(java.util.Locale.ROOT,
					"execute positioned %.1f %.1f %.1f run data merge entity @e[type=creeper,distance=..9] {Fuse:22,ignited:1b}", is.center.x, is.center.y, is.center.z));
				Xo.after(1.2, () -> {
					Xo.ptfx("proj_indep_firework_v2", "scr_firework_indep_ring_burst_rwb", is.center.add(0, 5, 0), 4.0);
					Xo.boom(is.center.add(0, 3, 0), 38, 1.0);
				});
			}
		}
	}

	private static void populate(final Island is) {
		Vec3 top = is.center.add(0.5, 1, 0.5);
		mob(EntityTypes.CREEPER, top.add(1.5, 0, 0));
		mob(EntityTypes.CREEPER, top.add(-1.5, 0, 2.5));
		mob(EntityTypes.CREEPER, top.add(0.5, 0, -2.5));
		mob(EntityTypes.PIG, top.add(-1, 0, -1));
		mob(EntityTypes.HUSK, top.add(-2, 0, 0));
	}

	private static void mob(final EntityType<? extends Mob> type, final Vec3 at) {
		Mob m = Xo.spawn(type, at);
		if (m != null) {
			m.setPersistenceRequired();
		}
	}

	/** Pairs of red and white block pillars either side of the avenue, ahead of Steve. */
	static void buildPillars() {
		if (pillarsBuilt) {
			return;
		}

		pillarsBuilt = true;
		ServerLevel level = Xo.level();
		for (int i = 0; i < 5; i++) {
			double y = -1005 - i * 30;
			for (double x : new double[] {190, 230}) {
				Vec3 b = Xo.fromGta(x, y, 28.5);
				BlockPos base = BlockPos.containing(b);
				for (int h = 0; h < 26; h++) {
					Block block = (h / 2) % 2 == 0 ? Blocks.RED_MUSHROOM_BLOCK : Blocks.SNOW_BLOCK;
					if (h >= 24) {
						block = Blocks.GLOWSTONE;
					}

					for (int w = 0; w < 2; w++) {
						for (int d = 0; d < 2; d++) {
							put(level, base.getX() + w, base.getY() + h, base.getZ() + d, block);
						}
					}
				}
			}
		}
	}

	/** Zombies, creepers and pigs on the avenue Steve is about to skim over. */
	static void avenueMobs() {
		Vec3 spot = Xo.fromGta(208, -1090, 29.5);
		double[] at = {spot.x, spot.y, spot.z};
		dev.rehan.passthrough.MobWar.spawn("zombie", 5, 2, 14, 180, 0, at);
		dev.rehan.passthrough.MobWar.spawn("creeper", 3, 2, 14, 180, 0, at);
		dev.rehan.passthrough.MobWar.spawn("pig", 5, 2, 12, 180, 0, at);
	}

	// ------------------------------------------------------------------ after the flight: the invasion

	/** Steve lands on the avenue; zombies, creepers and pigs pour into Los Santos, a house goes up, cars crash into it. */
	static void invasion(final boolean teleport) {
		if (invaded) {
			return;
		}

		invaded = true;
		if (teleport) {
			Xo.gta("drive", "on", 0);
			dev.rehan.passthrough.WorldBridge.glide(false, 1.0);
			Xo.after(0.4, () -> Xo.gta("teleport", "x", 208, "y", -1118, "z", 30.5, "h", 180, "pitch", 0));
			Xo.after(1.2, () -> Xo.gta("teleport", "x", 208, "y", -1118, "z", 30.5, "h", 180, "pitch", 0));
		}

		Xo.after(teleport ? 2.2 : 0.5, () -> {
			Xo.title("MOB INVASION", "Minecraft mobs vs Los Santos", 3);
			for (int i = 0; i < 4; i++) {
				Xo.gta("xo.ped", "id", "walker" + i, "model", i % 2 == 0 ? "a_m_y_hipster_01" : "a_f_y_tourist_01", "ahead", 18 + i * 4, "task", "wander");
			}
		});
		Xo.after(4.5, () -> dev.rehan.passthrough.MobWar.spawn("zombie", 8, 10, 26, 35, 0, null));
		Xo.after(5.5, () -> dev.rehan.passthrough.MobWar.spawn("creeper", 5, 10, 24, 40, 0, null));
		Xo.after(6.5, () -> dev.rehan.passthrough.MobWar.spawn("pig", 6, 5, 12, 70, 0, null));
		Xo.after(8.5, Scene::house);
		Xo.after(15, () -> dev.rehan.passthrough.MobWar.spawn("skeleton", 4, 12, 22, 40, 0, null));
		Xo.after(17, Scene::carCrash);
		Xo.after(20, () -> dev.rehan.passthrough.MobWar.spawn("creeper", 3, 10, 22, 50, 0, null));
	}

	private static BlockPos groundAbove(final ServerLevel level, final BlockPos column) {
		for (int dy = 6; dy >= -8; dy--) {
			BlockPos below = column.offset(0, dy - 1, 0);
			if (!level.getBlockState(below).isAir() && level.getBlockState(below.above()).isAir()) {
				return below.above();
			}
		}

		return null;
	}

	/** A small house 12 blocks ahead of Steve, placed block by block with the wooden sound. */
	private static void house() {
		Vec3 g = Xo.gtaPlayer();
		if (g == null) {
			return;
		}

		ServerLevel level = Xo.level();
		BlockPos ground = groundAbove(level, BlockPos.containing(g.x, g.y, g.z + 12));
		if (ground == null) {
			ground = BlockPos.containing(g.x, g.y, g.z + 12);
		}

		List<Runnable> steps = new ArrayList<>();
		final BlockPos o = ground;
		for (int y = 0; y < 4; y++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = 0; z <= 4; z++) {
					boolean wall = x == -3 || x == 3 || z == 0 || z == 4;
					if (!wall) {
						continue;
					}

					boolean door = z == 0 && x == 0 && y < 2;
					boolean window = y == 2 && (z == 0 || z == 4) && Math.abs(x) == 1 || y == 1 && (x == -3 || x == 3) && z == 2;
					if (door) {
						continue;
					}

					Block b = window ? Blocks.GLASS : (y == 0 ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS);
					final BlockPos pos = o.offset(x, y, z);
					steps.add(() -> level.setBlockAndUpdate(pos, b.defaultBlockState()));
				}
			}
		}

		for (int x = -4; x <= 4; x++) {
			for (int z = -1; z <= 5; z++) {
				final BlockPos pos = o.offset(x, 4, z);
				steps.add(() -> level.setBlockAndUpdate(pos, Blocks.BRICKS.defaultBlockState()));
			}
		}

		for (int i = 0; i < steps.size(); i += 4) {
			final int from = i;
			Xo.after(i / 4 * 0.05, () -> {
				for (int k = from; k < Math.min(steps.size(), from + 4); k++) {
					steps.get(k).run();
				}

				Xo.sound(SoundEvents.WOOD_PLACE, Vec3.atCenterOf(o), 1f, 0.9f + (from % 8) * 0.03f);
			});
		}
	}

	/** GTA traffic ploughs into the block house. */
	private static void carCrash() {
		Vec3 g = Xo.gtaPlayer();
		if (g == null) {
			return;
		}

		double[] gt = Xo.toGta(g);
		for (int i = 0; i < 2; i++) {
			// heading 180 = south, the house stands to the south of Steve: cars come from the north past him
			Xo.car("crash" + i, i == 0 ? "sultan" : "taxi", Xo.fromGta(gt[0] + 4 - i * 8, gt[1] + 6, gt[2] + 0.6), 180f, 22);
		}
	}
}
