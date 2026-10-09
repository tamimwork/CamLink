const { app, BrowserWindow, ipcMain, desktopCapturer, dialog, globalShortcut, Menu, screen, shell, systemPreferences } = require('electron');
const path = require('path');
const fs = require('fs');
const SignalingServer = require('./server');
const ffmpeg = require('fluent-ffmpeg');
const ffmpegStatic = require('ffmpeg-static');

// Set ffmpeg path for fluent-ffmpeg
if (ffmpegStatic) {
  ffmpeg.setFfmpegPath(ffmpegStatic.replace('app.asar', 'app.asar.unpacked'));
}

// Keep rendering/timers alive when the window is minimized or covered
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.commandLine.appendSwitch('disable-background-timer-throttling');
app.commandLine.appendSwitch('disable-backgrounding-occluded-windows');

let mainWindow = null;
let overlayWindow = null;
let facecamPopoutWindow = null;
let signalingServer = null;

// Persistent overlay position & settings memory
const configFilePath = path.join(app.getPath('userData'), 'camlink-settings.json');

function loadSavedSettings() {
  try {
    if (fs.existsSync(configFilePath)) {
      const data = fs.readFileSync(configFilePath, 'utf8');
      return JSON.parse(data);
    }
  } catch (err) {
    console.warn('Could not read saved settings:', err);
  }
  return {
    overlayPosition: null,
    autoShowOverlayOnRecord: true,
    minimizeOnRecord: false
  };
}

function saveSetting(key, value) {
  try {
    const settings = loadSavedSettings();
    settings[key] = value;
    fs.writeFileSync(configFilePath, JSON.stringify(settings, null, 2), 'utf8');
  } catch (err) {
    console.warn('Could not save setting:', err);
  }
}

