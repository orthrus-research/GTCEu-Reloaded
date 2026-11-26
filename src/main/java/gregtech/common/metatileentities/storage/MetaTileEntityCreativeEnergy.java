package gregtech.common.metatileentities.storage;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.ILaserContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static gregtech.api.GTValues.MAX;
import static gregtech.api.GTValues.V;
import static gregtech.api.capability.GregtechDataCodes.UPDATE_ACTIVE;
import static gregtech.api.capability.GregtechDataCodes.UPDATE_IO_SPEED;

public class MetaTileEntityCreativeEnergy extends MetaTileEntity implements ILaserContainer, IControllable {

    private long voltage = 0;
    private int amps = 1;

    private int setTier = 0;
    private boolean active = false;
    private boolean source = true;

    private long lastEnergyIOPerSec = 0;
    private long energyIOPerSec = 0;

    private long ampsReceived = 0;
    private boolean doExplosion = false;

    public MetaTileEntityCreativeEnergy() {
        super(GTUtility.gregtechId("infinite_energy"));
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        IVertexOperation[] renderPipeline = ArrayUtils.add(pipeline,
                new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
        Textures.VOLTAGE_CASINGS[14].render(renderState, translation, renderPipeline, Cuboid6.full);
        for (EnumFacing face : EnumFacing.VALUES) {
            Textures.INFINITE_EMITTER_FACE.renderSided(face, renderState, translation, pipeline);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.VOLTAGE_CASINGS[this.setTier].getParticleSprite(), this.getPaintingColorForRendering());
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCreativeEnergy();
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER) {
            return GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER.cast(this);
        } else if (capability == GregtechTileCapabilities.CAPABILITY_LASER) {
            return GregtechTileCapabilities.CAPABILITY_LASER.cast(this);
        } else if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        } else {
            return super.getCapability(capability, side);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        // every value comes from the client, so each setter keeps it within what the old screen allowed
        IntSyncValue tier = new IntSyncValue(() -> setTier, this::setTier);
        LongSyncValue voltage = new LongSyncValue(() -> this.voltage, this::setVoltage);
        IntSyncValue amperage = new IntSyncValue(() -> amps, value -> this.amps = Math.max(0, value));
        LongSyncValue energyIO = new LongSyncValue(() -> lastEnergyIOPerSec);
        BooleanSyncValue activity = new BooleanSyncValue(() -> active, this::setActive);
        BooleanSyncValue sourcing = new BooleanSyncValue(() -> source, this::setSource);
        guiSyncManager.syncValue("creative_energy_tier", tier);
        guiSyncManager.syncValue("creative_energy_voltage", voltage);
        guiSyncManager.syncValue("creative_energy_amperage", amperage);
        guiSyncManager.syncValue("creative_energy_io", energyIO);
        guiSyncManager.syncValue("creative_energy_active", activity);
        guiSyncManager.syncValue("creative_energy_source", sourcing);

        CycleButtonWidget tierButton = new CycleButtonWidget()
                .stateCount(GTValues.VNF.length)
                .value(tier);
        for (int i = 0; i < GTValues.VNF.length; i++) {
            tierButton.stateOverlay(i, IKey.str(GTValues.VNF[i]));
        }

        return GTGuis.createPanel(this, 176, 166)
                .child(tierButton.pos(7, 7).size(30, 20))
                .child(IKey.lang("gregtech.creative.energy.voltage").asWidget().pos(7, 32))
                .child(new TextFieldWidget()
                        .pos(7, 44).size(156, 20)
                        .background(GTGuiTextures.DISPLAY)
                        .setTextColor(Color.WHITE.main)
                        .setMaxLength(19)
                        .setNumbersLong(value -> Math.max(0L, value))
                        .value(voltage))
                .child(IKey.lang("gregtech.creative.energy.amperage").asWidget().pos(7, 74))
                .child(new ButtonWidget<>()
                        .pos(7, 87).size(20)
                        .overlay(IKey.str("-"))
                        .onMousePressed(mouseButton -> {
                            amperage.setIntValue(Math.max(0, amperage.getIntValue() - 1), true, true);
                            return true;
                        }))
                .child(new TextFieldWidget()
                        .pos(29, 87).size(118, 20)
                        .background(GTGuiTextures.DISPLAY)
                        .setTextColor(Color.WHITE.main)
                        .setMaxLength(10)
                        .setNumbers(0, Integer.MAX_VALUE)
                        .value(amperage))
                .child(new ButtonWidget<>()
                        .pos(149, 87).size(20)
                        .overlay(IKey.str("+"))
                        .onMousePressed(mouseButton -> {
                            int amps = amperage.getIntValue();
                            if (amps < Integer.MAX_VALUE) amperage.setIntValue(amps + 1, true, true);
                            return true;
                        }))
                .child(IKey.lang("gregtech.creative.energy.io_per_sec",
                        () -> new Object[] { energyIO.getLongValue() })
                        .color(0x232323)
                        .asWidget().pos(7, 110))
                .child(new CycleButtonWidget()
                        .stateCount(2)
                        .value(activity)
                        .stateOverlay(0, IKey.lang("gregtech.creative.activity.off"))
                        .stateOverlay(1, IKey.lang("gregtech.creative.activity.on"))
                        .pos(7, 139).size(77, 20))
                .child(new CycleButtonWidget()
                        .stateCount(2)
                        .value(sourcing)
                        .stateOverlay(0, IKey.lang("gregtech.creative.energy.sink"))
                        .stateOverlay(1, IKey.lang("gregtech.creative.energy.source"))
                        .pos(85, 139).size(77, 20));
    }

