--
-- PostgreSQL database dump
--

\restrict 1WRMSFZLbyrW9b9Hs0GbgiRT9JlO0csZoZCMnsl9mLkfLiHihfDQKzbU8qKVUia

-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

-- Started on 2026-11-09 18:36:09

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 249 (class 1255 OID 16564)
-- Name: sp_actualizar_estado_cita(integer, character varying); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_actualizar_estado_cita(p_id_cita integer, p_estado character varying) RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
BEGIN
  UPDATE citas SET estado = p_estado WHERE id_cita = p_id_cita;
  IF p_estado = 'cancelada' THEN  -- liberar el horario
    UPDATE horarios SET estado = 'DISPONIBLE', fecha_bloqueo = NULL
     WHERE id_horario = (SELECT c.id_horario FROM citas c WHERE c.id_cita = p_id_cita);
  END IF;
  RETURN QUERY SELECT 'Estado actualizado correctamente.'::TEXT;
END $$;


ALTER FUNCTION public.sp_actualizar_estado_cita(p_id_cita integer, p_estado character varying) OWNER TO postgres;

--
-- TOC entry 250 (class 1255 OID 16565)
-- Name: sp_admin_agregar_horario(integer, date, time without time zone, time without time zone); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_admin_agregar_horario(p_id_especialista integer, p_fecha date, p_hora_inicio time without time zone, p_hora_fin time without time zone) RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM horarios WHERE id_especialista = p_id_especialista AND fecha = p_fecha AND hora_inicio = p_hora_inicio) THEN
    RETURN QUERY SELECT 'Este horario ya existe para el especialista.'::TEXT; RETURN;
  END IF;
  INSERT INTO horarios (id_especialista, fecha, hora_inicio, hora_fin, estado)
  VALUES (p_id_especialista, p_fecha, p_hora_inicio, p_hora_fin, 'DISPONIBLE');
  RETURN QUERY SELECT 'Horario agregado correctamente.'::TEXT;
END $$;


ALTER FUNCTION public.sp_admin_agregar_horario(p_id_especialista integer, p_fecha date, p_hora_inicio time without time zone, p_hora_fin time without time zone) OWNER TO postgres;

--
-- TOC entry 248 (class 1255 OID 16563)
-- Name: sp_confirmar_cita(integer, integer); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_confirmar_cita(p_id_horario integer, p_id_usuario integer) RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM horarios WHERE id_horario = p_id_horario AND estado = 'EN_PROCESO') THEN
    UPDATE horarios SET estado = 'RESERVADO', fecha_bloqueo = NULL WHERE id_horario = p_id_horario;
    UPDATE citas SET estado = 'confirmada'
     WHERE id_horario = p_id_horario AND id_usuario = p_id_usuario AND estado = 'pendiente_confirmacion';
    RETURN QUERY SELECT 'Cita confirmada exitosamente.'::TEXT;
  ELSE
    RETURN QUERY SELECT 'El tiempo de reserva expiró o el horario no es válido.'::TEXT;
  END IF;
EXCEPTION WHEN OTHERS THEN
  RETURN QUERY SELECT 'Error al confirmar la cita.'::TEXT;
END $$;


ALTER FUNCTION public.sp_confirmar_cita(p_id_horario integer, p_id_usuario integer) OWNER TO postgres;

--
-- TOC entry 266 (class 1255 OID 16571)
-- Name: sp_crear_producto(character varying, character varying, double precision, integer, character varying); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_crear_producto(p_nombre character varying, p_categoria character varying, p_precio double precision, p_stock integer, p_descripcion character varying) RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
BEGIN
  INSERT INTO productos (nombre, categoria, precio, stock, descripcion)
  VALUES (p_nombre, p_categoria, p_precio::NUMERIC, p_stock, p_descripcion);
  RETURN QUERY SELECT 'Producto registrado en el inventario.'::TEXT;
END $$;


ALTER FUNCTION public.sp_crear_producto(p_nombre character varying, p_categoria character varying, p_precio double precision, p_stock integer, p_descripcion character varying) OWNER TO postgres;

--
-- TOC entry 268 (class 1255 OID 16572)
-- Name: sp_crear_promocion(character varying, integer, character varying, date, character varying); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_crear_promocion(p_nombre character varying, p_descuento integer, p_codigo character varying, p_fecha_expiracion date, p_descripcion character varying) RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
BEGIN
  INSERT INTO promociones (nombre, descuento, codigo, fecha_expiracion, descripcion)
  VALUES (p_nombre, p_descuento, p_codigo, p_fecha_expiracion, p_descripcion);
  RETURN QUERY SELECT 'Campaña promocional activada.'::TEXT;
END $$;


ALTER FUNCTION public.sp_crear_promocion(p_nombre character varying, p_descuento integer, p_codigo character varying, p_fecha_expiracion date, p_descripcion character varying) OWNER TO postgres;

--
-- TOC entry 246 (class 1255 OID 16561)
-- Name: sp_horarios_disponibles(integer, date); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_horarios_disponibles(p_id_especialista integer, p_fecha date) RETURNS TABLE(id_horario integer, hora_inicio time without time zone, hora_fin time without time zone)
    LANGUAGE sql STABLE
    AS $$
  SELECT h.id_horario, h.hora_inicio, h.hora_fin
  FROM horarios h
  WHERE h.id_especialista = p_id_especialista AND h.fecha = p_fecha AND h.estado = 'DISPONIBLE'
  ORDER BY h.hora_inicio;
$$;


ALTER FUNCTION public.sp_horarios_disponibles(p_id_especialista integer, p_fecha date) OWNER TO postgres;

