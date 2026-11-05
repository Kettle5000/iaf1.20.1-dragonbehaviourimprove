package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.iceandfire.IafConfig;
import com.github.alexthe666.iceandfire.api.event.DragonFireDamageWorldEvent;
import com.github.alexthe666.iceandfire.block.*;
import com.github.alexthe666.iceandfire.entity.explosion.BreathExplosion;
import com.github.alexthe666.iceandfire.entity.props.EntityDataProvider;
import com.github.alexthe666.iceandfire.entity.tile.TileEntityDragonforgeInput;
import com.github.alexthe666.iceandfire.entity.util.BlockLaunchExplosion;
import com.github.alexthe666.iceandfire.entity.util.DragonUtils;
import com.github.alexthe666.iceandfire.misc.IafDamageRegistry;
import com.github.alexthe666.iceandfire.integration.LycanitesCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

public class IafDragonDestructionManager {
    public static void destroyAreaBreath(final Level level, final BlockPos center, final EntityDragonBase dragon) {
        if (MinecraftForge.EVENT_BUS
                .post(new DragonFireDamageWorldEvent(dragon, center.getX(), center.getY(), center.getZ()))) {
            return;
        }

        int statusDuration;
        float damageScale;

        DragonForgeType forgeType;
        if (dragon.dragonType == DragonType.FIRE) {
            statusDuration = 5 + dragon.getDragonStage() * 5;
            damageScale = (float) IafConfig.dragonAttackDamageFire;
            forgeType = DragonForgeType.FIRE;
        } else if (dragon.dragonType == DragonType.ICE) {
            statusDuration = 50 * dragon.getDragonStage();
            damageScale = (float) IafConfig.dragonAttackDamageIce;
            forgeType = DragonForgeType.ICE;
        } else if (dragon.dragonType == DragonType.LIGHTNING) {
            statusDuration = 3;
            damageScale = (float) IafConfig.dragonAttackDamageLightning;
            forgeType = DragonForgeType.LIGHTNING;
        } else {
            return;
        }

        // Same reach as before the rework: small dragons hit a 3x3x3 area, stage 4/5 a radius of 2/3.
        final int blockRadius = Math.max(1, dragon.getDragonStage() - 2);
        final double damageRadius = dragon.getDragonStage() <= 3 ? 3.5D : 2.5D + (blockRadius + 0.5D) * 1.2D;

        // Dragon forges light whether or not griefing is on. Their input block is far too hard to
        // ever be picked by the breath, so look for it directly.
        BlockPos.betweenClosedStream(center.offset(-blockRadius, -blockRadius, -blockRadius),
                center.offset(blockRadius, blockRadius, blockRadius)).forEach(position -> {
            if (level.getBlockEntity(position) instanceof TileEntityDragonforgeInput forge) {
                forge.onHitWithFlame(forgeType);
            }
        });

        BreathExplosion explosion = new BreathExplosion(level, dragon, center.getX(), center.getY(), center.getZ(), blockRadius, damageRadius);
        explosion.explode();
        if (ForgeEventFactory.getMobGriefingEvent(level, dragon) && DragonUtils.canGrief(dragon)) {
            explosion.blocks.forEach(position -> {
                if (!(level.getBlockEntity(position) instanceof TileEntityDragonforgeInput)) {
                    attackBlock(level, dragon, position);
                }
            });
        }
        DamageSource damageSource = getDamageSource(dragon);
        float stageDamage = dragon.getDragonStage() * damageScale;

		explosion.entities.forEach(target -> {
			if (target instanceof LivingEntity living && !DragonUtils.onSameTeam(dragon, living) && !dragon.is(living) && dragon.hasLineOfSight(living)) {
				if (dragon instanceof EntityShivaxiDragon shivaxi) {
					damageShivaxiBreathTarget(living, shivaxi);
				} else {
					if (living.hurt(damageSource, stageDamage)) {
						applyDragonEffect(living, dragon.dragonType, statusDuration, dragon, stageDamage);
					}
				}
				if (living instanceof Player player) {
					DragonUtils.fillBottleWithDragonBreath(player, dragon.dragonType);
				}
			}
		});
	}

