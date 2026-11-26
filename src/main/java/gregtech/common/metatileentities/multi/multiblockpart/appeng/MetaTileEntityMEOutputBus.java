package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.*;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.inventory.appeng.SerializableItemList;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityMEOutputBus extends MetaTileEntityMEOutputPart<IAEItemStack, SerializableItemList>
                                       implements IMultiblockAbilityPart<IItemHandlerModifiable> {

    public final static String ITEM_BUFFER_TAG = "ItemBuffer";

    public MetaTileEntityMEOutputBus(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, MEStackType.ITEMS, ITEM_BUFFER_TAG);
    }

    @Override
    protected SerializableItemList createBuffer() {
        return new SerializableItemList();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEOutputBus(this.metaTileEntityId);
    }

    @Override
    protected void enableInfiniteSink(MultiblockWithDisplayBase controller) {
        controller.enableItemInfSink();
    }

    @Override
    protected SimpleOverlayRenderer getFrontOverlay(boolean online) {
        return online ? Textures.ME_OUTPUT_BUS_ACTIVE : Textures.ME_OUTPUT_BUS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.item_bus.export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.item_export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.item_export.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return MultiblockAbility.EXPORT_ITEMS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(new InaccessibleInfiniteSlot(this, getInternalBuffer(), this.getController()));
    }

    private static class InaccessibleInfiniteSlot extends InaccessibleInfiniteHandler<IAEItemStack>
                                                  implements IItemHandlerModifiable {

        public InaccessibleInfiniteSlot(MetaTileEntity holder, IItemList<IAEItemStack> internalBuffer,
                                        MetaTileEntity mte) {
            super(holder, internalBuffer, mte);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            insert(AEItemStack.fromItemStack(stack));
            this.trigger();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                insert(AEItemStack.fromItemStack(stack));
            }
            this.trigger();
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE - 1;
        }
    }
}
