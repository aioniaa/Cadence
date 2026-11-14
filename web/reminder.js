'use strict';

(() => {
  const params = new URLSearchParams(window.location.search);
  const shift = (params.get('shift') || '班次').slice(0, 40);
  const end = (params.get('end') || '').slice(0, 40);
  const shiftElement = document.getElementById('shiftLabel');
  const clockElement = document.getElementById('clock');
  const dismissButton = document.getElementById('dismiss');

  const now = new Date();
  const clock = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  clockElement.textContent = clock;
  shiftElement.textContent = end ? `${shift} · ${end}` : `${shift} · 今天的班次结束了`;
  document.title = `${shift} · 下班了`;

  function dismiss() {
    if (window.banbiaoShell && typeof window.banbiaoShell.dismissReminder === 'function') {
      window.banbiaoShell.dismissReminder();
    } else {
      window.close();
    }
  }

  dismissButton.addEventListener('click', dismiss);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      dismiss();
    }
  });

  window.setTimeout(() => dismissButton.focus({ preventScroll: true }), 180);
})();
