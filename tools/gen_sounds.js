// Synthesizes the CardGames sound set: 16-bit PCM mono 44.1kHz WAVs into the directory given
// (core/ui/src/main/res/raw). Deterministic — regenerating gives identical files.
//
//   node tools/gen_sounds.js core/ui/src/main/res/raw
//
// Voiced for a phone speaker, which plays almost nothing below ~300Hz: each sound's character
// lives between ~500Hz and ~6kHz (a little low body is kept for headphones). Card sounds are
// papery noise shaped by band-pass filters; the knock is a wooden rap (a few decaying modes);
// the win is a bell-like arpeggio over a warm chord with a few sparkles. Peaks are set per sound
// (PEAK below) so frequent sounds sit lower than the moments.
const fs = require("fs");
const path = require("path");

const SR = 44100;
const outDir = process.argv[2];
if (!outDir) {
  console.error("usage: node tools/gen_sounds.js <out dir>");
  process.exit(1);
}

const sec = (ms) => Math.round((ms / 1000) * SR);
const zeros = (ms) => new Float64Array(sec(ms));

function writeWav(name, samples, peakDb) {
  // Normalize to the sound's peak, with a 2ms fade-out so nothing ends on a click.
  let max = 0;
  for (const v of samples) max = Math.max(max, Math.abs(v));
  const gain = max > 0 ? Math.pow(10, peakDb / 20) / max : 0;
  const n = samples.length;
  const fade = sec(2);
  const data = Buffer.alloc(n * 2);
  for (let i = 0; i < n; i++) {
    const tail = i >= n - fade ? (n - i) / fade : 1;
    const v = Math.max(-1, Math.min(1, samples[i] * gain * tail));
    data.writeInt16LE(Math.round(v * 32767), i * 2);
  }
  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + data.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16); // PCM chunk size
  header.writeUInt16LE(1, 20); // PCM
  header.writeUInt16LE(1, 22); // mono
  header.writeUInt32LE(SR, 24);
  header.writeUInt32LE(SR * 2, 28); // byte rate
  header.writeUInt16LE(2, 32); // block align
  header.writeUInt16LE(16, 34); // bits
  header.write("data", 36);
  header.writeUInt32LE(data.length, 40);
  fs.writeFileSync(path.join(outDir, name), Buffer.concat([header, data]));
  console.log(`${name}: ${((header.length + data.length) / 1024) | 0} KB, ${((n / SR) * 1000) | 0} ms, peak ${peakDb} dBFS`);
}

// Deterministic noise; each sound reseeds so changing one never changes another.
let seed = 1;
function reseed(s) {
  seed = s;
}
function rand() {
  seed = (seed * 1103515245 + 12345) & 0x7fffffff;
  return seed / 0x40000000 - 1;
}
const noise = (n) => Float64Array.from({ length: n }, rand);

// RBJ biquad, with the centre frequency optionally changing per sample (fc as a function of t).
function biquad(input, type, fc, q) {
  const out = new Float64Array(input.length);
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < input.length; i++) {
    const f = typeof fc === "function" ? fc(i / SR) : fc;
    const w = (2 * Math.PI * f) / SR;
    const cos = Math.cos(w);
    const alpha = Math.sin(w) / (2 * q);
    let b0, b1, b2;
    if (type === "band") [b0, b1, b2] = [alpha, 0, -alpha];
    else if (type === "high") [b0, b1, b2] = [(1 + cos) / 2, -(1 + cos), (1 + cos) / 2];
    else [b0, b1, b2] = [(1 - cos) / 2, 1 - cos, (1 - cos) / 2];
    const a0 = 1 + alpha, a1 = -2 * cos, a2 = 1 - alpha;
    const x = input[i];
    const y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
    x2 = x1; x1 = x; y2 = y1; y1 = y;
    out[i] = y;
  }
  return out;
}

// Envelope: linear attack over attackMs, then exponential decay with time constant tauMs.
const env = (t, attackMs, tauMs) => Math.min(1, (t * 1000) / Math.max(attackMs, 0.01)) * Math.exp(-Math.max(0, t * 1000 - attackMs) / tauMs);

function mixAt(target, source, atMs, gain = 1) {
  const off = sec(atMs);
  for (let i = 0; i < source.length && off + i < target.length; i++) target[off + i] += source[i] * gain;
}

