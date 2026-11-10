import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface EmpleadoForm {
  nombre: string;
  especialidad: string;
  telefono: string;
}

interface Empleado {
  id_especialista: number;
  nombre: string;
  especialidad: string;
  telefono?: string | null;
  estado: boolean | number | string;
}

interface Horario {
  id_horario: number;
  fecha: string;
  hora_inicio: string;
  hora_fin: string;
  estado: string;
}

@Component({
  selector: 'app-empleados',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './empleados.html',
  styleUrl: './empleados.css'
})
export class EmpleadosComponent implements OnInit {
  private readonly apiUrl = environment.apiUrl;

  isModalOpen = false;
  modoEdicion = false;
  guardandoEmpleado = false;
  cargando = true;
  errorEmpleados = '';
  errorFormulario = '';
  idEmpleadoEditando: number | null = null;

  empleados: Empleado[] = [];
  empleadosFiltrados: Empleado[] = [];
  textoBusqueda = '';

  nuevoEmpleado: EmpleadoForm = this.formularioVacio();

  isModalHorariosOpen = false;
  cargandoHorarios = false;
  guardandoHorario = false;
  errorHorarios = '';
  especialistaSeleccionado: Empleado | null = null;
  horariosActuales: Horario[] = [];
  nuevoHorario = { fecha: '', hora_inicio: '', hora_fin: '' };

 constructor(
  private http: HttpClient,
  private cdr: ChangeDetectorRef
) {}

  ngOnInit(): void {
    this.obtenerEmpleados();
  }

  private formularioVacio(): EmpleadoForm {
    return { nombre: '', especialidad: 'Cabello', telefono: '' };
  }

  private getHeaders(): HttpHeaders {
    let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    const token = localStorage.getItem('token');
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  }

  private mensajeError(error: any, predeterminado: string): string {
    return error?.error?.mensaje || error?.error?.message || error?.message || predeterminado;
  }

  formatearFecha(fecha: string): string {
    const partes = String(fecha ?? '').slice(0, 10).split('-');
    return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : String(fecha ?? '');
  }

  esActivo(empleado: Empleado): boolean {
    const estado = String(empleado?.estado ?? '').toLowerCase();
    return empleado?.estado === true || estado === 'true' || estado === 't' ||
      estado === '1' || estado === 'activo';
  }

  obtenerEmpleados(): void {
  this.cargando = true;
  this.errorEmpleados = '';

  this.http.get<Empleado[]>(
    `${this.apiUrl}/empleados`,
    { headers: this.getHeaders() }
  ).subscribe({
    next: (data) => {
      this.empleados = Array.isArray(data) ? data : [];
      this.filtrarEmpleados();
      this.cargando = false;

      // Actualizar inmediatamente la tabla de empleados.
      this.cdr.detectChanges();
    },
    error: (error) => {
      console.error('Error al cargar colaboradores:', error);

      this.errorEmpleados = this.mensajeError(
        error,
        'No se pudo cargar el equipo. Verifica la conexión e inicia sesión nuevamente.'
      );

      this.empleados = [];
      this.empleadosFiltrados = [];
      this.cargando = false;

      // Actualizar también el mensaje de error.
      this.cdr.detectChanges();
    }
  });
}

  filtrarEmpleados(): void {
    const busqueda = this.textoBusqueda.toLowerCase().trim();
    this.empleadosFiltrados = !busqueda
      ? [...this.empleados]
      : this.empleados.filter((empleado) =>
          `${empleado.nombre ?? ''} ${empleado.especialidad ?? ''} ${empleado.telefono ?? ''}`
            .toLowerCase().includes(busqueda)
        );
  }

  abrirModal(): void {
    this.modoEdicion = false;
    this.idEmpleadoEditando = null;
    this.nuevoEmpleado = this.formularioVacio();
    this.errorFormulario = '';
    this.isModalOpen = true;
  }

