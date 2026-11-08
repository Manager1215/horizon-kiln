package kiln;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent;
import org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinServiceBootstrap;
import org.spongepowered.asm.service.IPropertyKey;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.ReEntranceLock;

/**
 * Kiln's service for the Mixin library (D-089): what Mixin asks of the place it runs in, the way Fabric Loader's Knot
 * answers it. Kiln has one class loader, the JVM's own (Kiln's runtime, the libraries and the mods' jars on its class
 * path), and patches classes through its Java agent ({@link Mixins}): Mixin reads class files from that class path
 * without loading them, and loads only what it runs itself (config plugins, its error handler).
 *
 * <p>Picked by the {@code mixin.service} and {@code mixin.bootstrapService} system properties, which {@link Mixins}
 * sets; the global property service by {@code META-INF/services} in Kiln's runtime jar, which comes first on the class
 * path.
 */
public final class MixinService extends MixinServiceAbstract
        implements IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    /** Mixin's transformer, offered to the service as Mixin starts. */
    static volatile IMixinTransformer transformer;
    /** The classes the JVM has loaded, as far as Kiln saw (before the agent and through it). */
    static final Set<String> LOADED = ConcurrentHashMap.newKeySet();

    private final ReEntranceLock lock = new ReEntranceLock(1);

    private static ClassLoader loader() {
        return ClassLoader.getSystemClassLoader();
    }

    @Override
    public String getName() {
        return "Kiln";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory factory) {
            transformer = factory.createTransformer();
        }
        super.offer(internal);
    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return lock;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return (required, name) -> "Kiln runs FabricMC's Mixin 0.17.5, as Fabric Loader 0.20.1 does";
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return List.of("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault", Side.class.getName());
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        try {
            return new ContainerHandleURI(MixinService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | RuntimeException e) {
            return new ContainerHandleURI(null);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return loader().getResourceAsStream(name);
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_8;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_25;
    }

    @Override
    protected ILogger createLogger(String name) {
        return new Logger(name);
    }

    // --- IClassProvider: what Mixin runs itself is loaded by the JVM's class loader, the mods' -------------------

    @Override
    @Deprecated
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader());
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader());
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader());
    }

    // --- IClassBytecodeProvider: a class file as it is on the class path, nothing loaded -------------------------

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {
        ClassNode node = new ClassNode();
        new ClassReader(bytes(name)).accept(node, readerFlags);
        return node;
    }

    /** A class's file from the class path (the JDK's too), by its binary or internal name. */
    static byte[] bytes(String name) throws ClassNotFoundException, IOException {
        try (InputStream in = loader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }
            return in.readAllBytes();
        }
    }

    // --- ITransformerProvider: no other transformers -------------------------------------------------------------

    @Override
    public Collection<ITransformer> getTransformers() {
        return List.of();
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return List.of();
    }

    @Override
    public void addTransformerExclusion(String name) {}

    // --- IClassTracker ---------------------------------------------------------------------------------------------

    @Override
    public void registerInvalidClass(String className) {}

    @Override
    public boolean isClassLoaded(String className) {
        return LOADED.contains(className.replace('.', '/'));
    }

    @Override
    public String getClassRestrictions(String className) {
        return "";
    }

    /** Kiln is the game's client (with its integrated server): Mixin's side, for configs' {@code client} lists. */
    public static final class Side extends MixinPlatformAgentAbstract implements IMixinPlatformServiceAgent {
        @Override
        public void init() {}

        @Override
        public String getSideName() {
            return Constants.SIDE_CLIENT;
        }

        @Override
        public Collection<IContainerHandle> getMixinContainers() {
            return null;
        }
    }

    /** Mixin's bootstrap service: nothing to set up before the service itself. */
    public static final class Boot implements IMixinServiceBootstrap {
        @Override
        public String getName() {
            return "Kiln";
        }

        @Override
        public String getServiceClassName() {
            return MixinService.class.getName();
        }

        @Override
        public void bootstrap() {}
    }

    /** Mixin's global properties (its blackboard), in a map. */
    public static final class Properties implements IGlobalPropertyService {
        private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();

        private record Key(String name) implements IPropertyKey {}

        @Override
        public IPropertyKey resolveKey(String name) {
            return new Key(name);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getProperty(IPropertyKey key) {
            return (T) VALUES.get(((Key) key).name());
        }

        @Override
        public void setProperty(IPropertyKey key, Object value) {
            VALUES.put(((Key) key).name(), value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getProperty(IPropertyKey key, T defaultValue) {
            return (T) VALUES.getOrDefault(((Key) key).name(), defaultValue);
        }

        @Override
        public String getPropertyString(IPropertyKey key, String defaultValue) {
            Object value = VALUES.get(((Key) key).name());
            return value != null ? value.toString() : defaultValue;
        }
    }

    /**
     * Mixin's log: everything to java.log (Java's standard error), and its warnings and errors to Kiln's log too, once
     * each, where a player sees what Mixin made of the mods' mixins.
     */
    static final class Logger extends LoggerAdapterAbstract {
        Logger(String name) {
            super(name);
        }

        @Override
        public String getType() {
            return "Kiln";
        }

        @Override
        public void catching(Level level, Throwable t) {
            log(level, "Catching", t);
        }

        @Override
        public void log(Level level, String message, Object... params) {
            FormattedMessage formatted = new FormattedMessage(message, params);
            write(level, formatted.getMessage(), formatted.getThrowable());
        }

        @Override
        public void log(Level level, String message, Throwable t) {
            write(level, message, t);
        }

        @Override
        public <T extends Throwable> T throwing(T t) {
            log(Level.WARN, "Throwing", t);
            return t;
        }

        private void write(Level level, String message, Throwable t) {
            if (level == Level.DEBUG || level == Level.TRACE) {
                return;
            }
            System.err.println("[Mixin/" + level + "] " + message);
            if (t != null) {
                t.printStackTrace();
            }
            if (level == Level.WARN || level == Level.ERROR || level == Level.FATAL) {
                Mixins.sayOnce("Mixin: " + message + (t != null && t.getMessage() != null
                        && !message.contains(t.getMessage()) ? ": " + t.getMessage() : ""));
            }
        }
    }
}