function setupAppMenu() {
  if (process.platform === 'darwin') {
    const template = [
      {
        label: app.name,
        submenu: [
          { role: 'about' },
          { type: 'separator' },
          { role: 'services' },
          { type: 'separator' },
          { role: 'hide' },
          { role: 'hideOthers' },
          { role: 'unhide' },
          { type: 'separator' },
          { role: 'quit' }
        ]
      },
      {
        label: 'Edit',
        submenu: [
          { role: 'undo' },
          { role: 'redo' },
          { type: 'separator' },
          { role: 'cut' },
          { role: 'copy' },
          { role: 'paste' },
          { role: 'selectAll' }
        ]
      },
      {
        label: 'View',
        submenu: [
          { role: 'reload' },
          { role: 'forceReload' },
          { role: 'toggleDevTools' },
          { type: 'separator' },
          { role: 'resetZoom' },
          { role: 'zoomIn' },
          { role: 'zoomOut' },
          { type: 'separator' },
          { role: 'togglefullscreen' }
        ]
      },
      {
        label: 'Window',
        submenu: [
          { role: 'minimize' },
          { role: 'zoom' },
          { type: 'separator' },
          { role: 'front' },
          { type: 'separator' },
          { role: 'close' }
        ]
      }
    ];
    const menu = Menu.buildFromTemplate(template);
    Menu.setApplicationMenu(menu);
  } else {
    Menu.setApplicationMenu(null);
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1360,
    height: 860,
    minWidth: 1024,
    minHeight: 700,
    backgroundColor: '#090D16',
    title: 'CamLink Recorder',
    icon: path.join(__dirname, 'assets', process.platform === 'win32' ? 'icon.ico' : 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      backgroundThrottling: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  mainWindow.on('closed', () => {
    mainWindow = null;
    if (overlayWindow && !overlayWindow.isDestroyed()) {
      overlayWindow.close();
      overlayWindow = null;
    }
    if (facecamPopoutWindow && !facecamPopoutWindow.isDestroyed()) {
      facecamPopoutWindow.close();
      facecamPopoutWindow = null;
    }
  });
}

/**
 * Validate that a given {x, y, width, height} fits on one of the connected displays.
 * If not, return sensible coordinates on the primary display.
 */
function validateOrClampPosition(targetX, targetY, winWidth, winHeight) {
  const displays = screen.getAllDisplays();
  const matched = displays.find(d => {
    const b = d.bounds;
    return targetX >= b.x - 20 &&
           targetX + winWidth <= b.x + b.width + 20 &&
           targetY >= b.y - 20 &&
           targetY + winHeight <= b.y + b.height + 20;
  });

  if (matched) {
    const work = matched.workArea;
    const clampedX = Math.max(work.x, Math.min(targetX, work.x + work.width - winWidth));
    const clampedY = Math.max(work.y, Math.min(targetY, work.y + work.height - winHeight));
    return { x: Math.round(clampedX), y: Math.round(clampedY) };
  }

  const primary = screen.getPrimaryDisplay().workArea;
  const defX = primary.x + primary.width - winWidth - 32;
  const defY = primary.y + 60;
  return { x: Math.round(defX), y: Math.round(defY) };
}

/**
 * Snap window to nearest screen edge (left, right, top, or bottom margin)
 */
function snapOverlayToNearestEdge(win) {
  if (!win || win.isDestroyed()) return;
  const [currentX, currentY] = win.getPosition();
  const [winWidth, winHeight] = win.getSize();

  const currentDisplay = screen.getDisplayNearestPoint({ x: currentX, y: currentY });
  const work = currentDisplay.workArea;
  const padding = 16;

  // Calculate distances to edges of current work area
  const distLeft = Math.abs(currentX - (work.x + padding));
  const distRight = Math.abs((work.x + work.width - padding - winWidth) - currentX);
  const distTop = Math.abs(currentY - (work.y + padding));
  const distBottom = Math.abs((work.y + work.height - padding - winHeight) - currentY);

  let newX = currentX;
  let newY = currentY;

  // Snap horizontally to closer side
  if (distLeft < distRight) {
    if (distLeft < 180) newX = work.x + padding;
  } else {
    if (distRight < 180) newX = work.x + work.width - padding - winWidth;
  }

  // Ensure within work area vertically
  newY = Math.max(work.y + padding, Math.min(newY, work.y + work.height - padding - winHeight));
  newX = Math.max(work.x + padding, Math.min(newX, work.x + work.width - padding - winWidth));

  win.setPosition(Math.round(newX), Math.round(newY));
  saveSetting('overlayPosition', { x: Math.round(newX), y: Math.round(newY) });
}

function createOverlayWindow() {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.showInactive();
    return overlayWindow;
  }

  const settings = loadSavedSettings();
  const initialWidth = 56;
  const initialHeight = 56;

  let pos = { x: 100, y: 100 };
  if (settings.overlayPosition && typeof settings.overlayPosition.x === 'number') {
    pos = validateOrClampPosition(settings.overlayPosition.x, settings.overlayPosition.y, initialWidth, initialHeight);
  } else {
    const primary = screen.getPrimaryDisplay().workArea;
    pos = {
      x: primary.x + primary.width - initialWidth - 28,
      y: primary.y + 60
    };
  }

  overlayWindow = new BrowserWindow({
    x: pos.x,
    y: pos.y,
    width: initialWidth,
    height: initialHeight,
    minWidth: 48,
    minHeight: 48,
    transparent: true,
    frame: false,
    alwaysOnTop: true,
    hasShadow: false,
    resizable: false,
    skipTaskbar: true,
    backgroundColor: '#00000000',
    title: 'CamLink Controls Overlay',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      backgroundThrottling: false
    }
  });

  // Section 3: setContentProtection(true) so it is NOT captured in recorded video!
  overlayWindow.setContentProtection(true);

  // macOS fullscreen & multi-workspace visibility
  if (typeof overlayWindow.setVisibleOnAllWorkspaces === 'function') {
    overlayWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  }

  // Float above full-screen windows and presentations
  overlayWindow.setAlwaysOnTop(true, 'screen-saver');

  overlayWindow.loadFile(path.join(__dirname, 'renderer', 'overlay', 'overlay.html'));

  // Section 3: showInactive() so it does not steal focus
  overlayWindow.showInactive();

  overlayWindow.on('closed', () => {
    overlayWindow = null;
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('overlay-closed');
    }
  });

  return overlayWindow;
}

