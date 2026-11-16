package net.minecraft.world.item.crafting;

import java.util.Optional;
import net.minecraft.core.component.predicates.PotionsPredicate;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code PotionIngredient}: a brewing recipe's input or reagent, an item and, for a potion, the
 * potions it takes ({@code {"item": "minecraft:potion", "potion_contents": {"potions": "minecraft:water"}}}).
 */
public record PotionIngredient(Ingredient ingredient, Optional<PotionsPredicate> potions) {
    public boolean test(ItemStack stack) {
        return ingredient.test(stack) && potions.map(p -> p.test(stack)).orElse(true);
    }
}
