package pe.nova.backend.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Service
public class RecordatorioWhatsAppService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    @PostConstruct
    public void initTwilio() {
    
        Twilio.init(accountSid, authToken);
    }

  
    @Scheduled(cron = "0 0 8 * * ?")
    public void enviarRecordatoriosManana() {
        System.out.println("Iniciando agente de recordatorios de WhatsApp...");
        
        String sql = "SELECT * FROM sp_obtener_citas_manana()";
        List<Map<String, Object>> citasManana = jdbcTemplate.queryForList(sql);

        if (citasManana.isEmpty()) {
            System.out.println("No hay citas para mañana.");
            return;
        }

        for (Map<String, Object> cita : citasManana) {
            String nombre = (String) cita.get("nombre");
            String telefono = (String) cita.get("telefono");
            String hora = cita.get("hora_inicio").toString();
            String servicio = (String) cita.get("servicio");

            
            String telefonoDestino = "whatsapp:" + telefono;
            String telefonoOrigen = "whatsapp:" + twilioPhoneNumber;
            
            String mensajeTexto = String.format("¡Hola %s! Recuerda que mañana tienes una cita en NOVA Beauty Salon a las %s para tu servicio de %s. ¡Te esperamos!", nombre, hora, servicio);

            try {
                Message message = Message.creator(
                        new PhoneNumber(telefonoDestino),
                        new PhoneNumber(telefonoOrigen),
                        mensajeTexto
                ).create();
                
                System.out.println("✅ Mensaje enviado a " + nombre + " (ID: " + message.getSid() + ")");
            } catch (Exception e) {
                System.err.println("❌ Error al enviar mensaje a " + nombre + ": " + e.getMessage());
            }
        }
    }
}