class VideoCompositor {
  constructor(canvas, screenVideo, phoneVideo) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.screenVideo = screenVideo;
    this.phoneVideo = phoneVideo;

    // Face Bubble Configuration
    this.bubble = {
      x: 1560,
      y: 720,
      size: 260,
      shape: 'circle', // 'circle', 'rounded', 'rect'
      borderColor: '#00E5FF',
      borderWidth: 4,
      isMirrored: false
    };

    this.isPhoneConnected = false;
    this.isRunning = false;
    this.timer = null;
    this.fps = 30;
  }

  start() {
    if (this.isRunning) return;
    this.isRunning = true;
    this.restartTimer();
  }

  restartTimer() {
    // setInterval keeps running when the window is minimized
    // (requestAnimationFrame stops, which would freeze the recording)
    if (this.timer) clearInterval(this.timer);
    this.timer = setInterval(() => this.render(), Math.round(1000 / this.fps));
  }

  setFps(fps) {
    this.fps = fps;
    if (this.isRunning) this.restartTimer();
  }

  stop() {
    this.isRunning = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  setResolution(width, height) {
    this.canvas.width = width;
    this.canvas.height = height;
    // Keep bubble within bounds
    this.clampBubblePosition();
  }

  setBubblePosition(normX, normY) {
    this.bubble.x = normX * this.canvas.width;
    this.bubble.y = normY * this.canvas.height;
    this.clampBubblePosition();
  }

  setBubbleSize(size) {
    this.bubble.size = size;
    this.clampBubblePosition();
  }

  setBubbleShape(shape) {
    this.bubble.shape = shape;
  }

  setBorderColor(color) {
    this.bubble.borderColor = color;
  }

  setMirror(isMirrored) {
    this.bubble.isMirrored = isMirrored;
  }

  setPhoneConnected(connected) {
    this.isPhoneConnected = connected;
  }

  clampBubblePosition() {
    const r = this.bubble.size / 2;
    this.bubble.x = Math.max(r, Math.min(this.canvas.width - r, this.bubble.x));
    this.bubble.y = Math.max(r, Math.min(this.canvas.height - r, this.bubble.y));
  }

  render() {
    const ctx = this.ctx;
    const w = this.canvas.width;
    const h = this.canvas.height;

    // 1. Draw Background Screen
    if (this.screenVideo && this.screenVideo.readyState >= 2) {
      ctx.drawImage(this.screenVideo, 0, 0, w, h);
    } else {
      // Dark standby canvas
      ctx.fillStyle = '#090D16';
      ctx.fillRect(0, 0, w, h);

      ctx.fillStyle = '#334155';
      ctx.font = 'bold 24px -apple-system, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('Select Screen Source in Sidebar', w / 2, h / 2);
    }

    // 2. Draw Face Bubble
    this.drawFaceBubble();
  }

  drawFaceBubble() {
    const ctx = this.ctx;
    const b = this.bubble;
    const x = b.x;
    const y = b.y;
    const size = b.size;
    const r = size / 2;

    ctx.save();

    // Setup Clipping Path based on Shape
    ctx.beginPath();
    if (b.shape === 'circle') {
      ctx.arc(x, y, r, 0, Math.PI * 2);
    } else if (b.shape === 'rounded') {
      this.drawRoundedRect(ctx, x - r, y - r, size, size, 24);
    } else {
      ctx.rect(x - r, y - r, size, size);
    }
    ctx.closePath();

    // Clip to shape
    ctx.save();
    ctx.clip();

    // Draw Phone Video Feed or Disconnected Placeholder
    const hasActiveVideo = this.isPhoneConnected && this.phoneVideo && this.phoneVideo.readyState >= 2;
    if (hasActiveVideo) {
      ctx.save();
      if (b.isMirrored) {
        ctx.translate(x, y);
        ctx.scale(-1, 1);
        ctx.translate(-x, -y);
      }

      // Center crop image into square
      const vw = this.phoneVideo.videoWidth || 1;
      const vh = this.phoneVideo.videoHeight || 1;
      const minDim = Math.min(vw, vh);
      const sx = (vw - minDim) / 2;
      const sy = (vh - minDim) / 2;

      ctx.drawImage(this.phoneVideo, sx, sy, minDim, minDim, x - r, y - r, size, size);
      ctx.restore();
    } else {
      // Disconnected Placeholder
      ctx.fillStyle = '#111827';
      ctx.fillRect(x - r, y - r, size, size);

      // Avatar Icon
      ctx.fillStyle = '#334155';
      ctx.beginPath();
      ctx.arc(x, y - 18, size * 0.22, 0, Math.PI * 2);
      ctx.fill();

      ctx.beginPath();
      ctx.arc(x, y + size * 0.35, size * 0.35, Math.PI, 0, false);
      ctx.fill();

      // Disconnected text
      ctx.fillStyle = '#F59E0B';
      ctx.font = 'bold 12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('Phone Disconnected', x, y + r - 18);
    }

    ctx.restore(); // Undo clip

    // Draw Border
    if (b.borderColor !== 'transparent') {
      ctx.strokeStyle = b.borderColor;
      ctx.lineWidth = b.borderWidth;
      ctx.shadowColor = b.borderColor;
      ctx.shadowBlur = 10;

      ctx.beginPath();
      if (b.shape === 'circle') {
        ctx.arc(x, y, r, 0, Math.PI * 2);
      } else if (b.shape === 'rounded') {
        this.drawRoundedRect(ctx, x - r, y - r, size, size, 24);
      } else {
        ctx.rect(x - r, y - r, size, size);
      }
      ctx.stroke();
    }

    ctx.restore();
  }

  drawRoundedRect(ctx, x, y, width, height, radius) {
    ctx.moveTo(x + radius, y);
    ctx.lineTo(x + width - radius, y);
    ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
    ctx.lineTo(x + width, y + height - radius);
    ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
    ctx.lineTo(x + radius, y + height);
    ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
    ctx.lineTo(x, y + radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);
  }
}

window.VideoCompositor = VideoCompositor;
