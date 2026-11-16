package net.minecraft.network.chat;

import com.google.gson.JsonObject;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Kiln's stand-in for {@code HoverEvent}: what shows when a piece of chat is hovered, a text or an item. The game gets
 * it as vanilla's JSON ({@link #kiln$json}).
 */
public interface HoverEvent {
    Action action();

    /** Kiln's own: vanilla's JSON, {@code {"action":"show_text","value":{...}}}. */
    JsonObject kiln$json();

    enum Action implements StringRepresentable {
        SHOW_TEXT("show_text"),
        SHOW_ITEM("show_item"),
        SHOW_ENTITY("show_entity");

        private final String name;

        Action(String name) {
            this.name = name;
        }

        public boolean isAllowedFromServer() {
            return true;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Override
        public String toString() {
            return "<action " + name + ">";
        }
    }

    record ShowText(Component value) implements HoverEvent {
        @Override
        public Action action() {
            return Action.SHOW_TEXT;
        }

        @Override
        public JsonObject kiln$json() {
            JsonObject json = new JsonObject();
            json.addProperty("action", Action.SHOW_TEXT.getSerializedName());
            json.add("value", value.kiln$json());
            return json;
        }
    }

    record ShowItem(ItemStackTemplate item) implements HoverEvent {
        @Override
        public Action action() {
            return Action.SHOW_ITEM;
        }

        @Override
        public JsonObject kiln$json() {
            JsonObject json = new JsonObject();
            json.addProperty("action", Action.SHOW_ITEM.getSerializedName());
            json.addProperty("id", item.item().value().kiln$id().toString());
            if (item.count() != 1) {
                json.addProperty("count", item.count());
            }
            return json;
        }
    }
}
