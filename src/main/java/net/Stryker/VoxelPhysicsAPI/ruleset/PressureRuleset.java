package net.Stryker.VoxelPhysicsAPI.ruleset;

import net.Stryker.VoxelPhysicsAPI.IRuleset;
import net.Stryker.VoxelPhysicsAPI.LongIntMap;
import net.Stryker.VoxelPhysicsAPI.PhysicsEngine;

public class PressureRuleset implements IRuleset {

    public static final PressureRuleset INSTANCE = new PressureRuleset();

    private static final int[] DX = { 1, -1,  0,  0,  0,  0 };
    private static final int[] DY = { 0,  0,  1, -1,  0,  0 };
    private static final int[] DZ = { 0,  0,  0,  0,  1, -1 };

    @Override
    public boolean tick(LongIntMap[] current, LongIntMap[] next) {
        // Pressure is single-value, so we only use index 0
        LongIntMap curr = current[0];
        LongIntMap nxt = next[0];

        curr.forEach((key, value) -> {
            if (value <= 1) return;

            int nextValue = value - 1;
            int x = PhysicsEngine.unpackX(key);
            int y = PhysicsEngine.unpackY(key);
            int z = PhysicsEngine.unpackZ(key);

            for (int i = 0; i < 6; i++) {
                int ny = y + DY[i];
                if (ny < -64 || ny > 319) continue;
                nxt.putMax(PhysicsEngine.pack(x + DX[i], ny, z + DZ[i]), nextValue);
            }
        });
        return !nxt.isEmpty();
    }
}