const https = require('https');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { EventEmitter } = require('events');
const { WebSocketServer } = require('ws');
const selfsigned = require('selfsigned');
const QRCode = require('qrcode');

class SignalingServer extends EventEmitter {
  constructor(userDataPath) {
    super();
    this.userDataPath = userDataPath;
    this.port = 8443;
    this.server = null;
    this.wss = null;
    this.activeWs = null;
    this.activeDeviceName = null;

    this.lanIps = this.detectLanIps();
    this.activeIp = this.lanIps[0] || '127.0.0.1';
    this.pairingCode = this.generateCode();
    this.qrDataUrl = '';

    // Rate limiting for pairing code brute-force protection
    this.failedAttempts = 0;
    this.lockedUntil = 0;
  }

  detectLanIps() {
    const interfaces = os.networkInterfaces();
    const candidates = [];

    for (const name of Object.keys(interfaces)) {
      for (const iface of interfaces[name]) {
        // Skip loopback and IPv6
        if (iface.family === 'IPv4' && !iface.internal) {
          const ip = iface.address;
          // Prefer standard private LAN subnets
          if (ip.startsWith('192.168.') || ip.startsWith('10.') || /^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(ip)) {
            candidates.unshift(ip); // Prioritize private LAN
          } else {
            candidates.push(ip);
          }
        }
      }
    }
    return candidates.length > 0 ? candidates : ['127.0.0.1'];
  }

  generateCode() {
    return Math.floor(100000 + Math.random() * 900000).toString();
  }

  async updateQrCode() {
    const payload = JSON.stringify({
      ip: this.activeIp,
      port: this.port,
      code: this.pairingCode,
      ssl: true
    });
    this.qrDataUrl = await QRCode.toDataURL(payload, {
      width: 260,
      margin: 2,
      color: {
        dark: '#00E5FF',
        light: '#0D1527'
      }
    });
  }

  getCertificates() {
    const certDir = path.join(this.userDataPath, 'certificates');
    const certFile = path.join(certDir, 'cert.pem');
    const keyFile = path.join(certDir, 'key.pem');

    if (fs.existsSync(certFile) && fs.existsSync(keyFile)) {
      return {
        cert: fs.readFileSync(certFile),
        key: fs.readFileSync(keyFile)
      };
    }

    // Generate fresh self-signed cert containing all detected LAN IPs in SAN
    const altNames = [
      { type: 2, value: 'localhost' },
      { type: 7, ip: '127.0.0.1' }
    ];
    for (const ip of this.lanIps) {
      altNames.push({ type: 7, ip });
    }

    const attrs = [{ name: 'commonName', value: 'CamLink LAN Server' }];
    const pems = selfsigned.generate(attrs, {
      days: 365,
      keySize: 2048,
      algorithm: 'sha256',
      extensions: [{ name: 'subjectAltName', altNames }]
    });

    if (!fs.existsSync(certDir)) {
      fs.mkdirSync(certDir, { recursive: true });
    }
    fs.writeFileSync(certFile, pems.cert);
    fs.writeFileSync(keyFile, pems.private);

    return {
      cert: pems.cert,
      key: pems.private
    };
  }

