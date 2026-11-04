package pe.nova.backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReporteService {

    private static final String SQL_SERVICIOS = "SELECT s.nombre AS nombre, "
            + "ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 0)::int AS porcentaje "
            + "FROM citas c JOIN servicios s ON c.id_servicio = s.id_servicio "
            + "JOIN horarios h ON c.id_horario = h.id_horario "
            + "WHERE LOWER(c.estado) IN "
            + "('pendiente_confirmacion', 'confirmada', 'completada') "
            + "AND h.fecha BETWEEN ? AND ? "
            + "GROUP BY s.nombre ORDER BY COUNT(*) DESC LIMIT 5";

    // Ingreso estimado por especialista: precio de los servicios de sus citas
    // confirmadas
    private static final String SQL_EMPLEADOS = "SELECT e.nombre AS nombre, "
            + "e.especialidad AS puesto, "
            + "COALESCE(SUM(s.precio), 0) AS monto "
            + "FROM citas c "
            + "JOIN especialistas e ON c.id_especialista = e.id_especialista "
            + "JOIN servicios s ON c.id_servicio = s.id_servicio "
            + "JOIN horarios h ON c.id_horario = h.id_horario "
            + "WHERE LOWER(c.estado) = 'completada' "
            + "AND h.fecha BETWEEN ? AND ? "
            + "GROUP BY e.id_especialista, e.nombre, e.especialidad "
            + "ORDER BY monto DESC LIMIT 5";

    private final JdbcTemplate jdbc;
    private final BigDecimal porcentajeCostos;
    private final String salon;

    public ReporteService(JdbcTemplate jdbc,
            @Value("${app.negocio.porcentaje-costos:0.30}") BigDecimal porcentajeCostos,
            @Value("${app.nombre-salon:NOVA Beauty Salon}") String salon) {
        this.jdbc = jdbc;
        this.porcentajeCostos = porcentajeCostos;
        this.salon = salon;
    }

    public Map<String, Object> dashboard(LocalDate desde, LocalDate hasta) {
        Timestamp ini = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp finExclusivo = Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());

        BigDecimal bruto = jdbc.queryForObject(
                "SELECT COALESCE(SUM(monto_total), 0) "
                        + "FROM ventas "
                        + "WHERE fecha_venta >= ? AND fecha_venta < ? "
                        + "AND LOWER(estado_venta) = 'completada'",
                BigDecimal.class, ini, finExclusivo);
        if (bruto == null)
            bruto = BigDecimal.ZERO;
        // No hay tabla de costos: los insumos y comisiones se estiman con un porcentaje
        // configurable
        BigDecimal costos = bruto.multiply(porcentajeCostos).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> balance = new HashMap<>();
        balance.put("ingresosBrutos", bruto);
        balance.put("insumosComisiones", costos);
        balance.put("utilidadNeta", bruto.subtract(costos));

        List<Map<String, Object>> servicios = jdbc.queryForList(SQL_SERVICIOS, Date.valueOf(desde),
                Date.valueOf(hasta));
        List<Map<String, Object>> empleados = jdbc.queryForList(SQL_EMPLEADOS, Date.valueOf(desde),
                Date.valueOf(hasta));

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("balance", balance);
        respuesta.put("serviciosTop", servicios);
        respuesta.put("empleadosTop", empleados);
        respuesta.put("desde", desde.toString());
        respuesta.put("hasta", hasta.toString());
        return respuesta;
    }

    @SuppressWarnings("unchecked")
    public byte[] generarPdf(LocalDate desde, LocalDate hasta) {
        Map<String, Object> d = dashboard(desde, hasta);
        Map<String, Object> b = (Map<String, Object>) d.get("balance");
        List<Map<String, Object>> servicios = (List<Map<String, Object>>) d.get("serviciosTop");
        List<Map<String, Object>> empleados = (List<Map<String, Object>>) d.get("empleadosTop");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font sub = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 11);

            doc.add(new Paragraph(salon + " - Reporte de balance", titulo));
            doc.add(new Paragraph("Periodo: " + desde + " al " + hasta, normal));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Balance", sub));
            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            fila(t, "Ingresos brutos", dinero(b.get("ingresosBrutos")), normal);
            fila(t, "Insumos y comisiones (estimado)", dinero(b.get("insumosComisiones")), normal);
            fila(t, "Utilidad estimada", dinero(b.get("utilidadNeta")), normal);
            doc.add(t);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Distribución de citas por servicio", sub));
            PdfPTable ts = new PdfPTable(2);
            ts.setWidthPercentage(100);
            for (Map<String, Object> s : servicios)
                fila(ts, String.valueOf(s.get("nombre")), s.get("porcentaje") + " %", normal);
            if (servicios.isEmpty())
                fila(ts, "Sin citas no canceladas en el periodo", "-", normal);
            doc.add(ts);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Valor de servicios completados por especialista", sub));
            PdfPTable te = new PdfPTable(2);
            te.setWidthPercentage(100);
            for (Map<String, Object> e : empleados)
                fila(te, e.get("nombre") + " (" + e.get("puesto") + ")", dinero(e.get("monto")), normal);
            if (empleados.isEmpty())
                fila(te, "Sin citas completadas en el periodo", "-", normal);
            doc.add(te);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            if (doc.isOpen())
                doc.close();
        }
        return out.toByteArray();
    }

    private static void fila(PdfPTable t, String a, String b, Font f) {
        t.addCell(new Phrase(a, f));
        t.addCell(new Phrase(b, f));
    }

    private static String dinero(Object n) {
        BigDecimal v = n instanceof BigDecimal ? (BigDecimal) n : new BigDecimal(String.valueOf(n));
        return String.format(Locale.US, "S/ %,.2f", v);
    }
}
