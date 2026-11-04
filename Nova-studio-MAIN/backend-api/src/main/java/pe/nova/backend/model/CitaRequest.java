package pe.nova.backend.model;

public class CitaRequest {

    private Integer id_usuario;
    private Integer id_servicio;
    private String fecha;
    private String hora;

    public Integer getId_usuario() {
        return id_usuario;
    }

    public void setId_usuario(Integer id_usuario) {
        this.id_usuario = id_usuario;
    }

    public Integer getId_servicio() {
        return id_servicio;
    }

    public void setId_servicio(Integer id_servicio) {
        this.id_servicio = id_servicio;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }
    private Integer id_horario;

public Integer getId_horario() {
    return id_horario;
}

public void setId_horario(Integer id_horario) {
    this.id_horario = id_horario;
}
}