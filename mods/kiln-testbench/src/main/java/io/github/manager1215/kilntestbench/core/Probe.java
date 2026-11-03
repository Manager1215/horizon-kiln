package io.github.manager1215.kilntestbench.core;

/**
 * One call into Minecraft or the Fabric API. It passes when it returns (what it returns goes into the report), and
 * fails when it throws; {@link Skip} when what it needs isn't there.
 */
public interface Probe {
    Object run() throws Exception;
}