// Re-validate overlay window position when displays change (monitors plugged / unplugged / resized)
// NOTE: the 'screen' module may only be used after the app 'ready' event, so this is registered from whenReady()
function registerDisplayWatcher() {
  screen.on('display-metrics-changed', () => {
    if (overlayWindow && !overlayWindow.isDestroyed()) {
      const [x, y] = overlayWindow.getPosition();
      const [w, h] = overlayWindow.getSize();
      const validated = validateOrClampPosition(x, y, w, h);
      overlayWindow.setPosition(validated.x, validated.y);
    }
  });
}

// Optional separate face cam popout window (also with setContentProtection)
function createFaceCamPopoutWindow() {
  if (facecamPopoutWindow && !facecamPopoutWindow.isDestroyed()) {
    facecamPopoutWindow.show();
    facecamPopoutWindow.focus();
    return facecamPopoutWindow;
  }

  facecamPopoutWindow = new BrowserWindow({
    width: 280,
    height: 280,
    minWidth: 160,
    minHeight: 160,
    maxWidth: 600,
    maxHeight: 600,
    transparent: true,
    frame: false,
    alwaysOnTop: true,
    hasShadow: false,
    resizable: true,
    skipTaskbar: false,
    backgroundColor: '#00000000',
    title: 'CamLink Face Cam Bubble',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      backgroundThrottling: false
    }
  });

  // Face cam popout must also use setContentProtection(true) so face is not captured twice
  facecamPopoutWindow.setContentProtection(true);
  if (typeof facecamPopoutWindow.setVisibleOnAllWorkspaces === 'function') {
    facecamPopoutWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  }
  facecamPopoutWindow.setAlwaysOnTop(true, 'screen-saver');

  facecamPopoutWindow.loadFile(path.join(__dirname, 'renderer', 'overlay', 'popout.html'));

  facecamPopoutWindow.on('closed', () => {
    facecamPopoutWindow = null;
  });

  return facecamPopoutWindow;
}

// Allowed remote control actions strictly matching the Shared Protocol allowlist
const ALLOWED_CONTROL_ACTIONS = new Set([
  'switchCamera',
  'toggleMic',
  'toggleVideo',
  'toggleMirror',
  'toggleTorch',
  'toggleAf',
  'toggleAe',
  'setZoom',
  'setQuality',
  'endSession'
]);

/**
 * Validates a PC->phone control message and returns a NEW sanitized object (unknown fields are dropped),
 * or null if the message must be rejected.
 */
function sanitizeControlMessage(msg) {
  if (!msg || typeof msg !== 'object') return null;
  if (typeof msg.action !== 'string' || !ALLOWED_CONTROL_ACTIONS.has(msg.action)) {
    console.warn('[CamLinkMain] Rejected control message not in allowlist:', msg.action);
    return null;
  }
  const out = { type: 'control', action: msg.action };
  if (msg.action === 'setZoom') {
    if (typeof msg.value !== 'number' || !Number.isFinite(msg.value)) {
      console.warn('[CamLinkMain] Rejected invalid zoom value:', msg.value);
      return null;
    }
    out.value = Math.max(1.0, Math.min(10.0, msg.value));
  }
  if (msg.action === 'setQuality') {
    if ((msg.resolution !== '720p' && msg.resolution !== '1080p') || (msg.fps !== 30 && msg.fps !== 60)) {
      console.warn('[CamLinkMain] Rejected invalid quality payload:', msg.resolution, msg.fps);
      return null;
    }
    out.resolution = msg.resolution;
    out.fps = msg.fps;
  }
  return out;
}

