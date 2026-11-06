package gregtech.common.pipelike.fluidpipe.net;

import gregtech.Bootstrap;
import gregtech.api.fluids.attribute.FluidAttribute;
import gregtech.api.fluids.attribute.FluidAttributes;
import gregtech.api.unification.material.properties.FluidPipeProperties;
import gregtech.common.pipelike.fluidpipe.FluidPipeType;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FluidPipeAttributePersistenceTest {

    private static final FluidAttribute BASE = attribute("base");
    private static final FluidAttribute DENIED = attribute("denied");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @ParameterizedTest
    @EnumSource(FluidPipeType.class)
    void namedAllowAndDenyDecisionsSurviveSave(FluidPipeType type) {
        var material = new FluidPipeProperties(3500, 100, true, true, true, true)
                .setCanContain(BASE, true).setCanContain(DENIED, false);
        var original = type.modifyProperties(material);
        assertTrue(original.canContain(BASE), "Pipe size conversion must retain custom attributes");
        assertEquals(100 * type.capacityMultiplier, original.getThroughput());
        assertEquals(type.channels, original.getTanks());
        var net = new FluidPipeNet(new WorldFluidPipeNet("attribute_test"));
        var tag = new NBTTagCompound();
        net.writeNodeData(original, tag);
        var restored = net.readNodeData(tag);
        assertTrue(restored.canContain(BASE), "Script-defined base resistance must survive saving");
        assertFalse(restored.canContain(DENIED));
        assertEquals(Set.of(BASE, DENIED, FluidAttributes.ACID), Set.copyOf(restored.getContainedAttributes()));
        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        restored.setCanContain(BASE, false);
        assertTrue(original.canContain(BASE), "Decoded decisions must not share mutable state with their source");
    }

    @Test
    void oldSharedPropertiesRecoverPerPositionWithoutChangingMasksOrOtherNodes() {
        var graph = new WorldFluidPipeNet("old_attributes");
        var first = new BlockPos(0, 64, 0);
        var second = first.east();
        var old = new FluidPipeProperties(1000, 100, true, false, true, false);
        graph.addNode(first, old, 17, 1 << 5, true);
        graph.addNode(second, old, 17, 1 << 4, false);
        var net = graph.getNetFromPos(first);
        assertSame(net, graph.getNetFromPos(second));
        var source = net.serializeNBT();
        source.getCompoundTag("Nodes").getTagList("WireProperties", 10).getCompoundTagAt(0).removeTag("attributes");
        net.deserializeNBT(source);
        assertSame(net.getNodeAt(first).data, net.getNodeAt(second).data);
        var current = new FluidPipeProperties(1000, 100, true, false, true, false)
                .setCanContain(BASE, true).setCanContain(DENIED, false);
        assertTrue(net.synchronizeNodeAttributes(first, current));
        var updated = net.getNodeAt(first);
        assertEquals(current, updated.data);
        assertNotSame(current, updated.data);
        assertEquals(17, updated.mark);
        assertEquals(1 << 5, updated.openConnections);
        assertTrue(updated.isActive);
        assertFalse(net.getNodeAt(second).data.canContain(BASE));
        assertFalse(net.getNodeAt(second).isActive);
        assertEquals(Set.of(new net.minecraft.util.math.ChunkPos(first)), net.getContainedChunks());
        assertFalse(net.synchronizeNodeAttributes(first, current));
        var resaved = net.serializeNBT();
        net.deserializeNBT(resaved);
        assertEquals(current, net.getNodeAt(first).data);
        assertEquals(old, net.getNodeAt(second).data);
        current.setCanContain(BASE, false);
        assertTrue(net.getNodeAt(first).data.canContain(BASE));
    }

    @Test
    void containmentRecoveryDoesNotHideGeometryChangesOrCreateMissingNodes() {
        var graph = new WorldFluidPipeNet("attribute_geometry");
        var position = new BlockPos(0, 64, 0);
        var old = new FluidPipeProperties(1000, 100, true, false, true, false);
        graph.addNode(position, old, 0, 0, false);
        var net = graph.getNetFromPos(position);
        var changed = new FluidPipeProperties(1000, 101, true, false, true, false).setCanContain(BASE, true);
        assertFalse(net.synchronizeNodeAttributes(position, changed));
        assertSame(old, net.getNodeAt(position).data);
        assertFalse(net.synchronizeNodeAttributes(position.up(), changed));
        assertEquals(1, net.getAllNodes().size());
    }

    @Test
    void malformedAttributeDataIsRejected() {
        var net = new FluidPipeNet(new WorldFluidPipeNet("invalid_attributes"));
        var tag = new NBTTagCompound();
        net.writeNodeData(new FluidPipeProperties(), tag);
        tag.setString("attributes", "invalid");
        assertThrows(IllegalArgumentException.class, () -> net.readNodeData(tag));
        var attributes = new NBTTagCompound();
        attributes.setString("test:base", "true");
        tag.setTag("attributes", attributes);
        assertThrows(IllegalArgumentException.class, () -> net.readNodeData(tag));
    }

    private static FluidAttribute attribute(String name) {
        return new FluidAttribute(new ResourceLocation("test", name), lines -> {}, lines -> {});
    }
}
