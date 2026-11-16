package net.minecraft.network.chat;

import com.google.gson.JsonObject;
import java.net.URI;
import net.minecraft.util.StringRepresentable;

/**
 * Kiln's stand-in for {@code ClickEvent}: what a click on a piece of chat does, run or suggest a command, open a link,
 * copy a text. The game gets it as vanilla's JSON ({@link #kiln$json}).
 */
public interface ClickEvent {
    Action action();

    /** Kiln's own: vanilla's JSON, {@code {"action":"suggest_command","command":"/day"}}. */
    JsonObject kiln$json();

    private static JsonObject json(Action action, String field, String value) {
        JsonObject json = new JsonObject();
        json.addProperty("action", action.getSerializedName());
        json.addProperty(field, value);
        return json;
    }

    enum Action implements StringRepresentable {
        OPEN_URL("open_url"),
        OPEN_FILE("open_file"),
        RUN_COMMAND("run_command"),
        SUGGEST_COMMAND("suggest_command"),
        SHOW_DIALOG("show_dialog"),
        CHANGE_PAGE("change_page"),
        COPY_TO_CLIPBOARD("copy_to_clipboard"),
        CUSTOM("custom");

        private final String name;

        Action(String name) {
            this.name = name;
        }

        public boolean isAllowedFromServer() {
            return this != OPEN_FILE;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    record OpenUrl(URI uri) implements ClickEvent {
        @Override
        public Action action() {
            return Action.OPEN_URL;
        }

        @Override
        public JsonObject kiln$json() {
            return json(Action.OPEN_URL, "url", uri.toString());
        }
    }

    /** A file to open, as the game's screenshot message has one: the game opens it only from its own chat. */
    record OpenFile(String path) implements ClickEvent {
        public OpenFile(java.io.File file) {
            this(file.toString());
        }

        public OpenFile(java.nio.file.Path path) {
            this(path.toFile());
        }

        public java.io.File file() {
            return new java.io.File(path);
        }

        @Override
        public Action action() {
            return Action.OPEN_FILE;
        }

        @Override
        public JsonObject kiln$json() {
            return json(Action.OPEN_FILE, "path", path);
        }
    }

    record RunCommand(String command) implements ClickEvent {
        @Override
        public Action action() {
            return Action.RUN_COMMAND;
        }

        @Override
        public JsonObject kiln$json() {
            return json(Action.RUN_COMMAND, "command", command);
        }
    }

    record SuggestCommand(String command) implements ClickEvent {
        @Override
        public Action action() {
            return Action.SUGGEST_COMMAND;
        }

        @Override
        public JsonObject kiln$json() {
            return json(Action.SUGGEST_COMMAND, "command", command);
        }
    }

    record CopyToClipboard(String value) implements ClickEvent {
        @Override
        public Action action() {
            return Action.COPY_TO_CLIPBOARD;
        }

        @Override
        public JsonObject kiln$json() {
            return json(Action.COPY_TO_CLIPBOARD, "value", value);
        }
    }

    record ChangePage(int page) implements ClickEvent {
        @Override
        public Action action() {
            return Action.CHANGE_PAGE;
        }

        @Override
        public JsonObject kiln$json() {
            JsonObject json = new JsonObject();
            json.addProperty("action", Action.CHANGE_PAGE.getSerializedName());
            json.addProperty("page", page);
            return json;
        }
    }
}
