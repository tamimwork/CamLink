/**
 * CamLink Remote Controls Module
 * Handles remote camera/audio controls to the connected phone,
 * telemetry reception, and desktop floating overlay synchronization.
 * 
 * Protocol conforms strictly to "Shared Protocol":
 * Actions: switchCamera, toggleMic, toggleVideo, toggleMirror, toggleTorch, toggleAf, toggleAe, setZoom {value}, setQuality {resolution, fps}, endSession
 * Phone sends 'state': facing, micMuted, videoPaused, mirrored, torch, afLocked, aeLocked, zoom, maxZoom,
 * resolution, fps, battery, thermal (nominal|fair|serious|critical), tempC, hasTorch, canFlip
 */
class RemoteControlsManager {
  constructor(options = {}) {
    this.compositor = options.compositor;
    this.recorder = options.recorder;
    this.audioMixer = options.audioMixer;
    this.phoneVideo = options.phoneVideo;

    // Remote Phone State (mirroring exactly the phone's echoed state)
    this.state = {
      connected: false,
      deviceName: 'No device connected',
      camera: 'back', // 'front' | 'back'
      torch: false,
      mic: true,       // true = active/unmuted, false = muted
      video: true,     // true = streaming, false = paused
      mirror: false,
      af: true,        // autofocus active
      ae: true,        // auto-exposure active
      zoom: 1.0,
      resolution: '1080p',
      fps: 30,
      battery: null,
      thermal: 'nominal',
      tempC: null,
      maxZoom: 5.0,
      hasTorch: true,
      canFlip: true,
      latency: null
    };

    // Zoom throttling / echo protection
    this.zoomInteractAt = 0;
    this._lastZoomSend = 0;
    this._pendingZoom = null;
    this._zoomTimer = null;
    this._byeTimer = null;

    // Overlay State
    this.overlayOpen = false;

    this.initDOMElements();
    this.attachEventListeners();
    this.initOverlayListeners();
  }

  initDOMElements() {
    // Remote Control Tab Elements
    this.elDeviceTitle = document.getElementById('rc-device-title');
    this.elBatteryBadge = document.getElementById('rc-battery-badge');
    this.elBatteryText = document.getElementById('rc-battery-text');
    this.elThermalBadge = document.getElementById('rc-thermal-badge');
    this.elLatencyBadge = document.getElementById('rc-latency-badge');

    this.btnSwitchCamera = document.getElementById('rc-btn-camera');
    this.cameraLabel = document.getElementById('rc-camera-label');
    this.btnTorch = document.getElementById('rc-btn-torch');
    this.torchText = this.btnTorch ? this.btnTorch.querySelector('.rc-btn-text') : null;

    this.btnRemoteMic = document.getElementById('rc-btn-mute');
    this.remoteMicText = this.btnRemoteMic ? this.btnRemoteMic.querySelector('.rc-btn-text') : null;

    this.btnRemoteVideo = document.getElementById('rc-btn-pause-video');
    this.remoteVideoText = this.btnRemoteVideo ? this.btnRemoteVideo.querySelector('.rc-btn-text') : null;

    this.btnRemoteMirror = document.getElementById('rc-btn-mirror');

    this.zoomSlider = document.getElementById('rc-zoom-slider');
    this.zoomVal = document.getElementById('rc-zoom-val');
    this.zoomPresetButtons = document.querySelectorAll('[data-rc-zoom]');

    this.btnAf = document.getElementById('rc-btn-af-lock');
    this.afText = this.btnAf ? this.btnAf.querySelector('.rc-btn-text') : null;

    this.btnAe = document.getElementById('rc-btn-ae-lock');
    this.aeText = this.btnAe ? this.btnAe.querySelector('.rc-btn-text') : null;

    this.btnLaunchOverlay = document.getElementById('btn-launch-overlay');
    this.btnQuickOverlay = document.getElementById('quick-btn-overlay');
    this.btnQuickCamera = document.getElementById('quick-btn-camera');
    this.btnQuickTorch = document.getElementById('quick-btn-torch');
    this.btnDisconnectPhone = document.getElementById('rc-btn-disconnect');
  }

