import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface CitaCliente {
  id_cita: number;
  estado: string;
  id_horario: number;
  id_servicio: number;
  id_especialista: number;
  servicio: string;
  precio: number;
  especialista: string;
  fecha: string;       // YYYY-MM-DD
  hora_inicio: string; // HH:MM
  hora_fin: string;
  puede_modificar: boolean;
}

@Component({
  selector: 'app-mis-citas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './mis-citas.html',
  styleUrl: './mis-citas.css'
})
export class MisCitasComponent implements OnInit {
  usuarioLogeado: string | null = null;
  rolLogeado: string | null = null;

  citas: CitaCliente[] = [];
  cargando = true;

  // Mensaje dentro de la página (en lugar de alert)
  aviso = '';
  avisoTipo: 'ok' | 'error' = 'ok';

  // Reprogramación: solo una cita se edita a la vez
  citaReprogramando: CitaCliente | null = null;
  fechaNueva = '';
  horariosNuevos: any[] = [];
  idHorarioNuevo: number | null = null;
  cargandoHorarios = false;
  horariosCargados = false;
  procesando = false;

  hoy = new Date().toLocaleDateString('en-CA');

  constructor(private http: HttpClient, private router: Router, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    if (typeof window !== 'undefined' && window.localStorage) {
      this.usuarioLogeado = localStorage.getItem('usuario');
      this.rolLogeado = localStorage.getItem('rol');
      if (!localStorage.getItem('token')) {
        this.router.navigate(['/login']);
        return;
      }
    }
    this.cargarCitas();
  }

  private headers(): HttpHeaders {
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    });
  }

  private mostrar(tipo: 'ok' | 'error', texto: string): void {
    this.avisoTipo = tipo;
    this.aviso = texto;
    this.cdr.detectChanges();
  }

  private sesionVencida(err: any): boolean {
    if (err.status === 401) {
      localStorage.clear();
      this.router.navigate(['/login']);
      return true;
    }
    return false;
  }

  cargarCitas(): void {
    this.cargando = true;
    this.http.get<CitaCliente[]>(`${environment.apiUrl}/citas/cliente/mis-citas`, { headers: this.headers() })
      .subscribe({
        next: (data) => { this.citas = data; this.cargando = false; this.cdr.detectChanges(); },
        error: (err) => {
          this.cargando = false;
          if (!this.sesionVencida(err)) {
            this.mostrar('error', 'No se pudieron cargar tus citas. Intenta de nuevo.');
          }
        }
      });
  }

  etiquetaEstado(estado: string): string {
    const mapa: Record<string, string> = {
      confirmada: 'Confirmada',
      pendiente_confirmacion: 'Pendiente',
      completada: 'Completada',
      cancelada: 'Cancelada',
      expirada: 'Expirada'
    };
    return mapa[estado] ?? estado;
  }

  // ---------- Cancelar ----------
  cancelar(cita: CitaCliente): void {
    if (this.procesando) { return; }
    if (!confirm(`¿Cancelar tu cita de ${cita.servicio} el ${cita.fecha} a las ${cita.hora_inicio}?`)) { return; }

    this.procesando = true;
    this.aviso = '';
    this.http.put<{ mensaje: string }>(
      `${environment.apiUrl}/citas/cliente/${cita.id_cita}/cancelar`, {}, { headers: this.headers() }
    ).subscribe({
      next: (r) => {
        this.procesando = false;
        this.mostrar('ok', r.mensaje);
        this.cargarCitas();
      },
      error: (err) => {
        this.procesando = false;
        if (!this.sesionVencida(err)) {
          this.mostrar('error', err.error?.mensaje ?? 'No se pudo cancelar la cita.');
          this.cargarCitas();
        }
      }
    });
  }

  // ---------- Reprogramar ----------
  abrirReprogramar(cita: CitaCliente): void {
    this.citaReprogramando = cita;
    this.fechaNueva = '';
    this.horariosNuevos = [];
    this.idHorarioNuevo = null;
    this.horariosCargados = false;
    this.aviso = '';
  }

  cerrarReprogramar(): void {
    this.citaReprogramando = null;
  }

  /** Mismo especialista de la cita: se piden sus horas libres de la fecha elegida. */
  cargarHorariosNuevos(): void {
    this.idHorarioNuevo = null;
    this.horariosNuevos = [];
    this.horariosCargados = false;
    if (!this.citaReprogramando || !this.fechaNueva) { return; }

    this.cargandoHorarios = true;
    this.http.get<any[]>(`${environment.apiUrl}/reservas/disponibles`, {
      headers: this.headers(),
      params: { idEspecialista: this.citaReprogramando.id_especialista, fecha: this.fechaNueva }
    }).subscribe({
      next: (data) => {
        // No ofrecer el turno actual
        this.horariosNuevos = data.filter(h => h.id_horario !== this.citaReprogramando?.id_horario);
        this.horariosCargados = true;
        this.cargandoHorarios = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.cargandoHorarios = false;
        if (!this.sesionVencida(err)) {
          this.mostrar('error', 'No se pudieron cargar los horarios.');
        }
      }
    });
  }

  confirmarReprogramacion(): void {
    if (!this.citaReprogramando || !this.idHorarioNuevo || this.procesando) { return; }

    this.procesando = true;
    this.http.put<{ mensaje: string }>(
      `${environment.apiUrl}/citas/cliente/${this.citaReprogramando.id_cita}/reprogramar`,
      { idHorario: this.idHorarioNuevo },
      { headers: this.headers() }
    ).subscribe({
      next: (r) => {
        this.procesando = false;
        this.citaReprogramando = null;
        this.mostrar('ok', r.mensaje);
        this.cargarCitas();
      },
      error: (err) => {
        this.procesando = false;
        if (!this.sesionVencida(err)) {
          this.mostrar('error', err.error?.mensaje ?? 'No se pudo reprogramar la cita.');
          if (err.status === 409) { this.cargarHorariosNuevos(); }
        }
      }
    });
  }

  cerrarSesion(): void {
    localStorage.clear();
    this.router.navigate(['/login']);
  }
}
