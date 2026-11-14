'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('banbiaoShell', {
  updateWidget: (payload) => ipcRenderer.send('widget:data', payload),
  remind: (payload) => ipcRenderer.send('widget:remind', payload),
  dismissReminder: () => ipcRenderer.send('reminder:dismiss'),
  setTheme: (theme) => ipcRenderer.send('theme:set', theme),
});
