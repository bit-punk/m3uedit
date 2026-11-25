package org.bitpunk.m3uedit.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.plugins.filter.sdchannel.SdChannelFilter;
import org.bitpunk.m3uedit.plugins.filter.uniquename.UniqueNameFilter;
import org.bitpunk.m3uedit.plugins.filter.uniqueuri.UniqueUriFilter;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataModelFactoryTest {

    @Test
    void missingPluginsAreAppendedAndTheOrderIsKept() {
        UniqueUriFilter uri = TestPluginSupport.create(UniqueUriFilter::new);
        UniqueNameFilter name = TestPluginSupport.create(UniqueNameFilter::new);
        ObservableList<Filter> filters = FXCollections.observableArrayList(uri, name);

        DataModelFactory.completePlugins(filters, List.of(TestPluginSupport.create(SdChannelFilter::new), TestPluginSupport.create(UniqueNameFilter::new), TestPluginSupport.create(UniqueUriFilter::new)));

        assertThat(filters).hasSize(3);
        assertThat(filters.get(0)).isSameAs(uri);
        assertThat(filters.get(1)).isSameAs(name);
        assertThat(filters.get(2)).isInstanceOf(SdChannelFilter.class);
    }

    @Test
    void duplicatePluginTypesAreDropped() {
        UniqueNameFilter first = TestPluginSupport.create(UniqueNameFilter::new);
        PluginStates.enabled(first).set(true);
        ObservableList<Filter> filters = FXCollections.observableArrayList(first, TestPluginSupport.create(UniqueNameFilter::new));

        DataModelFactory.completePlugins(filters, List.of(TestPluginSupport.create(UniqueNameFilter::new)));

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
