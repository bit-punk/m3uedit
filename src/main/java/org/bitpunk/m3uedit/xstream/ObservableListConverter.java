package org.bitpunk.m3uedit.xstream;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.mapper.Mapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ObservableListConverter extends CollectionConverter implements Converter {

    public ObservableListConverter(Mapper mapper) {
        super(mapper);
    }

    @Override
    public boolean canConvert(Class type) {
        return ObservableList.class.isAssignableFrom(type);
    }

    @Override
    protected Object createCollection(Class type) {
        return FXCollections.observableArrayList();
    }
}