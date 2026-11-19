package com.desarrollo.serviciosweb.service;

import com.desarrollo.serviciosweb.model.Usuario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioServiceTest {

    @Test
    void parseaCamposAnidadosDeJsonPlaceholder() throws IOException {
        String json = """
                [{"id":1,"name":"Leanne Graham","username":"Bret","email":"Sincere@april.biz",
                  "address":{"street":"Kulas Light","city":"Gwenborough"},
                  "phone":"1-770-736-8031 x56442",
                  "company":{"name":"Romaguera-Crona"}}]
                """;

        List<Usuario> usuarios = UsuarioService.parsearUsuarios(json);

        assertEquals(1, usuarios.size());
        Usuario u = usuarios.get(0);
        assertEquals(1, u.getId());
        assertEquals("Leanne Graham", u.getNombre());
        assertEquals("Bret", u.getUsername());
        assertEquals("Sincere@april.biz", u.getEmail());
        assertEquals("1-770-736-8031 x56442", u.getTelefono());
        assertEquals("Gwenborough", u.getCiudad());
        assertEquals("Romaguera-Crona", u.getEmpresa());
    }

    @Test
    void camposFaltantesQuedanVacios() throws IOException {
        Usuario u = UsuarioService.parsearUsuarios("[{}]").get(0);

        assertNull(u.getId());
        assertEquals("", u.getNombre());
        assertEquals("", u.getCiudad());
        assertEquals("", u.getEmpresa());
    }

    @Test
    void listaVaciaDevuelveCeroUsuarios() throws IOException {
        assertTrue(UsuarioService.parsearUsuarios("[]").isEmpty());
    }

    @Test
    void jsonInvalidoLanzaIOException() {
        IOException e = assertThrows(IOException.class, () -> UsuarioService.parsearUsuarios("{no es json"));
        assertTrue(e.getMessage().contains("JSON inválido"));
    }

    @Test
    void raizQueNoEsListaLanzaIOException() {
        assertThrows(IOException.class, () -> UsuarioService.parsearUsuarios("{\"id\":1}"));
    }

    @Test
    void elementoQueNoEsObjetoLanzaIOException() {
        assertThrows(IOException.class, () -> UsuarioService.parsearUsuarios("[1, 2]"));
    }
}
