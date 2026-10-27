package gregtech.common.metatileentities.storage;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;

final class QuantumStorageTestHolder extends MetaTileEntityHolder {

    void attach(MetaTileEntity machine) {
        setRawMetaTileEntity(machine);
    }
}