--
-- TOC entry 251 (class 1255 OID 16566)
-- Name: sp_liberar_reservas_expiradas(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_liberar_reservas_expiradas() RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
BEGIN
  -- Reservas sin confirmar tras 10 minutos: la cita expira y el horario se libera
  UPDATE citas c SET estado = 'expirada'
    FROM horarios h
   WHERE c.id_horario = h.id_horario AND h.estado = 'EN_PROCESO'
     AND h.fecha_bloqueo <= NOW() - INTERVAL '10 minutes' AND c.estado = 'pendiente_confirmacion';
  UPDATE horarios SET estado = 'DISPONIBLE', fecha_bloqueo = NULL
   WHERE estado = 'EN_PROCESO' AND fecha_bloqueo <= NOW() - INTERVAL '10 minutes';
  RETURN QUERY SELECT 'Limpieza de horarios expirados completada.'::TEXT;
END $$;


ALTER FUNCTION public.sp_liberar_reservas_expiradas() OWNER TO postgres;

--
-- TOC entry 252 (class 1255 OID 16567)
-- Name: sp_obtener_citas_manana(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_obtener_citas_manana() RETURNS TABLE(nombre character varying, telefono character varying, hora_inicio time without time zone, servicio character varying)
    LANGUAGE sql STABLE
    AS $$
  SELECT u.nombre, u.telefono, h.hora_inicio, s.nombre
  FROM citas c
  JOIN usuarios u  ON c.id_usuario  = u.id_usuario
  JOIN horarios h  ON c.id_horario  = h.id_horario
  JOIN servicios s ON c.id_servicio = s.id_servicio
  WHERE c.estado = 'confirmada' AND h.fecha = CURRENT_DATE + 1 AND u.telefono IS NOT NULL;
$$;


ALTER FUNCTION public.sp_obtener_citas_manana() OWNER TO postgres;

--
-- TOC entry 254 (class 1255 OID 16569)
-- Name: sp_registrar_usuario(character varying, character varying, character varying); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_registrar_usuario(p_nombre character varying, p_email character varying, p_contrasena character varying) RETURNS TABLE(mensaje text, token_generado uuid)
    LANGUAGE sql
    AS $$ SELECT * FROM sp_registrar_usuario(p_nombre, p_email, NULL::VARCHAR, p_contrasena); $$;


ALTER FUNCTION public.sp_registrar_usuario(p_nombre character varying, p_email character varying, p_contrasena character varying) OWNER TO postgres;

--
-- TOC entry 253 (class 1255 OID 16568)
-- Name: sp_registrar_usuario(character varying, character varying, character varying, character varying); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_registrar_usuario(p_nombre character varying, p_email character varying, p_telefono character varying, p_contrasena character varying) RETURNS TABLE(mensaje text, token_generado uuid)
    LANGUAGE plpgsql
    AS $$
DECLARE v_token UUID := gen_random_uuid();
BEGIN
  IF EXISTS (SELECT 1 FROM usuarios WHERE LOWER(email) = LOWER(p_email)) THEN
    RETURN QUERY SELECT 'El correo ya está registrado.'::TEXT, NULL::UUID; RETURN;
  END IF;
  INSERT INTO usuarios (nombre, email, telefono, contraseña, token_verificacion)
  VALUES (p_nombre, p_email, p_telefono, p_contrasena, v_token);
  RETURN QUERY SELECT 'Usuario registrado. Verifique su correo.'::TEXT, v_token;
END $$;


ALTER FUNCTION public.sp_registrar_usuario(p_nombre character varying, p_email character varying, p_telefono character varying, p_contrasena character varying) OWNER TO postgres;

--
-- TOC entry 269 (class 1255 OID 16573)
-- Name: sp_registrar_venta(character varying, character varying, double precision); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_registrar_venta(p_cliente character varying, p_metodo_pago character varying, p_monto_total double precision) RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
DECLARE v_id_cliente INTEGER;
BEGIN
  -- Busca al cliente por nombre (puede quedar NULL si es público general)
  SELECT u.id_usuario INTO v_id_cliente FROM usuarios u
   WHERE u.nombre ILIKE '%' || p_cliente || '%' AND u.rol = 'cliente' LIMIT 1;
  INSERT INTO ventas (id_cliente, metodo_pago, monto_total, fecha_venta)
  VALUES (v_id_cliente, p_metodo_pago, p_monto_total::NUMERIC, NOW());
  RETURN QUERY SELECT 'Venta procesada con éxito y caja actualizada.'::TEXT;
END $$;


ALTER FUNCTION public.sp_registrar_venta(p_cliente character varying, p_metodo_pago character varying, p_monto_total double precision) OWNER TO postgres;

--
-- TOC entry 247 (class 1255 OID 16562)
-- Name: sp_reservar_cita_temporal(integer, integer, integer); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_reservar_cita_temporal(p_id_usuario integer, p_id_horario integer, p_id_servicio integer) RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
DECLARE v_esp INTEGER;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM usuarios WHERE id_usuario = p_id_usuario) THEN
    RETURN QUERY SELECT 'Debe iniciar sesión para reservar.'::TEXT; RETURN;
  END IF;
  -- FOR UPDATE reemplaza WITH (UPDLOCK, ROWLOCK): evita que dos personas tomen el mismo horario
  SELECT h.id_especialista INTO v_esp FROM horarios h
   WHERE h.id_horario = p_id_horario AND h.estado = 'DISPONIBLE' FOR UPDATE;
  IF NOT FOUND THEN
    RETURN QUERY SELECT 'El horario ya fue tomado o no existe.'::TEXT; RETURN;
  END IF;
  UPDATE horarios SET estado = 'EN_PROCESO', fecha_bloqueo = NOW() WHERE id_horario = p_id_horario;
  INSERT INTO citas (id_usuario, id_especialista, id_servicio, id_horario, estado)
  VALUES (p_id_usuario, v_esp, p_id_servicio, p_id_horario, 'pendiente_confirmacion');
  RETURN QUERY SELECT 'Horario bloqueado temporalmente. Proceda a confirmar.'::TEXT;
EXCEPTION WHEN OTHERS THEN
  RETURN QUERY SELECT 'Ocurrió un error en la reserva.'::TEXT;
END $$;


ALTER FUNCTION public.sp_reservar_cita_temporal(p_id_usuario integer, p_id_horario integer, p_id_servicio integer) OWNER TO postgres;

--
-- TOC entry 259 (class 1255 OID 16570)
-- Name: sp_verificar_cuenta(uuid); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.sp_verificar_cuenta(p_token uuid) RETURNS TABLE(mensaje text)
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM usuarios WHERE token_verificacion = p_token) THEN
    UPDATE usuarios SET cuenta_verificada = TRUE, token_verificacion = NULL WHERE token_verificacion = p_token;
    RETURN QUERY SELECT 'Cuenta verificada con éxito.'::TEXT;
  ELSE
    RETURN QUERY SELECT 'Token inválido o expirado.'::TEXT;
  END IF;
END $$;


ALTER FUNCTION public.sp_verificar_cuenta(p_token uuid) OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 228 (class 1259 OID 16436)
-- Name: citas; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.citas (
    id_cita integer NOT NULL,
    id_usuario integer,
    id_especialista integer,
    id_servicio integer,
    id_horario integer,
    estado character varying(30) DEFAULT 'pendiente_confirmacion'::character varying,
    fecha_creacion timestamp without time zone DEFAULT now()
);


ALTER TABLE public.citas OWNER TO postgres;

--
-- TOC entry 227 (class 1259 OID 16435)
-- Name: citas_id_cita_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.citas ALTER COLUMN id_cita ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.citas_id_cita_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 240 (class 1259 OID 16550)
-- Name: configuracion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.configuracion (
    id_config integer NOT NULL,
    nombre_salon character varying(100) DEFAULT 'NOVA Beauty Salon'::character varying NOT NULL,
    telefono_whatsapp character varying(20),
    direccion character varying(200),
    instagram character varying(200),
    facebook character varying(200),
    fecha_actualizacion timestamp without time zone DEFAULT now(),
    correo_contacto character varying(150),
    tiktok_url character varying(250),
    whatsapp_url character varying(250)
);


ALTER TABLE public.configuracion OWNER TO postgres;

--
-- TOC entry 239 (class 1259 OID 16549)
-- Name: configuracion_id_config_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.configuracion ALTER COLUMN id_config ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.configuracion_id_config_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 236 (class 1259 OID 16517)
-- Name: detalle_venta_producto; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.detalle_venta_producto (
    id_detalle integer NOT NULL,
    id_venta integer,
    id_producto integer,
    cantidad integer NOT NULL,
    precio_unitario numeric(10,2) NOT NULL,
    CONSTRAINT detalle_venta_producto_cantidad_check CHECK ((cantidad > 0))
);


ALTER TABLE public.detalle_venta_producto OWNER TO postgres;

--
-- TOC entry 235 (class 1259 OID 16516)
-- Name: detalle_venta_producto_id_detalle_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.detalle_venta_producto ALTER COLUMN id_detalle ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.detalle_venta_producto_id_detalle_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 243 (class 1259 OID 16622)
-- Name: especialista_servicio; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.especialista_servicio (
    id_especialista integer NOT NULL,
    id_servicio integer NOT NULL,
    activo boolean DEFAULT true NOT NULL
);


ALTER TABLE public.especialista_servicio OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 16404)
-- Name: especialistas; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.especialistas (
    id_especialista integer NOT NULL,
    nombre character varying(50),
    especialidad character varying(50),
    estado boolean DEFAULT true,
    telefono character varying(9)
);


ALTER TABLE public.especialistas OWNER TO postgres;

--
-- TOC entry 221 (class 1259 OID 16403)
-- Name: especialistas_id_especialista_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.especialistas ALTER COLUMN id_especialista ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.especialistas_id_especialista_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 238 (class 1259 OID 16537)
-- Name: galeria; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.galeria (
    id_imagen integer NOT NULL,
    titulo character varying(100),
    categoria character varying(50) DEFAULT 'General'::character varying,
    url_imagen character varying(500) NOT NULL,
    fecha_subida timestamp without time zone DEFAULT now(),
    estado boolean DEFAULT true,
    url_imagen_despues text
);


ALTER TABLE public.galeria OWNER TO postgres;

