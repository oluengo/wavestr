# Cambios

## 0.11.0

- **Chat:** al escribir, el teclado ya no tapa la caja de texto; el reproductor se encoge y la deja a la vista.
- **Favoritos:** tocar un streamer que está emitiendo reproduce su directo al momento (anterior/siguiente pasa por los demás favoritos en directo). Si no emite, se abre su perfil; el perfil también está en su menú ⋮.
- **Compartir** una emisora o streamer (desde el reproductor o su menú en Favoritos), una carpeta con sus subcarpetas, o todos los favoritos (botón de compartir en Favoritos: comparte lo que estás viendo). Tres formatos:
  - **Enlace:** abre Wavestr y muestra una vista previa para elegir qué añadir; sin la app, una página web con la lista, la descarga en M3U y cómo instalarla. Todo va dentro del enlace: no pasa por ningún servidor.
  - **Lista M3U** para VLC y otros reproductores (solo las radios).
  - **Texto** con los nombres y las direcciones de las emisoras.
- Al importar, una carpeta llega como carpeta; «todos los favoritos», dentro de una carpeta nueva «… (compartido)». Lo que ya tienes en favoritos no se duplica.

## 0.10.0

- **Nuevo icono:** el anillo con la onda, renovado, con degradado ámbar → coral → magenta sobre fondo oscuro; también en el icono temático de Android 13+ y en los widgets.
- **Android Auto:** Wavestr aparece en el coche con Favoritos (y sus carpetas), Streams que están emitiendo, Radios (populares y cerca de ti) y la última escuchada; búsqueda por voz de emisoras y anterior/siguiente. Fuera de Google Play hay que activar «Fuentes desconocidas» en los ajustes de desarrollador de Android Auto (pasos en el README).
- **Chat de los streams:** ya se puede escribir (firmado con Amber).
- **Widgets:** anterior/siguiente en la barra y en el reproductor.
- **Ajustes reorganizados:** una pantalla principal con categorías (Cuenta, Zaps y cartera, Reproducción, Apariencia e idioma, Favoritos y copias, Relays, Acerca de) y un resumen de cada una.

## 0.9.0

- **Anterior / siguiente** también para los streams de Nostr (por la lista de Streams) y al volver a abrir la app con una radio sonando. Los botones están siempre a la vista; se apagan cuando no hay a dónde ir.
- **Ordenador:** la versión de Linux y Windows se retira de las descargas mientras se termina.
- **Relays:** las listas de Ajustes → Relays se pliegan y despliegan (cerradas por defecto, con el número de relays a la vista).
- **Emisora anterior / siguiente** (móvil y ordenador): si la radio está en favoritos, salta entre los favoritos de su carpeta (o todos); si no, por la lista de la que la elegiste (populares, cerca de ti, estilo, búsqueda, parecidas). En Android también desde la notificación, la pantalla de bloqueo, los auriculares y el coche; en el ordenador, desde la bandeja.
- **Margen contra cortes** (Ajustes → Reproducción, móvil y ordenador): Auto, Directo, 10, 20 o 30 s. Con más margen hay menos cortes con mala cobertura, a cambio de ir unos segundos por detrás del directo; la radio muestra «Cargando 12/20 s» mientras lo llena. Auto: directo con wifi y 20 s con datos móviles. Por defecto, 10 s.
- **Favoritos:** los streamers de Nostr indican si están emitiendo («EN DIRECTO») o no («Sin emitir»), en el móvil y en el ordenador. En el widget de favoritos, un punto rojo marca los que emiten. Se comprueba al abrir Favoritos y cada minuto.

## 0.8.0

- **Widgets para la pantalla de inicio (Android):** Barra (4×1), Reproductor (4×2), Favoritos (4×2, con la carpeta que elijas) y Cuadrado (2×2). Play/pausa, sorpresa y favoritos sin abrir la app; si no suena nada, play pone la última emisora. Siguen el tema del móvil y tu color de acento.

## 0.7.0

- **Temporizador de apagado** (Android y ordenador): 15, 30, 45, 60 o 90 minutos, con el volumen bajando en los últimos 30 segundos; «+10 min» y cancelar. Con un stream de Nostr, también «al terminar el stream».
- **«Directos» pasa a llamarse «Streams»:** la radio también es en directo; los streams son los de Nostr.

## 0.6.0

- **Wavestr para Linux y Windows** (beta): `.deb`, `.tar.gz` y `.msi`. Ventana que se adapta (formato móvil, navegación lateral o tres columnas con el reproductor fijo), icono en la bandeja para seguir escuchando con la ventana cerrada, tamaño y posición recordados. Directos, radios (con importación PLS/M3U), favoritos con carpetas, sorpresa, relays, copias para pasar los favoritos del móvil. Reproduce con VLC. Inicio de sesión con npub (solo lectura) por ahora.

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
