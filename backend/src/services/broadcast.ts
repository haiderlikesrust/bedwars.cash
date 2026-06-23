import type { TeamColor } from '../config.js';

export interface BroadcastCamera {
  team: TeamColor;
  playerName: string;
}

let camera: BroadcastCamera | null = null;

export function setBroadcastCamera(team: TeamColor, playerName: string): BroadcastCamera {
  camera = { team, playerName };
  return camera;
}

export function clearBroadcastCamera(): void {
  camera = null;
}

export function getBroadcastCamera(): BroadcastCamera | null {
  return camera;
}

export function streamPublicView(url: string | null) {
  return {
    url: url && url.length > 0 ? url : null,
    camera,
  };
}
