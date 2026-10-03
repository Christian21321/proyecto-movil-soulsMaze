---
name: ux-accesibilidad
description: "Usa esta skill cuando disenes o evalues la experiencia de usuario y accesibilidad de interfaces POS, kioskos o paneles de administracion. Cubre WCAG 2.1, heuristicas de Nielsen, diseno tactil, wireframes y prototipado."
---

# UX y Accesibilidad para POS y Kioskos Interactivos

## Diseno para POS

### Contexto de uso
- **Alta velocidad**: las transacciones deben completarse en segundos. Cada segundo extra en una orden acumula demoras en horas pico.
- **Ruido y distracciones**: entorno sonoro alto (cocina, clientes, timbres). Las interfaces deben priorizar legibilidad y respuesta visual clara por sobre el audio.
- **Estres y fatiga del operador**: turnos largos, presion por rapidez. Disenar para reducir carga cognitiva: pocos clics por accion, botones grandes, flujos lineales.
- **Pantallas tactiles resistivas o capacitivas**: calibratedas para uso intensivo, a menudo con sensibilidad reducida por suciedad o grasa. Los blancos de toque deben ser generosos.

## Heuristicas de Nielsen aplicadas a POS

| Heuristica | Aplicacion en POS |
|---|---|
| **Visibilidad del estado del sistema** | Indicar siempre que un item fue anadido al ticket, cuando el pago esta procesando, cuando se imprime el ticket. Usar spinners, cambios de color, checkmarks. |
| **Consistencia y estandares** | Botones de accion siempre en la misma posicion. Codigo de colores uniforme (verde = confirmar, rojo = cancelar/eliminar, amarillo = advertencia). Iconografia coherente. |
| **Prevencion de errores** | Confirmacion antes de eliminar items del ticket. Advertencia si se intenta cobrar un ticket vacio. Bloquear botones de pago hasta que todos los items esten registrados. |
| **Reconocimiento sobre recuerdo** | Categorias visibles con iconos + texto. Busqueda de productos con autocompletado. No obligar al cajero a memorizar codigos. |
| **Flexibilidad y eficiencia de uso** | Atajos de teclado para acciones frecuentes. Acceso rapido a productos mas vendidos. Permitir personalizacion de la pantalla de inicio. |
| **Diseno estetico y minimalista** | Solo la informacion necesaria para la transaccion actual. Ocultar opciones administrativas durante el flujo de venta. |
| **Ayuda y documentacion** | Tooltips en iconos criticos. Numero de soporte visible en pantalla de error. Manual de referencia accesible desde el menu. |

## WCAG 2.1 Niveles A y AA

### Contraste de color (AA)
- **Relacion de contraste minima**: 4.5:1 para texto normal, 3:1 para texto grande (>= 18px o >= 14px bold).
- Componentes de interfaz y objetos graficos deben tener relacion 3:1 minimo.
- No transmitir informacion solo con color (ej: errores deben incluir icono + texto).

### Target sizes (AA)
- Blancos de toque de al menos **44x44px** (WCAG 2.5.5).
- Excepcion: si el blanco es parte de una cadena de texto o el tamano esta determinado por el agente de usuario.

### Navegacion por teclado (A)
- Todas las funcionalidades deben ser accesibles solo con teclado.
- Orden de tabulacion logico (izquierda a derecha, arriba a abajo).
- Foco visible en todo momento (outline o indicador claro).
- Sin trampas de foco (focus trap solo en modales con mecanismo de cierre).

### Soporte de lectores de pantalla (A)
- Alternativas textuales para todo contenido no textual (imagenes, iconos).
- Encabezados semanticos (h1-h6) para estructurar la pagina.
- Landmarks ARIA (banner, navigation, main, complementary, contentinfo).
- Anuncios de cambios dinamicos via `aria-live` regions.

## Diseno Tactil

### Areas de toque amplias
- Botones de al menos 48x48dp (recomendacion Material Design), preferiblemente 56x56dp para uso en POS.
- Espaciado minimo de 8dp entre botones adyacentes para evitar pulsaciones accidentales.
- Zonas de toque seguras: bordes inferiores de la pantalla (donde descansan los dedos naturalmente).

### Gestos intuitivos
- **Swipe** para eliminar items del ticket (con confirmacion).
- **Tap** largo (long-press) para ver detalles del producto o editar cantidad.
- **Pinch-to-zoom** inhabilitado en pantallas de transaccion para evitar gestos accidentales.
- **Drag and drop** evitar en contexto de alta velocidad; preferir botones de anadir/remover.

