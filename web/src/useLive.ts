import { useEffect, useRef, useState } from 'react';
import { WS_URL } from './api';
import type { Odds, PublicState } from './types';

export interface LiveData {
  state: PublicState | null;
  odds: Odds | null;
  notices: string[];
  connected: boolean;
}

// Subscribes to the backend's live state/odds/notice WebSocket feed.
export function useLive(): LiveData {
  const [state, setState] = useState<PublicState | null>(null);
  const [odds, setOdds] = useState<Odds | null>(null);
  const [notices, setNotices] = useState<string[]>([]);
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    let stopped = false;
    let retry: ReturnType<typeof setTimeout>;

    const connect = () => {
      const ws = new WebSocket(WS_URL);
      wsRef.current = ws;
      ws.onopen = () => setConnected(true);
      ws.onclose = () => {
        setConnected(false);
        if (!stopped) retry = setTimeout(connect, 2000);
      };
      ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data as string);
          if (msg.kind === 'state') {
            setState(msg.state);
            if (msg.state?.odds) setOdds(msg.state.odds);
          } else if (msg.kind === 'odds') {
            setOdds(msg.odds);
          } else if (msg.kind === 'notice') {
            setNotices((prev) => [msg.message, ...prev].slice(0, 6));
          } else if (msg.kind === 'camera' && msg.stream) {
            setState((prev) => (prev ? { ...prev, stream: msg.stream } : prev));
          }
        } catch {
          /* ignore malformed */
        }
      };
    };
    connect();

    return () => {
      stopped = true;
      clearTimeout(retry);
      wsRef.current?.close();
    };
  }, []);

  return { state, odds, notices, connected };
}
