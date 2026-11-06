package gregtech.integration.groovy.cache;

/** Implemented by the engine mixin; checked once before each authoring stage, never during machine execution. */
public interface GroovyCacheIntegrity {

    void gregtech$verifyScriptInputs();
}
