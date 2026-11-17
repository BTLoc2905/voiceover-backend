export function validatePasswordChange(currentPassword, newPassword, confirmation) {
  if (!currentPassword) return "Hãy nhập mật khẩu hiện tại.";
  if (newPassword.length < 8 || new TextEncoder().encode(newPassword).length > 72) {
    return "Mật khẩu mới phải có ít nhất 8 ký tự và tối đa 72 byte.";
  }
  if (newPassword !== confirmation) return "Xác nhận mật khẩu không khớp.";
  if (newPassword === currentPassword) return "Mật khẩu mới phải khác mật khẩu hiện tại.";
  return "";
}

export function watchPasswordExpiry(account, onExpired, {
  now = Date.now, setTimer = setTimeout, clearTimer = clearTimeout,
} = {}) {
  if (!account || account.passwordChangeRequired || !account.passwordExpiresAt) return () => {};
  const expires = Date.parse(account.passwordExpiresAt);
  if (!Number.isFinite(expires)) return () => {};
  const timer = setTimer(onExpired, Math.max(0, expires - now()));
  return () => clearTimer(timer);
}
