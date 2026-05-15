# 1. Análisis profesional de la idea

## Problema que resuelve

La app resuelve un problema operativo concreto: recordar tomas diarias de medicación crónica o de larga duración, confirmar si se tomaron, registrar olvidos y controlar el stock disponible para anticipar compras.

No resuelve diagnóstico, pauta terapéutica, ajuste de dosis ni seguimiento clínico. Ese límite debe mantenerse para no convertir el producto en una herramienta de decisión médica.

## Usuario objetivo

Usuario individual que toma medicación diaria y necesita:

- Recordatorios fiables.
- Confirmación inmediata o diferida.
- Registro de adherencia.
- Control de unidades restantes.
- Exportación de historial para revisión personal o para mostrarlo a un profesional sanitario si lo desea.

## Riesgos principales

1. **Riesgo técnico:** Android puede retrasar o bloquear alarmas si faltan permisos, si el fabricante aplica ahorro agresivo de batería o si el dispositivo se reinicia y no se reprograman alarmas.
2. **Riesgo de salud:** el usuario podría interpretar la app como criterio médico. Se evita con avisos claros y alcance limitado.
3. **Riesgo de datos:** medicamentos y adherencia son datos sensibles. Se reduce guardando todo localmente y sin backend.
4. **Riesgo de stock incorrecto:** si el usuario toma una pastilla sin confirmarla o corrige manualmente mal, el stock puede no coincidir. Se mitiga con ajuste manual e historial de eventos.
5. **Riesgo de eliminación accidental:** se mitiga con confirmación antes de acciones destructivas.

## Supuestos

- El usuario utiliza Android.
- El usuario puede instalar APK fuera de Play Store.
- El usuario concederá permisos de notificación y alarmas.
- El usuario aceptará excluir la app de ahorro de batería si su fabricante restringe alarmas.
- El uso será personal, local y privado.

## Mejoras recomendadas incorporadas al MVP

- Onboarding de advertencia legal.
- Modelo de estados robusto para tomas.
- Stock descontado únicamente al confirmar.
- Eventos de stock separados del medicamento.
- Exportación CSV para historial e JSON para backup.
- Reschedule al reiniciar, cambiar hora/zona horaria o actualizar la app.
- Registro de posposiciones.
- Umbrales configurables.

## Alcance MVP

Incluido:

- Medicamentos CRUD.
- Pausar/reactivar.
- Alarmas locales.
- Check configurable.
- Historial.
- Stock.
- Export/import.
- Ajustes.
- Textos legales base.

No incluido en MVP:

- Sincronización nube.
- Login.
- Usuarios múltiples.
- Aviso a familiares.
- PDF para médico.
- OCR/reconocimiento de cajas.
- Widget Android.

## Funciones futuras

- Backup cifrado con clave local.
- Sincronización opcional entre dispositivos.
- Widget Android.
- Modo cuidador/familiar.
- Aviso si se acumulan olvidos.
- Gráficas de adherencia.
- Exportación PDF.
- Reconocimiento de caja por cámara.
- Calendario integrado.

# 2. Especificación funcional completa

## Medicamentos

### Campos

- id.
- nombre.
- unidades_por_caja.
- stock_actual.
- horas_toma.
- activo.
- notas.
- fecha_creacion.
- fecha_actualizacion.

### Reglas

- Nombre obligatorio.
- Unidades por caja > 0.
- Stock >= 0.
- Debe existir al menos una hora.
- Las horas deben cumplir `HH:mm`.
- Pausar medicamento impide nuevas alarmas futuras, pero no borra historial.
- Eliminar medicamento borra historial asociado por cascada.

### Criterios de aceptación

- Se puede crear Clopidogrel, Ezetimiba y Dercutane con los datos iniciales.
- Se puede editar stock, caja, nombre, notas y horas.
- Un medicamento pausado no genera nuevas alarmas.
- Un medicamento reactivado vuelve a programar alarmas.

## Recordatorios

### Reglas

- Cada medicamento y hora genera una alarma local.
- Varios medicamentos pueden compartir hora.
- La alarma muestra nombre y hora programada.
- La alarma permite abrir la app, confirmar o posponer 10 minutos desde notificación.
- Desde la app se puede elegir libremente el tiempo de posposición.
- Tras sonar la alarma se programa el check con retraso configurable.
- Tras reinicio/cambio de hora/zona horaria se reprograman alarmas activas.

### Criterios de aceptación

- Si dos medicamentos tienen 14:57, ambos tienen evento propio.
- Si se pospone, queda registrada la posposición.
- El check vuelve a programarse tras posposición.

