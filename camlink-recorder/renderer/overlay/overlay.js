document.addEventListener('DOMContentLoaded', () => {
  // Elements
  const overlayPill = document.getElementById('overlay-pill');
  const pillRecDot = document.getElementById('pill-rec-dot');
  const pillTimerText = document.getElementById('pill-timer-text');

  const overlayPanel = document.getElementById('overlay-panel');
  const panelDragBar = document.getElementById('panel-drag-bar');
  const overlayPhoneStatus = document.getElementById('overlay-phone-status');
  const btnPin = document.getElementById('btn-pin');

  // Panel Recording
  const panelTimerDot = document.getElementById('panel-timer-dot');
  const panelTimerText = document.getElementById('panel-timer-text');
  const btnRec = document.getElementById('overlay-btn-rec');
  const panelRecCore = document.getElementById('panel-rec-core');
  const btnPause = document.getElementById('overlay-btn-pause');

  // Camera & Audio
  const btnCamera = document.getElementById('overlay-btn-camera');
  const btnMic = document.getElementById('overlay-btn-mic');
  const btnVideo = document.getElementById('overlay-btn-video');
  const btnMirror = document.getElementById('overlay-btn-mirror');
  const btnTorch = document.getElementById('overlay-btn-torch');

  // AF / AE
  const btnAf = document.getElementById('overlay-btn-af');
  const afText = document.getElementById('overlay-af-text');
  const btnAe = document.getElementById('overlay-btn-ae');
  const aeText = document.getElementById('overlay-ae-text');

  // Zoom
  const zoomSlider = document.getElementById('overlay-zoom-slider');
  const zoomReadout = document.getElementById('overlay-zoom-readout');
  const zoomPresets = document.querySelectorAll('.btn-zoom-preset');

  // Quality
  const qualityPresets = document.querySelectorAll('.btn-quality-preset');

  // Bubble Shape & Dock
  const shapeButtons = document.querySelectorAll('.btn-shape-icon');
  const btnFocusMain = document.getElementById('overlay-btn-focus-main');

  // Footer
  const batteryItem = document.getElementById('overlay-battery-item');
  const batteryText = document.getElementById('overlay-battery-text');
  const latencyText = document.getElementById('overlay-latency-text');
  const btnDisconnect = document.getElementById('overlay-btn-disconnect');

  // Expansion & Drag State
  let isExpanded = false;
  let isPhoneConnected = false;
  let currentRecordingState = 'inactive';
  let lastLocalZoomAt = 0;

  // Dragging logic: movement under 4px = click!
  let isDragging = false;
  let startX = 0;
  let startY = 0;
  let lastScreenX = 0;
  let lastScreenY = 0;
  let totalDragDistance = 0;

  function initDraggable(element) {
    element.addEventListener('mousedown', (e) => {
      // Only drag on left-click and not directly on an interactive button
      if (e.button !== 0 || e.target.closest('button') || e.target.closest('input')) return;
      isDragging = true;
      startX = e.screenX;
      startY = e.screenY;
      lastScreenX = e.screenX;
      lastScreenY = e.screenY;
      totalDragDistance = 0;
    });
  }

  initDraggable(overlayPill);
  initDraggable(panelDragBar);

  window.addEventListener('mousemove', (e) => {
    if (!isDragging) return;
    const dx = e.screenX - lastScreenX;
    const dy = e.screenY - lastScreenY;
    lastScreenX = e.screenX;
    lastScreenY = e.screenY;

    totalDragDistance += Math.hypot(dx, dy);

    if (window.camlink && window.camlink.moveOverlay) {
      window.camlink.moveOverlay(dx, dy);
    }
  });

  window.addEventListener('mouseup', () => {
    if (isDragging) {
      isDragging = false;
      // If dragged across screen, snap to nearest screen edge
      if (totalDragDistance >= 4) {
        if (window.camlink && window.camlink.snapOverlay) {
          window.camlink.snapOverlay();
        }
      }
    }
  });

  // Pill click: if movement under 4px = click -> expand!
  overlayPill.addEventListener('click', () => {
    if (totalDragDistance < 4) {
      expandPanel();
    }
  });

  function expandPanel() {
    isExpanded = true;
    overlayPill.classList.add('hidden');
    overlayPanel.classList.remove('hidden');
    if (window.camlink && window.camlink.setOverlaySize) {
      // Panel dimensions: 290px x 420px
      window.camlink.setOverlaySize(294, 430);
    }
  }

  function collapsePanel() {
    isExpanded = false;
    overlayPanel.classList.add('hidden');
    overlayPill.classList.remove('hidden');
    if (window.camlink && window.camlink.setOverlaySize) {
      // Pill dimensions: 48px x 48px
      window.camlink.setOverlaySize(56, 56);
      window.camlink.snapOverlay();
    }
  }

  // Collapse triggers: Pin button, Esc key
  btnPin.addEventListener('click', collapsePanel);

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && isExpanded) {
      collapsePanel();
    }
  });

  // Action Dispatchers to Main Window
  function sendAction(action, payload) {
    if (window.camlink && window.camlink.sendOverlayAction) {
      window.camlink.sendOverlayAction(action, payload);
    }
  }

  // Recording Buttons
  btnRec.addEventListener('click', () => sendAction('record-toggle'));
  btnPause.addEventListener('click', () => sendAction('pause-toggle'));

  // Camera & Audio Controls
  btnCamera.addEventListener('click', () => sendAction('switchCamera'));
  btnMic.addEventListener('click', () => sendAction('toggleMic'));
  btnVideo.addEventListener('click', () => sendAction('toggleVideo'));
  btnMirror.addEventListener('click', () => sendAction('toggleMirror'));
  btnTorch.addEventListener('click', () => sendAction('toggleTorch'));

  // AF / AE
  btnAf.addEventListener('click', () => sendAction('toggleAf'));
  btnAe.addEventListener('click', () => sendAction('toggleAe'));

  // Zoom
  zoomSlider.addEventListener('input', (e) => {
    const val = parseFloat(e.target.value);
    zoomReadout.textContent = `${val.toFixed(1)}x`;
    lastLocalZoomAt = Date.now();
    sendAction('setZoom', { value: val });
  });

  zoomPresets.forEach(btn => {
    btn.addEventListener('click', () => {
      const val = parseFloat(btn.dataset.zoom);
      zoomSlider.value = val;
      zoomReadout.textContent = `${val.toFixed(1)}x`;
      lastLocalZoomAt = Date.now();
      sendAction('setZoom', { value: val });
    });
  });

  // Quality
  qualityPresets.forEach(btn => {
    btn.addEventListener('click', () => {
      const [resolution, fpsStr] = btn.dataset.quality.split('@');
      sendAction('setQuality', { resolution, fps: parseInt(fpsStr, 10) });
    });
  });

  // Shape
  shapeButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      shapeButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      sendAction('shape-change', btn.dataset.shape);
    });
  });

  // Focus Main Window
  btnFocusMain.addEventListener('click', () => {
    if (window.camlink && window.camlink.focusMainWindow) {
      window.camlink.focusMainWindow();
    }
  });

  // End Session
  btnDisconnect.addEventListener('click', () => sendAction('endSession'));

  // State Updates from Main Window
  if (window.camlink && window.camlink.onOverlayStateUpdate) {
    window.camlink.onOverlayStateUpdate((state) => {
      if (!state) return;

      // Phone status
      if (typeof state.phoneConnected === 'boolean') {
        isPhoneConnected = state.phoneConnected;
        overlayPhoneStatus.textContent = isPhoneConnected
          ? (state.deviceName || 'Phone Connected')
          : 'Phone Disconnected';
        overlayPhoneStatus.style.color = isPhoneConnected ? 'var(--cyan-primary)' : 'var(--text-muted)';
      }

      // Recording State
      if (state.recordingState) {
        currentRecordingState = state.recordingState;
        if (currentRecordingState === 'recording') {
          pillRecDot.className = 'pill-rec-dot recording';
          panelTimerDot.className = 'timer-dot recording';
          panelRecCore.className = 'rec-core square';
          btnPause.disabled = false;
          btnPause.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>';
        } else if (currentRecordingState === 'paused') {
          pillRecDot.className = 'pill-rec-dot paused';
          panelTimerDot.className = 'timer-dot paused';
          panelRecCore.className = 'rec-core square';
          btnPause.disabled = false;
          btnPause.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>';
        } else {
          pillRecDot.className = 'pill-rec-dot';
          panelTimerDot.className = 'timer-dot';
          panelRecCore.className = 'rec-core';
          btnPause.disabled = true;
        }
      }

      // Timer text
      if (state.recordTime) {
        panelTimerText.textContent = state.recordTime;
        // Pill shows MM:SS format
        const parts = state.recordTime.split(':');
        pillTimerText.textContent = parts.length === 3 ? `${parts[1]}:${parts[2]}` : state.recordTime;
      }

      // Camera lens
      if (state.camera) {
        btnCamera.classList.toggle('active', state.camera === 'front');
      }

      // Torch
      if (typeof state.torch === 'boolean') {
        btnTorch.classList.toggle('active', state.torch);
      }

      // Mic (active when mic is unmuted)
      if (typeof state.mic === 'boolean') {
        btnMic.classList.toggle('active', !state.mic); // Highlighted red/cyan when muted or active
      }

      // Video
      if (typeof state.video === 'boolean') {
        btnVideo.classList.toggle('active', !state.video);
      }

      // Mirror
      if (typeof state.mirror === 'boolean') {
        btnMirror.classList.toggle('active', state.mirror);
      }

      // AF / AE
      if (typeof state.af === 'boolean') {
        btnAf.classList.toggle('active', state.af);
        afText.textContent = state.af ? 'AF Auto' : 'AF Locked';
      }

      if (typeof state.ae === 'boolean') {
        btnAe.classList.toggle('active', state.ae);
        aeText.textContent = state.ae ? 'AE Auto' : 'AE Locked';
      }

      // Camera / torch capability + zoom range reported by the phone
      if (typeof state.canFlip === 'boolean') btnCamera.disabled = !state.canFlip;
      if (typeof state.hasTorch === 'boolean') btnTorch.disabled = !state.hasTorch;
      if (typeof state.maxZoom === 'number' && state.maxZoom >= 1) {
        const sliderMax = Math.min(10, state.maxZoom);
        zoomSlider.max = String(sliderMax);
        zoomPresets.forEach(b => {
          b.disabled = parseFloat(b.dataset.zoom) > sliderMax + 0.05;
        });
      }

      // Zoom (do not fight the user's own dragging)
      if (typeof state.zoom === 'number' && Date.now() - lastLocalZoomAt > 400) {
        zoomSlider.value = state.zoom;
        zoomReadout.textContent = `${state.zoom.toFixed(1)}x`;
        zoomPresets.forEach(b => {
          b.classList.toggle('active', Math.abs(parseFloat(b.dataset.zoom) - state.zoom) < 0.1);
        });
      }

      // Quality
      if (state.resolution && state.fps) {
        const key = `${state.resolution}@${state.fps}`;
        qualityPresets.forEach(b => {
          b.classList.toggle('active', b.dataset.quality === key);
        });
      }

      // Shape
      if (state.shape) {
        shapeButtons.forEach(b => {
          b.classList.toggle('active', b.dataset.shape === state.shape);
        });
      }

      // Telemetry
      if (state.battery !== null && typeof state.battery !== 'undefined') {
        batteryItem.style.display = 'flex';
        batteryText.textContent = `${state.battery}%`;
      } else {
        batteryItem.style.display = 'none';
      }

      if (state.latency !== null && typeof state.latency !== 'undefined') {
        latencyText.textContent = `${state.latency} ms`;
      }
    });
  }

  // Notify main window overlay is ready
  sendAction('overlay-ready');
});
