export const MC_SERVER = import.meta.env.VITE_MC_SERVER ?? 'join.bedwars.cash';

export const GUIDE_SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'getting-started', label: 'Getting started' },
  { id: 'matches', label: 'Matches' },
  { id: 'playing', label: 'Playing BedWars' },
  { id: 'rewards', label: 'Rewards & payouts' },
  { id: 'betting', label: 'Spectator betting' },
  { id: 'commands', label: 'Commands' },
  { id: 'parties', label: 'Parties' },
  { id: 'faq', label: 'FAQ' },
] as const;

export const PLAYER_COMMANDS = [
  { cmd: '/setwallet <address>', desc: 'Set your Solana payout address for match winnings.' },
  { cmd: '/bwlink <code>', desc: 'Link your website account (get the code on the Arena page).' },
  { cmd: '/queue', desc: 'Join the match queue manually.' },
  { cmd: '/queue leave', desc: 'Leave the queue.' },
  { cmd: '/shop', desc: 'Open the item shop during a live match.' },
  { cmd: '/upgrades', desc: 'Open team upgrades (shared with your squad).' },
  { cmd: '/bet <team> <sol>', desc: 'Bet on Green, Blue, Red, or Yellow during lobby.' },
  { cmd: '/bets', desc: 'Open the in-game betting board.' },
  { cmd: '/party invite <player>', desc: 'Invite someone to your party (same team at start).' },
  { cmd: '/party accept', desc: 'Accept a party invite.' },
  { cmd: '/party leave', desc: 'Leave or disband your party.' },
  { cmd: '/party list', desc: 'Show who is in your party.' },
] as const;

export const MATCH_PHASES = [
  {
    phase: 'Lobby',
    desc: 'Queue fills, spectators can bet. Fighters wait in the hub or arena countdown area.',
  },
  {
    phase: 'Live',
    desc: 'Countdown ends — PvP on. Protect your bed, rush others, buy from shops and upgrades.',
  },
  {
    phase: 'Settling',
    desc: 'Winner reported. Reward pool and bets are paid out automatically.',
  },
] as const;

export const SHOP_CATEGORIES = [
  'Blocks — wool, glass, end stone, obsidian',
  'Melee — swords, knockback stick',
  'Armor — permanent chainmail, iron, diamond sets',
  'Tools — shears, tiered pickaxes and axes',
  'Ranged — bows and arrows',
  'Potions — speed, jump boost, invisibility',
  'Utility — TNT, fireballs, bridge eggs, ender pearls, and more',
] as const;

export const TEAM_UPGRADES = [
  { name: 'Sharpened Swords', effect: 'Sharpness on all swords (4 tiers)', cost: 'Diamonds' },
  { name: 'Reinforced Armor', effect: 'Protection on all armor (4 tiers)', cost: 'Diamonds' },
  { name: 'Iron Forge', effect: 'Faster iron & gold generators (4 tiers)', cost: 'Gold' },
  { name: 'Maniac Miner', effect: 'Haste while on your island (2 tiers)', cost: 'Diamonds' },
] as const;

export const FAQ_ITEMS = [
  {
    q: 'Is this real money?',
    a: 'No. BedWars.cash runs on Solana devnet with free test SOL. It is a demo platform, not mainnet gambling.',
  },
  {
    q: 'Do I need the website to play?',
    a: 'No for fighting — join the server and queue. Yes if you want to deposit, bet as a spectator, or manage withdrawals from the Arena dashboard.',
  },
  {
    q: 'How do I get paid when my team wins?',
    a: 'Run /setwallet with your Solana address before the match. When your team wins, your share of the reward pool is sent on-chain (or credited to your custodial balance if no wallet is set).',
  },
  {
    q: 'How is the reward pool split?',
    a: 'The winning team splits the pool equally — 4 players by default, one equal share each.',
  },
  {
    q: 'Can I bet and play the same match?',
    a: 'No. If you are queued for the current lobby, bets on that match are rejected to prevent collusion.',
  },
  {
    q: 'When can I place bets?',
    a: 'Only while the match phase is Lobby. Betting locks the moment the match goes live.',
  },
  {
    q: 'How do spectator odds work?',
    a: 'Odds are parimutuel — they shift as more people bet. Winners on the winning team split 95% of the pool in proportion to their stake. The house takes 5% rake. If nobody bet on the winner, everyone gets a full refund.',
  },
  {
    q: 'What happens if I win two matches in a row?',
    a: 'After winning, you sit out one match (win cooldown) and join as a spectator. You can still bet on the next match.',
  },
  {
    q: 'Can teammates hurt each other?',
    a: 'No. Friendly fire is disabled — melee, arrows, fireballs, and team-placed TNT cannot damage teammates.',
  },
  {
    q: 'What if I disconnect mid-match?',
    a: 'There is no rejoin yet. Stay connected until the match ends or you may count as eliminated when your bed is gone.',
  },
  {
    q: 'How do I get test SOL?',
    a: 'On the Arena page, deposit devnet SOL to your personal deposit address. For match reward pools, the house wallet is funded separately by the operator (devnet faucet or admin top-up).',
  },
] as const;
