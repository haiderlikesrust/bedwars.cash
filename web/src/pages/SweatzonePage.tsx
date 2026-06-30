import { useEffect, useState } from 'react';

import { getSweatzone, type SweatzonePlayer } from '../api';
import { Layout } from '../components/Layout';
import { SolAmount } from '../components/SolIcon';

export function SweatzonePage() {
  const [players, setPlayers] = useState<SweatzonePlayer[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const data = await getSweatzone();
        if (!cancelled) {
          setPlayers(data.players);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();
    const t = setInterval(() => {
      void getSweatzone()
        .then((data) => setPlayers(data.players))
        .catch(() => {});
    }, 15000);
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, []);

  return (
    <Layout>
      <div className="sweatzone-layout">
        <header className="sweatzone-hero mc-panel">
          <p className="hero-tag">Leaderboard</p>
          <h1 className="sweatzone-title">Sweatzone</h1>
          <p className="sweatzone-lead">
            All-time stats from every match — wins, kills, beds broken, and SOL earned from the
            reward pool.
          </p>
        </header>

        {error && <div className="banner error">⚠ {error}</div>}

        <section className="card sweatzone-card">
          {loading ? (
            <p className="muted">Loading stats…</p>
          ) : players.length === 0 ? (
            <p className="muted">No matches played yet. Be the first on the board.</p>
          ) : (
            <div className="sweatzone-table-wrap">
              <table className="sweatzone-table">
                <thead>
                  <tr>
                    <th scope="col">#</th>
                    <th scope="col">Player</th>
                    <th scope="col" className="num">
                      Lvl
                    </th>
                    <th scope="col" className="num">
                      Wins
                    </th>
                    <th scope="col" className="num">
                      Kills
                    </th>
                    <th scope="col" className="num">
                      Beds
                    </th>
                    <th scope="col" className="num">
                      SOL won
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {players.map((p, i) => (
                    <tr key={`${p.username}-${i}`}>
                      <td className="sweatzone-rank">{i + 1}</td>
                      <td className="sweatzone-player">{p.username}</td>
                      <td className="num sweatzone-level">{p.level}</td>
                      <td className="num">{p.wins}</td>
                      <td className="num">{p.kills}</td>
                      <td className="num">{p.bedsBroken}</td>
                      <td className="num">
                        <SolAmount amount={p.solWon} decimals={3} className="pos" />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </Layout>
  );
}
