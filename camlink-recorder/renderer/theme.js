// Theme: remembers your choice, otherwise follows the Windows light/dark setting
(function () {
  const KEY = 'camlink-theme';
  function systemTheme() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }
  function saved() {
    try { return localStorage.getItem(KEY); } catch (_) { return null; }
  }
  function apply(theme) {
    document.documentElement.setAttribute('data-theme', theme);
  }
  apply(saved() || systemTheme());

  document.addEventListener('DOMContentLoaded', function () {
    const btn = document.getElementById('theme-toggle');
    if (!btn) return;
    btn.addEventListener('click', function () {
      const next = document.documentElement.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
      apply(next);
      try { localStorage.setItem(KEY, next); } catch (_) {}
    });
  });
})();
