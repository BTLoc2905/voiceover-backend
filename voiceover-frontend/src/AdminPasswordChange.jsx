import { useState } from "react";
import { validatePasswordChange } from "./adminPassword.js";

export default function AdminPasswordChange({ api, required, onChanged, onLogout, onCancel }) {
  const [currentPassword, setCurrent] = useState("");
  const [newPassword, setNew] = useState("");
  const [confirmPassword, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (event) => {
    event.preventDefault();
    const invalid = validatePasswordChange(currentPassword, newPassword, confirmPassword);
    setError(invalid);
    if (invalid || busy) return;
    setBusy(true);
    try {
      const account = await api("/api/admin/auth/password", {
        method: "POST", body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
      });
      onChanged(account);
    } catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  };
  return <main className="admin-login"><section className="login-card">
    <h1>Đổi mật khẩu Admin</h1>
    <p className="muted">{required ? "Mật khẩu đã hết hạn. Bạn phải đổi mật khẩu để tiếp tục quản trị." : "Bạn có thể đổi mật khẩu trước khi hết hạn."} Mật khẩu có hiệu lực trong 24 giờ kể từ lần đổi gần nhất.</p>
    {error && <div className="alert error" role="alert">{error}</div>}
    <form onSubmit={submit}>
      <label>Mật khẩu hiện tại<input type="password" autoComplete="current-password" value={currentPassword} onChange={e => setCurrent(e.target.value)} required disabled={busy} /></label>
      <label>Mật khẩu mới<input type="password" autoComplete="new-password" minLength={8} value={newPassword} onChange={e => setNew(e.target.value)} required disabled={busy} /></label>
      <label>Xác nhận mật khẩu mới<input type="password" autoComplete="new-password" minLength={8} value={confirmPassword} onChange={e => setConfirm(e.target.value)} required disabled={busy} /></label>
      <button className="primary" disabled={busy}>{busy ? "Đang đổi mật khẩu…" : "Lưu mật khẩu mới"}</button>
    </form>
    {!required && <button type="button" className="secondary" onClick={onCancel} disabled={busy}>Quay lại</button>}
    <button type="button" className="secondary" disabled={busy} onClick={() => onLogout().catch(reason => setError(reason.message))}>Đăng xuất</button>
  </section></main>;
}