--
-- TOC entry 237 (class 1259 OID 16536)
-- Name: galeria_id_imagen_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.galeria ALTER COLUMN id_imagen ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.galeria_id_imagen_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 242 (class 1259 OID 16594)
-- Name: horario_salon; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.horario_salon (
    id_horario_salon bigint NOT NULL,
    dia_semana smallint NOT NULL,
    hora_inicio time without time zone NOT NULL,
    hora_fin time without time zone NOT NULL,
    intervalo_minutos smallint DEFAULT 30 NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    CONSTRAINT horario_salon_check CHECK ((hora_fin > hora_inicio)),
    CONSTRAINT horario_salon_dia_semana_check CHECK (((dia_semana >= 1) AND (dia_semana <= 7))),
    CONSTRAINT horario_salon_intervalo_minutos_check CHECK (((intervalo_minutos >= 5) AND (intervalo_minutos <= 120)))
);


ALTER TABLE public.horario_salon OWNER TO postgres;

--
-- TOC entry 241 (class 1259 OID 16593)
-- Name: horario_salon_id_horario_salon_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.horario_salon_id_horario_salon_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.horario_salon_id_horario_salon_seq OWNER TO postgres;

--
-- TOC entry 5206 (class 0 OID 0)
-- Dependencies: 241
-- Name: horario_salon_id_horario_salon_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.horario_salon_id_horario_salon_seq OWNED BY public.horario_salon.id_horario_salon;


--
-- TOC entry 226 (class 1259 OID 16423)
-- Name: horarios; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.horarios (
    id_horario integer NOT NULL,
    id_especialista integer,
    fecha date,
    hora_inicio time without time zone,
    hora_fin time without time zone,
    estado character varying(20) DEFAULT 'DISPONIBLE'::character varying,
    fecha_bloqueo timestamp without time zone,
    id_servicio integer
);


ALTER TABLE public.horarios OWNER TO postgres;

