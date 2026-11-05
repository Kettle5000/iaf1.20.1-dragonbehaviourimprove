package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.iceandfire.entity.util.DragonUtils;
import com.github.alexthe666.iceandfire.entity.util.IDragonProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public abstract class EntityDragonCharge extends Projectile implements IDragonProjectile {
	/** Same drag vanilla fireballs use; without it the charge accelerates forever. */
	private static final double INERTIA = 0.95D;
	private static final double WATER_INERTIA = 0.8D;
	public double xPower;
	public double yPower;
	public double zPower;

    public EntityDragonCharge(EntityType<? extends Projectile> type, Level worldIn) {
        super(type, worldIn);
    }

    public EntityDragonCharge(EntityType<? extends Projectile> type, Level worldIn, double posX,
                              double posY, double posZ, double accelX, double accelY, double accelZ) {
        this(type, worldIn);
        this.moveTo(posX, posY, posZ, this.getYRot(), this.getXRot());
        this.reapplyPosition();
        double d0 = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
        if (d0 > 0D) {
        	this.xPower = (accelX / d0) * 0.21D;
            this.yPower = (accelY / d0) * 0.21D;
            this.zPower = (accelZ / d0) * 0.21D;
        }
    }

    public EntityDragonCharge(EntityType<? extends Projectile> type, Level worldIn,
                              EntityDragonBase shooter, double accelX, double accelY, double accelZ) {
        this(type, worldIn, shooter.getX(), shooter.getY(), shooter.getZ(), accelX, accelY, accelZ);
        this.setOwner(shooter);
        this.setRot(shooter.getYRot(), shooter.getXRot());
    }

    @SuppressWarnings("deprecation")
	@Override
    public void tick() {
        Entity shootingEntity = this.getOwner();
        if ((shootingEntity == null || shootingEntity.isAlive()) && this.level().hasChunkAt(this.blockPosition())) {
            super.baseTick();

            HitResult raytraceresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitMob);

            if (raytraceresult.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, raytraceresult)) {
                this.onHit(raytraceresult);
            }

            this.checkInsideBlocks();
            Vec3 vector3d = this.getDeltaMovement();
            double d0 = this.getX() + vector3d.x;
            double d1 = this.getY() + vector3d.y;
            double d2 = this.getZ() + vector3d.z;
            double inertia = INERTIA;
            if (this.isInWater()) {
                for (int i = 0; i < 4; ++i) {
                    this.level().addParticle(ParticleTypes.BUBBLE, this.getX() - vector3d.x * 0.25D, this.getY() - vector3d.y * 0.25D, this.getZ() - vector3d.z * 0.25D, vector3d.x, vector3d.y, vector3d.z);
                }
                inertia = WATER_INERTIA;
            }
            this.setDeltaMovement(vector3d.add(this.xPower, this.yPower, this.zPower).scale(inertia));
            this.level().addParticle(this.getTrailParticle(), this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
            this.setPos(d0, d1, d2);
        } else {
            this.remove(RemovalReason.DISCARDED);
        }
    }

    @Override
    protected void onHit(@NotNull HitResult movingObject) {
        Entity shootingEntity = this.getOwner();
        HitResult.Type hitresult = movingObject.getType();
        if (!this.level().isClientSide()) {
        	BlockPos center = BlockPos.containing(this.getX(), this.getY(), this.getZ());
        	if (movingObject instanceof BlockHitResult blockhit) {
        		center = blockhit.getBlockPos();
        	}
            if (hitresult == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) movingObject).getEntity();
                center = entity.blockPosition();

                if (entity instanceof IDragonProjectile) {
                    return;
                }
                if (shootingEntity != null && shootingEntity instanceof EntityDragonBase) {
                    EntityDragonBase dragon = (EntityDragonBase) shootingEntity;
                    if (dragon.isAlliedTo(entity) || dragon.is(entity) || dragon.isPart(entity)) {
                        return;
                    }
                }
                if (entity == null || !(entity instanceof IDragonProjectile) && entity != shootingEntity && shootingEntity instanceof EntityDragonBase) {
                    EntityDragonBase dragon = (EntityDragonBase) shootingEntity;
                    if (shootingEntity != null && (entity == shootingEntity || (entity instanceof TamableAnimal && ((EntityDragonBase) shootingEntity).isOwnedBy(((EntityDragonBase) shootingEntity).getOwner())))) {
                        return;
                    }
                    if (dragon != null) {
                        dragon.randomizeAttacks();
                    }
                    this.remove(RemovalReason.DISCARDED);
                }
                if (entity != null && !(entity instanceof IDragonProjectile) && !entity.is(shootingEntity)) {
                    if (shootingEntity != null && (entity.is(shootingEntity) || (shootingEntity instanceof EntityDragonBase && entity instanceof TamableAnimal && ((EntityDragonBase) shootingEntity).getOwner() == ((TamableAnimal) entity).getOwner()))) {
                        return;
                    }
                    if (shootingEntity instanceof EntityDragonBase) {
                        float damageAmount = getDamage() * ((EntityDragonBase) shootingEntity).getDragonStage();

                        EntityDragonBase shootingDragon = (EntityDragonBase) shootingEntity;
                        Entity cause = shootingDragon.getRidingPlayer() != null ? shootingDragon.getRidingPlayer() : shootingDragon;
                        DamageSource source = causeDamage(cause);

                        entity.hurt(source, damageAmount);
                        if (entity instanceof LivingEntity livingTarget) {
                            this.onDirectHit(livingTarget, shootingDragon);
                        }
                        if (entity instanceof Player player) {
                            DragonUtils.fillBottleWithDragonBreath(player, shootingDragon.dragonType);
                        }
                        if (entity instanceof LivingEntity && ((LivingEntity) entity).getHealth() == 0) {
                            ((EntityDragonBase) shootingEntity).randomizeAttacks();
                        }
                    }
                    if (shootingEntity instanceof LivingEntity) {
                        this.doEnchantDamageEffects((LivingEntity) shootingEntity, entity);
                    }
                    this.remove(RemovalReason.DISCARDED);
                }
            }
            if (hitresult != HitResult.Type.MISS) {
                if (shootingEntity instanceof EntityDragonBase && DragonUtils.canGrief((EntityDragonBase) shootingEntity)) {
                    destroyArea(level(), center, ((EntityDragonBase) shootingEntity));
                }
                this.remove(RemovalReason.DISCARDED);
            }
        }

    }

    public abstract DamageSource causeDamage(@Nullable Entity cause);

    public abstract void destroyArea(Level world, BlockPos center, EntityDragonBase destroyer);

    public abstract float getDamage();

    /**
     * Element-specific direct-hit behaviour kept separate from block-griefing/explosion logic,
     * matching the old projectile implementation.
     */
    protected void onDirectHit(LivingEntity target, EntityDragonBase dragon) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }
    
    @Override
    public boolean isAttackable() {
        return false;
     }

    protected boolean canHitMob(Entity hitMob) {
        Entity shooter = getOwner();
        return hitMob != this && super.canHitEntity(hitMob) && !(shooter == null || hitMob.isAlliedTo(shooter)) && !(hitMob instanceof EntityDragonPart);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public float getPickRadius() {
        return 0F;
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }
    
    @Override
	protected void defineSynchedData() {
	}

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        ListTag power = new ListTag();
        power.add(DoubleTag.valueOf(this.xPower));
        power.add(DoubleTag.valueOf(this.yPower));
        power.add(DoubleTag.valueOf(this.zPower));
        compound.put("power", power);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("power", Tag.TAG_LIST)) {
            ListTag power = compound.getList("power", Tag.TAG_DOUBLE);
            if (power.size() == 3) {
                this.xPower = power.getDouble(0);
                this.yPower = power.getDouble(1);
                this.zPower = power.getDouble(2);
            }
        }
    }
}
