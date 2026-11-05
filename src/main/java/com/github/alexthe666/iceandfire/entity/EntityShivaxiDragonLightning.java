package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.iceandfire.entity.props.EntityDataProvider;
import com.github.alexthe666.iceandfire.block.IafBlockRegistry;
import com.github.alexthe666.iceandfire.entity.util.DragonUtils;
import com.github.alexthe666.iceandfire.entity.util.IDragonProjectile;
import com.github.alexthe666.iceandfire.misc.IafDamageRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.PlayMessages;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/** High-speed explosive lightning projectile used only by the Shivaxi Dragon. */
public class EntityShivaxiDragonLightning extends EntityDragonLightningCharge {

    public EntityShivaxiDragonLightning(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    public EntityShivaxiDragonLightning(PlayMessages.SpawnEntity spawn, Level level) {
        this(IafEntityRegistry.SHIVAXI_DRAGON_LIGHTNING.get(), level);
    }

    public EntityShivaxiDragonLightning(EntityType<? extends Projectile> type, Level level, EntityDragonBase shooter,
                                        double accelX, double accelY, double accelZ) {
        super(type, level, shooter, accelX, accelY, accelZ);
        double length = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
        if (length > 1.0E-7D) {
            double speed = 0.1D * (shooter.isFlying() ? 4.0D * shooter.getDragonStage() : 1.0D);
            this.xPower = accelX / length * speed;
            this.yPower = accelY / length * speed;
            this.zPower = accelZ / length * speed;
        }
    }

    @Override
    public void tick() {
        if (this.tickCount > 160 || this.isInWater()) {
            this.discard();
            return;
        }
        super.tick();
    }

    @Override
    protected void onHit(@NotNull HitResult hitResult) {
        if (this.level().isClientSide()) {
            return;
        }
        Entity owner = this.getOwner();
        if (!(owner instanceof EntityDragonBase dragon)) {
            this.discard();
            return;
        }

        if (hitResult instanceof EntityHitResult entityHit) {
            Entity hit = entityHit.getEntity();
            if (hit instanceof IDragonProjectile) {
                return;
            }
            if (hit == owner || DragonUtils.onSameTeam(owner, hit)) {
                this.discard();
                return;
            }
        }

        explodeShivaxi(dragon);
        this.discard();
    }

    private void explodeShivaxi(EntityDragonBase dragon) {
        float radius = dragon.getDragonStage() * 2.5F;
        // Keep the dragon itself as the direct source even when ridden. Dragon multipart
        // parts use the direct source to reject their parent's own explosion damage, matching
        // the 1.12.2 EntityDragonPart self-damage guard.
        DamageSource source = dragon.getRidingPlayer() != null
                ? IafDamageRegistry.causeIndirectDragonLightningDamage(dragon, dragon.getRidingPlayer())
                : IafDamageRegistry.causeDragonLightningDamage(dragon);
        boolean canGrief = DragonUtils.canGrief(dragon)
                && ForgeEventFactory.getMobGriefingEvent(this.level(), dragon);

        // Preserve the Shivaxi blast shape, but every solid terrain block that would normally
        // become a lightning-crackled/ash variant becomes the legacy RLC fulgurite block instead.
        Explosion explosion = new Explosion(this.level(), dragon, source, null, this.getX(), this.getY(), this.getZ(),
                radius, false, Explosion.BlockInteraction.KEEP);
        explosion.explode();
        if (canGrief) {
            for (BlockPos pos : explosion.getToBlow()) {
                BlockState state = this.level().getBlockState(pos);
                if (DragonUtils.canDragonBreak(state, dragon)) {
                    BlockState transformed = IafDragonDestructionManager.transformBlockLightning(state);
                    if (!transformed.equals(state)) {
                        // Vegetation/snow that the normal lightning transform removes still becomes air.
                        // Everything else that would be transformed becomes fulgurite (the legacy "electric lava").
                        this.level().setBlockAndUpdate(pos, transformed.isAir()
                                ? transformed
                                : IafBlockRegistry.FULGURITE.get().defaultBlockState());
                    }
                }
            }
        }
        explosion.finalizeExplosion(true);

        // Old Shivaxi explosion applies its special burning/slowing capability throughout the blast.
        double effectRadius = radius * 2.0D;
        AABB area = new AABB(this.getX() - effectRadius, this.getY() - effectRadius, this.getZ() - effectRadius,
                this.getX() + effectRadius, this.getY() + effectRadius, this.getZ() + effectRadius);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (target != dragon && !DragonUtils.onSameTeam(dragon, target)
                    && target.distanceToSqr(this.getX(), this.getY(), this.getZ()) <= effectRadius * effectRadius) {
                EntityDataProvider.getCapability(target).ifPresent(data -> data.shivaxiBlazeData.setShivaxiBlazed(200));
                double xRatio = dragon.getX() - target.getX();
                double zRatio = dragon.getZ() - target.getZ();
                target.knockback(0.3F, xRatio, zRatio);
            }
        }
        dragon.attackDecision = true;
    }

    @Override
    public DamageSource causeDamage(@Nullable Entity cause) {
        return IafDamageRegistry.causeDragonLightningDamage(cause);
    }

    @Override
    public void destroyArea(Level world, BlockPos center, EntityDragonBase destroyer) {
        // onHit is fully overridden; the Shivaxi explosion above replaces normal lightning-charge destruction.
    }
}
