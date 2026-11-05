package com.github.alexthe666.iceandfire.util;

import java.util.ArrayList;
import java.util.List;

public final class IafExplosionRays {
    // Each entry is a primitive direction (dx, dy, dz) with gcd = 1
    // and dx²+dy²+dz² ≤ 25 (radius 5). Scaling these covers every block within radius 5,
    // which is the largest explosion that uses them (stage 5 charge).
    public static final int[][] RAYS;

    static {
        List<int[]> list = new ArrayList<>(512);
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    int r2 = x*x + y*y + z*z;
                    if (r2 > 25) continue;
                    if (gcd(gcd(Math.abs(x), Math.abs(y)), Math.abs(z)) == 1) {
                        list.add(new int[]{x, y, z});
                    }
                }
            }
        }
        RAYS = new int[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            RAYS[i] = list.get(i);
        }
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }
}