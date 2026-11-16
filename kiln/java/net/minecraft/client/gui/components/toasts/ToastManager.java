package net.minecraft.client.gui.components.toasts;

/**
 * Kiln's stand-in for {@code ToastManager}: the game's toasts. A mod's {@link SystemToast} shows as the game's own;
 * a toast a mod draws itself doesn't show (said once in Kiln's log).
 */
public class ToastManager {
    public void addToast(Toast toast) {
        if (toast instanceof SystemToast system) {
            system.kiln$show(false);
        } else {
            kiln.Missing.called("toasts mods draw themselves (" + toast.getClass().getName() + ")");
        }
    }

    /** A system toast of {@code token} still shown, if Kiln showed one; Kiln knows none of another kind. */
    @SuppressWarnings("unchecked")
    public <T extends Toast> T getToast(Class<? extends T> type, Object token) {
        SystemToast shown = SystemToast.kiln$shown(token);
        return type.isInstance(shown) ? (T) shown : null;
    }

    /** The toasts shown, each in its place: Kiln draws none of the mods', so none (the game's own are Horizon's). */
    public final java.util.List<ToastInstance<?>> visibleToasts = new java.util.ArrayList<>();

    /** A toast being shown. */
    public class ToastInstance<T extends Toast> {
        private final T toast;

        ToastInstance(T toast) {
            this.toast = toast;
        }

        public T getToast() {
            return toast;
        }
    }

    public net.minecraft.client.Minecraft getMinecraft() {
        return net.minecraft.client.Minecraft.getInstance();
    }
}