  attachEventListeners() {
    // 1. switchCamera
    if (this.btnSwitchCamera) {
      this.btnSwitchCamera.addEventListener('click', () => this.switchCamera());
    }
    if (this.btnQuickCamera) {
      this.btnQuickCamera.addEventListener('click', () => this.switchCamera());
    }

    // 2. toggleTorch
    if (this.btnTorch) {
      this.btnTorch.addEventListener('click', () => this.toggleTorch());
    }
    if (this.btnQuickTorch) {
      this.btnQuickTorch.addEventListener('click', () => this.toggleTorch());
    }

    // 3. toggleMic
    if (this.btnRemoteMic) {
      this.btnRemoteMic.addEventListener('click', () => this.toggleMic());
    }

    // 4. toggleVideo
    if (this.btnRemoteVideo) {
      this.btnRemoteVideo.addEventListener('click', () => this.toggleVideo());
    }

    // 5. toggleMirror
    if (this.btnRemoteMirror) {
      this.btnRemoteMirror.addEventListener('click', () => this.toggleMirror());
    }

    // 6. Zoom Slider & Presets (setZoom)
    if (this.zoomSlider) {
      this.zoomSlider.addEventListener('input', (e) => {
        const val = parseFloat(e.target.value);
        this.setZoom(val);
      });
    }

    if (this.zoomPresetButtons) {
      this.zoomPresetButtons.forEach(btn => {
        btn.addEventListener('click', () => {
          const val = parseFloat(btn.dataset.rcZoom);
          this.setZoom(val);
        });
      });
    }

    // 7. toggleAf
    if (this.btnAf) {
      this.btnAf.addEventListener('click', () => this.toggleAf());
    }

    // 8. toggleAe
    if (this.btnAe) {
      this.btnAe.addEventListener('click', () => this.toggleAe());
    }

    // 9. setQuality Presets
    document.querySelectorAll('[data-rc-quality]').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('[data-rc-quality]').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const [res, fps] = btn.dataset.rcQuality.split('@');
        this.setQuality(res, parseInt(fps, 10));
      });
    });

    // 10. Floating Desktop Remote Controls Overlay Toggle
    if (this.btnLaunchOverlay) {
      this.btnLaunchOverlay.addEventListener('click', () => this.toggleDesktopOverlay());
    }
    if (this.btnQuickOverlay) {
      this.btnQuickOverlay.addEventListener('click', () => this.toggleDesktopOverlay());
    }

    // 11. End Session
    if (this.btnDisconnectPhone) {
      this.btnDisconnectPhone.addEventListener('click', () => this.endSession());
    }
  }

  initOverlayListeners() {
    if (!window.camlink) return;

    // Listen for actions sent from overlay window
    window.camlink.onOverlayAction(({ action, payload }) => {
      console.log('[RemoteControls] Action received from overlay:', action, payload);
      switch (action) {
        case 'record-toggle':
          if (this.recorder) {
            if (this.recorder.state === 'inactive') {
              this.recorder.start();
            } else {
              this.recorder.stop();
            }
          }
          break;
        case 'pause-toggle':
          if (this.recorder) {
            if (this.recorder.state === 'recording') {
              this.recorder.pause();
            } else if (this.recorder.state === 'paused') {
              this.recorder.resume();
            }
          }
          break;
        case 'switchCamera':
          this.switchCamera();
          break;
        case 'toggleMic':
          this.toggleMic();
          break;
        case 'toggleVideo':
          this.toggleVideo();
          break;
        case 'toggleMirror':
          this.toggleMirror();
          break;
        case 'toggleTorch':
          this.toggleTorch();
          break;
        case 'toggleAf':
          this.toggleAf();
          break;
        case 'toggleAe':
          this.toggleAe();
          break;
        case 'setZoom':
          if (payload && typeof payload.value === 'number') {
            this.setZoom(payload.value);
          }
          break;
        case 'setQuality':
          if (payload && payload.resolution && payload.fps) {
            this.setQuality(payload.resolution, payload.fps);
          }
          break;
        case 'endSession':
          this.endSession();
          break;
        case 'shape-change':
          if (this.compositor && payload) {
            this.compositor.setBubbleShape(payload);
          }
          break;
        case 'overlay-ready':
          this.overlayOpen = true;
          this.syncOverlayState();
          break;
      }
    });

    window.camlink.onOverlayClosed(() => {
      this.overlayOpen = false;
      this.updateOverlayButtonUI(false);
    });

    // Global Hotkeys from main window
    window.camlink.onHotkeyFlipCamera(() => {
      this.switchCamera();
    });

    window.camlink.onHotkeyToggleMic(() => {
      this.toggleMic();
    });

    window.camlink.onHotkeyToggleOverlay(() => {
      this.toggleDesktopOverlay();
    });
  }

  // --- Strict Shared Protocol Action Dispatchers ---

  sendAction(action, payload = {}) {
    const msg = {
      type: 'control',
      action,
      ...payload
    };
    console.log('[RemoteControls] Sending control action:', msg);
    window.camlink.sendSignalToPhone(msg);
  }

  switchCamera() {
    this.sendAction('switchCamera');
  }

  toggleMic() {
    this.sendAction('toggleMic');
  }

  toggleVideo() {
    this.sendAction('toggleVideo');
  }

  toggleMirror() {
    this.sendAction('toggleMirror');
  }

  toggleTorch() {
    this.sendAction('toggleTorch');
  }

  toggleAf() {
    this.sendAction('toggleAf');
  }

  toggleAe() {
    this.sendAction('toggleAe');
  }

  setZoom(val) {
    if (typeof val !== 'number' || !isFinite(val)) return;
    const maxAllowed = Math.min(10.0, Math.max(1.0, this.state.maxZoom || 10.0));
    const clamped = Math.max(1.0, Math.min(maxAllowed, Math.round(val * 10) / 10));

    // Optimistic local UI update; incoming echoes are ignored while the user is interacting
    this.state.zoom = clamped;
    this.zoomInteractAt = Date.now();
    if (this.zoomSlider) this.zoomSlider.value = clamped;
    if (this.zoomVal) this.zoomVal.textContent = `${clamped.toFixed(1)}x`;
    this.syncOverlayState();

    // Throttle: max 1 message / 80 ms, trailing send of the final value
    this._pendingZoom = clamped;
    const wait = 80 - (Date.now() - this._lastZoomSend);
    if (wait <= 0) {
      this._flushZoom();
    } else if (!this._zoomTimer) {
      this._zoomTimer = setTimeout(() => this._flushZoom(), wait);
    }
  }

  _flushZoom() {
    if (this._zoomTimer) {
      clearTimeout(this._zoomTimer);
      this._zoomTimer = null;
    }
    if (this._pendingZoom === null) return;
    const value = this._pendingZoom;
    this._pendingZoom = null;
    this._lastZoomSend = Date.now();
    this.sendAction('setZoom', { value });
  }

  setQuality(resolution, fps) {
    this.sendAction('setQuality', { resolution, fps });
  }

  endSession() {
    // Ask the phone to end via control; if it is still connected after 3 s (e.g. phone has
    // "Allow PC to control camera" OFF), the PC ends the session itself with "bye".
    this.sendAction('endSession');
    if (this._byeTimer) clearTimeout(this._byeTimer);
    this._byeTimer = setTimeout(() => {
      this._byeTimer = null;
      if (this.state.connected) {
        window.camlink.sendSignalToPhone({ type: 'bye' });
      }
    }, 3000);
  }

  // --- Handling 'state' echo from the phone ---

  handlePhoneMessage(msg) {
    // Only the protocol "state" message is accepted; everything else is ignored
    if (!msg || msg.type !== 'state') return;

    const isNum = (v) => typeof v === 'number' && isFinite(v);

    if (msg.facing === 'front' || msg.facing === 'back') this.state.camera = msg.facing;
    if (typeof msg.torch === 'boolean') this.state.torch = msg.torch;
    if (typeof msg.micMuted === 'boolean') this.state.mic = !msg.micMuted;
    if (typeof msg.videoPaused === 'boolean') this.state.video = !msg.videoPaused;
    if (typeof msg.mirrored === 'boolean') this.state.mirror = msg.mirrored;
    if (typeof msg.afLocked === 'boolean') this.state.af = !msg.afLocked;
    if (typeof msg.aeLocked === 'boolean') this.state.ae = !msg.aeLocked;
    if (typeof msg.hasTorch === 'boolean') this.state.hasTorch = msg.hasTorch;
    if (typeof msg.canFlip === 'boolean') this.state.canFlip = msg.canFlip;

    if (isNum(msg.maxZoom) && msg.maxZoom >= 1) this.state.maxZoom = msg.maxZoom;
    // Do not let a stale echo move the slider while the user is dragging it
    if (isNum(msg.zoom) && msg.zoom >= 1 && Date.now() - this.zoomInteractAt > 400) {
      this.state.zoom = msg.zoom;
    }

    if (msg.resolution === '720p' || msg.resolution === '1080p') this.state.resolution = msg.resolution;
    if (msg.fps === 30 || msg.fps === 60) this.state.fps = msg.fps;

    if (isNum(msg.battery)) this.state.battery = Math.max(0, Math.min(100, Math.round(msg.battery)));
    if (['nominal', 'fair', 'serious', 'critical'].includes(msg.thermal)) this.state.thermal = msg.thermal;
    if (isNum(msg.tempC)) this.state.tempC = msg.tempC;

    this.updateAllUIFromState();
    this.syncOverlayState();
  }

  updateAllUIFromState() {
    // 1. Camera lens
    if (this.cameraLabel) {
      this.cameraLabel.textContent = this.state.camera === 'front' ? 'Front Camera' : 'Rear Camera';
    }
    if (this.btnSwitchCamera) {
      this.btnSwitchCamera.classList.toggle('active', this.state.camera === 'front');
      this.btnSwitchCamera.disabled = !this.state.canFlip;
    }
    if (this.btnQuickCamera) this.btnQuickCamera.disabled = !this.state.canFlip;

    // 2. Torch
    if (this.btnTorch) {
      this.btnTorch.disabled = !this.state.hasTorch;
      this.btnTorch.classList.toggle('active', this.state.torch);
      if (this.torchText) {
        this.torchText.textContent = this.state.torch ? 'Torch ON' : 'Torch OFF';
      }
    }
    if (this.btnQuickTorch) {
      this.btnQuickTorch.disabled = !this.state.hasTorch;
      this.btnQuickTorch.classList.toggle('active', this.state.torch);
    }

    // 3. Mic
    if (this.btnRemoteMic) {
      this.btnRemoteMic.classList.toggle('active', !this.state.mic);
      if (this.remoteMicText) {
        this.remoteMicText.textContent = this.state.mic ? 'Mic Active' : 'Mic Muted';
      }
    }

    // 4. Video
    if (this.btnRemoteVideo) {
      this.btnRemoteVideo.classList.toggle('active', !this.state.video);
      if (this.remoteVideoText) {
        this.remoteVideoText.textContent = this.state.video ? 'Video Active' : 'Video Paused';
      }
    }

    // 5. Mirror
    if (this.btnRemoteMirror) {
      this.btnRemoteMirror.classList.toggle('active', this.state.mirror);
    }
    if (this.compositor) {
      this.compositor.setMirror(this.state.mirror);
    }

    // 6. Zoom
    const sliderMax = Math.min(10.0, Math.max(1.0, this.state.maxZoom));
    if (this.zoomSlider) {
      this.zoomSlider.max = String(sliderMax);
      this.zoomSlider.value = this.state.zoom;
    }
    if (this.zoomVal) this.zoomVal.textContent = `${this.state.zoom.toFixed(1)}x`;
    if (this.zoomPresetButtons) {
      this.zoomPresetButtons.forEach(btn => {
        const val = parseFloat(btn.dataset.rcZoom);
        btn.disabled = val > sliderMax + 0.05;
        btn.classList.toggle('active', Math.abs(val - this.state.zoom) < 0.1);
      });
    }

    // 7. AF
    if (this.btnAf) {
      this.btnAf.classList.toggle('active', !this.state.af);
      if (this.afText) {
        this.afText.textContent = this.state.af ? 'AF Auto' : 'AF Locked';
      }
    }

    // 8. AE
    if (this.btnAe) {
      this.btnAe.classList.toggle('active', !this.state.ae);
      if (this.aeText) {
        this.aeText.textContent = this.state.ae ? 'AE Auto' : 'AE Locked';
      }
    }

    // 9. Quality badges
    document.querySelectorAll('[data-rc-quality]').forEach(btn => {
      const target = `${this.state.resolution}@${this.state.fps}`;
      btn.classList.toggle('active', btn.dataset.rcQuality === target);
    });

    // 10. Battery & Thermal
    if (this.state.battery !== null) {
      if (this.elBatteryBadge) this.elBatteryBadge.classList.remove('hidden');
      if (this.elBatteryText) this.elBatteryText.textContent = `${this.state.battery}%`;
    }
    if (this.state.thermal && this.elThermalBadge) {
      const cls = { nominal: 'normal', fair: 'warn', serious: 'danger', critical: 'danger' }[this.state.thermal] || 'normal';
      const temp = (typeof this.state.tempC === 'number') ? ` ${this.state.tempC.toFixed(0)}°C` : '';
      this.elThermalBadge.className = `status-badge ${cls}`;
      this.elThermalBadge.textContent = `${this.state.thermal.toUpperCase()}${temp}`;
    }
  }

  setConnected(connected, deviceName = '') {
    this.state.connected = connected;
    this.state.deviceName = connected ? (deviceName || 'Connected Phone') : 'No device connected';

    if (this.elDeviceTitle) {
      this.elDeviceTitle.textContent = this.state.deviceName;
    }

    if (!connected) {
      if (this.elBatteryBadge) this.elBatteryBadge.classList.add('hidden');
      if (this.elThermalBadge) this.elThermalBadge.classList.add('hidden');
      if (this.elLatencyBadge) this.elLatencyBadge.textContent = '-- ms';
      this.state.battery = null;
      this.state.thermal = 'nominal';
      this.state.tempC = null;
      this.state.hasTorch = true;
      this.state.canFlip = true;
      this.state.latency = null;
      if (this._byeTimer) { clearTimeout(this._byeTimer); this._byeTimer = null; }
      this._pendingZoom = null;
      if (this._zoomTimer) { clearTimeout(this._zoomTimer); this._zoomTimer = null; }
    }

    this.syncOverlayState();
  }

  setLatency(latencyMs) {
    this.state.latency = latencyMs;
    if (this.elLatencyBadge) {
      this.elLatencyBadge.textContent = `${latencyMs} ms`;
    }
    this.syncOverlayState();
  }

  // Floating Overlay Control
  async toggleDesktopOverlay() {
    if (!window.camlink) return;
    const isOpen = await window.camlink.toggleOverlay();
    this.overlayOpen = isOpen;
    this.updateOverlayButtonUI(isOpen);
    if (isOpen) {
      this.syncOverlayState();
    }
  }

  updateOverlayButtonUI(isOpen) {
    if (this.btnLaunchOverlay) {
      const text = this.btnLaunchOverlay.querySelector('.overlay-btn-text');
      if (text) {
        text.textContent = isOpen ? 'Hide Floating Controls' : 'Show Floating Controls';
      }
      this.btnLaunchOverlay.classList.toggle('active', isOpen);
    }
    if (this.btnQuickOverlay) {
      this.btnQuickOverlay.classList.toggle('active', isOpen);
    }
  }

  syncOverlayState() {
    if (!window.camlink) return;

    const overlayState = {
      phoneConnected: this.state.connected,
      deviceName: this.state.deviceName,
      recordingState: this.recorder ? this.recorder.state : 'inactive',
      recordTime: this.recorder ? this.recorder.timerString : '00:00:00',
      camera: this.state.camera,
      torch: this.state.torch,
      mic: this.state.mic,
      video: this.state.video,
      mirror: this.state.mirror,
      af: this.state.af,
      ae: this.state.ae,
      zoom: this.state.zoom,
      resolution: this.state.resolution,
      fps: this.state.fps,
      battery: this.state.battery,
      thermal: this.state.thermal,
      latency: this.state.latency,
      maxZoom: this.state.maxZoom,
      hasTorch: this.state.hasTorch,
      canFlip: this.state.canFlip,
      tempC: this.state.tempC,
      shape: this.compositor ? this.compositor.bubble.shape : 'circle',
      borderColor: this.compositor ? this.compositor.bubble.borderColor : '#00E5FF'
    };

    window.camlink.sendOverlayStateUpdate(overlayState);
  }
}

window.RemoteControlsManager = RemoteControlsManager;
