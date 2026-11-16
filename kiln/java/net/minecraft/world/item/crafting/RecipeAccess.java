package net.minecraft.world.item.crafting;

import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;

/**
 * Kiln's stand-in for {@code RecipeAccess}: what a level knows of the recipes. On the client it is what the server
 * synchronized: Kiln's are the client's recipes ({@code kiln.ClientRecipes}, D-152). Only Fabric's
 * {@code getSynchronizedRecipes} is here (a recipe viewer's plugin reads its mod's recipes through it, D-165).
 */
public interface RecipeAccess {
    /** Fabric's: the recipes the client has, the game's and the mods'. */
    default SynchronizedRecipes getSynchronizedRecipes() {
        return SynchronizedRecipes.kiln$of(kiln.ClientRecipes.all());
    }
}
