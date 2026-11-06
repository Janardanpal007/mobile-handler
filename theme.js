// Light / dark theme switch. Light is the default; the choice is remembered
// per browser. Storage can be blocked (private mode, disabled site data), so
// every storage call is guarded and the page still works without it.
(function () {
  const STORAGE_KEY = 'mh-theme';
  const THEMES = { light: 'light', dark: 'dark' };
  const root = document.documentElement;

  function readSavedTheme() {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch (error) {
      return null;
    }
  }

  function saveTheme(theme) {
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch (error) {
      // Storage unavailable: the theme still applies for this visit.
    }
  }

  function applyTheme(theme) {
    root.dataset.theme = theme;
  }

  function currentTheme() {
    return root.dataset.theme === THEMES.dark ? THEMES.dark : THEMES.light;
  }

  // Runs in <head>, so the saved theme is applied before the page paints.
  applyTheme(readSavedTheme() === THEMES.dark ? THEMES.dark : THEMES.light);

  document.addEventListener('DOMContentLoaded', function () {
    const toggle = document.getElementById('theme-toggle');
    if (!toggle) return;

    function syncToggle() {
      const isDark = currentTheme() === THEMES.dark;
      const label = isDark ? 'Switch to light theme' : 'Switch to dark theme';
      toggle.setAttribute('aria-pressed', String(isDark));
      toggle.setAttribute('aria-label', label);
      toggle.title = label;
      toggle.querySelector('span').textContent = isDark ? '☀' : '☾';
    }

    toggle.addEventListener('click', function () {
      const next = currentTheme() === THEMES.dark ? THEMES.light : THEMES.dark;
      applyTheme(next);
      saveTheme(next);
      syncToggle();
    });

    syncToggle();
  });
})();
