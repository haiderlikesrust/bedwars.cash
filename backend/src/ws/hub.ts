import type { FastifyInstance } from 'fastify';
import type WebSocket from 'ws';
import { config } from '../config.js';
import type { JoinAction, PluginInbound, PluginOutbound } from '../types.js';
import {
  abortMatch,
  buildOdds,
  handlePlayerJoin,
  joinQueue,
  leaveQueue,
  matchEvents,
  forceAbortCurrent,
  forceStartMatch,
  placeBet,
  publicState,
  queueUuids,
  reportResult,
} from '../services/match.js';
import {
  consumeLinkCode,
  getOrCreateByUuid,
  recordCheatFlag,
  setPayoutWallet,
} from '../services/accounts.js';
import {
  clearBroadcastCamera,
  setBroadcastCamera,
  streamPublicView,
} from '../services/broadcast.js';
import {
  partyAccept,
  partyInvite,
  partyLeave,
  partyList,
} from '../services/party.js';

let pluginSocket: WebSocket | null = null;
const webClients = new Set<WebSocket>();

function sendToPlugin(msg: PluginOutbound): void {
  if (pluginSocket && pluginSocket.readyState === pluginSocket.OPEN) {
    pluginSocket.send(JSON.stringify(msg));
  }
}

function broadcastWeb(payload: unknown): void {
  const data = JSON.stringify(payload);
  for (const ws of webClients) {
    if (ws.readyState === ws.OPEN) ws.send(data);
  }
}

// Bridge match events out to the plugin and web clients.
matchEvents.on('start_match', (m) => sendToPlugin({ type: 'start_match', matchId: m.matchId, teams: m.teams }));
matchEvents.on('odds', (odds) => {
  sendToPlugin({ type: 'odds', odds });
  broadcastWeb({ kind: 'odds', odds });
});
matchEvents.on('state', (state) => {
  sendToPlugin({ type: 'state', state });
  broadcastWeb({ kind: 'state', state });
});
matchEvents.on('notice', (n) => {
  sendToPlugin({ type: 'notice', message: n.message, mcUuid: n.mcUuid });
  broadcastWeb({ kind: 'notice', message: n.message });
});

function sendJoinAction(
  mcUuid: string,
  action: JoinAction,
  message: string,
  extra?: { queueSize?: number; queueCapacity?: number; phase?: import('../types.js').MatchPhase },
): void {
  sendToPlugin({
    type: 'join_action',
    mcUuid,
    action,
    message,
    queueSize: extra?.queueSize,
    queueCapacity: extra?.queueCapacity,
    phase: extra?.phase,
  });
}

