package gregtech.common.pipelike.fluidpipe.net;

import gregtech.api.fluids.attribute.FluidAttribute;
import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.FluidPipeProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants.NBT;

public class FluidPipeNet extends PipeNet<FluidPipeProperties> {

    public FluidPipeNet(WorldPipeNet<FluidPipeProperties, FluidPipeNet> world) {
        super(world);
    }

    @Override
    protected void writeNodeData(FluidPipeProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("max_temperature", nodeData.getMaxFluidTemperature());
        tagCompound.setInteger("throughput", nodeData.getThroughput());
        tagCompound.setBoolean("gas_proof", nodeData.isGasProof());
        tagCompound.setBoolean("acid_proof", nodeData.isAcidProof());
        tagCompound.setBoolean("cryo_proof", nodeData.isCryoProof());
        tagCompound.setBoolean("plasma_proof", nodeData.isPlasmaProof());
        tagCompound.setInteger("channels", nodeData.getTanks());
        var attributes = new NBTTagCompound();
        for (FluidAttribute attribute : nodeData.getContainedAttributes()) {
            attributes.setBoolean(attribute.getResourceLocation().toString(), nodeData.canContain(attribute));
        }
        tagCompound.setTag("attributes", attributes);
    }

    @Override
    protected FluidPipeProperties readNodeData(NBTTagCompound tagCompound) {
        int maxTemperature = tagCompound.getInteger("max_temperature");
        int throughput = tagCompound.getInteger("throughput");
        boolean gasProof = tagCompound.getBoolean("gas_proof");
        boolean acidProof = tagCompound.getBoolean("acid_proof");
        boolean cryoProof = tagCompound.getBoolean("cryo_proof");
        boolean plasmaProof = tagCompound.getBoolean("plasma_proof");
        int channels = tagCompound.getInteger("channels");
        var properties = new FluidPipeProperties(maxTemperature, throughput, gasProof, acidProof, cryoProof,
                plasmaProof,
                channels);
        if (tagCompound.hasKey("attributes")) {
            if (!tagCompound.hasKey("attributes", NBT.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Fluid pipe attributes must be a compound");
            }
            var attributes = tagCompound.getCompoundTag("attributes");
            for (String name : attributes.getKeySet()) {
                if (!attributes.hasKey(name, NBT.TAG_BYTE)) {
                    throw new IllegalArgumentException("Fluid pipe attribute decision must be a byte: " + name);
                }
                // Saved graph keys carry matching identity; presentation belongs to the registered material.
                var attribute = new FluidAttribute(new ResourceLocation(name), lines -> {}, lines -> {});
                properties.setCanContain(attribute, attributes.getBoolean(name));
            }
        }
        return properties;
    }

    /** Restores derived containment decisions from a loaded tile without changing saved geometry or topology. */
    public boolean synchronizeNodeAttributes(BlockPos position, FluidPipeProperties current) {
        Node<FluidPipeProperties> node = getNodeAt(position);
        if (node == null) return false;
        FluidPipeProperties saved = node.data;
        if (saved.equals(current) || saved.getThroughput() != current.getThroughput() ||
                saved.getTanks() != current.getTanks() ||
                saved.getMaxFluidTemperature() != current.getMaxFluidTemperature() ||
                saved.isGasProof() != current.isGasProof() || saved.isAcidProof() != current.isAcidProof() ||
                saved.isCryoProof() != current.isCryoProof() || saved.isPlasmaProof() != current.isPlasmaProof()) {
            return false;
        }
        var restored = new FluidPipeProperties(saved.getMaxFluidTemperature(), saved.getThroughput(),
                saved.isGasProof(), saved.isAcidProof(), saved.isCryoProof(), saved.isPlasmaProof(), saved.getTanks());
        for (FluidAttribute attribute : current.getContainedAttributes()) {
            restored.setCanContain(attribute, current.canContain(attribute));
        }
        addNode(position, new Node<>(restored, node.openConnections, node.mark, node.isActive));
        worldData.invalidateRoutes();
        return true;
    }
}