## Confirmación mediante check

### Reglas

- Retraso por defecto: 2 minutos.
- Editable desde ajustes.
- No se descuenta stock al sonar la alarma.
- Solo se descuenta stock al confirmar toma.
- Confirmar toma guarda fecha, hora programada, hora real, medicamento, estado y stock restante.
- Marcar no tomada guarda olvido sin descontar stock.

### Estados

- PROGRAMADA.
- ALARMA_SONADA.
- POSPUESTA.
- CHECK_PENDIENTE.
- TOMADA.
- OLVIDADA_NO_TOMADA.

## Historial

### Campos

- Fecha.
- Medicamento.
- Hora programada.
- Hora de alarma.
- Hora de check.
- Hora de confirmación.
- Estado.
- Número de posposiciones.
- Stock restante.
- Observaciones.

### Filtros MVP

- Todo.
- Hoy.
- Tomadas.
- Olvidadas.

### Exportación

- CSV para análisis sencillo.
- JSON para backup completo.

## Stock

### Reglas

- Stock actual se guarda en medicamento.
- Cada movimiento queda registrado en stock_events.
- Confirmación de toma crea evento `DESCUENTO_POR_TOMA`.
- Añadir caja crea evento `REPOSICION_CAJA`.
- Ajuste manual crea evento `AJUSTE_MANUAL`.
- Días restantes = stock actual / número de tomas diarias.
- Stock bajo si días restantes <= umbral.

## Ajustes

- Minutos hasta check.
- Días de aviso stock.
- Notificaciones activadas.
- Exportar backup.
- Importar backup.
- Reinicio fuerte de datos.
- Acceso a ajustes de alarmas exactas y batería.

# 3. Diseño UX/UI

## Arquitectura de pantallas

1. Inicio.
2. Medicamentos.
3. Añadir/editar medicamento.
4. Historial.
5. Stock.
6. Ajustes.
7. Modal de confirmación de toma.
8. Modal de eliminación/reinicio.

## Flujo principal

1. Usuario abre Inicio.
2. Ve próxima toma y tomas pendientes.
3. Al sonar alarma, recibe notificación.
4. Dos minutos después recibe check.
5. Confirma, pospone o marca no tomada.
6. La app registra historial y actualiza stock.

## Jerarquía visual

- Inicio: próxima toma como elemento principal.
- Acciones grandes: confirmar, posponer, historial, stock.
- Colores tranquilos: verde salud, fondo claro, rojo solo para errores o stock bajo.
- Textos claros: evitar tecnicismos médicos.

## Accesibilidad

- Botones grandes.
- Alto contraste razonable.
- Formato 24 h.
- Estados escritos, no solo por color.
- Avisos no alarmistas.
- Evitar bloques de texto densos salvo legales.

# 4. Arquitectura técnica

## Tecnología recomendada

Android nativo en Kotlin.

Justificación:

- Mejor integración con AlarmManager.
- Mejor control de permisos Android.
- APK instalable directamente.
- Datos locales robustos con Room.
- Menos dependencias que Flutter/React Native.

## Persistencia

Room/SQLite local.

Tablas:

- medications.
- intake_schedules.
- stock_events.
- settings.

## Alarmas

- AlarmManager.
- `setExactAndAllowWhileIdle()` si el permiso está concedido.
- Fallback a `setAndAllowWhileIdle()` si no lo está.
- Receivers para alarma, check, acciones de notificación y reprogramación.

## Notificaciones

Canales:

- Recordatorios de medicación.
- Avisos de stock.

Acciones:

- Confirmar.
- Posponer 10 minutos.
- Marcar no tomada en check.
- Abrir app para posposición personalizada.

## Seguridad y privacidad

- Datos locales.
- Sin servidor.
- Sin analítica externa.
- Sin permisos de almacenamiento general.
- Exportación iniciada por el usuario.
- Borrado local completo.
- `allowBackup=false` para evitar copias automáticas no controladas.

## Backups

- Export JSON completo.
- Import JSON desde selector de documentos.
- CSV para historial.

## Casos límite

- Permiso de notificación denegado: no aparecerán avisos aunque existan alarmas internas.
- Permiso de alarma exacta denegado: las alarmas pueden retrasarse.
- Ahorro de batería agresivo: el fabricante puede retrasar procesos.
- Reinicio del móvil: se reprograma mediante BOOT_COMPLETED, pero el usuario debe haber abierto la app al menos una vez tras instalación.
- Cambio de zona horaria/hora: se reprograman alarmas.
- Stock 0: confirmar no genera negativo; queda en 0 y registra evento.
