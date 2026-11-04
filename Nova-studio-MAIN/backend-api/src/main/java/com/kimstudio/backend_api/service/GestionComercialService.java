
package com.kimstudio.backend_api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class GestionComercialService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==========================================
    // REGISTRAR PRODUCTO
    // ==========================================

    public Map<String, Object> registrarProducto(
            String nombre,
            String categoria,
            double precio,
            int stock,
            String descripcion,
            String imagenUrl) {

        String sql = """
            INSERT INTO productos
                (nombre, categoria, precio, stock, descripcion, imagen_url)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id_producto
            """;

        Integer id = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                nombre,
                categoria,
                precio,
                stock,
                descripcion,
                imagenUrl
        );

        return Map.of(
                "mensaje", "Producto registrado correctamente.",
                "id_producto", id
        );
    }

    // ==========================================
    // LISTAR PRODUCTOS ACTIVOS
    // ==========================================

    public List<Map<String, Object>> obtenerProductos() {

        String sql = """
            SELECT
                id_producto,
                nombre,
                categoria,
                precio,
                stock,
                descripcion AS desc,
                imagen_url,
                estado
            FROM productos
            WHERE estado = TRUE
            ORDER BY id_producto DESC
            """;

        return jdbcTemplate.queryForList(sql);
    }

    // ==========================================
    // MODIFICAR PRODUCTO
    // ==========================================

    public Map<String, Object> modificarProducto(
            int idProducto,
            String nombre,
            String categoria,
            double precio,
            int stock,
            String descripcion,
            String imagenUrl) {

        String sql = """
            UPDATE productos
            SET nombre = ?,
                categoria = ?,
                precio = ?,
                stock = ?,
                descripcion = ?,
                imagen_url = ?
            WHERE id_producto = ?
              AND estado = TRUE
            """;

        int filas = jdbcTemplate.update(
                sql,
                nombre,
                categoria,
                precio,
                stock,
                descripcion,
                imagenUrl,
                idProducto
        );

        if (filas == 0) {
            return Map.of(
                    "no_encontrado", true,
                    "mensaje", "Producto no encontrado o eliminado."
            );
        }

        return Map.of(
                "mensaje", "Producto modificado correctamente.",
                "id_producto", idProducto
        );
    }

    // ==========================================
    // REGISTRAR VENTA
    // El servidor calcula el total con el catálogo.
    // Para producto, descuenta el stock dentro
    // de la misma transacción.
    // ==========================================

    @Transactional
    public Map<String, Object> registrarVenta(
            Integer idCliente,
            String tipoVenta,
            String metodoPago,
            Integer idServicio,
            Integer idProducto,
            Integer cantidad) {

        String tipo = tipoVenta == null
                ? ""
                : tipoVenta.trim().toLowerCase();

        String metodo = metodoPago == null
                ? ""
                : metodoPago.trim();

        if (!tipo.equals("servicio") && !tipo.equals("producto")) {
            throw new IllegalArgumentException(
                    "Selecciona un tipo de venta válido."
            );
        }

        if (!List.of("Yape", "Plin", "Tarjeta", "Efectivo")
                .contains(metodo)) {
            throw new IllegalArgumentException(
                    "El método de pago no es válido."
            );
        }

        if (metodo.equals("Plin")) {
            metodo = "Yape";
        }

        if (idCliente != null) {
            Integer existeCliente = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM usuarios
                    WHERE id_usuario = ?
                      AND rol = 'cliente'
                    """,
                    Integer.class,
                    idCliente
            );

            if (existeCliente == null || existeCliente == 0) {
                throw new IllegalArgumentException(
                        "El cliente seleccionado no existe."
                );
            }
        }

        // --------------------------------------
        // VENTA DE SERVICIO
        // --------------------------------------

        if (tipo.equals("servicio")) {

            if (idServicio == null) {
                throw new IllegalArgumentException(
                        "Selecciona el servicio que deseas cobrar."
                );
            }

            List<Map<String, Object>> servicios =
                    jdbcTemplate.queryForList(
                            """
                            SELECT nombre, precio
                            FROM servicios
                            WHERE id_servicio = ?
                              AND estado = TRUE
                            FOR UPDATE
                            """,
                            idServicio
                    );

            if (servicios.isEmpty()) {
                throw new IllegalArgumentException(
                        "El servicio seleccionado no existe o está inactivo."
                );
            }

            Object precioObjeto = servicios.get(0).get("precio");

            if (precioObjeto == null) {
                throw new IllegalArgumentException(
                        "El servicio no tiene un precio configurado."
                );
            }

            BigDecimal precio = new BigDecimal(
                    precioObjeto.toString()
            ).setScale(2, RoundingMode.HALF_UP);

            if (precio.signum() <= 0) {
                throw new IllegalArgumentException(
                        "El precio del servicio debe ser mayor que cero."
                );
            }

            Integer idVenta = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO ventas (
                        id_cliente,
                        id_servicio,
                        tipo_venta,
                        metodo_pago,
                        monto_total,
                        fecha_venta,
                        estado_venta
                    )
                    VALUES (?, ?, 'servicio', ?, ?, NOW(), 'completada')
                    RETURNING id_venta
                    """,
                    Integer.class,
                    idCliente,
                    idServicio,
                    metodo,
                    precio
            );

            return Map.of(
                    "mensaje", "Venta de servicio registrada correctamente.",
                    "id_venta", idVenta,
                    "tipo_venta", "servicio",
                    "total", precio
            );
        }

        // --------------------------------------
        // VENTA DE PRODUCTO
        // --------------------------------------

        if (idProducto == null) {
            throw new IllegalArgumentException(
                    "Selecciona el producto que deseas vender."
            );
        }

        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser un número entero mayor que cero."
            );
        }

        // Bloquea la fila del producto durante la venta
        // para evitar vender más unidades que las disponibles.
        List<Map<String, Object>> productos =
                jdbcTemplate.queryForList(
                        """
                        SELECT nombre, precio, stock
                        FROM productos
                        WHERE id_producto = ?
                          AND estado = TRUE
                        FOR UPDATE
                        """,
                        idProducto
                );

        if (productos.isEmpty()) {
            throw new IllegalArgumentException(
                    "El producto seleccionado no existe o está inactivo."
            );
        }

        Map<String, Object> producto = productos.get(0);

        Object precioObjeto = producto.get("precio");
        Object stockObjeto = producto.get("stock");

        if (precioObjeto == null || stockObjeto == null) {
            throw new IllegalArgumentException(
                    "El producto no tiene precio o stock configurado."
            );
        }

        BigDecimal precioUnitario = new BigDecimal(
                precioObjeto.toString()
        ).setScale(2, RoundingMode.HALF_UP);

        int stockDisponible = ((Number) stockObjeto).intValue();

        if (precioUnitario.signum() <= 0) {
            throw new IllegalArgumentException(
                    "El precio del producto debe ser mayor que cero."
            );
        }

        if (stockDisponible < cantidad) {
            throw new IllegalArgumentException(
                    "Stock insuficiente. Disponibles: "
                            + stockDisponible
                            + " unidades."
            );
        }

        BigDecimal total = precioUnitario
                .multiply(BigDecimal.valueOf(cantidad))
                .setScale(2, RoundingMode.HALF_UP);

        int filasStock = jdbcTemplate.update(
                """
                UPDATE productos
                SET stock = stock - ?
                WHERE id_producto = ?
                  AND estado = TRUE
                  AND stock >= ?
                """,
                cantidad,
                idProducto,
                cantidad
        );

        if (filasStock != 1) {
            throw new IllegalArgumentException(
                    "No se pudo actualizar el stock. Intenta nuevamente."
            );
        }

        Integer idVenta = jdbcTemplate.queryForObject(
                """
                INSERT INTO ventas (
                    id_cliente,
                    id_servicio,
                    tipo_venta,
                    metodo_pago,
                    monto_total,
                    fecha_venta,
                    estado_venta
                )
                VALUES (?, NULL, 'producto', ?, ?, NOW(), 'completada')
                RETURNING id_venta
                """,
                Integer.class,
                idCliente,
                metodo,
                total
        );

        jdbcTemplate.update(
                """
                INSERT INTO detalle_venta_producto (
                    id_venta,
                    id_producto,
                    cantidad,
                    precio_unitario
                )
                VALUES (?, ?, ?, ?)
                """,
                idVenta,
                idProducto,
                cantidad,
                precioUnitario
        );

        return Map.of(
                "mensaje", "Venta de producto registrada correctamente.",
                "id_venta", idVenta,
                "tipo_venta", "producto",
                "cantidad", cantidad,
                "total", total
        );
    }

   // ==========================================
    // LISTAR PROMOCIONES ACTIVAS
    // ==========================================

    public List<Map<String, Object>> obtenerPromociones() {
        return jdbcTemplate.queryForList(
                """
                SELECT id_promocion, nombre, descuento, codigo,
                       fecha_expiracion, descripcion, estado
                FROM public.promociones
                WHERE estado = TRUE
                ORDER BY id_promocion DESC
                """
        );
    }

    // ==========================================
    // CREAR PROMOCIÓN
    // ==========================================

    @Transactional
    public Map<String, Object> crearPromocion(
            String nombre,
            int descuento,
            String codigo,
            String fechaExpiracion,
            String descripcion) {

        String nombreLimpio = nombre == null ? "" : nombre.trim();
        String codigoLimpio = normalizarCodigoPromocion(codigo);
        String fechaLimpia = fechaExpiracion == null ? "" : fechaExpiracion.trim();
        String descripcionLimpia = descripcion == null ? "" : descripcion.trim();

        validarDatosPromocion(nombreLimpio, descuento, codigoLimpio,
                fechaLimpia, descripcionLimpia);
        verificarCodigoPromocionDisponible(codigoLimpio, null);

        // La conversión explícita permite que PostgreSQL seleccione la función
        // sp_crear_promocion(p_nombre, p_descuento, p_codigo, p_fecha date, p_descripcion).
        return jdbcTemplate.queryForMap(
                "SELECT * FROM public.sp_crear_promocion(?, ?, ?, CAST(? AS DATE), ?)",
                nombreLimpio,
                descuento,
                codigoLimpio,
                fechaLimpia,
                descripcionLimpia
        );
    }

    // ==========================================
    // ACTUALIZAR PROMOCIÓN
    // ==========================================

    @Transactional
    public Map<String, Object> actualizarPromocion(
            int id,
            String nombre,
            int descuento,
            String codigo,
            String fechaExpiracion,
            String descripcion) {

        String nombreLimpio = nombre == null ? "" : nombre.trim();
        String codigoLimpio = normalizarCodigoPromocion(codigo);
        String fechaLimpia = fechaExpiracion == null ? "" : fechaExpiracion.trim();
        String descripcionLimpia = descripcion == null ? "" : descripcion.trim();

        validarDatosPromocion(nombreLimpio, descuento, codigoLimpio,
                fechaLimpia, descripcionLimpia);

        Integer existentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.promociones WHERE id_promocion = ? AND estado = TRUE",
                Integer.class,
                id
        );
        if (existentes == null || existentes == 0) {
            throw new IllegalArgumentException("La promoción no existe o ya fue dada de baja.");
        }

        verificarCodigoPromocionDisponible(codigoLimpio, id);

        int filas = jdbcTemplate.update(
                """
                UPDATE public.promociones
                SET nombre = ?, descuento = ?, codigo = ?,
                    fecha_expiracion = CAST(? AS DATE), descripcion = ?
                WHERE id_promocion = ? AND estado = TRUE
                """,
                nombreLimpio,
                descuento,
                codigoLimpio,
                fechaLimpia,
                descripcionLimpia,
                id
        );

        if (filas == 0) {
            throw new IllegalArgumentException("No se pudo actualizar la promoción.");
        }

        return Map.of(
                "mensaje", "Promoción actualizada correctamente.",
                "id_promocion", id
        );
    }

    // ==========================================
    // VALIDACIONES DE PROMOCIONES
    // ==========================================

    private String normalizarCodigoPromocion(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    private void validarDatosPromocion(
            String nombre,
            int descuento,
            String codigo,
            String fechaExpiracion,
            String descripcion) {


         if (descripcion == null || descripcion.isBlank()) {
    throw new IllegalArgumentException("La descripción de la promoción es obligatoria.");
}
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la promoción es obligatorio.");
        }
        if (nombre.length() > 100) {
            throw new IllegalArgumentException("El nombre no puede superar los 100 caracteres.");
        }
        if (descuento < 1 || descuento > 100) {
            throw new IllegalArgumentException("El descuento debe estar entre 1 y 100.");
        }
        if (codigo == null || !codigo.matches("[A-Z0-9_-]{1,50}")) {
            throw new IllegalArgumentException(
                    "El código debe usar solo letras, números, guion o guion bajo (máximo 50 caracteres)."
            );
        }
        if (fechaExpiracion == null || fechaExpiracion.isBlank()) {
            throw new IllegalArgumentException("La fecha de vencimiento es obligatoria.");
        }

        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaExpiracion);
        } catch (Exception e) {
            throw new IllegalArgumentException("La fecha de vencimiento no tiene un formato válido (AAAA-MM-DD).");
        }

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        if (fecha.isBefore(hoy)) {
            throw new IllegalArgumentException("La fecha de vencimiento no puede estar en el pasado.");
        }
        if (descripcion != null && descripcion.length() > 255) {
            throw new IllegalArgumentException("La descripción no puede superar los 255 caracteres.");
        }
    }

    private void verificarCodigoPromocionDisponible(String codigo, Integer idExcluir) {
        Integer cantidad;
        if (idExcluir == null) {
            cantidad = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.promociones WHERE UPPER(codigo) = UPPER(?)",
                    Integer.class,
                    codigo
            );
        } else {
            cantidad = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.promociones WHERE UPPER(codigo) = UPPER(?) AND id_promocion <> ?",
                    Integer.class,
                    codigo,
                    idExcluir
            );
        }

        if (cantidad != null && cantidad > 0) {
            throw new IllegalArgumentException("Ya existe una promoción con ese código. Usa otro código.");
        }
    }


    // ==========================================
    // LISTAR VENTAS RECIENTES
    // No elimina del historial las anuladas.
    // ==========================================

    public List<Map<String, Object>> obtenerVentasRecientes() {

        String sql = """
            SELECT
                v.id_venta,
                'VTA-' || LPAD(
                    CAST(v.id_venta AS VARCHAR),
                    4,
                    '0'
                ) AS boleta,
                v.fecha_venta AS fecha,

                COALESCE(
                    u.nombre,
                    'Público General'
                ) AS cliente,

                CASE
                    WHEN v.tipo_venta = 'servicio'
                    THEN COALESCE(s.nombre, 'Servicio')
                    ELSE COALESCE(
                        dv.detalle,
                        'Venta de producto'
                    )
                END AS detalle,

                v.tipo_venta AS tipo,
                v.metodo_pago AS metodo,
                v.monto_total AS total,
                v.estado_venta AS estado,
                v.motivo_anulacion AS motivo,
                v.fecha_anulacion AS fecha_anulacion

            FROM ventas v

            LEFT JOIN usuarios u
                ON v.id_cliente = u.id_usuario

            LEFT JOIN servicios s
                ON v.id_servicio = s.id_servicio

            LEFT JOIN LATERAL (
                SELECT STRING_AGG(
                    COALESCE(p.nombre, 'Producto')
                    || ' x '
                    || d.cantidad::TEXT,
                    ', '
                    ORDER BY d.id_detalle
                ) AS detalle

                FROM detalle_venta_producto d

                LEFT JOIN productos p
                    ON p.id_producto = d.id_producto

                WHERE d.id_venta = v.id_venta
            ) dv ON TRUE

            ORDER BY v.fecha_venta DESC, v.id_venta DESC
            """;

        return jdbcTemplate.queryForList(sql);
    }

    // ==========================================
    // MÉTRICAS DEL DÍA
    // Solo suma ventas completadas.
    // ==========================================

    public Map<String, Object> obtenerMetricasHoy() {

        String sql = """
            SELECT
                COALESCE(
                    SUM(monto_total),
                    0
                ) AS "totalCaja",

                COALESCE(
                    SUM(
                        CASE
                            WHEN tipo_venta = 'servicio'
                            THEN monto_total
                            ELSE 0
                        END
                    ),
                    0
                ) AS "porServicios",

                COALESCE(
                    SUM(
                        CASE
                            WHEN tipo_venta = 'producto'
                            THEN monto_total
                            ELSE 0
                        END
                    ),
                    0
                ) AS "porProductos"

            FROM ventas
            WHERE fecha_venta::DATE = CURRENT_DATE
              AND estado_venta = 'completada'
            """;

        return jdbcTemplate.queryForMap(sql);
    }

    // ==========================================
    // ANULAR VENTA
    // Conserva el comprobante y el motivo.
    // Devuelve el stock de los productos vendidos.
    // ==========================================

    @Transactional
    public Map<String, Object> anularVenta(
            int idVenta,
            String motivo) {

        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                    "Debes indicar el motivo de la anulación."
            );
        }

        List<Map<String, Object>> ventas =
                jdbcTemplate.queryForList(
                        """
                        SELECT tipo_venta, estado_venta
                        FROM ventas
                        WHERE id_venta = ?
                        FOR UPDATE
                        """,
                        idVenta
                );

        if (ventas.isEmpty()) {
            throw new IllegalArgumentException(
                    "La venta no existe."
            );
        }

        Map<String, Object> venta = ventas.get(0);

        String estado = venta.get("estado_venta").toString();
        String tipo = venta.get("tipo_venta").toString();

        if (!estado.equals("completada")) {
            throw new IllegalArgumentException(
                    "Esta venta ya fue anulada."
            );
        }

        // Si fue una venta de producto, devuelve las existencias.
        if (tipo.equals("producto")) {

            List<Map<String, Object>> detalles =
                    jdbcTemplate.queryForList(
                            """
                            SELECT id_producto, cantidad
                            FROM detalle_venta_producto
                            WHERE id_venta = ?
                            FOR UPDATE
                            """,
                            idVenta
                    );

            if (detalles.isEmpty()) {
                throw new IllegalArgumentException(
                        "Esta venta no tiene detalle de productos. "
                                + "No se puede devolver el stock automáticamente."
                );
            }

            for (Map<String, Object> detalle : detalles) {

                int idProducto = ((Number)
                        detalle.get("id_producto")).intValue();

                int cantidad = ((Number)
                        detalle.get("cantidad")).intValue();

                int filas = jdbcTemplate.update(
                        """
                        UPDATE productos
                        SET stock = stock + ?
                        WHERE id_producto = ?
                        """,
                        cantidad,
                        idProducto
                );

                if (filas != 1) {
                    throw new IllegalArgumentException(
                            "No se encontró un producto de la venta "
                                    + "para devolver su stock."
                    );
                }
            }
        }

        int filasAnulacion = jdbcTemplate.update(
                """
                UPDATE ventas
                SET estado_venta = 'anulada',
                    motivo_anulacion = ?,
                    fecha_anulacion = NOW()
                WHERE id_venta = ?
                  AND estado_venta = 'completada'
                """,
                motivo.trim(),
                idVenta
        );

        if (filasAnulacion != 1) {
            throw new IllegalArgumentException(
                    "No se pudo anular la venta."
            );
        }

        return Map.of(
                "mensaje", "Venta anulada correctamente. "
                        + "El historial se ha conservado.",
                "id_venta", idVenta,
                "estado", "anulada"
        );
    }
}