### Feedback tactil/visual
- **Haptic feedback** en dispositivos compatibles para confirmar acciones criticas (pago exitoso, eliminacion de item).
- **Cambio visual inmediato**: boton se hunde (sombra) o cambia de color al presionar.
- **Animacion de transicion** sutil (< 200ms) para mostrar cambios de estado (ej: producto agregado al ticket).
- **Feedback sonoro opcional**: tono corto al registrar item, tono diferente al completar venta.

### Evitar hover-dependent interactions
- Las pantallas tactiles no tienen hover. Toda informacion debe ser accesible via tap o tap largo.
- Tooltips deben aparecer al tap largo (long-press), no al hover.
- Menus desplegables deben usar tap para abrir/cerrar, no hover.

## User Flows para POS

### 1. Seleccion de productos
```
Inicio -> Categoria -> Producto -> Cantidad -> Personalizacion (opcional) -> Anadir al ticket
```
- Pantalla dividida: categorias a la izquierda, productos a la derecha.
- Busqueda por nombre o codigo de barras (integracion con escaner).
- Productos mas vendidos en pestana "Rapido" o "Favoritos".

### 2. Personalizacion
```
Producto anadido -> Modal de personalizacion -> Opciones (tamano, ingredientes extras, sin algo) -> Confirmar
```
- Radio buttons para opciones mutuamente excluyentes (tamano).
- Checkboxes para extras (queso extra, sin cebolla).
- Precio actualizado en tiempo real.

### 3. Pago (efectivo/tarjeta)
```
Ticket completo -> Seleccionar metodo de pago -> Procesar -> Confirmacion
```
- **Efectivo**: mostrar total, ingresar monto recibido, calcular cambio automaticamente.
- **Tarjeta**: integracion con POS externo, mostrar pantalla de "Inserte/acerque tarjeta", feedback de exito/fallo.
- **Split payment**: permitir dividir entre efectivo y tarjeta.
- **Propina**: opcion de agregar propina antes del pago (botones de porcentaje: 0%, 10%, 15%, 20%, personalizado).

### 4. Emision de ticket
```
Pago confirmado -> Opciones de ticket -> Imprimir / Enviar por SMS/Email -> Finalizar venta
```
- Vista previa del ticket antes de imprimir.
- Opcion de reimpresion desde el historial.
- Ticket electronico con QR para seguimiento de orden.

### 5. Cierre de caja
```
Menu admin -> Cierre de caja -> Resumen de ventas -> Corte -> Imprimir reporte
```
- Total de ventas, metodos de pago, cantidad de transacciones.
- Diferencia entre efectivo esperado y contado.
- Exportacion de reporte a PDF/CSV.

## Wireframes y Prototipado

### Herramientas
| Herramienta | Uso recomendado |
|---|---|
| **Figma** | Prototipos de alta fidelidad, componentes reutilizables, colaboracion en tiempo real, handoff a frontend-dev. |
| **Balsamiq** | Wireframes de baja fidelidad, ideacion rapida, sketches en blanco y negro. |
| **Adobe XD** | Prototipado con animaciones, voice prototyping. |
| **Penpot** | Alternativa open-source a Figma. |

### Niveles de fidelidad
1. **Baja fidelidad (Balsamiq / lapiz y papel)**: estructura basica, layout, flujo de pantallas. Sin color ni detalles. Ideal para primeras iteraciones.
2. **Media fidelidad (Figma en escala de grises)**: wireframes con dimensiones reales, tipografia placeholder, navegacion entre pantallas.
3. **Alta fidelidad (Figma a color)**: diseno final con colores de marca, iconografia, tipografia, interacciones animadas. Util para pruebas de usabilidad y handoff.

### Pruebas con usuarios
- **Pruebas de usabilidad**: 5-8 cajeros reales por ronda de testing.
- **Tareas tipicas**: agregar un combo, aplicar descuento, procesar pago en efectivo, reembolsar un item.
- **Metricas**: tiempo por tarea, tasa de error, clics por transaccion, satisfaccion (SUS - System Usability Scale).
- **A/B testing**: probar dos variantes de un flujo critico (ej: seleccion de personalizacion).

## Arquitectura de Informacion

