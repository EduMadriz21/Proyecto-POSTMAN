package com.desarrollo.serviciosweb.controller;

import com.desarrollo.serviciosweb.model.Usuario;
import com.desarrollo.serviciosweb.service.UsuarioService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.util.List;

public class UsuariosController {
    private final UsuarioService usuarioService = new UsuarioService();

    @FXML
    private Button btnConsultar;
    @FXML
    private Label lblEstado;
    @FXML
    private TableView<Usuario> tablaUsuarios;

    @FXML
    private void initialize() {
        tablaUsuarios.setPlaceholder(new Label("Sin datos. Presione Consultar usuarios."));
    }

    @FXML
    private void onConsultarUsuarios() {
        Task<List<Usuario>> tarea = new Task<>() {
            @Override
            protected List<Usuario> call() throws Exception {
                return usuarioService.obtenerUsuarios();
            }
        };

        // Los handlers de Task se ejecutan en el hilo de JavaFX.
        tarea.setOnSucceeded(e -> {
            List<Usuario> usuarios = tarea.getValue();
            tablaUsuarios.setItems(FXCollections.observableArrayList(usuarios));
            lblEstado.setText("Se cargaron " + usuarios.size() + " usuarios.");
            btnConsultar.setDisable(false);
        });
        tarea.setOnFailed(e -> {
            Throwable error = tarea.getException();
            String mensaje = error != null && error.getMessage() != null
                    ? error.getMessage()
                    : "Error desconocido al consultar usuarios.";
            lblEstado.setText("Error: " + mensaje);
            btnConsultar.setDisable(false);
        });

        btnConsultar.setDisable(true);
        lblEstado.setText("Consultando usuarios...");

        Thread hilo = new Thread(tarea, "consulta-usuarios");
        hilo.setDaemon(true);
        hilo.start();
    }
}
