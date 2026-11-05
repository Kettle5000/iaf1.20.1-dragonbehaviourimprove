package com.github.alexthe666.iceandfire.entity.explosion;

import com.github.alexthe666.iceandfire.compat.EnhancedVisualsCompat;
import com.github.alexthe666.iceandfire.compat.ModCompatList;
import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * Picks the blocks and entities hit by one point of a dragon's breath stream.
 * Damage and block changes are applied by IafDragonDestructionManager.
 * <p>
 * Breath calls this for many points along the stream every tick, so it deliberately plays no
 * sound, sends no game event and fires no Forge explosion event (the breath isn't an explosion,
 * and upstream never fired them). Enhanced Visuals is throttled to once every 10 ticks per dragon.
 */
public class BreathExplosion extends Explosion {
	private static final int VISUALS_INTERVAL = 10;

	protected final EntityDragonBase dragon;
	protected final Level level;
	private final int blockRadius;
	private final double entityRadius;
	public final ObjectArrayList<BlockPos> blocks = new ObjectArrayList<>();
	public final ObjectArrayList<Entity> entities = new ObjectArrayList<>();

	public BreathExplosion(Level world, EntityDragonBase dragon, double x, double y, double z, int blockRadius, double entityRadius) {
		super(world, dragon, null, null, x, y, z, blockRadius, true, BlockInteraction.KEEP);
		this.dragon = dragon;
		this.level = world;
		this.blockRadius = blockRadius;
		this.entityRadius = entityRadius;
	}

	@Override
	public void explode() {
		BlockPos center = BlockPos.containing(this.getPosition());
		Set<BlockPos> set = Sets.newHashSet();
		final int radius = this.blockRadius;
		final int radius2 = radius * radius;
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					int d = x * x + y * y + z * z;
					if (d > radius2) {
						continue;
					}
					BlockPos newpos = center.offset(x, y, z);
					BlockState state = level.getBlockState(newpos);
					// Skip air and unbreakable blocks. Everything else goes to attackBlock, which
					// applies the dragon-break rules and the fire/ice/lightning transformations.
					if (state.isAir() || state.getDestroySpeed(level, newpos) < 0) {
						continue;
					}
					if (d <= 1 || level.random.nextFloat() * radius2 > (float) d) {
						set.add(newpos);
					}
				}
			}
		}
		this.blocks.addAll(set);
		this.entities.addAll(level.getEntities(this.dragon, new AABB(center).inflate(this.entityRadius)));

		if (ModCompatList.isEVon && dragon.lastBreathVisualsTick + VISUALS_INTERVAL <= dragon.tickCount) {
			dragon.lastBreathVisualsTick = dragon.tickCount;
			EnhancedVisualsCompat.explosion(this, this.entities);
		}
	}

	@Override
	public void finalizeExplosion(boolean spawnParticles) {
	}
}
