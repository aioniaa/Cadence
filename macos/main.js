'use strict';

const {
  app,
  BrowserWindow,
  Menu,
  ipcMain,
  nativeTheme,
  protocol,
  screen,
  session,
  shell,
} = require('electron');
const fs = require('node:fs/promises');
const fsSync = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');

const APP_ORIGIN = 'app://banbiao';
const WEB_ROOT = path.join(__dirname, 'web');
const PRELOAD = path.join(__dirname, 'preload.js');

if (process.env.BANBIAO_USER_DATA) {
  app.setPath('userData', process.env.BANBIAO_USER_DATA);
}
app.setName('班表');

const MIME_TYPES = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
};

function contentType(filePath) {
  return MIME_TYPES[path.extname(filePath).toLowerCase()] || 'application/octet-stream';
}

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'app',
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      stream: true,
    },
  },
]);

let mainWindow = null;
let countdownEngine = null;
let reminderWindow = null;
let reminderBounceId = 0;
let desktopWidgetProcess = null;
let widgetWriteChain = Promise.resolve();

function backgroundColor() {
  return nativeTheme.shouldUseDarkColors ? '#0f0f11' : '#f5f5f7';
}

function showMainWindow() {
  if (!mainWindow) {
    createWindow();
    return;
  }
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

function runInMain(script) {
  if (!mainWindow) createWindow();
  if (!mainWindow) return;
  if (mainWindow.webContents.isLoading()) {
    mainWindow.webContents.once('did-finish-load', () => {
      mainWindow.webContents.executeJavaScript(script).catch(() => {});
    });
  } else {
    mainWindow.webContents.executeJavaScript(script).catch(() => {});
  }
}

function createWindow() {
  const window = new BrowserWindow({
    width: 1200,
    height: 820,
    minWidth: 980,
    minHeight: 680,
    show: false,
    title: '班表',
    backgroundColor: backgroundColor(),
    titleBarStyle: 'hiddenInset',
    trafficLightPosition: { x: 18, y: 19 },
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
      devTools: !app.isPackaged,
    },
  });

  window.once('ready-to-show', () => window.show());
  window.loadURL(`${APP_ORIGIN}/index.html`);

  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith(`${APP_ORIGIN}/`)) event.preventDefault();
  });

  window.on('closed', () => {
    mainWindow = null;
  });

  mainWindow = window;
  return window;
}

// A hidden renderer keeps the countdown and reminder alive when the main
// window is closed. It never creates a visible floating window.
function createCountdownEngine() {
  if (countdownEngine) return countdownEngine;

  const window = new BrowserWindow({
    width: 360,
    height: 100,
    show: false,
    skipTaskbar: true,
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
      backgroundThrottling: false,
      devTools: !app.isPackaged,
    },
  });

  window.loadURL(`${APP_ORIGIN}/widget.html`);
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith(`${APP_ORIGIN}/`)) event.preventDefault();
  });
  window.on('closed', () => {
    countdownEngine = null;
  });

  countdownEngine = window;
  return window;
}

function widgetDataPath() {
  return path.join(app.getPath('userData'), 'desktop-widget-data.json');
}

function writeWidgetData(input) {
  const payload = {
    label: String((input && input.label) || '未排班').slice(0, 32),
    time: String((input && input.time) || '—').slice(0, 32),
    meta: String((input && input.meta) || '').slice(0, 80),
    progress: Math.min(1, Math.max(0, Number(input && input.progress) || 0)),
    muted: Boolean(input && input.muted),
  };
  const target = widgetDataPath();
  const temp = `${target}.tmp`;
  widgetWriteChain = widgetWriteChain
    .then(async () => {
      await fs.mkdir(path.dirname(target), { recursive: true });
      await fs.writeFile(temp, JSON.stringify(payload), 'utf8');
      await fs.rename(temp, target);
    })
    .catch(() => {});
}

function desktopWidgetExecutable() {
  if (app.isPackaged) {
    return path.join(
      process.resourcesPath,
      'BanbiaoDesktopWidget.app',
      'Contents',
      'MacOS',
      'BanbiaoDesktopWidget',
    );
  }
  return path.join(
    __dirname,
    'desktop-widget',
    'BanbiaoDesktopWidget.app',
    'Contents',
    'MacOS',
    'BanbiaoDesktopWidget',
  );
}

function startDesktopWidget(show = true) {
  if (desktopWidgetProcess) {
    if (show) desktopWidgetProcess.kill('SIGUSR1');
    return;
  }

  const executable = desktopWidgetExecutable();
  if (!fsSync.existsSync(executable)) return;

  const args = ['--data', widgetDataPath(), '--parent', String(process.pid)];
  if (show) args.push('--show');
  desktopWidgetProcess = spawn(executable, args, { stdio: 'ignore' });
  desktopWidgetProcess.on('exit', () => {
    desktopWidgetProcess = null;
  });
  desktopWidgetProcess.on('error', () => {
    desktopWidgetProcess = null;
  });
}

function showDesktopWidget() {
  if (!desktopWidgetProcess) {
    startDesktopWidget(false);
  } else {
    desktopWidgetProcess.kill('SIGUSR1');
  }
}

function hideDesktopWidget() {
  if (desktopWidgetProcess) desktopWidgetProcess.kill('SIGUSR2');
}

function stopDesktopWidget() {
  if (desktopWidgetProcess) {
    desktopWidgetProcess.kill('SIGTERM');
    desktopWidgetProcess = null;
  }
}

