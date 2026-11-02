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
  let player, audioDone = preference.audio === 'default', subtitlesDone = preference.subtitles === 'default';
  function apply() {
    try {
      if (!audioDone && typeof player.getAudioTracks === 'function') {
        const tracks = player.getAudioTracks() || [];
        const index = select(tracks, preference.audio, false);
        const setter = typeof player.setCurrentAudioTrack === 'function' ? player.setCurrentAudioTrack : player.setCurrentAudio;
        if (index >= 0 && typeof setter === 'function') {
          audioDone = true;
          try { setter.call(player, index); } catch (_) { audioDone = false; }
        }
      }
    } catch (_) { /* An unavailable audio API must not affect captions or playback. */ }
    try {
      if (!subtitlesDone && typeof player.getCaptionsList === 'function' && typeof player.setCurrentCaptions === 'function') {
        const tracks = player.getCaptionsList() || [];
        const index = preference.subtitles === 'off' ? (tracks.length ? 0 : -1) : select(tracks, preference.subtitles, true);
        if (index >= 0) {
          subtitlesDone = true;
          try { player.setCurrentCaptions(index); } catch (_) { subtitlesDone = false; }
        }
      }
    } catch (_) { /* Missing captions retain the provider's own default. */ }
  }
  function connect() {
    try {
      if (typeof window.jwplayer !== 'function' || !document.getElementById('player')) return false;
      const candidate = window.jwplayer('player');
      if (!candidate || typeof candidate.on !== 'function' || typeof candidate.getAudioTracks !== 'function' || typeof candidate.getConfig !== 'function') return false;
      const config = candidate.getConfig();
      if (!config || !Array.isArray(config.playlist) || !config.playlist.length) return false;
      player = candidate;
      ['ready', 'audioTracks', 'captionsList', 'firstFrame'].forEach(event => player.on(event, apply));
      // The app loads one episode per frame document; native next/reload creates a new document.
      // Track-list refreshes and manual in-player changes never reset the one-shot defaults.
      apply();
      return true;
    } catch (_) { return false; }
  }
  const timer = setInterval(() => { if (connect()) clearInterval(timer); }, 200);
  window.addEventListener('pagehide', () => clearInterval(timer), {once: true});
})(__OZOZUZ_PREFERENCES__);
