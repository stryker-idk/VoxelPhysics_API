package net.Stryker.VoxelPhysicsAPI.PhysicsType;

public enum MergeBehavior {
    PUT,      // Overwrite with new value
    PUT_MAX,  // Keep maximum value (highest pressure/energy wins)
    ADD       // Sum values (heat accumulates, flux adds up)
}
