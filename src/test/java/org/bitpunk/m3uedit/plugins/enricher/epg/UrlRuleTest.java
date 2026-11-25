package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UrlRuleTest {

    @Test
    void testRuleAssignsItsIdToMatchingUrls() {
        UrlRule rule = new UrlRule("service=514\\b", "ZDF.de");

        assertThat(rule.channelIdFor("rtsp://fritz.box:554/?avm=1&freq=450&service=514&pids=0,100")).isEqualTo("ZDF.de");
        assertThat(rule.channelIdFor("rtsp://fritz.box:554/?avm=1&freq=450&service=5140")).isNull();
        assertThat(rule.channelIdFor(null)).isNull();
    }

    @Test
    void testIdMayReferenceGroups() {
        UrlRule rule = new UrlRule("/live/([a-z]+)\\.m3u8", "$1.de");

        assertThat(rule.channelIdFor("https://cdn.example.com/live/zdf.m3u8?token=1")).isEqualTo("zdf.de");
    }

    @Test
    void testInvalidRulesMatchNothing() {
        assertThat(new UrlRule("(unclosed", "X").channelIdFor("(unclosed")).isNull();
        assertThat(new UrlRule("a", "$2").channelIdFor("a")).isNull();
    }

    @Test
    void testRulesAreParsedAndFormatted() {
        UrlRule rule = UrlRule.parse("  service=514  =>  ZDF.de ", XmltvTestFiles.CONTEXT);

        assertThat(rule).isEqualTo(new UrlRule("service=514", "ZDF.de"));
        assertThat(UrlRule.parse(rule.format(), XmltvTestFiles.CONTEXT)).isEqualTo(rule);
        // the last separator splits, so an expression may contain one
        assertThat(UrlRule.parse("a=>b => C.de", XmltvTestFiles.CONTEXT)).isEqualTo(new UrlRule("a=>b", "C.de"));
    }

    @Test
    void testInvalidLinesAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> UrlRule.parse("service=514 ZDF.de", XmltvTestFiles.CONTEXT))
                .withMessageContaining("=>");
        assertThatIllegalArgumentException().isThrownBy(() -> UrlRule.parse(" => ZDF.de", XmltvTestFiles.CONTEXT))
                .withMessageContaining("required");
        assertThatIllegalArgumentException().isThrownBy(() -> UrlRule.parse("(unclosed => ZDF.de", XmltvTestFiles.CONTEXT))
                .withMessageContaining("Invalid expression");
    }
}