  editarEmpleado(empleado: Empleado): void {
    this.modoEdicion = true;
    this.idEmpleadoEditando = empleado.id_especialista;
    this.nuevoEmpleado = {
      nombre: empleado.nombre ?? '',
      especialidad: empleado.especialidad ?? 'Cabello',
      telefono: empleado.telefono ?? ''
    };
    this.errorFormulario = '';
    this.isModalOpen = true;
  }

  cerrarModal(): void {
    if (this.guardandoEmpleado) return;
    this.isModalOpen = false;
    this.errorFormulario = '';
  }

  guardarEmpleado(event: Event): void {
    event.preventDefault();
    if (this.guardandoEmpleado) return;

    const payload: EmpleadoForm = {
      nombre: this.nuevoEmpleado.nombre.trim(),
      especialidad: this.nuevoEmpleado.especialidad.trim(),
      telefono: this.nuevoEmpleado.telefono.trim()
    };

    if (!payload.nombre) {
      this.errorFormulario = 'Ingresa el nombre completo del colaborador.';
      return;
    }
    if (!payload.especialidad) {
      this.errorFormulario = 'Selecciona una especialidad.';
      return;
    }
    if (!/^[0-9]{9}$/.test(payload.telefono)) {
      this.errorFormulario = 'El teléfono debe contener exactamente 9 dígitos.';
      return;
    }

    this.errorFormulario = '';
    this.guardandoEmpleado = true;
    const solicitud = this.modoEdicion && this.idEmpleadoEditando !== null
      ? this.http.put(`${this.apiUrl}/empleados/${this.idEmpleadoEditando}`, payload, { headers: this.getHeaders() })
      : this.http.post(`${this.apiUrl}/empleados`, payload, { headers: this.getHeaders() });

    solicitud.subscribe({
      next: () => {
        this.guardandoEmpleado = false;
        this.isModalOpen = false;
        alert(this.modoEdicion ? 'Colaborador actualizado correctamente.' : 'Colaborador registrado correctamente.');
        this.obtenerEmpleados();
      },
      error: (error) => {
        console.error('Error al guardar colaborador:', error);
        this.errorFormulario = this.mensajeError(error, 'No se pudo guardar el colaborador.');
        this.guardandoEmpleado = false;
      }
    });
  }

  cambiarEstadoEmpleado(empleado: Empleado): void {
    const nuevoEstado = !this.esActivo(empleado);
    const accion = nuevoEstado ? 'reactivar' : 'dar de baja';
    if (!confirm(`¿Deseas ${accion} a ${empleado.nombre}?`)) return;

    this.http.put(`${this.apiUrl}/empleados/${empleado.id_especialista}/estado`,
      { estado: nuevoEstado }, { headers: this.getHeaders() }).subscribe({
      next: () => this.obtenerEmpleados(),
      error: (error) => {
        console.error('Error al cambiar estado:', error);
        alert(this.mensajeError(error, 'No se pudo cambiar el estado del colaborador.'));
      }
    });
  }

  // Mantiene el método anterior por compatibilidad con la plantilla si aún lo referencia.
  eliminarEmpleado(id_especialista: number, nombre: string): void {
    const empleado = this.empleados.find((item) => item.id_especialista === id_especialista);
    if (empleado) {
      this.cambiarEstadoEmpleado(empleado);
      return;
    }
    if (!confirm(`¿Dar de baja a ${nombre}?`)) return;
    this.http.delete(`${this.apiUrl}/empleados/${id_especialista}`, { headers: this.getHeaders() }).subscribe({
      next: () => this.obtenerEmpleados(),
      error: (error) => alert(this.mensajeError(error, 'No se pudo dar de baja al colaborador.'))
    });
  }

  abrirModalHorarios(empleado: Empleado): void {
    if (!this.esActivo(empleado)) {
      alert('Reactiva al colaborador antes de gestionar nuevos horarios.');
      return;
    }
    this.especialistaSeleccionado = empleado;
    this.isModalHorariosOpen = true;
    this.nuevoHorario = { fecha: '', hora_inicio: '', hora_fin: '' };
    this.errorHorarios = '';
    this.obtenerHorarios(empleado.id_especialista);
  }

