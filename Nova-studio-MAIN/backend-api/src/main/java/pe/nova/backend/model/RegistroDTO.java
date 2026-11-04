package pe.nova.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistroDTO {
    private String nombre;
    private String email;
    private String password;
    private String telefono;
    // Consentimientos (Ley N.° 29733): se validan en el servidor y se guardan con fecha y versión
    private boolean aceptaPrivacidad;
    private boolean aceptaTerminos;
    private boolean aceptaMarketing;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public boolean isAceptaPrivacidad() { return aceptaPrivacidad; }
    public void setAceptaPrivacidad(boolean aceptaPrivacidad) { this.aceptaPrivacidad = aceptaPrivacidad; }
    public boolean isAceptaTerminos() { return aceptaTerminos; }
    public void setAceptaTerminos(boolean aceptaTerminos) { this.aceptaTerminos = aceptaTerminos; }
    public boolean isAceptaMarketing() { return aceptaMarketing; }
    public void setAceptaMarketing(boolean aceptaMarketing) { this.aceptaMarketing = aceptaMarketing; }
}
