package com.mojang.authlib.properties;

import com.google.common.collect.ForwardingMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.Map;

/** Kiln's stand-in for authlib's {@code PropertyMap}: a profile's properties by name, which can't be changed. */
public class PropertyMap extends ForwardingMultimap<String, Property> {
    public static final PropertyMap EMPTY = new PropertyMap(ImmutableMultimap.of());

    private final Multimap<String, Property> properties;

    public PropertyMap(Multimap<String, Property> properties) {
        this.properties = ImmutableMultimap.copyOf(properties);
    }

    @Override
    protected Multimap<String, Property> delegate() {
        return properties;
    }

    /**
     * authlib's JSON form: a list of {@code {name, value, signature}} (the signature only when there is one); read also
     * as an object of names, each a list of values.
     */
    public static class Serializer implements JsonSerializer<PropertyMap>, JsonDeserializer<PropertyMap> {
        @Override
        public PropertyMap deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            Multimap<String, Property> read = LinkedHashMultimap.create();
            if (json instanceof JsonObject object) {
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (entry.getValue() instanceof JsonArray values) {
                        for (JsonElement value : values) {
                            read.put(entry.getKey(), new Property(entry.getKey(), value.getAsString()));
                        }
                    }
                }
            } else if (json instanceof JsonArray list) {
                for (JsonElement element : list) {
                    if (element instanceof JsonObject property) {
                        String name = property.getAsJsonPrimitive("name").getAsString();
                        String value = property.getAsJsonPrimitive("value").getAsString();
                        read.put(name, property.has("signature")
                                ? new Property(name, value, property.getAsJsonPrimitive("signature").getAsString())
                                : new Property(name, value));
                    }
                }
            } else if (json != null && !json.isJsonNull()) {
                throw new JsonParseException("A property map is a list or an object: " + json);
            }
            return new PropertyMap(read);
        }

        @Override
        public JsonElement serialize(PropertyMap map, Type type, JsonSerializationContext context) {
            JsonArray list = new JsonArray();
            for (Property property : map.values()) {
                JsonObject object = new JsonObject();
                object.addProperty("name", property.name());
                object.addProperty("value", property.value());
                if (property.hasSignature()) {
                    object.addProperty("signature", property.signature());
                }
                list.add(object);
            }
            return list;
        }
    }
}