/**
 * Only these message types may leave the PC toward the phone. Each one is rebuilt field by field.
 */
function sanitizeOutgoingMessage(msg) {
  if (!msg || typeof msg !== 'object' || typeof msg.type !== 'string') return null;
  switch (msg.type) {
    case 'control':
      return sanitizeControlMessage(msg);
    case 'answer':
      return typeof msg.sdp === 'string' && msg.sdp.length > 0 ? { type: 'answer', sdp: msg.sdp } : null;
    case 'ice':
      if (typeof msg.candidate !== 'string' || msg.candidate.length === 0) return null;
      return {
        type: 'ice',
        candidate: msg.candidate,
        sdpMid: typeof msg.sdpMid === 'string' ? msg.sdpMid : '0',
        sdpMLineIndex: Number.isInteger(msg.sdpMLineIndex) ? msg.sdpMLineIndex : 0
      };
    case 'bye':
      return { type: 'bye' };
    default:
      console.warn('[CamLinkMain] Rejected outgoing message type:', msg.type);
      return null;
  }
}

app.whenReady().then(() => {
  setupAppMenu();
  registerDisplayWatcher();
  createWindow();

  // Initialize HTTPS + WSS Signaling Server
  signalingServer = new SignalingServer(app.getPath('userData'));

  signalingServer.on('phone-connected', (info) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('phone-connected', info);
    }
    if (overlayWindow && !overlayWindow.isDestroyed()) {
      overlayWindow.webContents.send('phone-connected', info);
    }
  });

  signalingServer.on('phone-disconnected', (reason) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('phone-disconnected', reason);
    }
    if (overlayWindow && !overlayWindow.isDestroyed()) {
      overlayWindow.webContents.send('phone-disconnected', reason);
    }
  });

  signalingServer.on('signal-message', (msg) => {
    // Intercept phone's state / device-state telemetry or forward to renderer
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('signal-message', msg);
    }
    if (overlayWindow && !overlayWindow.isDestroyed()) {
      overlayWindow.webContents.send('signal-message', msg);
    }
  });

  signalingServer.on('latency-update', (latencyMs) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('latency-update', latencyMs);
    }
  });

  signalingServer.on('server-error', (err) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('server-error', err);
    }
  });

  signalingServer.start();

  // Register Global Hotkeys as per Prompt 1 specification
  // 1. Record toggle
  globalShortcut.register('CommandOrControl+Shift+R', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('hotkey-record-toggle');
    }
  });

  // 2. Pause toggle
  globalShortcut.register('CommandOrControl+Shift+P', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('hotkey-pause-toggle');
    }
  });

  // 3. Overlay toggle (Ctrl/Cmd+Shift+O)
  globalShortcut.register('CommandOrControl+Shift+O', () => {
    if (overlayWindow && !overlayWindow.isDestroyed()) {
      if (overlayWindow.isVisible()) {
        overlayWindow.hide();
      } else {
        overlayWindow.showInactive();
      }
    } else {
      createOverlayWindow();
    }
  });

  // 4. Flip camera (Ctrl/Cmd+Shift+F)
  globalShortcut.register('CommandOrControl+Shift+F', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('hotkey-flip-camera');
    }
  });

  // 5. Toggle phone mic (Ctrl/Cmd+Shift+M)
  globalShortcut.register('CommandOrControl+Shift+M', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('hotkey-toggle-mic');
    }
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
  if (signalingServer) {
    signalingServer.stop();
  }
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

// IPC: Server control & network info
ipcMain.handle('get-server-info', async () => {
  return signalingServer ? signalingServer.getInfo() : null;
});

ipcMain.handle('generate-new-code', async () => {
  return signalingServer ? signalingServer.generateNewCode() : null;
});

