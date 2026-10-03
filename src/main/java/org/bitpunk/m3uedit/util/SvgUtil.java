package org.bitpunk.m3uedit.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

public final class SvgUtil {

    private SvgUtil() {}

    public static String loadSvgPath(String resourcePath) {
        try (InputStream inputStream = SvgUtil.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document document = factory.newDocumentBuilder().parse(inputStream);
            NodeList pathNodes = document.getElementsByTagName("path");
            if (pathNodes.getLength() > 0) {
                Node dAttribute = pathNodes.item(0).getAttributes().getNamedItem("d");
                if (dAttribute != null) {
                    return dAttribute.getNodeValue();
                }
            }
            throw new IllegalStateException("No path element with 'd' attribute found in SVG: " + resourcePath);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SVG resource: " + resourcePath, e);
        }
    }
}
