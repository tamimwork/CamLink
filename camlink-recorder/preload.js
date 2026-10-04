const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('camlink', {
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

  // Desktop Sources
  getScreenSources: () => ipcRenderer.invoke('get-screen-sources'),

  // Recording Storage
  recStart: () => ipcRenderer.invoke('rec-start'),
  recChunk: (arrayBuffer) => ipcRenderer.invoke('rec-chunk', arrayBuffer),
  recFinish: (options) => ipcRenderer.invoke('rec-finish', options),

  // Hotkeys
  onHotkeyRecordToggle: (callback) => {
    ipcRenderer.on('hotkey-record-toggle', () => callback());
  },
  onHotkeyPauseToggle: (callback) => {
    ipcRenderer.on('hotkey-pause-toggle', () => callback());
  }
});
