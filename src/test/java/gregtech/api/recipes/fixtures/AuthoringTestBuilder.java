package gregtech.api.recipes.fixtures;

import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.properties.RecipeProperty;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagInt;

public class AuthoringTestBuilder extends RecipeBuilder<AuthoringTestBuilder> {

    public static final RecipeProperty<Integer> POWER = new ScalarProperty("extension_test_power");
    public static final RecipeProperty<Integer> PRESSURE = new ScalarProperty("extension_test_pressure");

    public AuthoringTestBuilder() {}

    protected AuthoringTestBuilder(AuthoringTestBuilder source) {
        super(source);
    }

    @Override
    public AuthoringTestBuilder copy() {
        return new AuthoringTestBuilder(this);
    }

    public void power(int value) {
        if (value <= 0) throw new IllegalArgumentException("Power must be positive");
        applyProperty(POWER, value);
    }

    @Override
    public AuthoringTestBuilder EUt(long value) {
        // Models addons whose physical power property still has an int domain.
        Math.toIntExact(value);
        return super.EUt(value);
    }

    public void pressure(int value) {
        if (value < 0) throw new IllegalArgumentException("Pressure must be nonnegative");
        applyProperty(PRESSURE, value);
    }

    private static final class ScalarProperty extends RecipeProperty<Integer> {

        private ScalarProperty(String key) {
            super(key, Integer.class);
        }

        @Override
        public NBTBase serialize(Object value) {
            return new NBTTagInt(castValue(value));
        }

        @Override
        public Object deserialize(NBTBase nbt) {
            return ((NBTTagInt) nbt).getInt();
        }

        @Override
        public void drawInfo(Minecraft minecraft, int x, int y, int color, Object value) {}
    }
}