async function handlePluginMessage(raw: string): Promise<void> {
  let msg: PluginInbound;
  try {
    msg = JSON.parse(raw) as PluginInbound;
  } catch {
    return;
  }
  switch (msg.type) {
    case 'hello':
      sendToPlugin({ type: 'welcome' });
      break;
    case 'setwallet': {
      setPayoutWallet(msg.mcUuid, msg.mcUsername, msg.address);
      sendToPlugin({ type: 'ack', ok: true, message: 'Payout wallet saved.' });
      break;
    }
    case 'verify': {
      const userId = consumeLinkCode(msg.code.toUpperCase(), msg.mcUuid, msg.mcUsername);
      sendToPlugin({
        type: 'ack',
        ok: userId != null,
        message: userId != null ? 'Account linked to website.' : 'Invalid or expired code.',
      });
      break;
    }
    case 'player_join': {
      const r = handlePlayerJoin(msg.mcUuid, msg.mcUsername);
      sendJoinAction(msg.mcUuid, r.action, r.message, {
        queueSize: r.queueSize,
        queueCapacity: r.queueCapacity,
        phase: r.phase,
      });
      break;
    }
    case 'queue_join': {
      const r = joinQueue(msg.mcUuid);
      const action: JoinAction = r.action ?? (r.ok ? 'queued' : 'denied');
      sendJoinAction(msg.mcUuid, action, r.message, {
        queueSize: r.queueSize,
        queueCapacity: r.queueCapacity,
        phase: r.phase,
      });
      break;
    }
    case 'queue_leave':
      leaveQueue(msg.mcUuid);
      break;
    case 'place_bet': {
      const user = getOrCreateByUuid(msg.mcUuid, msg.mcUuid.slice(0, 8));
      const r = placeBet(user.id, msg.team, msg.amountSol);
      sendToPlugin({ type: 'notice', mcUuid: msg.mcUuid, message: r.message });
      break;
    }
    case 'match_result':
      await reportResult(msg.matchId, msg.winningTeam, msg.winnerUuids);
      break;
    case 'match_aborted':
      abortMatch(msg.matchId, msg.reason);
      break;
    case 'cheat_flag':
      recordCheatFlag(msg.mcUuid, msg.check, msg.details);
      break;
    case 'force_start': {
      if (!queueUuids().includes(msg.mcUuid)) joinQueue(msg.mcUuid);
      const r = await forceStartMatch();
      sendToPlugin({ type: 'notice', mcUuid: msg.mcUuid, message: r.message });
      break;
    }
    case 'force_abort': {
      const r = forceAbortCurrent(msg.reason ?? 'Admin abort');
      sendToPlugin({ type: 'notice', mcUuid: msg.mcUuid, message: r.message });
      break;
    }
    case 'match_camera': {
      const camera = setBroadcastCamera(msg.team, msg.playerName);
      broadcastWeb({ kind: 'camera', stream: streamPublicView(config.stream.url) });
      sendToPlugin({ type: 'ack', ok: true, message: `Camera on ${camera.playerName} (${camera.team}).` });
      break;
    }
    case 'match_camera_clear':
      clearBroadcastCamera();
      broadcastWeb({ kind: 'camera', stream: streamPublicView(config.stream.url) });
      break;
    case 'party_invite': {
      const r = partyInvite(msg.mcUuid, msg.targetUuid);
      sendToPlugin({ type: 'notice', mcUuid: msg.mcUuid, message: r.message });
      if (r.ok) {
        sendToPlugin({
          type: 'notice',
          mcUuid: msg.targetUuid,
          message: `${msg.leaderName} invited you to their party. Use /party accept`,
        });
      }
      break;
    }
    case 'party_accept': {
      const r = partyAccept(msg.mcUuid);
      sendToPlugin({ type: 'notice', mcUuid: msg.mcUuid, message: r.message });
      break;
    }
    case 'party_leave': {
      const r = partyLeave(msg.mcUuid);
      sendToPlugin({ type: 'notice', mcUuid: msg.mcUuid, message: r.message });
      break;
    }
    case 'party_list': {
      const r = partyList(msg.mcUuid);
      sendToPlugin({ type: 'notice', mcUuid: msg.mcUuid, message: r.message });
      break;
    }
  }
}

export function registerWs(app: FastifyInstance): void {
  app.get('/ws/plugin', { websocket: true }, (socket, req) => {
    const token = (req.query as { token?: string })?.token;
    if (token !== config.pluginToken) {
      socket.close(1008, 'bad token');
      return;
    }
    pluginSocket = socket;
    app.log.info('Minecraft plugin connected');
    sendToPlugin({ type: 'welcome' });
    sendToPlugin({ type: 'odds', odds: buildOdds() });
    sendToPlugin({ type: 'state', state: publicState() });
    socket.on('message', (data: Buffer) => void handlePluginMessage(data.toString()));
    socket.on('close', () => {
      if (pluginSocket === socket) pluginSocket = null;
      app.log.warn('Minecraft plugin disconnected');
    });
  });

  app.get('/ws/web', { websocket: true }, (socket) => {
    webClients.add(socket);
    socket.send(JSON.stringify({ kind: 'state', state: publicState() }));
    socket.on('close', () => webClients.delete(socket));
  });
}

export function pluginConnected(): boolean {
  return pluginSocket != null && pluginSocket.readyState === pluginSocket.OPEN;
}
