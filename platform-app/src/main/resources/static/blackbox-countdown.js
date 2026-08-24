(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.BlackboxCountdown = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  function startCountdown(seconds, onTick, schedule, cancel) {
    let remaining = Math.max(0, Math.floor(seconds));
    let cancelled = false;
    let timerId;

    function tick() {
      if (cancelled) return;
      onTick(remaining);
      if (remaining === 0) return;
      timerId = schedule(() => {
        remaining -= 1;
        tick();
      }, 1000);
    }

    tick();
    return () => {
      cancelled = true;
      if (timerId != null && cancel) cancel(timerId);
    };
  }

  return { startCountdown };
}));
