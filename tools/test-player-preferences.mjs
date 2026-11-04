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
    config: options.config || { playlist: [{ file: 'https://media.invalid/master.m3u8' }] },
    state: options.state || 'playing', audioIndex: 0, captionIndex: 0, ignoreAudio: !!options.ignoreAudio, ignoreCaptions: !!options.ignoreCaptions
  };
  const player = {
    getConfig: () => model.config, getAudioTracks: () => model.audio, getCaptionsList: () => model.captions,
    getState: () => model.state, getCurrentAudioTrack: () => options.objectGetter ? model.audio[model.audioIndex] : model.audioIndex, getCurrentCaptions: () => model.captionIndex,
    setCurrentAudioTrack: index => { calls.audio.push(index); if (!model.ignoreAudio) model.audioIndex = index; },
    setCurrentCaptions: index => { calls.subtitles.push(index); if (!model.ignoreCaptions) model.captionIndex = index; },
    on: (event, listener) => { (events[event] ||= []).push(listener); return player; }
  };
  if (options.legacyAudioSetter) { player.setCurrentAudio = player.setCurrentAudioTrack; delete player.setCurrentAudioTrack; }
  if (options.noAudioSetter) delete player.setCurrentAudioTrack;
  let tick, cleared = false, now = 1000;
  const location = { origin: options.origin || 'https://vixcloud.cc', pathname: '/embed/402042' };
  const context = { Date: { now: () => now }, location, document: { getElementById: () => ({}) },
    window: { jwplayer: () => player, addEventListener() {} },
    setInterval: fn => { tick = fn; return 1; }, clearInterval: () => { cleared = true; } };
  runInNewContext(source.replace('__OZOZUZ_PREFERENCES__', JSON.stringify(preference)), context);
  return { model, calls, events, tick: () => { if (!cleared && tick) tick(); }, advance: ms => { now += ms; }, fire: event => { if (event === 'firstFrame') model.state = 'playing'; (events[event] || []).forEach(fn => fn()); } };
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
  const f = fixture({ audio: 'default', subtitles: 'off' }); f.model.captionIndex = 2; f.tick();
  assert.deepEqual(f.calls, { audio: [], subtitles: [0] });
});
test('provider defaults are left untouched', () => {
  const f = fixture({ audio: 'default', subtitles: 'default' }); f.tick();
  assert.deepEqual(f.calls, { audio: [], subtitles: [] });
});
test('region codes, ISO 639-2 and language labels are recognized', () => {
  for (const language of ['en-US', 'eng']) {
    const f = fixture(undefined, { audio: [{ language }], captions: [{ id: 'off' }, { label: 'Italian [CC]' }] }); f.tick();
    assert.deepEqual(f.calls, { audio: [], subtitles: [1] });
    assert.equal(f.model.audioIndex, 0);
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
  const f = fixture({ audio: 'es', subtitles: 'off' }, { audio: [{ language: 'en' }, { language: 'es' }] }); f.model.captionIndex = 1; f.tick();
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

test('idle setup never consumes the defaults before the first frame', () => {
  const f = fixture(undefined, { state: 'idle' }); f.tick();
  assert.deepEqual(f.calls, { audio: [], subtitles: [] });
  f.fire('audioTracks'); f.fire('captionsList');
  assert.deepEqual(f.calls, { audio: [], subtitles: [] });
  f.fire('firstFrame');
  assert.deepEqual(f.calls, { audio: [1], subtitles: [2] });
});
test('a silently ignored setter is retried and confirmed after provider initialization', () => {
  const f = fixture(undefined, { ignoreAudio: true, ignoreCaptions: true }); f.tick();
  assert.equal(f.model.audioIndex, 0); assert.equal(f.model.captionIndex, 0);
  f.model.ignoreAudio = false; f.model.ignoreCaptions = false;
  f.advance(1100); f.tick();
  assert.equal(f.model.audioIndex, 1); assert.equal(f.model.captionIndex, 2);
  assert.deepEqual(f.calls, { audio: [1, 1], subtitles: [2, 2] });
  f.tick(); assert.deepEqual(f.calls, { audio: [1, 1], subtitles: [2, 2] });
});
test('an asynchronous track-change confirmation completes without repeating the setter', () => {
  const f = fixture(undefined, { ignoreAudio: true }); f.tick();
  f.model.audioIndex = 1; f.fire('audioTrackChanged');
  f.advance(2000); f.tick();
  assert.deepEqual(f.calls.audio, [1]);
});
test('confirmed defaults allow later manual audio and caption choices', () => {
  const f = fixture(); f.tick();
  f.model.audioIndex = 0; f.model.captionIndex = 0;
  f.fire('audioTrackChanged'); f.fire('captionsChanged'); f.fire('firstFrame');
  assert.equal(f.model.audioIndex, 0); assert.equal(f.model.captionIndex, 0);
  assert.deepEqual(f.calls, { audio: [1], subtitles: [2] });
});
test('SDKs returning an audio-track object are confirmed by language', () => {
  const f = fixture(undefined, { objectGetter: true }); f.tick();
  assert.equal(f.model.audioIndex, 1); assert.deepEqual(f.calls.audio, [1]);
});
test('failed initialization retries are bounded and do not force a choice later', () => {
  const f = fixture(undefined, { ignoreAudio: true }); f.tick();
  f.advance(11000); f.tick(); f.model.ignoreAudio = false; f.fire('audioTracks');
  assert.equal(f.model.audioIndex, 0); assert.deepEqual(f.calls.audio, [1]);
});
