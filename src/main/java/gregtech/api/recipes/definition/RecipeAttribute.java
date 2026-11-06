package gregtech.api.recipes.definition;

import gregtech.api.recipes.conditions.RecipeConditionSet;

import net.minecraft.nbt.NBTBase;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Typed built-in property values. The compiler resolves them through native builder APIs during registration. */
public sealed interface RecipeAttribute {

    /** Engine-neutral addon payload. Both entry and exit take deep copies of mutable NBT. */
    record Custom(ResourceLocation type, NBTBase payload) implements RecipeAttribute {

        public Custom {
            type = new ResourceLocation(Objects.requireNonNull(type, "type").toString());
            payload = Objects.requireNonNull(payload, "payload").copy();
        }

        @Override
        public NBTBase payload() {
            return payload.copy();
        }
    }

    record Temperature(int kelvin) implements RecipeAttribute {}

    record FusionStart(long energy) implements RecipeAttribute {}

    record SolderMultiplier(int multiplier) implements RecipeAttribute {}

    record DistilleryRecipes(boolean enabled) implements RecipeAttribute {}

    record Recycling() implements RecipeAttribute {}

    record Research(List<ResearchRequirement> requirements) implements RecipeAttribute {

        public Research {
            requirements = List.copyOf(requirements);
        }
    }

    record ComputationRate(int perTick) implements RecipeAttribute {}

    /** Native computation recipes store total work in duration as well; both declarations must agree. */
    record TotalComputation(int amount) implements RecipeAttribute {}

    record Explosives(ItemValue stack) implements RecipeAttribute {

        public Explosives {
            Objects.requireNonNull(stack, "stack");
        }
    }

    record Cleanroom(String name) implements RecipeAttribute {

        public Cleanroom {
            Objects.requireNonNull(name, "name");
        }
    }

    record Dimensions(List<Integer> allowed, List<Integer> blocked) implements RecipeAttribute {

        public Dimensions {
            allowed = List.copyOf(allowed);
            blocked = List.copyOf(blocked);
        }
    }

    record Conditions(RecipeConditionSet conditions) implements RecipeAttribute {

        public Conditions {
            Objects.requireNonNull(conditions, "conditions");
        }
    }
}
