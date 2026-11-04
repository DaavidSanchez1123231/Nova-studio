import {
  Component,
  OnInit,
  AfterViewInit,
  ChangeDetectorRef
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

declare var particlesJS: any;

interface ConfiguracionPublicaRespuesta {
  nombre_salon?: string;
  telefono_whatsapp?: string;
  correo_contacto?: string;
  direccion?: string;
  instagram?: string;
  facebook?: string;
  tiktok_url?: string;
  whatsapp_url?: string;
}

interface ConfiguracionPublica {
  nombre: string;
  telefono: string;
  correo: string;
  direccion: string;
  instagram: string;
  facebook: string;
  whatsapp: string;
  tiktok: string;
}

@Component({
  selector: 'app-contacto',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './contacto.html',
  styleUrl: './contacto.css'
})
export class ContactoComponent implements OnInit, AfterViewInit {

  usuarioLogeado: string | null = null;
  rolLogeado: string | null = null;

  enviandoMensaje = false;

  mensajeContacto = {
    nombre: '',
    correo: '',
    telefono: '',
    mensaje: '',
    aceptaPrivacidad: false
  };

  config: ConfiguracionPublica = {
    nombre: 'NOVA Beauty Salon',
    telefono: 'No disponible',
    correo: 'No disponible',
    direccion: 'No disponible',
    instagram: '#',
    facebook: '#',
    whatsapp: '#',
    tiktok: '#'
  };

  constructor(
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    if (typeof window !== 'undefined') {
      this.usuarioLogeado = localStorage.getItem('usuario');
      this.rolLogeado = localStorage.getItem('rol');
    }

    this.cargarDatosEmpresa();
  }

  ngAfterViewInit(): void {
    if (typeof particlesJS !== 'undefined') {
      particlesJS('particles-js', {
        particles: {
          number: { value: 250 },
          size: { value: 2 },
          color: { value: '#10efff' },
          line_linked: { opacity: 0.1 }
        }
      });
    }
  }

  // Convertir el teléfono internacional en enlace de WhatsApp
  private crearEnlaceWhatsapp(telefono: string): string {
    const numero = telefono.replace(/\D/g, '');

    return numero
      ? `https://wa.me/${numero}`
      : '#';
  }

  // Consultar información pública de NOVA
  cargarDatosEmpresa(): void {
    this.http.get<ConfiguracionPublicaRespuesta>(
      `${environment.apiUrl}/publico/configuracion`
    ).subscribe({
      next: (data) => {
        const telefono = data.telefono_whatsapp || '';

        this.config = {
  nombre: data.nombre_salon || 'NOVA Beauty Salon',
  telefono: telefono || 'No disponible',
  correo: data.correo_contacto || 'No disponible',
  direccion: data.direccion || 'No disponible',
  instagram: data.instagram || '#',
  facebook: data.facebook || '#',

  // Recuperar enlaces guardados en PostgreSQL
  whatsapp: data.whatsapp_url || this.crearEnlaceWhatsapp(telefono),
  tiktok: data.tiktok_url || '#'
};

        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error(
          'No se pudo cargar la información de NOVA:',
          err
        );
      }
    });
  }

  // Cerrar sesión
  cerrarSesion(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    localStorage.removeItem('rol');

    this.usuarioLogeado = null;
    this.rolLogeado = null;

    this.router.navigate(['/login']);
  }

  // Enviar consulta desde el formulario de contacto
  enviarMensaje(event: Event): void {
    event.preventDefault();

    if (this.enviandoMensaje) {
      return;
    }

    if (!this.mensajeContacto.aceptaPrivacidad) {
      alert('Debes aceptar la Política de Privacidad.');
      return;
    }

    const payload = {
      nombre: this.mensajeContacto.nombre.trim(),
      correo: this.mensajeContacto.correo.trim(),
      telefono: this.mensajeContacto.telefono.trim(),
      mensaje: this.mensajeContacto.mensaje.trim(),
      aceptaPrivacidad: this.mensajeContacto.aceptaPrivacidad
    };

    if (!payload.nombre || !payload.correo || !payload.mensaje) {
      alert('Completa todos los campos obligatorios.');
      return;
    }

    this.enviandoMensaje = true;

    this.http.post<{ mensaje?: string }>(
      `${environment.apiUrl}/publico/mensajes`,
      payload
    ).subscribe({
      next: (respuesta) => {
        alert(
          respuesta.mensaje ||
          '¡Mensaje enviado correctamente!'
        );

        this.mensajeContacto = {
          nombre: '',
          correo: '',
          telefono: '',
          mensaje: '',
          aceptaPrivacidad: false
        };

        this.enviandoMensaje = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error enviando mensaje:', err);

        const mensaje =
          err?.error?.mensaje ||
          'No fue posible enviar tu mensaje. Intenta nuevamente.';

        alert(mensaje);

        this.enviandoMensaje = false;
        this.cdr.detectChanges();
      }
    });
  }
}