  async start() {
    try {
      const { cert, key } = this.getCertificates();
      await this.updateQrCode();

      this.server = https.createServer({ cert, key }, (req, res) => {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'online', service: 'CamLink Signaling Server' }));
      });

      this.wss = new WebSocketServer({
        server: this.server,
        path: '/ws'
      });

      this.wss.on('connection', (ws, req) => {
        this.handleConnection(ws, req);
      });

      this.server.on('error', (err) => {
        console.error('Signaling server error:', err);
        if (err.code === 'EADDRINUSE') {
          this.emit('server-error', `Port ${this.port} is already in use by another application.`);
        } else if (err.code === 'EACCES') {
          this.emit('server-error', `Permission denied on port ${this.port}. Windows Firewall or Admin permissions required.`);
        } else {
          this.emit('server-error', err.message);
        }
      });

      this.server.listen(this.port, '0.0.0.0', () => {
        console.log(`CamLink Signaling Server listening at wss://${this.activeIp}:${this.port}/ws`);
      });
    } catch (err) {
      console.error('Failed to start signaling server:', err);
      this.emit('server-error', err.message);
    }
  }

  handleConnection(ws, req) {
    const remoteIp = req.socket.remoteAddress;
    console.log(`[CamLinkSignal] Incoming connection from ${remoteIp}`);

    let isAuthorized = false;

    ws.on('message', (messageText) => {
      try {
        const text = messageText.toString();
        console.log(`[CamLinkSignal] Received from phone: ${text}`);
        const msg = JSON.parse(text);

        // 1. Join flow
        if (msg.type === 'join') {
          // Check lockout
          if (Date.now() < this.lockedUntil) {
            const waitSec = Math.ceil((this.lockedUntil - Date.now()) / 1000);
            ws.send(JSON.stringify({ type: 'error', reason: `Too many wrong attempts. Locked for ${waitSec}s` }));
            ws.close(1008, 'Locked out');
            return;
          }

          // Check if already connected with an active phone
          if (this.activeWs && this.activeWs !== ws && this.activeWs.readyState === ws.OPEN) {
            // If it's a re-join with the correct code from the same session, allow handover
            if (msg.code === this.pairingCode) {
              console.log('[CamLinkSignal] Existing phone session replaced by reconnecting phone');
              try { this.activeWs.close(1000, 'Replaced by reconnect'); } catch (_) {}
            } else {
              ws.send(JSON.stringify({ type: 'error', reason: 'busy' }));
              ws.close(1008, 'Server busy');
              return;
            }
          }

          // Validate pairing code
          if (msg.code !== this.pairingCode) {
            this.failedAttempts++;
            if (this.failedAttempts >= 5) {
              this.lockedUntil = Date.now() + 30000;
              this.failedAttempts = 0;
              console.warn('[CamLinkSignal] 5 failed pairing attempts. Locked for 30 seconds.');
            }
            ws.send(JSON.stringify({ type: 'error', reason: 'invalid_code' }));
            ws.close(1008, 'Invalid code');
            return;
          }

          // Authorized successfully
          isAuthorized = true;
          this.failedAttempts = 0;
          this.activeWs = ws;
          this.activeDeviceName = msg.deviceName || 'Android Device';

          // Reply: {"type":"joined"}
          const joinedReply = JSON.stringify({ type: 'joined' });
          console.log(`[CamLinkSignal] Sent to phone: ${joinedReply}`);
          ws.send(joinedReply);

          this.emit('phone-connected', {
            deviceName: this.activeDeviceName,
            remoteIp
          });
          return;
        }

        if (!isAuthorized) {
          ws.send(JSON.stringify({ type: 'error', reason: 'invalid_code' }));
          ws.close(1008, 'Unauthorized');
          return;
        }

        // 2. Ping / Pong
        if (msg.type === 'ping') {
          const pong = JSON.stringify({ type: 'pong', t: msg.t });
          ws.send(pong);
          return;
        }

        // 3. User taps leave
        if (msg.type === 'leave') {
          console.log('[CamLinkSignal] Phone sent leave');
          this.handlePhoneDisconnect('Phone disconnected');
          return;
        }

        // 4. Forward offer or ice to renderer process
        if (msg.type === 'offer' || msg.type === 'ice') {
          this.emit('signal-message', msg);
        }
      } catch (err) {
        console.error('[CamLinkSignal] Error processing message:', err);
      }
    });

    ws.on('close', () => {
      if (this.activeWs === ws) {
        this.handlePhoneDisconnect('Connection dropped');
      }
    });

    ws.on('error', (err) => {
      console.error('[CamLinkSignal] WebSocket error:', err);
      if (this.activeWs === ws) {
        this.handlePhoneDisconnect('Socket error');
      }
    });
  }

  handlePhoneDisconnect(reason) {
    this.activeWs = null;
    this.activeDeviceName = null;
    this.emit('phone-disconnected', reason);
  }

  sendToPhone(msg) {
    if (this.activeWs && this.activeWs.readyState === 1 /* OPEN */) {
      const text = JSON.stringify(msg);
      console.log(`[CamLinkSignal] Sent to phone: ${text}`);
      this.activeWs.send(text);
    } else {
      console.warn('[CamLinkSignal] Cannot send to phone: No active WebSocket');
    }
  }

  async setActiveIp(ip) {
    if (this.lanIps.includes(ip)) {
      this.activeIp = ip;
      await this.updateQrCode();
      return this.getInfo();
    }
    return this.getInfo();
  }

  async generateNewCode() {
    this.pairingCode = this.generateCode();
    await this.updateQrCode();
    // If phone was connected, notify disconnect
    if (this.activeWs) {
      try {
        this.sendToPhone({ type: 'bye' });
        this.activeWs.close(1000, 'Code regenerated');
      } catch (_) {}
      this.activeWs = null;
      this.activeDeviceName = null;
      this.emit('phone-disconnected', 'Pairing code changed');
    }
    return this.getInfo();
  }

  getInfo() {
    return {
      activeIp: this.activeIp,
      lanIps: this.lanIps,
      port: this.port,
      code: this.pairingCode,
      qrDataUrl: this.qrDataUrl,
      connectedDevice: this.activeDeviceName,
      isLocked: Date.now() < this.lockedUntil,
      lockRemainingSeconds: Math.max(0, Math.ceil((this.lockedUntil - Date.now()) / 1000))
    };
  }

  stop() {
    if (this.activeWs) {
      try {
        this.sendToPhone({ type: 'bye' });
        this.activeWs.close(1000, 'Server stopping');
      } catch (_) {}
    }
    if (this.wss) {
      this.wss.close();
    }
    if (this.server) {
      this.server.close();
    }
  }
}

module.exports = SignalingServer;
