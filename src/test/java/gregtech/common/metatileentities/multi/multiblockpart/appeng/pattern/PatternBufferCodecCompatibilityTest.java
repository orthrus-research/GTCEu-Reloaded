package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternBufferCodecCompatibilityTest {

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    @Test
    void nativeIntegerIdentityCountsRestorePatternSharedAndPrivateOwnership() {
        PatternBufferStorage storage = populatedStorage();
        NBTTagCompound canonical = storage.write();
        NBTTagCompound nativeSave = canonical.copy();
        for (int kind = 0; kind < 3; kind++) identity(nativeSave, kind).setInteger("Count", 1);
        NBTTagCompound unchanged = nativeSave.copy();

        PatternBufferStorage reload = storage(() -> {});
        reload.read(nativeSave);

        assertFalse(reload.isQuarantined(), "AE2 UEL writes integer Count identities in the actual runtime");
        assertEquals(unchanged, nativeSave, "Decoding must not normalize the caller's recovery payload in place");
        assertEquals(storage.getIdentity(), reload.getIdentity());
        assertEquals(storage.getIncarnation(), reload.getIncarnation());
        assertEquals(1, reload.getBatches().size());
        PatternBufferBatch batch = reload.getBatches().get(0);
        assertEquals(storage.getBatches().get(0).getId(), batch.getId());
        assertEquals(0, batch.getPattern());
        assertFalse(batch.isRefundPending());
        assertEquals(513, batch.getItems().getStackInSlot(0).getCount());
        assertEquals("private", batch.getItems().getStackInSlot(0).getTagCompound().getString("marker"));
        assertEquals(1347, batch.getFluids().getTankAt(0).getFluidAmount());
        assertEquals("private-fluid", batch.getFluids().getTankAt(0).getFluid().tag.getString("batch"));
        assertEquals(12, reload.getSharedItems().getStackInSlot(0).getCount());
        assertEquals(321, reload.getSharedFluids().getTankAt(0).getFluidAmount());
        assertEquals("shared-fluid", reload.getSharedFluids().getTankAt(0).getFluid().tag.getString("batch"));
        assertTrue(reload.ownsPattern(0));
        assertTrue(reload.getPatterns().extractItem(0, 1, false).isEmpty());
        identity(nativeSave, 2).getCompoundTag("tag").setString("marker", "changed after decode");
        assertEquals("private", batch.getItems().getStackInSlot(0).getTagCompound().getString("marker"));
        assertEquals(canonical, reload.write(), "New saves use canonical identity Count without changing owned Amount");
    }

    @Test
    void malformedIdentityCountTypesAndValuesQuarantineTheExactWholePayload() {
        List<NBTBase> malformed = Arrays.asList(null,
                new NBTTagByte((byte) 0), new NBTTagByte((byte) -1), new NBTTagByte((byte) 2),
                new NBTTagInt(0), new NBTTagInt(-1), new NBTTagInt(2), new NBTTagInt(257),
                new NBTTagInt(Integer.MAX_VALUE), new NBTTagShort((short) 1), new NBTTagLong(1),
                new NBTTagFloat(1), new NBTTagDouble(1), new NBTTagString("1"),
                new NBTTagCompound(), new NBTTagList());
        NBTTagCompound valid = populatedStorage().write();
        for (int kind = 0; kind < 3; kind++) {
            for (NBTBase count : malformed) {
                NBTTagCompound bad = valid.copy();
                if (count == null) identity(bad, kind).removeTag("Count");
                else identity(bad, kind).setTag("Count", count.copy());
                String context = "identity kind=" + kind + " Count=" + count;
                PatternBufferStorage reload = storage(() -> {});
                reload.read(bad);
                assertTrue(reload.isQuarantined(), context);
                assertFalse(reload.canAccept(), context);
                assertTrue(reload.getBatches().isEmpty(), context);
                assertTrue(reload.getPatterns().getStackInSlot(0).isEmpty(), context);
                assertTrue(reload.getSharedItems().getStackInSlot(0).isEmpty(), context);
                assertEquals(0, reload.getSharedFluids().getTankAt(0).getFluidAmount(), context);
                assertEquals(bad, reload.write(), context);
            }
        }
    }

    @Test
    void writerKeepsCanonicalIdentityCountSeparateFromLargeOwnedAmount() {
        ItemStack owned = new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE);
        owned.setTagCompound(new NBTTagCompound());
        owned.getTagCompound().setString("marker", "owned");
        ItemStack before = owned.copy();

        NBTTagCompound encoded = PatternBufferCodec.writeItem(owned);

        assertTrue(encoded.getCompoundTag("Stack").hasKey("Count", 1));
        assertEquals(1, encoded.getCompoundTag("Stack").getByte("Count"));
        assertEquals(Integer.MAX_VALUE, encoded.getInteger("Amount"));
        assertTrue(ItemStack.areItemStacksEqual(before, owned));
        assertTrue(ItemStack.areItemStacksEqual(owned, PatternBufferCodec.readItem(encoded)));
    }

    private static PatternBufferStorage populatedStorage() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 513);
        iron.setTagCompound(new NBTTagCompound());
        iron.getTagCompound().setString("marker", "private");
        ItemStack fluid = drop(1347, "private-fluid");
        assertTrue(storage.accept(0, details(iron, fluid), table(iron, fluid), true));
        assertTrue(storage.getSharedItems().insertItem(0, new ItemStack(Items.FEATHER, 12), false).isEmpty());
        assertEquals(321,
                storage.getSharedFluids().fill(PatternBufferTokens.fluid(drop(321, "shared-fluid"), true), true));
        return storage;
    }

    private static NBTTagCompound identity(NBTTagCompound state, int kind) {
        if (kind == 0) return state.getTagList("Patterns", 10).getCompoundTagAt(0).getCompoundTag("Stack");
        if (kind == 1) return state.getTagList("SharedItems", 10).getCompoundTagAt(0).getCompoundTag("Stack");
        return state.getTagList("Batches", 10).getCompoundTagAt(0).getTagList("Items", 10)
                .getCompoundTagAt(0).getCompoundTag("Stack");
    }
}
