package gregtech.api.pipenet;

final class PipeNetPhaseWorld extends WorldPipeNet<Integer, PipeNetPhaseNetwork> {

    PipeNetPhaseWorld() {
        super("callback_phase_test");
    }

    @Override
    protected PipeNetPhaseNetwork createNetInstance() {
        return new PipeNetPhaseNetwork(this);
    }
}
