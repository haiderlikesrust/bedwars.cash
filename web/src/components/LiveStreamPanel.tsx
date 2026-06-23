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

export function LiveStreamPanel({ state }: { state: PublicState | null }) {
  const phase = state?.match?.phase;
  const stream = state?.stream;
  const show = !!stream?.url && (phase === 'live' || phase === 'starting' || phase === 'settling');

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
          <video className="stream-video" src={stream.url} controls autoPlay muted playsInline />
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
