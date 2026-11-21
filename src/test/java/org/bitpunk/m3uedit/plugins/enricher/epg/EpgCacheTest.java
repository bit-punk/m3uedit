package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EpgCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void testTheUsageOfTheDownloadedSources() throws Exception {
        Files.write(tempDir.resolve("a.xml.gz"), new byte[3 * 1024 * 1024]);
        Files.write(tempDir.resolve("a.properties"), new byte[512 * 1024]);

        EpgCache.Usage usage = EpgCache.usage(tempDir);

        assertThat(usage.files()).isEqualTo(2);
        assertThat(usage.describe()).isEqualTo("3.5 MB in 2 files");
        assertThat(EpgCache.usage(tempDir.resolve("missing")).describe()).isEqualTo("empty");
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
        assertThat(EpgCache.formatSize(100)).isEqualTo("100 bytes");
        assertThat(EpgCache.formatSize(1536)).isEqualTo("1.5 KB");
        assertThat(EpgCache.formatSize(5L * 1024 * 1024 * 1024)).isEqualTo("5.0 GB");
    }
}
