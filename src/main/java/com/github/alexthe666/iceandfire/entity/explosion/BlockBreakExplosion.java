package com.github.alexthe666.iceandfire.entity.explosion;

import com.github.alexthe666.iceandfire.compat.EnhancedVisualsCompat;
import com.github.alexthe666.iceandfire.compat.ModCompatList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockBreakExplosion extends Explosion {

    private final Level worldObj;
    private final double explosionX;
    private final double explosionY;
    private final double explosionZ;
    private final Mob exploder;
    private final float explosionSize;
    private final List<BlockPos> affectedBlockPositions;
    private final Map<Player, Vec3> playerKnockbackMap;
    private final Vec3 position;

    public BlockBreakExplosion(Level world, Mob entity, double x, double y, double z, float size) {
        super(world, entity, null, null, x, y, z, size, true, BlockInteraction.DESTROY);
        this.affectedBlockPositions = Lists.newArrayList();
        this.playerKnockbackMap = Maps.newHashMap();
        this.worldObj = world;
        this.exploder = entity;
        this.explosionSize = size;
        this.explosionX = x;
        this.explosionY = y;
        this.explosionZ = z;
        this.position = new Vec3(explosionX, explosionY, explosionZ);
    }

    @Override
    public void explode() {
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        HashMap<BlockPos, Float> resistanceMap = new HashMap<>();
        Set<BlockPos> affectedSet = new HashSet<>();
        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d0 = (float) j / 15.0F * 2.0F - 1.0F;
                        double d1 = (float) k / 3.0F * 2.0F - 1.0F;
                        double d2 = (float) l / 15.0F * 2.0F - 1.0F;
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 = d0 / d3;
                        d1 = d1 / d3;
                        d2 = d2 / d3;
                        float f = this.explosionSize * (0.7F + this.worldObj.random.nextFloat() * 0.6F);
                        double d4 = this.explosionX;
                        double d6 = this.explosionY;
                        double d8 = this.explosionZ;

                        for (; f > 0.0F; f -= 0.22500001F) {
                            mutPos.set(Mth.floor(d4), Mth.floor(d6), Mth.floor(d8));

                            BlockPos immutPos = null;
                            BlockState blockState = null;
                            Float resistance = resistanceMap.get(mutPos);
                            if (resistance == null) {
                                blockState = this.worldObj.getBlockState(mutPos);
                                if (!blockState.isAir()) {
                                    float f2 = blockState.getExplosionResistance(this.worldObj, mutPos, this);
                                    if (this.exploder != null) {
                                        f2 = this.exploder.getBlockExplosionResistance(this, this.worldObj, mutPos, blockState, blockState.getFluidState(), f2);
                                    }
                                    resistance = (f2 + 0.3F) * 0.3F;
                                } else {
                                    resistance = 0.0F;
                                }
                                immutPos = mutPos.immutable();
                                resistanceMap.put(immutPos, resistance);
                            }
                            f -= resistance;

                            if (f <= 0.0F) {
                                break;
                            }

                            if (!affectedSet.contains(mutPos)) {
                                if (blockState == null) {
                                    blockState = this.worldObj.getBlockState(mutPos);
                                }
                                if ((this.exploder == null || this.exploder.shouldBlockExplode(this, this.worldObj, mutPos, blockState, f))
                                        && blockState.canEntityDestroy(this.worldObj, mutPos, this.exploder)) {
                                    if (immutPos == null) {
                                        immutPos = mutPos.immutable();
                                    }
                                    affectedSet.add(immutPos);
                                }
                            }

                            d4 += d0 * 0.30000001192092896D;
                            d6 += d1 * 0.30000001192092896D;
                            d8 += d2 * 0.30000001192092896D;
                        }
                    }
                }
            }
        }
        this.affectedBlockPositions.addAll(affectedSet);

        float f3 = this.explosionSize * 2.0F;
        int k1 = Mth.floor(this.explosionX - f3 - 1.0D);
        int l1 = Mth.floor(this.explosionX + f3 + 1.0D);
        int i2 = Mth.floor(this.explosionY - f3 - 1.0D);
        int i1 = Mth.floor(this.explosionY + f3 + 1.0D);
        int j2 = Mth.floor(this.explosionZ - f3 - 1.0D);
        int j1 = Mth.floor(this.explosionZ + f3 + 1.0D);
        List<Entity> list = this.worldObj.getEntities(this.exploder, new AABB(k1, i2, j2, l1, i1, j1));
        ForgeEventFactory.onExplosionDetonate(this.worldObj, this, list, f3);
        Vec3 explosionPos = new Vec3(this.explosionX, this.explosionY, this.explosionZ);
        if (ModCompatList.isEVon) {
            EnhancedVisualsCompat.explosion(this, list);
        }
        for (Entity entity : list) {
            Entity controllingPassenger = this.exploder == null ? null : this.exploder.getControllingPassenger();
            boolean isControllingPassenger = controllingPassenger != null && controllingPassenger.getUUID().equals(entity.getUUID());
            if (!entity.ignoreExplosion() && !isControllingPassenger) {
                double d12 = Math.sqrt(entity.distanceToSqr(this.explosionX, this.explosionY, this.explosionZ)) / f3;

                if (d12 <= 1.0D) {
                    double d5 = entity.getX() - this.explosionX;
                    double d7 = entity.getEyeY() - this.explosionY;
                    double d9 = entity.getZ() - this.explosionZ;
                    double d13 = Mth.sqrt((float) (d5 * d5 + d7 * d7 + d9 * d9));
                    double d14 = Explosion.getSeenPercent(explosionPos, entity);
                    double d10 = (1.0D - d12) * d14;
                    if (d13 != 0.0D && this.exploder != null) {
                        d5 = d5 / d13;
                        d7 = d7 / d13;
                        d9 = d9 / d13;
                        entity.hurt(this.worldObj.damageSources().mobAttack(this.exploder),
                                ((float) ((int) ((d10 * d10 + d10) / 2.0D * 7.0D * (double) f3 + 1.0D))) / 6.0F);
                    }
                    double d11 = 0.5D;

                    if (entity instanceof LivingEntity livingEntity) {
                        d11 = ProtectionEnchantment.getExplosionKnockbackAfterDampener(livingEntity, d10);
                    }
                    entity.setDeltaMovement(entity.getDeltaMovement().add(d5 * d11, d7 * d11, d9 * d11));

                    if (entity instanceof Player player && !player.getAbilities().invulnerable) {
                        this.playerKnockbackMap.put(player, new Vec3(d5 * d10, d7 * d10, d9 * d10));
                    }
                }
            }
        }
    }

    @Override
    public void finalizeExplosion(boolean spawnParticles) {
        if (this.affectedBlockPositions.isEmpty()) {
            return;
        }
        for (BlockPos blockpos : this.affectedBlockPositions) {
            BlockState state = this.worldObj.getBlockState(blockpos);
            if (!state.isAir()) {
                if (spawnParticles && this.worldObj.random.nextBoolean()) {
                    double d0 = (float) blockpos.getX() + this.worldObj.random.nextFloat();
                    double d1 = (float) blockpos.getY() + this.worldObj.random.nextFloat();
                    double d2 = (float) blockpos.getZ() + this.worldObj.random.nextFloat();
                    double d3 = d0 - this.explosionX;
                    double d4 = d1 - this.explosionY;
                    double d5 = d2 - this.explosionZ;
                    double d6 = Mth.sqrt((float) (d3 * d3 + d4 * d4 + d5 * d5));
                    d3 = d3 / d6;
                    d4 = d4 / d6;
                    d5 = d5 / d6;
                    double d7 = 0.5D / (d6 / (double) this.explosionSize + 0.1D);
                    d7 = d7 * (double) (this.worldObj.random.nextFloat() * this.worldObj.random.nextFloat() + 0.3F);
                    d3 = d3 * d7;
                    d4 = d4 * d7;
                    d5 = d5 * d7;

                    double explosionParticleX = (d0 + this.explosionX) / 2.0D;
                    double explosionParticleY = (d1 + this.explosionY) / 2.0D;
                    double explosionParticleZ = (d2 + this.explosionZ) / 2.0D;
                    if (this.worldObj instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION, explosionParticleX, explosionParticleY, explosionParticleZ, 0, d3, d4, d5, 1.0D);
                        serverLevel.sendParticles(ParticleTypes.SMOKE, d0, d1, d2, 0, d3, d4, d5, 1.0D);
                    } else {
                        this.worldObj.addParticle(ParticleTypes.EXPLOSION, explosionParticleX, explosionParticleY, explosionParticleZ, d3, d4, d5);
                        this.worldObj.addParticle(ParticleTypes.SMOKE, d0, d1, d2, d3, d4, d5);
                    }
                }
                if (state.canDropFromExplosion(this.worldObj, blockpos, this) && this.worldObj instanceof ServerLevel serverLevel) {
                    BlockEntity blockEntity = this.worldObj.getBlockEntity(blockpos);
                    for (ItemStack stack : Block.getDrops(state, serverLevel, blockpos, blockEntity, this.exploder, ItemStack.EMPTY)) {
                        if (this.worldObj.random.nextFloat() <= 1.0F / this.explosionSize) {
                            Block.popResource(this.worldObj, blockpos, stack);
                        }
                    }
                }
                state.onBlockExploded(this.worldObj, blockpos, this);
            }
        }
    }

    @Override
    public Map<Player, Vec3> getHitPlayers() {
        return this.playerKnockbackMap;
    }

    @Override
    public LivingEntity getIndirectSourceEntity() {
        return this.exploder;
    }

    @Override
    public void clearToBlow() {
        this.affectedBlockPositions.clear();
    }

    @Override
    public List<BlockPos> getToBlow() {
        return this.affectedBlockPositions;
    }

    @Override
    public Vec3 getPosition() {
        return this.position;
    }

    @Override
    public Entity getExploder() {
        return this.exploder;
    }
}