--
-- TOC entry 225 (class 1259 OID 16422)
-- Name: horarios_id_horario_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.horarios ALTER COLUMN id_horario ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.horarios_id_horario_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 245 (class 1259 OID 16644)
-- Name: mensajes_contacto; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.mensajes_contacto (
    id_mensaje integer NOT NULL,
    nombre character varying(100) NOT NULL,
    correo character varying(100) NOT NULL,
    telefono character varying(20),
    mensaje text NOT NULL,
    acepta_privacidad boolean DEFAULT false NOT NULL,
    fecha_envio timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.mensajes_contacto OWNER TO postgres;

--
-- TOC entry 244 (class 1259 OID 16643)
-- Name: mensajes_contacto_id_mensaje_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.mensajes_contacto ALTER COLUMN id_mensaje ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.mensajes_contacto_id_mensaje_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 232 (class 1259 OID 16480)
-- Name: productos; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.productos (
    id_producto integer NOT NULL,
    nombre character varying(100) NOT NULL,
    categoria character varying(50),
    precio numeric(10,2) NOT NULL,
    stock integer NOT NULL,
    descripcion character varying(255),
    estado boolean DEFAULT true,
    imagen_url text,
    CONSTRAINT productos_stock_check CHECK ((stock >= 0))
);


ALTER TABLE public.productos OWNER TO postgres;

--
-- TOC entry 231 (class 1259 OID 16479)
-- Name: productos_id_producto_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.productos ALTER COLUMN id_producto ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.productos_id_producto_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 230 (class 1259 OID 16465)
-- Name: promociones; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.promociones (
    id_promocion integer NOT NULL,
    nombre character varying(100) NOT NULL,
    descuento integer NOT NULL,
    codigo character varying(50) NOT NULL,
    fecha_expiracion date NOT NULL,
    descripcion character varying(255),
    estado boolean DEFAULT true,
    CONSTRAINT promociones_descuento_check CHECK (((descuento > 0) AND (descuento <= 100)))
);


ALTER TABLE public.promociones OWNER TO postgres;

--
-- TOC entry 229 (class 1259 OID 16464)
-- Name: promociones_id_promocion_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.promociones ALTER COLUMN id_promocion ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.promociones_id_promocion_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 224 (class 1259 OID 16412)
-- Name: servicios; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.servicios (
    id_servicio integer NOT NULL,
    nombre character varying(50),
    descripcion text,
    precio numeric(10,2),
    duracion_minutos integer,
    imagen_url text,
    categoria character varying(50) DEFAULT 'cabello'::character varying,
    estado boolean DEFAULT true
);


ALTER TABLE public.servicios OWNER TO postgres;

--
-- TOC entry 223 (class 1259 OID 16411)
-- Name: servicios_id_servicio_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.servicios ALTER COLUMN id_servicio ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.servicios_id_servicio_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 220 (class 1259 OID 16390)
-- Name: usuarios; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.usuarios (
    id_usuario integer NOT NULL,
    nombre character varying(50),
    email character varying(50),
    "contraseña" character varying(80),
    rol character varying(20) DEFAULT 'cliente'::character varying,
    cuenta_verificada boolean DEFAULT false,
    token_verificacion uuid,
    fecha_registro timestamp without time zone DEFAULT now(),
    telefono character varying(9),
    acepta_privacidad boolean DEFAULT false NOT NULL,
    acepta_terminos boolean DEFAULT false NOT NULL,
    acepta_marketing boolean DEFAULT false NOT NULL,
    fecha_consentimiento timestamp without time zone,
    version_politicas character varying(20),
    token_recuperacion_hash character varying(64),
    recuperacion_expira timestamp without time zone,
    CONSTRAINT chk_rol CHECK (((rol)::text = ANY ((ARRAY['cliente'::character varying, 'recepcion'::character varying, 'admin'::character varying])::text[])))
);


ALTER TABLE public.usuarios OWNER TO postgres;

--
-- TOC entry 219 (class 1259 OID 16389)
-- Name: usuarios_id_usuario_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.usuarios ALTER COLUMN id_usuario ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.usuarios_id_usuario_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 234 (class 1259 OID 16492)
-- Name: ventas; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ventas (
    id_venta integer NOT NULL,
    id_cliente integer,
    id_cita integer,
    id_promocion integer,
    metodo_pago character varying(50),
    monto_total numeric(10,2) NOT NULL,
    fecha_venta timestamp without time zone DEFAULT now(),
    tipo_venta character varying(20) DEFAULT 'producto'::character varying NOT NULL,
    id_servicio integer,
    estado_venta character varying(20) DEFAULT 'completada'::character varying NOT NULL,
    motivo_anulacion text,
    fecha_anulacion timestamp without time zone,
    CONSTRAINT ventas_estado_venta_check CHECK (((estado_venta)::text = ANY ((ARRAY['completada'::character varying, 'anulada'::character varying])::text[]))),
    CONSTRAINT ventas_metodo_pago_check CHECK (((metodo_pago)::text = ANY ((ARRAY['Yape'::character varying, 'Tarjeta'::character varying, 'Efectivo'::character varying])::text[]))),
    CONSTRAINT ventas_tipo_venta_check CHECK (((tipo_venta)::text = ANY ((ARRAY['servicio'::character varying, 'producto'::character varying])::text[])))
);


ALTER TABLE public.ventas OWNER TO postgres;

--
-- TOC entry 233 (class 1259 OID 16491)
-- Name: ventas_id_venta_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.ventas ALTER COLUMN id_venta ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.ventas_id_venta_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 4955 (class 2604 OID 16597)
-- Name: horario_salon id_horario_salon; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.horario_salon ALTER COLUMN id_horario_salon SET DEFAULT nextval('public.horario_salon_id_horario_salon_seq'::regclass);


--
-- TOC entry 5183 (class 0 OID 16436)
-- Dependencies: 228
-- Data for Name: citas; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.citas (id_cita, id_usuario, id_especialista, id_servicio, id_horario, estado, fecha_creacion) FROM stdin;
1	6	1	1	36	cancelada	2026-10-21 20:47:27.933106
2	7	1	1	7	confirmada	2026-10-21 20:50:52.722741
3	7	3	3	45	pendiente_confirmacion	2026-11-08 14:50:57.718745
4	16	3	2	46	pendiente_confirmacion	2026-11-08 14:53:12.570851
5	10	2	1	65	pendiente_confirmacion	2026-11-08 19:23:33.792156
6	16	2	2	84	confirmada	2026-11-08 19:24:04.07309
7	16	2	2	100	confirmada	2026-11-08 19:26:15.679546
\.


--
-- TOC entry 5195 (class 0 OID 16550)
-- Dependencies: 240
-- Data for Name: configuracion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.configuracion (id_config, nombre_salon, telefono_whatsapp, direccion, instagram, facebook, fecha_actualizacion, correo_contacto, tiktok_url, whatsapp_url) FROM stdin;
1	NOVA BEAUTY SALON	985740023	Lima, Collique, Peru	https://www.instagram.com/darky_z7z/	https://www.instagram.com/darky_z7z/	2026-11-03 22:27:04.117603	novabeautysalon@gmail.com	https://www.tiktok.com/@darky_z7?is_from_webapp=1&sender_device=pc	https://wa.me/51921414198
\.


--
-- TOC entry 5191 (class 0 OID 16517)
-- Dependencies: 236
-- Data for Name: detalle_venta_producto; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.detalle_venta_producto (id_detalle, id_venta, id_producto, cantidad, precio_unitario) FROM stdin;
1	2	3	1	20.00
\.


--
-- TOC entry 5198 (class 0 OID 16622)
-- Dependencies: 243
-- Data for Name: especialista_servicio; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.especialista_servicio (id_especialista, id_servicio, activo) FROM stdin;
2	2	t
2	3	t
2	4	t
2	1	t
3	2	t
3	3	t
3	4	t
3	1	t
\.


--
-- TOC entry 5177 (class 0 OID 16404)
-- Dependencies: 222
-- Data for Name: especialistas; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.especialistas (id_especialista, nombre, especialidad, estado, telefono) FROM stdin;
2	Niurka Escate Marca	Manicura	t	954751684
1	Especialista de Prueba	Belleza	f	999999999
3	Carlos Rodas	Cabello	t	954751684
\.


--
-- TOC entry 5193 (class 0 OID 16537)
-- Dependencies: 238
-- Data for Name: galeria; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.galeria (id_imagen, titulo, categoria, url_imagen, fecha_subida, estado, url_imagen_despues) FROM stdin;
2	Mirada & Cejas	cejas	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSu7phFWICIOR1NNSoZ8zDu1aJ8iFw9dowOOqkSE5cPvw&s=10	2026-10-21 13:15:51.130609	f	\N
1	Uñas Acrilicas	cabello	https://www.essie.es/-/media/project/loreal/brand-sites/essie/emea/es/articles/tips_trends/unas-desgastadas/unas-desgastadas.jpeg?la=es-es&rev=f754b697f064420ba64a10f5f3572ed3&hash=1F19523EE9E8532D81E3CE17A5ECF4AA	2026-10-21 01:34:29.213105	f	\N
3	Mirada & Cejas	cejas	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSu7phFWICIOR1NNSoZ8zDu1aJ8iFw9dowOOqkSE5cPvw&s=10	2026-10-21 17:43:50.898516	f	\N
4	Mirada & Cejas	cejas	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSu7phFWICIOR1NNSoZ8zDu1aJ8iFw9dowOOqkSE5cPvw&s=10	2026-10-21 17:54:49.736014	f	\N
5	Mirada & Cejas	cejas	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSu7phFWICIOR1NNSoZ8zDu1aJ8iFw9dowOOqkSE5cPvw&s=10	2026-10-21 17:59:47.327188	f	/img/servicios/cejas_despues..jpg
6	Mirada & Cejas	cejas	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSu7phFWICIOR1NNSoZ8zDu1aJ8iFw9dowOOqkSE5cPvw&s=10	2026-10-21 18:27:58.188226	t	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSLfFxreLXLB_ogZ3N-4kpSPgdI9Z8OxRgu81akuMjnHj0069IoojRpgvIL&s=10
7	Mirada & Cejas	cejas	https://www.essie.es/-/media/project/loreal/brand-sites/essie/emea/es/articles/tips_trends/unas-desgastadas/unas-desgastadas.jpeg?la=es-es&rev=f754b697f064420ba64a10f5f3572ed3&hash=1F19523EE9E8532D81E3CE17A5ECF4AA	2026-10-21 18:37:43.73971	f	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSpiOqaWypbwKr47p31iyPeF09yt5T63_7W3BDjsp8O-eIlr2PGqFrZVkw&s=10
8	Cabello	cabello	https://img.magnific.com/fotos-premium/mujer-cabello-pan-expresion-sorpresa-su-cara_818261-89477.jpg?semt=ais_hybrid&w=740&q=80	2026-10-22 11:26:02.816268	t	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQVnC60hFQknWO1wO9Q7p7y5Y6vC4BOUIpDQfhMTTJXYAxuOYM-9lGrvnJu&s=10
9	Tintes	cabello	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSOBWVcNzAgzf--KSM3wwefMaEkI-0dDmdXVy4E4Z8AZkb_fQ-eHwNUX5g&s=10	2026-10-22 11:28:49.210385	t	https://media.glamour.mx/photos/677c4ff7138f0bd4f56f1f44/master/w_1600%2Cc_limit/tendencias-de-cabello-2025-long-midi.jpg
10	Ondulación	cabello	https://img.magnific.com/foto-gratis/mujer-timida-escondida-detras-su-cabello_329181-3547.jpg?semt=ais_hybrid&w=740&q=80	2026-10-22 11:34:19.426942	t	https://i.pinimg.com/originals/2a/72/13/2a7213e557faff17971eb13fe6d928d8.jpg
11	Uñas Acrilicas	uñas	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTzr7nF3HNO6HqR0goydlGi8s1AKICiPl6H_zlpn7_ATwKu_7hcohSZd-0&s=10	2026-10-22 11:37:09.590886	t	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQe4my-iGh0XCnA64uJfpGPMFNFN3pJUDPEqTdh_PtUKnhdllobrPaTeV0&s=10
12	Manicure	uñas	https://i.pinimg.com/736x/7b/ea/3a/7bea3aa168c73768db435329ac6f93ec.jpg	2026-10-22 11:39:54.215679	t	https://www.hola.com/horizon/square/132311cea9f3-manicura-flores.jpg
13	Uñas	uñas	https://hips.hearstapps.com/hmg-prod/images/manicura-limpia-endurecedor-675becd5d4fca.jpg?resize=980:*	2026-10-22 11:41:12.382133	t	https://img.kwcdn.com/product/Fancyalgo/VirtualModelMatting/913fadd3ad02dde464dbb31d95760ef5.jpg?imageMogr2/auto-orient%7CimageMogr2/thumbnail/800x9999%3E/quality/70/format/webp
14	cejas	cejas	https://images.ecestaticos.com/IOgjF381n1Gr2tTLd9vbTdJsmjc=/0x0:2272x1516/600x315/filters:fill(white):format(jpg)/f.elconfidencial.com%2Foriginal%2Fdf8%2F001%2Fb94%2Fdf8001b949d7e7c188bc0e183923c258.jpg	2026-10-22 11:47:03.268129	t	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ5YrgDVswlOI8XKlR6HJBlTSrsOoCX98fIcTuCciCCpA&s=10
15	Depilación de Cejas	cejas	https://www.clarin.com/2021/02/21/prEb-0QK6_1200x0__1.jpg	2026-10-22 11:51:29.526106	t	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRcj0qna58dFP_YxotOmC4SQHoIVYS3MvTIJ72EdvbAfA&s=10
\.


--
-- TOC entry 5197 (class 0 OID 16594)
-- Dependencies: 242
-- Data for Name: horario_salon; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.horario_salon (id_horario_salon, dia_semana, hora_inicio, hora_fin, intervalo_minutos, activo) FROM stdin;
1	1	09:00:00	20:00:00	30	t
2	2	09:00:00	20:00:00	30	t
3	3	09:00:00	20:00:00	30	t
4	4	09:00:00	20:00:00	30	t
5	5	09:00:00	20:00:00	30	t
6	6	09:00:00	20:00:00	30	t
7	7	10:00:00	20:00:00	30	t
\.


--
-- TOC entry 5181 (class 0 OID 16423)
-- Dependencies: 226
-- Data for Name: horarios; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.horarios (id_horario, id_especialista, fecha, hora_inicio, hora_fin, estado, fecha_bloqueo, id_servicio) FROM stdin;
1	1	2026-10-26	09:00:00	10:00:00	DISPONIBLE	\N	\N
2	1	2026-10-25	10:00:00	11:00:00	DISPONIBLE	\N	\N
3	1	2026-10-24	10:00:00	11:00:00	DISPONIBLE	\N	\N
4	1	2026-10-22	14:00:00	15:00:00	DISPONIBLE	\N	\N
5	1	2026-10-27	14:00:00	15:00:00	DISPONIBLE	\N	\N
6	1	2026-10-27	11:00:00	12:00:00	DISPONIBLE	\N	\N
8	1	2026-10-23	09:00:00	10:00:00	DISPONIBLE	\N	\N
9	1	2026-10-28	15:00:00	16:00:00	DISPONIBLE	\N	\N
10	1	2026-10-24	09:00:00	10:00:00	DISPONIBLE	\N	\N
11	1	2026-10-22	15:00:00	16:00:00	DISPONIBLE	\N	\N
12	1	2026-10-27	15:00:00	16:00:00	DISPONIBLE	\N	\N
13	1	2026-10-25	09:00:00	10:00:00	DISPONIBLE	\N	\N
14	1	2026-10-26	10:00:00	11:00:00	DISPONIBLE	\N	\N
15	1	2026-10-23	10:00:00	11:00:00	DISPONIBLE	\N	\N
16	1	2026-10-28	14:00:00	15:00:00	DISPONIBLE	\N	\N
17	1	2026-10-28	11:00:00	12:00:00	DISPONIBLE	\N	\N
18	1	2026-10-28	16:00:00	17:00:00	DISPONIBLE	\N	\N
19	1	2026-10-27	16:00:00	17:00:00	DISPONIBLE	\N	\N
20	1	2026-10-22	16:00:00	17:00:00	DISPONIBLE	\N	\N
21	1	2026-10-25	16:00:00	17:00:00	DISPONIBLE	\N	\N
22	1	2026-10-24	16:00:00	17:00:00	DISPONIBLE	\N	\N
23	1	2026-10-26	16:00:00	17:00:00	DISPONIBLE	\N	\N
24	1	2026-10-23	16:00:00	17:00:00	DISPONIBLE	\N	\N
25	1	2026-10-24	15:00:00	16:00:00	DISPONIBLE	\N	\N
26	1	2026-10-22	09:00:00	10:00:00	DISPONIBLE	\N	\N
27	1	2026-10-27	09:00:00	10:00:00	DISPONIBLE	\N	\N
28	1	2026-10-25	15:00:00	16:00:00	DISPONIBLE	\N	\N
29	1	2026-10-26	14:00:00	15:00:00	DISPONIBLE	\N	\N
30	1	2026-10-26	11:00:00	12:00:00	DISPONIBLE	\N	\N
31	1	2026-10-23	11:00:00	12:00:00	DISPONIBLE	\N	\N
32	1	2026-10-28	10:00:00	11:00:00	DISPONIBLE	\N	\N
33	1	2026-10-23	14:00:00	15:00:00	DISPONIBLE	\N	\N
34	1	2026-10-26	15:00:00	16:00:00	DISPONIBLE	\N	\N
35	1	2026-10-27	10:00:00	11:00:00	DISPONIBLE	\N	\N
37	1	2026-10-25	11:00:00	12:00:00	DISPONIBLE	\N	\N
38	1	2026-10-25	14:00:00	15:00:00	DISPONIBLE	\N	\N
39	1	2026-10-24	14:00:00	15:00:00	DISPONIBLE	\N	\N
40	1	2026-10-24	11:00:00	12:00:00	DISPONIBLE	\N	\N
41	1	2026-10-23	15:00:00	16:00:00	DISPONIBLE	\N	\N
42	1	2026-10-28	09:00:00	10:00:00	DISPONIBLE	\N	\N
7	1	2026-10-22	11:00:00	12:00:00	RESERVADO	\N	\N
36	1	2026-10-22	10:00:00	11:00:00	DISPONIBLE	\N	\N
43	2	2026-11-10	10:00:00	11:00:00	DISPONIBLE	\N	\N
44	2	2026-11-10	09:00:00	10:00:00	DISPONIBLE	\N	\N
84	2	2026-11-14	10:00:00	11:00:00	RESERVADO	\N	\N
45	3	2026-11-10	10:00:00	11:00:00	RESERVADO	\N	\N
92	2	2026-11-16	09:00:00	10:00:00	DISPONIBLE	\N	\N
46	3	2026-11-10	09:00:00	10:00:00	RESERVADO	\N	\N
47	2	2026-11-11	17:00:00	18:00:00	DISPONIBLE	\N	\N
48	2	2026-11-11	16:00:00	17:00:00	DISPONIBLE	\N	\N
49	2	2026-11-11	15:00:00	16:00:00	DISPONIBLE	\N	\N
50	2	2026-11-11	14:00:00	15:00:00	DISPONIBLE	\N	\N
51	2	2026-11-11	13:00:00	14:00:00	DISPONIBLE	\N	\N
52	2	2026-11-11	12:00:00	13:00:00	DISPONIBLE	\N	\N
53	2	2026-11-11	11:00:00	12:00:00	DISPONIBLE	\N	\N
54	2	2026-11-11	10:00:00	11:00:00	DISPONIBLE	\N	\N
55	2	2026-11-11	09:00:00	10:00:00	DISPONIBLE	\N	\N
56	3	2026-11-11	17:00:00	18:00:00	DISPONIBLE	\N	\N
57	3	2026-11-11	16:00:00	17:00:00	DISPONIBLE	\N	\N
58	3	2026-11-11	15:00:00	16:00:00	DISPONIBLE	\N	\N
59	3	2026-11-11	14:00:00	15:00:00	DISPONIBLE	\N	\N
60	3	2026-11-11	13:00:00	14:00:00	DISPONIBLE	\N	\N
61	3	2026-11-11	12:00:00	13:00:00	DISPONIBLE	\N	\N
62	3	2026-11-11	11:00:00	12:00:00	DISPONIBLE	\N	\N
63	3	2026-11-11	10:00:00	11:00:00	DISPONIBLE	\N	\N
64	3	2026-11-11	09:00:00	10:00:00	DISPONIBLE	\N	\N
66	2	2027-01-26	16:00:00	17:00:00	DISPONIBLE	\N	\N
67	2	2027-01-26	15:00:00	16:00:00	DISPONIBLE	\N	\N
68	2	2027-01-26	14:00:00	15:00:00	DISPONIBLE	\N	\N
69	2	2027-01-26	13:00:00	14:00:00	DISPONIBLE	\N	\N
70	2	2027-01-26	12:00:00	13:00:00	DISPONIBLE	\N	\N
71	2	2027-01-26	11:00:00	12:00:00	DISPONIBLE	\N	\N
72	2	2027-01-26	10:00:00	11:00:00	DISPONIBLE	\N	\N
73	2	2027-01-26	09:00:00	10:00:00	DISPONIBLE	\N	\N
74	3	2027-01-26	17:00:00	18:00:00	DISPONIBLE	\N	\N
75	3	2027-01-26	16:00:00	17:00:00	DISPONIBLE	\N	\N
76	3	2027-01-26	15:00:00	16:00:00	DISPONIBLE	\N	\N
77	3	2027-01-26	14:00:00	15:00:00	DISPONIBLE	\N	\N
78	3	2027-01-26	13:00:00	14:00:00	DISPONIBLE	\N	\N
79	3	2027-01-26	12:00:00	13:00:00	DISPONIBLE	\N	\N
80	3	2027-01-26	11:00:00	12:00:00	DISPONIBLE	\N	\N
81	3	2027-01-26	10:00:00	11:00:00	DISPONIBLE	\N	\N
82	3	2027-01-26	09:00:00	10:00:00	DISPONIBLE	\N	\N
65	2	2027-01-26	17:00:00	18:00:00	RESERVADO	\N	\N
83	2	2026-11-14	09:00:00	10:00:00	DISPONIBLE	\N	\N
85	2	2026-11-14	11:00:00	12:00:00	DISPONIBLE	\N	\N
86	2	2026-11-14	12:00:00	13:00:00	DISPONIBLE	\N	\N
87	2	2026-11-14	13:00:00	14:00:00	DISPONIBLE	\N	\N
88	2	2026-11-14	14:00:00	15:00:00	DISPONIBLE	\N	\N
89	2	2026-11-14	15:00:00	16:00:00	DISPONIBLE	\N	\N
90	2	2026-11-14	16:00:00	17:00:00	DISPONIBLE	\N	\N
91	2	2026-11-14	17:00:00	18:00:00	DISPONIBLE	\N	\N
93	2	2026-11-16	10:00:00	11:00:00	DISPONIBLE	\N	\N
94	2	2026-11-16	11:00:00	12:00:00	DISPONIBLE	\N	\N
95	2	2026-11-16	12:00:00	13:00:00	DISPONIBLE	\N	\N
96	2	2026-11-16	13:00:00	14:00:00	DISPONIBLE	\N	\N
97	2	2026-11-16	14:00:00	15:00:00	DISPONIBLE	\N	\N
98	2	2026-11-16	15:00:00	16:00:00	DISPONIBLE	\N	\N
99	2	2026-11-16	16:00:00	17:00:00	DISPONIBLE	\N	\N
101	2	2026-11-17	09:00:00	10:00:00	DISPONIBLE	\N	\N
102	2	2026-11-17	10:00:00	11:00:00	DISPONIBLE	\N	\N
103	2	2026-11-17	11:00:00	12:00:00	DISPONIBLE	\N	\N
104	2	2026-11-17	12:00:00	13:00:00	DISPONIBLE	\N	\N
105	2	2026-11-17	13:00:00	14:00:00	DISPONIBLE	\N	\N
106	2	2026-11-17	14:00:00	15:00:00	DISPONIBLE	\N	\N
107	2	2026-11-17	15:00:00	16:00:00	DISPONIBLE	\N	\N
108	2	2026-11-17	16:00:00	17:00:00	DISPONIBLE	\N	\N
109	2	2026-11-17	17:00:00	18:00:00	DISPONIBLE	\N	\N
110	2	2026-11-30	09:00:00	10:00:00	DISPONIBLE	\N	\N
111	2	2026-11-30	10:00:00	11:00:00	DISPONIBLE	\N	\N
112	2	2026-11-30	11:00:00	12:00:00	DISPONIBLE	\N	\N
113	2	2026-11-30	12:00:00	13:00:00	DISPONIBLE	\N	\N
114	2	2026-11-30	13:00:00	14:00:00	DISPONIBLE	\N	\N
115	2	2026-11-30	14:00:00	15:00:00	DISPONIBLE	\N	\N
116	2	2026-11-30	15:00:00	16:00:00	DISPONIBLE	\N	\N
117	2	2026-11-30	16:00:00	17:00:00	DISPONIBLE	\N	\N
118	2	2026-11-30	17:00:00	18:00:00	DISPONIBLE	\N	\N
119	2	2026-11-23	09:00:00	10:00:00	DISPONIBLE	\N	\N
120	2	2026-11-23	10:00:00	11:00:00	DISPONIBLE	\N	\N
121	2	2026-11-23	11:00:00	12:00:00	DISPONIBLE	\N	\N
122	2	2026-11-23	12:00:00	13:00:00	DISPONIBLE	\N	\N
123	2	2026-11-23	13:00:00	14:00:00	DISPONIBLE	\N	\N
124	2	2026-11-23	14:00:00	15:00:00	DISPONIBLE	\N	\N
125	2	2026-11-23	15:00:00	16:00:00	DISPONIBLE	\N	\N
126	2	2026-11-23	16:00:00	17:00:00	DISPONIBLE	\N	\N
127	2	2026-11-23	17:00:00	18:00:00	DISPONIBLE	\N	\N
100	2	2026-11-16	17:00:00	18:00:00	RESERVADO	\N	\N
128	3	2026-11-16	09:00:00	10:00:00	DISPONIBLE	\N	\N
129	3	2026-11-16	10:00:00	11:00:00	DISPONIBLE	\N	\N
130	3	2026-11-16	11:00:00	12:00:00	DISPONIBLE	\N	\N
131	3	2026-11-16	12:00:00	13:00:00	DISPONIBLE	\N	\N
132	3	2026-11-16	13:00:00	14:00:00	DISPONIBLE	\N	\N
133	3	2026-11-16	14:00:00	15:00:00	DISPONIBLE	\N	\N
134	3	2026-11-16	15:00:00	16:00:00	DISPONIBLE	\N	\N
135	3	2026-11-16	16:00:00	17:00:00	DISPONIBLE	\N	\N
136	3	2026-11-16	17:00:00	18:00:00	DISPONIBLE	\N	\N
137	3	2026-12-11	09:00:00	10:00:00	DISPONIBLE	\N	\N
138	3	2026-12-11	10:00:00	11:00:00	DISPONIBLE	\N	\N
139	3	2026-12-11	11:00:00	12:00:00	DISPONIBLE	\N	\N
140	3	2026-12-11	12:00:00	13:00:00	DISPONIBLE	\N	\N
141	3	2026-12-11	13:00:00	14:00:00	DISPONIBLE	\N	\N
142	3	2026-12-11	14:00:00	15:00:00	DISPONIBLE	\N	\N
143	3	2026-12-11	15:00:00	16:00:00	DISPONIBLE	\N	\N
144	3	2026-12-11	16:00:00	17:00:00	DISPONIBLE	\N	\N
145	3	2026-12-11	17:00:00	18:00:00	DISPONIBLE	\N	\N
\.


--
-- TOC entry 5200 (class 0 OID 16644)
-- Dependencies: 245
-- Data for Name: mensajes_contacto; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.mensajes_contacto (id_mensaje, nombre, correo, telefono, mensaje, acepta_privacidad, fecha_envio) FROM stdin;
\.


--
-- TOC entry 5187 (class 0 OID 16480)
-- Dependencies: 232
-- Data for Name: productos; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.productos (id_producto, nombre, categoria, precio, stock, descripcion, estado, imagen_url) FROM stdin;
1	Tinte color rojo	Cabello	100.00	2	Cambio de tono	f	\N
2	Promo Invierno	Cabello	400.00	4		f	\N
3	Tinte Color Essence – Rojo 6000 Tubo 60g	Cabello	20.00	2	Tinte Color Essence – Rojo 6000 Tubo 60g	t	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQcBr-bmKc87CUDpdRCczuyQH2C0YwomSY65lzI6NT-GS0ydn2RTiCeIH0j&s=10
4	Shampoo Sin Sal Cabello Alisado 1lt Liss Control Salon In Recamier Pro	Cabello	90.00	5	Shampoo Sin Sal Cabello Alisado 1lt Liss Control Salon In Recamier Pro	t	https://oechsle.vteximg.com.br/arquivos/ids/23957775-1000-1000/imageUrl_1.jpg?v=639051338085500000
5	Uñas Postizas soft gel	Uñas	25.00	5	Kit de 100 uñas postizas al por mayor, 10 tamaños, 12 kits	t	https://http2.mlstatic.com/D_NQ_NP_902675-MLU70650760509_072023-O.webp
\.


--
-- TOC entry 5185 (class 0 OID 16465)
-- Dependencies: 230
-- Data for Name: promociones; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.promociones (id_promocion, nombre, descuento, codigo, fecha_expiracion, descripcion, estado) FROM stdin;
1	promo venarano	10	PROMO10	2026-11-07	descuento solo en pintados de cabello	t
2	promo verano	20	BELLEZA20	2026-11-21	Disfruta un momento para ti con un descuento especial en nuestros servicios de belleza. Presenta este cupón al reservar y renueva tu look en NOVA Beauty Salon.	t
\.


--
-- TOC entry 5179 (class 0 OID 16412)
-- Dependencies: 224
-- Data for Name: servicios; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.servicios (id_servicio, nombre, descripcion, precio, duracion_minutos, imagen_url, categoria, estado) FROM stdin;
2	Corte de cabello		20.00	60	https://i.pinimg.com/474x/ec/7b/87/ec7b87111daaebf197ac20d41762d36b.jpg	cabello	t
3	Peinados y brushing		120.00	60	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFNEQrq-JWuYSfJIB9UnOdwM_Absj08_ptshyMPhzYTw&s=10	cabello	t
4	Lifting de pestañas		15.00	60	https://makeupmestudio.com/wp-content/uploads/2018/06/lifting-pesta.jpg	cejas	t
1	Manicura clásica o spa		50.00	15	https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSpuxxbouLRmQrmPgMmnsd1HxUMbY4J-J-_mkJXY-xvMTGYRuKkAPdfgkTq&s=10	uñas	t
\.


--
-- TOC entry 5175 (class 0 OID 16390)
-- Dependencies: 220
-- Data for Name: usuarios; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.usuarios (id_usuario, nombre, email, "contraseña", rol, cuenta_verificada, token_verificacion, fecha_registro, telefono, acepta_privacidad, acepta_terminos, acepta_marketing, fecha_consentimiento, version_politicas, token_recuperacion_hash, recuperacion_expira) FROM stdin;
3	Maicol Villanueva	maicol10villanueva@gmail.com	$2a$10$SynV/9chC6jWpOhyYSgAKeLeev1quQ9Jq4kkirXcymxIVN3cx6fiq	admin	t	89e1fb7b-3c00-4dd0-8249-5fa64d7f633c	2026-10-20 23:29:37.479127	921414198	f	f	f	\N	\N	\N	\N
7	David Sanchez	navidsanchez@gmail.com	$2a$10$s8gWeQq/rH3/vxj5zxLndO56diffZpc1LC/DQOFYV4RPo7ysjQYsK	cliente	t	\N	2026-10-21 20:50:22.404579	98547514	f	f	f	\N	\N	\N	\N
6	Niurka Escate Marca	niurkaescate@gmial.com	$2a$10$bXc/r4U7k/NErBCtorGzVO5jqxW8VNKUvZW/hoLMv3wds/K9T9Z0O	cliente	t	\N	2026-10-21 18:51:04.25526	954751685	f	f	f	\N	\N	\N	\N
8	Enrique Gil Gonzales	enriquegil@gmail.com	$2a$10$ZXnNHFtyNm.TCU8DBvLu3.o2x2I34A3eezDGYaszMeSnF25D6XeTy	cliente	t	\N	2026-10-22 10:55:07.297889	954214785	f	f	f	\N	\N	\N	\N
9	Carlos Rodas	carlosrodas@gmail.com	$2a$10$mEcv8Du2R9VLyYzGz7K3QuZBFkWJPosvvXPuhTI0QOm2uCZcfjjaC	cliente	t	\N	2026-10-22 10:56:57.12093	98541354	f	f	f	\N	\N	\N	\N
10	Piero Martel	pieromartel@gmail.com	$2a$10$ZrgNWD9gHVVC0mEESwuWze9F36bIi/4byNAfL/.Bx1e4YPj3SrRs.	cliente	t	\N	2026-10-22 10:58:52.184289	985210235	f	f	f	\N	\N	\N	\N
13	Niurka Escate Marcaa	darky.z7z@gmail.com	$2a$10$O7g3v4pdcvSgLUMtb6kYYeKN431lOxW0wQwAdbu0UFzKHJ6uA36DC	cliente	t	\N	2026-10-22 12:36:45.99213	987412012	f	f	f	\N	\N	\N	\N
16	Enrique Gil Gonzales	darky.vs10@gmail.com	$2a$10$vVQYAAAsAxrrw4VkUhILlu1TCN0J.SZMn7MNogroL5t835yHaOuqq	cliente	t	\N	2026-10-22 13:13:46.106452	920014572	t	t	t	2026-10-22 13:13:46.107964	2026-10	\N	\N
17	juan Sanchez	darky.z7zz@gmail.com	$2a$10$0X0c43jmiqeG/w/b4KgGFeh8w6sSOHo3YOXwJ9T26gQbCB1ESvpVG	cliente	f	416d1d35-86a2-403a-986b-74fd1859a6c0	2026-11-08 14:12:44.033671	985201347	t	t	t	2026-11-08 14:12:44.043681	2026-10	\N	\N
\.


--
-- TOC entry 5189 (class 0 OID 16492)
-- Dependencies: 234
-- Data for Name: ventas; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ventas (id_venta, id_cliente, id_cita, id_promocion, metodo_pago, monto_total, fecha_venta, tipo_venta, id_servicio, estado_venta, motivo_anulacion, fecha_anulacion) FROM stdin;
1	\N	\N	\N	Yape	50.00	2026-10-21 22:09:26.885244	producto	\N	completada	\N	\N
2	6	\N	\N	Efectivo	20.00	2026-10-21 23:34:02.022609	producto	\N	completada	\N	\N
3	6	\N	\N	Yape	50.00	2026-10-22 01:04:05.753089	servicio	1	completada	\N	\N
4	8	\N	\N	Yape	120.00	2026-11-08 13:12:24.475811	servicio	3	completada	\N	\N
\.


--
-- TOC entry 5207 (class 0 OID 0)
-- Dependencies: 227
-- Name: citas_id_cita_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.citas_id_cita_seq', 7, true);


--
-- TOC entry 5208 (class 0 OID 0)
-- Dependencies: 239
-- Name: configuracion_id_config_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.configuracion_id_config_seq', 1, true);


--
-- TOC entry 5209 (class 0 OID 0)
-- Dependencies: 235
-- Name: detalle_venta_producto_id_detalle_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.detalle_venta_producto_id_detalle_seq', 1, true);


--
-- TOC entry 5210 (class 0 OID 0)
-- Dependencies: 221
-- Name: especialistas_id_especialista_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.especialistas_id_especialista_seq', 3, true);


--
-- TOC entry 5211 (class 0 OID 0)
-- Dependencies: 237
-- Name: galeria_id_imagen_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.galeria_id_imagen_seq', 15, true);


--
-- TOC entry 5212 (class 0 OID 0)
-- Dependencies: 241
-- Name: horario_salon_id_horario_salon_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.horario_salon_id_horario_salon_seq', 7, true);


--
-- TOC entry 5213 (class 0 OID 0)
-- Dependencies: 225
-- Name: horarios_id_horario_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.horarios_id_horario_seq', 145, true);


--
-- TOC entry 5214 (class 0 OID 0)
-- Dependencies: 244
-- Name: mensajes_contacto_id_mensaje_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.mensajes_contacto_id_mensaje_seq', 1, false);


--
-- TOC entry 5215 (class 0 OID 0)
-- Dependencies: 231
-- Name: productos_id_producto_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.productos_id_producto_seq', 5, true);


--
-- TOC entry 5216 (class 0 OID 0)
-- Dependencies: 229
-- Name: promociones_id_promocion_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.promociones_id_promocion_seq', 2, true);


--
-- TOC entry 5217 (class 0 OID 0)
-- Dependencies: 223
-- Name: servicios_id_servicio_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.servicios_id_servicio_seq', 4, true);


--
-- TOC entry 5218 (class 0 OID 0)
-- Dependencies: 219
-- Name: usuarios_id_usuario_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.usuarios_id_usuario_seq', 17, true);


--
-- TOC entry 5219 (class 0 OID 0)
-- Dependencies: 233
-- Name: ventas_id_venta_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ventas_id_venta_seq', 4, true);


--
-- TOC entry 4988 (class 2606 OID 16443)
-- Name: citas citas_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.citas
    ADD CONSTRAINT citas_pkey PRIMARY KEY (id_cita);


--
-- TOC entry 5005 (class 2606 OID 16560)
-- Name: configuracion configuracion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.configuracion
    ADD CONSTRAINT configuracion_pkey PRIMARY KEY (id_config);


--
-- TOC entry 5000 (class 2606 OID 16525)
-- Name: detalle_venta_producto detalle_venta_producto_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.detalle_venta_producto
    ADD CONSTRAINT detalle_venta_producto_pkey PRIMARY KEY (id_detalle);


--
-- TOC entry 5010 (class 2606 OID 16630)
-- Name: especialista_servicio especialista_servicio_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.especialista_servicio
    ADD CONSTRAINT especialista_servicio_pkey PRIMARY KEY (id_especialista, id_servicio);


--
-- TOC entry 4978 (class 2606 OID 16410)
-- Name: especialistas especialistas_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.especialistas
    ADD CONSTRAINT especialistas_pkey PRIMARY KEY (id_especialista);


--
-- TOC entry 5003 (class 2606 OID 16548)
-- Name: galeria galeria_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.galeria
    ADD CONSTRAINT galeria_pkey PRIMARY KEY (id_imagen);


--
-- TOC entry 5007 (class 2606 OID 16610)
-- Name: horario_salon horario_salon_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.horario_salon
    ADD CONSTRAINT horario_salon_pkey PRIMARY KEY (id_horario_salon);


--
-- TOC entry 4984 (class 2606 OID 16429)
-- Name: horarios horarios_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.horarios
    ADD CONSTRAINT horarios_pkey PRIMARY KEY (id_horario);


--
-- TOC entry 5013 (class 2606 OID 16658)
-- Name: mensajes_contacto mensajes_contacto_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.mensajes_contacto
    ADD CONSTRAINT mensajes_contacto_pkey PRIMARY KEY (id_mensaje);


--
-- TOC entry 4995 (class 2606 OID 16490)
-- Name: productos productos_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT productos_pkey PRIMARY KEY (id_producto);


--
-- TOC entry 4991 (class 2606 OID 16478)
-- Name: promociones promociones_codigo_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.promociones
    ADD CONSTRAINT promociones_codigo_key UNIQUE (codigo);


--
-- TOC entry 4993 (class 2606 OID 16476)
-- Name: promociones promociones_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.promociones
    ADD CONSTRAINT promociones_pkey PRIMARY KEY (id_promocion);


--
-- TOC entry 4982 (class 2606 OID 16421)
-- Name: servicios servicios_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.servicios
    ADD CONSTRAINT servicios_pkey PRIMARY KEY (id_servicio);


--
-- TOC entry 4974 (class 2606 OID 16401)
-- Name: usuarios usuarios_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_email_key UNIQUE (email);


--
-- TOC entry 4976 (class 2606 OID 16399)
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id_usuario);


