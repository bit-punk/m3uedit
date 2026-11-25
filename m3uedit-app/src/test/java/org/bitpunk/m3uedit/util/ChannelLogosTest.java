package org.bitpunk.m3uedit.util;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelLogosTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @AfterEach
    void clearCache() {
        ChannelLogos.clear();
    }

    static String logoUrl(Path dir, String name, int width, int height) throws Exception {
        Path file = dir.resolve(name + ".png");
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        return file.toUri().toString();
    }

    @Test
    void testALogoIsLoadedOnceScaledToFit() throws Exception {
        String url = logoUrl(tempDir, "logo", 200, 100);

        Image logo = ChannelLogos.get(url, 32, 20);

        assertThat(ChannelLogos.get(" " + url + " ", 32, 20)).isSameAs(logo);
        assertThat(ChannelLogos.get(url, 40, 24)).isNotSameAs(logo);
        long deadline = System.currentTimeMillis() + 5000;
        while (logo.getProgress() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(logo.isError()).isFalse();
        // the ratio is kept: 200x100 fits into 32x20 as 32x16
        assertThat(logo.getWidth()).isEqualTo(32);
        assertThat(logo.getHeight()).isEqualTo(16);
    }

    @Test
    void testWithoutAValidUrlThereIsNoLogo() {
        assertThat(ChannelLogos.get(null, 32, 20)).isNull();
        assertThat(ChannelLogos.get("  ", 32, 20)).isNull();
        assertThat(ChannelLogos.get("no url", 32, 20)).isNull();
    }

    @Test
    void testTheLeastRecentlyUsedLogosAreDropped() throws Exception {
        String first = logoUrl(tempDir, "first", 10, 10);
        Image logo = ChannelLogos.get(first, 32, 20);
        for (int i = 0; i < ChannelLogos.MAX_CACHED; i++) {
            ChannelLogos.get("file:/missing/" + i + ".png", 32, 20);
        }

        assertThat(ChannelLogos.get(first, 32, 20)).isNotSameAs(logo);
    }
}
