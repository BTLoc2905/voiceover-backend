import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "./SessionRoutes.css";

export default function OrderedRouteMap({ pois, geometry, selectedIndex, onSelect, location }) {
  const container = useRef(null);
  const map = useRef(null);
  const select = useRef(onSelect);
  const locationLayer = useRef(null);
  useEffect(() => { select.current = onSelect; }, [onSelect]);
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
      const label = document.createElement("span"); label.textContent = `${index + 1}. ${poi.name}`;
      const marker = L.marker([Number(poi.latitude), Number(poi.longitude)], {
        title: `${index + 1}. ${poi.name}`, keyboard: true,
        icon: L.divIcon({ className: `ordered-marker${selectedIndex === index ? " selected" : ""}`,
          html: `<span>${index + 1}</span>`, iconSize: [30, 30], iconAnchor: [15, 15] }),
      }).bindTooltip(label).addTo(group);
      marker.on("click", () => select.current?.(index));
      if (selectedIndex === index) marker.openTooltip();
    });
    if (geometry?.type === "LineString") L.geoJSON(geometry, { style: { color: "#356ff2", weight: 5 } }).addTo(group);
    if (group.getBounds().isValid()) instance.fitBounds(group.getBounds(), { padding: [35, 35], maxZoom: 16, animate: false });
    return () => group.remove();
  }, [pois, geometry, selectedIndex]);
  useEffect(() => {
    const instance = map.current;
    locationLayer.current?.remove();
    locationLayer.current = null;
    if (instance && location) {
      locationLayer.current = L.circleMarker([location.lat, location.lng], {
        radius: 7, color: "white", fillColor: "#13946a", fillOpacity: 1,
      }).bindTooltip("Vị trí của bạn").addTo(instance);
    }
    return () => {
      locationLayer.current?.remove();
      locationLayer.current = null;
    };
  }, [location]);
  return <div ref={container} className="ordered-route-map" aria-label="Bản đồ thứ tự POI" />;
}
