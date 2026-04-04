# VoxelPhysics API

A sparse cellular automata framework for Minecraft Forge. Runs physics simulation on a dedicated thread at 100 TPS with minimal performance loss because only "active" voxels use memory.

---

## How it works

The engine uses a double-buffered sparse hash map. Each tick:

1. Constant sources are applied (fire, heat emitters, etc.)
2. One-time seeds are drained (explosions, player events, etc.)
3. Rulesets propagate values to neighbors
4. Buffers are swapped

Positions are packed as `long` keys with `int` values, no object allocation per voxel.

!!!The simulation only runs on loaded chunks!!!
*(will ***probably*** be fixed later tho)*

---

## Setup

Add to `build.gradle`:

```gradle
dependencies {
    implementation files('libs/voxelphysics_api-1.0.0.jar')
}
```

**Requirements:** Minecraft 1.20.1+, Forge 47.4.0+, Java 17+

---

## Defining a Physics Type

Physics types represent things like temperature, pressure, or radiation. Each type has a ruleset that controls how values spread.

```java
// Simple single-value type
public static final PhysicsType EXAMPLE_TYPE = new PhysicsType(
    ResourceLocation.fromNamespaceAndPath("yourmodid", "example_type"),
    new ExampleRuleset(),
    1,                    // tick interval (1 = every tick)
    "example_value",      // value name
    MergeBehavior.PUT_MAX // how values merge when written to same cell
);
```

### Writing a Ruleset

```java
public class ExampleRuleset implements IRuleset {
    @Override
    public boolean tick(LongIntMap[] current, LongIntMap[] next) {
        current[0].forEach((key, value) -> {
            if (value <= 1) return; // dissipate if too low

            int x = PhysicsEngine.unpackX(key);
            int y = PhysicsEngine.unpackY(key);
            int z = PhysicsEngine.unpackZ(key);

            int spreadValue = value - 1;
            next[0].putMax(PhysicsEngine.pack(x+1, y, z), spreadValue);
            next[0].putMax(PhysicsEngine.pack(x-1, y, z), spreadValue);
            next[0].putMax(PhysicsEngine.pack(x, y+1, z), spreadValue);
            next[0].putMax(PhysicsEngine.pack(x, y-1, z), spreadValue);
            next[0].putMax(PhysicsEngine.pack(x, y, z+1), spreadValue);
            next[0].putMax(PhysicsEngine.pack(x, y, z-1), spreadValue);
        });

        return !next[0].isEmpty(); // false = this type goes dormant next tick
    }
}
```

### Multi-value Types

For types that track multiple values at the same position (e.g. neutron flux + energy):

```java
public static final PhysicsType EXAMPLE_MULTI = new PhysicsType(
    ResourceLocation.fromNamespaceAndPath("yourmodid", "example_multi"),
    new ExampleMultiRuleset(),
    1,
    new String[]{"value_a", "value_b"},
    new MergeBehavior[]{MergeBehavior.ADD, MergeBehavior.PUT_MAX}
);

// In your ruleset, each index is a separate map:
current[0].forEach((key, valA) -> { /* value_a */ });
current[1].forEach((key, valB) -> { /* value_b */ });
```

---

## Block Properties

Block properties are static per-block values (flammability, conductivity, density, etc.) that rulesets can look up.

```java
// Register a property type
public static final BlockPropertyType EXAMPLE_PROPERTY =
    BlockPropertyRegistry.registerType(
        ResourceLocation.fromNamespaceAndPath("yourmodid", "example_property"),
        50,                 // default value
        MergeBehavior.PUT_MAX
    );

// Assign to specific blocks (call in FMLCommonSetupEvent, before freeze())
BlockPropertyRegistry.register(Blocks.STONE, EXAMPLE_PROPERTY, 100);
BlockPropertyRegistry.register(Blocks.DIRT,  EXAMPLE_PROPERTY, 10);

// Or assign by tag (applies to every block with that tag)
BlockPropertyRegistry.register(BlockTags.LOGS,      EXAMPLE_PROPERTY, 80);
BlockPropertyRegistry.register(Tags.Blocks.ORES,    EXAMPLE_PROPERTY, 60);

// Freeze after all registrations
BlockPropertyRegistry.freeze();

// Look up in a ruleset
int resistance = BlockPropertyRegistry.get(blockState.getBlock(), EXAMPLE_PROPERTY);
```

---

## Seeding Values

### One-time events (explosions, player actions)

```java
PhysicsThread.get().engine.setSource(x, y, z, EXAMPLE_TYPE, 100);

// Multi-value
PhysicsThread.get().engine.setSource(x, y, z, EXAMPLE_MULTI, 100, 50);
```

### Persistent emitters (fire, lava, heat blocks)

```java
// Applies the given value every tick automatically
PhysicsThread.get().engine.setConstantSource(x, y, z, EXAMPLE_TYPE, 100);

// Remove when the block is broken or extinguished
PhysicsThread.get().engine.removeConstantSource(x, y, z, EXAMPLE_TYPE);

// Nuke all constant sources of a type
PhysicsThread.get().engine.clearConstantSources(EXAMPLE_TYPE);
```

---

## Querying Values

```java
int value   = PhysicsThread.get().engine.getValue(x, y, z, EXAMPLE_TYPE);
boolean hot = PhysicsThread.get().engine.getValue(x, y, z, EXAMPLE_TYPE) > 0;
```

Values are read from a thread-safe snapshot, safe to call from the main thread.

---

## World Interaction in Rulesets

```java
public boolean tick(LongIntMap[] current, LongIntMap[] next, Level level, ...) {
    current[0].forEach((key, value) -> {
        int x = PhysicsEngine.unpackX(key);
        int y = PhysicsEngine.unpackY(key);
        int z = PhysicsEngine.unpackZ(key);
        BlockPos pos = new BlockPos(x, y, z);

        BlockState state = level.getBlockState(pos);
        int resistance = BlockPropertyRegistry.get(state.getBlock(), EXAMPLE_PROPERTY);

        if (value > resistance) {
            level.destroyBlock(pos, true);
            level.playSound(null, pos, SoundEvents.BLOCK_BREAK, ...);
        }

        // propagate to neighbors...
    });
}
```

---

## Debug Commands

All commands require OP. Run them while looking at a block or standing in a spot.

| Command | Description |
|---|---|
| `/vpdebug setsource <type> <value>` | Seed a one-time value at your position |
| `/vpdebug setconstantsource <type> <value>` | Set a persistent emitter at your position |
| `/vpdebug clearconstants <type>` | Remove all constant sources of a type |
| `/vpdebug status` | Show active voxel counts per type |
| `/vpdebug property <block_id>` | Dump block properties for a block |
| `/vpdebug clear` | Wipe all physics data |

---

## Planned Addons

**VoxelPhysics: Thermodynamics/explosions & fire**: Pressure, temparature. explosions now create realistic shockwave that bounce off, recombine, and... do.. stuff. also temparature but that is like super boring me want big boom to be realistic

**VoxelPhysics: Radioactive**: nuclear radiation, Alpha, Beta, Gam-, wait thats a type of electromagnetic radiation... eh, some future addon will add that clusterfuck. also neutron radiation so i can now make a real RBMK

**VoxelPhysics: ValkyrienSkies**: adds comapatibility with Valkyrien skies 2, not a single clue how that will work... but i will probably figure out some way to do it.
---

## License

MIT License
