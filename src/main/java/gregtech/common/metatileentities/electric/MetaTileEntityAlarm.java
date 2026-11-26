package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetaTileEntityAlarm extends TieredMetaTileEntity {

    private SoundEvent selectedSound;
    private boolean isActive;
    private static final int DEFAULT_RADIUS = 64;
    private int radius = DEFAULT_RADIUS;
    public static final int BASE_EU_CONSUMPTION = 4;
    private static final int MAX_RADIUS = 128;

    public MetaTileEntityAlarm(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 1);
        selectedSound = GTSoundEvents.DEFAULT_ALARM;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityAlarm(metaTileEntityId);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.uses_per_tick", BASE_EU_CONSUMPTION));
        tooltip.add(
                I18n.format("gregtech.universal.tooltip.energy_storage_capacity", energyContainer.getEnergyCapacity()));
    }

    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.isActive) {
            Textures.ALARM_OVERLAY_ACTIVE.renderSided(getFrontFacing(), renderState, translation, pipeline);
        } else {
            Textures.ALARM_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        StringSyncValue sound = new StringSyncValue(() -> getNameOfSound(this.selectedSound), this::selectSound);
        IntSyncValue radius = new IntSyncValue(() -> this.radius, this::setRadius);
        guiSyncManager.syncValue("alarm_sound", sound);
        guiSyncManager.syncValue("alarm_radius", radius);

        List<String> names = new ArrayList<>();
        for (SoundEvent event : getSounds()) {
            names.add(getNameOfSound(event));
        }
        return GTGuis.createPanel(this, 240, 86)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(10, 5))
                .child(new DropdownWidget<>("alarm_sounds", String.class)
                        .options(names)
                        .optionToWidget((name, selected) -> IKey.str(name).color(0x555555).asWidget())
                        .value(sound)
                        .pos(10, 20).size(220, 20))
                .child(IKey.lang("gregtech.gui.alarm.radius").asWidget().pos(10, 44))
                .child(new TextFieldWidget()
                        .pos(10, 54).size(220, 20)
                        .background(GTGuiTextures.DISPLAY)
                        .setTextColor(Color.WHITE.main)
                        .setMaxLength(10)
                        .setNumbers(0, MAX_RADIUS)
                        .value(radius));
    }

    /** Selects one of the offered sounds by name; the server ignores any other name a client sends. */
    private void selectSound(String name) {
        SoundEvent newSound = null;
        for (SoundEvent sound : getSounds()) {
            if (getNameOfSound(sound).equals(name)) {
                newSound = sound;
                break;
            }
        }
        if (newSound == null || newSound == this.selectedSound) return;
        this.selectedSound = newSound;
        if (getWorld() == null) return;
        if (getWorld().isRemote) {
            GregTechAPI.soundManager.stopTileSound(getPos());
        } else {
            writeCustomData(GregtechDataCodes.UPDATE_SOUND,
                    writer -> writer.writeResourceLocation(getResourceLocationOfSound(this.selectedSound)));
            markDirty();
        }
    }

    /** Sets the radius in blocks, within 0 to {@value MAX_RADIUS} whatever a client sends. */
    private void setRadius(int radius) {
        int newRadius = MathHelper.clamp(radius, 0, MAX_RADIUS);
        if (newRadius == this.radius) return;
        this.radius = newRadius;
        if (getWorld() != null && !getWorld().isRemote) {
            writeCustomData(GregtechDataCodes.UPDATE_RADIUS, writer -> writer.writeInt(newRadius));
            markDirty();
        }
    }

    protected List<SoundEvent> getSounds() {
        if (GTValues.FOOLS.get() && ConfigHolder.misc.specialEvents) {
            return Arrays.asList(GTSoundEvents.DEFAULT_ALARM, GTSoundEvents.ARC, SoundEvents.ENTITY_WOLF_HOWL,
                    SoundEvents.ENTITY_ENDERMEN_DEATH, GTSoundEvents.SUS_RECORD);
        }
        return Arrays.asList(GTSoundEvents.DEFAULT_ALARM, GTSoundEvents.ARC, SoundEvents.ENTITY_WOLF_HOWL,
                SoundEvents.ENTITY_ENDERMEN_DEATH);
    }

    @Override
    public SoundEvent getSound() {
        return selectedSound;
    }

    /**
     * A query only: the energy is drained once per tick in {@link #update()}, which also decides this state.
     */
    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public void update() {
        super.update();
        if (!this.getWorld().isRemote) {
            boolean active = this.isBlockRedstonePowered() &&
                    this.energyContainer.getEnergyStored() >= BASE_EU_CONSUMPTION &&
                    this.energyContainer.changeEnergy(-BASE_EU_CONSUMPTION) == -BASE_EU_CONSUMPTION;
            if (this.isActive != active) {
                this.isActive = active;
                this.writeCustomData(GregtechDataCodes.UPDATE_ACTIVE, (writer) -> writer.writeBoolean(active));
            }
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_ACTIVE) {
            this.isActive = buf.readBoolean();
            this.scheduleRenderUpdate();
        } else if (dataId == GregtechDataCodes.UPDATE_SOUND) {
            this.selectedSound = soundOrDefault(buf.readResourceLocation());
            GregTechAPI.soundManager.stopTileSound(getPos());
        } else if (dataId == GregtechDataCodes.UPDATE_RADIUS) {
            this.radius = buf.readInt();
            GregTechAPI.soundManager.stopTileSound(getPos());
        }
    }

    @Override
    public float getVolume() {
        return radius / 16f;
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.isActive = buf.readBoolean();
        this.selectedSound = soundOrDefault(buf.readResourceLocation());
        this.radius = buf.readInt();
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(this.isActive);
        buf.writeResourceLocation(getResourceLocationOfSound(this.selectedSound));
        buf.writeInt(this.radius);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        data.setBoolean("isActive", this.isActive);
        data.setString("selectedSound", getNameOfSound(this.selectedSound));
        data.setInteger("radius", this.radius);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        this.isActive = data.getBoolean("isActive");
        this.selectedSound = soundOrDefault(new ResourceLocation(data.getString("selectedSound")));
        this.radius = data.hasKey("radius", Constants.NBT.TAG_ANY_NUMERIC) ?
                MathHelper.clamp(data.getInteger("radius"), 0, MAX_RADIUS) : DEFAULT_RADIUS;
        super.readFromNBT(data);
    }

    /**
     * @return the registered sound of that name, or the default alarm for one that is not registered (saved without
     *         a sound, or from a mod no longer installed), which the alarm could neither sync nor save
     */
    private static @NotNull SoundEvent soundOrDefault(@NotNull ResourceLocation name) {
        SoundEvent sound = SoundEvent.REGISTRY.getObject(name);
        return sound != null ? sound : GTSoundEvents.DEFAULT_ALARM;
    }

    public String getNameOfSound(SoundEvent sound) {
        return getResourceLocationOfSound(sound).toString();
    }

    public ResourceLocation getResourceLocationOfSound(SoundEvent sound) {
        return SoundEvent.REGISTRY.getNameForObject(sound);
    }
}
