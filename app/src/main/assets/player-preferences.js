(function (preference) {
  'use strict';
  if (location.origin !== 'https://vixcloud.cc' || !/^\/embed\/\d+$/.test(location.pathname)) return;
  if (window.__ozozuzLanguageDefaults) return;
  window.__ozozuzLanguageDefaults = true;

  const aliases = {
    en: ['en', 'eng', 'english', 'inglese'], it: ['it', 'ita', 'italian', 'italiano'],
    fr: ['fr', 'fra', 'fre', 'french', 'francais', 'francese'],
    de: ['de', 'deu', 'ger', 'german', 'deutsch', 'tedesco'],
    es: ['es', 'spa', 'spanish', 'espanol', 'spagnolo'],
    pt: ['pt', 'por', 'portuguese', 'portugues', 'portoghese'],
    ja: ['ja', 'jpn', 'japanese', 'giapponese'], ko: ['ko', 'kor', 'korean', 'coreano'],
    zh: ['zh', 'zho', 'chi', 'chinese', 'cinese'], ru: ['ru', 'rus', 'russian', 'russo'],
    ar: ['ar', 'ara', 'arabic', 'arabo']
  };
  const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  function score(track, wanted) {
    const codes = aliases[wanted] || [];
    const language = normalize(track.language || track.lang).split(/[-_]/)[0];
    const label = normalize(track.label || track.name);
    if (codes.indexOf(language) >= 0) return 100;
    const words = label.split(/[^a-z]+/);
    return codes.some(code => words.indexOf(code) >= 0) ? 50 : 0;
  }
  function select(tracks, wanted, captions) {
    let best = -1, bestScore = 0;
    tracks.forEach((track, index) => {
      if (captions && (index === 0 || track.id === 'off')) return;
      const value = score(track, wanted);
      if (value > bestScore) { best = index; bestScore = value; }
    });
    return best;
  }
  let player, started = false, retryUntil = 0, disposed = false;
  const audio = { done: preference.audio === 'default', retryAt: 0 };
  const subtitles = { done: preference.subtitles === 'default', retryAt: 0 };
  function selected(current, tracks, index, wanted) {
    if (typeof current === 'number') return current === index;
    if (!current || typeof current !== 'object') return false;
    if (wanted === 'off') return current.id === 'off';
    return score(current, wanted) > 0 || current === tracks[index];
  }
  function choose(state, tracks, wanted, captions, getter, setter) {
    if (state.done || typeof setter !== 'function') return;
    const index = wanted === 'off' ? (tracks.length ? 0 : -1) : select(tracks, wanted, captions);
    if (index < 0) return;
    if (typeof getter === 'function' && selected(getter.call(player), tracks, index, wanted)) {
      state.done = true;
      return;
    }
    if (Date.now() < state.retryAt) return;
    // An SDK setter can return successfully while its provider is still initializing.
    state.retryAt = Date.now() + 1000;
    setter.call(player, index);
    state.done = typeof getter !== 'function' || selected(getter.call(player), tracks, index, wanted);
  }
  function apply() {
    if (disposed) return;
    if (!started) {
      try { if (typeof player.getState !== 'function' || player.getState() !== 'playing') return; }
      catch (_) { return; }
      started = true;
      retryUntil = Date.now() + 10000;
    }
    if (Date.now() > retryUntil) { audio.done = true; subtitles.done = true; }
    try {
      if (!audio.done && typeof player.getAudioTracks === 'function') {
        const setter = typeof player.setCurrentAudioTrack === 'function' ? player.setCurrentAudioTrack : player.setCurrentAudio;
        choose(audio, player.getAudioTracks() || [], preference.audio, false, player.getCurrentAudioTrack, setter);
      }
    } catch (_) { /* Audio errors never prevent applying subtitles. */ }
    try {
      if (!subtitles.done && typeof player.getCaptionsList === 'function') {
        choose(subtitles, player.getCaptionsList() || [], preference.subtitles, true, player.getCurrentCaptions, player.setCurrentCaptions);
      }
    } catch (_) { /* Unavailable subtitles retain the provider default. */ }
    if (audio.done && subtitles.done) clearInterval(timer);
  }
  function firstFrame() {
    if (!started) { started = true; retryUntil = Date.now() + 10000; }
    apply();
  }
  function connect() {
    try {
      if (typeof window.jwplayer !== 'function' || !document.getElementById('player')) return false;
      const candidate = window.jwplayer('player');
      if (!candidate || typeof candidate.on !== 'function' || typeof candidate.getAudioTracks !== 'function' || typeof candidate.getConfig !== 'function') return false;
      const config = candidate.getConfig();
      if (!config || !Array.isArray(config.playlist) || !config.playlist.length) return false;
      player = candidate;
      ['ready', 'audioTracks', 'captionsList', 'audioTrackChanged', 'captionsChanged'].forEach(event => player.on(event, apply));
      player.on('firstFrame', firstFrame);
      return true;
    } catch (_) { return false; }
  }
  const timer = setInterval(() => {
    if (!player && !connect()) return;
    apply();
  }, 200);
  window.addEventListener('pagehide', () => { disposed = true; clearInterval(timer); }, {once: true});
})(__OZOZUZ_PREFERENCES__);
