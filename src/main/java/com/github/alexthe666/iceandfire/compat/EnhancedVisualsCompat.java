package com.github.alexthe666.iceandfire.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import team.creative.enhancedvisuals.EnhancedVisuals;

import java.util.List;

/**
 * The only class that touches Enhanced Visuals. Callers check {@link ModCompatList#isEVon} first,
 * so this class (and Enhanced Visuals) is never loaded when the mod isn't installed.
 */
public final class EnhancedVisualsCompat {

    private EnhancedVisualsCompat() {
    }

    public static void explosion(Explosion explosion, List<Entity> entities) {
        EnhancedVisuals.EVENTS.explosion(explosion, entities);
    }
}
