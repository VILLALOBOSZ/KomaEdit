package com.komainu.moxia.image;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Descarga una imagen por {@code https} con los límites del servidor. Se llama desde un worker de KomaEdit, nunca
 * desde el hilo del servidor.
 *
 * <ul>
 *     <li>La URL pasa por {@link ImageUrlPolicy}: dominio permitido y sin direcciones internas.</li>
 *     <li>Las redirecciones no se siguen solas: se aceptan hasta tres, y solo dentro del mismo host.</li>
 *     <li>El cuerpo se corta en cuanto pasa de {@code maxBytes}, lo diga o no la cabecera {@code Content-Length}.</li>
 *     <li>Toda la descarga, redirecciones incluidas, tiene un tiempo máximo; al pasarlo se cancela.</li>
 * </ul>
 */
public final class ImageDownloader {
    static final int MAX_REDIRECTS = 3;
    private final List<String> allowedHosts;
    private final long maxBytes;
    private final Duration timeout;
    private final boolean insecure;

    private ImageDownloader(List<String> allowedHosts, long maxBytes, Duration timeout, boolean insecure) {
        this.allowedHosts = List.copyOf(allowedHosts);
        this.maxBytes = maxBytes;
        this.timeout = timeout;
        this.insecure = insecure;
    }

    public static ImageDownloader of(ImageSettings settings) {
        return new ImageDownloader(settings.allowedHosts(), settings.maxImageBytes(),
                Duration.ofSeconds(settings.timeoutSeconds()), false);
    }

    /** Para las pruebas con un servidor local: admite {@code http}, cualquier puerto y el bucle local. */
    static ImageDownloader forTests(List<String> allowedHosts, long maxBytes, Duration timeout) {
        return new ImageDownloader(allowedHosts, maxBytes, timeout, true);
    }

    public byte[] download(String url) throws InterruptedException {
        URI uri = ImageUrlPolicy.check(url, allowedHosts, insecure);
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout)
                .build();
        try {
            for (int redirects = 0; ; redirects++) {
                if (!insecure) {
                    ImageUrlPolicy.checkResolves(ImageUrlPolicy.host(uri));
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw timedOut();
                }
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofNanos(remaining))
                        .header("User-Agent", "KomaEdit-ImageFetcher")
                        .header("Accept", "image/png,image/jpeg,image/gif")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = send(client, request, remaining);
                int status = response.statusCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    if (redirects >= MAX_REDIRECTS) {
                        throw new IllegalArgumentException("La imagen redirige demasiadas veces");
                    }
                    uri = ImageUrlPolicy.redirect(uri, response.headers().firstValue("location").orElse(null),
                            allowedHosts, insecure);
                    continue;
                }
                if (status != 200) {
                    throw new IllegalArgumentException("El servidor de la imagen respondió " + status);
                }
                return response.body();
            }
        } finally {
            client.shutdownNow();
        }
    }

    private HttpResponse<byte[]> send(HttpClient client, HttpRequest request, long remainingNanos)
            throws InterruptedException {
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, this::handler);
        try {
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw timedOut();
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause != null) {
                if (cause instanceof IllegalArgumentException illegal) {
                    throw illegal;
                }
                if (cause instanceof HttpTimeoutException) {
                    throw timedOut();
                }
                if (cause.getCause() == null || cause.getCause() == cause) {
                    break;
                }
                cause = cause.getCause();
            }
            String reason = cause == null || cause.getMessage() == null ? String.valueOf(cause)
                    : cause.getMessage();
            throw new IllegalArgumentException("No se pudo descargar la imagen: " + reason);
        }
    }

    private HttpResponse.BodySubscriber<byte[]> handler(HttpResponse.ResponseInfo info) {
        if (info.statusCode() != 200) {
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        }
        long declared = info.headers().firstValueAsLong("content-length").orElse(-1L);
        return new Limited(maxBytes, declared);
    }

    private IllegalArgumentException timedOut() {
        return new IllegalArgumentException("La descarga de la imagen tardó más de " + timeout.toSeconds()
                + " s; se ha cancelado");
    }

    static IllegalArgumentException tooLarge(long maxBytes) {
        return new IllegalArgumentException("La imagen pesa más de " + maxBytes / 1024 + " KiB, el máximo del "
                + "servidor (maxImageBytes)");
    }

    /** Junta el cuerpo y lo corta en cuanto pasa del límite. */
    private static final class Limited implements HttpResponse.BodySubscriber<byte[]> {
        private final long maxBytes;
        private final long declared;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private long received;

        Limited(long maxBytes, long declared) {
            this.maxBytes = maxBytes;
            this.declared = declared;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (declared > maxBytes) {
                subscription.cancel();
                result.completeExceptionally(tooLarge(maxBytes));
                return;
            }
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                received += item.remaining();
                if (received > maxBytes) {
                    subscription.cancel();
                    result.completeExceptionally(tooLarge(maxBytes));
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                buffer.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(buffer.toByteArray());
        }
    }
}
