class AudioMixer {
  constructor() {
    this.audioCtx = null;
    this.mixedDestination = null;

    // Phone Mic Source
    this.phoneSource = null;
    this.phoneGain = null;
    this.phoneAnalyser = null;
    this.isPhoneMuted = false;
    this.phoneVol = 1.0;

    // System Audio Source
    this.systemSource = null;
    this.systemGain = null;
    this.systemAnalyser = null;
    this.isSystemMuted = false;
    this.systemVol = 1.0;

    this.meterAnimationId = null;
  }

  ensureContext() {
    if (!this.audioCtx) {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      this.audioCtx = new AudioContextClass();
      this.mixedDestination = this.audioCtx.createMediaStreamDestination();
    }
    if (this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
  }

  setPhoneStream(stream) {
    this.ensureContext();
    const audioTracks = stream ? stream.getAudioTracks() : [];
    if (audioTracks.length === 0) {
      if (this.phoneSource) {
        try { this.phoneSource.disconnect(); } catch (_) {}
        this.phoneSource = null;
      }
      return;
    }

    try {
      if (this.phoneSource) {
        try { this.phoneSource.disconnect(); } catch (_) {}
      }

      this.phoneSource = this.audioCtx.createMediaStreamSource(new MediaStream([audioTracks[0]]));
      this.phoneGain = this.audioCtx.createGain();
      this.phoneAnalyser = this.audioCtx.createAnalyser();
      this.phoneAnalyser.fftSize = 64;

      this.phoneGain.gain.value = this.isPhoneMuted ? 0 : this.phoneVol;

      this.phoneSource.connect(this.phoneGain);
      this.phoneGain.connect(this.phoneAnalyser);
      this.phoneGain.connect(this.mixedDestination);
    } catch (err) {
      console.error('Error setting phone audio stream:', err);
    }
  }

  setSystemStream(stream) {
    this.ensureContext();
    const audioTracks = stream ? stream.getAudioTracks() : [];
    if (audioTracks.length === 0) {
      if (this.systemSource) {
        try { this.systemSource.disconnect(); } catch (_) {}
        this.systemSource = null;
      }
      return;
    }

    try {
      if (this.systemSource) {
        try { this.systemSource.disconnect(); } catch (_) {}
      }

      this.systemSource = this.audioCtx.createMediaStreamSource(new MediaStream([audioTracks[0]]));
      this.systemGain = this.audioCtx.createGain();
      this.systemAnalyser = this.audioCtx.createAnalyser();
      this.systemAnalyser.fftSize = 64;

      this.systemGain.gain.value = this.isSystemMuted ? 0 : this.systemVol;

      this.systemSource.connect(this.systemGain);
      this.systemGain.connect(this.systemAnalyser);
      this.systemGain.connect(this.mixedDestination);
    } catch (err) {
      console.error('Error setting system audio stream:', err);
    }
  }

  setPhoneVolume(volume) {
    this.phoneVol = volume;
    if (this.phoneGain && !this.isPhoneMuted) {
      this.phoneGain.gain.value = volume;
    }
  }

  togglePhoneMute() {
    this.isPhoneMuted = !this.isPhoneMuted;
    if (this.phoneGain) {
      this.phoneGain.gain.value = this.isPhoneMuted ? 0 : this.phoneVol;
    }
    return this.isPhoneMuted;
  }

  setSystemVolume(volume) {
    this.systemVol = volume;
    if (this.systemGain && !this.isSystemMuted) {
      this.systemGain.gain.value = volume;
    }
  }

  toggleSystemMute() {
    this.isSystemMuted = !this.isSystemMuted;
    if (this.systemGain) {
      this.systemGain.gain.value = this.isSystemMuted ? 0 : this.systemVol;
    }
    return this.isSystemMuted;
  }

  getMixedStream() {
    this.ensureContext();
    return this.mixedDestination.stream;
  }

  startMeterPolling(onLevels) {
    const poll = () => {
      let phoneLevel = 0;
      let systemLevel = 0;

      if (this.phoneAnalyser && !this.isPhoneMuted) {
        const data = new Uint8Array(this.phoneAnalyser.frequencyBinCount);
        this.phoneAnalyser.getByteFrequencyData(data);
        const avg = data.reduce((a, b) => a + b, 0) / data.length;
        phoneLevel = Math.min(100, Math.round((avg / 255) * 100 * 1.5));
      }

      if (this.systemAnalyser && !this.isSystemMuted) {
        const data = new Uint8Array(this.systemAnalyser.frequencyBinCount);
        this.systemAnalyser.getByteFrequencyData(data);
        const avg = data.reduce((a, b) => a + b, 0) / data.length;
        systemLevel = Math.min(100, Math.round((avg / 255) * 100 * 1.5));
      }

      onLevels(phoneLevel, systemLevel);
      this.meterAnimationId = requestAnimationFrame(poll);
    };

    poll();
  }

  stopMeterPolling() {
    if (this.meterAnimationId) {
      cancelAnimationFrame(this.meterAnimationId);
      this.meterAnimationId = null;
    }
  }
}

window.AudioMixer = AudioMixer;
