# Cambios

## 0.5.0

- **Tablets y móvil en horizontal:** navegación lateral; en pantallas anchas, el reproductor queda fijo a la derecha con el chat y los zaps.
- **Emisoras parecidas** debajo del reproductor de radio, por estilo.
- **Botón sorpresa** (el dado, arriba en Directos, Radios y Favoritos): radio, directo o ambos; de tus favoritos o de todo. Recuerda tu elección.
- **Relays de Wavestr editables** en Ajustes → Relays: añade los que quieras (p. ej. los de una plataforma de directos), quítalos o vuelve a los de serie. Tu lista NIP-65 se muestra pero no se toca.

## 0.4.0

- **Radiostr pasa a llamarse Wavestr** (el nombre ya existía). Nuevo logo: un anillo atravesado por una onda. Es otra app para Android: instala Wavestr y, si tenías Radiostr, exporta antes tus favoritos (Ajustes → Datos y copias) e impórtalos en Wavestr.
- **Favoritos en Nostr:** tras entrar con Amber, la app pregunta si guardarlos en Nostr, cifrados para que solo tú los leas. Se sincronizan solos y se recuperan en otro móvil con tu cuenta (Ajustes → Favoritos en Nostr).
- **Violeta** como color de acento por defecto (también en el icono).

## 0.3.0

- **Zaps con NWC:** conecta tu cartera (Nostr Wallet Connect) en Ajustes → Zaps y envía zaps a los directos desde el reproductor. Con Amber, el zap va firmado por ti; sin sesión, es anónimo. Sin cartera conectada, se abre tu app Lightning o se copia la factura.
- **Límite mensual de zaps** e importe por defecto. La conexión NWC se guarda cifrada con el almacén de claves de Android y nunca entra en las copias.
- **Chat y zaps del directo** visibles en el reproductor (solo lectura de momento), con el total de sats.
- **Carátulas de las radios:** si el directorio no trae logo, se busca en la web de la emisora (icono, apple-touch-icon, og:image) y se guarda.
- **Exportar e importar favoritos:** copia JSON completa (carpetas incluidas) o lista M3U; al importar, se combina con lo que ya tienes.
- **Color de acento** a elegir: verde, violeta, coral, azul o frambuesa.

## 0.2.0

- **Tu perfil de Nostr:** tu avatar arriba en cada pestaña; al tocarlo, tu nombre, NIP-05, seguidos, relays, npub y dirección Lightning. Wavestr lo lee, no lo modifica.
- **Radio online** con el directorio libre Radio Browser: populares, por país (por defecto el del móvil), por estilo y búsqueda.
- Las emisoras con varias URLs se agrupan y su **calidad** se elige en el reproductor (Auto: la mejor con wifi, la más ligera con datos).
- **Canción que suena** (metadatos ICY) y aviso cuando una emisora no responde, con opción de probar otro formato.
- **Importar PLS/M3U:** pegar una URL, un enlace a la lista o su contenido, o elegir un archivo.
- **Favoritos en carpetas y subcarpetas:** radios y streamers de Nostr; mover, renombrar y eliminar (el contenido sube a la carpeta superior). Se guardan en el móvil.

## 0.1.0

- Directos de Nostr (NIP-53) de la gente que sigues o de todo Nostr.
- Solo audio o vídeo sin cortar el directo; audio automático con la pantalla apagada y con datos móviles.
- Calidad de vídeo seleccionable.
- Perfil del streamer con sus grabaciones.
- Inicio de sesión con Amber o con tu npub (solo lectura).
- Català, castellano e inglés; tema claro, oscuro o automático.
- Acerca de: licencia, enlaces al proyecto y formas de apoyarlo.
