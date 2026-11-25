package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.api.CollectedData;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EpgCacheTest {
    private static final PluginContext CONTEXT = new DefaultPluginContext(EpgEnricher.class);

    @TempDir
    Path tempDir;

    @Test
    void testTheUsageOfTheDownloadedSources() throws Exception {
        Files.write(tempDir.resolve("a.xml.gz"), new byte[3 * 1024 * 1024]);
        Files.write(tempDir.resolve("a.properties"), new byte[512 * 1024]);

        EpgCache.Usage usage = EpgCache.usage(tempDir);

        assertThat(usage.files()).isEqualTo(2);
        assertThat(usage.describe(CONTEXT)).isEqualTo("3.5 MB in 2 files");
        assertThat(EpgCache.usage(tempDir.resolve("missing")).describe(CONTEXT)).isEqualTo("empty");
    }

    @Test
    void testClearingDeletesTheSourcesButKeepsTheDirectory() throws Exception {
        Files.writeString(tempDir.resolve("a.xml.gz"), "guide");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub").resolve("b.xml"), "guide");

        EpgCache.clear(tempDir);

        assertThat(tempDir).isEmptyDirectory();
        EpgCache.clear(tempDir.resolve("missing"));
    }

    @Test
    void testSizes() {
        assertThat(EpgCache.formatSize(CONTEXT, 100)).isEqualTo("100 bytes");
        assertThat(EpgCache.formatSize(CONTEXT, 1536)).isEqualTo("1.5 KB");
        assertThat(EpgCache.formatSize(CONTEXT, 5L * 1024 * 1024 * 1024)).isEqualTo("5.0 GB");
    }

    @Test
    void testTheDownloadedSourcesAreOfferedAsCollectedData() throws Exception {
        CollectedData data = EpgCache.collectedData(CONTEXT);
        Path directory = EpgCache.directory(CONTEXT);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("guide.xml"), "guide");

        assertThat(data.getName()).isEqualTo("Downloaded EPG sources");
        assertThat(data.describe()).isEqualTo("5 bytes in 1 file");
        assertThat(data.getHint()).isNotBlank();
        assertThat(data.isEmpty()).isFalse();

        data.clear();

        assertThat(data.isEmpty()).isTrue();
        assertThat(directory).isEmptyDirectory();
    }
}
