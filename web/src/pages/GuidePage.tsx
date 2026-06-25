import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { Layout } from '../components/Layout';
import { SolIcon } from '../components/SolIcon';
import {
  FAQ_ITEMS,
  GUIDE_SECTIONS,
  MATCH_PHASES,
  MC_SERVER,
  PLAYER_COMMANDS,
  SHOP_CATEGORIES,
  TEAM_UPGRADES,
} from '../content/guideContent';

function GuideSection({
  id,
  title,
  children,
}: {
  id: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="guide-section card" id={id}>
      <h2 className="guide-section-title">{title}</h2>
      <div className="guide-section-body">{children}</div>
    </section>
  );
}

export function GuidePage() {
  return (
    <Layout>
      <div className="guide-layout">
        <aside className="guide-toc mc-panel" aria-label="Guide contents">
          <p className="guide-toc-label">On this page</p>
          <nav>
            <ul>
              {GUIDE_SECTIONS.map((s) => (
                <li key={s.id}>
                  <a href={`#${s.id}`}>{s.label}</a>
                </li>
              ))}
            </ul>
          </nav>
          <Link to="/play" className="btn btn-primary guide-toc-cta">
            Open Arena
          </Link>
        </aside>

        <div className="guide-main">
          <header className="guide-hero mc-panel">
            <p className="hero-tag">Player guide</p>
            <h1 className="guide-hero-title">How BedWars.cash works</h1>
            <p className="guide-hero-lead">
              Everything you need to queue, fight, get paid, and optionally bet — written for
              players, not developers.
            </p>
            <div className="guide-hero-meta">
              <div className="guide-meta-item">
                <span className="guide-meta-label">Server</span>
                <code className="guide-meta-val">{MC_SERVER}</code>
              </div>
              <div className="guide-meta-item">
                <span className="guide-meta-label">Format</span>
                <span className="guide-meta-val">4v4v4v4 · 16 players</span>
              </div>
              <div className="guide-meta-item">
                <span className="guide-meta-label">Network</span>
                <span className="guide-meta-val guide-meta-sol">
                  <SolIcon size={18} /> Solana devnet
                </span>
              </div>
            </div>
          </header>

          <GuideSection id="overview" title="Overview">
            <p>
              BedWars.cash is competitive BedWars on Minecraft with a{' '}
              <strong>Solana devnet reward pool</strong> for the winning team. One match runs at a
              time: four teams (Green, Blue, Red, Yellow), four players each.
            </p>
            <div className="guide-callout guide-callout--info">
              <strong>Two separate money pools</strong>
              <ul>
                <li>
                  <strong>Player reward pool</strong> — fighters only. The winning squad splits it
                  equally. Paid to your <code>/setwallet</code> address.
                </li>
                <li>
                  <strong>Spectator betting pool</strong> — optional. Bettors stake on a team during
                  lobby; winners share 95% of the pool (5% rake). Separate from fighter rewards.
                </li>
              </ul>
            </div>
            <p className="muted">
              The main experience is playing and winning. Spectator betting is optional side action
              while you watch.
            </p>
          </GuideSection>

          <GuideSection id="getting-started" title="Getting started">
            <ol className="guide-steps">
              <li>
                <strong>Join the server</strong>
                <p>
                  Add <code>{MC_SERVER}</code> in Minecraft (Java Edition, 1.21+). You are
                  auto-queued in the lobby when a match is open.
                </p>
              </li>
              <li>
                <strong>Set your payout wallet</strong>
                <p>
                  In-game: <code>/setwallet &lt;solana_address&gt;</code>. Match winnings are sent
                  here when your team wins.
                </p>
              </li>
              <li>
                <strong>Link your website account (optional)</strong>
                <p>
                  Open the <Link to="/play">Arena dashboard</Link>, generate a link code, then run{' '}
                  <code>/bwlink &lt;code&gt;</code> in Minecraft. Required for spectator betting and
                  custodial deposits/withdrawals.
                </p>
              </li>
              <li>
                <strong>Wait for the queue</strong>
                <p>
                  When 16 players are queued, the match starts automatically. Ops can force-start
                  with fewer players.
                </p>
              </li>
              <li>
                <strong>Fight and win</strong>
                <p>
                  Protect your bed, eliminate other teams, be the last squad standing. Collect your
                  share of the reward pool.
                </p>
              </li>
            </ol>
          </GuideSection>

          <GuideSection id="matches" title="How matches work">
            <div className="guide-phases">
              {MATCH_PHASES.map((p, i) => (
                <div key={p.phase} className="guide-phase card">
                  <span className="guide-phase-num">{i + 1}</span>
                  <h3>{p.phase}</h3>
                  <p className="muted">{p.desc}</p>
                </div>
              ))}
            </div>
            <h3 className="guide-subhead">Match start</h3>
            <ul className="guide-list">
              <li>Backend assigns teams — parties stay together when possible.</li>
              <li>
                Countdown at the arena (default 10 seconds). PvP is off during countdown.
              </li>
              <li>
                Fighters teleport to team islands with a starter kit: wooden sword, team wool, shop
                item in slot 9.
              </li>
              <li>Generators, item shop villagers, and upgrade villagers spawn.</li>
            </ul>
            <h3 className="guide-subhead">Winning</h3>
            <p>
              Break enemy beds to stop respawns. Kill players whose beds are gone (final kills).
              The last team with at least one player still in the fight wins. An MVP is announced
              by kills and bed breaks.
            </p>
            <h3 className="guide-subhead">After the match</h3>
            <ul className="guide-list">
              <li>Winners receive reward pool payouts.</li>
              <li>Spectator bets settle on the winning team.</li>
              <li>Winners sit out the next match (win cooldown) but can spectate and bet.</li>
              <li>Everyone returns to the lobby; a new queue opens.</li>
            </ul>
          </GuideSection>

          <GuideSection id="playing" title="Playing BedWars">
            <h3 className="guide-subhead">Core rules</h3>
            <ul className="guide-list">
              <li>
                <strong>Beds</strong> — if your bed is destroyed, you cannot respawn. Further deaths
                eliminate you.
              </li>
              <li>
                <strong>Void</strong> — falling off the map kills you (respawn if your bed is alive).
              </li>
              <li>
                <strong>Building</strong> — you can place blocks anywhere in the arena. Only
                player-placed blocks can be broken; map terrain is protected.
              </li>
              <li>
                <strong>Friendly fire</strong> — disabled. Teammates cannot damage each other
                (melee, arrows, fireballs, team TNT).
              </li>
              <li>
                <strong>Shops</strong> — use <code>/shop</code>, right-click the item shop villager,
                or the nether star in your hotbar.
              </li>
            </ul>

            <h3 className="guide-subhead">Resources & generators</h3>
            <p>Each team island spawns iron and gold. Diamond and emerald generators sit at mid.</p>
            <div className="guide-table-wrap">
              <table className="guide-table">
                <thead>
                  <tr>
                    <th>Resource</th>
                    <th>Where</th>
                    <th>Use</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>Iron</td>
                    <td>Your island</td>
                    <td>Blocks, basic gear, tools</td>
                  </tr>
                  <tr>
                    <td>Gold</td>
                    <td>Your island</td>
                    <td>Armor, bows, utility items</td>
                  </tr>
                  <tr>
                    <td>Diamond</td>
                    <td>Mid map</td>
                    <td>Team upgrades, strong gear</td>
                  </tr>
                  <tr>
                    <td>Emerald</td>
                    <td>Mid map</td>
                    <td>Potions, pearls, obsidian</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <h3 className="guide-subhead">Item shop</h3>
            <ul className="guide-list">
              {SHOP_CATEGORIES.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
            <p className="muted">
              Wool from the shop is automatically dyed to your team color. Quick-buy and category
              tabs work like Hypixel-style BedWars.
            </p>

            <h3 className="guide-subhead">Team upgrades</h3>
            <p>
              Open with <code>/upgrades</code> or the upgrade villager. Upgrades apply to your whole
              team for that match only.
            </p>
            <div className="guide-table-wrap">
              <table className="guide-table">
                <thead>
                  <tr>
                    <th>Upgrade</th>
                    <th>Effect</th>
                    <th>Cost</th>
                  </tr>
                </thead>
                <tbody>
                  {TEAM_UPGRADES.map((u) => (
                    <tr key={u.name}>
                      <td>{u.name}</td>
                      <td>{u.effect}</td>
                      <td>{u.cost}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <h3 className="guide-subhead">Spectating</h3>
            <p>
              If you join during a live match, or you are eliminated, you enter spectator mode: fly,
              invisible to fighters, no block interaction. Use <code>/bet</code> during lobby if you
              have a linked account with balance.
            </p>
          </GuideSection>

          <GuideSection id="rewards" title="Rewards & payouts">
            <h3 className="guide-subhead">Player reward pool</h3>
            <p>
              When a match goes live, the backend snapshots the available reward pool from the house
              wallet. The <strong>winning team splits it equally</strong> — four equal shares in the
              default 4v4v4v4 format.
            </p>
            <div className="guide-callout guide-callout--success">
              <strong>Payout order</strong>
              <ol>
                <li>
                  <code>/setwallet</code> address → on-chain SOL transfer
                </li>
                <li>No wallet set → credited to your custodial balance on the Arena page (withdraw
                  anytime)</li>
              </ol>
            </div>
            <p>
              Check the current pool size on the{' '}
              <Link to="/play">Arena dashboard</Link> under &quot;Player reward pool&quot;.
            </p>

            <h3 className="guide-subhead">Win cooldown</h3>
            <p>
              After winning, you cannot queue for the <em>next</em> match. You will spectate that
              round instead. This keeps the queue rotating and prevents the same squad from farming
              every game.
            </p>

            <h3 className="guide-subhead">Custodial balance (web)</h3>
            <p>
              The Arena page gives you a personal deposit address. Send devnet SOL there — it credits
              within about 15 seconds. Use this balance for spectator bets and withdrawals. Fighter
              match rewards go to <code>/setwallet</code> first, not necessarily this deposit
              address.
            </p>
          </GuideSection>

          <GuideSection id="betting" title="Spectator betting">
            <div className="guide-callout guide-callout--warn">
              Optional — you do not need to bet to play or enjoy the server.
            </div>
            <h3 className="guide-subhead">How to bet</h3>
            <ol className="guide-steps guide-steps--compact">
              <li>
                Link your account on the <Link to="/play">Arena page</Link> and deposit devnet SOL.
              </li>
              <li>
                While phase is <strong>Lobby</strong>, run{' '}
                <code>/bet &lt;green|blue|red|yellow&gt; &lt;amountSol&gt;</code> in-game.
              </li>
              <li>Watch live odds on the Arena dashboard — they update as bets come in.</li>
              <li>If your team wins, your payout credits to your custodial balance.</li>
            </ol>

            <h3 className="guide-subhead">Rules</h3>
            <ul className="guide-list">
              <li>Betting closes when the match starts.</li>
              <li>Queued fighters cannot bet on their own match.</li>
              <li>Minimum bet 0.01 SOL, maximum 100 SOL (defaults).</li>
              <li>Multiple bets on the same team add to your stake.</li>
            </ul>

            <h3 className="guide-subhead">Parimutuel odds</h3>
            <p>
              Odds are not fixed. They shift as people bet. If Green has less money staked on it,
              Green bettors get a higher multiplier if Green wins — but only winners get paid.
            </p>
            <p className="muted">
              Winners split <strong>95%</strong> of the total pool proportional to stake. House rake
              is 5%. If nobody bet on the winning team, all bets are refunded with no rake.
            </p>
          </GuideSection>

          <GuideSection id="commands" title="Commands">
            <div className="guide-table-wrap">
              <table className="guide-table guide-table--commands">
                <thead>
                  <tr>
                    <th>Command</th>
                    <th>What it does</th>
                  </tr>
                </thead>
                <tbody>
                  {PLAYER_COMMANDS.map((c) => (
                    <tr key={c.cmd}>
                      <td>
                        <code>{c.cmd}</code>
                      </td>
                      <td>{c.desc}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="muted">
              Team names for bets are <code>green</code>, <code>blue</code>, <code>red</code>,{' '}
              <code>yellow</code> (case-insensitive).
            </p>
          </GuideSection>

          <GuideSection id="parties" title="Parties">
            <p>
              Queue with friends on the same team using the party system (max 4 players, same as
              team size).
            </p>
            <ol className="guide-steps guide-steps--compact">
              <li>
                <code>/party invite &lt;player&gt;</code> — send an invite
              </li>
              <li>
                <code>/party accept</code> — join the party
              </li>
              <li>When anyone in the party queues, the whole party joins together.</li>
              <li>At match start, the backend tries to put the party on the same team.</li>
            </ol>
            <p className="muted">
              Leave with <code>/party leave</code>. List members with <code>/party list</code>.
            </p>
          </GuideSection>

          <GuideSection id="faq" title="FAQ">
            <dl className="guide-faq">
              {FAQ_ITEMS.map((item) => (
                <div key={item.q} className="guide-faq-item">
                  <dt>{item.q}</dt>
                  <dd>{item.a}</dd>
                </div>
              ))}
            </dl>
          </GuideSection>

          <section className="guide-cta mc-panel">
            <h2>Ready to play?</h2>
            <p className="muted">
              Set your wallet, link your account, and join{' '}
              <code>{MC_SERVER}</code>.
            </p>
            <div className="guide-cta-actions">
              <Link to="/play" className="btn btn-primary btn-lg">
                Open Arena Dashboard
              </Link>
              <Link to="/" className="btn btn-secondary btn-lg">
                Back to home
              </Link>
            </div>
          </section>
        </div>
      </div>
    </Layout>
  );
}
