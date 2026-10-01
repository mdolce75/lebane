package com.lebane.address;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor HTTP local que imita {@code GET /direcciones} de Georef, con respuesta configurable (status, cuerpo,
 * demora) para probar el cliente real sin red: éxito, 4xx, 5xx, JSON inválido y lentitud.
 */
public final class FakeGeorefServer implements AutoCloseable {

    public static final String RESPUESTA_OK = """
            {"cantidad":1,"direcciones":[{"altura":{"unidad":null,"valor":4850},
            "calle":{"categoria":"CALLE","id":"0209801005940","nombre":"AV. DEL LIBERTADOR"},
            "departamento":{"id":"02098","nombre":"Comuna 14"},
            "localidad_censal":{"id":"02000010","nombre":"Ciudad Autónoma de Buenos Aires"},
            "nomenclatura":"AV. DEL LIBERTADOR 4850, Comuna 14, Ciudad Autónoma de Buenos Aires","piso":null,
            "provincia":{"id":"02","nombre":"Ciudad Autónoma de Buenos Aires"},
            "ubicacion":{"lat":-34.5903465757709,"lon":-58.429718046774255}}],"inicio":0,"total":1}""";

    private record Respuesta(int status, String body, long delayMs) {
    }

    private final HttpServer server;
    private final AtomicReference<Respuesta> respuesta = new AtomicReference<>(new Respuesta(200, RESPUESTA_OK, 0));
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private final List<String> requestIds = new CopyOnWriteArrayList<>();

    public FakeGeorefServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/direcciones", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respond(int status, String body) {
        respuesta.set(new Respuesta(status, body, 0));
    }

    public void respondSlowly(long delayMs) {
        respuesta.set(new Respuesta(200, RESPUESTA_OK, delayMs));
    }

    public List<String> queries() {
        return queries;
    }

    public List<String> requestIds() {
        return requestIds;
    }

    public void reset() {
        respuesta.set(new Respuesta(200, RESPUESTA_OK, 0));
        queries.clear();
        requestIds.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        queries.add(exchange.getRequestURI().getRawQuery());
        String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
        if (requestId != null) {
            requestIds.add(requestId);
        }
        Respuesta actual = respuesta.get();
        try {
            if (actual.delayMs() > 0) {
                Thread.sleep(actual.delayMs());
            }
            byte[] body = actual.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(actual.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // El cliente cortó la conexión por timeout.
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
