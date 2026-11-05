package net.minecraft.client.gui.components.toasts;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code SystemToast}: a title over a message, shown by the game as its own system toast (its
 * look, its sounds, its timing) with the next tick.
 */
public class SystemToast implements Toast {
    /** The toasts Kiln showed, by token, for {@link #addOrUpdate}: the game shows each for its id's time. */
    private static final java.util.Map<SystemToastId, SystemToast> SHOWN = new java.util.WeakHashMap<>();

    private final SystemToastId id;
    private Component title;
    private Component message;
    private long shownAt;

    public SystemToast(SystemToastId id, Component title, Component message) {
        this.id = id;
        this.title = title;
        this.message = message;
    }

    public static void add(ToastManager manager, SystemToastId id, Component title, Component message) {
        manager.addToast(new SystemToast(id, title, message));
    }

    /** One of {@code id} still shown gets the new title and message (and its time again); else a new one. */
    public static void addOrUpdate(ToastManager manager, SystemToastId id, Component title, Component message) {
        SystemToast shown = kiln$shown(id);
        if (shown != null) {
            shown.reset(title, message);
        } else {
            add(manager, id, title, message);
        }
    }

    /** The one of {@code id} goes: Kiln can't take a toast back from the game, so it goes when its time is up. */
    public static void forceHide(ToastManager manager, SystemToastId id) {
        kiln.Missing.called("hiding a toast before its time");
    }

    public void reset(Component title, Component message) {
        this.title = title;
        this.message = message;
        kiln$show(true);
    }

    public void forceHide() {
        kiln.Missing.called("hiding a toast before its time");
    }

    @Override
    public SystemToastId getToken() {
        return id;
    }

    @Override
    public Visibility getWantedVisibility() {
        return System.currentTimeMillis() - shownAt < id.displayTime ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {}

    /** The game draws it. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {}

    /** Kiln's own: the game shows it, a new toast or (for {@code reset}) the one of its id still shown. */
    void kiln$show(boolean reset) {
        shownAt = System.currentTimeMillis();
        synchronized (SHOWN) {
            SHOWN.put(id, this);
        }
        kiln.Native.toast(title == null ? "" : title.getString(), message == null ? "" : message.getString(),
                id.displayTime, Integer.toHexString(System.identityHashCode(id)), reset);
    }

    /** Kiln's own: the toast of {@code token} Kiln showed, while the game still shows it. */
    static SystemToast kiln$shown(Object token) {
        synchronized (SHOWN) {
            SystemToast shown = token instanceof SystemToastId id ? SHOWN.get(id) : null;
            return shown != null && shown.getWantedVisibility() == Visibility.SHOW ? shown : null;
        }
    }

    /** What a system toast is about, and how long one shows. */
    public static class SystemToastId {
        public static final SystemToastId NARRATOR_TOGGLE = new SystemToastId();
        public static final SystemToastId WORLD_BACKUP = new SystemToastId();
        public static final SystemToastId PACK_LOAD_FAILURE = new SystemToastId();
        public static final SystemToastId WORLD_ACCESS_FAILURE = new SystemToastId();
        public static final SystemToastId PACK_COPY_FAILURE = new SystemToastId();
        public static final SystemToastId FILE_DROP_FAILURE = new SystemToastId();
        public static final SystemToastId PERIODIC_NOTIFICATION = new SystemToastId();
        public static final SystemToastId LOW_DISK_SPACE = new SystemToastId(10000L);
        public static final SystemToastId CHUNK_LOAD_FAILURE = new SystemToastId();
        public static final SystemToastId CHUNK_SAVE_FAILURE = new SystemToastId();
        public static final SystemToastId UNSECURE_SERVER_WARNING = new SystemToastId(10000L);
        public static final SystemToastId FRIEND_SYSTEM_NOTIFICATION = new SystemToastId();

        /** How long a toast of it shows, in milliseconds. */
        final long displayTime;

        public SystemToastId(long displayTime) {
            this.displayTime = displayTime;
        }

        public SystemToastId() {
            this(5000L);
        }
    }
}
