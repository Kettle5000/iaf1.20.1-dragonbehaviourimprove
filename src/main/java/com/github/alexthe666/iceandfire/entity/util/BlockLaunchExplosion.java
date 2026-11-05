package com.github.alexthe666.iceandfire.entity.util;

import com.github.alexthe666.iceandfire.block.IDragonProof;
import com.github.alexthe666.iceandfire.compat.EnhancedVisualsCompat;
import com.github.alexthe666.iceandfire.compat.ModCompatList;
import com.github.alexthe666.iceandfire.datagen.tags.IafBlockTags;
import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.github.alexthe666.iceandfire.entity.IafDragonDestructionManager;
import com.github.alexthe666.iceandfire.util.IafExplosionRays;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.Set;


public class BlockLaunchExplosion extends Explosion {
	protected final EntityDragonBase dragon;
    private final float size;
    private final Level level;
    private final BlockInteraction mode;
    public final ObjectArrayList<BlockPos> blocks = new ObjectArrayList<>();
	public final ObjectArrayList<Entity> entities = new ObjectArrayList<>();
	public final BlockPos center;
	/** When false, edge blocks are only scorched/frozen instead of being launched as falling blocks. */
	private boolean launchBlocks = true;

    public BlockLaunchExplosion(Level world, EntityDragonBase entity, double x, double y, double z, float size) {
        this(world, entity, x, y, z, size, BlockInteraction.DESTROY);
    }

    public BlockLaunchExplosion(Level world, EntityDragonBase entity, double x, double y, double z, float size, BlockInteraction mode) {
        this(world, entity, null, x, y, z, size, mode);
    }

    public BlockLaunchExplosion(Level world, EntityDragonBase entity, DamageSource source, double x, double y, double z, float size, BlockInteraction mode) {
        super(world, entity, source, null, x, y, z, size, false, mode);
        this.dragon = entity;
        this.level = world;
        this.size = size;
        this.mode = mode;
        this.center = BlockPos.containing(x, y, z).immutable();
    }

    public BlockLaunchExplosion setLaunchBlocks(boolean launchBlocks) {
        this.launchBlocks = launchBlocks;
        return this;
    }

    @Override
	public void explode() {
		level.gameEvent(dragon, GameEvent.EXPLODE, center);
		level.playSound(null, center, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F + this.size * 0.2f, 1.0F);
		Set<BlockPos> set = getExplosionBlocks(level, center, this.size, dragon);
		this.blocks.addAll(set);
		this.entities.addAll(level.getEntities(this.dragon, new AABB(center).inflate(this.size)));
		ForgeEventFactory.onExplosionDetonate(this.level, this, this.entities, this.size * 2f);
		if (ModCompatList.isEVon) {
			EnhancedVisualsCompat.explosion(this, this.entities);
		}
	}

    /**
     * Does the second part of the explosion (sound, particles, block changes)
     */
    @Override
    public void finalizeExplosion(boolean spawnParticles) {
        if (level.isClientSide()) {
            this.level.playLocalSound(center.getX(), center.getY(), center.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
        }

        boolean flag = this.mode != BlockInteraction.KEEP;
        if (spawnParticles) {
            if (!(this.size < 2.0F) && flag) {
                this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, center.getX(), center.getY(), center.getZ(), 1.0D, 0.0D, 0.0D);
            } else {
                this.level.addParticle(ParticleTypes.EXPLOSION, center.getX(), center.getY(), center.getZ(), 1.0D, 0.0D, 0.0D);
            }
        }

        if (flag) {
            for (BlockPos blockpos : this.blocks) {
                BlockState blockstate = this.level.getBlockState(blockpos);
                if (blockstate.isAir() || blockstate.hasBlockEntity()) {
                    continue;
                }
                int d = (int) this.size - blockpos.distManhattan(this.center);
                if (d > 0) {
                    // Inner blocks are destroyed without drops.
                    blockstate.onBlockExploded(this.level, blockpos, this);
                } else if (d == 0) {
                    // Edge blocks: scorch/freeze, then optionally launch.
                    if (dragon.getRandom().nextBoolean()) {
                        IafDragonDestructionManager.attackBlock(level, dragon, blockpos);
                    }
                    if (launchBlocks && dragon.getRandom().nextBoolean()) {
                        launchBlock(blockpos);
                    }
                }
            }
        }
    }

    private void launchBlock(BlockPos blockpos) {
        BlockState blockstate = this.level.getBlockState(blockpos);
        if (blockstate.isAir() || blockstate.hasBlockEntity()) {
            return;
        }
        this.level.getProfiler().push("explosion_blocks");
        blockstate.onBlockExploded(this.level, blockpos, this);
        // fall() places the entity at the block's centre and replaces the block.
        FallingBlockEntity fallingBlockEntity = FallingBlockEntity.fall(level, blockpos, blockstate);
        double angle = Math.PI * 2 * dragon.getRandom().nextDouble();
        double distance = 3 - (2 * dragon.getRandom().nextDouble());
        fallingBlockEntity.setDeltaMovement(
                distance * Math.sin(angle),
                1 + dragon.getRandom().nextDouble(),
                distance * Math.cos(angle)
        );
        fallingBlockEntity.dropItem = false;
        fallingBlockEntity.setInvulnerable(true);
        this.level.getProfiler().pop();
    }

	public static Set<BlockPos> getExplosionBlocks(Level level, BlockPos center, float radius, Entity source) {
		Set<BlockPos> affectedBlocks = Sets.newHashSet();

		if (radius <= 0.0F) {
			return affectedBlocks;
		}

		if (canExplosionDestroy(level, center, radius, 0, source)) {
			affectedBlocks.add(center);
		}

		final int maxSteps = (int) Math.ceil(radius);
		final float radius2 = radius * radius;
	    for (int[] dir : IafExplosionRays.RAYS) {
	        int dx = dir[0], dy = dir[1], dz = dir[2];
	        for (int k = 1; k <= maxSteps; k++) {
	            // Cheap distance² check (no sqrt)
	            int dist2 = (dx * k) * (dx * k) + (dy * k) * (dy * k) + (dz * k) * (dz * k);
	            if (dist2 > radius2) {
	                break;
	            }

	            BlockPos pos = center.offset(dx * k, dy * k, dz * k);

	            if (!canExplosionDestroy(level, pos, radius, k, source)) {
	                break;   // this ray is finished
	            }

	            affectedBlocks.add(pos);
	        }
	    }
		return affectedBlocks;
	}

	private static boolean canExplosionDestroy(Level level, BlockPos pos, float radius, int distance, Entity source) {
		BlockState state = level.getBlockState(pos);
		// Air passes because the random part keeps "hardness" above 0; it's skipped later.
		float hardness = state.getDestroySpeed(level, pos) + (level.getRandom().nextFloat() * 0.5f);
		float ratio = radius - distance;
		float threshold = (ratio * ratio);
		return hardness > 0.0F && hardness <= threshold
				&& !state.is(IafBlockTags.DRAGON_BLOCK_BREAK_BLACKLIST)
				&& state.getFluidState().isEmpty()
				&& !(state.getBlock() instanceof IDragonProof)
				&& (state.isAir() || DragonUtils.canDragonBreak(state, source));
	}

}
