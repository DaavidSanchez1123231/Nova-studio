package pe.nova.backend.model;

/** Lo que envía el frontend. El usuario NO viene aquí: sale del token JWT. */
public record ReservarCitaRequest(Integer idHorario, Integer idServicio) {}