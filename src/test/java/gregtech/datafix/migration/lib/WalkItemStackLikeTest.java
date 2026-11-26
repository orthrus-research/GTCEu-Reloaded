package gregtech.datafix.migration.lib;

import gregtech.datafix.GTFixType;
import gregtech.datafix.walker.WalkItemStackLike;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.datafix.IDataFixer;
import net.minecraft.util.datafix.IFixType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WalkItemStackLikeTest {

    @Test
    void walksStacksSavedWithAByteOrIntCountAndSkipsOtherTags() {
        NBTTagCompound vanilla = stack();
        vanilla.setByte("Count", (byte) 1);
        NBTTagCompound large = stack();
        large.setInteger("Count", 4096);
        NBTTagCompound notAStack = new NBTTagCompound();
        notAStack.setString("id", "gregtech:mte");
        notAStack.setShort("Damage", (short) 5000);

        NBTTagList items = new NBTTagList();
        items.appendTag(vanilla);
        items.appendTag(notAStack);
        NBTTagCompound machine = new NBTTagCompound();
        machine.setTag("ImportInventory", items);
        machine.setTag("Buffer", large);
        NBTTagCompound blockEntity = new NBTTagCompound();
        blockEntity.setTag("MetaTileEntity", machine);

        List<NBTTagCompound> walked = new ArrayList<>();
        new WalkItemStackLike().process(new RecordingFixer(walked), blockEntity, 0);

        // a compound's keys are walked in hash order
        assertEquals(2, walked.size());
        assertEquals(Set.of(vanilla, large), new HashSet<>(walked));
    }

    private static NBTTagCompound stack() {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setString("id", "gregtech:mte");
        stack.setShort("Damage", (short) 5000);
        return stack;
    }

    private record RecordingFixer(List<NBTTagCompound> walked) implements IDataFixer {

        @Override
        public NBTTagCompound process(IFixType type, NBTTagCompound compound, int versionIn) {
            assertSame(GTFixType.ITEM_STACK_LIKE, type);
            walked.add(compound);
            return compound;
        }
    }
}
