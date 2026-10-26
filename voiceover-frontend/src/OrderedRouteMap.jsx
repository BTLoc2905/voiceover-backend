import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "./SessionRoutes.css";
import { guestText } from "./guestUi.js";

export default function OrderedRouteMap({ pois, geometry, activeGeometry, selectedIndex, onSelect, onListen, location, language = "vi", formatPoiName = (name) => name }) {
  const container = useRef(null);
  const map = useRef(null);
  const select = useRef(onSelect);
  const listen = useRef(onListen);
  const locationLayer = useRef(null);
  useEffect(() => { select.current = onSelect; }, [onSelect]);
  useEffect(() => { listen.current = onListen; }, [onListen]);
  useEffect(() => {
    const instance = L.map(container.current).setView([10.7769, 106.7009], 13);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>', maxZoom: 19,
    }).addTo(instance);
    map.current = instance;
    const observer = new ResizeObserver(() => instance.invalidateSize());
    observer.observe(container.current);
    return () => { observer.disconnect(); instance.remove(); map.current = null; };
  }, []);
  useEffect(() => {
    const instance = map.current;
    const group = L.featureGroup().addTo(instance);
    pois.forEach((poi, index) => {
      if (poi?.latitude == null || poi?.longitude == null || !Number.isFinite(Number(poi.latitude)) || !Number.isFinite(Number(poi.longitude)) || Math.abs(Number(poi.latitude)) > 90 || Math.abs(Number(poi.longitude)) > 180) return;
      const name = formatPoiName(poi.name, language);
      const label = document.createElement("span"); label.textContent = `${index + 1}. ${name}`;
      const marker = L.marker([Number(poi.latitude), Number(poi.longitude)], {
        title: `${index + 1}. ${name}`, keyboard: true,
        icon: L.divIcon({ className: `ordered-marker${selectedIndex === index ? " selected" : ""}`,
          html: `<span>${index + 1}</span>`, iconSize: [30, 30], iconAnchor: [15, 15] }),
      }).bindTooltip(label).addTo(group);
      if (listen.current) {
        const popup = document.createElement("div");
        popup.textContent = name;
        const button = document.createElement("button");
        button.type = "button";
        button.className = "map-listen-button";
        button.textContent = guestText(language, "listenToNarration");
        button.addEventListener("click", () => listen.current?.(poi.id));
        popup.append(document.createElement("br"), button);
        marker.bindPopup(popup);
      }
      marker.on("click", () => select.current?.(index));
      if (selectedIndex === index) marker.openTooltip();
    });
    if (geometry?.type === "LineString") L.geoJSON(geometry, { style: { color: "#356ff2", weight: 5 } }).addTo(group);
    if (activeGeometry?.type === "LineString") L.geoJSON(activeGeometry, { style: { color: "#f97316", weight: 7 } }).addTo(group);
    if (group.getBounds().isValid()) instance.fitBounds(group.getBounds(), { padding: [35, 35], maxZoom: 16, animate: false });
    return () => group.remove();
  }, [pois, geometry, activeGeometry, selectedIndex, language, formatPoiName]);
  useEffect(() => {
    const instance = map.current;
    locationLayer.current?.remove();
    locationLayer.current = null;
    if (instance && location) {
      locationLayer.current = L.circleMarker([location.lat, location.lng], {
        radius: 7, color: "white", fillColor: "#13946a", fillOpacity: 1,
      }).bindTooltip(guestText(language, "yourLocation")).addTo(instance);
    }
    return () => {
      locationLayer.current?.remove();
      locationLayer.current = null;
    };
  }, [location, language]);
  return <div ref={container} className="ordered-route-map" aria-label={guestText(language, "stopOrder")} />;
}
