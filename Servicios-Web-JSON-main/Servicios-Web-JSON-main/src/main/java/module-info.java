module com.desarrollo.serviciosweb {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    opens com.desarrollo.serviciosweb to javafx.fxml;
    opens com.desarrollo.serviciosweb.controller to javafx.fxml;
    // PropertyValueFactory (javafx.base) lee getters y Jackson mapea Cliente por reflexión.
    opens com.desarrollo.serviciosweb.model to javafx.base, com.fasterxml.jackson.databind;

    exports com.desarrollo.serviciosweb;
}
