package dev.underdeep.survival;

import dev.underdeep.registry.Reg;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/** Registers the Stage 7 foods, drinks, medicines and materials described in content.json. */
public final class SurvivalItems {
    private SurvivalItems() {
    }

    public static final Map<String, Item> ITEMS = new HashMap<>();

    public static void init() {
        for (SurvivalContent.FoodDef d : SurvivalContent.FOODS.values()) {
            FoodComponent.Builder b = new FoodComponent.Builder().nutrition(d.nutrition()).saturationModifier(d.saturation());
            if (d.drink()) {
                b.alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE);
            } else if (d.bowl()) {
                b.usingConvertsTo(Items.BOWL);
            }
            if (d.nutrition() <= 3 && !d.drink()) {
                b.snack();
            }
            int stack = d.stack() > 0 ? d.stack() : d.bowl() || d.drink() ? 16 : 64;
            ITEMS.put(d.id(), Reg.item(d.id(), new MealItem(new Item.Settings().food(b.build()).maxCount(stack), d)));
        }
        for (SurvivalContent.MedicineDef d : SurvivalContent.MEDICINES.values()) {
            ITEMS.put(d.id(), Reg.item(d.id(), new MedicineItem(new Item.Settings().maxCount(16), d)));
        }
        for (String m : SurvivalContent.MATERIALS) {
            ITEMS.put(m, Reg.item(m, m.equals("compost") ? new CompostItem(new Item.Settings()) : new Item(new Item.Settings())));
        }
    }

    /** After all items exist: compost chances (vanilla composter and our compost bin). */
    public static void postInit() {
        for (Map.Entry<String, Float> e : SurvivalContent.COMPOSTABLE.entrySet()) {
            Item it = Registries.ITEM.get(Identifier.of(e.getKey()));
            if (it != Items.AIR && !(CompostingChanceRegistry.INSTANCE.get(it) > 0)) {
                CompostingChanceRegistry.INSTANCE.add(it, e.getValue());
            }
        }
        for (SurvivalContent.FoodDef d : SurvivalContent.FOODS.values()) {
            Item it = ITEMS.get(d.id());
            if (!(CompostingChanceRegistry.INSTANCE.get(it) > 0) && !d.drink()) {
                CompostingChanceRegistry.INSTANCE.add(it, 0.85f);
            }
        }
    }

    public static Item get(String id) {
        Item it = ITEMS.get(id);
        if (it == null) {
            it = Registries.ITEM.get(Reg.id(id));
        }
        return it;
    }

    /**
     * Compost: a gentle fertiliser. Used on a growing crop it advances exactly one growth stage (bone meal jumps
     * several); on other growing plants (bushes, herbs, saplings, fungi) it has an even chance to make them grow once.
     */
    public static class CompostItem extends Item {
        public CompostItem(Settings settings) {
            super(settings);
        }

        @Override
        public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext ctx) {
            World world = ctx.getWorld();
            net.minecraft.util.math.BlockPos pos = ctx.getBlockPos();
            net.minecraft.block.BlockState s = world.getBlockState(pos);
            if (!(s.getBlock() instanceof net.minecraft.block.Fertilizable f) || !f.isFertilizable(world, pos, s)
                    || Registries.BLOCK.getId(s.getBlock()).getPath().startsWith("relic")) {
                return ActionResult.PASS;
            }
            if (world instanceof net.minecraft.server.world.ServerWorld sw) {
                if (s.getBlock() instanceof net.minecraft.block.CropBlock crop) {
                    sw.setBlockState(pos, crop.withAge(Math.min(crop.getMaxAge(), crop.getAge(s) + 1)), net.minecraft.block.Block.NOTIFY_LISTENERS);
                } else if (sw.random.nextBoolean() && f.canGrow(sw, sw.random, pos, s)) {
                    f.grow(sw, sw.random, pos, s);
                }
                sw.syncWorldEvent(net.minecraft.world.WorldEvents.BONE_MEAL_USED, pos, 0);
                if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().creativeMode) {
                    ctx.getStack().decrement(1);
                }
            }
            return ActionResult.success(world.isClient);
        }
    }

    /** A food or drink with bounded meal buffs. */
    public static class MealItem extends Item {
        public final SurvivalContent.FoodDef def;

        public MealItem(Settings settings, SurvivalContent.FoodDef def) {
            super(settings);
            this.def = def;
        }

        @Override
        public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
            if (!world.isClient) {
                Meals.applyBuffs(user, def.buffs(), true);
            }
            return super.finishUsing(stack, world, user);
        }

        @Override
        public UseAction getUseAction(ItemStack stack) {
            return def.drink() ? UseAction.DRINK : UseAction.EAT;
        }

        @Override
        public SoundEvent getEatSound() {
            return def.drink() ? SoundEvents.ENTITY_GENERIC_DRINK : super.getEatSound();
        }
    }

    /**
     * Medicine: drunk (tinctures, in a bottle that is returned) or applied (salves, bandages, kits). Has a per-item
     * cooldown so strong medicine cannot be chained. Applied medicine can be used on another player, a tamed animal or
     * an inhabitant by right-clicking them (cooperative care).
     */
    public static class MedicineItem extends Item {
        public final SurvivalContent.MedicineDef def;

        public MedicineItem(Settings settings, SurvivalContent.MedicineDef def) {
            super(settings);
            this.def = def;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            return ItemUsage.consumeHeldItem(world, user, hand);
        }

        @Override
        public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
            if (!world.isClient) {
                treat(user);
                if (user instanceof ServerPlayerEntity sp) {
                    Criteria.CONSUME_ITEM.trigger(sp, stack);
                    dev.underdeep.guide.Advancements.event(sp, "medicine_used", Registries.ITEM.getId(this).getPath());
                }
            }
            if (user instanceof PlayerEntity p) {
                p.getItemCooldownManager().set(this, cooldown(p));
                if (!p.getAbilities().creativeMode) {
                    stack.decrement(1);
                    if (def.drink()) {
                        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                        if (stack.isEmpty()) {
                            return bottle;
                        }
                        if (!p.getInventory().insertStack(bottle)) {
                            p.dropItem(bottle, false);
                        }
                    }
                }
            }
            return stack;
        }

        int cooldown(PlayerEntity p) {
            int t = def.cooldownSeconds() * 20;
            return dev.underdeep.equipment.Accessories.flags(p).contains("healer") ? t * 3 / 4 : t;
        }

        /** Applies this medicine to a living entity (server side). */
        public void treat(LivingEntity target) {
            for (Identifier c : def.cures()) {
                if (c.getPath().equals("fire")) {
                    target.extinguish();
                    continue;
                }
                RegistryEntry<StatusEffect> e = SurvivalEffects.byId(c);
                if (e != null) {
                    target.removeStatusEffect(e);
                }
            }
            if (def.id().equals("purging_draught")) {
                target.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.NAUSEA, 100, 0));
            }
            for (SurvivalContent.Buff b : def.effects()) {
                Meals.apply(target, SurvivalEffects.byId(b.effect()), b.seconds() * 20, b.amplifier(), false);
            }
            if (def.heal() > 0) {
                target.heal(def.heal());
            }
        }

        @Override
        public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
            if (def.drink() || user.getItemCooldownManager().isCoolingDown(this)) {
                return ActionResult.PASS;
            }
            if (!user.getWorld().isClient) {
                treat(entity);
                user.getItemCooldownManager().set(this, cooldown(user));
                if (!user.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                user.getWorld().playSound(null, entity.getBlockPos(), SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, 0.8f, 1.2f);
                user.sendMessage(Text.literal("You treat " + entity.getDisplayName().getString() + ".").formatted(Formatting.GREEN), true);
                if (entity instanceof PlayerEntity other && other != user) {
                    other.sendMessage(Text.literal(user.getName().getString() + " treats you.").formatted(Formatting.GREEN), true);
                }
            }
            return ActionResult.success(user.getWorld().isClient);
        }

        @Override
        public int getMaxUseTime(ItemStack stack, LivingEntity user) {
            return def.drink() ? 32 : 24;
        }

        @Override
        public UseAction getUseAction(ItemStack stack) {
            return def.drink() ? UseAction.DRINK : UseAction.BOW;
        }
    }
}
