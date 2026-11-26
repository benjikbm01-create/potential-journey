package dev.underdeep.wildlife.population;

import dev.underdeep.registry.ModPlants;
import dev.underdeep.wildlife.CreatureSpecies.Temperament;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.Voices;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * What tamed companions do for their owner (called every 10 seconds for owned creatures within 24
 * blocks). Each tamable species has a distinct reason to exist: sentinels warn of predators, foragers
 * sense resources, gentle companions comfort, the otter brings fish. Combat helpers use vanilla
 * owner-defence goals; mounts are handled by the entity.
 */
final class Companions {
    private Companions() {
    }

    static void act(ServerWorld w, UnderdeepCreature c, ServerPlayerEntity owner) {
        switch (c.species().id) {
            case "rootglider", "amberwing", "lumen_kit", "shadecat", "ledgewing" -> warn(w, c, owner);
            case "snufflet" -> sniff(w, c, owner);
            case "puffling" -> comfort(c, owner);
            case "rillpup" -> fetchFish(w, c, owner);
            default -> {
            }
        }
        if (c.species().id.equals("lumen_kit")) {
            comfort(c, owner);
        }
    }

    /** Sentinel: spots wild predators/ambushers near the owner and makes them glow briefly. */
    private static void warn(ServerWorld w, UnderdeepCreature c, ServerPlayerEntity owner) {
        for (UnderdeepCreature e : w.getEntitiesByClass(UnderdeepCreature.class, owner.getBoundingBox().expand(20, 8, 20),
                x -> !x.isTamed() && (x.species().isPredator() || x.species().temperament() == Temperament.AMBUSH))) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0, false, false));
            c.playSound(Voices.warning(c.species()), 1.0f, c.getSoundPitch());
            owner.sendMessage(Text.translatable("message.underdeep.companion.warn", c.getDisplayName(), e.getDisplayName()), true);
            return;
        }
    }

    /** Forager: sniffs out truffles and ores within 12 blocks and points towards one. */
    private static void sniff(ServerWorld w, UnderdeepCreature c, ServerPlayerEntity owner) {
        BlockPos o = c.getBlockPos();
        for (int i = 0; i < 300; i++) {
            BlockPos p = o.add(w.random.nextInt(25) - 12, w.random.nextInt(13) - 6, w.random.nextInt(25) - 12);
            BlockState s = w.getBlockState(p);
            Identifier id = Registries.BLOCK.getId(s.getBlock());
            if (s.isOf(ModPlants.CAVE_TRUFFLES) || s.isIn(BlockTags.COAL_ORES) || s.isIn(BlockTags.IRON_ORES) || s.isIn(BlockTags.GOLD_ORES)
                    || s.isIn(BlockTags.DIAMOND_ORES) || id.getNamespace().equals("underdeep") && id.getPath().endsWith("_ore")) {
                Vec3d dir = Vec3d.ofCenter(p).subtract(c.getPos()).normalize();
                for (int k = 1; k <= 5; k++) {
                    Vec3d q = c.getPos().add(dir.multiply(k)).add(0, 0.6, 0);
                    w.spawnParticles(ParticleTypes.COMPOSTER, q.x, q.y, q.z, 2, 0.1, 0.1, 0.1, 0);
                }
                c.getLookControl().lookAt(Vec3d.ofCenter(p));
                owner.sendMessage(Text.translatable("message.underdeep.companion.sniff", c.getDisplayName()), true);
                return;
            }
        }
    }

    /** Gentle companion: its company slowly mends an injured owner sitting or standing close by. */
    private static void comfort(UnderdeepCreature c, ServerPlayerEntity owner) {
        if (c.squaredDistanceTo(owner) < 36 && owner.getHealth() < owner.getMaxHealth() * 0.75f) {
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, true));
        }
    }

    /** Otter: near water it sometimes brings its owner a fish. */
    private static void fetchFish(ServerWorld w, UnderdeepCreature c, ServerPlayerEntity owner) {
        if (c.isTouchingWater() || w.getFluidState(c.getBlockPos().down()).isIn(FluidTags.WATER)) {
            if (w.random.nextInt(18) == 0 && c.squaredDistanceTo(owner) < 64) {
                c.dropStack(new ItemStack(Registries.ITEM.get(Identifier.of("underdeep", "fish_fillet"))));
                owner.sendMessage(Text.translatable("message.underdeep.companion.fish", c.getDisplayName()), true);
            }
        }
    }
}
