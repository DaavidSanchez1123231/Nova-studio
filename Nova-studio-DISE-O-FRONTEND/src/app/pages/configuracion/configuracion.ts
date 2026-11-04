import { Component, OnInit, ChangeDetectorRef } from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface EmpresaConfiguracion {
  nombre: string;
  telefono: string;
  correo: string;
  direccion: string;
  instagram: string;
  facebook: string;
  tiktokUrl: string;
whatsappUrl: string;
}

interface EmpresaRespuesta {
  nombre_salon?: string;
  telefono_whatsapp?: string;
  correo_contacto?: string;
  direccion?: string;
  instagram?: string;
  facebook?: string;
  tiktok_url?: string;
whatsapp_url?: string;
}

@Component({
  selector: 'app-configuracion',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.css',
})
export class ConfiguracionComponent implements OnInit {
  empresa: EmpresaConfiguracion = {
    nombre: '',
    telefono: '',
    correo: '',
    direccion: '',
    instagram: '',
    facebook: '',
    tiktokUrl: '',
    whatsappUrl: '',
  };

  usuario = '';
  nuevaClave = '';
  confirmarClave = '';

  mostrarError = false;
  mensajeServidor = '';

  constructor(
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.usuario = localStorage.getItem('usuario') || 'Usuario Desconocido';
    this.cargarDatosEmpresa();
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');

    let headers = new HttpHeaders({
      'Content-Type': 'application/json',
    });

    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }

    return headers;
  }

  // Cargar los datos guardados en PostgreSQL
  cargarDatosEmpresa(): void {
    this.http
      .get<EmpresaRespuesta>(`${environment.apiUrl}/configuracion/empresa`, {
        headers: this.getHeaders(),
      })
      .subscribe({
        next: (data) => {
          this.empresa = {
            nombre: data.nombre_salon || '',
            telefono: data.telefono_whatsapp || '',
            correo: data.correo_contacto || '',
            direccion: data.direccion || '',
            instagram: data.instagram || '',
            facebook: data.facebook || '',
            tiktokUrl: data.tiktok_url || '',
            whatsappUrl: data.whatsapp_url || '',
          };

          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Error al cargar la configuración:', err);
          this.mensajeServidor = 'No se pudo cargar la información del salón.';
          this.mostrarError = true;
        },
      });
  }

  // Actualizar los datos de NOVA
  guardarDatosEmpresa(event: Event): void {
    event.preventDefault();

    const nombre = this.empresa.nombre.trim();
    const telefono = this.empresa.telefono.trim();
    const correo = this.empresa.correo.trim();
    const direccion = this.empresa.direccion.trim();
    const instagram = this.empresa.instagram.trim();
    const facebook = this.empresa.facebook.trim();
    const tiktokUrl = this.empresa.tiktokUrl.trim();
    const whatsappUrl = this.empresa.whatsappUrl.trim();

    if (!nombre || !telefono || !correo || !direccion) {
      alert('Completa todos los campos obligatorios.');
      return;
    }

    const emailValido = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo);

    if (!emailValido) {
      alert('Ingresa un correo electrónico válido.');
      return;
    }

    // Los nombres coinciden con las columnas de PostgreSQL.
    const payload = {
      nombre_salon: nombre,
      telefono_whatsapp: telefono,
      correo_contacto: correo,
      direccion,
      instagram: this.empresa.instagram.trim(),
      facebook: this.empresa.facebook.trim(),
      tiktok_url: this.empresa.tiktokUrl.trim(),
      whatsapp_url: this.empresa.whatsappUrl.trim(),
    };

    this.http
      .put(`${environment.apiUrl}/configuracion/empresa`, payload, { headers: this.getHeaders() })
      .subscribe({
        next: () => {
          alert('¡La información de NOVA se actualizó correctamente!');
          this.cargarDatosEmpresa();
        },
        error: (err) => {
          console.error('Error al guardar la configuración:', err);
          alert(
            err?.error?.mensaje || 'No se pudieron guardar los cambios. Comprueba la conexión.',
          );
        },
      });
  }

  // Cambiar contraseña
  cambiarContrasena(event: Event): void {
    event.preventDefault();

    this.mostrarError = false;
    this.mensajeServidor = '';

    if (this.nuevaClave !== this.confirmarClave) {
      this.mostrarError = true;
      this.mensajeServidor = 'Las contraseñas no coinciden.';
      return;
    }

    if (this.nuevaClave.length < 6) {
      this.mostrarError = true;
      this.mensajeServidor = 'La contraseña debe tener al menos 6 caracteres.';
      return;
    }

    const payload = {
      usuario: this.usuario,
      nuevaContrasenia: this.nuevaClave,
    };

    this.http
      .put(`${environment.apiUrl}/usuarios/cambiar-password`, payload, {
        headers: this.getHeaders(),
      })
      .subscribe({
        next: () => {
          alert('Contraseña actualizada correctamente.');

          this.nuevaClave = '';
          this.confirmarClave = '';
          this.mostrarError = false;
          this.mensajeServidor = '';

          this.cdr.detectChanges();
        },
        error: (err) => {
          this.mostrarError = true;
          this.mensajeServidor =
            err?.error?.mensaje || err?.error?.message || 'No se pudo actualizar la contraseña.';

          this.cdr.detectChanges();
        },
      });
  }
}
