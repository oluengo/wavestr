import { t } from './i18n'
import { bestName, type LiveStream, type Profile } from './nostr'
import type { NowPlaying } from './player.svelte'

/** Un directo listo para el reproductor. */
export function liveNow(s: LiveStream, host?: Profile): NowPlaying {
  return {
    id: s.address, url: s.streamingUrl!, title: s.title || t('untitled_stream'), subtitle: bestName(host, s.hostPubkey),
    artwork: s.image || host?.picture, hostPubkey: s.hostPubkey, isLive: true, hasVideo: true,
  }
}