ipcMain.handle('select-network-ip', async (event, ip) => {
  if (signalingServer) {
    return await signalingServer.setActiveIp(ip);
  }
  return null;
});

// IPC: Signaling send to phone with strict allowlist and sanitizing
ipcMain.on('send-signal-to-phone', (event, msg) => {
  if (!signalingServer) return;
  const clean = sanitizeOutgoingMessage(msg);
  if (!clean) return;
  signalingServer.sendToPhone(clean);
});

// IPC: Screen capture sources & macOS Screen Recording permission check
ipcMain.handle('check-screen-recording-permission', async () => {
  if (process.platform === 'darwin' && systemPreferences && systemPreferences.getMediaAccessStatus) {
    const status = systemPreferences.getMediaAccessStatus('screen');
    return { hasPermission: status === 'granted', status };
  }
  return { hasPermission: true, status: 'granted' };
});

ipcMain.handle('open-mac-screen-recording-preferences', async () => {
  if (process.platform === 'darwin') {
    try {
      await shell.openExternal('x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture');
      return true;
    } catch (err) {
      console.warn('Could not open macOS preferences directly:', err);
    }
  }
  return false;
});

ipcMain.handle('get-screen-sources', async () => {
  try {
    const sources = await desktopCapturer.getSources({
      types: ['screen', 'window'],
      thumbnailSize: { width: 360, height: 200 },
      fetchWindowIcons: true
    });
    return sources.map(s => ({
      id: s.id,
      name: s.name,
      thumbnail: s.thumbnail.toDataURL()
    }));
  } catch (err) {
    console.error('Failed to get desktop sources:', err);
    return [];
  }
});

// IPC: Floating Overlay Window (48px / expandable)
ipcMain.handle('open-overlay', async () => {
  createOverlayWindow();
  return true;
});

ipcMain.handle('close-overlay', async () => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.close();
    overlayWindow = null;
  }
  return true;
});

ipcMain.handle('toggle-overlay', async () => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    if (overlayWindow.isVisible()) {
      overlayWindow.hide();
      return false;
    } else {
      overlayWindow.showInactive();
      return true;
    }
  } else {
    createOverlayWindow();
    return true;
  }
});

ipcMain.handle('is-overlay-open', async () => {
  return Boolean(overlayWindow && !overlayWindow.isDestroyed() && overlayWindow.isVisible());
});

ipcMain.handle('move-overlay', async (event, { dx, dy }) => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    const [x, y] = overlayWindow.getPosition();
    overlayWindow.setPosition(Math.round(x + dx), Math.round(y + dy));
  }
  return true;
});

ipcMain.handle('snap-overlay', async () => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    snapOverlayToNearestEdge(overlayWindow);
  }
  return true;
});

ipcMain.handle('set-overlay-position', async (event, { x, y }) => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.setPosition(Math.round(x), Math.round(y));
    saveSetting('overlayPosition', { x: Math.round(x), y: Math.round(y) });
  }
  return true;
});

ipcMain.handle('set-overlay-size', async (event, { width, height }) => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.setSize(Math.round(width), Math.round(height));
  }
  return true;
});

ipcMain.handle('focus-main-window', async () => {
  if (mainWindow && !mainWindow.isDestroyed()) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
  }
  return true;
});

// Separate Face Cam Popout Window
ipcMain.handle('open-facecam-popout', async () => {
  createFaceCamPopoutWindow();
  return true;
});

ipcMain.handle('close-facecam-popout', async () => {
  if (facecamPopoutWindow && !facecamPopoutWindow.isDestroyed()) {
    facecamPopoutWindow.close();
    facecamPopoutWindow = null;
  }
  return true;
});

ipcMain.handle('is-facecam-popout-open', async () => {
  return Boolean(facecamPopoutWindow && !facecamPopoutWindow.isDestroyed());
});

