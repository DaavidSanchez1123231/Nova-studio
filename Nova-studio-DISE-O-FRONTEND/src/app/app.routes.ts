import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home';
import { LoginComponent } from './pages/login/login';
import { VentasComponent } from './pages/ventas/ventas';
import { DashboardComponent } from './pages/dashboard/dashboard'; 
import { CitasComponent } from './pages/citas/citas';
import { MisCitasComponent } from './pages/mis-citas/mis-citas';
import { ClientesComponent } from './pages/clientes/clientes';
import { ProductosComponent } from './pages/productos/productos';
import { ServiciosComponent } from './pages/servicios/servicios';
import { EmpleadosComponent } from './pages/empleados/empleados';
import { PromocionesComponent } from './pages/promociones/promociones';
import { ReportesComponent } from './pages/reportes/reportes';
import { GaleriaComponent } from './pages/galeria/galeria'; 
import { ConfiguracionComponent } from './pages/configuracion/configuracion';
import { RecuperarComponent } from './pages/recuperar/recuperar'; 
import { RegistroComponent } from './pages/registro/registro';

// IMPORTAMOS TUS NUEVAS PANTALLAS PÚBLICAS
import { ReservasComponent } from './pages/reservas/reservas';
import { ServiciosPublicoComponent } from './pages/servicios-publico/servicios-publico';
import { PromocionesPublicoComponent } from './pages/promociones-publico/promociones-publico';
import { NosotrosComponent } from './pages/nosotros/nosotros';
import { ContactoComponent } from './pages/contacto/contacto';

// 1. IMPORTAMOS EL GUARDIÁN
import { LegalComponent } from './pages/legal/legal';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  // ==========================================
  // Rutas de la web pública (SIN PROTECCIÓN)
  // ==========================================
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent }, 
  { path: 'recuperar', component: RecuperarComponent },
  { path: 'crear-cuenta', component: RegistroComponent },
  { path: 'reservas', component: ReservasComponent }, 
  { path: 'mis-citas', component: MisCitasComponent, canActivate: [authGuard] },
  { path: 'nuestros-servicios', component: ServiciosPublicoComponent },
  { path: 'promociones-publico', component: PromocionesPublicoComponent },
  { path: 'nosotros', component: NosotrosComponent },
  { path: 'contacto', component: ContactoComponent },
  { path: 'politica-privacidad', component: LegalComponent, data: { tipo: 'privacidad' } },
  { path: 'terminos-condiciones', component: LegalComponent, data: { tipo: 'terminos' } },
  { path: 'politica-cookies', component: LegalComponent, data: { tipo: 'cookies' } },
  { path: 'politica-reembolsos', component: LegalComponent, data: { tipo: 'reembolsos' } },

  // ==========================================
  // Rutas del Panel Administrativo (PROTEGIDAS CON canActivate)
  // ==========================================
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'ventas', component: VentasComponent, canActivate: [authGuard] },
  { path: 'citas', component: CitasComponent, canActivate: [authGuard] },
  { path: 'clientes', component: ClientesComponent, canActivate: [authGuard] },
  { path: 'productos', component: ProductosComponent, canActivate: [authGuard] },
  { path: 'servicios', component: ServiciosComponent, canActivate: [authGuard] },
  { path: 'empleados', component: EmpleadosComponent, canActivate: [authGuard] },
  { path: 'promociones', component: PromocionesComponent, canActivate: [authGuard] },
  { path: 'reportes', component: ReportesComponent, canActivate: [authGuard] },
  { path: 'galeria', component: GaleriaComponent, canActivate: [authGuard] },
  { path: 'configuracion', component: ConfiguracionComponent, canActivate: [authGuard] },

  // Ruta comodín para redirigir URLs que no existen
  { path: '**', redirectTo: '' }
];