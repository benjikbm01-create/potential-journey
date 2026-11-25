package dev.underdeep.item;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Simple medicine made from Underdeep herbs: drunk (tonics, in a glass bottle) or applied (poultices,
 * salves). Applies its effects and optionally cures harmful effects. Advanced medicine is Stage 7.
 */
public class RemedyItem extends Item {
    private final boolean drink;
    private final boolean cures;
    private final List<Supplier<StatusEffectInstance>> effects;

    @SafeVarargs
    public RemedyItem(Settings settings, boolean drink, boolean cures, Supplier<StatusEffectInstance>... effects) {
        super(settings);
        this.drink = drink;
        this.cures = cures;
        this.effects = List.of(effects);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            if (cures) {
                List<StatusEffectInstance> active = new ArrayList<>(user.getStatusEffects());
                for (StatusEffectInstance e : active) {
                    if (e.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL) {
                        user.removeStatusEffect(e.getEffectType());
                    }
                }
            }
            for (Supplier<StatusEffectInstance> e : effects) {
                user.addStatusEffect(e.get());
            }
        }
        if (user instanceof ServerPlayerEntity sp) {
            Criteria.CONSUME_ITEM.trigger(sp, stack);
        }
        if (user instanceof PlayerEntity p && !p.getAbilities().creativeMode) {
            stack.decrement(1);
            if (drink) {
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) {
                    return bottle;
                }
                if (!p.getInventory().insertStack(bottle)) {
                    p.dropItem(bottle, false);
                }
            }
        }
        return stack;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return drink ? 32 : 24;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return drink ? UseAction.DRINK : UseAction.EAT;
    }

    @Override
    public SoundEvent getDrinkSound() {
        return SoundEvents.ENTITY_GENERIC_DRINK;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }
}
