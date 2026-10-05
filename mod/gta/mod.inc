// Minecraft Crossover, GTA side: everything for the elytra flight is driven from Minecraft through the kit's bricks
// and rehan's flight ops, so the GTA plugin only needs the three hooks.

	void mod_connected(Ped player)
	{
		(void)player;
	}

	void mod_tick(Ped player)
	{
		(void)player;
	}

	bool mod_op(Ped player, const std::string &op, const std::string &m)
	{
		(void)player; (void)op; (void)m;
		return false;
	}
