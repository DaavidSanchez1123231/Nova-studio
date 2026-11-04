package pe.nova.backend.model;

public class DashboardMetricsDTO {
    private Integer citasHoy;        
    private Double ingresosEstimados;    
    private Integer clientesRegistrados;  
    private Integer solicitudesPendientes;
    public Integer getCitasHoy() {
        return citasHoy;
    }
    public void setCitasHoy(Integer citasHoy) {
        this.citasHoy = citasHoy;
    }
    public Double getIngresosEstimados() {
        return ingresosEstimados;
    }
    public void setIngresosEstimados(Double ingresosEstimados) {
        this.ingresosEstimados = ingresosEstimados;
    }
    public Integer getClientesRegistrados() {
        return clientesRegistrados;
    }
    public void setClientesRegistrados(Integer clientesRegistrados) {
        this.clientesRegistrados = clientesRegistrados;
    }
    public Integer getSolicitudesPendientes() {
        return solicitudesPendientes;
    }
    public void setSolicitudesPendientes(Integer solicitudesPendientes) {
        this.solicitudesPendientes = solicitudesPendientes;
    }
    
    

}