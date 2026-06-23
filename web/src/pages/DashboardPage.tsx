import { useEffect, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import {
  createLinkCode,
  ensureSession,
  getHouse,
  getLeaderboard,
  getMe,
  withdraw,
  type HouseResp,
  type Leaderboard,
  type MeResp,
} from '../api';
import { useLive } from '../useLive';
import { BettingBoard } from '../components/BettingBoard';
import { LiveStreamPanel } from '../components/LiveStreamPanel';
import { Layout } from '../components/Layout';

export function DashboardPage() {
  const live = useLive();
  const [me, setMe] = useState<MeResp | null>(null);
  const [house, setHouse] = useState<HouseResp | null>(null);
  const [leaderboard, setLeaderboard] = useState<Leaderboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refreshMe = async () => {
    try {
      setMe(await getMe());
    } catch (e) {
      setError((e as Error).message);
    }
  };

  useEffect(() => {
    (async () => {
      try {
        await ensureSession();
        await refreshMe();
        setHouse(await getHouse());
        setLeaderboard(await getLeaderboard());
      } catch (e) {
        setError((e as Error).message);
      }
    })();
    const t = setInterval(() => {
      void refreshMe();
      getHouse().then(setHouse).catch(() => {});
      getLeaderboard().then(setLeaderboard).catch(() => {});
    }, 8000);
    return () => clearInterval(t);
  }, []);

  return (
    <Layout connected={live.connected}>
      {error && <div className="banner error">⚠ {error}</div>}
      <div className="notice-stack">
        {live.notices.map((n, i) => (
          <div className="banner notice" key={i}>
            {n}
          </div>
        ))}
      </div>

      <main className="grid">
        <LiveStreamPanel state={live.state} />
        <BettingBoard odds={live.odds} state={live.state} />

        <section className="card">
          <h2>Your wallet</h2>
          {me ? (
            <>
              <div className="balance">
                {me.balanceSol.toFixed(4)} <span>SOL</span>
              </div>
              <p className="muted">Deposit devnet SOL to this address. It is credited automatically.</p>
              <div className="deposit">
                <div className="qr">
                  <QRCodeSVG value={me.depositAddress} size={132} bgColor="#2a2a2a" fgColor="#e8e0d0" />
                </div>
                <code className="addr" title={me.depositAddress}>
                  {me.depositAddress}
                </code>
              </div>
              <CopyButton text={me.depositAddress} />
            </>
          ) : (
            <p className="muted">Loading account…</p>
          )}
        </section>

        <LinkCard me={me} onLinked={refreshMe} />
        <WithdrawCard onDone={refreshMe} />

        <section className="card">
          <h2>Player reward pool</h2>
          {house ? (
            <ul className="kv">
              <li>
                <span>Available for winners</span>
                <strong>{house.availableRewardPoolSol.toFixed(4)} SOL</strong>
              </li>
              <li>
                <span>House balance</span>
                <strong>{house.balanceSol.toFixed(4)} SOL</strong>
              </li>
            </ul>
          ) : (
            <p className="muted">…</p>
          )}
          <p className="muted small">
            Funded by Pump.fun creator fees (mocked on devnet). When your team wins, all 4 players split this pool equally.
          </p>
        </section>

        <section className="card">
          <h2>Leaderboard</h2>
          <div className="board-cols">
            <div>
              <h3>Top bettors (profit)</h3>
              <ol className="lb">
                {leaderboard?.bettors.length ? (
                  leaderboard.bettors.map((b, i) => (
                    <li key={i}>
                      <span>{b.name}</span>
                      <strong className={b.netProfitSol >= 0 ? 'pos' : 'neg'}>
                        {b.netProfitSol >= 0 ? '+' : ''}
                        {b.netProfitSol.toFixed(3)}
                      </strong>
                    </li>
                  ))
                ) : (
                  <li className="muted">No bets settled yet.</li>
                )}
              </ol>
            </div>
            <div>
              <h3>Top players (winnings)</h3>
              <ol className="lb">
                {leaderboard?.players.length ? (
                  leaderboard.players.map((p, i) => (
                    <li key={i}>
                      <span>{p.name}</span>
                      <strong className="pos">{p.wonSol.toFixed(3)}</strong>
                    </li>
                  ))
                ) : (
                  <li className="muted">No matches won yet.</li>
                )}
              </ol>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}

function LinkCard({ me, onLinked }: { me: MeResp | null; onLinked: () => void }) {
  const [code, setCode] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const generate = async () => {
    setBusy(true);
    try {
      const r = await createLinkCode();
      setCode(r.code);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card">
      <h2>Link Minecraft</h2>
      {me?.linked ? (
        <p className="linked">✓ Linked as <strong>{me.mcUsername}</strong></p>
      ) : (
        <>
          <p className="muted">Generate a code, then run it in-game to bind your Minecraft account.</p>
          {code ? (
            <div className="code-box">
              <div className="code">{code}</div>
              <p className="muted small">
                In Minecraft type: <code>/bwlink {code}</code>
              </p>
              <button className="btn btn-secondary" onClick={() => { setCode(null); onLinked(); }}>
                Done
              </button>
            </div>
          ) : (
            <button className="btn btn-primary" onClick={generate} disabled={busy}>
              {busy ? '…' : 'Get link code'}
            </button>
          )}
        </>
      )}
    </section>
  );
}

function WithdrawCard({ onDone }: { onDone: () => void }) {
  const [dest, setDest] = useState('');
  const [amount, setAmount] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setMsg(null);
    setBusy(true);
    try {
      const r = await withdraw(dest.trim(), Number(amount));
      setMsg(r.status === 'held' ? 'Withdrawal held for manual review.' : `Sent! ${r.signature?.slice(0, 16)}…`);
      setAmount('');
      onDone();
    } catch (e) {
      setMsg((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card">
      <h2>Withdraw</h2>
      <input
        className="input"
        placeholder="Destination Solana address"
        value={dest}
        onChange={(e) => setDest(e.target.value)}
      />
      <input
        className="input"
        placeholder="Amount (SOL)"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        inputMode="decimal"
      />
      <button className="btn btn-primary" onClick={submit} disabled={busy || !dest || !amount}>
        {busy ? '…' : 'Withdraw'}
      </button>
      {msg && <p className="muted small">{msg}</p>}
    </section>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      className="btn btn-secondary"
      onClick={async () => {
        await navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      }}
    >
      {copied ? 'Copied!' : 'Copy address'}
    </button>
  );
}
