package com.komainu.moxia.image;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Qué URL de imagen se pueden descargar: solo {@code https}, sin usuario ni contraseña, en el puerto 443, a un
 * dominio de la lista del admin y que no resuelva a una dirección interna (bucle local, red privada, enlace local…).
 * Las redirecciones solo se siguen dentro del mismo host.
 */
public final class ImageUrlPolicy {
    public static final int MAX_LENGTH = 2048;
    /** Permiso de las URL cuando {@code urlsRequireAdmin} está activo. */
    public static final String ADMIN_NODE = "komaedit.admin.image.url";

    private ImageUrlPolicy() {
    }

    /**
     * Las URL tienen que estar activadas; si el admin lo pide, solo para quien tenga
     * {@link #ADMIN_NODE}; y la URL tiene que pasar las reglas de {@link #check(String, ImageSettings)}.
     */
    public static URI checkAccess(ImageSettings settings, boolean admin, String url) {
        if (settings.allowUrls() && settings.urlsRequireAdmin() && !admin) {
            throw new IllegalArgumentException("En este servidor las imágenes por URL son solo para admins ("
                    + ADMIN_NODE + "); usa un archivo de config/komaedit/images/");
        }
        return check(url, settings);
    }

    /** Valida la URL pedida con la configuración del servidor. */
    public static URI check(String text, ImageSettings settings) {
        if (!settings.allowUrls()) {
            throw new IllegalArgumentException("Las imágenes por URL están desactivadas en este servidor "
                    + "(allowUrls en config/komaedit/images.json); usa un archivo de config/komaedit/images/");
        }
        return check(text, settings.allowedHosts(), false);
    }

    /** @param insecure solo para pruebas: admite {@code http} y cualquier puerto */
    static URI check(String text, List<String> allowedHosts, boolean insecure) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Falta la URL de la imagen");
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("La URL es demasiado larga (máximo " + MAX_LENGTH + " caracteres)");
        }
        URI uri;
        try {
            uri = new URI(text.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("La URL no es válida: " + exception.getReason());
        }
        return checkUri(uri, allowedHosts, insecure);
    }

    private static URI checkUri(URI uri, List<String> allowedHosts, boolean insecure) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(insecure && scheme.equals("http"))) {
            throw new IllegalArgumentException("Solo se admiten URL https://");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("La URL no puede llevar usuario ni contraseña");
        }
        String host = host(uri);
        if (host.isEmpty()) {
            throw new IllegalArgumentException("La URL no tiene dominio");
        }
        if (!insecure && uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Solo se admite el puerto 443 de https");
        }
        if (!allowed(host, allowedHosts)) {
            throw new IllegalArgumentException("El dominio " + host + " no está en la lista de permitidos del "
                    + "servidor (allowedHosts en config/komaedit/images.json)");
        }
        return uri;
    }

    /** Dominio en minúsculas y sin el punto final. */
    static String host(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return "";
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    /** {@code ejemplo.com} es exacto; {@code *.ejemplo.com} admite sus subdominios (no el dominio pelado). */
    public static boolean allowed(String host, List<String> patterns) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (pattern.startsWith("*.")) {
                String suffix = pattern.substring(1);
                if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                    return true;
                }
            } else if (normalized.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adónde lleva una redirección. Solo se acepta dentro del mismo esquema, host y puerto; si no, la descarga se
     * corta.
     */
    static URI redirect(URI from, String location, List<String> allowedHosts, boolean insecure) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("La imagen redirige sin decir adónde");
        }
        URI target;
        try {
            target = from.resolve(new URI(location.trim()));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("La imagen redirige a una URL no válida");
        }
        String fromHost = host(from);
        String toHost = host(target);
        boolean sameScheme = from.getScheme().equalsIgnoreCase(target.getScheme() == null ? "" : target.getScheme());
        if (!sameScheme || !fromHost.equals(toHost) || port(from) != port(target)) {
            throw new IllegalArgumentException("La imagen redirige a otro sitio (" + (toHost.isEmpty() ? location
                    : toHost) + "); no se siguen redirecciones fuera de " + fromHost);
        }
        return checkUri(target, allowedHosts, insecure);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    /** Falla si el dominio resuelve a alguna dirección interna. */
    static void checkResolves(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("No se encuentra el dominio " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IllegalArgumentException("El dominio " + host + " apunta a una dirección interna ("
                        + address.getHostAddress() + "); no se descarga");
            }
        }
    }

    /** ¿Es una dirección de internet (ni local, ni privada, ni reservada)? */
    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 0.0.0.0/8, 100.64.0.0/10 (CGNAT), 192.0.0.0/24, 198.18.0.0/15 (pruebas) y 240.0.0.0/4 (reservada).
            return first != 0 && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (bytes[2] & 0xFF) == 0)
                    && !(first == 198 && (second == 18 || second == 19)) && first < 240;
        }
        if (address instanceof Inet6Address) {
            // fc00::/7 (direcciones locales únicas).
            return (bytes[0] & 0xFE) != 0xFC;
        }
        return false;
    }
}
