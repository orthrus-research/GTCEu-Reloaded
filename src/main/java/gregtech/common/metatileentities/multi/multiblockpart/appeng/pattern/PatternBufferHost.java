package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternBuffer;

import net.minecraft.inventory.InventoryCrafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;

/** The actual AE node host must expose the provider; unrelated GT holders remain unchanged. */
public final class PatternBufferHost implements IGridProxyable, ICraftingProvider {

    private final MetaTileEntityHolder holder;
    private final MetaTileEntityMEPatternBuffer buffer;

    public PatternBufferHost(MetaTileEntityHolder holder, MetaTileEntityMEPatternBuffer buffer) {
        this.holder = holder;
        this.buffer = buffer;
    }

    @Override
    public AENetworkProxy getProxy() {
        return buffer.getProxy();
    }

    @Override
    public DimensionalCoord getLocation() {
        return holder.getLocation();
    }

    @Override
    public void gridChanged() {
        buffer.gridChanged();
    }

    @Override
    public IGridNode getGridNode(AEPartLocation side) {
        return holder.getGridNode(side);
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation side) {
        return holder.getCableConnectionType(side);
    }

    @Override
    public void securityBreak() {
        buffer.onSecurityBreak();
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        buffer.provideCrafting(helper, this);
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table) {
        return buffer.pushPattern(details, table);
    }

    @Override
    public boolean isBusy() {
        return buffer.isBusy();
    }
}
