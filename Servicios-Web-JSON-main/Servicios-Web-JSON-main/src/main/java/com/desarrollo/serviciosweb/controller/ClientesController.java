package com.desarrollo.serviciosweb.controller;

import com.desarrollo.serviciosweb.model.Cliente;
import com.desarrollo.serviciosweb.service.ClienteService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ClientesController {
    /** Valores en el mismo orden que las 16 columnas declaradas en clientes-view.fxml. */
    private static final List<Function<Cliente, Object>> VALORES = List.of(
            Cliente::getId, Cliente::getFirstName, Cliente::getLastName, Cliente::getEmail,
            Cliente::getPhone, Cliente::getDateOfBirth, Cliente::getCountry, Cliente::getCity,
            Cliente::getAddress, Cliente::getAnnualIncome, Cliente::getCreditScore,
            Cliente::getHasUnpaidDebt, Cliente::getUnpaidDebtAmount, Cliente::getStatus,
            Cliente::getCreatedAt, Cliente::getUpdatedAt);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss xxx");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ClienteService clienteService = new ClienteService();
    private final ObservableList<Cliente> clientes = FXCollections.observableArrayList();
    private final DecimalFormat formatoMonto = new DecimalFormat("#,##0.00");
    private Label[] valoresDetalle;

    @FXML
    private Button btnConsultar;
    @FXML
    private Button btnActualizar;
    @FXML
    private ProgressIndicator indicadorCarga;
    @FXML
    private Label lblEstado;
    @FXML
    private Label lblContador;
    @FXML
    private TableView<Cliente> tablaClientes;
    @FXML
    private GridPane gridDetalle;

    @FXML
    @SuppressWarnings("unchecked")
    private void initialize() {
        if (tablaClientes.getColumns().size() != VALORES.size()) {
            throw new IllegalStateException("clientes-view.fxml debe declarar " + VALORES.size() + " columnas.");
        }

        valoresDetalle = new Label[VALORES.size()];
        for (int i = 0; i < VALORES.size(); i++) {
            Function<Cliente, Object> valor = VALORES.get(i);
            TableColumn<Cliente, Object> columna = (TableColumn<Cliente, Object>) tablaClientes.getColumns().get(i);
            // El valor crudo se conserva para ordenar correctamente (números, fechas); solo la celda se formatea.
            columna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valor.apply(d.getValue())));
            columna.setCellFactory(c -> new TableCell<>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : formatear(item, ""));
                }
            });

            Label titulo = new Label(columna.getText() + ":");
            titulo.setStyle("-fx-font-weight: bold;");
            Label dato = new Label("—");
            dato.setWrapText(true);
            valoresDetalle[i] = dato;
            gridDetalle.addRow(i, titulo, dato);
        }

        tablaClientes.setItems(clientes);
        tablaClientes.setPlaceholder(new Label("Sin datos. Presione Consultar clientes."));
        tablaClientes.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, actual) -> mostrarDetalle(actual));
    }

    @FXML
    private void onConsultarClientes() {
        Long idSeleccionado = tablaClientes.getSelectionModel().getSelectedItem() != null
                ? tablaClientes.getSelectionModel().getSelectedItem().getId()
                : null;

        Task<List<Cliente>> tarea = new Task<>() {
            @Override
            protected List<Cliente> call() throws Exception {
                return clienteService.obtenerClientes();
            }
        };

        // Los handlers de Task se ejecutan en el hilo de JavaFX.
        tarea.setOnSucceeded(e -> {
            List<Cliente> resultado = tarea.getValue();
            clientes.setAll(resultado); // reemplaza, nunca acumula filas
            lblContador.setText("Clientes cargados: " + resultado.size());
            lblEstado.setText("Conectado a Supabase. Última consulta: " + LocalTime.now().format(FORMATO_HORA));
            reseleccionar(idSeleccionado);
            finalizarCarga();
        });
        tarea.setOnFailed(e -> {
            Throwable error = tarea.getException();
            String mensaje = error != null && error.getMessage() != null
                    ? error.getMessage()
                    : "Error desconocido al consultar clientes.";
            lblEstado.setText("Error: " + mensaje);
            finalizarCarga();
        });

        btnConsultar.setDisable(true);
        btnActualizar.setDisable(true);
        indicadorCarga.setVisible(true);
        lblEstado.setText("Consultando clientes en Supabase...");

        Thread hilo = new Thread(tarea, "consulta-clientes");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void finalizarCarga() {
        indicadorCarga.setVisible(false);
        btnConsultar.setDisable(false);
        btnActualizar.setDisable(clientes.isEmpty());
    }

    private void reseleccionar(Long id) {
        clientes.stream()
                .filter(c -> id != null && Objects.equals(c.getId(), id))
                .findFirst()
                .ifPresentOrElse(c -> tablaClientes.getSelectionModel().select(c),
                        () -> tablaClientes.getSelectionModel().clearSelection());
    }

    private void mostrarDetalle(Cliente cliente) {
        for (int i = 0; i < VALORES.size(); i++) {
            valoresDetalle[i].setText(cliente == null ? "—" : formatear(VALORES.get(i).apply(cliente), "—"));
        }
    }

    private String formatear(Object valor, String siNulo) {
        if (valor == null) {
            return siNulo;
        }
        if (valor instanceof BigDecimal monto) {
            return formatoMonto.format(monto);
        }
        if (valor instanceof Boolean b) {
            return b ? "Sí" : "No";
        }
        if (valor instanceof LocalDate fecha) {
            return fecha.format(FORMATO_FECHA);
        }
        if (valor instanceof OffsetDateTime fechaHora) {
            return fechaHora.format(FORMATO_FECHA_HORA);
        }
        return valor.toString();
    }
}
