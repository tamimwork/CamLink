const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('camlink', {
  // Platform Detection
  isMac: process.platform === 'darwin',
  isWindows: process.platform === 'win32',

  // Server and Network
  getServerInfo: () => ipcRenderer.invoke('get-server-info'),
  generateNewCode: () => ipcRenderer.invoke('generate-new-code'),
  selectNetworkIp: (ip) => ipcRenderer.invoke('select-network-ip', ip),

  // Signaling
  sendSignalToPhone: (msg) => ipcRenderer.send('send-signal-to-phone', msg),
  onSignalMessage: (callback) => {
    ipcRenderer.on('signal-message', (event, msg) => callback(msg));
  },
  onPhoneConnected: (callback) => {
    ipcRenderer.on('phone-connected', (event, info) => callback(info));
  },
  onPhoneDisconnected: (callback) => {
    ipcRenderer.on('phone-disconnected', (event, reason) => callback(reason));
  },
  onLatencyUpdate: (callback) => {
    ipcRenderer.on('latency-update', (event, latencyMs) => callback(latencyMs));
  },
  onServerError: (callback) => {
    ipcRenderer.on('server-error', (event, err) => callback(err));
  },

  // Desktop Sources & Permissions
  getScreenSources: () => ipcRenderer.invoke('get-screen-sources'),
  checkScreenRecordingPermission: () => ipcRenderer.invoke('check-screen-recording-permission'),
  openMacScreenRecordingPreferences: () => ipcRenderer.invoke('open-mac-screen-recording-preferences'),

  // Recording Storage
  recStart: () => ipcRenderer.invoke('rec-start'),
  recChunk: (arrayBuffer) => ipcRenderer.invoke('rec-chunk', arrayBuffer),
  recFinish: (options) => ipcRenderer.invoke('rec-finish', options),

  // Hotkeys & Actions triggered from main
  onHotkeyRecordToggle: (callback) => {
    ipcRenderer.on('hotkey-record-toggle', () => callback());
  },
  onHotkeyPauseToggle: (callback) => {
    ipcRenderer.on('hotkey-pause-toggle', () => callback());
  },
  onHotkeyFlipCamera: (callback) => {
    ipcRenderer.on('hotkey-flip-camera', () => callback());
  },
  onHotkeyToggleMic: (callback) => {
    ipcRenderer.on('hotkey-toggle-mic', () => callback());
  },
  onHotkeyToggleOverlay: (callback) => {
    ipcRenderer.on('hotkey-toggle-overlay', () => callback());
  },

  // Floating Desktop Remote Control Overlay Window Management
  openOverlay: () => ipcRenderer.invoke('open-overlay'),
  closeOverlay: () => ipcRenderer.invoke('close-overlay'),
  toggleOverlay: () => ipcRenderer.invoke('toggle-overlay'),
  isOverlayOpen: () => ipcRenderer.invoke('is-overlay-open'),
  moveOverlay: (dx, dy) => ipcRenderer.invoke('move-overlay', { dx, dy }),
  snapOverlay: () => ipcRenderer.invoke('snap-overlay'),
  setOverlayPosition: (x, y) => ipcRenderer.invoke('set-overlay-position', { x, y }),
  setOverlaySize: (width, height) => ipcRenderer.invoke('set-overlay-size', { width, height }),
  focusMainWindow: () => ipcRenderer.invoke('focus-main-window'),

  // Inter-Window Communication (Main <-> Overlay)
  sendOverlayStateUpdate: (state) => ipcRenderer.send('overlay-state-update', state),
  onOverlayStateUpdate: (callback) => {
    ipcRenderer.on('overlay-state-update', (event, state) => callback(state));
  },
  sendOverlayAction: (action, payload) => ipcRenderer.send('overlay-action', { action, payload }),
  onOverlayAction: (callback) => {
    ipcRenderer.on('overlay-action', (event, data) => callback(data));
  },
  onOverlayClosed: (callback) => {
    ipcRenderer.on('overlay-closed', () => callback());
  },

  // Face Cam Pop-out window management
  openFaceCamPopout: () => ipcRenderer.invoke('open-facecam-popout'),
  closeFaceCamPopout: () => ipcRenderer.invoke('close-facecam-popout'),
  isFaceCamPopoutOpen: () => ipcRenderer.invoke('is-facecam-popout-open'),
  sendOverlayFrame: (frameData) => ipcRenderer.send('overlay-frame', frameData),
  onOverlayFrame: (callback) => {
    ipcRenderer.on('overlay-frame', (event, frameData) => callback(frameData));
  }
});
