import vi from "./guestUi.vi.json";
import en from "./guestUi.en.json";
import ar from "./guestUi.ar.json";
import de from "./guestUi.de.json";
import es from "./guestUi.es.json";
import fr from "./guestUi.fr.json";
import hi from "./guestUi.hi.json";
import id from "./guestUi.id.json";
import it from "./guestUi.it.json";
import ja from "./guestUi.ja.json";
import ko from "./guestUi.ko.json";
import ms from "./guestUi.ms.json";
import pt from "./guestUi.pt.json";
import ru from "./guestUi.ru.json";
import th from "./guestUi.th.json";
import zhCN from "./guestUi.zh-CN.json";

export const guestCatalogs = { vi, en, ar, de, es, fr, hi, id, it, ja, ko, ms, pt, ru, th, "zh-CN": zhCN };

export function guestText(language, key) {
  return guestCatalogs[language]?.[key] || en[key] || key;
}
