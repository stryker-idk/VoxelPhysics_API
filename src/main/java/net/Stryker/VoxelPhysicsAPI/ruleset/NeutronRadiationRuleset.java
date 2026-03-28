package net.Stryker.VoxelPhysicsAPI.ruleset;

import net.Stryker.VoxelPhysicsAPI.IRuleset;
import net.Stryker.VoxelPhysicsAPI.LongIntMap;
import net.Stryker.VoxelPhysicsAPI.PhysicsEngine;

/**
 * Neutron radiation with separate flux and energy values.
 * No bit-packing needed! Native multi-value support.
 */
public class NeutronRadiationRuleset implements IRuleset {

    public static final NeutronRadiationRuleset INSTANCE = new NeutronRadiationRuleset();

    private static final int FLUX = 0;    // Index 0: particle count
    private static final int ENERGY = 1;  // Index 1: MeV/speed

    private static final int[] DX = { 1, -1,  0,  0,  0,  0 };
    private static final int[] DY = { 0,  0,  1, -1,  0,  0 };
    private static final int[] DZ = { 0,  0,  0,  0,  1, -1 };

    @Override
    public boolean tick(LongIntMap[] current, LongIntMap[] next) {
        LongIntMap currFlux = current[FLUX];
        LongIntMap currEnergy = current[ENERGY];
        LongIntMap nextFlux = next[FLUX];
        LongIntMap nextEnergy = next[ENERGY];

        currFlux.forEach((key, flux) -> {
            if (flux <= 0) return;

            int energy = currEnergy.get(key);
            if (energy <= 1) return; // Absorbed

            int x = PhysicsEngine.unpackX(key);
            int y = PhysicsEngine.unpackY(key);
            int z = PhysicsEngine.unpackZ(key);

            // Lose energy traveling through matter
            int newEnergy = energy - 1;

            for (int i = 0; i < 6; i++) {
                int ny = y + DY[i];
                if (ny < -64 || ny > 319) continue;

                long neighborKey = PhysicsEngine.pack(x + DX[i], ny, z + DZ[i]);

                // Flux conserved, energy decreases
                nextFlux.putMax(neighborKey, flux);
                nextEnergy.putMax(neighborKey, newEnergy);
            }
        });

        return !nextFlux.isEmpty();
    }
}