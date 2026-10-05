package sigf.xo.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import sigf.xo.Xo;

/**
 * Elytra flight over Los Santos. Steve starts on the Maze Bank roof, runs off the edge and glides along a smooth path
 * between the towers, through glowstone rings that each fire a firework rocket, then dives into the avenue south of
 * Legion Square, skims the traffic and climbs out. The pilot (this class) steers with eased turns, like a mouse.
 * Coordinates below are GTA's (x, y, z up); heading 0 = north, 90 = west.
 */
final class Flight {
	/** Roof start: x, y, z, heading. */
	private static final double[] START = {-68.5, -820.4, 326.6, 262.0};
	/** Path control points: x, y, z (GTA), ring radius (0 = no ring), boost (1 = rocket as Steve flies through). */
	private static final double[][] PATH = {
		{-68, -820, 326, 0, 0},
		{-32, -836, 306, 0, 0},
		{5, -858, 262, 13, 1},
		{50, -885, 214, 13, 1},
		{105, -912, 176, 13, 1},
		{160, -945, 148, 13, 0},
		{196, -985, 100, 13, 1},
		{210, -1030, 58, 13, 1},
		{214, -1062, 44, 9, 1},
		{206, -1105, 42, 9, 1},
		{212, -1150, 60, 13, 1},
		{207, -1200, 110, 0, 0},
		{200, -1260, 165, 0, 0},
	};
	private static final double STEP = 4.0;
	/** Blocks per tick a rocket pushes Steve up to (2.5 = 50 m/s). */
	private static final double BOOST_SPEED = 2.5;

	private static final List<Vec3> path = new ArrayList<>();
	private static final List<int[]> rings = new ArrayList<>(); // dense index, boost
	private static final java.util.Set<BlockPos> built = new java.util.HashSet<>();
	private static boolean armed;
	private static boolean flying;
	private static int at;
	private static int nextRing;
	private static float lookYaw, lookPitch;
	private static int sinceLook;
	private static int boostTicks;
	private static boolean traffic;
	private static int finale;

	private Flight() {
	}

	static void init() {
		Xo.onLink(Flight::stage);
		Xo.every(0.05, Flight::tick);
		Xo.every(1.5, Flight::ensureRoof);
		Xo.demo(0, Flight::go);
		Xo.demo(30, () -> Scene.invasion(true));
	}

	/** Until the flight starts, keeps checking that GTA's player really stands on the roof, and puts Steve back when not. */
	private static void ensureRoof() {
		Vec3 g = Xo.gtaPlayer();
		if (!staged || armed || g == null) {
			return;
		}

		double[] gta = Xo.toGta(g);
		if (Math.abs(gta[0] - START[0]) > 12 || Math.abs(gta[1] - START[1]) > 12 || Math.abs(gta[2] - START[2]) > 8) {
			Xo.log("flight: Steve is not on the roof, placing him");
			Xo.gta("drive", "on", 0);
			dev.rehan.passthrough.WorldBridge.glide(false, 1.0);
			Xo.gta("teleport", "x", START[0], "y", START[1], "z", START[2], "h", START[3], "pitch", 0);
			Xo.gta("armdrive", "h", START[3], "p", -6, "speed", 1.3, "drop", 0.8, "hold", 2500, "dist", 3.9, "height", 0.75);
		}
	}

	private static boolean staged;

	/** Steve on the roof, rings built along the route, camera behind him. */
	static void stage() {
		flying = false;
		armed = false;
		staged = true;
		Xo.gta("drive", "on", 0);
		dev.rehan.passthrough.WorldBridge.glide(false, 1.0);
		Xo.gta("time", "h", 11, "m", 30);
		Xo.gta("weather", "w", "EXTRASUNNY");
		Xo.gta("teleport", "x", START[0], "y", START[1], "z", START[2], "h", START[3], "pitch", 0);
		Xo.gta("armdrive", "h", START[3], "p", -6, "speed", 1.3, "drop", 0.8, "hold", 2500, "dist", 3.9, "height", 0.75);
		Xo.after(3, Flight::buildPath);
	}

