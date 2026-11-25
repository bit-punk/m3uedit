package org.bitpunk.m3uedit.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvgUtilTest {

    @Test
    void testLoadSvgPathSuccess() {
        String pencilPath = SvgUtil.loadSvgPath("/icons/pencil.svg");
        assertThat(pencilPath).isNotEmpty().startsWith("M3 ");

        String newProjectPath = SvgUtil.loadSvgPath("/icons/new_project.svg");
        assertThat(newProjectPath).isNotEmpty().startsWith("M14 2H6");

        String openProjectPath = SvgUtil.loadSvgPath("/icons/open_project.svg");
        assertThat(openProjectPath).isNotEmpty().startsWith("M20 6h-8");

        String aboutPath = SvgUtil.loadSvgPath("/icons/about.svg");
        assertThat(aboutPath).isNotEmpty().startsWith("M12 2C6.48");

        String playPath = SvgUtil.loadSvgPath("/icons/play.svg");
        assertThat(playPath).isNotEmpty().startsWith("M8 5v14");
    }

    @Test
    void testLoadSvgPathResourceNotFoundThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> SvgUtil.loadSvgPath("/icons/non_existent.svg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource not found");
    }

    @Test
    void testLoadSvgPathInvalidXmlThrowsRuntimeException() {
        assertThatThrownBy(() -> SvgUtil.loadSvgPath("/fxml/css/scene.css"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load SVG resource");
    }
}
