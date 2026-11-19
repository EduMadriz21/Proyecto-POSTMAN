package com.desarrollo.serviciosweb.model;

/**
 * Usuario obtenido de JSONPlaceholder (/users).
 * Los getters son usados por PropertyValueFactory en la tabla.
 */
public class Usuario {
    private Integer id;
    private String nombre;
    private String username;
    private String email;
    private String telefono;
    private String ciudad;
    private String empresa;

    public Usuario() {
    }

    public Usuario(Integer id, String nombre, String username, String email,
                   String telefono, String ciudad, String empresa) {
        this.id = id;
        this.nombre = nombre;
        this.username = username;
        this.email = email;
        this.telefono = telefono;
        this.ciudad = ciudad;
        this.empresa = empresa;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public String getEmpresa() {
        return empresa;
    }

    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }
}