--
-- TOC entry 4998 (class 2606 OID 16500)
-- Name: ventas ventas_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ventas
    ADD CONSTRAINT ventas_pkey PRIMARY KEY (id_venta);


--
-- TOC entry 4989 (class 1259 OID 16618)
-- Name: idx_citas_horario_estado; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_citas_horario_estado ON public.citas USING btree (id_horario, estado);


--
-- TOC entry 5001 (class 1259 OID 16586)
-- Name: idx_detalle_venta_producto_venta; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_detalle_venta_producto_venta ON public.detalle_venta_producto USING btree (id_venta);


--
-- TOC entry 5011 (class 1259 OID 16641)
-- Name: idx_especialista_servicio_activo; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_especialista_servicio_activo ON public.especialista_servicio USING btree (id_servicio, activo);


--
-- TOC entry 4979 (class 1259 OID 16619)
-- Name: idx_especialistas_activos; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_especialistas_activos ON public.especialistas USING btree (estado, especialidad);


--
-- TOC entry 4985 (class 1259 OID 16617)
-- Name: idx_horarios_fecha_estado; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_horarios_fecha_estado ON public.horarios USING btree (fecha, estado);


--
-- TOC entry 4980 (class 1259 OID 16620)
-- Name: idx_servicios_activos; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_servicios_activos ON public.servicios USING btree (estado, categoria);


