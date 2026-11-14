/* Apply the platform class and Android light/dark preference before first paint. */
(() => {
  try {
    if (/Electron/i.test(navigator.userAgent)) {
      document.documentElement.classList.add('is-electron');
      const saved = window.localStorage.getItem('banbiao.theme');
      document.documentElement.dataset.theme =
        saved === 'light' || saved === 'dark'
          ? saved
          : window.matchMedia('(prefers-color-scheme: dark)').matches
            ? 'dark'
            : 'light';
      return;
    }
    if (!/Android/i.test(navigator.userAgent)) return;
    document.documentElement.classList.add('is-android');
    const saved = window.localStorage.getItem('banbiao.theme');
    const followsSystem =
      saved !== 'light' && saved !== 'dark'
        ? window.matchMedia('(prefers-color-scheme: dark)').matches
        : false;
    const theme = saved === 'light' || saved === 'dark' ? saved : followsSystem ? 'dark' : 'light';
    document.documentElement.dataset.theme = theme;
    if (window.BanbiaoNative && typeof window.BanbiaoNative.setDark === 'function') {
      window.BanbiaoNative.setDark(theme === 'dark');
    }
  } catch (error) {
    // The app still works with the system appearance.
  }
})();
