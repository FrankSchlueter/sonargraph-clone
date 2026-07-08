package com.example.sonargraph.rap;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Servlet, das das persistierte Bundle-Modell-JSON unter
 * {@link BundleDependencyWidget#MODEL_URL} ausliefert.
 *
 * <p>Wird vom {@link LaunchBundleGraph} zusätzlich zu den RWT-Servlets
 * registriert, damit das HTML die großen Modelldaten (>3 MB) per
 * {@code fetch()} laden kann, statt sie per {@code browser.execute()}
 * in den Browser zu pushen.
 */
public class ModelJsonServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Path file = BundleModelFileStore.modelFile();
        if (!Files.exists(file)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Model not yet generated: " + file);
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json; charset=utf-8");
        resp.setContentLength(bytes.length);
        resp.setHeader("Cache-Control", "no-store");
        resp.getOutputStream().write(bytes);
        resp.getOutputStream().flush();
    }
}