// A decaying sine (a mode of something struck).
function mode(ms, freq, tauMs, attackMs = 0.5) {
  const out = zeros(ms);
  for (let i = 0; i < out.length; i++) {
    const t = i / SR;
    out[i] = Math.sin(2 * Math.PI * freq * t) * env(t, attackMs, tauMs);
  }
  return out;
}

// Filtered noise under an envelope.
function shapedNoise(ms, type, fc, q, attackMs, tauMs) {
  const n = sec(ms);
  const shaped = biquad(noise(n), type, fc, q);
  for (let i = 0; i < n; i++) shaped[i] *= env(i / SR, attackMs, tauMs);
  return shaped;
}

// ---- card_place: a card landing — a soft, papery slap with some body. Three variants. ---------
// Kept low and warm (most of it 400Hz–2kHz, rolled off above ~4kHz) so it doesn't sound tinny.
[
  { s: 101, slap: 1300, body: 540, low: 210 },
  { s: 202, slap: 1450, body: 580, low: 200 },
  { s: 303, slap: 1200, body: 510, low: 220 },
].forEach(({ s, slap, body, low }, i) => {
  reseed(s);
  const out = zeros(120);
  mixAt(out, shapedNoise(120, "band", 2600, 1.0, 0.3, 3), 0, 0.18); // the edge's touch
  mixAt(out, shapedNoise(120, "band", slap, 0.8, 0.6, 16), 0, 1.0); // paper on felt
  mixAt(out, shapedNoise(120, "band", body, 1.2, 1, 24), 0, 0.95); // the card's body
  mixAt(out, mode(120, low, 20, 1), 0, 0.1); // a touch of thump (headphones)
  writeWav(`card_place_${i + 1}.wav`, biquad(out, "low", 4200, 0.7), -7);
});

// ---- card_flip: a card turning over — a quick rising "fwip", then the snap of it landing. -----
{
  reseed(404);
  const out = zeros(110);
  const n = sec(70);
  const sweep = biquad(noise(n), "band", (t) => 1200 + 4200 * (t / 0.07), 2.2);
  for (let i = 0; i < n; i++) {
    const x = i / n;
    sweep[i] *= Math.pow(Math.sin(Math.PI * Math.min(1, x * 1.15)), 1.5);
  }
  mixAt(out, sweep, 0, 0.8);
  mixAt(out, shapedNoise(50, "band", 3000, 1.0, 0.3, 6), 62, 0.9);
  mixAt(out, shapedNoise(50, "band", 900, 1.4, 0.5, 12), 62, 0.4);
  writeWav("card_flip.wav", out, -8);
}

// ---- card_deal: a card sliding off the stock — a falling "shhk" that ends in a soft landing. --
{
  reseed(505);
  const out = zeros(170);
  const n = sec(120);
  const slide = biquad(noise(n), "band", (t) => 4800 - 2900 * (t / 0.12), 1.4);
  for (let i = 0; i < n; i++) {
    const x = i / n;
    slide[i] *= Math.min(1, x * 12) * Math.pow(1 - x, 0.8);
  }
  mixAt(out, slide, 0, 0.8);
  mixAt(out, shapedNoise(60, "band", 2200, 0.9, 0.5, 10), 110, 0.7);
  mixAt(out, shapedNoise(60, "band", 800, 1.4, 1, 16), 110, 0.35);
  writeWav("card_deal.wav", out, -9);
}

// ---- shuffle: a riffle — a run of tiny card clicks speeding up, then the bridge settling. ------
{
  reseed(606);
  const out = zeros(900);
  let at = 20;
  for (let k = 0; k < 30; k++) {
    const gap = 26 - 13 * (k / 29); // 26ms apart, closing to 13ms
    const click = shapedNoise(30, "band", 2400 + 1600 * Math.abs(rand()), 1.2, 0.2, 3.5);
    mixAt(out, click, at, 0.55 + 0.45 * Math.abs(rand()));
    at += gap * (0.85 + 0.3 * Math.abs(rand()));
  }
  // The riffle's air under the clicks, swelling with them.
  const bedN = sec(at);
  const bed = biquad(noise(bedN), "band", 2800, 0.7);
  for (let i = 0; i < bedN; i++) bed[i] *= 0.18 * Math.pow(i / bedN, 1.5);
  mixAt(out, bed, 20);
  // The bridge: the halves pushed together — a soft falling swish and a landing.
  const swishN = sec(260);
  const swish = biquad(noise(swishN), "band", (t) => 3600 - 2200 * (t / 0.26), 1.0);
  for (let i = 0; i < swishN; i++) {
    const x = i / swishN;
    swish[i] *= 0.45 * Math.pow(Math.sin(Math.PI * x), 2);
  }
  mixAt(out, swish, at + 30);
  mixAt(out, shapedNoise(80, "band", 1800, 0.9, 0.5, 14), at + 250, 0.6);
  writeWav("shuffle.wav", out, -7);
}