--
-- TOC entry 4971 (class 1259 OID 16642)
-- Name: idx_usuarios_token_recuperacion; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_usuarios_token_recuperacion ON public.usuarios USING btree (token_recuperacion_hash) WHERE (token_recuperacion_hash IS NOT NULL);


--
-- TOC entry 4996 (class 1259 OID 16585)
-- Name: idx_ventas_fecha_estado; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ventas_fecha_estado ON public.ventas USING btree (fecha_venta, estado_venta);


--
-- TOC entry 5008 (class 1259 OID 16621)
-- Name: uq_horario_salon_dia; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_horario_salon_dia ON public.horario_salon USING btree (dia_semana);


--
-- TOC entry 4986 (class 1259 OID 16616)
-- Name: uq_horarios_generados; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_horarios_generados ON public.horarios USING btree (id_especialista, id_servicio, fecha, hora_inicio) WHERE (id_servicio IS NOT NULL);


--
-- TOC entry 4972 (class 1259 OID 16402)
-- Name: uq_usuarios_email_lower; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_usuarios_email_lower ON public.usuarios USING btree (lower((email)::text));


--
-- TOC entry 5025 (class 2606 OID 16631)
-- Name: especialista_servicio especialista_servicio_id_especialista_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.especialista_servicio
    ADD CONSTRAINT especialista_servicio_id_especialista_fkey FOREIGN KEY (id_especialista) REFERENCES public.especialistas(id_especialista);


