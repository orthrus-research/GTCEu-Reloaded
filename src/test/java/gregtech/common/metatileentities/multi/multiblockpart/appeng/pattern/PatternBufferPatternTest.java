package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternBufferPatternTest {

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    @Test
    void ordinarySparseProcessingArrayIsValidAndFactoryReceivesAnOwnedPatternCopy() {
        PatternBufferTestPatternItem item = new PatternBufferTestPatternItem();
        ItemStack encoded = new ItemStack(item);
        encoded.setTagCompound(new NBTTagCompound());
        encoded.getTagCompound().setString("metadata", "preserve");
        ICraftingPatternDetails details = PatternBufferPatterns.decode(encoded, null, false);
        assertNotNull(details, "AE2 processing arrays have 16 positions with null empty slots");
        assertEquals(16, details.getInputs().length);
        assertEquals(1, details.getCondensedInputs().length);
        details.getPattern().getTagCompound().setString("metadata", "changed by consumer");
        assertEquals("preserve", encoded.getTagCompound().getString("metadata"));
        assertEquals(1, item.decodes);
    }

    @Test
    void advertisementsRespectBaseSixteenAndBridgeHundredInputTablesButDoNotRestrictOutputArraysToSixteen() {
        PatternBufferTestPatternItem item = new PatternBufferTestPatternItem();
        ItemStack encoded = new ItemStack(item);
        item.full = new IAEItemStack[17];
        item.full[16] = item.condensed[0];
        assertNull(PatternBufferPatterns.decode(encoded, null, false),
                "The positive seventeenth input cannot reach a base CPU");
        assertNotNull(PatternBufferPatterns.decode(encoded, null, true));
        item.full = new IAEItemStack[100];
        item.full[99] = item.condensed[0];
        assertNotNull(PatternBufferPatterns.decode(encoded, null, true));
        item.full = new IAEItemStack[101];
        item.full[100] = item.condensed[0];
        assertNull(PatternBufferPatterns.decode(encoded, null, true));
        item.full = new IAEItemStack[16];
        item.full[15] = item.condensed[0];
        item.outputs = new IAEItemStack[17];
        java.util.Arrays.fill(item.outputs, AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND)));
        assertNotNull(PatternBufferPatterns.decode(encoded, null, false),
                "Outputs are not indexed into the CPU input table");
    }

    @Test
    void overlongKnownBaseInputListsRejectBeforeAnUpstreamFactoryCanTruncateThem() {
        for (String key : new String[] { "Inputs", "in" }) {
            PatternBufferTestPatternItem item = new PatternBufferTestPatternItem();
            ItemStack encoded = new ItemStack(item);
            encoded.setTagCompound(new NBTTagCompound());
            NBTTagList list = new NBTTagList();
            for (int i = 0; i < 17; i++)
                list.appendTag(new ItemStack(Items.IRON_INGOT).writeToNBT(new NBTTagCompound()));
            encoded.getTagCompound().setTag(key, list);
            assertNull(PatternBufferPatterns.decode(encoded, null, false));
            assertEquals(0, item.decodes);
            assertNotNull(PatternBufferPatterns.decode(encoded, null, true));
            assertEquals(1, item.decodes);
        }
    }

    @Test
    void inconsistentCondensedRequirementsEmptyEntriesAndOverlongArraysAreRejected() {
        PatternBufferTestPatternItem item = new PatternBufferTestPatternItem();
        ItemStack encoded = new ItemStack(item);
        item.condensed = new IAEItemStack[] { AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT, 2)) };
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
        item.condensed = new IAEItemStack[] { null };
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
        item.condensed = new IAEItemStack[] { item.full[0] };
        item.full = new IAEItemStack[101];
        item.full[0] = item.condensed[0];
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
        item.full = new IAEItemStack[16];
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
    }

    @Test
    void unsupportedModesAndMismatchedPatternIdentityDoNotAdvertise() {
        PatternBufferTestPatternItem item = new PatternBufferTestPatternItem();
        ItemStack encoded = new ItemStack(item);
        item.crafting = true;
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
        item.crafting = false;
        item.substitute = true;
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
        item.substitute = false;
        item.differentIdentity = true;
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
        assertNull(PatternBufferPatterns.decode(new ItemStack(Items.PAPER), null, false));
    }

    @Test
    void malformedPresentListsModesAndOversizedPatternRejectBeforeFactoryCalls() {
        PatternBufferTestPatternItem item = new PatternBufferTestPatternItem();
        for (String key : new String[] { "Inputs", "Outputs", "in", "out", "crafting", "substitute" }) {
            ItemStack encoded = new ItemStack(item);
            encoded.setTagCompound(new NBTTagCompound());
            encoded.getTagCompound().setString(key, "wrong type");
            assertNull(PatternBufferPatterns.decode(encoded, null, false));
        }
        ItemStack encoded = new ItemStack(item);
        encoded.setTagCompound(new NBTTagCompound());
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("wrong list member"));
        encoded.getTagCompound().setTag("in", list);
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
        encoded.setTagCompound(new NBTTagCompound());
        encoded.getTagCompound().setByteArray("tooLarge", new byte[PatternBufferCodec.MAX_STACK_BYTES]);
        assertNull(PatternBufferPatterns.decode(encoded, null, false));
        assertEquals(0, item.decodes);
    }
}
