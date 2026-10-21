package com.komainu.moxia.image;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Límites de seguridad de las imágenes por URL: política de URL y descargas contra un servidor local. */
class ImageDownloadTest {
    private static final ImageSettings ENABLED = new ImageSettings(true, false,
            List.of("imagenes.ejemplo.com", "*.cdn.ejemplo.org"), 1024 * 1024, 2048L * 2048, 5, 256);
    private static final byte[] PNG = TestImages.encode(TestImages.icon(9), "png");

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private HttpServer server;
    private String base;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok.png", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, PNG.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(PNG);
            }
        });
        server.createContext("/big-declared", exchange -> {
            exchange.sendResponseHeaders(200, 5_000_000);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(new byte[64 * 1024]);
            } catch (IOException ignored) {
                // El cliente corta.
            }
        });
        server.createContext("/big-chunked", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                byte[] chunk = new byte[16 * 1024];
                for (int written = 0; written < 4_000_000; written += chunk.length) {
                    body.write(chunk);
                }
            } catch (IOException ignored) {
                // El cliente corta.
            }
        });
        server.createContext("/same-host", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/other-host", exchange -> {
            exchange.getResponseHeaders().add("Location", "https://evil.example.net/x.png");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().add("Location", "/loop");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            // Mucho más que el plazo del cliente (1 s), para que el margen no dependa de la máquina. Al acabar la
            // prueba, shutdownNow lo interrumpe.
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, PNG.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(PNG);
            } catch (IOException ignored) {
                // El cliente ya se fue.
            }
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.setExecutor(executor);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    private static ImageDownloader local(long maxBytes, int seconds) {
        return ImageDownloader.forTests(List.of("127.0.0.1"), maxBytes, Duration.ofSeconds(seconds));
    }

    @Test
    void downloadsAnAllowedImage() throws InterruptedException {
        assertArrayEquals(PNG, local(1024 * 1024, 5).download(base + "/ok.png"));
    }

    @Test
    void cutsTheBodyAtTheSizeLimit() {
        IllegalArgumentException declared = assertThrows(IllegalArgumentException.class,
                () -> local(256 * 1024, 5).download(base + "/big-declared"));
        assertTrue(declared.getMessage().contains("pesa más de 256 KiB"), declared.getMessage());
        IllegalArgumentException chunked = assertThrows(IllegalArgumentException.class,
                () -> local(256 * 1024, 5).download(base + "/big-chunked"));
        assertTrue(chunked.getMessage().contains("pesa más de 256 KiB"), chunked.getMessage());
    }

    @Test
    void followsRedirectsOnlyWithinTheSameHost() throws InterruptedException {
        assertArrayEquals(PNG, local(1024 * 1024, 5).download(base + "/same-host"));
        IllegalArgumentException other = assertThrows(IllegalArgumentException.class,
                () -> local(1024 * 1024, 5).download(base + "/other-host"));
        assertTrue(other.getMessage().contains("otro sitio"), other.getMessage());
        IllegalArgumentException loop = assertThrows(IllegalArgumentException.class,
                () -> local(1024 * 1024, 5).download(base + "/loop"));
        assertTrue(loop.getMessage().contains("demasiadas veces"), loop.getMessage());
        // localhost no es 127.0.0.1: aunque sea la misma máquina, es otro host.
        String renamed = base.replace("127.0.0.1", "localhost");
        assertThrows(IllegalArgumentException.class, () -> local(1024 * 1024, 5).download(renamed + "/ok.png"));
    }

    @Test
    void givesUpAfterTheTimeout() {
        // Lo corta el plazo (lo dice el mensaje), no la respuesta del servidor, que tarda 30 s.
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> local(1024 * 1024, 1).download(base + "/slow"));
        assertTrue(error.getMessage().contains("tardó más de 1 s"), error.getMessage());
    }

    @Test
    void reportsHttpErrors() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> local(1024 * 1024, 5).download(base + "/missing"));
        assertTrue(error.getMessage().contains("404"), error.getMessage());
    }

    @Test
    void hostsOutsideTheListAreNeverContacted() {
        ImageDownloader other = ImageDownloader.forTests(List.of("imagenes.ejemplo.com"), 1024, Duration.ofSeconds(5));
        assertThrows(IllegalArgumentException.class, () -> other.download(base + "/ok.png"));
        assertEquals(0, requests.get());
    }

    @Test
    void onlyHttpsToAllowedHosts() {
        assertEquals("imagenes.ejemplo.com",
                ImageUrlPolicy.check("https://imagenes.ejemplo.com/a.png", ENABLED).getHost());
        ImageUrlPolicy.check("https://img.cdn.ejemplo.org/a.png", ENABLED);
        ImageUrlPolicy.check("https://IMAGENES.ejemplo.com./a.png", ENABLED);
        ImageUrlPolicy.check("https://imagenes.ejemplo.com:443/a.png", ENABLED);
        String[] refused = {
                "http://imagenes.ejemplo.com/a.png",
                "ftp://imagenes.ejemplo.com/a.png",
                "file:///etc/passwd",
                "https://usuario:clave@imagenes.ejemplo.com/a.png",
                "https://imagenes.ejemplo.com:8443/a.png",
                "https://otro.ejemplo.com/a.png",
                "https://imagenes.ejemplo.com.atacante.net/a.png",
                "https://cdn.ejemplo.org/a.png",
                "https://127.0.0.1/a.png",
                "https:///sin-host.png",
                "https://imagenes.ejemplo.com/" + "a".repeat(ImageUrlPolicy.MAX_LENGTH),
                "no es una url"
        };
        for (String url : refused) {
            assertThrows(IllegalArgumentException.class, () -> ImageUrlPolicy.check(url, ENABLED), url);
        }
    }

    @Test
    void urlsAreOffUnlessTheAdminTurnsThemOn() {
        IllegalArgumentException off = assertThrows(IllegalArgumentException.class,
                () -> ImageUrlPolicy.checkAccess(ImageSettings.DEFAULT, true, "https://imagenes.ejemplo.com/a.png"));
        assertTrue(off.getMessage().contains("desactivadas"), off.getMessage());
        ImageSettings adminOnly = new ImageSettings(true, true, List.of("imagenes.ejemplo.com"), 4096, 4096, 5, 64);
        IllegalArgumentException player = assertThrows(IllegalArgumentException.class,
                () -> ImageUrlPolicy.checkAccess(adminOnly, false, "https://imagenes.ejemplo.com/a.png"));
        assertTrue(player.getMessage().contains(ImageUrlPolicy.ADMIN_NODE), player.getMessage());
        ImageUrlPolicy.checkAccess(adminOnly, true, "https://imagenes.ejemplo.com/a.png");
        ImageUrlPolicy.checkAccess(ENABLED, false, "https://imagenes.ejemplo.com/a.png");
    }

    @Test
    void redirectsMustStayOnTheSameHostSchemeAndPort() {
        URI from = URI.create("https://imagenes.ejemplo.com/a.png");
        List<String> hosts = List.of("imagenes.ejemplo.com", "otro.ejemplo.com");
        assertEquals("/b.png", ImageUrlPolicy.redirect(from, "/b.png", hosts, false).getPath());
        assertEquals("/c/d.png", ImageUrlPolicy.redirect(from, "https://imagenes.ejemplo.com/c/d.png", hosts, false)
                .getPath());
        for (String location : new String[]{"https://otro.ejemplo.com/a.png", "http://imagenes.ejemplo.com/a.png",
                "https://imagenes.ejemplo.com:8443/a.png", "//otro.ejemplo.com/a.png", "", "https://user@imagenes"
                + ".ejemplo.com/a.png"}) {
            assertThrows(IllegalArgumentException.class, () -> ImageUrlPolicy.redirect(from, location, hosts, false),
                    location);
        }
    }

    @Test
    void internalAddressesAreNotPublic() throws IOException {
        for (String address : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.9", "192.168.1.20", "169.254.169.254",
                "0.0.0.0", "100.64.0.1", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd12:3456::1"}) {
            assertFalse(ImageUrlPolicy.isPublic(InetAddress.getByName(address)), address);
        }
        for (String address : new String[]{"8.8.8.8", "93.184.216.34", "2606:4700::1111"}) {
            assertTrue(ImageUrlPolicy.isPublic(InetAddress.getByName(address)), address);
        }
        assertThrows(IllegalArgumentException.class, () -> ImageUrlPolicy.checkResolves("localhost"));
    }

    @Test
    void hostPatterns() {
        List<String> patterns = List.of("ejemplo.com", "*.cdn.org");
        assertTrue(ImageUrlPolicy.allowed("ejemplo.com", patterns));
        assertFalse(ImageUrlPolicy.allowed("www.ejemplo.com", patterns));
        assertTrue(ImageUrlPolicy.allowed("a.b.cdn.org", patterns));
        assertFalse(ImageUrlPolicy.allowed("cdn.org", patterns));
        assertFalse(ImageUrlPolicy.allowed("malcdn.org", patterns));
    }
}