--
-- TOC entry 5026 (class 2606 OID 16636)
-- Name: especialista_servicio especialista_servicio_id_servicio_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.especialista_servicio
    ADD CONSTRAINT especialista_servicio_id_servicio_fkey FOREIGN KEY (id_servicio) REFERENCES public.servicios(id_servicio);


--
-- TOC entry 5016 (class 2606 OID 16449)
-- Name: citas fk_citas_especialista; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.citas
    ADD CONSTRAINT fk_citas_especialista FOREIGN KEY (id_especialista) REFERENCES public.especialistas(id_especialista);


--
-- TOC entry 5017 (class 2606 OID 16459)
-- Name: citas fk_citas_horario; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.citas
    ADD CONSTRAINT fk_citas_horario FOREIGN KEY (id_horario) REFERENCES public.horarios(id_horario);


--
-- TOC entry 5018 (class 2606 OID 16454)
-- Name: citas fk_citas_servicio; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.citas
    ADD CONSTRAINT fk_citas_servicio FOREIGN KEY (id_servicio) REFERENCES public.servicios(id_servicio);


--
-- TOC entry 5019 (class 2606 OID 16444)
-- Name: citas fk_citas_usuario; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.citas
    ADD CONSTRAINT fk_citas_usuario FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id_usuario);