	/** The smooth path (Catmull-Rom through the control points, a point every STEP metres) and a ring at each ring point. */
	private static void buildPath() {
		ServerLevel level = Xo.level();
		ServerPlayer p = Xo.player();
		if (p != null) {
			p.getAbilities().invulnerable = true;
		}

		for (BlockPos pos : built) {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}

		built.clear();
		path.clear();
		rings.clear();
		int n = PATH.length;
		List<double[]> ringAt = new ArrayList<>();
		int[] ctrlAt = new int[n];
		for (int i = 0; i < n - 1; i++) {
			Vec3 p0 = control(i - 1), p1 = control(i), p2 = control(i + 1), p3 = control(i + 2);
			int steps = Math.max(2, (int) Math.round(p1.distanceTo(p2) / STEP));
			for (int s = 0; s < steps; s++) {
				double t = (double) s / steps;
				if (s == 0) {
					ctrlAt[i] = path.size();
				}

				if (s == 0 && PATH[i][3] > 0) {
					ringAt.add(new double[] {path.size(), PATH[i][3]});
					rings.add(new int[] {path.size(), (int) PATH[i][4]});
				}

				path.add(spline(p0, p1, p2, p3, t));
			}
		}

		path.add(control(n - 1));
		for (double[] ring : ringAt) {
			int idx = (int) ring[0];
			Vec3 dir = path.get(Math.min(path.size() - 1, idx + 2)).subtract(path.get(Math.max(0, idx - 2))).normalize();
			buildRing(level, path.get(idx), dir, ring[1]);
			buildRing(level, path.get(idx).add(dir), dir, ring[1] + 1);
		}

		Scene.buildWorld(path, new int[] {ctrlAt[2], ctrlAt[4], ctrlAt[6]}, new double[] {-17, 19, -17});
	}

	private static Vec3 control(final int i) {
		double[] c = PATH[Math.max(0, Math.min(PATH.length - 1, i))];
		return Xo.fromGta(c[0], c[1], c[2]);
	}

	private static Vec3 spline(final Vec3 p0, final Vec3 p1, final Vec3 p2, final Vec3 p3, final double t) {
		double t2 = t * t, t3 = t2 * t;
		return p0.scale(-0.5 * t3 + t2 - 0.5 * t).add(p1.scale(1.5 * t3 - 2.5 * t2 + 1)).add(p2.scale(-1.5 * t3 + 2 * t2 + 0.5 * t))
			.add(p3.scale(0.5 * t3 - 0.5 * t2));
	}

