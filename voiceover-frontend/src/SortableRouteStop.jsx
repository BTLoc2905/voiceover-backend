import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";

function DragDots() {
  return <svg width="18" height="24" viewBox="0 0 18 24" aria-hidden="true" focusable="false">
    {[5, 12, 19].flatMap((y) => [5, 13].map((x) => <circle key={`${x}-${y}`} cx={x} cy={y} r="1.6" fill="currentColor" />))}
  </svg>;
}

export default function SortableRouteStop({ poi, index, name, detail, disabled, menuOpen, menuAbove, canRemove, onToggleMenu, onRemove, t }) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({ id: poi.id, disabled });
  return <li ref={setNodeRef} className={`route-stop-row sortable-stop${isDragging ? " is-dragging" : ""}`}
    style={{ transform: CSS.Transform.toString(transform), transition }}>
    <button type="button" ref={setActivatorNodeRef} className="route-drag-handle"
      {...attributes} {...listeners} aria-label={`${t("dragToReorder")}: ${name}`} disabled={disabled}><DragDots /></button>
    <span className="stop-number">{index + 1}</span>
    <div className="route-stop-name"><strong>{name}</strong><small>{detail}</small></div>
    <div className="route-stop-menu">
      <button type="button" className="route-more-button" aria-label={`${t("moreOptions")}: ${name}`}
        aria-expanded={menuOpen} disabled={disabled} onClick={onToggleMenu}>⋮</button>
      {menuOpen && <div className={`route-stop-menu-popover${menuAbove ? " above" : ""}`}>
        <button type="button" disabled={!canRemove || disabled} onClick={onRemove}>{t("removeFromRoute")}</button>
        {!canRemove && <small>{t("minTwoStops")}</small>}
      </div>}
    </div>
  </li>;
}