--
-- TOC entry 5023 (class 2606 OID 16531)
-- Name: detalle_venta_producto fk_detalle_producto; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.detalle_venta_producto
    ADD CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto) REFERENCES public.productos(id_producto);


--
-- TOC entry 5024 (class 2606 OID 16526)
-- Name: detalle_venta_producto fk_detalle_venta; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.detalle_venta_producto
    ADD CONSTRAINT fk_detalle_venta FOREIGN KEY (id_venta) REFERENCES public.ventas(id_venta);


--
-- TOC entry 5014 (class 2606 OID 16430)
-- Name: horarios fk_horarios_especialista; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.horarios
    ADD CONSTRAINT fk_horarios_especialista FOREIGN KEY (id_especialista) REFERENCES public.especialistas(id_especialista);


--
-- TOC entry 5015 (class 2606 OID 16611)
-- Name: horarios fk_horarios_servicio; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.horarios
    ADD CONSTRAINT fk_horarios_servicio FOREIGN KEY (id_servicio) REFERENCES public.servicios(id_servicio);


--
-- TOC entry 5020 (class 2606 OID 16506)
-- Name: ventas fk_ventas_cita; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ventas
    ADD CONSTRAINT fk_ventas_cita FOREIGN KEY (id_cita) REFERENCES public.citas(id_cita);


--
-- TOC entry 5021 (class 2606 OID 16501)
-- Name: ventas fk_ventas_cliente; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ventas
    ADD CONSTRAINT fk_ventas_cliente FOREIGN KEY (id_cliente) REFERENCES public.usuarios(id_usuario);


--
-- TOC entry 5022 (class 2606 OID 16511)
-- Name: ventas fk_ventas_promocion; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ventas
    ADD CONSTRAINT fk_ventas_promocion FOREIGN KEY (id_promocion) REFERENCES public.promociones(id_promocion);


-- Completed on 2026-11-09 18:36:09

--
-- PostgreSQL database dump complete
--

\unrestrict 1WRMSFZLbyrW9b9Hs0GbgiRT9JlO0csZoZCMnsl9mLkfLiHihfDQKzbU8qKVUia

