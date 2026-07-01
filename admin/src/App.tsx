import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import {
  api,
  setCsrf,
  type AuditEntry,
  type CheatFlag,
  type HeldWithdrawal,
  type Overview,
  type Player,
} from './api';

export function App() {
  const [username, setUsername] = useState<string | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    api
      .me()
      .then((m) => {
        setCsrf(m.csrf);
        setUsername(m.username);
      })
      .catch(() => setUsername(null))
      .finally(() => setChecking(false));
  }, []);

  if (checking) return <div className="center muted">Loading…</div>;
  if (!username) return <Login onIn={setUsername} />;
  return <Dashboard username={username} onOut={() => setUsername(null)} />;
}

function Login({ onIn }: { onIn: (u: string) => void }) {
  const [u, setU] = useState('');
  const [p, setP] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    setBusy(true);
    try {
      const r = await api.login(u, p);
      setCsrf(r.csrf);
      onIn(r.username);
    } catch (e) {
      setErr((e as Error).message === 'locked_out' ? 'Too many attempts — locked out. Try later.' : 'Invalid credentials.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="center">
      <form className="card login" onSubmit={submit}>
        <h1>BedWars.cash Admin</h1>
        <input placeholder="Username" value={u} onChange={(e) => setU(e.target.value)} autoFocus />
        <input placeholder="Password" type="password" value={p} onChange={(e) => setP(e.target.value)} />
        <button className="btn primary" disabled={busy || !u || !p}>
          {busy ? '…' : 'Sign in'}
        </button>
        {err && <p className="err">{err}</p>}
      </form>
    </div>
  );
}

function Dashboard({ username, onOut }: { username: string; onOut: () => void }) {
  const [toast, setToast] = useState<string | null>(null);
  const flash = (m: string) => {
    setToast(m);
    setTimeout(() => setToast(null), 3000);
  };
  const logout = async () => {
    await api.logout().catch(() => {});
    onOut();
  };

  return (
    <div className="app">
      <header className="topbar">
        <span className="brand">BedWars.cash <b>Admin</b></span>
        <span className="spacer" />
        <span className="muted small">{username}</span>
        <button className="btn ghost" onClick={logout}>Log out</button>
      </header>
      {toast && <div className="toast">{toast}</div>}
      <main className="grid">
        <OverviewCard flash={flash} />
        <MatchCard flash={flash} />
        <PlayersCard flash={flash} />
        <WithdrawalsCard flash={flash} />
        <CheatFlagsCard />
        <AuditCard />
      </main>
    </div>
  );
}

function Section({ title, children, actions }: { title: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      <div className="card-head">
        <h2>{title}</h2>
        {actions}
      </div>
      {children}
    </section>
  );
}

function OverviewCard({ flash }: { flash: (m: string) => void }) {
  const [o, setO] = useState<Overview | null>(null);
  const [topupSol, setTopupSol] = useState('1');
  const load = () => api.overview().then(setO).catch(() => {});
  useEffect(() => {
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, []);

  const clearQueue = async () => {
    const r = await api.clearQueue();
    flash(`Queue cleared (${r.removed} removed).`);
    load();
  };
  const topup = async () => {
    try {
      await api.topup(Number(topupSol));
      flash(`Funded ${topupSol} SOL.`);
      load();
    } catch (e) {
      flash((e as Error).message);
    }
  };

  return (
    <Section title="Overview" actions={<button className="btn ghost sm" onClick={load}>↻</button>}>
      {!o ? (
        <p className="muted">…</p>
      ) : (
        <>
          <div className="kv">
            <div><span>Phase</span><b className={`tag ${o.phase}`}>{o.phase}</b></div>
            <div><span>Queue</span><b>{o.queue.size}/{o.queue.capacity}</b></div>
            <div><span>Plugin</span><b className={o.pluginConnected ? 'ok' : 'bad'}>{o.pluginConnected ? 'connected' : 'offline'}</b></div>
            <div><span>House balance</span><b>{o.houseBalanceSol.toFixed(4)} SOL</b></div>
            <div><span>Reward pool</span><b>{o.availableRewardPoolSol.toFixed(4)} SOL</b></div>
            <div><span>Liabilities</span><b>{o.liabilitiesSol.toFixed(4)} SOL</b></div>
            <div><span>Solvent</span><b className={o.solvent ? 'ok' : 'bad'}>{o.solvent ? 'yes' : 'NO — underfunded'}</b></div>
          </div>
          <div className="row">
            <button className="btn" onClick={clearQueue}>Clear queue</button>
            {o.cluster !== 'mainnet-beta' && (
              <span className="row tight">
                <input className="sm-input" value={topupSol} onChange={(e) => setTopupSol(e.target.value)} inputMode="decimal" />
                <button className="btn" onClick={topup}>Fund house</button>
              </span>
            )}
          </div>
        </>
      )}
    </Section>
  );
}

function MatchCard({ flash }: { flash: (m: string) => void }) {
  const [reason, setReason] = useState('Admin abort');
  const forceStart = async () => flash((await api.forceStart()).message);
  const abort = async () => flash((await api.abort(reason)).message);
  return (
    <Section title="Match control">
      <p className="muted small">Force-start with fewer than the required players, or void the current match (all bets refunded).</p>
      <div className="row">
        <button className="btn" onClick={forceStart}>Force start</button>
      </div>
      <div className="row">
        <input className="grow" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Abort reason" />
        <button className="btn danger" onClick={abort}>Abort / void</button>
      </div>
    </Section>
  );
}

function PlayersCard({ flash }: { flash: (m: string) => void }) {
  const [players, setPlayers] = useState<Player[]>([]);
  const [q, setQ] = useState('');
  const load = () => api.players().then((r) => setPlayers(r.players)).catch(() => {});
  useEffect(() => { load(); }, []);
  const credit = async (id: number) => {
    const sol = prompt('Adjust balance by how many SOL? (negative to debit)');
    if (sol === null) return;
    try {
      const r = await api.credit(id, Number(sol));
      flash(`New balance: ${r.balanceSol.toFixed(4)} SOL`);
      load();
    } catch (e) {
      flash((e as Error).message);
    }
  };
  const shown = players.filter((p) => !q || (p.mcUsername ?? '').toLowerCase().includes(q.toLowerCase()));
  return (
    <Section title="Players" actions={<input className="sm-input" placeholder="filter" value={q} onChange={(e) => setQ(e.target.value)} />}>
      <div className="table-wrap">
        <table>
          <thead><tr><th>#</th><th>Player</th><th className="num">Balance</th><th>Linked</th><th></th></tr></thead>
          <tbody>
            {shown.slice(0, 50).map((p) => (
              <tr key={p.id}>
                <td className="muted">{p.id}</td>
                <td>{p.mcUsername ?? <span className="muted">— unlinked —</span>}</td>
                <td className="num">{p.balanceSol.toFixed(4)}</td>
                <td>{p.mcUuid ? <span className="ok">✓</span> : <span className="muted">—</span>}</td>
                <td><button className="btn ghost sm" onClick={() => credit(p.id)}>Adjust</button></td>
              </tr>
            ))}
            {shown.length === 0 && <tr><td colSpan={5} className="muted">No players.</td></tr>}
          </tbody>
        </table>
      </div>
    </Section>
  );
}

function WithdrawalsCard({ flash }: { flash: (m: string) => void }) {
  const [rows, setRows] = useState<HeldWithdrawal[]>([]);
  const load = () => api.heldWithdrawals().then((r) => setRows(r.withdrawals)).catch(() => {});
  useEffect(() => { load(); }, []);
  const reject = async (id: number) => {
    if (!confirm('Reject and refund this withdrawal to the user balance?')) return;
    await api.rejectWithdrawal(id);
    flash('Withdrawal rejected and refunded.');
    load();
  };
  return (
    <Section title="Held withdrawals" actions={<button className="btn ghost sm" onClick={load}>↻</button>}>
      {rows.length === 0 ? (
        <p className="muted">None held for review.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>User</th><th className="num">Amount</th><th>Destination</th><th></th></tr></thead>
            <tbody>
              {rows.map((w) => (
                <tr key={w.id}>
                  <td>{w.username ?? w.userId}</td>
                  <td className="num">{w.amountSol.toFixed(4)}</td>
                  <td className="mono small">{w.destination.slice(0, 10)}…</td>
                  <td><button className="btn danger sm" onClick={() => reject(w.id)}>Reject</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  );
}

function CheatFlagsCard() {
  const [flags, setFlags] = useState<CheatFlag[]>([]);
  const load = () => api.cheatFlags().then((r) => setFlags(r.flags)).catch(() => {});
  useEffect(() => { load(); }, []);
  return (
    <Section title="Cheat flags" actions={<button className="btn ghost sm" onClick={load}>↻</button>}>
      {flags.length === 0 ? (
        <p className="muted">No flags logged.</p>
      ) : (
        <ul className="list">
          {flags.map((f, i) => (
            <li key={i}>
              <b>{f.username ?? f.mcUuid.slice(0, 8)}</b> <span className="tag warn">{f.checkName}</span>
              <span className="muted small"> {f.details} · {new Date(f.createdAt).toLocaleString()}</span>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}

function AuditCard() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const load = () => api.audit().then((r) => setEntries(r.entries)).catch(() => {});
  useEffect(() => { load(); const t = setInterval(load, 10000); return () => clearInterval(t); }, []);
  return (
    <Section title="Audit log" actions={<button className="btn ghost sm" onClick={load}>↻</button>}>
      <ul className="list">
        {entries.map((e, i) => (
          <li key={i}>
            <b>{e.action}</b> <span className="muted small">{e.details} · {e.username} · {e.ip} · {new Date(e.createdAt).toLocaleTimeString()}</span>
          </li>
        ))}
        {entries.length === 0 && <li className="muted">No actions yet.</li>}
      </ul>
    </Section>
  );
}
