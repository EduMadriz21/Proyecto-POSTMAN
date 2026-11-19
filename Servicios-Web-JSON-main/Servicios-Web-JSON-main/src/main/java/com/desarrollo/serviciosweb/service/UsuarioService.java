package com.desarrollo.serviciosweb.service;

import com.desarrollo.serviciosweb.model.Usuario;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class UsuarioService {
    private static final String URL = "https://jsonplaceholder.typicode.com/users";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Consulta la API de forma bloqueante: no llamar desde el hilo de JavaFX.
     *
     * @throws IOException con un mensaje legible si falla la conexión, el estado HTTP o el JSON.
     */
    public List<Usuario> obtenerUsuarios() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = cliente.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new IOException("Tiempo de espera agotado al consultar la API.", e);
        } catch (IOException e) {
            throw new IOException("No se pudo conectar con la API. Verifique su conexión a Internet.", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("La API respondió con el código HTTP " + response.statusCode() + ".");
        }

        return parsearUsuarios(response.body());
    }

    static List<Usuario> parsearUsuarios(String json) throws IOException {
        JsonNode datos;
        try {
            datos = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IOException("La API devolvió un JSON inválido.", e);
        }

        if (datos == null || !datos.isArray()) {
            throw new IOException("Formato de respuesta inesperado: se esperaba una lista de usuarios.");
        }

        List<Usuario> usuarios = new ArrayList<>();
        for (JsonNode dato : datos) {
            if (!dato.isObject()) {
                throw new IOException("Formato de respuesta inesperado: elemento de usuario inválido.");
            }
            JsonNode id = dato.path("id");
            usuarios.add(new Usuario(
                    id.canConvertToInt() ? id.asInt() : null,
                    dato.path("name").asText(""),
                    dato.path("username").asText(""),
                    dato.path("email").asText(""),
                    dato.path("phone").asText(""),
                    dato.path("address").path("city").asText(""),
                    dato.path("company").path("name").asText("")));
        }
        return usuarios;
    }
}
