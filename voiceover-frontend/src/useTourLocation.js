import { useEffect, useState } from "react";
import { watchGuestLocation } from "./guestLocation.js";
import { guestText } from "./guestUi.js";
import { demoGps, GPS_DEMO_ACTIVE } from "./demoGps.js";

export default function useTourLocation(language = "vi") {
  const [state, setState] = useState({ position: null, code: "waitingGps" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    return watchGuestLocation({ geolocation: GPS_DEMO_ACTIVE ? demoGps : navigator.geolocation, onChange: setState });
  }, [attempt]);
  return { position: state.position, gpsError: state.code ? guestText(language, state.code) : "", retryGps: () => setAttempt(value => value + 1) };
}
