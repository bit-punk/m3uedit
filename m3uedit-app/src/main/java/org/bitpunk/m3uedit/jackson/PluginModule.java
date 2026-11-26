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
import org.bitpunk.m3uedit.plugins.missing.MissingEnricher;
import org.bitpunk.m3uedit.plugins.missing.MissingFilter;
import org.bitpunk.m3uedit.plugins.missing.MissingInput;
import org.bitpunk.m3uedit.plugins.missing.MissingOutput;
import org.bitpunk.m3uedit.plugins.missing.MissingPlugin;
import org.bitpunk.m3uedit.plugins.missing.MissingSorter;
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
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Stores the plugins of a project: each as its type id, whether it is enabled and its settings, e.g.
 * <pre>
 * - type: FileInput
 *   enabled: true
 *   source: /home/me/tv.m3u
 * </pre>
 * Only the plugins of the plugin services of the expected kind are accepted (an output can not be loaded as input),
 * and the settings are read as the settings record of the plugin, so a project-file cannot create other objects. A
 * plugin whose type id is not known (e.g. a plugin of another party that is not installed) is kept as
 * {@link MissingPlugin}, only as data, and written back unchanged.
 */
public class PluginModule extends SimpleModule {
    public static final String TYPE_PROPERTY = "type";
    public static final String ENABLED_PROPERTY = "enabled";

    /**
     * @param settingsMapper maps the settings of the plugins (without this module)
     */
    public PluginModule(Supplier<ObjectMapper> settingsMapper) {
        this(settingsMapper, new Services(InputPluginService::getInstance, FilterPluginService::getInstance,
                SorterPluginService::getInstance, EnricherPluginService::getInstance,
                OutputPluginService::getInstance));
    }

    /**
     * The plugin services of each kind, by default those of m3uedit.
     */
    public record Services(Supplier<? extends PluginService<Input>> inputs,
                           Supplier<? extends PluginService<Filter>> filters,
                           Supplier<? extends PluginService<Sorter>> sorters,
                           Supplier<? extends PluginService<Enricher>> enrichers,
                           Supplier<? extends PluginService<Output>> outputs) {

        List<PluginService<?>> all() {
            return List.of(inputs.get(), filters.get(), sorters.get(), enrichers.get(), outputs.get());
        }
    }

    /**
     * @param settingsMapper maps the settings of the plugins (without this module)
     * @param services       the plugins accepted, e.g. with plugins installed by the user in tests
     */
    public PluginModule(Supplier<ObjectMapper> settingsMapper, Services services) {
        super(PluginModule.class.getSimpleName());
        addSerializer(Plugin.class, new PluginSerializer(settingsMapper, services));
        addDeserializer(Input.class, new PluginDeserializer<>(Input.class, services.inputs(), MissingInput::new,
                services, settingsMapper));
        addDeserializer(Filter.class, new PluginDeserializer<>(Filter.class, services.filters(), MissingFilter::new,
                services, settingsMapper));
        addDeserializer(Sorter.class, new PluginDeserializer<>(Sorter.class, services.sorters(), MissingSorter::new,
                services, settingsMapper));
        addDeserializer(Enricher.class, new PluginDeserializer<>(Enricher.class, services.enrichers(),
                MissingEnricher::new, services, settingsMapper));
        addDeserializer(Output.class, new PluginDeserializer<>(Output.class, services.outputs(), MissingOutput::new,
                services, settingsMapper));
    }

    static class PluginSerializer extends StdSerializer<Plugin> {
        private final transient Supplier<ObjectMapper> settingsMapper;
        private final transient Services services;

        PluginSerializer(Supplier<ObjectMapper> settingsMapper, Services services) {
            super(Plugin.class);
            this.settingsMapper = settingsMapper;
            this.services = services;
        }

        @Override
        public void serialize(Plugin plugin, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            String id;
            JsonNode settingsNode;
            if (plugin instanceof MissingPlugin missing) {
                // written back as read, so nothing is lost
                id = missing.getTypeId();
                settingsNode = missing.getRawSettings();
            } else {
                id = services.all().stream()
                        .flatMap(service -> service.find(plugin.getClass()).stream())
                        .map(PluginDescriptor::id).findFirst()
                        .orElseThrow(() -> new IllegalStateException("unknown plugin " + plugin.getClass().getName()));
                settingsNode = settingsMapper.get().valueToTree(plugin.getSettings());
            }
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
        private final transient BiFunction<String, ObjectNode, ? extends T> missing;
        private final transient Services services;
        private final transient Supplier<ObjectMapper> settingsMapper;

        /**
         * @param missing creates the placeholder of a plugin that is not installed
         * @param services the plugins of all kinds, to tell a plugin that is not installed from one of another kind
         */
        PluginDeserializer(Class<T> kind, Supplier<? extends PluginService<T>> service,
                           BiFunction<String, ObjectNode, ? extends T> missing, Services services,
                           Supplier<ObjectMapper> settingsMapper) {
            super(kind);
            this.service = service;
            this.missing = missing;
            this.services = services;
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
            boolean otherKind = services.all().stream().anyMatch(other -> other.find(type.asString()).isPresent());
            if (descriptor == null && otherKind) {
                return context.reportInputMismatch(this, "%s is not a plugin of the kind %s", type.asString(),
                        handledType().getSimpleName());
            }
            JsonNode enabled = node.get(ENABLED_PROPERTY);
            if (enabled != null && !enabled.isBoolean()) {
                return context.reportInputMismatch(this, "%s of %s is not a boolean", ENABLED_PROPERTY,
                        type.asString());
            }
            ObjectNode settings = ((ObjectNode) node).deepCopy();
            settings.remove(TYPE_PROPERTY);
            settings.remove(ENABLED_PROPERTY);

            T plugin;
            if (descriptor == null) {
                // not installed: kept as data, no class is loaded for it
                plugin = missing.apply(type.asString(), settings);
            } else {
                plugin = descriptor.create();
                plugin.setSettings(settingsMapper.get().treeToValue(settings, plugin.getSettingsType()));
            }
            PluginStates.enabled(plugin).set(enabled != null && enabled.booleanValue());
            return plugin;
        }
    }
}
