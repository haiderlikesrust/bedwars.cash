import { useEffect, useRef, useState } from 'react';
import type HlsType from 'hls.js';
import type { PublicState } from '../types';

const TEAM_LABEL: Record<string, string> = {
  GREEN: 'Green',
  BLUE: 'Blue',
  RED: 'Red',
  YELLOW: 'Yellow',
};

function isHlsUrl(url: string): boolean {
  return url.includes('.m3u8');
}

type StreamStatus = 'loading' | 'playing' | 'offline';

// A Minecraft "building blocks" loading screen shown while the feed buffers or
// when the broadcast is offline (instead of a stuck video / console errors).
function StreamLoading({ status }: { status: StreamStatus }) {
  const offline = status === 'offline';
  return (
    <div className="stream-loading" role="status">
      <div className="mc-builder" aria-hidden="true">
        {Array.from({ length: 6 }).map((_, i) => (
          <span key={i} className={`mc-block b${i + 1}`} />
        ))}
      </div>
      <p className="stream-loading-text">
        {offline ? 'Waiting for the broadcast' : 'Loading stream'}
      </p>
      <p className="stream-loading-sub muted small">
        {offline
          ? 'The cast client is offline — this starts automatically when a match is live.'
          : 'Buffering the live feed…'}
      </p>
    </div>
  );
}

// HLS playback: Safari plays .m3u8 natively; Chrome/Firefox need hls.js (loaded lazily).
// Surfaces a status so the panel can show the loading screen and auto-recovers when
// the stream (re)appears.
function HlsVideo({ url }: { url: string }) {
  const ref = useRef<HTMLVideoElement>(null);
  const [status, setStatus] = useState<StreamStatus>('loading');

  useEffect(() => {
    const video = ref.current;
    if (!video) return;
    let cancelled = false;
    let hls: HlsType | null = null;
    let retry: ReturnType<typeof setTimeout> | null = null;

    const onPlaying = () => !cancelled && setStatus('playing');
    video.addEventListener('playing', onPlaying);

    const scheduleRetry = (fn: () => void) => {
      if (retry) clearTimeout(retry);
      retry = setTimeout(() => !cancelled && fn(), 4000);
    };

    // Native HLS (Safari / iOS).
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      const load = () => {
        video.src = url;
        video.load();
      };
      const onError = () => {
        if (cancelled) return;
        setStatus('offline');
        scheduleRetry(load);
      };
      video.addEventListener('error', onError);
      load();
      return () => {
        cancelled = true;
        video.removeEventListener('playing', onPlaying);
        video.removeEventListener('error', onError);
        if (retry) clearTimeout(retry);
      };
    }

    void import('hls.js').then(({ default: Hls }) => {
      if (cancelled || !ref.current) return;
      if (!Hls.isSupported()) {
        ref.current.src = url;
        return;
      }
      const setup = () => {
        if (cancelled || !ref.current) return;
        hls = new Hls({ lowLatencyMode: true });
        hls.loadSource(url);
        hls.attachMedia(ref.current);
        hls.on(Hls.Events.FRAG_BUFFERED, () => !cancelled && setStatus('playing'));
        hls.on(Hls.Events.ERROR, (_evt, data) => {
          if (!data.fatal) return;
          // Fatal (e.g. manifest 500 when nobody is publishing): show the loading
          // screen and re-create the player so it recovers when the stream returns.
          setStatus('offline');
          hls?.destroy();
          hls = null;
          scheduleRetry(setup);
        });
      };
      setup();
    });

    return () => {
      cancelled = true;
      video.removeEventListener('playing', onPlaying);
      if (retry) clearTimeout(retry);
      hls?.destroy();
    };
  }, [url]);

  return (
    <>
      <video ref={ref} className="stream-video" controls autoPlay muted playsInline />
      {status !== 'playing' && <StreamLoading status={status} />}
    </>
  );
}

export function LiveStreamPanel({ state }: { state: PublicState | null }) {
  const phase = state?.match?.phase;
  const stream = state?.stream;
  const show = !!stream?.url && (phase === 'live' || phase === 'lobby' || phase === 'settling');

  if (!show || !stream?.url) return null;

  const camera = stream.camera;
  const cameraLabel = camera
    ? `Camera: ${camera.playerName} (${TEAM_LABEL[camera.team] ?? camera.team})`
    : 'Camera waiting for cast account…';

  return (
    <section className="card stream-panel">
      <div className="stream-head">
        <h2>Live broadcast</h2>
        <span className="stream-live">● LIVE</span>
      </div>
      <p className="muted small stream-camera">{cameraLabel}</p>
      <div className="stream-frame">
        {isHlsUrl(stream.url) ? (
          <HlsVideo url={stream.url} />
        ) : (
          <iframe
            className="stream-iframe"
            src={stream.url}
            title="BedWars live stream"
            allow="autoplay; fullscreen"
            allowFullScreen
          />
        )}
      </div>
      <p className="muted small">
        Run a Minecraft client as the cast account, capture with OBS/ffmpeg, and set{' '}
        <code>STREAM_URL</code> in the backend. The plugin rotates the in-game camera between teams.
      </p>
    </section>
  );
}
