package gregtech.api.util;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class BlockUtility {

    private static final BlockWrapper WRAPPER = new BlockWrapper();

    private static final Object2DoubleMap<IBlockState> walkingSpeedBonusInternal = new Object2DoubleOpenHashMap<>();
    /**
     * View-only collection of block states that give speed bonus when walking over it. The bonus value is a percentage
     * value that gets added to the player speed; for example, a bonus value of {@link 0.25} will add 25% of extra speed
     * to the player.
     */
    public static final Object2DoubleMap<IBlockState> WALKING_SPEED_BONUS = Object2DoubleMaps.unmodifiable(
            walkingSpeedBonusInternal);

    /**
     * UUID of the walking speed bonus attribute applied to players.
     */
    public static final UUID WALKING_SPEED_UUID = UUID.fromString("415ac431-8339-4150-965c-e673a8a328be");

    /**
     * Walking speed bonus applied to asphalt and concrete blocks.
     */
    public static final double ASPHALT_WALKING_SPEED_BONUS = 0.6;
    /**
     * Walking speed bonus applied to studs.
     */
    public static final double STUDS_WALKING_SPEED_BONUS = 0.25;

    private static class BlockWrapper extends Block {

        public BlockWrapper() {
            super(Material.AIR);
        }

        @Override
        public @NotNull NonNullList<ItemStack> captureDrops(boolean start) {
            return super.captureDrops(start);
        }
    }

    public static void startCaptureDrops() {
        WRAPPER.captureDrops(true);
    }

    public static @NotNull NonNullList<ItemStack> stopCaptureDrops() {
        return WRAPPER.captureDrops(false);
    }

    /**
     * Set walking speed bonus for the block state. The bonus value is a percentage value that gets added to the player
     * speed; for example, a bonus value of {@link 0.25} will add 25% of extra speed to the player.
     *
     * @param state  block state
     * @param amount amount of walking speed bonus
     */
    public static void setWalkingSpeedBonus(@NotNull IBlockState state, double amount) {
        Objects.requireNonNull(state, "state == null");
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Haha funny i put NaN and Infinity in your API method haha no");
        }
        if (amount == 0) {
            walkingSpeedBonusInternal.remove(state);
        } else {
            walkingSpeedBonusInternal.put(state, amount);
        }
    }

    /**
     * The variant a block state's model is named by: its properties as {@code name=value}, sorted by name and
     * separated by commas, or {@code normal} for a block without properties.
     *
     * @param properties the properties of a block state, as {@link IBlockState#getProperties()} gives them
     */
    public static String statePropertiesToString(Map<IProperty<?>, Comparable<?>> properties) {
        StringBuilder stringbuilder = new StringBuilder();

        List<Entry<IProperty<?>, Comparable<?>>> entries = properties.entrySet().stream()
                .sorted(Comparator.comparing(c -> c.getKey().getName()))
                .collect(Collectors.toList());

        for (Map.Entry<IProperty<?>, Comparable<?>> entry : entries) {
            if (stringbuilder.length() != 0) {
                stringbuilder.append(",");
            }

            IProperty<?> property = entry.getKey();
            stringbuilder.append(property.getName());
            stringbuilder.append("=");
            stringbuilder.append(getPropertyName(property, entry.getValue()));
        }

        if (stringbuilder.length() == 0) {
            stringbuilder.append("normal");
        }

        return stringbuilder.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyName(IProperty<T> property, Comparable<?> value) {
        return property.getName((T) value);
    }
}
