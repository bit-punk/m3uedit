package org.bitpunk.m3uedit.jackson;

import org.bitpunk.m3uedit.api.Enricher;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.api.Sorter;
import org.bitpunk.m3uedit.plugins.EnricherPluginService;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.PluginDescriptor;
import org.bitpunk.m3uedit.plugins.PluginService;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.plugins.SorterPluginService;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Stores the plugins of a project: each as its type id, whether it is enabled and its settings, e.g.
 * <pre>
 * - type: FileInput
 *   enabled: true
 *   source: /home/me/tv.m3u
 * </pre>
 * Only the plugins of the plugin services of the expected kind are accepted (an output can not be loaded as input),
 * and the settings are read as the settings record of the plugin, so a project-file cannot create other objects.
 */
public class PluginModule extends SimpleModule {
    public static final String TYPE_PROPERTY = "type";
    public static final String ENABLED_PROPERTY = "enabled";

    /**
     * @param settingsMapper maps the settings of the plugins (without this module)
     */
    public PluginModule(Supplier<ObjectMapper> settingsMapper) {
        super(PluginModule.class.getSimpleName());
        addSerializer(Plugin.class, new PluginSerializer(settingsMapper));
        addDeserializer(Input.class, new PluginDeserializer<>(Input.class, InputPluginService::getInstance, settingsMapper));
        addDeserializer(Filter.class, new PluginDeserializer<>(Filter.class, FilterPluginService::getInstance, settingsMapper));
        addDeserializer(Sorter.class, new PluginDeserializer<>(Sorter.class, SorterPluginService::getInstance, settingsMapper));
        addDeserializer(Enricher.class, new PluginDeserializer<>(Enricher.class, EnricherPluginService::getInstance,
                settingsMapper));
        addDeserializer(Output.class, new PluginDeserializer<>(Output.class, OutputPluginService::getInstance,
                settingsMapper));
    }

    /**
     * @return the plugin services, by the kind of plugin they provide
     */
    private static List<PluginService<?>> services() {
        return List.of(InputPluginService.getInstance(), FilterPluginService.getInstance(),
                SorterPluginService.getInstance(), EnricherPluginService.getInstance(),
                OutputPluginService.getInstance());
    }

    static class PluginSerializer extends StdSerializer<Plugin> {
        private final transient Supplier<ObjectMapper> settingsMapper;

        PluginSerializer(Supplier<ObjectMapper> settingsMapper) {
            super(Plugin.class);
            this.settingsMapper = settingsMapper;
        }

        @Override
        public void serialize(Plugin plugin, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            String id = services().stream()
                    .flatMap(service -> service.find(plugin.getClass()).stream())
                    .map(PluginDescriptor::id).findFirst()
                    .orElseThrow(() -> new IllegalStateException("unknown plugin " + plugin.getClass().getName()));
            JsonNode settingsNode = settingsMapper.get().valueToTree(plugin.getSettings());
            generator.writeStartObject(plugin);
            generator.writeStringProperty(TYPE_PROPERTY, id);
            generator.writeBooleanProperty(ENABLED_PROPERTY, PluginStates.enabled(plugin).get());
            if (settingsNode != null && settingsNode.isObject()) {
                for (Map.Entry<String, JsonNode> property : settingsNode.properties()) {
                    if (!TYPE_PROPERTY.equals(property.getKey()) && !ENABLED_PROPERTY.equals(property.getKey())) {
                        generator.writeName(property.getKey());
                        generator.writeTree(property.getValue());
                    }
                }
            }
            generator.writeEndObject();
        }
    }

    static class PluginDeserializer<T extends Plugin> extends StdDeserializer<T> {
        private final transient Supplier<? extends PluginService<T>> service;
        private final transient Supplier<ObjectMapper> settingsMapper;

        PluginDeserializer(Class<T> kind, Supplier<? extends PluginService<T>> service,
                           Supplier<ObjectMapper> settingsMapper) {
            super(kind);
            this.service = service;
            this.settingsMapper = settingsMapper;
        }

        @Override
        public T deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            JsonNode node = context.readTree(parser);
            if (node == null || !node.isObject()) {
                return context.reportInputMismatch(this, "a plugin must be a map with its type");
            }
            JsonNode type = node.get(TYPE_PROPERTY);
            if (type == null || !type.isString()) {
                return context.reportInputMismatch(this, "plugin without %s", TYPE_PROPERTY);
            }
            PluginDescriptor<T> descriptor = service.get().find(type.asString()).orElse(null);
            if (descriptor == null) {
                return context.reportInputMismatch(this, "unknown %s: %s", handledType().getSimpleName(),
                        type.asString());
            }
            JsonNode enabled = node.get(ENABLED_PROPERTY);
            if (enabled != null && !enabled.isBoolean()) {
                return context.reportInputMismatch(this, "%s of %s is not a boolean", ENABLED_PROPERTY,
                        type.asString());
            }
            ObjectNode settings = ((ObjectNode) node).deepCopy();
            settings.remove(TYPE_PROPERTY);
            settings.remove(ENABLED_PROPERTY);

            T plugin = descriptor.create();
            plugin.setSettings(settingsMapper.get().treeToValue(settings, plugin.getSettingsType()));
            PluginStates.enabled(plugin).set(enabled != null && enabled.booleanValue());
            return plugin;
        }
    }
}
