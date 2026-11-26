package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.*;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.inventory.appeng.SerializableFluidList;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.fluids.util.AEFluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityMEOutputHatch extends MetaTileEntityMEOutputPart<IAEFluidStack, SerializableFluidList>
                                         implements IMultiblockAbilityPart<IFluidTank> {

    public final static String FLUID_BUFFER_TAG = "FluidBuffer";

    public MetaTileEntityMEOutputHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, MEStackType.FLUIDS, FLUID_BUFFER_TAG);
    }

    @Override
    protected SerializableFluidList createBuffer() {
        return new SerializableFluidList();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEOutputHatch(this.metaTileEntityId);
    }

    @Override
    protected void enableInfiniteSink(MultiblockWithDisplayBase controller) {
        controller.enableFluidInfSink();
    }

    @Override
    protected SimpleOverlayRenderer getFrontOverlay(boolean online) {
        return online ? Textures.ME_OUTPUT_HATCH_ACTIVE : Textures.ME_OUTPUT_HATCH;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.fluid_hatch.export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.fluid_export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.fluid_export.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public MultiblockAbility<IFluidTank> getAbility() {
        return MultiblockAbility.EXPORT_FLUIDS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(new InaccessibleInfiniteTank(this, getInternalBuffer(), this.getController()));
    }

    private static class InaccessibleInfiniteTank extends InaccessibleInfiniteHandler<IAEFluidStack>
                                                  implements IFluidTank {

        public InaccessibleInfiniteTank(MetaTileEntity holder, IItemList<IAEFluidStack> internalBuffer,
                                        MetaTileEntity mte) {
            super(holder, internalBuffer, mte);
        }

        @Nullable
        @Override
        public FluidStack getFluid() {
            return null;
        }

        @Override
        public int getFluidAmount() {
            return 0;
        }

        @Override
        public int getCapacity() {
            return Integer.MAX_VALUE - 1;
        }

        @Override
        public FluidTankInfo getInfo() {
            return null;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null) {
                return 0;
            }
            if (doFill) {
                insert(AEFluidStack.fromFluidStack(resource));
            }
            this.trigger();
            return resource.amount;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }
}
