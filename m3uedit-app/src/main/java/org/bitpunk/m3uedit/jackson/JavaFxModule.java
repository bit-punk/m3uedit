package org.bitpunk.m3uedit.jackson;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Persists JavaFX properties by their value only, e.g. {@code enabled: true}.
 * <p>
 * Serializing the property object itself would include its listeners and bindings (e.g. of the UI), which are
 * neither meaningful in a project-file nor serializable.
 */
public class JavaFxModule extends SimpleModule {

    public JavaFxModule() {
        super(JavaFxModule.class.getSimpleName());
        addSerializer(BooleanProperty.class, new BooleanPropertySerializer());
        addDeserializer(BooleanProperty.class, new BooleanPropertyDeserializer());
    }

    static class BooleanPropertySerializer extends StdScalarSerializer<BooleanProperty> {
        BooleanPropertySerializer() {
            super(BooleanProperty.class);
        }

        @Override
        public void serialize(BooleanProperty value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeBoolean(value.get());
        }
    }

    static class BooleanPropertyDeserializer extends StdScalarDeserializer<BooleanProperty> {
        BooleanPropertyDeserializer() {
            super(BooleanProperty.class);
        }

        @Override
        public BooleanProperty deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            return new SimpleBooleanProperty(_parseBooleanPrimitive(parser, context));
        }

        @Override
        public Object getNullValue(DeserializationContext context) {
            return new SimpleBooleanProperty();
        }
    }
}
