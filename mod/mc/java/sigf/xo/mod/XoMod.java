package sigf.xo.mod;

import net.fabricmc.api.ModInitializer;
import sigf.xo.Xo;

/** Minecraft Crossover: Steve's elytra flight over Los Santos (see Flight). */
public final class XoMod implements ModInitializer {
	@Override
	public void onInitialize() {
		Flight.init();
	}
}