// IPC: Relaying between Main Window and Overlay Window
ipcMain.on('overlay-state-update', (event, state) => {
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.webContents.send('overlay-state-update', state);
  }
  if (facecamPopoutWindow && !facecamPopoutWindow.isDestroyed()) {
    facecamPopoutWindow.webContents.send('overlay-state-update', state);
  }
});

ipcMain.on('overlay-action', (event, data) => {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('overlay-action', data);
  }
});

ipcMain.on('overlay-frame', (event, frameData) => {
  if (facecamPopoutWindow && !facecamPopoutWindow.isDestroyed()) {
    facecamPopoutWindow.webContents.send('overlay-frame', frameData);
  }
});

// IPC: Streaming recording to disk
let recSession = null;

function runFfmpeg(input, output, options) {
  return new Promise((resolve, reject) => {
    ffmpeg(input)
      .outputOptions(options)
      .save(output)
      .on('end', resolve)
      .on('error', reject);
  });
}

ipcMain.handle('rec-start', async () => {
  if (recSession) {
    try { recSession.stream.destroy(); } catch (_) {}
  }
  const tempPath = path.join(app.getPath('temp'), `camlink_rec_${Date.now()}.webm`);
  const stream = fs.createWriteStream(tempPath);
  recSession = { tempPath, stream, error: null };
  stream.on('error', (e) => { if (recSession) recSession.error = e.message; });
  return { success: true };
});

ipcMain.handle('rec-chunk', async (event, chunk) => {
  if (!recSession) return { success: false, error: 'No active recording' };
  if (recSession.error) return { success: false, error: recSession.error };
  const buf = Buffer.from(chunk);
  await new Promise((resolve) => recSession.stream.write(buf, () => resolve()));
  return recSession.error ? { success: false, error: recSession.error } : { success: true };
});

ipcMain.handle('rec-finish', async (event, { format }) => {
  const session = recSession;
  if (!session) return { success: false, error: 'No active recording' };
  recSession = null;

  try {
    await new Promise((resolve) => session.stream.end(resolve));
    if (session.error) throw new Error(session.error);

    const stat = await fs.promises.stat(session.tempPath).catch(() => null);
    if (!stat || stat.size === 0) {
      return { success: false, error: 'Recording is empty' };
    }

    const wantMp4 = format === 'mp4';
    const defaultName = `Recording_${new Date().toISOString().replace(/[:.]/g, '-')}.${wantMp4 ? 'mp4' : 'webm'}`;
    const result = await dialog.showSaveDialog(mainWindow, {
      title: 'Save Recording',
      defaultPath: path.join(app.getPath('videos') || app.getPath('documents'), defaultName),
      filters: [wantMp4 ? { name: 'MP4 Video', extensions: ['mp4'] } : { name: 'WebM Video', extensions: ['webm'] }]
    });

    if (result.canceled || !result.filePath) {
      return { success: false, canceled: true, tempPath: session.tempPath };
    }

    const destination = result.filePath;
    try {
      if (wantMp4) {
        await runFfmpeg(session.tempPath, destination, [
          '-c:v libx264', '-preset veryfast', '-crf 22', '-pix_fmt yuv420p',
          '-c:a aac', '-b:a 192k', '-movflags +faststart'
        ]);
      } else {
        await runFfmpeg(session.tempPath, destination, ['-c copy']);
      }
      await fs.promises.unlink(session.tempPath).catch(() => {});
      return { success: true, filePath: destination };
    } catch (err) {
      console.error('FFmpeg error:', err);
      const fallback = destination.replace(/\.(mp4|webm)$/i, '') + '.webm';
      await fs.promises.copyFile(session.tempPath, fallback);
      await fs.promises.unlink(session.tempPath).catch(() => {});
      return { success: true, filePath: fallback, warning: 'Saved as WebM because the converter failed' };
    }
  } catch (err) {
    console.error('Error saving recording:', err);
    return { success: false, error: err.message, tempPath: session.tempPath };
  }
});