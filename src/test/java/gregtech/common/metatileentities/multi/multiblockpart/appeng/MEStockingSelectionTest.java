package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.Bootstrap;

import net.minecraft.nbt.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MEStockingSelectionTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void heapRetainsAtMostSixteenAcrossOneHundredThousandCandidates() {
        AtomicInteger comparisons = new AtomicInteger();
        MEStockingSelection<Integer> selected = new MEStockingSelection<>(16, (a, b) -> {
            comparisons.incrementAndGet();
            return Integer.compare(b, a);
        });
        for (int i = 0; i < 100000; i++) {
            selected.offer(i);
            assertTrue(selected.size() <= 16);
        }
        List<Integer> expected = IntStream.range(99984, 100000).boxed()
                .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        assertEquals(expected, selected.sorted());
        assertTrue(comparisons.get() < 1600000, "bounded heap comparison count: " + comparisons.get());
    }

    @Test
    void equalAmountCutoffAndOrderDoNotDependOnEncounterOrder() {
        List<Integer> entries = IntStream.range(0, 40).boxed().collect(Collectors.toList());
        for (int seed = 0; seed < 20; seed++) {
            Collections.shuffle(entries, new Random(seed));
            MEStockingSelection<MEStockingSelection.Candidate<Integer>> selected = new MEStockingSelection<>(16,
                    Comparator.<MEStockingSelection.Candidate<Integer>>naturalOrder());
            for (int value : entries) {
                selected.offer(new MEStockingSelection.Candidate<>(value, Long.MAX_VALUE,
                        key -> String.format(Locale.ROOT, "%03d", key)));
            }
            assertEquals(IntStream.range(0, 16).boxed().collect(Collectors.toList()),
                    selected.sorted().stream().map(MEStockingSelection.Candidate::getStack)
                            .collect(Collectors.toList()));
        }
    }

    @Test
    void fullLongAmountsRankWithoutNarrowingAndKeysAreLazy() {
        AtomicInteger keys = new AtomicInteger();
        MEStockingSelection<MEStockingSelection.Candidate<String>> selected = new MEStockingSelection<>(2,
                Comparator.<MEStockingSelection.Candidate<String>>naturalOrder());
        selected.offer(new MEStockingSelection.Candidate<>("small", Integer.MAX_VALUE + 1L,
                key -> {
                    keys.incrementAndGet();
                    return key;
                }));
        selected.offer(new MEStockingSelection.Candidate<>("large", Long.MAX_VALUE,
                key -> {
                    keys.incrementAndGet();
                    return key;
                }));
        selected.offer(new MEStockingSelection.Candidate<>("middle", 1L << 40,
                key -> {
                    keys.incrementAndGet();
                    return key;
                }));
        assertEquals(Arrays.asList("large", "middle"), selected.sorted().stream()
                .map(MEStockingSelection.Candidate::getStack).collect(Collectors.toList()));
        assertEquals(0, keys.get());
    }

    @Test
    void canonicalCompoundsIgnoreInsertionOrderRecursively() {
        NBTTagCompound first = new NBTTagCompound();
        NBTTagCompound second = new NBTTagCompound();
        first.setString("Aa", "first");
        first.setInteger("BB", 2);
        second.setInteger("BB", 2);
        second.setString("Aa", "first");
        NBTTagCompound nestedFirst = new NBTTagCompound();
        NBTTagCompound nestedSecond = new NBTTagCompound();
        nestedFirst.setTag("nested", first);
        nestedFirst.setInteger("outer", 1);
        nestedSecond.setInteger("outer", 1);
        nestedSecond.setTag("nested", second);
        assertEquals(MEStockingIdentity.nbt(nestedFirst), MEStockingIdentity.nbt(nestedSecond));
    }

    @Test
    void canonicalKeysPreserveTypesArrayContentsAndListOrder() {
        List<NBTBase> values = new ArrayList<>(Arrays.asList(new NBTTagByte((byte) 1), new NBTTagShort((short) 1),
                new NBTTagInt(1), new NBTTagLong(1), new NBTTagFloat(1), new NBTTagDouble(1), new NBTTagString("1"),
                new NBTTagByteArray(new byte[] { 1 }), new NBTTagIntArray(new int[] { 1 }),
                new NBTTagLongArray(new long[] { 1 }), new NBTTagLongArray(new long[] { 2 })));
        NBTTagList first = new NBTTagList();
        first.appendTag(new NBTTagInt(1));
        first.appendTag(new NBTTagInt(2));
        NBTTagList second = new NBTTagList();
        second.appendTag(new NBTTagInt(2));
        second.appendTag(new NBTTagInt(1));
        values.add(first);
        values.add(second);
        values.add(null);
        assertEquals(values.size(), values.stream().map(MEStockingIdentity::nbt).distinct().count());
        assertNotEquals(MEStockingIdentity.nbt(new NBTTagString("\uD800")),
                MEStockingIdentity.nbt(new NBTTagString("\uD801")));
    }
}
