import type { Odds, PublicState, TeamColor } from '../types';
import { lamportsToSol } from '../types';

const TEAM_META: Record<TeamColor, { label: string; class: string }> = {
  GREEN: { label: 'Green', class: 'green' },
  BLUE: { label: 'Blue', class: 'blue' },
  RED: { label: 'Red', class: 'red' },
  YELLOW: { label: 'Yellow', class: 'yellow' },
};

const PHASE_LABEL: Record<string, string> = {
  idle: 'Idle',
  lobby: 'Betting open',
  live: 'In progress - betting locked',
  settling: 'Settling…',
};

export function BettingBoard({ odds, state }: { odds: Odds | null; state: PublicState | null }) {
  const phase = odds?.phase ?? 'idle';
  const total = odds ? lamportsToSol(odds.totalPoolLamports) : 0;
  const queue = state?.queue;
  const rewardPool = state?.match ? lamportsToSol(state.match.rewardPoolLamports) : 0;

  return (
    <section className="card board">
      <div className="board-head">
        <h2>Live match</h2>
        <span className={`phase ${phase}`}>{PHASE_LABEL[phase] ?? phase}</span>
      </div>

      <div className="board-stats">
        <div>
          <span className="muted small">Player reward pool</span>
          <div className="stat">{rewardPool.toFixed(4)} SOL</div>
        </div>
        <div>
          <span className="muted small">Spectator pool</span>
          <div className="stat stat--secondary">{total.toFixed(4)} SOL</div>
        </div>
        <div>
          <span className="muted small">Queue</span>
          <div className="stat">
            {queue ? `${queue.size}/${queue.capacity}` : '-'}
          </div>
        </div>
      </div>

      <p className="board-player-note muted small">
        Winning team of 4 splits the <strong>player reward pool</strong> equally. The stats below are
        spectator bets only — optional if you are watching, not playing.
      </p>

      <h3 className="board-subhead">Spectator bets</h3>
      <div className="teams">
        {(odds?.teams ?? []).map((t) => {
          const meta = TEAM_META[t.team];
          const pool = lamportsToSol(t.poolLamports);
          const share = total > 0 ? (pool / total) * 100 : 0;
          return (
            <div className={`team ${meta.class}`} key={t.team}>
              <div className="team-top">
                <span className="team-name">{meta.label}</span>
                <span className="mult">{t.impliedMultiplier > 0 ? `${t.impliedMultiplier.toFixed(2)}x` : '—'}</span>
              </div>
              <div className="bar">
                <div className="bar-fill" style={{ width: `${share}%` }} />
              </div>
              <div className="team-bottom muted small">
                {pool.toFixed(3)} SOL · {t.bettors} bettor{t.bettors === 1 ? '' : 's'}
              </div>
            </div>
          );
        })}
      </div>

      <p className="muted small">
        Optional spectator feature: parimutuel odds shift live as bets come in. Winning-team backers split{' '}
        {odds ? (100 - odds.rakeFraction * 100).toFixed(0) : '95'}% of the spectator pool.
        In-game: <code>/bet &lt;team&gt; &lt;amount&gt;</code> during lobby.
      </p>
    </section>
  );
}
