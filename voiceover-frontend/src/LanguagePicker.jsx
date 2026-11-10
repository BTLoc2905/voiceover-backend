import { useState } from "react";
import { LANGUAGE_OPTIONS, languageLabel } from "./languageDisplay.js";
import { guestText } from "./guestUi.js";

export default function LanguagePicker({ language, onChange }) {
  const [open, setOpen] = useState(false);

  return <div className="language-picker">
    <button type="button" className="language-chip" aria-label={guestText(language, "narrationLanguage")} aria-expanded={open} onClick={() => setOpen((value) => !value)}>
      {languageLabel(language)}⌄
    </button>
    {open && <div className="language-menu">
      {LANGUAGE_OPTIONS.map(({ code, label }) => <button type="button" key={code} aria-current={language === code ? "true" : undefined} onClick={() => {
        onChange(code);
        setOpen(false);
      }}>{label}</button>)}
    </div>}
  </div>;
}