function showReminder(payload) {
  if (reminderWindow) {
    reminderWindow.show();
    reminderWindow.focus();
    return;
  }

  const display = screen.getDisplayNearestPoint(screen.getCursorScreenPoint());
  const area = display.workArea;
  const shift = String((payload && payload.shift) || '班次').slice(0, 40);
  const end = String((payload && payload.end) || '').slice(0, 40);
  const query = new URLSearchParams({ shift, end }).toString();

  reminderWindow = new BrowserWindow({
    x: area.x,
    y: area.y,
    width: area.width,
    height: area.height,
    show: false,
    frame: false,
    transparent: false,
    resizable: false,
    movable: false,
    minimizable: false,
    maximizable: false,
    fullscreen: true,
    skipTaskbar: true,
    alwaysOnTop: true,
    backgroundColor: '#07140d',
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
      devTools: !app.isPackaged,
    },
  });

  reminderWindow.setAlwaysOnTop(true, 'screen-saver');
  reminderWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  reminderWindow.loadURL(`${APP_ORIGIN}/reminder.html?${query}`);

  reminderWindow.once('ready-to-show', () => {
    if (!reminderWindow) return;
    reminderWindow.show();
    reminderWindow.focus();
    shell.beep();
    if (app.dock) reminderBounceId = app.dock.bounce('critical');
  });

  reminderWindow.on('closed', () => {
    if (app.dock && reminderBounceId) app.dock.cancelBounce(reminderBounceId);
    reminderBounceId = 0;
    reminderWindow = null;
  });
}

function buildMenu() {
  const template = [
    {
      label: '班表',
      submenu: [
        { label: '关于班表', role: 'about' },
        { type: 'separator' },
        { label: '隐藏班表', role: 'hide' },
        { label: '隐藏其他', role: 'hideOthers' },
        { label: '显示全部', role: 'unhide' },
        { type: 'separator' },
        { label: '退出班表', role: 'quit' },
      ],
    },
    {
      label: '编辑',
      submenu: [
        { label: '撤销', role: 'undo' },
        { label: '重做', role: 'redo' },
        { type: 'separator' },
        { label: '剪切', role: 'cut' },
        { label: '拷贝', role: 'copy' },
        { label: '粘贴', role: 'paste' },
        { label: '全选', role: 'selectAll' },
      ],
    },
    {
      label: '显示',
      submenu: [
        {
          label: '今天',
          accelerator: 'CmdOrCtrl+T',
          click: () => runInMain("document.getElementById('todayButton').click()"),
        },
        {
          label: '班次时间…',
          accelerator: 'CmdOrCtrl+Shift+T',
          click: () => runInMain("document.getElementById('desktopTimesButton').click()"),
        },
        { type: 'separator' },
        {
          label: '显示桌面小组件',
          accelerator: 'CmdOrCtrl+Shift+D',
          click: showDesktopWidget,
        },
        { label: '隐藏桌面小组件', click: hideDesktopWidget },
        { type: 'separator' },
        { label: '进入全屏幕', role: 'togglefullscreen' },
      ],
    },
    {
      label: '窗口',
      submenu: [
        { label: '显示班表', click: showMainWindow },
        { type: 'separator' },
        { label: '最小化', role: 'minimize' },
        { label: '缩放', role: 'zoom' },
        { type: 'separator' },
        { label: '前置全部窗口', role: 'front' },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function isCountdownSender(event) {
  return Boolean(countdownEngine && event.sender === countdownEngine.webContents);
}

function isReminderSender(event) {
  return Boolean(reminderWindow && event.sender === reminderWindow.webContents);
}

app.whenReady().then(async () => {
  protocol.handle('app', async (request) => {
    const url = new URL(request.url);
    if (url.protocol !== 'app:' || url.hostname !== 'banbiao') {
      return new Response('Not found', { status: 404 });
    }

    let pathname;
    try {
      pathname = decodeURIComponent(url.pathname);
    } catch {
      return new Response('Bad request', { status: 400 });
    }

    if (pathname === '/' || pathname === '') pathname = '/index.html';
    const target = path.normalize(path.join(WEB_ROOT, pathname));
    if (target !== WEB_ROOT && !target.startsWith(`${WEB_ROOT}${path.sep}`)) {
      return new Response('Forbidden', { status: 403 });
    }

    try {
      const body = await fs.readFile(target);
      return new Response(body, {
        status: 200,
        headers: {
          'Content-Type': contentType(target),
          'Cache-Control': 'no-store',
        },
      });
    } catch {
      return new Response('Not found', { status: 404 });
    }
  });

  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
    callback(false);
  });

  app.setAboutPanelOptions({
    applicationName: '班表',
    applicationVersion: app.getVersion(),
    version: `版本 ${app.getVersion()}`,
    copyright: '按日期排班，轻巧而清晰。',
  });

  ipcMain.on('widget:data', (event, payload) => {
    if (isCountdownSender(event)) writeWidgetData(payload);
  });
  ipcMain.on('widget:remind', (event, payload) => {
    if (isCountdownSender(event)) showReminder(payload);
  });
  ipcMain.on('reminder:dismiss', (event) => {
    if (isReminderSender(event) && reminderWindow) reminderWindow.close();
  });
  ipcMain.on('theme:set', (_event, theme) => {
    if (theme === 'light' || theme === 'dark') nativeTheme.themeSource = theme;
  });

  nativeTheme.on('updated', () => {
    if (mainWindow) mainWindow.setBackgroundColor(backgroundColor());
  });

  buildMenu();
  createWindow();
  createCountdownEngine();
  writeWidgetData({ label: '正在读取', time: '—', meta: '班表', progress: 0, muted: true });
  startDesktopWidget(false);

  app.on('activate', () => {
    if (!mainWindow) createWindow();
  });
});

app.on('before-quit', () => {
  stopDesktopWidget();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    showMainWindow();
  });
}
