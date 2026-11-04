
package pe.nova.backend.Controller;

import pe.nova.backend.service.GestionComercialService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GestionComercialController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GestionComercialService comercialService;

    // ==========================================
    // REGISTRAR PRODUCTO
    // ==========================================

    @PostMapping("/productos")
    public ResponseEntity<?> registrarProducto(
            @RequestBody Map<String, Object> body) {

        try {
            String nombre = texto(body, "nombre");
            String categoria = texto(body, "categoria");

            if (nombre.isBlank() || categoria.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "El nombre y la categoría son obligatorios.")
                );
            }

            if (nombre.length() > 100 || categoria.length() > 50) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "El nombre o la categoría exceden el tamaño permitido.")
                );
            }

            double precio = Double.parseDouble(
                    obligatorio(body, "precio")
            );

            int stock = Integer.parseInt(
                    obligatorio(body, "stock")
            );

            if (!Double.isFinite(precio) || precio < 0 || stock < 0) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "El precio y el stock deben ser no negativos.")
                );
            }

            if (stock < 0) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "El stock no puede ser negativo.")
                );
            }

            String descripcion = texto(body, "desc");
            String imagenUrl = texto(body, "imagen_url");

            if (descripcion.length() > 255) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "La descripción no puede superar los 255 caracteres.")
                );
            }

            return ResponseEntity.ok(
                    comercialService.registrarProducto(
                            nombre,
                            categoria,
                            precio,
                            stock,
                            descripcion,
                            imagenUrl
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("mensaje", mensajeError(e,
                            "Los datos del producto no son válidos."))
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("mensaje", "Error al registrar el producto.")
            );
        }
    }

    // ==========================================
    // LISTAR PRODUCTOS
    // ==========================================

    @GetMapping("/productos")
    public ResponseEntity<List<Map<String, Object>>> listarProductos() {
        return ResponseEntity.ok(
                comercialService.obtenerProductos()
        );
    }

    // ==========================================
    // MODIFICAR PRODUCTO
    // ==========================================

    @PutMapping("/productos/{id}")
    public ResponseEntity<?> modificarProducto(
            @PathVariable int id,
            @RequestBody Map<String, Object> body) {

        try {
            String nombre = texto(body, "nombre");
            String categoria = texto(body, "categoria");

            if (nombre.isBlank() || categoria.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "El nombre y la categoría son obligatorios.")
                );
            }

            if (nombre.length() > 100 || categoria.length() > 50) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "El nombre o la categoría exceden el tamaño permitido.")
                );
            }

            double precio = Double.parseDouble(
                    obligatorio(body, "precio")
            );

            int stock = Integer.parseInt(
                    obligatorio(body, "stock")
            );

            if (!Double.isFinite(precio) || precio < 0 || stock < 0) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "El precio y el stock deben ser no negativos.")
                );
            }

            String descripcion = texto(body, "desc");
            String imagenUrl = texto(body, "imagen_url");

            if (descripcion.length() > 255) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "La descripción no puede superar los 255 caracteres.")
                );
            }

            Map<String, Object> respuesta =
                    comercialService.modificarProducto(
                            id,
                            nombre,
                            categoria,
                            precio,
                            stock,
                            descripcion,
                            imagenUrl
                    );

            if (respuesta.containsKey("no_encontrado")) {
                return ResponseEntity.status(404).body(
                        Map.of("mensaje",
                                "Producto no encontrado o eliminado.")
                );
            }

            return ResponseEntity.ok(respuesta);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("mensaje", mensajeError(e,
                            "Los datos del producto no son válidos."))
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("mensaje", "Error al modificar el producto.")
            );
        }
    }

    // ==========================================
    // ELIMINAR PRODUCTO (BAJA LÓGICA)
    // ==========================================

    @DeleteMapping("/productos/{id}")
    public ResponseEntity<?> eliminarProducto(@PathVariable int id) {

        try {
            int filas = jdbcTemplate.update(
                    """
                    UPDATE productos
                    SET estado = FALSE
                    WHERE id_producto = ?
                      AND estado = TRUE
                    """,
                    id
            );

            if (filas == 0) {
                return ResponseEntity.status(404).body(
                        Map.of("mensaje",
                                "Producto no encontrado o ya eliminado.")
                );
            }

            return ResponseEntity.ok(
                    Map.of("mensaje",
                            "Producto eliminado correctamente.")
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("mensaje", "Error al eliminar el producto.")
            );
        }
    }

    // ==========================================
    // REGISTRAR VENTA
    // ==========================================

    @PostMapping("/ventas")
    public ResponseEntity<?> registrarVenta(
            @RequestBody Map<String, Object> body) {

        try {
            Integer idCliente =
                    enteroOpcional(body.get("id_cliente"), "cliente");

            Integer idServicio =
                    enteroOpcional(body.get("id_servicio"), "servicio");

            Integer idProducto =
                    enteroOpcional(body.get("id_producto"), "producto");

            Integer cantidad =
                    enteroOpcional(body.get("cantidad"), "cantidad");

            String tipo = texto(body, "tipo");
            String metodo = texto(body, "metodo");

            Map<String, Object> respuesta =
                    comercialService.registrarVenta(
                            idCliente,
                            tipo,
                            metodo,
                            idServicio,
                            idProducto,
                            cantidad
                    );

            return ResponseEntity.ok(respuesta);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("mensaje", mensajeError(e,
                            "Los datos de la venta no son válidos."))
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("mensaje",
                            "No se pudo registrar la venta. "
                                    + "Comprueba el servidor y la base de datos.")
            );
        }
    }

    // ==========================================
    // LISTAR VENTAS
    // ==========================================

    @GetMapping("/ventas")
    public ResponseEntity<List<Map<String, Object>>> listarVentas() {
        return ResponseEntity.ok(
                comercialService.obtenerVentasRecientes()
        );
    }

    // ==========================================
    // MÉTRICAS DE CAJA DEL DÍA
    // ==========================================

    @GetMapping("/ventas/metricas-hoy")
    public ResponseEntity<Map<String, Object>> metricasHoy() {
        return ResponseEntity.ok(
                comercialService.obtenerMetricasHoy()
        );
    }

    // ==========================================
    // ANULAR VENTA
    // ==========================================

    @PostMapping("/ventas/{id}/anular")
    public ResponseEntity<?> anularVenta(
            @PathVariable int id,
            @RequestBody Map<String, Object> body) {

        try {
            String motivo = texto(body, "motivo");

            if (motivo.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("mensaje",
                                "Debes indicar el motivo de la anulación.")
                );
            }

            return ResponseEntity.ok(
                    comercialService.anularVenta(id, motivo)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("mensaje", mensajeError(e,
                            "No se pudo anular la venta."))
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("mensaje", "Error al anular la venta.")
            );
        }
    }

    // ==========================================
    // CREAR PROMOCIÓN
    // ==========================================

    @PostMapping("/promociones")
    public ResponseEntity<?> crearPromocion(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> respuesta = comercialService.crearPromocion(
                    obligatorio(body, "nombre"),
                    Integer.parseInt(obligatorio(body, "descuento")),
                    obligatorio(body, "codigo"),
                    obligatorio(body, "fecha_expiracion"),
                    texto(body, "descripcion")
            );
            return ResponseEntity.ok(respuesta);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(409).body(
                    Map.of("mensaje", "Ya existe una promoción con ese código. Usa otro código.")
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("mensaje", "No se pudo crear la promoción. "
                            + mensajeError(e, "Revisa los datos."))
            );
        }
    }

    @PutMapping("/promociones/{id}")
    public ResponseEntity<?> actualizarPromocion(
            @PathVariable int id,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> respuesta = comercialService.actualizarPromocion(
                    id,
                    obligatorio(body, "nombre"),
                    Integer.parseInt(obligatorio(body, "descuento")),
                    obligatorio(body, "codigo"),
                    obligatorio(body, "fecha_expiracion"),
                    texto(body, "descripcion")
            );
            return ResponseEntity.ok(respuesta);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(409).body(
                    Map.of("mensaje", "Ya existe una promoción con ese código. Usa otro código.")
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("mensaje", "No se pudo actualizar la promoción. "
                            + mensajeError(e, "Revisa los datos."))
            );
        }
    }


    // ==========================================
    // LISTAR PROMOCIONES
    // ==========================================

    @GetMapping("/promociones")
    public ResponseEntity<List<Map<String, Object>>> listarPromociones() {
        return ResponseEntity.ok(
                comercialService.obtenerPromociones()
        );
    }

    // ==========================================
    // ELIMINAR PROMOCIÓN (BAJA LÓGICA)
    // ==========================================

    @DeleteMapping("/promociones/{id}")
    public ResponseEntity<?> eliminarPromocion(@PathVariable int id) {

        try {
            int filas = jdbcTemplate.update(
                    """
                    UPDATE promociones
                    SET estado = FALSE
                    WHERE id_promocion = ?
                      AND estado = TRUE
                    """,
                    id
            );

            if (filas == 0) {
                return ResponseEntity.status(404).body(
                        Map.of("mensaje", "Promoción no encontrada.")
                );
            }

            return ResponseEntity.ok(
                    Map.of("mensaje",
                            "Promoción eliminada correctamente.")
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("mensaje", "Error al eliminar la promoción.")
            );
        }
    }

    // ==========================================
    // MÉTODOS AUXILIARES
    // ==========================================

    private String texto(
            Map<String, Object> body,
            String campo) {

        Object valor = body.get(campo);

        return valor == null
                ? ""
                : valor.toString().trim();
    }

    private String obligatorio(
            Map<String, Object> body,
            String campo) {

        String valor = texto(body, campo);

        if (valor.isBlank()) {
            throw new IllegalArgumentException(
                    "El campo '" + campo + "' es obligatorio."
            );
        }

        return valor;
    }

    private Integer enteroOpcional(
            Object valor,
            String campo) {

        if (valor == null || valor.toString().isBlank()) {
            return null;
        }

        try {
            return Integer.valueOf(valor.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "El campo '" + campo + "' debe ser un número entero."
            );
        }
    }

    private String mensajeError(
            Exception e,
            String mensajePredeterminado) {

        return e.getMessage() == null || e.getMessage().isBlank()
                ? mensajePredeterminado
                : e.getMessage();
    }
}