    /**
     * Recreates the small elemental explosion produced by the throwable dragon-breath bottles
     * from the 1.12.2 RLCraft build.  It deliberately uses the modern block transformations and
     * block-protection checks so claims, dragon-proof blocks and the current blacklist continue
     * to work.
     */
    public static void destroyAreaBreathItem(final Level level, final BlockPos center, final DragonType type,
                                             final Entity projectile, @Nullable final Entity owner) {
        if (type != DragonType.FIRE && type != DragonType.ICE && type != DragonType.LIGHTNING) {
            return;
        }

        final Entity source = owner == null ? projectile : owner;
        final int radius = 2;
        final double radiusSquared = radius * radius;
        final DragonForgeType forgeType = type == DragonType.FIRE ? DragonForgeType.FIRE
                : type == DragonType.ICE ? DragonForgeType.ICE : DragonForgeType.LIGHTNING;

        if (ForgeEventFactory.getMobGriefingEvent(level, source)) {
            BlockPos.betweenClosedStream(center.offset(-radius, -radius, -radius),
                            center.offset(radius, radius, radius))
                    .filter(position -> center.distSqr(position) <= radiusSquared)
                    .forEach(position -> {
                        if (level.getBlockEntity(position) instanceof TileEntityDragonforgeInput forge) {
                            forge.onHitWithFlame(forgeType);
                        } else {
                            attackBlock(level, type, source, position, level.getBlockState(position));
                        }
                    });
        }

        final double damageRadius = radius * 2.0D;
        final Vec3 explosionCenter = Vec3.atCenterOf(center);
        final DamageSource damageSource = getDamageSource(type, projectile, owner);

        level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(center).inflate(damageRadius),
                        target -> !target.is(source))
                .forEach(target -> {
                    double normalizedDistance = Math.sqrt(target.distanceToSqr(explosionCenter)) / damageRadius;
                    if (normalizedDistance > 1.0D) {
                        return;
                    }

                    double x = target.getX() - explosionCenter.x;
                    double y = target.getEyeY() - explosionCenter.y;
                    double z = target.getZ() - explosionCenter.z;
                    double length = Math.sqrt(x * x + y * y + z * z);
                    if (length == 0.0D) {
                        return;
                    }

                    double exposure = Explosion.getSeenPercent(explosionCenter, target);
                    double impact = (1.0D - normalizedDistance) * exposure;
                    float damage = (float) ((int) (((impact * impact + impact) / 2.0D)
                            * 7.0D * damageRadius + 1.0D)) / 3.0F;
                    if (damage > 0.0F && target.hurt(damageSource, damage)) {
                        int duration = type == DragonType.FIRE ? 5 : type == DragonType.ICE ? 200 : 3;
                        applyDragonEffect(target, type, duration, source, damage);
                    }

                    x /= length;
                    y /= length;
                    z /= length;
                    target.setDeltaMovement(target.getDeltaMovement().add(x * impact, y * impact, z * impact));
                });
    }

    public static void destroyAreaCharge(final Level level, final BlockPos center, final EntityDragonBase dragon) {
        if (dragon == null) {
            return;
        }

        if (MinecraftForge.EVENT_BUS
                .post(new DragonFireDamageWorldEvent(dragon, center.getX(), center.getY(), center.getZ()))) {
            return;
        }
        boolean canBreakBlocks = DragonUtils.canGrief(dragon) && ForgeEventFactory.getMobGriefingEvent(level, dragon);
        int size = Math.max(2, dragon.getDragonStage());
        BlockLaunchExplosion explosion = new BlockLaunchExplosion(level, dragon, center.getX(), center.getY(), center.getZ(), size)
                .setLaunchBlocks(IafConfig.explosiveDragonBreath);
        explosion.explode(); // block and entity lists are created here
        if (canBreakBlocks) {
        	explosion.finalizeExplosion(true);
        }

        final int statusDuration;

        if (dragon.dragonType == DragonType.FIRE) {
            statusDuration = 15;
        } else if (dragon.dragonType == DragonType.ICE) {
            statusDuration = 400;
        } else if (dragon.dragonType == DragonType.LIGHTNING) {
            statusDuration = 9;
        } else {
            return;
        }

        final float stageDamage = Math.max(1, dragon.getDragonStage()) * 6F;
        DamageSource damageSource = getDamageSource(dragon);
        final Vec3 hitCenter = Vec3.atCenterOf(center);
        for (Entity entity : explosion.entities) {
            if (entity instanceof LivingEntity target && !dragon.isAlliedTo(target) && !dragon.is(target)
                    && dragon.hasLineOfSight(target)) {
                // Linear falloff with real distance (not squared), never below 1.
                double distance = Math.sqrt(target.distanceToSqr(hitCenter));
                if (distance <= size) {
                    float damage = Math.max(1.0F, stageDamage - (float) distance);
                    if (target.hurt(damageSource, damage)) {
                        applyDragonEffect(target, dragon.dragonType, statusDuration, dragon, damage);
                    }
                }
            }
        }
    }

    private static DamageSource getDamageSource(final EntityDragonBase dragon) {
        return getDamageSource(dragon, dragon.dragonType);
    }

    private static DamageSource getDamageSource(final EntityDragonBase dragon, final DragonType type) {
        Player player = dragon.getRidingPlayer();

        if (type == DragonType.FIRE) {
            return player != null ? IafDamageRegistry.causeIndirectDragonFireDamage(dragon, player)
                    : IafDamageRegistry.causeDragonFireDamage(dragon);
        } else if (type == DragonType.ICE) {
            return player != null ? IafDamageRegistry.causeIndirectDragonIceDamage(dragon, player)
                    : IafDamageRegistry.causeDragonIceDamage(dragon);
        } else if (type == DragonType.LIGHTNING) {
            return player != null ? IafDamageRegistry.causeIndirectDragonLightningDamage(dragon, player)
                    : IafDamageRegistry.causeDragonLightningDamage(dragon);
        } else {
            return dragon.level().damageSources().mobAttack(dragon);
        }
    }

    private static DamageSource getDamageSource(final DragonType type, final Entity projectile,
                                                @Nullable final Entity owner) {
        if (type == DragonType.FIRE) {
            return owner == null ? IafDamageRegistry.causeDragonFireDamage(projectile)
                    : IafDamageRegistry.causeIndirectDragonFireDamage(projectile, owner);
        } else if (type == DragonType.ICE) {
            return owner == null ? IafDamageRegistry.causeDragonIceDamage(projectile)
                    : IafDamageRegistry.causeIndirectDragonIceDamage(projectile, owner);
        }
        return owner == null ? IafDamageRegistry.causeDragonLightningDamage(projectile)
                : IafDamageRegistry.causeIndirectDragonLightningDamage(projectile, owner);
    }

    /**
     * Shivaxi breath replacement for the temporary three-element port.  The breath keeps the
     * lightning dragon's normal direct breath damage, but its status component is the legacy
     * RLCraft ShivaxiBlazed capability for 200 ticks.
     */
    private static void damageShivaxiBreathTarget(final LivingEntity target, final EntityShivaxiDragon dragon) {
        float damage = dragon.getDragonStage() * (float) IafConfig.dragonAttackDamageLightning;
        target.hurt(getDamageSource(dragon, DragonType.LIGHTNING), damage);
        EntityDataProvider.getCapability(target)
                .ifPresent(data -> data.shivaxiBlazeData.setShivaxiBlazed(200, 0));
    }

    private static void attackBlock(final Level level, final EntityDragonBase dragon, final BlockPos position,
                                    final BlockState state) {
        attackBlock(level, dragon.dragonType, dragon, position, state);
    }

    @SuppressWarnings("deprecation")
	private static void attackBlock(final Level level, final DragonType type, final Entity source,
                                    final BlockPos position, final BlockState state) {
        if (state.getBlock() instanceof IDragonProof || !DragonUtils.canDragonBreak(state, source)) {
            return;
        }

        BlockState transformed;

        if (type == DragonType.FIRE) {
            transformed = transformBlockFire(state);
        } else if (type == DragonType.ICE) {
            transformed = transformBlockIce(state);
        } else if (type == DragonType.LIGHTNING) {
            transformed = transformBlockLightning(state);
        } else {
            return;
        }

        if (!transformed.is(state.getBlock())) {
            level.setBlockAndUpdate(position, transformed);
        }

        Block elementalBlock;
        boolean doPlaceBlock;

        if (type == DragonType.FIRE) {
            elementalBlock = Blocks.FIRE;
            doPlaceBlock = level.random.nextBoolean();
        } else if (type == DragonType.ICE) {
            elementalBlock = IafBlockRegistry.DRAGON_ICE_SPIKES.get();
            doPlaceBlock = level.random.nextInt(9) == 0;
        } else {
            return;
        }

        BlockState stateAbove = level.getBlockState(position.above());

        if (doPlaceBlock && transformed.isSolid() && stateAbove.getFluidState().isEmpty() && !stateAbove.canOcclude()
                && state.canOcclude() && DragonUtils.canDragonBreak(stateAbove, source)) {
            level.setBlockAndUpdate(position.above(), elementalBlock.defaultBlockState());
        }
    }

    public static void attackBlock(final Level level, final EntityDragonBase dragon, final BlockPos position) {
        attackBlock(level, dragon, position, level.getBlockState(position));
    }

    private static boolean applyDragonEffect(final LivingEntity target, final DragonType type, int statusDuration,
                                             final Entity source, float baseDamage) {
        if (type == DragonType.FIRE) {
            if (com.github.alexthe666.iceandfire.item.blooded.ItemBloodedArmor.hasFullArmorSet(target,
                    com.github.alexthe666.iceandfire.item.blooded.BloodedDragonType.DragonElement.FIRE)) {
                return false;
            }
            // Legacy behaviour: dragon-fire damage itself is not tagged as vanilla fire damage,
            // so Fire Protection / Fire Resistance do not absorb the breath hit.  Burning is
            // applied separately, matching the old 1.12.2 dragon-fire projectile.
            target.setSecondsOnFire(5);
        } else if (type == DragonType.ICE) {
            if (com.github.alexthe666.iceandfire.item.blooded.ItemBloodedArmor.hasFullArmorSet(target,
                    com.github.alexthe666.iceandfire.item.blooded.BloodedDragonType.DragonElement.ICE)) {
                return false;
            }
            // Keep ice as synchronized entity data/capability state, not a potion effect.
            EntityDataProvider.getCapability(target)
                    .ifPresent(data -> data.frozenData.setFrozen(target, 200));
        } else if (type == DragonType.LIGHTNING) {
            if (com.github.alexthe666.iceandfire.item.blooded.ItemBloodedArmor.hasFullArmorSet(target,
                    com.github.alexthe666.iceandfire.item.blooded.BloodedDragonType.DragonElement.LIGHTNING)) {
                return false;
            }
            double x = source.getX() - target.getX();
            double z = source.getZ() - target.getZ();
            target.knockback((double) statusDuration / 10.0D, x, z);
            if (IafConfig.lightningDragonParalysis) {
                LycanitesCompat.applyParalysis(target, IafConfig.lightningDragonParalysisTicks);
            }
        } else {
            return false;
        }
        return true;
    }

    public static BlockState transformBlockFire(BlockState in) {
        if (in.getBlock() instanceof SpreadingSnowyDirtBlock) {
            return IafBlockRegistry.CHARRED_GRASS.get().defaultBlockState().setValue(BlockReturningState.REVERTS, true);
        } else if (in.is(Blocks.DIRT)) {
            return IafBlockRegistry.CHARRED_DIRT.get().defaultBlockState().setValue(BlockReturningState.REVERTS, true);
        } else if (in.is(BlockTags.SAND) && in.getBlock() == Blocks.GRAVEL) {
            return IafBlockRegistry.CHARRED_GRAVEL.get().defaultBlockState()
                    .setValue(BlockFallingReturningState.REVERTS, true);
        } else if (in.is(BlockTags.BASE_STONE_OVERWORLD)
                && (in.getBlock() == Blocks.COBBLESTONE || in.getBlock().getDescriptionId().contains("cobblestone"))) {
            return IafBlockRegistry.CHARRED_COBBLESTONE.get().defaultBlockState().setValue(BlockReturningState.REVERTS,
                    true);
        } else if (in.is(BlockTags.BASE_STONE_OVERWORLD)
                && in.getBlock() != IafBlockRegistry.CHARRED_COBBLESTONE.get()) {
            return IafBlockRegistry.CHARRED_STONE.get().defaultBlockState().setValue(BlockReturningState.REVERTS, true);
        } else if (in.getBlock() == Blocks.DIRT_PATH) {
            return IafBlockRegistry.CHARRED_DIRT_PATH.get().defaultBlockState().setValue(BlockCharedPath.REVERTS, true);
        } else if (in.is(BlockTags.LOGS) || in.is(BlockTags.PLANKS)) {
            return IafBlockRegistry.ASH.get().defaultBlockState();
        } else if (in.is(BlockTags.LEAVES) || in.is(BlockTags.FLOWERS) || in.is(BlockTags.CROPS)
                || in.getBlock() == Blocks.SNOW) {
            return Blocks.AIR.defaultBlockState();
        }
        return in;
    }

    public static BlockState transformBlockIce(BlockState in) {
        if (in.getBlock() instanceof SpreadingSnowyDirtBlock) {
            return IafBlockRegistry.FROZEN_GRASS.get().defaultBlockState().setValue(BlockReturningState.REVERTS, true);
        } else if (in.is(BlockTags.DIRT) && in.getBlock() == Blocks.DIRT || in.is(BlockTags.SNOW)) {
            return IafBlockRegistry.FROZEN_DIRT.get().defaultBlockState().setValue(BlockReturningState.REVERTS, true);
        } else if (in.is(BlockTags.SAND) && in.getBlock() == Blocks.GRAVEL) {
            return IafBlockRegistry.FROZEN_GRAVEL.get().defaultBlockState().setValue(BlockFallingReturningState.REVERTS,
                    true);
        } else if (in.is(BlockTags.SAND) && in.getBlock() != Blocks.GRAVEL) {
            return in;
        } else if (in.is(BlockTags.BASE_STONE_OVERWORLD)
                && (in.getBlock() == Blocks.COBBLESTONE || in.getBlock().getDescriptionId().contains("cobblestone"))) {
            return IafBlockRegistry.FROZEN_COBBLESTONE.get().defaultBlockState().setValue(BlockReturningState.REVERTS,
                    true);
        } else if (in.is(BlockTags.BASE_STONE_OVERWORLD)
                && in.getBlock() != IafBlockRegistry.FROZEN_COBBLESTONE.get()) {
            return IafBlockRegistry.FROZEN_STONE.get().defaultBlockState().setValue(BlockReturningState.REVERTS, true);
        } else if (in.getBlock() == Blocks.DIRT_PATH) {
            return IafBlockRegistry.FROZEN_DIRT_PATH.get().defaultBlockState().setValue(BlockCharedPath.REVERTS, true);
        } else if (in.is(BlockTags.LOGS) || in.is(BlockTags.PLANKS)) {
            return IafBlockRegistry.FROZEN_SPLINTERS.get().defaultBlockState();
        } else if (in.is(Blocks.WATER)) {
            return Blocks.ICE.defaultBlockState();
        } else if (in.is(BlockTags.LEAVES) || in.is(BlockTags.FLOWERS) || in.is(BlockTags.CROPS)
                || in.getBlock() == Blocks.SNOW) {
            return Blocks.AIR.defaultBlockState();
        }
        return in;
    }

    public static BlockState transformBlockLightning(BlockState in) {
        if (in.getBlock() instanceof SpreadingSnowyDirtBlock) {
            return IafBlockRegistry.CRACKLED_GRASS.get().defaultBlockState().setValue(BlockReturningState.REVERTS,
                    true);
        } else if (in.is(BlockTags.DIRT) && in.getBlock() == Blocks.DIRT) {
            return IafBlockRegistry.CRACKLED_DIRT.get().defaultBlockState().setValue(BlockReturningState.REVERTS, true);
        } else if (in.is(BlockTags.SAND) && in.getBlock() == Blocks.GRAVEL) {
            return IafBlockRegistry.CRACKLED_GRAVEL.get().defaultBlockState()
                    .setValue(BlockFallingReturningState.REVERTS, true);
        } else if (in.is(BlockTags.BASE_STONE_OVERWORLD)
                && (in.getBlock() == Blocks.COBBLESTONE || in.getBlock().getDescriptionId().contains("cobblestone"))) {
            return IafBlockRegistry.CRACKLED_COBBLESTONE.get().defaultBlockState().setValue(BlockReturningState.REVERTS,
                    true);
        } else if (in.is(BlockTags.BASE_STONE_OVERWORLD)
                && in.getBlock() != IafBlockRegistry.CRACKLED_COBBLESTONE.get()) {
            return IafBlockRegistry.CRACKLED_STONE.get().defaultBlockState().setValue(BlockReturningState.REVERTS,
                    true);
        } else if (in.getBlock() == Blocks.DIRT_PATH) {
            return IafBlockRegistry.CRACKLED_DIRT_PATH.get().defaultBlockState().setValue(BlockCharedPath.REVERTS,
                    true);
        } else if (in.is(BlockTags.LOGS) || in.is(BlockTags.PLANKS)) {
            return IafBlockRegistry.ASH.get().defaultBlockState();
        } else if (in.is(BlockTags.LEAVES) || in.is(BlockTags.FLOWERS) || in.is(BlockTags.CROPS)
                || in.getBlock() == Blocks.SNOW) {
            return Blocks.AIR.defaultBlockState();
        }
        return in;
    }
}
