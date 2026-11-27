package dev.underdeep.mixin;

import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Stage 8 audit: read smithing recipe inputs. */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
    @Accessor("template")
    Ingredient underdeep$template();

    @Accessor("base")
    Ingredient underdeep$base();

    @Accessor("addition")
    Ingredient underdeep$addition();
}
