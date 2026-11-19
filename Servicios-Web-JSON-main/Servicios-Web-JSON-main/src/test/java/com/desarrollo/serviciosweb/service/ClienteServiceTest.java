package com.desarrollo.serviciosweb.service;

import com.desarrollo.serviciosweb.model.Cliente;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ClienteServiceTest {

    private static final String CLIENTE_COMPLETO = """
            {"id":7,"first_name":"Ana","last_name":"Pérez","email":"ana@example.com",
             "phone":"+505 8888-0000","date_of_birth":"1990-05-12","country":"Nicaragua",
             "city":"Managua","address":"Calle 1","annual_income":123456789.123456789,
             "credit_score":712,"has_unpaid_debt":true,"unpaid_debt_amount":1500.50,
             "status":"active","created_at":"2024-01-15T10:30:00.123456+00:00",
             "updated_at":"2024-02-01T08:00:00-06:00","campo_nuevo":"ignorado"}""";

    @Test
    void mapeaLos16Campos() throws IOException {
        Cliente c = ClienteService.parsearClientes("[" + CLIENTE_COMPLETO + "]").get(0);

        assertEquals(7L, c.getId());
        assertEquals("Ana", c.getFirstName());
        assertEquals("Pérez", c.getLastName());
        assertEquals("ana@example.com", c.getEmail());
        assertEquals("+505 8888-0000", c.getPhone());
        assertEquals(LocalDate.of(1990, 5, 12), c.getDateOfBirth());
        assertEquals("Nicaragua", c.getCountry());
        assertEquals("Managua", c.getCity());
        assertEquals("Calle 1", c.getAddress());
        assertEquals(new BigDecimal("123456789.123456789"), c.getAnnualIncome()); // sin pérdida de precisión
        assertEquals(712, c.getCreditScore());
        assertEquals(Boolean.TRUE, c.getHasUnpaidDebt());
        assertEquals(new BigDecimal("1500.50"), c.getUnpaidDebtAmount());
        assertEquals("active", c.getStatus());
        assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 123_456_000, ZoneOffset.UTC), c.getCreatedAt());
        assertEquals(ZoneOffset.ofHours(-6), c.getUpdatedAt().getOffset()); // offset original conservado
    }

    @Test
    void camposNulosQuedanNull() throws IOException {
        Cliente c = ClienteService.parsearClientes("""
                [{"id":1,"first_name":null,"date_of_birth":null,"annual_income":null,
                  "credit_score":null,"has_unpaid_debt":null,"created_at":null}]""").get(0);

        assertEquals(1L, c.getId());
        assertNull(c.getFirstName());
        assertNull(c.getDateOfBirth());
        assertNull(c.getAnnualIncome());
        assertNull(c.getCreditScore());
        assertNull(c.getHasUnpaidDebt());
        assertNull(c.getCreatedAt());
    }

    @Test
    void datosInvalidosLanzanIOException() {
        assertThrows(IOException.class, () -> ClienteService.parsearClientes("{no es json"));
        assertThrows(IOException.class, () -> ClienteService.parsearClientes("{\"id\":1}"));
        assertThrows(IOException.class, () -> ClienteService.parsearClientes("[{\"date_of_birth\":\"12/05/1990\"}]"));
        assertThrows(IOException.class, () -> ClienteService.parsearClientes("null"));
    }

    @Test
    void estadosHttpDeError() throws IOException {
        ClienteService.verificarEstado(200);
        ClienteService.verificarEstado(206);
        assertTrue(assertThrows(IOException.class, () -> ClienteService.verificarEstado(401))
                .getMessage().contains("autenticación"));
        assertTrue(assertThrows(IOException.class, () -> ClienteService.verificarEstado(403))
                .getMessage().contains("autenticación"));
        assertTrue(assertThrows(IOException.class, () -> ClienteService.verificarEstado(500))
                .getMessage().contains("500"));
    }

    @Test
    void sinClaveNoConsulta() {
        ClienteService servicio = new ClienteService("http://127.0.0.1:1/", null, null, 10);
        assertTrue(assertThrows(IOException.class, servicio::obtenerClientes)
                .getMessage().contains("clave publicable"));
    }

    @Test
    void paginaPorRangosSinDuplicados() throws Exception {
        // 5 registros, páginas de 2: rangos 0-1, 2-3, 4-5 (la última trae 1 y termina).
        try (ServidorPrueba servidor = new ServidorPrueba(desde -> respuestaRango(desde, 2, 5))) {
            List<Cliente> clientes = new ClienteService(servidor.url(), "clave-prueba", null, 2).obtenerClientes();

            assertEquals(List.of(1L, 2L, 3L, 4L, 5L), clientes.stream().map(Cliente::getId).toList());
            assertEquals(List.of("0-1", "2-3", "4-5"), servidor.rangos);
            assertTrue(servidor.claves.stream().allMatch("clave-prueba"::equals));
        }
    }

    @Test
    void paginaCompletaFinalCon416() throws Exception {
        // 4 registros, páginas de 2: el rango 4-5 responde 416 (fuera del total).
        try (ServidorPrueba servidor = new ServidorPrueba(desde ->
                desde >= 4 ? new String[]{"416 Range Not Satisfiable", "{}"} : respuestaRango(desde, 2, 4))) {
            List<Cliente> clientes = new ClienteService(servidor.url(), "k", null, 2).obtenerClientes();

            assertEquals(4, clientes.size());
            assertEquals(List.of("0-1", "2-3", "4-5"), servidor.rangos);
        }
    }

    @Test
    void error401DelServidorSePropaga() throws Exception {
        try (ServidorPrueba servidor = new ServidorPrueba(desde ->
                new String[]{"401 Unauthorized", "{\"message\":\"Invalid API key\"}"})) {
            IOException e = assertThrows(IOException.class,
                    () -> new ClienteService(servidor.url(), "mala", null, 2).obtenerClientes());
            assertTrue(e.getMessage().contains("401"));
        }
    }

    private static String[] respuestaRango(int desde, int tamano, int total) {
        String cuerpo = IntStream.rangeClosed(desde + 1, Math.min(desde + tamano, total))
                .mapToObj(id -> "{\"id\":" + id + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return new String[]{"206 Partial Content", cuerpo};
    }

    /** Servidor HTTP/1.1 mínimo: responde según el inicio del Range y registra cabeceras recibidas. */
    private static final class ServidorPrueba implements AutoCloseable {
        final List<String> rangos = new CopyOnWriteArrayList<>();
        final List<String> claves = new CopyOnWriteArrayList<>();
        private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());

        ServidorPrueba(Function<Integer, String[]> respuesta) throws IOException {
            Thread hilo = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket s = socket.accept()) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                        String rango = "0-0";
                        for (String linea = in.readLine(); linea != null && !linea.isEmpty(); linea = in.readLine()) {
                            String minus = linea.toLowerCase();
                            if (minus.startsWith("range:")) {
                                rango = linea.substring(6).trim();
                            } else if (minus.startsWith("apikey:")) {
                                claves.add(linea.substring(7).trim());
                            }
                        }
                        rangos.add(rango);
                        String[] r = respuesta.apply(Integer.parseInt(rango.split("-")[0]));
                        byte[] cuerpo = r[1].getBytes(StandardCharsets.UTF_8);
                        OutputStream out = s.getOutputStream();
                        out.write(("HTTP/1.1 " + r[0] + "\r\nContent-Type: application/json\r\nContent-Length: "
                                + cuerpo.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        out.write(cuerpo);
                        out.flush();
                    } catch (IOException e) {
                        // socket cerrado al terminar la prueba
                    }
                }
            }, "servidor-prueba");
            hilo.setDaemon(true);
            hilo.start();
        }

        String url() {
            return "http://127.0.0.1:" + socket.getLocalPort() + "/rest/v1/customers?select=*&order=id.asc";
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
