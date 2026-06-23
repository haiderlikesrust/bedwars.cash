import { Link } from 'react-router-dom';

import { Layout } from '../components/Layout';
import { useLive } from '../useLive';
import { lamportsToSol, type MatchPhase } from '../types';

const PHASE_LABEL: Record<MatchPhase, string> = {
  idle: 'Idle',
  lobby: 'Lobby open',
  live: 'Match live',
  settling: 'Settling',
};

const FEATURES = [  {

    icon: '🏆',

    title: 'Win the match',

    desc: 'Queue for 4v4v4v4 BedWars. Destroy beds, outlast the other teams, and take the W — the winning squad splits the SOL reward pool.',

  },

  {

    icon: '⚔',

    title: 'Skill-based PvP',

    desc: 'Sixteen players, four teams, one island. Your gameplay decides the outcome — not luck, not house odds.',

  },

  {

    icon: '⛓',

    title: 'Solana payouts',

    desc: 'Link your Minecraft account, and winnings land in your wallet automatically when your team wins.',

  },

  {

    icon: '◎',

    title: 'Spectator betting',

    desc: 'Side option: watch from the sidelines and bet on a team during lobby. Separate pool, live parimutuel odds — totally optional.',

    side: true,

  },

];



const STEPS = [

  { num: '1', title: 'Join & queue', desc: 'Connect to the BedWars.cash server and join the match queue.' },

  { num: '2', title: 'Link your wallet', desc: 'Deposit devnet SOL on the Arena page and bind your account in-game with /bwlink.' },

  { num: '3', title: 'Play & win', desc: 'Fight in a live 4v4v4v4 match. Protect your bed, rush the others, be the last team standing.' },

  { num: '4', title: 'Collect SOL', desc: 'Your share of the reward pool is credited when your team wins. Spectators who bet get a separate payout.' },

];



const TEAMS = [

  { name: 'Green', class: 'green', emoji: '🟩' },

  { name: 'Blue', class: 'blue', emoji: '🟦' },

  { name: 'Red', class: 'red', emoji: '🟥' },

  { name: 'Yellow', class: 'yellow', emoji: '🟨' },

];



export function LandingPage() {
  const live = useLive();
  const phase = live.odds?.phase ?? live.state?.match?.phase ?? 'idle';
  const spectatorPool = live.odds ? lamportsToSol(live.odds.totalPoolLamports) : 0;

  return (
    <Layout connected={live.connected}>      <section className="hero">

        <div className="hero-content">

          <p className="hero-tag">Skill-based BedWars · Solana devnet</p>

          <h1 className="hero-title">

            Play.

            <span className="hero-bed"> Win.</span>

            <br />

            Get paid.

          </h1>

          <p className="hero-sub">

            Queue for live 4v4v4v4 BedWars matches. Win with your team and split the SOL reward pool —

            four players, equal shares, straight to your wallet.

          </p>

          <p className="hero-sub hero-sub--secondary">

            Spectator betting is available too, but the main event is playing the game and winning it.

          </p>

          <div className="hero-actions">

            <Link to="/play" className="btn btn-primary btn-lg">

              Enter Arena

            </Link>

            <a href="#how-it-works" className="btn btn-secondary btn-lg">

              How it works

            </a>

          </div>

        </div>



        <div className="hero-visual mc-panel" aria-hidden="true">

          <div className="pixel-bed">

            <div className="bed-frame" />

            <div className="bed-sheet" />

            <div className="bed-pillow" />

          </div>

          <div className="hero-stats">

            <div className="hero-stat">

              <span className="hero-stat-val">4×4</span>

              <span className="hero-stat-label">Teams</span>

            </div>

            <div className="hero-stat">

              <span className="hero-stat-val">16</span>

              <span className="hero-stat-label">Players</span>

            </div>

            <div className="hero-stat">

              <span className="hero-stat-val">SOL</span>

              <span className="hero-stat-label">Prize pool</span>

            </div>

          </div>

        </div>

      </section>

      <section className="live-strip mc-panel" aria-label="Live match status">
        <div className="live-strip-inner">
          <div className="live-strip-item">
            <span className="live-strip-label">Match phase</span>
            <span className={`phase ${phase}`}>{PHASE_LABEL[phase] ?? phase}</span>
          </div>
          <div className="live-strip-item">
            <span className="live-strip-label">Spectator pool</span>
            <span className="live-strip-val">{spectatorPool.toFixed(4)} SOL</span>
          </div>
        </div>
      </section>

      <section className="teams-showcase">        {TEAMS.map((t) => (

          <div key={t.name} className={`team-badge team ${t.class}`}>

            <span className="team-badge-emoji">{t.emoji}</span>

            <span className="team-badge-name">{t.name}</span>

          </div>

        ))}

      </section>



      <section className="section">

        <h2 className="section-title">Why play here</h2>

        <div className="feature-grid">

          {FEATURES.map((f) => (

            <article key={f.title} className={`card feature-card${f.side ? ' feature-card--side' : ''}`}>

              {f.side && <span className="feature-badge">Optional</span>}

              <span className="feature-icon">{f.icon}</span>

              <h3>{f.title}</h3>

              <p className="muted">{f.desc}</p>

            </article>

          ))}

        </div>

      </section>



      <section className="section" id="how-it-works">

        <h2 className="section-title">How it works</h2>

        <div className="steps">

          {STEPS.map((s) => (

            <div key={s.num} className="card step-card">

              <div className="step-num">{s.num}</div>

              <div>

                <h3>{s.title}</h3>

                <p className="muted">{s.desc}</p>

              </div>

            </div>

          ))}

        </div>

      </section>



      <section className="section side-betting-section">

        <h2 className="section-title">Spectator betting</h2>

        <div className="card side-betting-card">

          <p>

            Not playing this round? During the lobby phase you can stake SOL on any team with{' '}

            <code>/bet &lt;team&gt; &lt;amount&gt;</code>. Backers on the winning team split a separate

            spectator pool (95% payout, 5% rake). This is entirely optional — the core experience is

            playing and winning matches.

          </p>

        </div>

      </section>



      <section className="cta-section mc-panel">

        <h2>Ready to queue up?</h2>

        <p className="muted">Link your wallet, join the server, and fight for your share of the pool.</p>

        <Link to="/play" className="btn btn-primary btn-lg">

          Open Arena Dashboard

        </Link>

      </section>

    </Layout>

  );

}


