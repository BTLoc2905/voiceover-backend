import { useEffect, useState } from "react";
import { watchGuestLocation } from "./guestLocation.js";
import { guestText } from "./guestUi.js";

export default function useTourLocation(language = "vi") {
  const [state, setState] = useState({ position: null, code: "waitingGps" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    return watchGuestLocation({ geolocation: navigator.geolocation, onChange: setState });
  }, [attempt]);
  return { position: state.position, gpsError: state.code ? guestText(language, state.code) : "", retryGps: () => setAttempt(value => value + 1) };
}
