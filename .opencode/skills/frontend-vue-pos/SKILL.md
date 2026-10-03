---
name: frontend-vue-pos
description: "Usa esta skill cuando desarrolles interfaces de usuario para sistemas POS, kioskos interactivos o paneles de restaurante con Vue 3. Cubre Composition API, TypeScript, Vite, Tailwind CSS v4 y UX para entornos de alta velocidad."
---

# Skill: frontend-vue-pos

## Vue 3 + Composition API + TypeScript

Usa siempre `<script setup lang="ts">` con `defineProps` y `defineEmits` tipados. Los componentes deben tener una unica responsabilidad. Para `v-model` personalizado usa `defineModel`.

Estructura por capas:

- `components/` -- atomicos, moleculas, organismos
- `views/` -- paginas completas (orden, pago, historial)
- `composables/` -- logica reactiva reutilizable
- `stores/` -- estado global con Pinia
- `types/` -- interfaces compartidas
- `services/` -- llamadas a API

### Props tipadas

```ts
interface ProductCardProps {
  product: Product
  selected?: boolean
  quantity?: number
}

const props = defineProps<ProductCardProps>()
const emit = defineEmits<{
  select: [product: Product]
  increment: []
  decrement: []
}>()
```

## Vite -- configuracion optima para POS

- `build.target`: `'es2020'` para navegadores modernos en kioskos
- `build.rollupOptions.output.manualChunks`: separa vendor (vue, pinia, etc.) de logica de negocio
- `server.hmr.overlay`: `false` en produccion para evitar pantallazos en kioskos
- `define` para inyectar variables de entorno: `__API_URL__`, `__TENANT_ID__`

Ejemplo conceptual:

```ts
// vite.config.ts
export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: { target: 'es2020', sourcemap: false },
  server: { port: 3000, strictPort: true }
})
```

## Tailwind CSS v4 -- diseno para pantallas tactiles

Estrategia de diseno:

- Clases utilitarias para espaciado y tamano. Sin componentes CSS personalizados.
- `min-h-screen` para ocupar viewport completo sin scroll horizontal.
- Breakpoints: `sm` (640px), `md` (768px), `lg` (1024px). POS tipicamente en tabletas (768-1024px).
- Botones grandes: `min-h-[44px] min-w-[44px]` y `text-lg` o `text-xl`.
- Grid de productos: `grid grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2`.
- Tarjetas de producto con `active:scale-95` para feedback tactil.
- Uso de `@variant` para estado `disabled` y `selected` en lugar de clases condicionales largas.

### Ejemplo de grid de productos

```html
<div class="grid grid-cols-3 md:grid-cols-4 gap-2 p-2">
  <button
    v-for="product in products"
    :key="product.id"
    class="flex flex-col items-center justify-center rounded-xl border p-3
           active:scale-95 transition-transform"
    @click="select(product)"
  >
    <img :src="product.image" class="w-12 h-12 object-contain" />
    <span class="text-sm font-medium mt-1">{{ product.name }}</span>
    <span class="text-xs text-gray-500">${{ product.price }}</span>
  </button>
</div>
```

## Componentes tipicos de POS

### Selector de productos

Renderiza una grilla de botones con imagen, nombre y precio. Soporta busqueda por nombre y filtro por categoria. Usa `computed` con `watch` debounced para la busqueda.

Propiedades: `products: Product[]`, `columns?: number`, `searchable?: boolean`.

### Carrito de orden

Sidebar o panel inferior (segun viewport) con lista de items seleccionados. Cada item muestra nombre, cantidad, precio unitario, subtotal. Botones `+`/`-` para modificar cantidad y un boton para eliminar.

Emite eventos: `update:quantity`, `remove`, `clear`.

### Teclado numerico

Componente independiente para entrada de cantidad, precio personalizado o PIN. Layout 3x4 con digitos 0-9, punto decimal, borrar y confirmar. Cada tecla mide 48x48px minimo. Propaga entrada via `defineModel`.

### Panel de pago

Muestra resumen de orden (subtotal, impuesto, total), metodos de pago (efectivo, tarjeta, credito, app) y boton de confirmacion. El metodo seleccionado se resalta con `ring-2 ring-blue-500`.

Estados: `idle`, `processing`, `success`, `error`. Usa una maquina de estados simple con `ref` y `computed`.

### Ticket / recibo

Vista previa del ticket antes de imprimir. Muestra encabezado del establecimiento, linea de items, totales, metodo de pago, numero de orden y hora. Disenado para impresion termica 58mm/80mm: fuente mono, sin margenes, ancho fijo.

## Estado global con Pinia

Store central `useOrderStore`:

- `items: OrderItem[]`
- `customer?: Customer`
- `paymentMethod?: PaymentMethod`
- Acciones: `addItem`, `removeItem`, `updateQuantity`, `setPaymentMethod`, `clearOrder`
- Getters: `subtotal`, `tax`, `total`, `itemCount`

Stores secundarios: `useProductStore` (catalogo), `useUIStore` (sidebar abierta/cerrada, modal activo).

## Llamadas a API REST

Servicio centralizado con `axios` en `services/api.ts`:

- Instancia con `baseURL` desde variable de entorno
- Interceptor para adjuntar token de autenticacion
- Interceptor para manejo global de errores (timeout, 401, 500)

Endpoints tipicos para POS:

- `GET /api/products` -- catalogo
- `GET /api/products/:id` -- detalle
- `POST /api/orders` -- crear orden
- `GET /api/orders/:id` -- estado de orden
- `POST /api/payments` -- procesar pago
- `GET /api/customers/:id` -- obtener cliente

## Accesibilidad tactil

- Todos los elementos interactivos: `min-w-[44px] min-h-[44px]` (target size WCAG 2.5.5).
- Contraste de color minimo 4.5:1 para texto normal, 3:1 para texto grande.
- Feedback visual inmediato en cada interaccion: `active:scale-95`, `transition-colors`, `hover:bg-gray-100`.
- Estados focus visibles con `focus-visible:ring-2 focus-visible:ring-blue-400`.
- Atributo `aria-label` en botones sin texto visible.
- `role="button"` y `tabindex="0"` en elementos clickeables que no sean `<button>`.
- Eventos tactiles: usar `@click` nativo (cubre mouse y touch), evitar `@touchstart` duplicados.
- Contenido no interactivo no debe estar en orden de tabulacion (`tabindex="-1"`).

## Lo que NO hace esta skill

- No implementa logica de backend ni base de datos.
- No disena sistemas de autenticacion avanzados (solo interceptor basico).
- No cubre testing unitario ni e2e.
- No define diseno grafico ni paletas de color especificas.

## Colaboracion

Este skill se coordina con el agente de logica de negocio para definir contratos de API y modelos de datos. Si se requiere personalizacion de Tailwind, coordinar con el agente de diseno.
