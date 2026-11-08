package org.bitpunk.m3uedit.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.sdchannel.SdChannelFilter;
import org.bitpunk.m3uedit.plugins.filter.uniquename.UniqueNameFilter;
import org.bitpunk.m3uedit.plugins.filter.uniqueuri.UniqueUriFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataModelFactoryTest {

    @Test
    void missingPluginsAreAppendedAndTheOrderIsKept() {
        UniqueUriFilter uri = new UniqueUriFilter();
        UniqueNameFilter name = new UniqueNameFilter();
        ObservableList<Filter> filters = FXCollections.observableArrayList(uri, name);

        DataModelFactory.completePlugins(filters, List.of(new SdChannelFilter(), new UniqueNameFilter(), new UniqueUriFilter()));

        assertThat(filters).hasSize(3);
        assertThat(filters.get(0)).isSameAs(uri);
        assertThat(filters.get(1)).isSameAs(name);
        assertThat(filters.get(2)).isInstanceOf(SdChannelFilter.class);
    }

    @Test
    void duplicatePluginTypesAreDropped() {
        UniqueNameFilter first = new UniqueNameFilter();
        first.getEnabled().set(true);
        ObservableList<Filter> filters = FXCollections.observableArrayList(first, new UniqueNameFilter());

        DataModelFactory.completePlugins(filters, List.of(new UniqueNameFilter()));

        assertThat(filters).containsExactly(first);
    }

    @Test
    void completeProjectIsNotChanged() {
        DataModel dataModel = DataModelFactory.createDataModel("test");
        dataModel.setChanged(false);

        DataModelFactory.completePlugins(dataModel);

        assertThat(dataModel.isChanged()).isFalse();
    }
}
