/** One in-flight request, bounded lifetime, and cleanup safe under React StrictMode. */
export function startDashboardPolling({ load, onData, onError, onLoading,
  intervalMs = 5000, timeoutMs = 4500, setTimer = setTimeout, clearTimer = clearTimeout,
  setRepeating = setInterval, clearRepeating = clearInterval }) {
  let disposed = false;
  let current = null;
  let currentTimeout = null;
  function refresh() {
    if (disposed || current) return false;
    const controller = new AbortController();
    current = controller;
    onLoading(true);
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true;
      clearTimer(timeout);
      if (current === controller) current = null;
      if (!disposed) onLoading(false);
    };
    const timeout = setTimer(() => {
      if (disposed || finished) return;
      controller.abort();
      onError("Monitoring phản hồi quá lâu. Hệ thống sẽ tự thử lại.");
      finish();
    }, timeoutMs);
    currentTimeout = timeout;
    Promise.resolve().then(() => {
      if (disposed || controller.signal.aborted) return;
      return load(controller.signal);
    }).then(data => {
      if (!disposed && !finished && !controller.signal.aborted) onData(data);
    }).catch(error => {
      if (!disposed && !finished && !controller.signal.aborted) onError(error.message || "Monitoring tạm thời không khả dụng.");
    }).finally(finish);
    return true;
  }
  refresh();
  const timer = setRepeating(refresh, intervalMs);
  return { refresh, dispose() {
    disposed = true;
    clearRepeating(timer);
    current?.abort();
    clearTimer(currentTimeout);
  } };
}