### Categorizacion de productos (comida rapida)
```
- Combos / Promociones
- Hamburguesas
- Pollo
- Papas / Acompanantes
- Bebidas
- Postres
- Infantil
- Extras / Salsas
```
- Cada categoria con icono representativo.
- Subcategorias solo si es necesario (ej: Bebidas -> Calientes / Frias / Cerveza).
- Productos visibles como tarjetas con imagen, nombre y precio.

### Busqueda y filtros
- Busqueda por nombre, codigo SKU o ingrediente.
- Autocompletado con soporte para typos (fuzzy search).
- Filtros rapidos: mas vendidos, en promocion, nuevos, vegetarianos/veganos.
- Resultados de busqueda en tiempo real (< 100ms de respuesta).

### Navegacion
- **Navegacion principal**: barra lateral izquierda con categorias (icono + texto).
- **Breadcrumbs**: visibles en pantallas de personalizacion (Categoria > Producto > Personalizacion).
- **Navegacion contextual**: boton "Volver a menu" desde cualquier pantalla.

## Investigacion UX

### Entrevistas con cajeros
- Preguntas clave: "Cual es la parte mas frustrante del sistema actual?", "Que accion repites mas veces al dia?", "Como manejas un error en el cobro?".
- Documentar pain points y workarounds que los cajeros han desarrollado.

### Observacion en campo
- Registrar sesiones de 2-3 horas en horario pico (12:00-14:00, 20:00-22:00).
- Mapear movimientos: numero de toques por transaccion, tiempos de espera, momentos de confusion.
- Identificar tareas de alta frecuencia para priorizar en el diseno.

### Task Analysis
- Descomponer cada flujo critico en pasos atomicos.
- Identificar cuellos de botella (ej: buscar un producto requiere 5+ pasos).
- Propuesta de flujo optimizado con reduccion de pasos.

## Accesibilidad

### ARIA labels y roles
- Todos los botones e iconos funcionales deben tener `aria-label` descriptivo.
- Roles ARIA correctos: `button`, `tab`, `tabpanel`, `dialog`, `alertdialog`, `progressbar`.
- Estados: `aria-disabled`, `aria-expanded`, `aria-selected`, `aria-pressed`.
- Relaciones: `aria-labelledby`, `aria-describedby`, `aria-controls`.

### Foco visible
- Outline de 2-3px de color contrastante (no solo cambio de color).
- Skip link al inicio de la pagina para saltar navegacion repetitiva.
- Gestion de foco en modales: atrapar foco dentro del modal, restaurar foco al cerrar.
- No usar `outline: none` sin proporcionar alternativa visible.

### Skip navigation
- Enlace "Saltar al contenido" visible al recibir foco por teclado (primer elemento tabulable).
- En paginas POS: "Saltar al ticket", "Saltar al catalogo".

### Anuncios para lectores de pantalla
- `aria-live="polite"` para cambios no urgentes (ej: total actualizado).
- `aria-live="assertive"` para errores criticos (ej: "Tarjeta rechazada").
- `role="alert"` en mensajes de error del sistema.
- Notificaciones de estado (ej: "Item anadido al ticket") usando `role="status"`.

### Consideraciones adicionales
- Modo de alto contraste para entornos con mucha iluminacion.
- Tamano de fuente ajustable sin romper el layout (hasta 200%).
- No depender exclusivamente del color para transmitir informacion.
- Tiempo de sesion configurable para evitar timeouts durante transacciones en progreso.

## Trabajo conjunto con frontend-dev

### Proceso de handoff
- Componentes disenados en Figma con especificaciones: dimensiones, colores, tipografia, estados (default, hover, active, disabled, focus, error).
- Tokens de diseno compartidos: colores, espaciados, tipografia, sombras, radios de borde.
- Documentacion de interacciones: animaciones, transiciones, micro-interacciones.

### Revision de implementacion
- Verificar que el diseno se implemente fielmente (pixel perfect).
- Probar accesibilidad del componente implementado (lector de pantalla, teclado, contraste).
- Ajustar tiempos de animacion, estados de carga, manejo de errores.
- Iterar: revision semanal de componentes implementados vs diseno.

### Colaboracion continua
- Participar en code reviews de componentes UI para garantizar consistencia.
- Mantener un sistema de diseno vivo (design tokens + component library).
- Documentar decisiones de diseno y justificaciones de accesibilidad en cada componente.
