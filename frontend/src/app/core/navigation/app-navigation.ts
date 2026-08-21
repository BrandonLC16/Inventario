export const APP_ROLES = ['ADMIN', 'INVENTORY_MANAGER', 'SALES'] as const;

export type AppRole = (typeof APP_ROLES)[number];

export const ROLE_LABELS: Readonly<Record<AppRole, string>> = {
  ADMIN: 'Administración',
  INVENTORY_MANAGER: 'Gestión de inventario',
  SALES: 'Ventas',
};

export type NavigationGroup = 'operation' | 'supply' | 'sales' | 'administration' | 'account';

export interface AppSection {
  readonly id: string;
  readonly path: string;
  readonly label: string;
  readonly eyebrow: string;
  readonly description: string;
  readonly group: NavigationGroup;
  readonly roles: readonly AppRole[];
}

export interface NavigationGroupDefinition {
  readonly id: NavigationGroup;
  readonly label: string;
}

const ALL_ROLES: readonly AppRole[] = APP_ROLES;
export const INVENTORY_MANAGEMENT_ROLES: readonly AppRole[] = ['ADMIN', 'INVENTORY_MANAGER'];
const INVENTORY_ROLES = INVENTORY_MANAGEMENT_ROLES;
const SALES_ROLES: readonly AppRole[] = ['ADMIN', 'SALES'];
const ADMIN_ROLES: readonly AppRole[] = ['ADMIN'];

export const NAVIGATION_GROUPS: readonly NavigationGroupDefinition[] = [
  { id: 'operation', label: 'Operación' },
  { id: 'supply', label: 'Abastecimiento' },
  { id: 'sales', label: 'Ventas' },
  { id: 'administration', label: 'Administración' },
  { id: 'account', label: 'Cuenta' },
];

export const APP_SECTIONS = {
  dashboard: {
    id: 'dashboard',
    path: 'dashboard',
    label: 'Resumen',
    eyebrow: 'Panel principal',
    description: 'Indicadores y actividad relevante según las capacidades del rol activo.',
    group: 'operation',
    roles: ALL_ROLES,
  },
  products: {
    id: 'products',
    path: 'products',
    label: 'Productos',
    eyebrow: 'Catálogo',
    description: 'Consulta del catálogo de productos y sus datos generales.',
    group: 'operation',
    roles: ALL_ROLES,
  },
  warehouses: {
    id: 'warehouses',
    path: 'warehouses',
    label: 'Almacenes',
    eyebrow: 'Ubicaciones',
    description: 'Consulta de almacenes y acceso a sus existencias.',
    group: 'operation',
    roles: ALL_ROLES,
  },
  inventory: {
    id: 'inventory',
    path: 'inventory',
    label: 'Inventario',
    eyebrow: 'Existencias',
    description: 'Vista de existencias físicas, reservadas, disponibles y en tránsito.',
    group: 'operation',
    roles: ALL_ROLES,
  },
  suppliers: {
    id: 'suppliers',
    path: 'suppliers',
    label: 'Proveedores',
    eyebrow: 'Abastecimiento',
    description: 'Directorio de proveedores y asociaciones con productos.',
    group: 'supply',
    roles: INVENTORY_ROLES,
  },
  purchases: {
    id: 'purchases',
    path: 'purchase-orders',
    label: 'Compras',
    eyebrow: 'Órdenes de compra',
    description: 'Seguimiento de órdenes, recepciones y pendientes de compra.',
    group: 'supply',
    roles: INVENTORY_ROLES,
  },
  transfers: {
    id: 'transfers',
    path: 'inventory-transfers',
    label: 'Transferencias',
    eyebrow: 'Movimiento entre almacenes',
    description: 'Control de borradores, tránsito, recepción y cancelaciones.',
    group: 'supply',
    roles: INVENTORY_ROLES,
  },
  inventoryCounts: {
    id: 'inventory-counts',
    path: 'inventory-counts',
    label: 'Conteos físicos',
    eyebrow: 'Conciliación',
    description: 'Captura y seguimiento de conteos físicos de inventario.',
    group: 'supply',
    roles: INVENTORY_ROLES,
  },
  customers: {
    id: 'customers',
    path: 'customers',
    label: 'Clientes',
    eyebrow: 'Ventas',
    description: 'Consulta y mantenimiento del directorio de clientes.',
    group: 'sales',
    roles: SALES_ROLES,
  },
  orders: {
    id: 'orders',
    path: 'orders',
    label: 'Pedidos',
    eyebrow: 'Ciclo de venta',
    description: 'Seguimiento de pedidos, reservas y confirmaciones.',
    group: 'sales',
    roles: SALES_ROLES,
  },
  users: {
    id: 'users',
    path: 'admin/users',
    label: 'Usuarios',
    eyebrow: 'Administración',
    description: 'Administración de usuarios, roles, estado y sesiones.',
    group: 'administration',
    roles: ADMIN_ROLES,
  },
  profile: {
    id: 'profile',
    path: 'profile',
    label: 'Perfil',
    eyebrow: 'Cuenta',
    description: 'Consulta de identidad y punto de entrada para la contraseña propia.',
    group: 'account',
    roles: ALL_ROLES,
  },
} as const satisfies Readonly<Record<string, AppSection>>;

export const NAVIGATION_SECTIONS: readonly AppSection[] = Object.values(APP_SECTIONS);

export const APP_SECTION_DATA_KEY = 'appSection';
export const BREADCRUMB_DATA_KEY = 'breadcrumb';
export const ALLOWED_ROLES_DATA_KEY = 'allowedRoles';

export function canAccessSection(roles: readonly AppRole[], section: AppSection): boolean {
  return roles.some((role) => section.roles.includes(role));
}

export function isAppRole(value: string): value is AppRole {
  return APP_ROLES.some((role) => role === value);
}
