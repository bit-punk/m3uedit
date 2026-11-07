package org.bitpunk.m3uedit.xstream;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Persists a {@link BooleanProperty} by its value only, e.g. {@code <enabled>true</enabled>}.
 * <p>
 * Serializing the property object itself would include its listeners and bindings (e.g. of the UI), which are
 * neither meaningful in a project-file nor serializable (bidirectional bindings use {@link java.lang.ref.WeakReference}s).
 * <p>
 * Project-files written by older versions, which contain the serialized property object
 * ({@code <enabled><value>true</value>...</enabled>}), can still be read.
 */
public class BooleanPropertyConverter implements Converter {
    private static final String LEGACY_VALUE_NODE = "value";

    @Override
    public boolean canConvert(Class type) {
        return type != null && BooleanProperty.class.isAssignableFrom(type);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        writer.setValue(Boolean.toString(((BooleanProperty) source).get()));
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        if (!reader.hasMoreChildren()) {
            return new SimpleBooleanProperty(Boolean.parseBoolean(reader.getValue().strip()));
        }
        // legacy format: the serialized SimpleBooleanProperty object
        boolean value = false;
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if (LEGACY_VALUE_NODE.equals(reader.getNodeName())) {
                value = Boolean.parseBoolean(reader.getValue().strip());
            }
            reader.moveUp();
        }
        return new SimpleBooleanProperty(value);
    }
}