// ---- knock: two raps of a knuckle on a wooden table. -------------------------------------------
{
  reseed(707);
  const rap = () => {
    const r = zeros(120);
    mixAt(r, shapedNoise(120, "band", 2400, 1.0, 0.2, 3), 0, 0.8); // knuckle contact
    [
      [420, 1.0, 28],
      [930, 0.55, 16],
      [1620, 0.35, 9],
      [2750, 0.18, 5],
    ].forEach(([f, g, tau]) => mixAt(r, mode(120, f, tau), 0, g)); // the wood's modes
    mixAt(r, mode(120, 150, 30, 1), 0, 0.35); // the table's thump (headphones)
    return r;
  };
  const out = zeros(320);
  mixAt(out, rap(), 0, 1.0);
  mixAt(out, rap(), 150, 0.85);
  writeWav("knock.wav", out, -5);
}

// ---- win: a bell-like C–E–G–C arpeggio over a warm C major chord, with a few sparkles. ---------
{
  reseed(808);
  const out = zeros(1900);
  const bell = (freq, ms) => {
    const b = zeros(ms);
    // Partials of a soft bell: brighter ones die away sooner.
    [
      [1, 1.0, 650],
      [2, 0.42, 380],
      [3, 0.2, 240],
      [4.16, 0.12, 150],
      [5.43, 0.06, 90],
    ].forEach(([ratio, g, tau]) => mixAt(b, mode(ms, freq * ratio, tau, 2), 0, g));
    return b;
  };
  const notes = [523.25, 659.25, 783.99, 1046.5];
  notes.forEach((f, k) => mixAt(out, bell(f, 1500), k * 105, k === 3 ? 1.0 : 0.85));
  // The chord, swelling in under the last note and fading out slowly.
  const padN = sec(1500);
  const pad = new Float64Array(padN);
  for (let i = 0; i < padN; i++) {
    const t = i / SR;
    const e = Math.min(1, t / 0.12) * Math.exp(-t / 0.55);
    let v = 0;
    for (const f of [261.63, 523.25, 659.25, 783.99]) v += Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(4 * Math.PI * f * t);
    pad[i] = (0.11 * e * v);
  }
  mixAt(out, pad, 315);
  // Sparkles: short, high glints scattered after the arpeggio.
  for (let k = 0; k < 7; k++) {
    const f = 2600 + 2600 * Math.abs(rand());
    mixAt(out, mode(200, f, 45, 1), 360 + k * 90 + 40 * rand(), 0.12 + 0.06 * Math.abs(rand()));
  }
  writeWav("win.wav", out, -4);
}

// ---- hand_won: a short flourish for winning a gin hand. It starts after a beat of silence, as it
// plays together with the knock and should follow the knock's two raps. ----------------------------
{
  reseed(909);
  const lead = 300;
  const out = zeros(lead + 1000);
  const chime = (freq, ms) => {
    const b = zeros(ms);
    [
      [1, 1.0, 420],
      [2, 0.38, 260],
      [3, 0.16, 160],
      [4.16, 0.08, 100],
    ].forEach(([ratio, g, tau]) => mixAt(b, mode(ms, freq * ratio, tau, 2), 0, g));
    return b;
  };
  mixAt(out, chime(783.99, 900), lead, 0.8); // G5
  mixAt(out, chime(1046.5, 1000), lead + 120, 1.0); // C6
  for (let k = 0; k < 4; k++) {
    const f = 3000 + 2200 * Math.abs(rand());
    mixAt(out, mode(180, f, 40, 1), lead + 170 + k * 80 + 30 * rand(), 0.1);
  }
  writeWav("hand_won.wav", out, -6);
}
