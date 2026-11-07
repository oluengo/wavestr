import type { Shared } from '../lib/share'
/** Lo recibido por enlace, pendiente de revisar. */
export const incoming = $state<{ shared: Shared | null }>({ shared: null })
