import { guestText } from "./guestUi.js";

/** Text nodes keep admin content safe; source content is immediately visible while translation loads. */
export function createPoiCard({ poi, name, language, distance, onListen, loader }) {
  const t = key => guestText(language, key);
  const entry = document.createElement("section"); entry.className = "guest-poi-card";
  if (typeof poi.thumbnailUrl === "string" && /^(https?:\/\/|\/images\/)/i.test(poi.thumbnailUrl)) {
    const image = document.createElement("img"); image.src = poi.thumbnailUrl; image.alt = name;
    image.addEventListener("error", () => image.remove()); entry.append(image);
  }
  const heading = document.createElement("h3"); heading.textContent = name; entry.append(heading);
  if (Number.isFinite(distance)) { const label = document.createElement("p"); label.textContent = `${Math.round(distance)} m`; entry.append(label); }
  const description = document.createElement("p"); description.className = "guest-poi-description";
  description.textContent = poi.descriptionSource || t("noContent"); description.lang = poi.sourceLanguageCode || language;
  const status = document.createElement("small"); status.setAttribute("role", "status");
  status.textContent = description.lang !== language ? `${t("descriptionFallback")} (${description.lang})` : "";
  entry.append(description, status);
  if (onListen) {
    const button = document.createElement("button"); button.type = "button"; button.className = "map-listen-button";
    button.textContent = t("listenToNarration"); button.addEventListener("click", () => onListen(poi.id)); entry.append(button);
  }
  const load = () => {
    status.textContent = t("loadingNarration");
    loader.load(poi, language).then(content => {
      if (!entry.isConnected) return;
      description.textContent = content.text || t("noContent"); description.lang = content.language || language;
      status.textContent = description.lang !== language ? `${t("descriptionFallback")} (${description.lang})` : "";
    }).catch(error => { if (entry.isConnected && error.name !== "AbortError") status.textContent = t("requestFailed"); });
  };
  return { entry, load };
}
