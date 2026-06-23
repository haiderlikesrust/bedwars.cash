import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';

export function Layout({
  children,
  connected,
}: {
  children: ReactNode;
  connected?: boolean;
}) {
  const { pathname } = useLocation();
  const isLanding = pathname === '/';

  return (
    <div className={`app ${isLanding ? 'app--landing' : 'app--dashboard'}`}>
      <div className="sky" aria-hidden="true">
        <div className="cloud cloud-1" />
        <div className="cloud cloud-2" />
        <div className="cloud cloud-3" />
      </div>
      <div className="terrain" aria-hidden="true" />

      <header className="topbar mc-panel">
        <Link to="/" className="brand">
          <span className="brand-icon" aria-hidden="true">
            🛏
          </span>
          BedWars<span className="accent">.cash</span>
        </Link>

        <nav className="nav">
          <Link to="/" className={pathname === '/' ? 'nav-link active' : 'nav-link'}>
            Home
          </Link>
          <Link to="/play" className={pathname === '/play' ? 'nav-link active' : 'nav-link'}>
            Arena
          </Link>
        </nav>

        {connected !== undefined && (
          <div className="net">
            <span className={`dot ${connected ? 'on' : 'off'}`} />
            devnet
          </div>
        )}
      </header>

      {children}

      <footer className="foot mc-panel">
        <p>
          Devnet demo — not real money. Winning players split the reward pool.
          Spectators can optionally bet with <code>/bet &lt;team&gt; &lt;amount&gt;</code>.
        </p>
      </footer>
    </div>
  );
}
