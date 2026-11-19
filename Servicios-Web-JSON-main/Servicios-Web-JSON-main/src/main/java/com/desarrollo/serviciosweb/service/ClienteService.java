package com.desarrollo.serviciosweb.service;

import com.desarrollo.serviciosweb.model.Cliente;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Consulta (solo lectura) la tabla "customers" de Supabase vía PostgREST.
 * <p>
 * Configuración, en orden de prioridad: variables de entorno SUPABASE_PUBLISHABLE_KEY /
 * SUPABASE_ACCESS_TOKEN, o el archivo local {@code supabase.local.properties}
 * (claves supabase.publishableKey / supabase.accessToken) en el directorio de trabajo.
 * Ese archivo está excluido del control de versiones.
 */
public class ClienteService {
    static final String URL_POR_DEFECTO =
            "https://sqpvqkkdfgtzdgrjrgxs.supabase.co/rest/v1/customers?select=*&order=id.asc";
    static final Path ARCHIVO_CONFIG = Path.of("supabase.local.properties");
    /** Límite por defecto de filas por respuesta en Supabase (max-rows). */
    static final int TAMANO_PAGINA = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // Conservar el offset que envía Supabase en los timestamptz.
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    private static final TypeReference<List<Cliente>> LISTA_CLIENTES = new TypeReference<>() {
    };

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String url;
    private final String apiKey;
    private final String accessToken;
    private final int tamanoPagina;

    public ClienteService() {
        this(URL_POR_DEFECTO, leerConfig("SUPABASE_PUBLISHABLE_KEY", "supabase.publishableKey"),
                leerConfig("SUPABASE_ACCESS_TOKEN", "supabase.accessToken"), TAMANO_PAGINA);
    }

    ClienteService(String url, String apiKey, String accessToken, int tamanoPagina) {
        this.url = url;
        this.apiKey = apiKey;
        this.accessToken = accessToken;
        this.tamanoPagina = tamanoPagina;
    }

    /**
     * Recupera todos los clientes paginando con cabeceras Range.
     * Bloqueante: no llamar desde el hilo de JavaFX.
     *
     * @throws IOException con mensaje legible ante falta de configuración, red, HTTP o JSON inválido.
     */
    public List<Cliente> obtenerClientes() throws IOException, InterruptedException {
        if (apiKey == null) {
            throw new IOException("Falta la clave publicable de Supabase. Defina SUPABASE_PUBLISHABLE_KEY o cree "
                    + ARCHIVO_CONFIG.toAbsolutePath() + ".");
        }

        List<Cliente> todos = new ArrayList<>();
        // Orden estable por id (en la URL) + rangos contiguos = sin duplicados ni huecos.
        for (int inicio = 0; ; inicio += tamanoPagina) {
            List<Cliente> pagina = obtenerPagina(inicio, inicio + tamanoPagina - 1);
            todos.addAll(pagina);
            if (pagina.size() < tamanoPagina) {
                return todos;
            }
        }
    }

    private List<Cliente> obtenerPagina(int desde, int hasta) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", apiKey)
                .header("Accept", "application/json")
                .header("Range-Unit", "items")
                .header("Range", desde + "-" + hasta)
                .GET();
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        HttpResponse<String> response;
        try {
            response = cliente.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new IOException("Tiempo de espera agotado al consultar Supabase.", e);
        } catch (IOException e) {
            throw new IOException("No se pudo conectar con Supabase. Verifique su conexión a Internet.", e);
        }

        // 416: rango fuera del total; la página anterior era la última y estaba completa.
        if (response.statusCode() == 416) {
            return List.of();
        }
        verificarEstado(response.statusCode());
        return parsearClientes(response.body());
    }

    static void verificarEstado(int estado) throws IOException {
        if (estado == 200 || estado == 206) {
            return;
        }
        if (estado == 401 || estado == 403) {
            throw new IOException("Supabase rechazó la autenticación (HTTP " + estado
                    + "). Verifique la clave publicable y las políticas RLS.");
        }
        throw new IOException("Supabase respondió con el código HTTP " + estado + ".");
    }

    static List<Cliente> parsearClientes(String json) throws IOException {
        try {
            List<Cliente> clientes = MAPPER.readValue(json, LISTA_CLIENTES);
            if (clientes == null) {
                throw new IOException("Formato de respuesta inesperado: se esperaba una lista de clientes.");
            }
            return clientes;
        } catch (JsonProcessingException e) {
            throw new IOException("Supabase devolvió datos con un formato inesperado.", e);
        }
    }

    private static String leerConfig(String variableEntorno, String propiedad) {
        String valor = System.getenv(variableEntorno);
        if (valor == null && Files.isRegularFile(ARCHIVO_CONFIG)) {
            Properties props = new Properties();
            try (Reader reader = Files.newBufferedReader(ARCHIVO_CONFIG)) {
                props.load(reader);
                valor = props.getProperty(propiedad);
            } catch (IOException e) {
                // Archivo ilegible: se trata como configuración ausente (el servicio lo informa).
            }
        }
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
