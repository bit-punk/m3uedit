package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuideCompilerTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    @TempDir
    Path tempDir;

    private static String programme(String channel, String start, String stop, String title) {
        return "<programme start=\"%s\"%s channel=\"%s\"><title>%s</title></programme>".formatted(start,
                stop == null ? "" : " stop=\"" + stop + "\"", channel, title);
    }

    private String write(GuideCompiler compiler, List<GuideCompiler.SourceSelection> selections) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GuideCompiler.Statistics statistics = compiler.write(selections, outputStream);
        String guide = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(statistics.channelCount()).isEqualTo(count(guide, "<channel "));
        assertThat(statistics.programmeCount()).isEqualTo(count(guide, "<programme "));
        return guide;
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    void testChannelsAreWrittenBeforeTheProgrammesOfAllSources() throws IOException {
        Path first = XmltvTestFiles.write(tempDir.resolve("first.xml"), "<tv>"
                + "<channel id=\"A\"><display-name>A</display-name></channel>"
                + programme("A", "20261113120000 +0000", "20261113130000 +0000", "A1")
                + "<channel id=\"B\"><display-name>B</display-name></channel>"
                + programme("B", "20261113120000 +0000", "20261113130000 +0000", "B1")
                + "<channel id=\"A\"><display-name>A again</display-name></channel>"
                + "</tv>");
        Path second = XmltvTestFiles.writeGzip(tempDir.resolve("second.xml.gz"), "<tv>"
                + programme("c.at", "20261113120000 +0000", "20261113130000 +0000", "C1 &amp; more")
                + "<channel id=\"c.at\"><display-name>C</display-name><icon src=\"http://x/c.png\"/></channel>"
                + "</tv>");
        GuideCompiler compiler = new GuideCompiler(XmltvTestFiles.CONTEXT, NOW, null, null, ZoneOffset.UTC);

        String guide = write(compiler, List.of(new GuideCompiler.SourceSelection(first, Map.of("A", "A")),
                new GuideCompiler.SourceSelection(second, Map.of("c.at", "C.at"))));

        assertThat(guide).contains("<!DOCTYPE tv SYSTEM \"xmltv.dtd\">", "<tv generator-info-name=\"m3uedit\">");
        assertThat(guide.lastIndexOf("<channel ")).isLessThan(guide.indexOf("<programme "));
        assertThat(guide).contains("<channel id=\"A\"><display-name>A</display-name></channel>",
                        "<channel id=\"C.at\"><display-name>C</display-name><icon src=\"http://x/c.png\"></icon></channel>",
                        "channel=\"C.at\"><title>C1 &amp; more</title>", "<title>A1</title>")
                .doesNotContain("B1", "A again", "\"B\"");
        Path written = XmltvTestFiles.write(tempDir.resolve("written.xml"), guide);
        assertThat(XmltvFiles.readChannels(XmltvTestFiles.CONTEXT, written)).extracting(XmltvChannel::id).containsExactly("A", "C.at");
    }

    @Test
    void testProgrammesOutsideTheTimeWindowAreLeftOut() throws IOException {
        Path source = XmltvTestFiles.write(tempDir.resolve("source.xml"), "<tv>"
                + "<channel id=\"A\"><display-name>A</display-name></channel>"
                + programme("A", "20261113030000 +0000", "20261113040000 +0000", "ended 8 hours ago")
                + programme("A", "20261113090000 +0000", "20261113100000 +0000", "ended 2 hours ago")
                + programme("A", "20261113110000 +0000", null, "without stop")
                + programme("A", "20261114110000 +0000", "20261114120000 +0000", "tomorrow")
                + programme("A", "20261120110000 +0000", "20261120120000 +0000", "in a week")
                + programme("A", "unknown", "unknown", "unknown times")
                + "</tv>");
        GuideCompiler compiler = new GuideCompiler(XmltvTestFiles.CONTEXT, NOW, Duration.ofHours(6), Duration.ofDays(2), ZoneOffset.UTC);

        String guide = write(compiler, List.of(new GuideCompiler.SourceSelection(source, Map.of("A", "A"))));

        assertThat(guide).contains("ended 2 hours ago", "without stop", "tomorrow", "unknown times")
                .doesNotContain("ended 8 hours ago", "in a week");
    }

    @Test
    void testEmptySelectionGivesAnEmptyGuide() throws IOException {
        String guide = write(new GuideCompiler(XmltvTestFiles.CONTEXT, NOW, null, null, ZoneOffset.UTC), List.of());

        assertThat(guide).contains("<tv generator-info-name=\"m3uedit\">").contains("</tv>");
    }
}
