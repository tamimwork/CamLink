document.addEventListener('DOMContentLoaded', async () => {
  // Elements
  const canvas = document.getElementById('compositor-canvas');
  const screenVideo = document.getElementById('screen-video');
  const phoneVideo = document.getElementById('phone-video');
  const interactiveBubble = document.getElementById('interactive-bubble');
  const countdownOverlay = document.getElementById('countdown-overlay');
  const countdownNumber = document.getElementById('countdown-number');

  // Status & Connect Elements
  const statusIndicator = document.getElementById('status-indicator');
  const statusLabel = document.getElementById('status-label');
  const qrImage = document.getElementById('qr-image');
  const networkIpSelect = document.getElementById('network-ip-select');
  const pairingCodeLabel = document.getElementById('pairing-code-label');
  const btnNewCode = document.getElementById('btn-new-code');
  const firewallHintBox = document.getElementById('firewall-hint-box');

  // Diagnostics
  const diagLatency = document.getElementById('diag-latency');
  const diagResolution = document.getElementById('diag-resolution');

  // Sources
  const sourcesGrid = document.getElementById('sources-grid');
  const btnRefreshSources = document.getElementById('btn-refresh-sources');

  // Bubble Controls
  const bubbleSizeSlider = document.getElementById('bubble-size-slider');
  const bubbleSizeVal = document.getElementById('bubble-size-val');
  const mirrorCheckbox = document.getElementById('mirror-checkbox');
  const btnFaceCamPopout = document.getElementById('btn-facecam-popout');

  // Audio Controls
  const phoneVolumeSlider = document.getElementById('phone-volume-slider');
  const systemVolumeSlider = document.getElementById('system-volume-slider');
  const btnPhoneMute = document.getElementById('btn-phone-mute');
  const btnSystemMute = document.getElementById('btn-system-mute');
  const phoneVuBar = document.getElementById('phone-vu-bar');
  const systemVuBar = document.getElementById('system-vu-bar');
  const systemAudioUnsupportedNote = document.getElementById('system-audio-unsupported-note');

  // Recording Controls
  const btnRecord = document.getElementById('btn-record');
  const btnPause = document.getElementById('btn-pause');
  const btnStop = document.getElementById('btn-stop');
  const recordTimer = document.getElementById('record-timer');
  const recDot = document.getElementById('rec-dot');

  // Settings
  const settingAutoOverlay = document.getElementById('setting-auto-overlay');
  const settingMinimizeMain = document.getElementById('setting-minimize-main');

  // macOS Permission Modal
  const macPermissionModal = document.getElementById('mac-permission-modal');
  const btnOpenMacPrefs = document.getElementById('btn-open-mac-prefs');
  const btnDismissMacModal = document.getElementById('btn-dismiss-mac-modal');

  // Instantiate Modules
  const compositor = new VideoCompositor(canvas, screenVideo, phoneVideo);
  const audioMixer = new AudioMixer();
  const recorder = new ScreenRecorder(canvas, audioMixer);
  const remoteControls = new RemoteControlsManager({
    compositor,
    recorder,
    audioMixer,
    phoneVideo
  });

  compositor.start();
  audioMixer.startMeterPolling((phoneLevel, systemLevel) => {
    phoneVuBar.style.width = phoneLevel + '%';
    systemVuBar.style.width = systemLevel + '%';
  });

  // WebRTC PeerConnection State
  let peerConnection = null;
  let phoneMediaStream = null;

  // Platform-aware adjustments
  if (window.camlink && window.camlink.isMac) {
    if (firewallHintBox) {
      firewallHintBox.innerHTML = '<strong>macOS & Wi-Fi:</strong> Ensure your iPhone/Android and Mac are connected to the same Wi-Fi. If macOS asks to allow incoming network connections for CamLink Recorder, click <em>Allow</em>.';
    }
  }

  // Initialize Server Info & QR
  async function refreshServerInfo() {
    const info = await window.camlink.getServerInfo();
    if (!info) return;

    pairingCodeLabel.textContent = info.code;
    qrImage.src = info.qrDataUrl;

    // Populate network IP dropdown
    networkIpSelect.innerHTML = '';
    info.lanIps.forEach(ip => {
      const opt = document.createElement('option');
      opt.value = ip;
      opt.textContent = ip;
      if (ip === info.activeIp) opt.selected = true;
      networkIpSelect.appendChild(opt);
    });

    if (info.connectedDevice) {
      setConnectionStatus(true, info.connectedDevice);
    } else {
      setConnectionStatus(false);
    }
  }

  networkIpSelect.addEventListener('change', async (e) => {
    const info = await window.camlink.selectNetworkIp(e.target.value);
    pairingCodeLabel.textContent = info.code;
    qrImage.src = info.qrDataUrl;
  });

  btnNewCode.addEventListener('click', async () => {
    const info = await window.camlink.generateNewCode();
    pairingCodeLabel.textContent = info.code;
    qrImage.src = info.qrDataUrl;
    closePeerConnection();
    setConnectionStatus(false);
  });

  function setConnectionStatus(connected, deviceName = '') {
    if (connected) {
      statusIndicator.className = 'status-dot connected';
      statusLabel.textContent = `Connected: ${deviceName}`;
      compositor.setPhoneConnected(true);
    } else {
      statusIndicator.className = 'status-dot pulsing';
      statusLabel.textContent = 'Waiting for phone...';
      compositor.setPhoneConnected(false);
      diagResolution.textContent = '--';
      diagLatency.textContent = '-- ms';
    }
  }

  // WebRTC Setup: Phone is offerer; PC is answerer
  function createPeerConnection() {
    closePeerConnection();

    const config = {
      iceServers: [] // LAN only: no STUN needed
    };

    peerConnection = new RTCPeerConnection(config);

    peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        window.camlink.sendSignalToPhone({
          type: 'ice',
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex
        });
      }
    };

    peerConnection.ontrack = (event) => {
      console.log('[WebRTC] Received remote track:', event.track.kind);
      if (!phoneMediaStream) {
        phoneMediaStream = new MediaStream();
        phoneVideo.srcObject = phoneMediaStream;
      }
      phoneMediaStream.addTrack(event.track);

      if (event.track.kind === 'video') {
        phoneVideo.play().catch(() => {});
        event.track.onended = () => {
          compositor.setPhoneConnected(false);
        };
      } else if (event.track.kind === 'audio') {
        audioMixer.setPhoneStream(phoneMediaStream);
      }

      phoneVideo.onloadedmetadata = () => {
        diagResolution.textContent = `${phoneVideo.videoWidth}x${phoneVideo.videoHeight}`;
      };
    };

    peerConnection.onconnectionstatechange = () => {
      console.log('[WebRTC] Connection state:', peerConnection.connectionState);
      if (peerConnection.connectionState === 'connected') {
        compositor.setPhoneConnected(true);
      } else if (peerConnection.connectionState === 'disconnected' || peerConnection.connectionState === 'failed') {
        compositor.setPhoneConnected(false);
      }
    };

    return peerConnection;
  }

  function closePeerConnection() {
    if (peerConnection) {
      try {
        peerConnection.close();
      } catch (_) {}
      peerConnection = null;
    }
    phoneMediaStream = null;
    phoneVideo.srcObject = null;
    compositor.setPhoneConnected(false);
  }

  // Handle IPC signaling events from phone
  window.camlink.onPhoneConnected((info) => {
    setConnectionStatus(true, info.deviceName);
    remoteControls.setConnected(true, info.deviceName);
  });

  window.camlink.onPhoneDisconnected((reason) => {
    console.log('[WebRTC] Phone disconnected:', reason);
    setConnectionStatus(false);
    remoteControls.setConnected(false);
    closePeerConnection();
  });

  window.camlink.onLatencyUpdate((latencyMs) => {
    diagLatency.textContent = `${latencyMs} ms`;
    remoteControls.setLatency(latencyMs);
  });

  window.camlink.onSignalMessage(async (msg) => {
    // If msg is state echo, device-state, or battery/status, forward to remoteControls
    if (msg.type !== 'offer' && msg.type !== 'ice') {
      remoteControls.handlePhoneMessage(msg);
      return;
    }

    if (msg.type === 'offer') {
      console.log('[WebRTC] Handling offer from phone');
      try {
        const pc = createPeerConnection();

        await pc.setRemoteDescription(new RTCSessionDescription({
          type: 'offer',
          sdp: msg.sdp
        }));

        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);

        // Reply with answer
        window.camlink.sendSignalToPhone({
          type: 'answer',
          sdp: answer.sdp
        });
      } catch (err) {
        console.error('[WebRTC] Failed to handle offer:', err);
      }
    } else if (msg.type === 'ice') {
      if (peerConnection && msg.candidate) {
        try {
          await peerConnection.addIceCandidate(new RTCIceCandidate({
            candidate: msg.candidate,
            sdpMid: msg.sdpMid,
            sdpMLineIndex: msg.sdpMLineIndex
          }));
        } catch (err) {
          console.warn('[WebRTC] Error adding ICE candidate:', err);
        }
      }
    }
  });

  window.camlink.onServerError((err) => {
    alert('Server Error: ' + err);
  });

  // Check macOS Screen Recording Permissions
  async function verifyScreenRecordingPermission() {
    if (window.camlink && window.camlink.isMac) {
      const res = await window.camlink.checkScreenRecordingPermission();
      if (res && res.hasPermission === false) {
        macPermissionModal.classList.remove('hidden');
      }
    }
  }

  if (btnOpenMacPrefs) {
    btnOpenMacPrefs.addEventListener('click', () => {
      window.camlink.openMacScreenRecordingPreferences();
      macPermissionModal.classList.add('hidden');
    });
  }

  if (btnDismissMacModal) {
    btnDismissMacModal.addEventListener('click', () => {
      macPermissionModal.classList.add('hidden');
    });
  }

  // Screen Sources Selection
  async function loadScreenSources() {
    const sources = await window.camlink.getScreenSources();
    sourcesGrid.innerHTML = '';

    if (sources.length === 0 && window.camlink.isMac) {
      macPermissionModal.classList.remove('hidden');
    }

    sources.forEach((source, index) => {
      const item = document.createElement('div');
      item.className = 'source-item' + (index === 0 ? ' selected' : '');
      item.innerHTML = `
        <img src="${source.thumbnail}" alt="${source.name}">
        <span title="${source.name}">${source.name}</span>
      `;
      item.addEventListener('click', () => {
        document.querySelectorAll('.source-item').forEach(el => el.classList.remove('selected'));
        item.classList.add('selected');
        startScreenCapture(source.id);
      });
      sourcesGrid.appendChild(item);
    });

    if (sources.length > 0) {
      startScreenCapture(sources[0].id);
    }
  }

  async function startScreenCapture(sourceId) {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          mandatory: {
            chromeMediaSource: 'desktop'
          }
        },
        video: {
          mandatory: {
            chromeMediaSource: 'desktop',
            chromeMediaSourceId: sourceId,
            minWidth: 1280,
            maxWidth: 1920,
            minHeight: 720,
            maxHeight: 1080
          }
        }
      });

      screenVideo.srcObject = stream;
      screenVideo.play();
      audioMixer.setSystemStream(stream);

      // System audio succeeded
      if (systemAudioUnsupportedNote) systemAudioUnsupportedNote.classList.add('hidden');
      if (btnSystemMute) {
        btnSystemMute.disabled = false;
        btnSystemMute.title = 'Mute System Audio';
      }
    } catch (err) {
      console.warn('System audio loopback not supported or denied; retrying video-only fallback:', err);
      try {
        const videoOnly = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: {
            mandatory: {
              chromeMediaSource: 'desktop',
              chromeMediaSourceId: sourceId
            }
          }
        });
        screenVideo.srcObject = videoOnly;
        screenVideo.play();

        // System audio fallback: disable system audio toggle safely with tooltip, never crash
        if (systemAudioUnsupportedNote) {
          systemAudioUnsupportedNote.classList.remove('hidden');
        }
        if (btnSystemMute) {
          btnSystemMute.disabled = true;
          btnSystemMute.title = 'System audio loopback unsupported on this source';
          btnSystemMute.style.opacity = '0.4';
        }
        if (systemVolumeSlider) {
          systemVolumeSlider.disabled = true;
          systemVolumeSlider.title = 'System audio unavailable';
        }
      } catch (e2) {
        console.error('Failed to capture desktop source:', e2);
      }
    }
  }

  btnRefreshSources.addEventListener('click', loadScreenSources);

  // Tab Switching
  document.querySelectorAll('.nav-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      tab.classList.add('active');
      document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
    });
  });

  // Face Cam Bubble Controls
  document.querySelectorAll('[data-shape]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-shape]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const shape = btn.dataset.shape;
      compositor.setBubbleShape(shape);
      interactiveBubble.className = `interactive-bubble ${shape}`;
      remoteControls.syncOverlayState();
    });
  });

  bubbleSizeSlider.addEventListener('input', (e) => {
    const size = parseInt(e.target.value, 10);
    bubbleSizeVal.textContent = size;
    compositor.setBubbleSize(size);
    updateInteractiveBubbleUI();
  });

  document.querySelectorAll('[data-color]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-color]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const color = btn.dataset.color;
      compositor.setBorderColor(color);
      interactiveBubble.style.borderColor = color === 'transparent' ? 'var(--border-strong)' : color;
      remoteControls.syncOverlayState();
    });
  });

  mirrorCheckbox.addEventListener('change', (e) => {
    compositor.setMirror(e.target.checked);
    remoteControls.syncOverlayState();
  });

  if (btnFaceCamPopout) {
    btnFaceCamPopout.addEventListener('click', async () => {
      if (window.camlink && window.camlink.openFaceCamPopout) {
        window.camlink.openFaceCamPopout();
      }
    });
  }

  // Audio Sliders & Mutes
  phoneVolumeSlider.addEventListener('input', (e) => {
    audioMixer.setPhoneVolume(parseFloat(e.target.value));
  });

  systemVolumeSlider.addEventListener('input', (e) => {
    audioMixer.setSystemVolume(parseFloat(e.target.value));
  });

  btnPhoneMute.addEventListener('click', () => {
    const muted = audioMixer.togglePhoneMute();
    btnPhoneMute.style.color = muted ? 'var(--danger)' : 'var(--muted)';
  });

  btnSystemMute.addEventListener('click', () => {
    if (btnSystemMute.disabled) return;
    const muted = audioMixer.toggleSystemMute();
    btnSystemMute.style.color = muted ? 'var(--danger)' : 'var(--muted)';
  });

  // Output Resolution & FPS
  document.querySelectorAll('[data-res]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-res]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const res = btn.dataset.res;
      if (res === '1080') {
        compositor.setResolution(1920, 1080);
        document.getElementById('diag-canvas-size').textContent = '1920x1080';
      } else {
        compositor.setResolution(1280, 720);
        document.getElementById('diag-canvas-size').textContent = '1280x720';
      }
      updateInteractiveBubbleUI();
    });
  });

  document.querySelectorAll('[data-fps]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-fps]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const fpsValue = parseInt(btn.dataset.fps, 10);
      recorder.setFps(fpsValue);
      compositor.setFps(fpsValue);
    });
  });

  document.querySelectorAll('[data-format]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-format]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      recorder.setFormat(btn.dataset.format);
    });
  });

  // Draggable Face Bubble Overlay
  let isDragging = false;
  let dragOffset = { x: 0, y: 0 };

  function updateInteractiveBubbleUI() {
    const rect = canvas.getBoundingClientRect();
    const scaleX = rect.width / canvas.width;
    const scaleY = rect.height / canvas.height;

    const b = compositor.bubble;
    const displaySize = b.size * scaleX;

    interactiveBubble.style.width = displaySize + 'px';
    interactiveBubble.style.height = displaySize + 'px';
    interactiveBubble.style.left = (rect.left + b.x * scaleX - displaySize / 2) + 'px';
    interactiveBubble.style.top = (rect.top + b.y * scaleY - displaySize / 2) + 'px';
  }

  interactiveBubble.addEventListener('mousedown', (e) => {
    isDragging = true;
    const bRect = interactiveBubble.getBoundingClientRect();
    dragOffset.x = e.clientX - bRect.left;
    dragOffset.y = e.clientY - bRect.top;
    e.preventDefault();
  });

  window.addEventListener('mousemove', (e) => {
    if (!isDragging) return;
    const cRect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / cRect.width;
    const scaleY = canvas.height / cRect.height;

    const mouseCanvasX = (e.clientX - cRect.left) * scaleX;
    const mouseCanvasY = (e.clientY - cRect.top) * scaleY;

    compositor.bubble.x = mouseCanvasX;
    compositor.bubble.y = mouseCanvasY;
    compositor.clampBubblePosition();
    updateInteractiveBubbleUI();
  });

  window.addEventListener('mouseup', () => {
    isDragging = false;
  });

  window.addEventListener('resize', updateInteractiveBubbleUI);

  // Recorder State Callback
  let lastRecState = 'inactive';
  recorder.onStateChange = async (state) => {
    const prevRecState = lastRecState;
    lastRecState = state;
    if (state === 'recording') {
      recDot.className = 'rec-dot recording';
      btnRecord.disabled = true;
      btnPause.disabled = false;
      btnStop.disabled = false;
      btnPause.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>';

      // Setting: Show floating controls while recording (default ON)
      // Only when recording starts from inactive (not when resuming from pause)
      if (prevRecState === 'inactive' && settingAutoOverlay && settingAutoOverlay.checked) {
        if (window.camlink && window.camlink.openOverlay) {
          window.camlink.openOverlay();
        }
      }

      // Setting: Minimize main window when recording starts
      if (settingMinimizeMain && settingMinimizeMain.checked) {
        // Electron window minimize can be requested via ipc or direct
        const win = window.camlink;
        if (win && win.isMac) {
          // Keep active or minimize
        }
      }
    } else if (state === 'paused') {
      recDot.className = 'rec-dot';
      btnPause.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>';
    } else {
      recDot.className = 'rec-dot';
      btnRecord.disabled = false;
      btnPause.disabled = true;
      btnStop.disabled = true;

      // When recording stops: auto hide floating controls if setting enabled
      if (settingAutoOverlay && settingAutoOverlay.checked) {
        if (window.camlink && window.camlink.closeOverlay) {
          window.camlink.closeOverlay();
        }
      }
    }
    remoteControls.syncOverlayState();
  };

  recorder.onTimerTick = (timeString) => {
    recordTimer.textContent = timeString;
    remoteControls.syncOverlayState();
  };

  // 3-2-1 Countdown & Trigger
  function triggerRecordingWithCountdown() {
    if (recorder.state !== 'inactive') {
      recorder.stop();
      return;
    }

    countdownOverlay.classList.remove('hidden');
    let count = 3;
    countdownNumber.textContent = count;

    const interval = setInterval(() => {
      count--;
      if (count > 0) {
        countdownNumber.textContent = count;
      } else {
        clearInterval(interval);
        countdownOverlay.classList.add('hidden');
        recorder.start();
      }
    }, 900);
  }

  btnRecord.addEventListener('click', triggerRecordingWithCountdown);

  btnPause.addEventListener('click', () => {
    if (recorder.state === 'recording') {
      recorder.pause();
    } else if (recorder.state === 'paused') {
      recorder.resume();
    }
  });

  btnStop.addEventListener('click', () => {
    recorder.stop();
  });

  // Global Hotkey IPC
  window.camlink.onHotkeyRecordToggle(() => {
    triggerRecordingWithCountdown();
  });

  window.camlink.onHotkeyPauseToggle(() => {
    if (recorder.state === 'recording') {
      recorder.pause();
    } else if (recorder.state === 'paused') {
      recorder.resume();
    }
  });

  // Initial setup & verification
  await refreshServerInfo();
  await verifyScreenRecordingPermission();
  await loadScreenSources();
  setTimeout(updateInteractiveBubbleUI, 200);

  // Platform-specific Hotkey Tips
  if (window.camlink && window.camlink.isMac) {
    const tip = document.querySelector('.hotkey-tip');
    if (tip) {
      tip.innerHTML = 'Hotkeys: <strong>Cmd+Shift+R</strong> (Rec) | <strong>Cmd+Shift+P</strong> (Pause) | <strong>Cmd+Shift+O</strong> (Overlay) | <strong>Cmd+Shift+F</strong> (Flip) | <strong>Cmd+Shift+M</strong> (Mic)';
    }
  }
});
