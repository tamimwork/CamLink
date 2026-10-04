class ScreenRecorder {
  constructor(canvas, audioMixer) {
    this.canvas = canvas;
    this.audioMixer = audioMixer;
    this.mediaRecorder = null;
    this.writeChain = Promise.resolve();
    this.writeError = null;
    this.state = 'inactive'; // 'inactive', 'recording', 'paused'

    this.timerInterval = null;
    this.elapsedSeconds = 0;
    this.fps = 30;
    this.format = 'webm'; // 'webm' or 'mp4'

    this.onStateChange = null;
    this.onTimerTick = null;
  }

  setFps(fps) {
    this.fps = fps;
  }

  setFormat(format) {
    this.format = format;
  }

  async start() {
    if (this.state !== 'inactive') return;

    this.writeChain = Promise.resolve();
    this.writeError = null;
    await window.camlink.recStart();
    const canvasStream = this.canvas.captureStream(this.fps);
    const audioStream = this.audioMixer.getMixedStream();

    // Combine canvas video track + mixed audio tracks
    const combinedTracks = [
      ...canvasStream.getVideoTracks(),
      ...audioStream.getAudioTracks()
    ];
    const combinedStream = new MediaStream(combinedTracks);

    // Pick best supported MIME type
    let mimeType = 'video/webm;codecs=vp9,opus';
    if (!MediaRecorder.isTypeSupported(mimeType)) {
      mimeType = 'video/webm;codecs=vp8,opus';
    }
    if (!MediaRecorder.isTypeSupported(mimeType)) {
      mimeType = 'video/webm';
    }

    const options = {
      mimeType,
      videoBitsPerSecond: 8000000 // 8 Mbps high quality
    };

    try {
      this.mediaRecorder = new MediaRecorder(combinedStream, options);

      this.mediaRecorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          const blob = e.data;
          // Write chunks to disk in order, one after another
          this.writeChain = this.writeChain.then(async () => {
            const buf = await blob.arrayBuffer();
            const r = await window.camlink.recChunk(buf);
            if (!r.success && !this.writeError) {
              this.writeError = r.error || 'Disk write failed';
              if (this.state !== 'inactive') this.stop();
            }
          });
        }
      };

      this.mediaRecorder.onstart = () => {
        this.state = 'recording';
        this.startTimer();
        if (this.onStateChange) this.onStateChange(this.state);
      };

      this.mediaRecorder.onpause = () => {
        this.state = 'paused';
        this.pauseTimer();
        if (this.onStateChange) this.onStateChange(this.state);
      };

      this.mediaRecorder.onresume = () => {
        this.state = 'recording';
        this.resumeTimer();
        if (this.onStateChange) this.onStateChange(this.state);
      };

      this.mediaRecorder.onstop = async () => {
        this.state = 'inactive';
        this.stopTimer();
        if (this.onStateChange) this.onStateChange(this.state);
        await this.handleSave();
      };

      this.mediaRecorder.start(1000); // 1-second chunks
    } catch (err) {
      console.error('Failed to start MediaRecorder:', err);
      alert('Recording error: ' + err.message);
    }
  }

  pause() {
    if (this.mediaRecorder && this.state === 'recording') {
      this.mediaRecorder.pause();
    }
  }

  resume() {
    if (this.mediaRecorder && this.state === 'paused') {
      this.mediaRecorder.resume();
    }
  }

  stop() {
    if (this.mediaRecorder && this.state !== 'inactive') {
      this.mediaRecorder.stop();
    }
  }

  startTimer() {
    this.elapsedSeconds = 0;
    this.updateTimerDisplay();
    this.timerInterval = setInterval(() => {
      this.elapsedSeconds++;
      this.updateTimerDisplay();
    }, 1000);
  }

  pauseTimer() {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  resumeTimer() {
    this.timerInterval = setInterval(() => {
      this.elapsedSeconds++;
      this.updateTimerDisplay();
    }, 1000);
  }

  stopTimer() {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
    this.elapsedSeconds = 0;
    this.updateTimerDisplay();
  }

  updateTimerDisplay() {
    const hrs = Math.floor(this.elapsedSeconds / 3600).toString().padStart(2, '0');
    const mins = Math.floor((this.elapsedSeconds % 3600) / 60).toString().padStart(2, '0');
    const secs = (this.elapsedSeconds % 60).toString().padStart(2, '0');
    const formatted = `${hrs}:${mins}:${secs}`;
    if (this.onTimerTick) this.onTimerTick(formatted);
  }

  async handleSave() {
    await this.writeChain; // wait until the last chunk is on disk

    if (this.writeError) {
      alert(`Recording problem: ${this.writeError}\nThe part recorded so far will still be saved.`);
    }

    const result = await window.camlink.recFinish({ format: this.format });

    if (result.success) {
      alert(`Recording saved to:\n${result.filePath}` + (result.warning ? `\n\n${result.warning}` : ''));
    } else if (result.canceled) {
      alert(`Save canceled. Your recording was kept at:\n${result.tempPath}`);
    } else {
      alert(`Failed to save recording: ${result.error || 'Unknown error'}` +
            (result.tempPath ? `\nRaw file kept at:\n${result.tempPath}` : ''));
    }
  }
}

window.ScreenRecorder = ScreenRecorder;
