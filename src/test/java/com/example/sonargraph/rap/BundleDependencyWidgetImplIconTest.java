package com.example.sonargraph.rap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleDependencyWidgetImplIconTest {

    @Test
    void injectsSvgSymbolViaPlaceholder() {
        String html = "<html><head>__IMPL_ICON_SYMBOL__</head><body></body></html>";
        String svg = "<svg viewBox=\"-5 -10 110 135\"><path d=\"M0 0\"/></svg>";
        String result = BundleDependencyWidget.injectImplIcon(html, svg);
        assertFalse(result.contains("__IMPL_ICON_SYMBOL__"), "placeholder must be replaced");
        // Das <svg>-Tag wird zu <symbol id="locked-icon"> umgewickelt
        assertTrue(result.contains("<symbol id=\"locked-icon\""),
                "must contain symbol with id locked-icon: " + result);
        assertTrue(result.contains("<defs>"), "must contain <defs> wrapper");
        assertTrue(result.contains("</symbol>"), "must close symbol");
    }

    @Test
    void emptySvgYieldsEmptyBlock() {
        String html = "<html><head>__IMPL_ICON_SYMBOL__</head></html>";
        String result = BundleDependencyWidget.injectImplIcon(html, "");
        assertFalse(result.contains("__IMPL_ICON_SYMBOL__"));
        assertFalse(result.contains("<symbol"), "no symbol when SVG is empty");
    }

    @Test
    void nullSvgYieldsEmptyBlock() {
        String html = "<html><head>__IMPL_ICON_SYMBOL__</head></html>";
        String result = BundleDependencyWidget.injectImplIcon(html, null);
        assertFalse(result.contains("__IMPL_ICON_SYMBOL__"));
        assertFalse(result.contains("<symbol"));
    }

    @Test
    void fallbackInjectsAfterBodyTagWhenPlaceholderMissing() {
        String html = "<html><head><title>x</title></head><body><p>hi</p></body></html>";
        String svg = "<svg><path d=\"M0 0\"/></svg>";
        String result = BundleDependencyWidget.injectImplIcon(html, svg);
        // Sollte direkt nach <body> eingefügt sein
        int bodyEnd = result.indexOf("<body>") + "<body>".length();
        assertTrue(result.substring(bodyEnd).startsWith("<svg"),
                "must be injected right after <body>: " + result);
    }

    @Test
    void locksIconMarkupContainsViewBox() {
        // Das echte locked.svg hat ein viewBox-Attribut, das durchgereicht wird
        String html = "<html><head>__IMPL_ICON_SYMBOL__</head></html>";
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"-5.0 -10.0 110.0 135.0\">"
                + "<path d=\"M0 0\"/>"
                + "<text>credits</text>"
                + "</svg>";
        String result = BundleDependencyWidget.injectImplIcon(html, svg);
        assertTrue(result.contains("viewBox=\"-5.0 -10.0 110.0 135.0\""));
        // Credits-Text sollte nicht im sichtbaren Icon landen (aber im <symbol> schon, da Teil des SVG)
        // Da das <symbol> im hidden <svg> liegt, ist das OK
    }
}
