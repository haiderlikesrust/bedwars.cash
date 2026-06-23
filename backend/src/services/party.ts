import { config, type TeamColor } from '../config.js';

const TEAM_COLORS: TeamColor[] = ['GREEN', 'BLUE', 'RED', 'YELLOW'];

/** leader uuid -> member uuids (includes leader) */
const parties = new Map<string, Set<string>>();
/** member uuid -> leader uuid */
const memberOf = new Map<string, string>();
/** invitee uuid -> leader uuid */
const invites = new Map<string, string>();

function partySize(leader: string): number {
  return parties.get(leader)?.size ?? 0;
}

export function partyFor(uuid: string): string[] | null {
  const leader = memberOf.get(uuid) ?? (parties.has(uuid) ? uuid : null);
  if (!leader || !parties.has(leader)) return null;
  return [...parties.get(leader)!];
}

export function partyInvite(leader: string, target: string): { ok: boolean; message: string } {
  if (leader === target) return { ok: false, message: 'You cannot invite yourself.' };
  const max = config.game.teamSize;
  const leaderParty = memberOf.get(leader) ?? (parties.has(leader) ? leader : null);
  const actualLeader = leaderParty ?? leader;

  if (!parties.has(actualLeader)) {
    parties.set(actualLeader, new Set([actualLeader]));
    memberOf.set(actualLeader, actualLeader);
  }
  if (memberOf.has(target)) return { ok: false, message: 'That player is already in a party.' };
  if (partySize(actualLeader) >= max) return { ok: false, message: `Party is full (${max}).` };

  invites.set(target, actualLeader);
  return { ok: true, message: `Invited player to your party (${partySize(actualLeader)}/${max}).` };
}

export function partyAccept(uuid: string): { ok: boolean; message: string } {
  const leader = invites.get(uuid);
  if (!leader || !parties.has(leader)) {
    return { ok: false, message: 'No pending party invite.' };
  }
  const max = config.game.teamSize;
  if (partySize(leader) >= max) return { ok: false, message: 'That party is now full.' };
  if (memberOf.has(uuid)) return { ok: false, message: 'Leave your current party first.' };

  parties.get(leader)!.add(uuid);
  memberOf.set(uuid, leader);
  invites.delete(uuid);
  return { ok: true, message: `Joined party (${partySize(leader)}/${max}).` };
}

export function partyLeave(uuid: string): { ok: boolean; message: string } {
  const leader = memberOf.get(uuid);
  if (!leader || !parties.has(leader)) return { ok: false, message: 'You are not in a party.' };

  parties.get(leader)!.delete(uuid);
  memberOf.delete(uuid);
  invites.delete(uuid);

  if (uuid === leader || parties.get(leader)!.size === 0) {
    for (const m of parties.get(leader) ?? []) {
      memberOf.delete(m);
      invites.delete(m);
    }
    parties.delete(leader);
    return { ok: true, message: 'Party disbanded.' };
  }
  return { ok: true, message: 'Left the party.' };
}

export function partyList(uuid: string): { ok: boolean; message: string } {
  const leader = memberOf.get(uuid) ?? (parties.has(uuid) ? uuid : null);
  if (!leader || !parties.has(leader)) return { ok: false, message: 'You are not in a party.' };
  const members = [...parties.get(leader)!];
  return { ok: true, message: `Party (${members.length}/${config.game.teamSize}): ${members.join(', ')}` };
}

export function clearPartyOnQueueLeave(uuid: string): void {
  partyLeave(uuid);
}

/** Assign queued players to teams, keeping parties together. */
export function assignTeamsWithParties(uuids: string[]): Record<string, string[]> {
  const teams: Record<string, string[]> = { GREEN: [], BLUE: [], RED: [], YELLOW: [] };
  const cap = config.game.teamSize;
  const used = new Set<string>();
  const queued = new Set(uuids);

  const groups: string[][] = [];
  const seenLeaders = new Set<string>();
  for (const uuid of uuids) {
    const leader = memberOf.get(uuid);
    if (!leader || seenLeaders.has(leader)) continue;
    const party = partyFor(leader);
    if (!party) continue;
    if (party.every((m) => queued.has(m)) && party.length <= cap) {
      groups.push(party);
      seenLeaders.add(leader);
    }
  }
  groups.sort((a, b) => b.length - a.length);

  let teamIdx = 0;
  const teamOrder = () => {
    for (let i = 0; i < TEAM_COLORS.length; i++) {
      const t = TEAM_COLORS[(teamIdx + i) % TEAM_COLORS.length];
      if (teams[t].length < cap) return t;
    }
    return TEAM_COLORS[teamIdx % TEAM_COLORS.length];
  };

  for (const group of groups) {
    let placed = false;
    for (let i = 0; i < TEAM_COLORS.length; i++) {
      const team = TEAM_COLORS[(teamIdx + i) % TEAM_COLORS.length];
      if (teams[team].length + group.length <= cap) {
        for (const m of group) {
          teams[team].push(m);
          used.add(m);
        }
        teamIdx = (teamIdx + i + 1) % TEAM_COLORS.length;
        placed = true;
        break;
      }
    }
    if (!placed) {
      for (const m of group) if (!used.has(m)) used.add(m);
    }
  }

  for (const uuid of uuids) {
    if (used.has(uuid)) continue;
    const team = teamOrder();
    if (teams[team].length >= cap) {
      teamIdx++;
      continue;
    }
    teams[team].push(uuid);
    used.add(uuid);
    teamIdx++;
  }

  return teams;
}
