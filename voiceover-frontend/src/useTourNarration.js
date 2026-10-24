import { useEffect, useState, useSyncExternalStore } from "react";
import { AutoNarration, distanceMeters, goodFix } from "./autoNarration.js";
import { NarrationPlayer } from "./narrationPlayer.js";
import { anonymousClientId, getGuide, GuidePreloader, recordPlaybackEvent } from "./narrationApi.js";

function createRuntime() {
  let storage;
  try { storage = window.localStorage; } catch { /* Use memory if storage is blocked. */ }
  const engine = new AutoNarration({ storage });
  const clientId = anonymousClientId();
  const preloader = new GuidePreloader();
  const player = new NarrationPlayer({ getGuide, recordEvent: (event) => recordPlaybackEvent(event, clientId),
    canAutoStart: (id, now) => !document.hidden && engine.canStart(id, now),
    onStarted: (id, now, trigger) => engine.started(id, now, trigger === "AUTO_PLAY"),
    onFinished: (id, reason) => { if (reason === "skip") engine.skip(id); engine.release(id, Date.now(), reason === "error"); },
  });
  return { engine, player, preloader };
}

export default function useTourNarration(position, catalog, language) {
  const [runtime] = useState(createRuntime);
  const playback = useSyncExternalStore(runtime.player.subscribe, runtime.player.getSnapshot);
  useEffect(() => {
    const poll = () => {
      const now = Date.now();
      runtime.engine.update(position, catalog, now);
      const ready = !!goodFix(position, now);
      if (ready && !document.hidden) runtime.preloader.preload(catalog.filter((poi) => distanceMeters(position, poi) <= 300)
        .sort((a, b) => distanceMeters(position, a) - distanceMeters(position, b)), language);
      if (document.hidden) { runtime.engine.resetDwell(); return; }
      if (runtime.engine.reserved != null && !runtime.player.started && !runtime.engine.canStart(runtime.engine.reserved, now)) {
        runtime.player.stop(false);
      }
      const next = runtime.engine.reserveNext(now, runtime.player.isBusy());
      if (next) void runtime.player.load(next.id, language, { autoplay: true, trigger: "AUTO_PLAY" });
    };
    const timer = window.setInterval(poll, 250);
    return () => window.clearInterval(timer);
  }, [runtime, position, catalog, language]);
  useEffect(() => {
    const onVisibility = () => { runtime.engine.resetDwell(); };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [runtime]);
  useEffect(() => () => { runtime.player.dispose(); runtime.preloader.clear(); runtime.engine.resetVisits(); }, [runtime]);
  return { player: runtime.player, playback };
}