  cerrarModalHorarios(): void {
    if (this.guardandoHorario) return;
    this.isModalHorariosOpen = false;
    this.especialistaSeleccionado = null;
    this.horariosActuales = [];
    this.errorHorarios = '';
  }

  obtenerHorarios(id_especialista: number): void {
    this.cargandoHorarios = true;
    this.errorHorarios = '';
    this.http.get<Horario[]>(`${this.apiUrl}/horarios/especialista/${id_especialista}`, { headers: this.getHeaders() }).subscribe({
      next: (data) => {
        this.horariosActuales = (Array.isArray(data) ? data : []).map((horario) => ({
          ...horario,
          fecha: String(horario.fecha ?? '').slice(0, 10),
          hora_inicio: String(horario.hora_inicio ?? '').slice(0, 5),
          hora_fin: String(horario.hora_fin ?? '').slice(0, 5),
          estado: String(horario.estado ?? 'DISPONIBLE').toUpperCase()
        }));
        this.cargandoHorarios = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Error al cargar horarios:', error);
        this.errorHorarios = this.mensajeError(error, 'No se pudieron cargar los horarios.');
        this.horariosActuales = [];
        this.cargandoHorarios = false;
        this.cdr.detectChanges();
      }
    });
  }

  guardarHorario(event: Event): void {
    event.preventDefault();
    if (!this.especialistaSeleccionado || this.guardandoHorario) return;

    const { fecha, hora_inicio, hora_fin } = this.nuevoHorario;
    if (!fecha || !hora_inicio || !hora_fin) {
      this.errorHorarios = 'Completa la fecha, la hora de inicio y la hora de fin.';
      return;
    }
    if (hora_fin <= hora_inicio) {
      this.errorHorarios = 'La hora de fin debe ser posterior a la hora de inicio.';
      return;
    }

    const hoy = new Date();
    const fechaHoy = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
    if (fecha < fechaHoy || (fecha === fechaHoy && hora_inicio <= `${String(hoy.getHours()).padStart(2, '0')}:${String(hoy.getMinutes()).padStart(2, '0')}`)) {
      this.errorHorarios = 'El turno debe programarse para una fecha y hora futuras.';
      return;
    }

    this.guardandoHorario = true;
    this.errorHorarios = '';
    const payload = {
      id_especialista: this.especialistaSeleccionado.id_especialista,
      fecha,
      hora_inicio,
      hora_fin
    };

    this.http.post(`${this.apiUrl}/horarios`, payload, { headers: this.getHeaders() }).subscribe({
      next: () => {
        this.nuevoHorario = { fecha: '', hora_inicio: '', hora_fin: '' };
        this.guardandoHorario = false;
        this.cdr.detectChanges();
        this.obtenerHorarios(payload.id_especialista);
      },
      error: (error) => {
        console.error('Error al asignar horario:', error);
        this.errorHorarios = this.mensajeError(error, 'No se pudo asignar el turno.');
        this.guardandoHorario = false;
        this.cdr.detectChanges();
      }
    });
  }

  eliminarHorario(id_horario: number): void {
    if (!confirm('¿Cancelar este turno disponible?')) return;
    this.errorHorarios = '';
    this.http.delete(`${this.apiUrl}/horarios/${id_horario}`, { headers: this.getHeaders() }).subscribe({
      next: () => {
        if (this.especialistaSeleccionado) this.obtenerHorarios(this.especialistaSeleccionado.id_especialista);
      },
      error: (error) => {
        console.error('Error al cancelar horario:', error);
        this.errorHorarios = this.mensajeError(error, 'No se pudo cancelar el turno.');
        this.cdr.detectChanges();
      }
    });
  }
}
