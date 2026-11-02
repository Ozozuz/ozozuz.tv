import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';
const source = readFileSync(new URL('../app/src/main/assets/player-preferences.js', import.meta.url), 'utf8');
function fixture(preference = { audio: 'en', subtitles: 'it' }, options = {}) {
  const events = {}, calls = { audio: [], subtitles: [] };
  const model = {
    audio: options.audio || [{ language: 'it' }, { language: 'en' }],
    captions: options.captions || [{ id: 'off', label: 'Off' }, { language: 'en', label: 'English [CC]' }, { label: 'Italian' }],
    config: options.config || { playlist: [{ file: 'https://media.invalid/master.m3u8' }] }
  };
  const player = {
    getConfig: () => model.config, getAudioTracks: () => model.audio, getCaptionsList: () => model.captions,
    setCurrentAudioTrack: index => calls.audio.push(index), setCurrentCaptions: index => calls.subtitles.push(index),
    on: (event, listener) => { (events[event] ||= []).push(listener); return player; }
  };
  if (options.legacyAudioSetter) { player.setCurrentAudio = player.setCurrentAudioTrack; delete player.setCurrentAudioTrack; }
  if (options.noAudioSetter) delete player.setCurrentAudioTrack;
  let tick, cleared = false;
  const location = { origin: options.origin || 'https://vixcloud.cc', pathname: '/embed/402042' };
  const context = { location, document: { getElementById: () => ({}) },
    window: { jwplayer: () => player, addEventListener() {} },
    setInterval: fn => { tick = fn; return 1; }, clearInterval: () => { cleared = true; } };
  runInNewContext(source.replace('__OZOZUZ_PREFERENCES__', JSON.stringify(preference)), context);
  return { model, calls, events, tick: () => { if (!cleared && tick) tick(); }, fire: event => (events[event] || []).forEach(fn => fn()) };
}
test('English audio and Italian captions are selected independently', () => {
  const f = fixture(); f.tick();
  assert.deepEqual(f.calls, { audio: [1], subtitles: [2] });
});
test('missing languages leave defaults intact and delayed tracks can still be selected', () => {
  const f = fixture(undefined, { audio: [{ language: 'it' }], captions: [{ id: 'off' }] }); f.tick();
  assert.deepEqual(f.calls, { audio: [], subtitles: [] });
  f.model.audio.push({ language: 'en' }); f.model.captions.push({ language: 'it' });
  f.fire('audioTracks'); f.fire('captionsList');
  assert.deepEqual(f.calls, { audio: [1], subtitles: [1] });
});
test('manual choices are not overwritten by track refreshes or playback events', () => {
  const f = fixture(); f.tick();
  for (const event of ['audioTracks', 'captionsList', 'firstFrame', 'ready', 'playlistItem']) f.fire(event);
  assert.deepEqual(f.calls, { audio: [1], subtitles: [2] });
});
test('subtitles can be disabled explicitly', () => {
  const f = fixture({ audio: 'default', subtitles: 'off' }); f.tick();
  assert.deepEqual(f.calls, { audio: [], subtitles: [0] });
});
test('provider defaults are left untouched', () => {
  const f = fixture({ audio: 'default', subtitles: 'default' }); f.tick();
  assert.deepEqual(f.calls, { audio: [], subtitles: [] });
});
test('region codes, ISO 639-2 and language labels are recognized', () => {
  for (const language of ['en-US', 'eng']) {
    const f = fixture(undefined, { audio: [{ language }], captions: [{ id: 'off' }, { label: 'Italian [CC]' }] }); f.tick();
    assert.deepEqual(f.calls, { audio: [0], subtitles: [1] });
  }
});
test('script runs only in the trusted provider embed origin', () => {
  const f = fixture(undefined, { origin: 'https://other.invalid' }); f.tick();
  assert.deepEqual(f.calls, { audio: [], subtitles: [] });
});
test('polling waits for real setup before attaching listeners', () => {
  const f = fixture(undefined, { config: {} }); f.tick(); assert.deepEqual(f.events, {});
  f.model.config = { playlist: [{}] }; f.tick();
  assert.deepEqual(f.calls, { audio: [1], subtitles: [2] });
});
test('new frame documents apply their own fresh preference snapshot', () => {
  const f = fixture({ audio: 'es', subtitles: 'off' }, { audio: [{ language: 'en' }, { language: 'es' }] }); f.tick();
  assert.deepEqual(f.calls, { audio: [1], subtitles: [0] });
});

test('JW Player 8 audio-track setter and older compatible APIs are supported', () => {
  const f = fixture(undefined, { legacyAudioSetter: true }); f.tick();
  assert.deepEqual(f.calls, { audio: [1], subtitles: [2] });
});
test('an unavailable audio setter never prevents subtitle selection', () => {
  const f = fixture(undefined, { noAudioSetter: true }); f.tick();
  assert.deepEqual(f.calls, { audio: [], subtitles: [2] });
});