	/** A ring of gold and glowstone blocks standing across the flight direction. */
	private static void buildRing(final ServerLevel level, final Vec3 c, final Vec3 dir, final double r) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
		Vec3 up = side.cross(dir).normalize();
		int steps = (int) (r * 8);
		for (int s = 0; s < steps; s++) {
			double a = Math.PI * 2 * s / steps;
			BlockPos pos = BlockPos.containing(c.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)));
			if (built.contains(pos)) {
				continue;
			}

			boolean gold = (s * 8 / steps) % 2 == 0;
			level.setBlockAndUpdate(pos, (gold ? Blocks.GOLD_BLOCK : Blocks.GLOWSTONE).defaultBlockState());
			built.add(pos);
		}
	}

	/** Steve runs off the roof; the pilot takes over when the elytra opens. */
	static void go() {
		Xo.title("ELYTRA FLIGHT", "Minecraft wings over Los Santos", 3);
		Xo.gta("armdrive", "h", START[3], "p", -6, "speed", 1.3, "drop", 0.8, "hold", 2500, "dist", 3.9, "height", 0.75);
		double h = Math.toRadians(START[3]);
		Xo.gta("walk", "x", START[0] - Math.sin(h) * 40, "y", START[1] + Math.cos(h) * 40, "z", START[2], "speed", 2.0, "timeout", 6000);
		staged = false;
		flying = false;
		at = 0;
		nextRing = 0;
		traffic = false;
		finale = 0;
		armed = true;
	}

	private static void tick() {
		ServerPlayer p = Xo.player();
		if (p == null || !armed || path.isEmpty()) {
			return;
		}

		if (!flying) {
			if (p.isFallFlying()) {
				flying = true;
				lookYaw = p.getYRot();
				lookPitch = p.getXRot();
				Xo.log("flight: elytra open");
				// the roof's edge hides Steve at first: the camera starts high and settles behind him
				Xo.gta("drive", "on", 1, "dist", 4.6, "height", 2.6);
				Xo.after(1.0, () -> Xo.gta("drive", "on", 1, "dist", 4.3, "height", 1.6));
				Xo.after(2.2, () -> Xo.gta("drive", "on", 1, "dist", 3.9, "height", 0.75));
			}

			return;
		}

		if (!p.isFallFlying()) {
			Xo.log("flight: landed");
			flying = false;
			armed = false;
			return;
		}

		Vec3 pos = p.position();
		Scene.onFlight(p);
		// progress: the nearest path point ahead
		double best = Double.MAX_VALUE;
		for (int i = at; i < Math.min(path.size(), at + 30); i++) {
			double d = path.get(i).distanceToSqr(pos);
			if (d < best) {
				best = d;
				at = i;
			}
		}

		if (nextRing < rings.size() && at >= rings.get(nextRing)[0] - 1) {
			int[] ring = rings.get(nextRing++);
			Vec3 c = path.get(ring[0]);
			Xo.particles(ParticleTypes.FIREWORK, c, 60, 5);
			Xo.boom(c, 38, 1.0);
			Xo.ptfx("proj_indep_firework_v2", "scr_firework_indep_ring_burst_rwb", c, 3.0);
			if (ring[1] > 0) {
				boost(p);
			}

			if (nextRing == rings.size()) {
				finale = 1;
				Xo.after(1.3, () -> Scene.invasion(true));
			}
		}

		if (!traffic && at >= 60) {
			traffic = true;
			Scene.buildPillars();
			Xo.after(2.0, Scene::avenueMobs);
			spawnTraffic();
		}

		if (finale > 0 && ++finale % 24 == 0 && finale < 80) {
			boost(p);
		}

		// aim at a carrot ahead on the path, further when fast; turns are rate-limited like a hand on a mouse
		int ahead = Math.min(path.size() - 1, at + 4 + (int) (p.getDeltaMovement().length() * 2));
		Vec3 want = path.get(ahead).subtract(pos);
		double yaw = Math.toDegrees(Math.atan2(-want.x, want.z));
		double pitch = -Math.toDegrees(Math.atan2(want.y, Math.hypot(want.x, want.z)));
		if (at >= path.size() - 5) {
			// past the last point: hold the heading and settle into a long, gentle glide down
			yaw = lookYaw;
			pitch = 8;
		}

		lookYaw += clamp(wrap(yaw - lookYaw), 3.0f);
		lookPitch += clamp((float) (Math.max(-22, Math.min(50, pitch)) - lookPitch), 2.0f);
		if (++sinceLook >= 2) {
			sinceLook = 0;
			Xo.gta("flylook", "h", Xo.heading(lookYaw), "p", -lookPitch);
		}

		if (boostTicks > 0) {
			boostTicks--;
			// the rocket's thrust: the client is told to speed up along where Steve looks (up to BOOST_SPEED blocks per tick)
			double sp = Math.min(BOOST_SPEED, p.getDeltaMovement().length() + 0.15);
			p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(), p.getLookAngle().scale(sp)));
			Vec3 back = pos.subtract(p.getLookAngle().scale(1.3)).add(0, 0.6, 0);
			Xo.particles(ParticleTypes.FIREWORK, back, 4, 0.12);
			Xo.particles(ParticleTypes.FLAME, back, 3, 0.1);
			Xo.particles(ParticleTypes.CAMPFIRE_COSY_SMOKE, back, 1, 0.1);
		}
	}

	/** Cars on the avenue ahead, coming at Steve, so the low pass skims real traffic. */
	private static void spawnTraffic() {
		String[] models = {"taxi", "adder", "bus", "sultan", "buffalo", "tailgater", "futo", "rocoto"};
		double[] lanes = {203, 208, 213, 217};
		for (int i = 0; i < 9; i++) {
			double y = -1035 - i * 16;
			double x = lanes[i % lanes.length] + ((i * 7) % 3 - 1);
			Xo.car("traffic" + i, models[i % models.length], Xo.fromGta(x, y, 29.6), 0f, 11 + (i % 3) * 3);
		}
	}

	private static void boost(final ServerPlayer p) {
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(2, List.of()));
		Xo.level().addFreshEntity(new FireworkRocketEntity(Xo.level(), rocket, p));
		Xo.sound(SoundEvents.FIREWORK_ROCKET_LAUNCH, p.position(), 1.5f, 1f);
		boostTicks = 40;
	}

	private static float wrap(final double d) {
		double x = d % 360;
		if (x > 180) {
			x -= 360;
		} else if (x < -180) {
			x += 360;
		}

		return (float) x;
	}

	private static float clamp(final float v, final float max) {
		return Math.max(-max, Math.min(max, v));
	}
}
