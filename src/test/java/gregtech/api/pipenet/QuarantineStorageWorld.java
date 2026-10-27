package gregtech.api.pipenet;

public final class QuarantineStorageWorld extends WorldPipeNet<Integer, QuarantineStorageNetwork> {

    public QuarantineStorageWorld(String name) {
        super(name);
    }

    @Override
    protected QuarantineStorageNetwork createNetInstance() {
        return new QuarantineStorageNetwork(this);
    }
}
