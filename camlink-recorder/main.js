const { app, BrowserWindow, ipcMain, desktopCapturer, dialog, globalShortcut } = require('electron');
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
// (otherwise the canvas compositor freezes and the recording shows a frozen screen)
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.commandLine.appendSwitch('disable-background-timer-throttling');
app.commandLine.appendSwitch('disable-backgrounding-occluded-windows');

let mainWindow = null;
let signalingServer = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1360,
    height: 860,
    minWidth: 1024,
    minHeight: 700,
    backgroundColor: '#090D16',
    title: 'CamLink Recorder',
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
  });
}

app.whenReady().then(() => {
  createWindow();

  // Initialize HTTPS + WSS Signaling Server
  signalingServer = new SignalingServer(app.getPath('userData'));
  
  signalingServer.on('phone-connected', (info) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('phone-connected', info);
    }
  });

  signalingServer.on('phone-disconnected', (reason) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('phone-disconnected', reason);
    }
  });

  signalingServer.on('signal-message', (msg) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('signal-message', msg);
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

  // Register Global Hotkeys
  globalShortcut.register('CommandOrControl+Shift+R', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('hotkey-record-toggle');
    }
  });

  globalShortcut.register('CommandOrControl+Shift+P', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('hotkey-pause-toggle');
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

// IPC: Signaling send to phone
ipcMain.on('send-signal-to-phone', (event, msg) => {
  if (signalingServer) {
    signalingServer.sendToPhone(msg);
  }
});

// IPC: Screen capture sources
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

// IPC: Streaming recording to disk (chunks are written as they arrive,
// so long tutorials do not fill up RAM and a crash does not lose the file)
let recSession = null; // { tempPath, stream, error }

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
      // Keep the temp file so the recording is never lost
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
        // Remux (no re-encode) so the WebM has duration + seeking info
        await runFfmpeg(session.tempPath, destination, ['-c copy']);
      }
      await fs.promises.unlink(session.tempPath).catch(() => {});
      return { success: true, filePath: destination };
    } catch (err) {
      console.error('FFmpeg error:', err);
      // Fallback: save the raw WebM next to the chosen name
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