    private void setTier(int tier) {
        this.setTier = Math.max(0, Math.min(GTValues.V.length - 1, tier));
        this.voltage = GTValues.V[this.setTier];
    }

    private void setVoltage(long voltage) {
        this.voltage = Math.max(0, voltage);
        this.setTier = GTUtility.getTierByVoltage(this.voltage);
    }

    /** A source starts with nothing to emit; a sink starts accepting everything. */
    private void setSource(boolean source) {
        this.source = source;
        if (source) {
            this.voltage = 0;
            this.amps = 0;
            this.setTier = 0;
        } else {
            this.voltage = V[MAX];
            this.amps = Integer.MAX_VALUE;
            this.setTier = MAX;
        }
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!getWorld().isRemote) {
            writeCustomData(GregtechDataCodes.UPDATE_ACTIVE, buf -> buf.writeBoolean(active));
            markDirty();
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.creative_tooltip.1") + TooltipHelper.RAINBOW +
                I18n.format("gregtech.creative_tooltip.2") + I18n.format("gregtech.creative_tooltip.3"));
    }

    @Override
    public long getOutputPerSec() {
        return lastEnergyIOPerSec;
    }

    @Override
    public void update() {
        super.update();
        if (getWorld().isRemote) return;
        if (getOffsetTimer() % 20 == 0) {
            this.setIOSpeed(energyIOPerSec);
            energyIOPerSec = 0;
            if (doExplosion) {
                getWorld().createExplosion(null, getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5,
                        1, false);
                doExplosion = false;
            }
        }
        ampsReceived = 0;
        if (!active || !source || voltage <= 0 || amps <= 0) return;
        long ampsUsed = 0;
        for (EnumFacing facing : EnumFacing.values()) {
            EnumFacing opposite = facing.getOpposite();
            TileEntity tile = getNeighbor(facing);
            if (tile != null) {
                IEnergyContainer container = tile.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                        opposite);
                // Try to get laser capability
                if (container == null)
                    container = tile.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, opposite);

                if (container == null || !container.inputsEnergy(opposite) || container.getEnergyCanBeInserted() == 0)
                    continue;
                ampsUsed += container.acceptEnergyFromNetwork(opposite, voltage, amps - ampsUsed);
                if (ampsUsed >= amps)
                    break;
            }
        }
        energyIOPerSec += ampsUsed * voltage;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        data.setLong("Voltage", voltage);
        data.setInteger("Amps", amps);
        data.setByte("Tier", (byte) setTier);
        data.setBoolean("Active", active);
        data.setBoolean("Source", source);
        data.setLong("EnergyIOPerSec", lastEnergyIOPerSec);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        voltage = data.getLong("Voltage");
        amps = data.getInteger("Amps");
        setTier = data.getByte("Tier");
        active = data.getBoolean("Active");
        source = data.getBoolean("Source");
        if (data.hasKey("EnergyIOPerSec"))
            lastEnergyIOPerSec = data.getLong("EnergyIOPerSec");
        super.readFromNBT(data);
    }

    @Override
    public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
        if (source || !active || ampsReceived >= amps) {
            return 0;
        }
        if (voltage > this.voltage) {
            if (doExplosion)
                return 0;
            doExplosion = true;
            return Math.min(amperage, getInputAmperage() - ampsReceived);
        }
        long amperesAccepted = Math.min(amperage, getInputAmperage() - ampsReceived);
        if (amperesAccepted > 0) {
            ampsReceived += amperesAccepted;
            energyIOPerSec += amperesAccepted * voltage;
            return amperesAccepted;
        }
        return 0;
    }

    @Override
    public boolean inputsEnergy(EnumFacing side) {
        return !source;
    }

    @Override
    public boolean outputsEnergy(EnumFacing side) {
        return source;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        if (source || !active) {
            return 0;
        }
        energyIOPerSec += differenceAmount;
        return differenceAmount;
    }

    @Override
    public long getEnergyStored() {
        return 69;
    }

    @Override
    public long getEnergyCapacity() {
        return 420;
    }

    @Override
    public long getInputAmperage() {
        return source ? 0 : amps;
    }

    @Override
    public long getInputVoltage() {
        return source ? 0 : voltage;
    }

    @Override
    public long getOutputVoltage() {
        return source ? voltage : 0;
    }

    @Override
    public long getOutputAmperage() {
        return source ? amps : 0;
    }

    public void setIOSpeed(long energyIOPerSec) {
        if (this.lastEnergyIOPerSec != energyIOPerSec) {
            this.lastEnergyIOPerSec = energyIOPerSec;
            this.writeCustomData(UPDATE_IO_SPEED, packetBuffer -> packetBuffer.writeLong(energyIOPerSec));
        }
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_IO_SPEED) {
            this.lastEnergyIOPerSec = buf.readLong();
        } else if (dataId == UPDATE_ACTIVE) {
            this.active = buf.readBoolean();
        }
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(active);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.active = buf.readBoolean();
    }

    @Override
    public boolean isWorkingEnabled() {
        return active;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        setActive(isWorkingAllowed);
    }
}
