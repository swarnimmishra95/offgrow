/* Offgrow garden engine: generative cottage garden rendered as SVG (400x400 viewBox).
   Port of the design-stage Python generator. Deterministic per seed. */
(function (root) {
  'use strict';

  // ---------------- utils ----------------
  function clamp(x, a, b) { if (a === undefined) a = 0; if (b === undefined) b = 1; return x < a ? a : (x > b ? b : x); }
  function hx(h) { h = h.replace('#', ''); return [parseInt(h.substr(0, 2), 16), parseInt(h.substr(2, 2), 16), parseInt(h.substr(4, 2), 16)]; }
  function hex2(v) { var s = Math.round(v).toString(16).toUpperCase(); return s.length < 2 ? '0' + s : s; }
  function mix(a, b, t) {
    t = clamp(t); var A = hx(a), B = hx(b);
    return '#' + hex2(A[0] + (B[0] - A[0]) * t) + hex2(A[1] + (B[1] - A[1]) * t) + hex2(A[2] + (B[2] - A[2]) * t);
  }
  function lt(c, t) { return mix(c, '#FFFFFF', t); }
  function dk(c, t) { return mix(c, '#000000', t); }
  function n(x) {
    var s = x.toFixed(1);
    if (s.slice(-2) === '.0') s = s.slice(0, -2);
    return s === '-0' ? '0' : s;
  }
  function rad(d) { return d * Math.PI / 180; }
  function deg(r) { return r * 180 / Math.PI; }
  function C(x, y, r, fill, extra) { return '<circle cx="' + n(x) + '" cy="' + n(y) + '" r="' + n(r) + '" fill="' + fill + '"' + (extra || '') + '></circle>'; }
  function E(x, y, rx, ry, fill, rot, extra) {
    var t = rot ? ' transform="rotate(' + n(rot) + ' ' + n(x) + ' ' + n(y) + ')"' : '';
    return '<ellipse cx="' + n(x) + '" cy="' + n(y) + '" rx="' + n(rx) + '" ry="' + n(ry) + '" fill="' + fill + '"' + t + (extra || '') + '></ellipse>';
  }
  function P(d, fill, stroke, sw, extra) {
    var s = stroke ? ' stroke="' + stroke + '" stroke-width="' + n(sw) + '" stroke-linecap="round" stroke-linejoin="round"' : '';
    return '<path d="' + d + '" fill="' + (fill || 'none') + '"' + s + (extra || '') + '></path>';
  }

  // ---------------- seeded random ----------------
  function hashSeed(v) {
    var str = String(v), h = 1779033703 ^ str.length;
    for (var i = 0; i < str.length; i++) { h = Math.imul(h ^ str.charCodeAt(i), 3432918353); h = (h << 13) | (h >>> 19); }
    h = Math.imul(h ^ (h >>> 16), 2246822507); h = Math.imul(h ^ (h >>> 13), 3266489909);
    return (h ^= h >>> 16) >>> 0;
  }
  function Rand(seed) { this.s = hashSeed(seed); this._g = null; }
  Rand.prototype.random = function () {
    var t = (this.s += 0x6D2B79F5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  Rand.prototype.uniform = function (a, b) { return a + (b - a) * this.random(); };
  Rand.prototype.choice = function (arr) { return arr[Math.floor(this.random() * arr.length)]; };
  Rand.prototype.randint = function (a, b) { return a + Math.floor(this.random() * (b - a + 1)); };
  Rand.prototype.gauss = function (mu, sigma) {
    var u = 1 - this.random(), v = this.random();
    return mu + sigma * Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
  };
  Rand.prototype.shuffle = function (arr) {
    for (var i = arr.length - 1; i > 0; i--) { var j = Math.floor(this.random() * (i + 1)); var t = arr[i]; arr[i] = arr[j]; arr[j] = t; }
    return arr;
  };

  // ---------------- geometry ----------------
  function walk(x, y, ang, length, segs, rr, curl, wobble, droop) {
    curl = curl || 0; wobble = (wobble === undefined) ? 6 : wobble; droop = droop || 0;
    var pts = [[x, y]], a = ang, step = length / segs;
    for (var i = 0; i < segs; i++) {
      a += rr.uniform(-wobble, wobble) + curl;
      a += droop * (i / segs) * (a > -90 ? 1 : -1) * 12;
      x += Math.cos(rad(a)) * step; y += Math.sin(rad(a)) * step;
      pts.push([x, y]);
    }
    return pts;
  }
  function tapered(pts, w0, w1) {
    var L = [], R = [], m = pts.length;
    for (var i = 0; i < m; i++) {
      var x = pts[i][0], y = pts[i][1], dx, dy;
      if (i === 0) { dx = pts[1][0] - x; dy = pts[1][1] - y; }
      else if (i === m - 1) { dx = x - pts[i - 1][0]; dy = y - pts[i - 1][1]; }
      else { dx = pts[i + 1][0] - pts[i - 1][0]; dy = pts[i + 1][1] - pts[i - 1][1]; }
      var ln = Math.hypot(dx, dy) || 1, nx = -dy / ln, ny = dx / ln;
      var w = (w0 + (w1 - w0) * i / (m - 1)) / 2;
      L.push([x + nx * w, y + ny * w]); R.push([x - nx * w, y - ny * w]);
    }
    var poly = L.concat(R.reverse());
    var d = 'M' + n(poly[0][0]) + ' ' + n(poly[0][1]);
    for (var k = 1; k < poly.length; k++) d += ' L' + n(poly[k][0]) + ' ' + n(poly[k][1]);
    return d + ' Z';
  }
  function smooth(pts) {
    var d = 'M' + n(pts[0][0]) + ' ' + n(pts[0][1]);
    for (var i = 1; i < pts.length - 1; i++) {
      var mx = (pts[i][0] + pts[i + 1][0]) / 2, my = (pts[i][1] + pts[i + 1][1]) / 2;
      d += ' Q' + n(pts[i][0]) + ' ' + n(pts[i][1]) + ' ' + n(mx) + ' ' + n(my);
    }
    var l = pts[pts.length - 1];
    return d + ' L' + n(l[0]) + ' ' + n(l[1]);
  }
  function leafHalves(x, y, ang, L, w, curve) {
    if (curve === undefined) curve = 0.12;
    var a = rad(ang), dx = Math.cos(a), dy = Math.sin(a), px = -dy, py = dx, bend = curve * L;
    var tip = [x + dx * L + px * bend, y + dy * L + py * bend];
    var mid = [x + dx * L * 0.5 + px * bend * 0.5, y + dy * L * 0.5 + py * bend * 0.5];
    var c1 = [x + dx * L * 0.35 + px * w * 1.1 + px * bend * 0.3, y + dy * L * 0.35 + py * w * 1.1 + py * bend * 0.3];
    var c1b = [x + dx * L * 0.8 + px * w * 0.7 + px * bend * 0.8, y + dy * L * 0.8 + py * w * 0.7 + py * bend * 0.8];
    var c2 = [x + dx * L * 0.35 - px * w * 1.1 + px * bend * 0.3, y + dy * L * 0.35 - py * w * 1.1 + py * bend * 0.3];
    var c2b = [x + dx * L * 0.8 - px * w * 0.7 + px * bend * 0.8, y + dy * L * 0.8 - py * w * 0.7 + py * bend * 0.8];
    var rib = 'M' + n(x) + ' ' + n(y) + ' Q' + n(mid[0]) + ' ' + n(mid[1]) + ' ' + n(tip[0]) + ' ' + n(tip[1]);
    var h1 = 'M' + n(x) + ' ' + n(y) + ' C' + n(c1[0]) + ' ' + n(c1[1]) + ' ' + n(c1b[0]) + ' ' + n(c1b[1]) + ' ' + n(tip[0]) + ' ' + n(tip[1]) + ' Q' + n(mid[0]) + ' ' + n(mid[1]) + ' ' + n(x) + ' ' + n(y) + ' Z';
    var h2 = 'M' + n(x) + ' ' + n(y) + ' C' + n(c2[0]) + ' ' + n(c2[1]) + ' ' + n(c2b[0]) + ' ' + n(c2b[1]) + ' ' + n(tip[0]) + ' ' + n(tip[1]) + ' Q' + n(mid[0]) + ' ' + n(mid[1]) + ' ' + n(x) + ' ' + n(y) + ' Z';
    return [h1, h2, rib];
  }
  function petal(x, y, ang, L, w, notch) {
    notch = notch || 0;
    var a = rad(ang), dx = Math.cos(a), dy = Math.sin(a), px = -dy, py = dx;
    var b1 = [x + dx * L * 0.3 + px * w * 0.9, y + dy * L * 0.3 + py * w * 0.9];
    var b2 = [x + dx * L * 0.95 + px * w, y + dy * L * 0.95 + py * w];
    var t1 = [x + dx * L + px * w * 0.45, y + dy * L + py * w * 0.45];
    var t0 = [x + dx * L * (1 - notch), y + dy * L * (1 - notch)];
    var t2 = [x + dx * L - px * w * 0.45, y + dy * L - py * w * 0.45];
    var b3 = [x + dx * L * 0.95 - px * w, y + dy * L * 0.95 - py * w];
    var b4 = [x + dx * L * 0.3 - px * w * 0.9, y + dy * L * 0.3 - py * w * 0.9];
    return 'M' + n(x) + ' ' + n(y) + ' C' + n(b1[0]) + ' ' + n(b1[1]) + ' ' + n(b2[0]) + ' ' + n(b2[1]) + ' ' + n(t1[0]) + ' ' + n(t1[1]) +
      ' Q' + n(t0[0]) + ' ' + n(t0[1]) + ' ' + n(t2[0]) + ' ' + n(t2[1]) + ' C' + n(b3[0]) + ' ' + n(b3[1]) + ' ' + n(b4[0]) + ' ' + n(b4[1]) + ' ' + n(x) + ' ' + n(y) + ' Z';
  }

  // ---------------- species ----------------
  var SPECIES = {
    cosmos: [['#F4A7C0', '#E86A9A', '#FBE3EC', '#D94C86'], '#F2B632', [0.55, 0.85], 'cosmos'],
    poppy: [['#E8432E', '#F06A3A', '#D9302A'], '#2B1D1A', [0.4, 0.62], 'poppy'],
    daisy: [['#FFFDF6'], '#F2B632', [0.3, 0.5], 'daisy'],
    rudbeckia: [['#F7B52A', '#F2A01E'], '#4A2A14', [0.4, 0.6], 'daisy'],
    lavender: [['#8E7BC8', '#A08ED6'], null, [0.35, 0.55], 'spike'],
    foxglove: [['#D98AB8', '#C878AC', '#F1D6E6'], null, [0.7, 0.95], 'foxglove'],
    allium: [['#A77BD0', '#9466C2'], null, [0.7, 0.9], 'allium'],
    marigold: [['#F29A1E', '#F6B52C', '#E8781A'], null, [0.25, 0.4], 'pompom'],
    sunflower: [['#F7B81E', '#F4A91A'], '#5A3418', [0.95, 1.15], 'sunflower'],
    tulip: [['#E8433E', '#F27AA0', '#F7C33C', '#FFF6EE', '#D9365C'], null, [0.32, 0.45], 'tulip'],
    cornflower: [['#4A6FD8', '#5B82E6', '#3E5CC4'], null, [0.4, 0.58], 'cornflower'],
    hydrangea: [['#8FA8F0', '#B79BE8', '#F2A8C8'], null, [0, 0], 'bush'],
    rosebush: [['#F06A8A', '#F7A8BC', '#E8434E'], null, [0, 0], 'bush']
  };
  var BASE_KINDS = ['cosmos', 'poppy', 'daisy', 'lavender', 'foxglove', 'allium', 'rudbeckia', 'marigold'];
  var DEAD = { bloom: 0, bud: 0, fading: 0.35, seedhead: 0.65, dead: 1 };
  var DROOP = { bloom: 0, bud: 0, fading: 0.6, seedhead: 1.2, dead: 2.2 };
  var NLEAF = { poppy: 5, daisy: 4, rudbeckia: 5, foxglove: 7, cosmos: 3, lavender: 6, allium: 3, marigold: 7 };
  var NSTEM = { poppy: 2, daisy: 4, rudbeckia: 3, foxglove: 1, cosmos: 3, lavender: 7, allium: 1, marigold: 5 };
  var FENCE = { white: ['#F7F4EC', '#D8D2C2'], wood: ['#B88A5C', '#8E6640'], sage: ['#B8CCB2', '#8FA88A'], blue: ['#C4D6E6', '#9AB2C8'] };
  var PATH = [[206, 412], [212, 382], [222, 352], [226, 322], [230, 296], [234, 274], [238, 256], [240, 242]];

  function range(k) { var a = []; for (var i = 0; i < k; i++) a.push(i); return a; }
  function stopsStr(st) {
    return st.map(function (s) { return '<stop offset="' + s[0] + '" stop-color="' + s[1] + '"' + (s.length > 2 ? ' stop-opacity="' + s[2] + '"' : '') + '></stop>'; }).join('');
  }

  // ======================= GARDEN =======================
  function Garden(cfg) {
    cfg = cfg || {};
    this.seed = cfg.seed === undefined ? 11 : (cfg.seed | 0);
    this.v = cfg.vitality === undefined ? 92 : cfg.vitality;
    this.uid = cfg.uid || 'g';
    this.tod = cfg.tod || 'day';
    this.age = cfg.age === undefined ? 1 : cfg.age;
    this.density = cfg.density || 1;
    var F = { sunflowers: true, arch: true, birdbath: true, pot: true, fence: 'white', tulips: true, cornflowers: true };
    var f = cfg.features || {};
    for (var k in f) if (f.hasOwnProperty(k)) F[k] = f[k];
    if (!FENCE[F.fence]) F.fence = 'white';
    this.F = F;
    var sp = (cfg.species || []).filter(function (s) { return BASE_KINDS.indexOf(s) >= 0; });
    this.species = sp.length ? sp : BASE_KINDS.slice();
    this.d = Math.pow(clamp((75 - this.v) / 65), 1.1);
    this.bloom = clamp((this.v - 20) / 65);
    this.defs = []; this.gid = 0; this.filters = {};
    this.r = new Rand(this.seed);
    this.plan();
  }
  var G = Garden.prototype;

  G.lg = function (st, x1, y1, x2, y2) {
    x1 = x1 || 0; y1 = y1 || 0; x2 = x2 || 0; y2 = (y2 === undefined) ? 1 : y2;
    this.gid++; var i = this.uid + 'g' + this.gid;
    this.defs.push('<linearGradient id="' + i + '" x1="' + x1 + '" y1="' + y1 + '" x2="' + x2 + '" y2="' + y2 + '">' + stopsStr(st) + '</linearGradient>');
    return 'url(#' + i + ')';
  };
  G.rg = function (st, o) {
    o = o || {}; this.gid++; var i = this.uid + 'g' + this.gid;
    var f = (o.fx !== undefined ? ' fx="' + o.fx + '"' : '') + (o.fy !== undefined ? ' fy="' + o.fy + '"' : '');
    this.defs.push('<radialGradient id="' + i + '" cx="' + (o.cx === undefined ? 0.5 : o.cx) + '" cy="' + (o.cy === undefined ? 0.5 : o.cy) + '" r="' + (o.r === undefined ? 0.6 : o.r) + '"' + f + '>' + stopsStr(st) + '</radialGradient>');
    return 'url(#' + i + ')';
  };
  G.blur = function (sd) {
    var i = this.uid + 'b' + String(sd).replace('.', '_');
    if (!this.filters[i]) {
      this.filters[i] = 1;
      this.defs.push('<filter id="' + i + '" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="' + sd + '"></feGaussianBlur></filter>');
    }
    return ' filter="url(#' + i + ')"';
  };
  G.wc = function (scale, freq, key) {
    freq = freq || 0.04; key = key || 'w';
    var i = this.uid + key;
    if (!this.filters[i]) {
      this.filters[i] = 1;
      this.defs.push('<filter id="' + i + '" x="-5%" y="-5%" width="110%" height="110%"><feTurbulence type="fractalNoise" baseFrequency="' + freq + '" numOctaves="3" seed="' + (this.seed % 97) + '" result="t"></feTurbulence>' +
        '<feDisplacementMap in="SourceGraphic" in2="t" scale="' + scale + '" xChannelSelector="R" yChannelSelector="G"></feDisplacementMap></filter>');
    }
    return ' filter="url(#' + i + ')"';
  };
  G.green = function (rr, depth) {
    var base = rr.choice(['#3E6E3A', '#4F8045', '#5E9150', '#6E9C56', '#35603A']);
    return mix(base, '#9FB8A0', 0.35 * (1 - depth));
  };
  G.dry = function (c, k) { return mix(c, '#A8854E', k * 0.85); };

  G.nearPath = function (x, y, pad) {
    for (var i = 0; i < PATH.length; i++) {
      var px = PATH[i][0], py = PATH[i][1], s = 0.5 + (py - 230) / 180;
      if (Math.pow(x - px, 2) / Math.pow(18 * s + pad, 2) + Math.pow(y - py, 2) / Math.pow(10 * s + pad * 0.5, 2) < 1) return true;
    }
    return false;
  };

  // --------- plan ---------
  G.plan = function () {
    var rr = this.r, self = this, sp = this.species, plants = [];
    var bands = [[175, 245, 46, 0.75], [240, 320, 44, 1.15], [315, 415, 30, 1.6]];
    bands.forEach(function (b, band) {
      var y0 = b[0], y1 = b[1], cnt = Math.floor(b[2] * self.density), scale = b[3];
      var pool = rr.shuffle(sp.slice());
      var centers = range(9).map(function (k) { return [rr.uniform(0, 400), pool[k % pool.length]]; });
      for (var i = 0; i < cnt; i++) {
        var c = centers[i % centers.length];
        var x = clamp(c[0] + rr.gauss(0, 34), -10, 410), y = rr.uniform(y0, y1);
        plants.push({ sp: c[1], x: x, y: y, depth: band / 2 + (y - y0) / (y1 - y0) * 0.25, scale: scale * rr.uniform(0.85, 1.15), h: rr.random(), seed: rr.random(), lean: rr.uniform(-12, 12) });
      }
    });
    plants.sort(function (a, b) { return a.y - b.y; });
    this.grasses = range(Math.floor(120 * this.density)).map(function () { return [rr.uniform(-10, 410), rr.uniform(170, 410), rr.random()]; });
    this.foliage = range(Math.floor(60 * this.density)).map(function () { return [rr.uniform(-20, 420), rr.uniform(318, 425), rr.random()]; });
    this.hedge = range(30).map(function () { return [rr.uniform(-30, 430), rr.uniform(150, 200), rr.uniform(18, 40), rr.random()]; });
    this.bugs = range(5).map(function () { return [rr.uniform(40, 360), rr.uniform(50, 170), rr.random()]; });

    // cottage-garden layer
    var r2 = new Rand(this.seed + 300);
    plants = plants.filter(function (p) {
      return !self.nearPath(p.x, p.y, 16) && !(p.x > 282 && p.x < 356 && p.y > 250 && p.y < 352) && !(p.x < 64 && p.y > 360);
    });
    var extra = [];
    for (var i = 0; i < (this.F.sunflowers ? 6 : 0); i++)
      extra.push({ sp: 'sunflower', x: 18 + i * 22 + r2.uniform(-6, 6), y: r2.uniform(214, 232), depth: 0.55, scale: 0.95 * r2.uniform(0.9, 1.1), h: r2.random(), seed: r2.random(), lean: r2.uniform(-8, 8) });
    [[150, 'hydrangea'], [330, 'rosebush'], [378, 'hydrangea']].forEach(function (b) {
      extra.push({ sp: b[1], x: b[0] + r2.uniform(-6, 6), y: r2.uniform(236, 246), depth: 0.6, scale: 1, h: r2.random() * 0.8, seed: r2.random(), lean: 0 });
    });
    extra.push({ sp: 'rosebush', x: 96, y: 300, depth: 0.8, scale: 1.25, h: r2.random() * 0.7, seed: r2.random(), lean: 0 });
    [[126, 360, 'tulip', 9], [318, 372, 'tulip', 8], [262, 300, 'cornflower', 5], [170, 300, 'cornflower', 6], [360, 330, 'tulip', 6]].forEach(function (dr) {
      if (dr[2] === 'tulip' && !self.F.tulips) return;
      if (dr[2] === 'cornflower' && !self.F.cornflowers) return;
      for (var i = 0; i < dr[3]; i++) {
        var x = dr[0] + r2.gauss(0, 16), y = dr[1] + r2.gauss(0, 10);
        if (self.nearPath(x, y, 4)) continue;
        if (self.F.birdbath && x > 286 && x < 352 && y > 280 && y < 384) continue;
        extra.push({ sp: dr[2], x: x, y: y, depth: clamp((y - 175) / 240 * 1.1), scale: (y < 330 ? 1.1 : 1.45) * r2.uniform(0.9, 1.1), h: r2.random(), seed: r2.random(), lean: r2.uniform(-8, 8) });
      }
    });
    plants = plants.concat(extra);
    if (this.age < 1) plants = plants.filter(function (p) { return p.seed <= self.age; });
    plants.sort(function (a, b) { return a.y - b.y; });
    this.plants = plants;
    this.foliage = this.foliage.filter(function (f) { return !self.nearPath(f[0], f[1], 18); });
    this.grasses = this.grasses.filter(function (g) { return !self.nearPath(g[0], g[1], 6); });
  };

  G.stage = function (p) {
    var h = p.h;
    if (this.v >= 60) return h < 0.2 + 0.8 * this.bloom ? 'bloom' : 'bud';
    if (h < this.bloom) return 'bloom';
    if (h < this.bloom + 0.25 + 0.2 * this.d) return 'fading';
    if (h < this.bloom + 0.55 + 0.2 * this.d) return 'seedhead';
    return 'dead';
  };

  // ================= BACKGROUND =================
  G.background = function () {
    var d = this.d, o = [];
    var top = mix('#E9EFD8', '#E9E1CE', d), mid = mix('#CFDDB0', '#DCCFA8', d), bot = mix('#8FB06E', '#B89E68', d);
    o.push('<rect width="400" height="400" fill="' + this.lg([[0, top], [0.45, mid], [1, bot]]) + '"></rect>');
    var rr = new Rand(this.seed + 50), i;
    for (i = 0; i < 26; i++) {
      var x = rr.uniform(-40, 440), y = rr.uniform(20, 200), r = rr.uniform(26, 70);
      var c = mix(rr.choice(['#9CBF7E', '#7FA868', '#B9D092', '#6E9660', '#C8D8A0']), '#C8B888', d * 0.8);
      o.push(C(x, y, r, c, ' opacity="' + n(rr.uniform(0.35, 0.7)) + '"' + this.blur(14)));
    }
    for (i = 0; i < 60; i++) {
      var x2 = rr.uniform(0, 400), y2 = rr.uniform(110, 200);
      var c2 = rr.choice(['#F4B6C8', '#FFF4E0', '#C8B6E6', '#F7C86A']);
      if (rr.random() < this.bloom) o.push(C(x2, y2, rr.uniform(4, 10), c2, ' opacity="0.55"' + this.blur(3.5)));
    }
    o.push(C(360, 20, 220, this.rg([[0, '#FFF6DA', 0.9 - 0.5 * d], [0.5, '#FFF6DA', 0.25], [1, '#FFF6DA', 0]], { r: 0.5 })));
    if (this.v >= 60) {
      for (i = 0; i < 14; i++)
        o.push(C(rr.uniform(150, 400), rr.uniform(20, 200), rr.uniform(4, 11), '#FFFBEA', ' opacity="' + n(rr.uniform(0.15, 0.35)) + '"' + this.blur(1.2)));
    }
    return o.join('');
  };
  G.hedgeLayer = function () {
    var self = this;
    var o = this.hedge.map(function (h) { return C(h[0], h[1], h[2], mix(mix('#6E9660', '#4E7A48', h[3]), '#B8A878', self.d * 0.7), ' opacity="0.8"'); });
    return '<g' + this.blur(4) + '>' + o.join('') + '</g>';
  };
  G.ground = function () {
    var d = this.d;
    return '<rect x="0" y="250" width="400" height="150" fill="' + this.lg([[0, mix('#7EA862', '#B8A070', d), 0], [0.5, mix('#5E8A4A', '#9C8458', d), 0.8], [1, mix('#3E6A36', '#7A6440', d)]]) + '"></rect>';
  };
  G.foliageLayer = function () {
    var o = [], self = this;
    this.foliage.forEach(function (f) {
      var x = f[0], y = f[1], rr = new Rand(f[2]), depth = clamp((y - 300) / 120), cnt = rr.randint(4, 7);
      for (var j = 0; j < cnt; j++) {
        var ang = -90 + rr.uniform(-80, 80); ang += self.d * (ang > -90 ? 35 : -35);
        var L = rr.uniform(14, 26) * (0.8 + depth * 0.6), w = L * rr.uniform(0.28, 0.4);
        var gc = mix(rr.choice(['#2E5A32', '#3E6E3A', '#4F8045', '#35603A']), '#FFFFFF', 0.05);
        if (rr.random() < self.d * 0.9) gc = self.dry(gc, 0.3 + 0.6 * self.d);
        var lh = leafHalves(x + rr.uniform(-6, 6), y, ang, L, w, rr.uniform(-0.25, 0.25));
        o.push(P(lh[0], lt(gc, 0.14)) + P(lh[1], dk(gc, 0.1)) + P(lh[2], 'none', dk(gc, 0.3), 0.4, ' opacity="0.6"'));
      }
    });
    return o.join('');
  };
  G.branch = function () {
    var rr = new Rand(this.seed + 70), o = [], self = this;
    var pts = walk(-10, 30, -5, 190, 12, rr, 1.6, 4);
    var bark = mix('#5A4030', '#6E5A44', this.d);
    o.push(P(tapered(pts, 7, 1.2), bark));
    var twigs = [];
    for (var i = 2; i < 12; i += 2) {
      var tw = walk(pts[i][0], pts[i][1], rr.choice([-60, 40, 70, -40]) + rr.uniform(-15, 15), rr.uniform(26, 44), 5, rr, 0, 8);
      o.push(P(tapered(tw, 2.4, 0.5), bark)); twigs.push(tw);
    }
    twigs.concat([pts.slice(5)]).forEach(function (tw) {
      tw.slice(1).forEach(function (pt) {
        var tx = pt[0], ty = pt[1];
        for (var j = 0; j < 2; j++) {
          var gc = mix(rr.choice(['#4F8045', '#6E9C56', '#3E6E3A']), '#FFFFFF', 0.05);
          if (rr.random() < self.d) gc = self.dry(gc, 0.5 + 0.5 * self.d);
          if (rr.random() < 0.3 + 0.5 * self.d * self.d) continue;
          var lh = leafHalves(tx, ty, rr.uniform(0, 360), rr.uniform(9, 14), rr.uniform(3, 4.5));
          o.push(P(lh[0], lt(gc, 0.12)) + P(lh[1], dk(gc, 0.1)));
        }
        if (rr.random() < self.bloom * 0.8) {
          for (var k = 0; k < 5; k++) {
            var a = k * 72 + rr.uniform(0, 30);
            o.push(P(petal(tx + rr.uniform(-3, 3), ty + rr.uniform(-3, 3), a, 5, 3.2), rr.choice(['#FFFFFF', '#FBE3EC', '#F7D0DC'])));
          }
          o.push(C(tx, ty, 1, '#E8A83A'));
        }
      });
    });
    return '<g' + this.blur(0.9) + '>' + o.join('') + '</g>';
  };
  G.grass = function (band) {
    var o = [], self = this;
    this.grasses.forEach(function (g) {
      var x = g[0], y = g[1], depth = (y - 170) / 240;
      if (band === 0 && depth >= 0.33) return;
      if (band === 1 && depth < 0.33) return;
      var rr = new Rand(g[2]), s = 0.5 + depth * 0.9, cnt = rr.randint(3, 6);
      for (var j = 0; j < cnt; j++) {
        var h = rr.uniform(18, 46) * s * (1 - 0.3 * self.d);
        var ang = -90 + rr.uniform(-28, 28);
        var pts = walk(x + rr.uniform(-3, 3), y, ang, h, 5, rr, rr.uniform(-5, 5) * (1 + 2 * self.d), 2);
        var c = self.green(rr, depth);
        if (rr.random() < 0.25 + 0.7 * self.d) c = self.dry(c, 0.4 + 0.6 * self.d);
        o.push(P(tapered(pts, 1.6 * s, 0.1), c, null, null, ' opacity="' + n(0.75 + 0.25 * depth) + '"'));
      }
    });
    return o.join('');
  };

  // ================= PLANTS =================
  G.fade = function (c, st, haze) {
    if (st === 'fading') c = mix(c, '#C8A88A', 0.45);
    if (st === 'seedhead' || st === 'dead') c = mix(c, '#9A7A52', 0.8);
    return mix(c, '#E8EEDF', haze * 0.7);
  };
  G.plant = function (p) {
    var sp = p.sp;
    if (sp === 'hydrangea' || sp === 'rosebush') return this.bush(p);
    if (sp === 'sunflower') return this.sunflower(p);
    if (sp === 'tulip' || sp === 'cornflower') return this.simple(p);
    var S = SPECIES[sp], cols = S[0], centre = S[1], hr = S[2], kind = S[3];
    var rr = new Rand(p.seed), st = this.stage(p), s = p.scale, depth = p.depth;
    var H = 400 * rr.uniform(hr[0], hr[1]) * 0.38 * s;
    if (st === 'bud') H *= 0.8;
    var dead = DEAD[st], droop = DROOP[st], o = [], x0 = p.x, y0 = p.y, haze = 0.35 * (1 - depth), k, lh, gc;
    for (k = 0; k < NLEAF[sp]; k++) {
      var ang = -90 + rr.uniform(-75, 75) + (k % 2 - 0.5) * 30;
      ang += dead * (ang > -90 ? 40 : -40);
      var L = rr.uniform(10, 20) * s * (sp === 'foxglove' ? 1.4 : 1);
      var w = L * ((sp === 'allium' || sp === 'lavender') ? 0.12 : 0.3);
      gc = this.green(rr, depth);
      if (rr.random() < this.d * 0.9 || dead > 0.5) gc = this.dry(gc, 0.3 + 0.7 * Math.max(dead, this.d));
      lh = leafHalves(x0, y0, ang, L, w, rr.uniform(-0.2, 0.2));
      o.push(P(lh[0], mix(gc, '#FFFFFF', 0.12 + haze)) + P(lh[1], dk(mix(gc, '#FFFFFF', haze), 0.12)) + P(lh[2], 'none', dk(gc, 0.3), 0.35 * s, ' opacity="0.6"'));
    }
    var nst = NSTEM[sp], stc = mix('#4E7A3E', '#9FB8A0', haze);
    if (dead > 0.3) stc = this.dry(stc, dead);
    for (k = 0; k < nst; k++) {
      var ang2 = -90 + p.lean + (k - (nst - 1) / 2) * (sp !== 'lavender' ? 9 : 5) + rr.uniform(-5, 5);
      var hh = H * rr.uniform(0.75, 1.0);
      var pts = walk(x0 + rr.uniform(-2, 2), y0, ang2, hh, 8, rr, 0, 3, droop);
      if (sp === 'poppy' && (st === 'bloom' || st === 'bud')) pts = walk(x0, y0, ang2, hh, 8, rr, 0, 5);
      o.push(P(tapered(pts, (sp !== 'lavender' ? 1.6 : 0.9) * s, 0.7 * s), stc));
      o.push(P(smooth(pts.map(function (q) { return [q[0] + 0.35 * s, q[1]]; })), 'none', lt(stc, 0.35), 0.35 * s, ' opacity="0.55"'));
      if (sp === 'cosmos') {
        var self = this;
        [3, 5].forEach(function (j) {
          var lx = pts[j][0], ly = pts[j][1];
          [-1, 1].forEach(function (sg) {
            for (var f = 0; f < 3; f++)
              o.push(P(leafHalves(lx, ly, -90 + sg * (40 + f * 25) + dead * sg * 60, 6 * s, 0.6 * s)[0], mix(self.green(rr, depth), '#FFFFFF', haze)));
          });
        });
      }
      if (sp === 'rudbeckia' || sp === 'daisy' || sp === 'marigold') {
        lh = leafHalves(pts[4][0], pts[4][1], -90 + rr.choice([-1, 1]) * (50 + dead * 50), 8 * s, 2.4 * s);
        gc = this.green(rr, depth);
        if (dead > 0.3) gc = this.dry(gc, dead);
        o.push(P(lh[0], mix(gc, '#FFFFFF', 0.1 + haze)) + P(lh[1], dk(gc, 0.1)));
      }
      var tx = pts[pts.length - 1][0], ty = pts[pts.length - 1][1];
      var ha = deg(Math.atan2(pts[pts.length - 1][1] - pts[pts.length - 3][1], pts[pts.length - 1][0] - pts[pts.length - 3][0]));
      o.push(this.head(kind, tx, ty, ha, s, cols, centre, st, rr, haze, depth));
    }
    return o.join('');
  };

  G.head = function (kind, x, y, ha, s, cols, centre, st, rr, haze, depth) {
    if (kind === 'sunflower' || kind === 'tulip' || kind === 'cornflower') return this.head3(kind, x, y, ha, s, cols, centre, st, rr, haze, depth);
    var o = [], col = rr.choice(cols), c = this.fade(col, st, haze);
    var tilt = rr.uniform(0.45, 0.95), rot = rr.uniform(-25, 25), k, a, self = this;
    if (st === 'bud') {
      var bc = mix(col, '#6E9C56', 0.5);
      o.push(P(petal(x, y, ha, 5 * s, 2 * s), mix(bc, '#FFFFFF', haze)));
      o.push(P(petal(x, y + 0.5, ha + 180, 2 * s, 1.8 * s), mix('#5E9150', '#FFFFFF', haze)));
      return o.join('');
    }
    if (st === 'dead') {
      if (kind === 'cosmos' || kind === 'daisy' || kind === 'poppy') o.push(C(x, y, 1.8 * s, '#6E5436'));
      return o.join('');
    }
    function disc(inner) {
      return '<g transform="translate(' + n(x) + ' ' + n(y) + ') rotate(' + n(rot) + ') scale(1 ' + n(tilt) + ') translate(' + n(-x) + ' ' + n(-y) + ')">' + inner + '</g>';
    }
    var parts = [], R;
    if (kind === 'cosmos') {
      R = 8 * s;
      var np = st === 'bloom' ? 8 : (st === 'fading' ? 5 : 0);
      for (k = 0; k < np; k++) {
        a = k * 45 + rr.uniform(-6, 6);
        var pc = mix(c, '#FFFFFF', 0.25 * rr.random());
        var pl = R * (st === 'bloom' ? 1 : 0.85);
        if (st === 'fading' && k % 2) continue;
        parts.push(P(petal(x, y, a, pl, pl * 0.36, 0.12), pc));
        parts.push(P('M' + n(x) + ' ' + n(y) + ' L' + n(x + Math.cos(rad(a)) * pl * 0.8) + ' ' + n(y + Math.sin(rad(a)) * pl * 0.8), 'none', dk(pc, 0.12), 0.3 * s, ' opacity="0.5"'));
      }
      parts.push(C(x, y, R * 0.24, st !== 'seedhead' ? this.fade(centre, st, haze) : '#6E5436'));
      for (k = 0; k < 7; k++) {
        a = k * 0.9;
        parts.push(C(x + Math.cos(a) * R * 0.14, y + Math.sin(a) * R * 0.14, 0.5 * s, dk(this.fade(centre, st, haze), 0.3)));
      }
      o.push(disc(parts.join('')));
    } else if (kind === 'daisy') {
      R = 6.5 * s * (centre === '#4A2A14' ? 1.2 : 1);
      var npd = st === 'bloom' ? 16 : (st === 'fading' ? 8 : 0);
      for (k = 0; k < npd; k++) {
        a = k * (360 / 16) + rr.uniform(-4, 4);
        var pl2 = R * rr.uniform(0.9, 1.05);
        var a2 = a + ((st === 'fading' && ((a % 360) + 360) % 360 > 0 && ((a % 360) + 360) % 360 < 180) ? 35 : 0);
        var pcol = centre !== '#4A2A14' ? mix(c, '#FFFFFF', 0.15 * rr.random()) : c;
        parts.push(P(petal(x, y, st !== 'fading' ? a2 : a + 60 * Math.sin(rad(a)), pl2, pl2 * 0.17), pcol));
      }
      var cc = st !== 'seedhead' ? this.fade(centre, st, haze) : '#6E5436';
      parts.push(C(x, y, R * 0.3, cc)); parts.push(C(x - R * 0.08, y - R * 0.1, R * 0.14, lt(cc, 0.3), ' opacity="0.6"'));
      o.push(disc(parts.join('')));
    } else if (kind === 'poppy') {
      R = 9 * s;
      if (st === 'seedhead') {
        o.push(E(x, y, 2.4 * s, 3 * s, '#8A8458')); o.push(E(x, y - 3 * s, 3 * s, s, '#6E6A44'));
      } else {
        var npet = st === 'bloom' ? 4 : 2;
        for (k = 0; k < npet; k++) {
          a = st === 'bloom' ? -150 + k * 45 + rr.uniform(-10, 10) : 60 + k * 60;
          var pcp = mix(c, '#000000', 0.08 * (k % 2));
          parts.push(P(petal(x, y, a, R, R * 0.62, 0.05), pcp));
          parts.push(P(petal(x, y, a, R * 0.9, R * 0.5, 0.05), mix(pcp, '#FFFFFF', 0.12), null, null, ' opacity="0.5"'));
        }
        parts.push(C(x, y, R * 0.18, '#1E1812'));
        for (k = 0; k < 8; k++) { a = k * 0.785; parts.push(C(x + Math.cos(a) * R * 0.26, y + Math.sin(a) * R * 0.26, 0.45 * s, '#2B1D1A')); }
        o.push('<g transform="translate(' + n(x) + ' ' + n(y) + ') scale(1 ' + n(0.7 + 0.25 * tilt) + ') translate(' + n(-x) + ' ' + n(-y) + ')">' + parts.join('') + '</g>');
      }
    } else if (kind === 'spike') {
      for (k = 0; k < 11; k++) {
        var t = k / 10;
        if (st === 'fading' && rr.random() < 0.4) continue;
        var fx = x + Math.cos(rad(ha)) * t * 12 * s + (k % 2 - 0.5) * 1.6 * s;
        var fy = y + Math.sin(rad(ha)) * t * 12 * s;
        o.push(E(fx, fy, 1.1 * s, 1.6 * s, mix(c, '#FFFFFF', 0.25 * (k % 3 === 0 ? 1 : 0)), ha + 90));
      }
    } else if (kind === 'foxglove') {
      for (k = 0; k < 12; k++) {
        var tt = k / 11;
        var bx = x - Math.cos(rad(ha)) * (1 - tt) * 34 * s, by = y - Math.sin(rad(ha)) * (1 - tt) * 34 * s;
        var side = k % 2 ? 1 : -1;
        if (st === 'fading' && k < 6) continue;
        if (tt > 0.8) { o.push(E(bx, by, 1.4 * s, 2.2 * s, mix(c, '#6E9C56', 0.5))); continue; }
        var bl = 6 * s * (1 - tt * 0.4), bxx = bx + side * 2.5 * s;
        o.push(P('M' + n(bxx) + ' ' + n(by) + ' Q' + n(bxx + side * bl * 0.6) + ' ' + n(by - s) + ' ' + n(bxx + side * bl) + ' ' + n(by + bl * 0.5) + ' L' + n(bxx + side * bl * 0.6) + ' ' + n(by + bl * 0.75) + ' Q' + n(bxx + side * bl * 0.2) + ' ' + n(by + 2 * s) + ' ' + n(bxx) + ' ' + n(by) + ' Z', c));
        o.push(E(bxx + side * bl * 0.72, by + bl * 0.45, bl * 0.18, bl * 0.28, dk(c, 0.3), side * 30));
        for (var j = 0; j < 2; j++) o.push(C(bxx + side * bl * (0.55 + j * 0.12), by + bl * 0.5, 0.35 * s, '#7A2A5A'));
      }
    } else if (kind === 'allium') {
      R = 7.5 * s * (st === 'fading' ? 0.8 : 1);
      if (st === 'seedhead') {
        for (k = 0; k < 26; k++) {
          a = rr.uniform(0, 6.283); var r2 = R * Math.sqrt(rr.random());
          o.push(P('M' + n(x) + ' ' + n(y) + ' L' + n(x + Math.cos(a) * r2) + ' ' + n(y + Math.sin(a) * r2), 'none', '#9A8458', 0.3 * s));
        }
      } else {
        o.push(C(x, y, R * 0.9, dk(c, 0.2), ' opacity="0.8"'));
        for (k = 0; k < 34; k++) {
          a = rr.uniform(0, 6.283); var r3 = R * Math.sqrt(rr.random());
          var ax = x + Math.cos(a) * r3, ay = y + Math.sin(a) * r3, lit = (ax - x) + (y - ay) > 0;
          for (var q = 0; q < 3; q++) o.push(P(petal(ax, ay, rr.uniform(0, 360), 1.6 * s, 0.6 * s), lit ? lt(c, 0.25) : c));
        }
      }
    } else if (kind === 'pompom') {
      R = 5.5 * s * (st === 'fading' ? 0.85 : 1);
      if (st === 'seedhead') o.push(C(x, y, R * 0.4, '#7A5A36'));
      else {
        o.push(C(x, y, R, dk(c, 0.25)));
        [[14, 0.78], [10, 0.52], [6, 0.26]].forEach(function (ring) {
          for (var kk = 0; kk < ring[0]; kk++) {
            var aa = kk * 6.283 / ring[0] + rr.uniform(-0.2, 0.2);
            o.push(E(x + Math.cos(aa) * R * ring[1], y + Math.sin(aa) * R * ring[1] * 0.8, R * 0.26, R * 0.2, mix(c, '#FFE6A0', 0.3 * (1 - ring[1])), deg(aa)));
          }
        });
      }
    }
    return o.join('');
  };

  G.head3 = function (kind, x, y, ha, s, cols, centre, st, rr, haze, depth) {
    var o = [], col = rr.choice(cols), c = this.fade(col, st, haze), k, a;
    if (st === 'dead') return kind === 'sunflower' ? C(x, y, 1.6 * s, '#6E5436') : '';
    if (kind === 'tulip') {
      if (st === 'bud') return P(petal(x, y + 3 * s, -90, 7 * s, 2.6 * s), mix(col, '#7FAE5A', 0.45));
      if (st === 'seedhead') return E(x, y, 1.6 * s, 3 * s, '#8A7A4A');
      var op = st === 'bloom' ? 1 : 1.8;
      o.push(P(petal(x, y + 4 * s, -90, 9 * s, 3.6 * s), dk(c, 0.18)));
      o.push(P(petal(x - 0.6 * s, y + 4 * s, -90 - 12 * op, 8.6 * s, 3.2 * s), c));
      o.push(P(petal(x + 0.6 * s, y + 4 * s, -90 + 12 * op, 8.6 * s, 3.2 * s), mix(c, '#FFFFFF', 0.12)));
      o.push(P('M' + n(x) + ' ' + n(y + 3 * s) + ' L' + n(x) + ' ' + n(y - 3 * s), 'none', lt(c, 0.35), 0.4 * s, ' opacity="0.7"'));
      return o.join('');
    }
    if (kind === 'cornflower') {
      if (st === 'bud' || st === 'seedhead') return E(x, y, 1.8 * s, 2.4 * s, st === 'bud' ? '#6E8A5A' : '#9A8A6A');
      var R = 5 * s * (st === 'fading' ? 0.8 : 1), parts = [];
      for (k = 0; k < 11; k++) {
        a = k * 32.7 + rr.uniform(-5, 5);
        if (st === 'fading' && k % 3 === 0) continue;
        var ca = Math.cos(rad(a)), sa = Math.sin(rad(a)), tx = x + ca * R, ty = y + sa * R, px = -sa, py = ca;
        parts.push(P('M' + n(x + ca * R * 0.3) + ' ' + n(y + sa * R * 0.3) + ' L' + n(tx + px * R * 0.28) + ' ' + n(ty + py * R * 0.28) + ' L' + n(tx + ca * R * 0.12) + ' ' + n(ty + sa * R * 0.12) +
          ' L' + n(tx) + ' ' + n(ty) + ' L' + n(tx + ca * R * 0.12 - px * R * 0.1) + ' ' + n(ty + sa * R * 0.12 - py * R * 0.1) + ' L' + n(tx - px * R * 0.28) + ' ' + n(ty - py * R * 0.28) + ' Z', mix(c, '#FFFFFF', 0.1 * (k % 2))));
      }
      parts.push(C(x, y, R * 0.3, dk(c, 0.35)));
      for (k = 0; k < 6; k++) parts.push(C(x + Math.cos(k) * R * 0.15, y + Math.sin(k) * R * 0.15, 0.4 * s, '#1E2A5A'));
      return '<g transform="translate(' + n(x) + ' ' + n(y) + ') scale(1 ' + n(rr.uniform(0.6, 0.95)) + ') translate(' + n(-x) + ' ' + n(-y) + ')">' + parts.join('') + '</g>';
    }
    // sunflower
    var Rs = 13 * s;
    if (st === 'bud') {
      o.push(C(x, y, Rs * 0.45, '#6E9A4E'));
      for (k = 0; k < 10; k++) o.push(P(petal(x, y, k * 36, Rs * 0.5, Rs * 0.16), '#7FAE5A'));
      return o.join('');
    }
    var ps = [], nring = st === 'seedhead' ? 0 : (st === 'bloom' ? 22 : 11);
    for (k = 0; k < nring; k++) {
      a = k * (360 / 22) + 8;
      ps.push(P(petal(x + Math.cos(rad(a)) * Rs * 0.42, y + Math.sin(rad(a)) * Rs * 0.42, a, Rs * 0.62, Rs * 0.17), dk(c, 0.14)));
    }
    for (k = 0; k < nring; k++) {
      a = k * (360 / 22);
      var am = ((a % 360) + 360) % 360;
      var pa = a + ((st === 'fading' && am > 20 && am < 160) ? 40 : 0);
      ps.push(P(petal(x + Math.cos(rad(a)) * Rs * 0.44, y + Math.sin(rad(a)) * Rs * 0.44, pa, Rs * 0.58, Rs * 0.19), mix(c, '#FFE9A0', 0.18 * (k % 2))));
    }
    var dc = st !== 'seedhead' ? '#5A3418' : '#4A3A28';
    ps.push(C(x, y, Rs * 0.48, dc)); ps.push(C(x, y, Rs * 0.3, dk(dc, 0.25)));
    for (k = 0; k < 26; k++) {
      var aa = k * 2.39996, rd = Rs * 0.44 * Math.sqrt((k + 1) / 26);
      ps.push(C(x + Math.cos(aa) * rd, y + Math.sin(aa) * rd, 0.5 * s, st !== 'seedhead' ? '#8A5A2E' : '#2A1E12'));
    }
    var droop = st === 'bloom' ? 0 : (st === 'fading' ? 35 : 70);
    o.push('<g transform="translate(' + n(x) + ' ' + n(y) + ') rotate(' + n(droop * (ha > -90 ? 1 : -1)) + ') scale(1 0.8) translate(' + n(-x) + ' ' + n(-y) + ')">' + ps.join('') + '</g>');
    return o.join('');
  };

  G.simple = function (p) {
    var sp = p.sp, S = SPECIES[sp], cols = S[0], centre = S[1], hr = S[2], kind = S[3];
    var rr = new Rand(p.seed), st = this.stage(p), s = p.scale, depth = p.depth, haze = 0.35 * (1 - depth);
    var dead = DEAD[st], droop = DROOP[st];
    var H = 400 * rr.uniform(hr[0], hr[1]) * 0.38 * s * (st === 'bud' ? 0.8 : 1);
    var o = [], x0 = p.x, y0 = p.y, self = this;
    if (sp === 'tulip') {
      [-1, 1].forEach(function (sg) {
        var gc = self.green(rr, depth);
        if (dead > 0.3 || rr.random() < self.d * 0.8) gc = self.dry(gc, 0.3 + 0.7 * Math.max(dead, self.d));
        var lh = leafHalves(x0, y0, -90 + sg * (18 + 30 * dead), 16 * s, 3.6 * s, sg * 0.2);
        o.push(P(lh[0], mix(gc, '#FFFFFF', 0.12 + haze)) + P(lh[1], dk(mix(gc, '#FFFFFF', haze), 0.1)));
      });
    }
    var stc = mix('#4E7A3E', '#9FB8A0', haze);
    if (dead > 0.3) stc = this.dry(stc, dead);
    var nst = sp === 'tulip' ? 1 : 3;
    for (var k = 0; k < nst; k++) {
      var ang = -90 + p.lean + (k - (nst - 1) / 2) * 12 + rr.uniform(-4, 4);
      var pts = walk(x0, y0, ang, H * rr.uniform(0.8, 1.0), 7, rr, 0, 3, droop);
      o.push(P(tapered(pts, 1.5 * s, 0.7 * s), stc));
      if (sp === 'cornflower')
        o.push(P(leafHalves(pts[3][0], pts[3][1], -90 + rr.choice([-1, 1]) * 40, 8 * s, s)[0], mix(this.green(rr, depth), '#FFFFFF', haze)));
      var l = pts[pts.length - 1], l3 = pts[pts.length - 3];
      o.push(this.head(kind, l[0], l[1], deg(Math.atan2(l[1] - l3[1], l[0] - l3[0])), s, cols, centre, st, rr, haze, depth));
    }
    return o.join('');
  };

  G.sunflower = function (p) {
    var rr = new Rand(p.seed), st = this.stage(p), s = p.scale, depth = p.depth, haze = 0.25 * (1 - depth);
    var dead = DEAD[st], H = 112 * s * (st === 'bud' ? 0.75 : 1) * rr.uniform(0.9, 1.1), o = [];
    var stc = mix('#4E7A3E', '#9FB8A0', haze);
    if (dead > 0.3) stc = this.dry(stc, dead);
    var pts = walk(p.x, p.y, -90 + p.lean * 0.5, H, 10, rr, 0, 2, 0.3 * dead);
    o.push(P(tapered(pts, 4.2 * s, 2.4 * s), stc));
    var self = this;
    [2, 4, 6, 8].forEach(function (j) {
      var sg = j % 4 === 0 ? 1 : -1, gc = self.green(rr, depth);
      if (dead > 0.3 || rr.random() < self.d * 0.8) gc = self.dry(gc, 0.3 + 0.7 * Math.max(dead, self.d));
      var lh = leafHalves(pts[j][0], pts[j][1], -90 + sg * (70 + 40 * dead), (16 - j) * s + 6, (6 - j * 0.3) * s + 2, sg * 0.25);
      o.push(P(lh[0], mix(gc, '#FFFFFF', 0.12 + haze)) + P(lh[1], dk(mix(gc, '#FFFFFF', haze), 0.1)) + P(lh[2], 'none', dk(gc, 0.3), 0.4, ' opacity="0.6"'));
    });
    var l = pts[pts.length - 1], l3 = pts[pts.length - 3];
    o.push(this.head('sunflower', l[0], l[1], deg(Math.atan2(l[1] - l3[1], l[0] - l3[0])), s, SPECIES.sunflower[0], null, st, rr, haze, depth));
    return o.join('');
  };

  G.bush = function (p) {
    var sp = p.sp, rr = new Rand(p.seed), st = this.stage(p), s = p.scale, x0 = p.x, y0 = p.y, o = [], dead = DEAD[st];
    var W = (sp === 'hydrangea' ? 26 : 24) * s, leaves = [], k;
    for (k = 0; k < 46; k++) {
      var a = rr.uniform(-180, 0), r = W * Math.sqrt(rr.random());
      leaves.push([y0 - 4 * s + Math.sin(rad(a)) * r * 0.9, x0 + Math.cos(rad(a)) * r * 1.1, rr.uniform(0, 360), rr.uniform(7, 11) * s * (sp === 'rosebush' ? 0.8 : 1)]);
    }
    leaves.sort(function (A, B) { return A[0] - B[0] || A[1] - B[1]; });
    for (k = 0; k < leaves.length; k++) {
      var lf = leaves[k], gc = rr.choice(['#2E5A32', '#3E6E3A', '#4F8045', '#5E9150']);
      if (dead > 0.3 || rr.random() < this.d * 0.85) gc = this.dry(gc, 0.3 + 0.7 * Math.max(dead, this.d));
      if (dead > 0.8 && rr.random() < 0.5) continue;
      var lh = leafHalves(lf[1], lf[0], lf[2], lf[3], lf[3] * 0.42, rr.uniform(-0.2, 0.2));
      o.push(P(lh[0], lt(gc, 0.14)) + P(lh[1], dk(gc, 0.08)));
    }
    var cols = SPECIES[sp][0], nf = { bloom: 7, bud: 4, fading: 4, seedhead: 2, dead: 0 }[st];
    for (k = 0; k < nf; k++) {
      var aa = rr.uniform(-165, -15), rr2 = W * rr.uniform(0.35, 0.85);
      var fx = x0 + Math.cos(rad(aa)) * rr2, fy = y0 - 6 * s + Math.sin(rad(aa)) * rr2 * 0.8, col = rr.choice(cols), R, q;
      if (st === 'fading') col = mix(col, '#C8A88A', 0.45);
      if (st === 'seedhead') col = mix(col, '#9A7A52', 0.8);
      if (sp === 'hydrangea') {
        R = 7.5 * s * (st !== 'bloom' ? 0.85 : 1);
        o.push(C(fx, fy, R, dk(col, 0.2)));
        for (var j = 0; j < 20; j++) {
          var ab = rr.uniform(0, 6.283), rj = R * Math.sqrt(rr.random()) * 0.9, cx = fx + Math.cos(ab) * rj, cy = fy + Math.sin(ab) * rj;
          var lit = ((cx - fx) - (cy - fy)) / (2 * R) + 0.5, fc = mix(dk(col, 0.1), lt(col, 0.45), lit);
          for (q = 0; q < 4; q++) o.push(P(petal(cx, cy, q * 90 + ab * 57, 1.6 * s, 0.9 * s), fc));
        }
      } else {
        if (st === 'bud') { o.push(P(petal(fx, fy + 2 * s, -90, 4 * s, 1.8 * s), col)); continue; }
        R = 4.6 * s * (st !== 'bloom' ? 0.85 : 1);
        o.push(C(fx, fy, R, dk(col, 0.22)));
        [[0.55, 0.8], [0.3, 0.6], [0.05, 0.4]].forEach(function (rp, ring) {
          for (var qq = 0; qq < 5 - ring; qq++) {
            var ang = qq * (360 / (5 - ring)) + ring * 25;
            o.push(P(petal(fx + Math.cos(rad(ang)) * R * rp[0] * 0.3, fy + Math.sin(rad(ang)) * R * rp[0] * 0.3, ang, R * rp[1], R * 0.42), mix(col, '#FFFFFF', 0.1 + ring * 0.12)));
          }
        });
        o.push(C(fx - R * 0.15, fy - R * 0.2, R * 0.2, dk(col, 0.3), ' opacity="0.6"'));
      }
    }
    return o.join('');
  };

  G.litter = function () {
    var o = [], rr = new Rand(this.seed + 60);
    var cnt = this.v < 70 ? Math.floor(90 * Math.pow(1 - this.bloom, 1.2)) : 0;
    for (var i = 0; i < cnt; i++) {
      var x = rr.uniform(0, 400), y = rr.uniform(300, 400);
      var c = rr.choice(['#E8B0C0', '#E88A6A', '#F2E8D0', '#C8A058', '#A8854E', '#B8966A']);
      o.push(P(petal(x, y, rr.uniform(0, 360), rr.uniform(3, 6), rr.uniform(1.2, 2.2)), mix(c, '#A8854E', this.d * 0.5), null, null, ' opacity="0.9"'));
    }
    return o.join('');
  };

  // ---------------- structure & props ----------------
  G.trees = function () {
    var rr = new Rand(this.seed + 400), o = [], self = this;
    [[64, 120, 70, false], [338, 118, 56, true], [210, 136, 48, false]].forEach(function (t) {
      var x = t[0], y = t[1], R = t[2], blossom = t[3], tc = mix('#6E5A48', '#8A7A62', 0.3);
      o.push(P(tapered([[x, y + R * 0.9], [x + 2, y + R * 0.4], [x - 1, y]], 9, 5), tc));
      for (var k = 0; k < 14; k++) {
        var a = rr.uniform(0, 6.283), r = R * Math.sqrt(rr.random()) * 0.8;
        var c = (blossom && self.bloom > 0.3) ? rr.choice(['#F2D6DE', '#F7E4EA', '#E8C8D2', '#B8CC9A']) : rr.choice(['#7FA868', '#6E9660', '#9CBF7E']);
        c = mix(c, '#C8B888', self.d * 0.8);
        o.push(C(x + Math.cos(a) * r, y + Math.sin(a) * r * 0.75, R * rr.uniform(0.35, 0.55), c, ' opacity="0.9"'));
      }
    });
    return '<g opacity="0.55"' + this.blur(5) + '>' + o.join('') + '</g>';
  };
  G.fenceCols = function () {
    var FC = FENCE[this.F.fence];
    return [mix(FC[0], '#DCD4C0', this.d), mix(FC[1], '#B8AE96', this.d)];
  };
  G.fence = function () {
    var o = [], rr = new Rand(this.seed + 410), d = this.d, fc = this.fenceCols(), paint = fc[0], shade = fc[1], base = 212;
    o.push('<rect x="-5" y="' + (base - 22) + '" width="410" height="3.6" fill="' + shade + '"></rect>');
    o.push('<rect x="-5" y="' + (base - 9) + '" width="410" height="3.6" fill="' + shade + '"></rect>');
    var x = -4;
    while (x < 410) {
      var tilt = rr.random() < d ? rr.uniform(-8, 8) * d : 0;
      if (d > 0.75 && rr.random() < 0.12) { x += 15; continue; }
      var pk = 'M' + n(x) + ' ' + n(base) + ' L' + n(x) + ' ' + n(base - 28) + ' L' + n(x + 3.5) + ' ' + n(base - 33) + ' L' + n(x + 7) + ' ' + n(base - 28) + ' L' + n(x + 7) + ' ' + n(base) + ' Z';
      var g = P(pk, paint) + P('M' + n(x + 4.6) + ' ' + n(base) + ' L' + n(x + 4.6) + ' ' + n(base - 29) + ' L' + n(x + 7) + ' ' + n(base - 28) + ' L' + n(x + 7) + ' ' + n(base) + ' Z', shade);
      o.push(tilt ? '<g transform="rotate(' + n(tilt) + ' ' + n(x + 3.5) + ' ' + n(base) + ')">' + g + '</g>' : g);
      x += 15;
    }
    var vine = [];
    for (var k = 0; k < 30; k++) {
      var vx = rr.uniform(150, 400), vy = base - rr.uniform(6, 30), gc = rr.choice(['#3E6E3A', '#4F8045', '#2E5A32']);
      if (rr.random() < d * 0.9) gc = this.dry(gc, 0.4 + 0.6 * d);
      vine.push(P(leafHalves(vx, vy, rr.uniform(0, 360), 6, 2.6)[0], gc));
      if (rr.random() < this.bloom * 0.6) {
        var col = rr.choice(['#F06A8A', '#F7A8BC', '#FFFFFF']);
        vine.push(C(vx + 2, vy - 1, 2.6, dk(col, 0.15)) + C(vx + 1.6, vy - 1.4, 1.8, col) + C(vx + 1.4, vy - 1.8, 0.8, lt(col, 0.4)));
      }
    }
    return '<g' + this.blur(0.5) + '>' + o.join('') + vine.join('') + '</g>';
  };
  function rose(o, fx, fy, R, col) {
    o.push(C(fx, fy, R, dk(col, 0.22)));
    [[0.55, 0.8], [0.3, 0.6], [0.05, 0.4]].forEach(function (rp, ring) {
      for (var q = 0; q < 5 - ring; q++) {
        var aa = q * (360 / (5 - ring)) + ring * 25;
        o.push(P(petal(fx + Math.cos(rad(aa)) * R * rp[0] * 0.3, fy + Math.sin(rad(aa)) * R * rp[0] * 0.3, aa, R * rp[1], R * 0.42), mix(col, '#FFFFFF', 0.1 + ring * 0.12)));
      }
    });
  }
  G.arch = function (cx, base) {
    cx = cx || 240; base = base || 216;
    var o = [], rr = new Rand(this.seed + 430), d = this.d, fc = this.fenceCols(), paint = fc[0], shade = fc[1];
    var L = cx - 30, R = cx + 30, top = base - 92, self = this;
    [L, R].forEach(function (x) {
      o.push('<rect x="' + n(x - 3.2) + '" y="' + n(top + 22) + '" width="6.4" height="' + n(base - top - 22) + '" fill="' + paint + '"></rect>');
      o.push('<rect x="' + n(x + 0.8) + '" y="' + n(top + 22) + '" width="2.4" height="' + n(base - top - 22) + '" fill="' + shade + '"></rect>');
      for (var k = 0; k < 6; k++) { var yy = top + 30 + k * 11; o.push(P('M' + n(x - 3.2) + ' ' + n(yy) + ' L' + n(x + 3.2) + ' ' + n(yy + 6), 'none', shade, 0.8)); }
    });
    o.push(P('M' + n(L - 3.2) + ' ' + n(top + 24) + ' Q' + n(cx) + ' ' + n(top - 22) + ' ' + n(R + 3.2) + ' ' + n(top + 24), 'none', paint, 6));
    o.push(P('M' + n(L + 2) + ' ' + n(top + 24) + ' Q' + n(cx) + ' ' + n(top - 12) + ' ' + n(R - 2) + ' ' + n(top + 24), 'none', shade, 1.6));
    var pts = [];
    for (var i = 0; i < 40; i++) {
      var t = i / 39, x, y;
      if (t < 0.3) { x = L + rr.uniform(-5, 5); y = base - (base - top - 22) * (t / 0.3); }
      else if (t < 0.7) {
        var u = (t - 0.3) / 0.4;
        x = Math.pow(1 - u, 2) * (L - 3) + 2 * (1 - u) * u * cx + u * u * (R + 3);
        y = Math.pow(1 - u, 2) * (top + 24) + 2 * (1 - u) * u * (top - 22) + u * u * (top + 24);
        y += rr.uniform(-4, 4);
      } else { x = R + rr.uniform(-5, 5); y = top + 22 + (base - top - 22) * ((t - 0.7) / 0.3); }
      pts.push([x, y]);
    }
    pts.forEach(function (pt) {
      for (var j = 0; j < 3; j++) {
        if (rr.random() < 0.15 + 0.5 * d * d) continue;
        var gc = rr.choice(['#2E5A32', '#3E6E3A', '#4F8045', '#5E9150']);
        if (rr.random() < d * 0.9) gc = self.dry(gc, 0.4 + 0.6 * d);
        var lh = leafHalves(pt[0] + rr.uniform(-5, 5), pt[1] + rr.uniform(-5, 5), rr.uniform(0, 360), rr.uniform(6, 9), rr.uniform(2.6, 3.6));
        o.push(P(lh[0], lt(gc, 0.14)) + P(lh[1], dk(gc, 0.08)));
      }
    });
    for (var m = 0; m < pts.length; m += 2) {
      if (rr.random() > this.bloom * 0.95) continue;
      var col = rr.choice(['#F06A8A', '#F7A8BC', '#E8434E', '#FFF0F2']);
      if (this.v < 60) col = mix(col, '#B09A8A', 0.5);
      var fx = pts[m][0] + rr.uniform(-6, 6), fy = pts[m][1] + rr.uniform(-6, 6);
      rose(o, fx, fy, rr.uniform(3.4, 4.6), col);
    }
    return '<g' + this.blur(0.4) + '>' + o.join('') + '</g>';
  };
  G.robin = function (x, y, s, flip) {
    s = s || 1; flip = flip || 1;
    function F(u) { return x + u * s * flip; }
    var o = [P('M' + n(F(-6)) + ' ' + n(y - 2 * s) + ' L' + n(F(-12)) + ' ' + n(y - s) + ' L' + n(F(-11)) + ' ' + n(y + 1.4 * s) + ' L' + n(F(-5)) + ' ' + n(y) + ' Z', '#5A4838')];
    o.push(E(x, y - 3 * s, 6.5 * s, 4.6 * s, '#8A7058'));
    o.push(E(F(1.8), y - 1.6 * s, 4.2 * s, 3.4 * s, '#E8743A'));
    o.push(E(F(-1.5), y - 3.4 * s, 4 * s, 2.4 * s, '#6E5A46', 10 * flip));
    o.push(C(F(4.5), y - 7 * s, 3.3 * s, '#8A7058'));
    o.push(E(F(5.2), y - 5.6 * s, 2.2 * s, 1.8 * s, '#E8743A'));
    o.push(C(F(5.8), y - 7.8 * s, 0.7 * s, '#1A1410'));
    o.push(P('M' + n(F(7.4)) + ' ' + n(y - 7.4 * s) + ' L' + n(F(9.6)) + ' ' + n(y - 6.9 * s) + ' L' + n(F(7.4)) + ' ' + n(y - 6.3 * s) + ' Z', '#3A3028'));
    o.push(P('M' + n(F(0)) + ' ' + n(y + s) + ' L' + n(F(-0.5)) + ' ' + n(y + 3.2 * s) + ' M' + n(F(2)) + ' ' + n(y + s) + ' L' + n(F(2.2)) + ' ' + n(y + 3.2 * s), 'none', '#7A5A40', 0.5 * s));
    return o.join('');
  };
  G.birdbath = function (x, y) {
    var o = [E(x - 3, y + 2, 20, 4, '#1E2A1A', 0, ' opacity="0.25"' + this.blur(1.5))], d = this.d;
    var stone = '#CFC8B8', dark = '#A8A090', light = '#ECE6D8';
    o.push(P('M' + n(x - 11) + ' ' + n(y) + ' L' + n(x + 11) + ' ' + n(y) + ' L' + n(x + 8) + ' ' + n(y - 5) + ' L' + n(x - 8) + ' ' + n(y - 5) + ' Z', dark));
    o.push(P('M' + n(x - 4) + ' ' + n(y - 5) + ' Q' + n(x - 6) + ' ' + n(y - 18) + ' ' + n(x - 3) + ' ' + n(y - 30) + ' L' + n(x + 3) + ' ' + n(y - 30) + ' Q' + n(x + 6) + ' ' + n(y - 18) + ' ' + n(x + 4) + ' ' + n(y - 5) + ' Z', stone));
    o.push(P('M' + n(x + 1) + ' ' + n(y - 6) + ' Q' + n(x + 3) + ' ' + n(y - 18) + ' ' + n(x + 2) + ' ' + n(y - 29), 'none', light, 1.2, ' opacity="0.7"'));
    o.push(E(x, y - 32, 22, 5.5, dark)); o.push(E(x, y - 33.5, 22, 5.5, stone));
    var lvl = 1 - 0.7 * d;
    o.push(E(x, y - 34, 18 * (0.6 + 0.4 * lvl), 3.8 * (0.6 + 0.4 * lvl), mix('#8CC8E0', '#8A8058', d)));
    if (d < 0.4) o.push(E(x - 5, y - 35, 6, 1.2, '#FFFFFF', 0, ' opacity="0.6"'));
    if (d > 0.3) {
      var rr = new Rand(5);
      for (var k = 0; k < 4; k++) o.push(P(leafHalves(x + rr.uniform(-14, 14), y - 34 + rr.uniform(-2, 2), rr.uniform(0, 360), 4, 1.6)[0], '#A8834A'));
    }
    if (this.v >= 60 && this.tod !== 'night') o.push(this.robin(x + 14, y - 37, 1, -1));
    return o.join('');
  };
  G.path = function () {
    var o = [], rr = new Rand(this.seed + 420), d = this.d, strip = [];
    PATH.forEach(function (pp) { var s = 0.5 + (pp[1] - 230) / 180; strip.push(E(pp[0], pp[1], 26 * s, 11 * s, mix('#9CC47A', '#C8B480', d))); });
    o.push('<g opacity="0.9"' + this.blur(3) + '>' + strip.join('') + '</g>');
    var self = this;
    PATH.forEach(function (pp) {
      var px = pp[0], py = pp[1], s = 0.5 + (py - 230) / 180, rx = 17 * s * rr.uniform(0.92, 1.08), ry = 6.4 * s;
      o.push(E(px - 1, py + 2 * s, rx * 1.05, ry, '#2A3A24', 0, ' opacity="0.3"' + self.blur(0.8)));
      o.push(E(px, py + 1.2 * s, rx, ry, mix('#A8A090', '#9A8E78', d)));
      o.push(E(px, py, rx, ry, mix('#D6D0C2', '#C8BCA2', d)));
      o.push(E(px - rx * 0.2, py - ry * 0.3, rx * 0.55, ry * 0.4, '#EEE9DE', 0, ' opacity="0.85"'));
      if (d < 0.5 && rr.random() < 0.7) {
        for (var q = 0; q < 3; q++)
          o.push(P(leafHalves(px + rx * rr.uniform(0.6, 1.0) * rr.choice([-1, 1]), py + ry * 0.6, -90 + rr.uniform(-50, 50), 5 * s, 1.5 * s)[0], '#6FA850'));
      }
    });
    return o.join('');
  };
  G.pot = function (x, yb) {
    var o = [E(x - 3, yb + 2, 26, 4, '#1E2A1A', 0, ' opacity="0.3"' + this.blur(1.5))];
    o.push(P('M' + n(x - 22) + ' ' + n(yb - 34) + ' L' + n(x + 22) + ' ' + n(yb - 34) + ' L' + n(x + 16) + ' ' + n(yb) + ' L' + n(x - 16) + ' ' + n(yb) + ' Z', '#C8704A'));
    o.push(P('M' + n(x + 6) + ' ' + n(yb - 34) + ' L' + n(x + 22) + ' ' + n(yb - 34) + ' L' + n(x + 16) + ' ' + n(yb) + ' L' + n(x + 4) + ' ' + n(yb) + ' Z', '#A85A38'));
    o.push('<rect x="' + n(x - 25) + '" y="' + n(yb - 40) + '" width="50" height="7" rx="2" fill="#D98458"></rect>');
    var rr = new Rand(7), k;
    for (k = 0; k < 26; k++) {
      var a = rr.uniform(-180, 20), r = rr.uniform(4, 22), gc = rr.choice(['#3E6E3A', '#4F8045']);
      var lx = x + Math.cos(rad(a)) * r, ly = yb - 40 + Math.sin(rad(a)) * r * 0.6;
      if (rr.random() < this.d) gc = this.dry(gc, 0.5 + 0.5 * this.d);
      o.push(P(leafHalves(lx, ly, rr.uniform(0, 360), 7, 3)[0], gc));
    }
    for (k = 0; k < 9; k++) {
      if (rr.random() > this.bloom + 0.1) continue;
      var fx = x + rr.uniform(-24, 24), fy = yb - 40 + rr.uniform(-14, 10), col = rr.choice(['#B8409A', '#E26AB0', '#FFFFFF']);
      if (this.v < 60) col = mix(col, '#B09A8A', 0.5);
      for (var q = 0; q < 5; q++) o.push(P(petal(fx, fy, q * 72 - 90, 4.2, 2.6), col));
      o.push(C(fx, fy, 1, '#F2E08A'));
    }
    return o.join('');
  };
  G.wateringCan = function (x, yb) {
    var body = '#6E9AA8', o = [E(x - 3, yb + 1.5, 17, 3, '#1E2A1A', 0, ' opacity="0.3"' + this.blur(1.2))];
    o.push(P('M' + n(x - 11) + ' ' + n(yb) + ' L' + n(x - 10) + ' ' + n(yb - 18) + ' L' + n(x + 10) + ' ' + n(yb - 18) + ' L' + n(x + 11) + ' ' + n(yb) + ' Z', body));
    o.push(P('M' + n(x + 3) + ' ' + n(yb) + ' L' + n(x + 3) + ' ' + n(yb - 18) + ' L' + n(x + 10) + ' ' + n(yb - 18) + ' L' + n(x + 11) + ' ' + n(yb) + ' Z', dk(body, 0.15)));
    o.push(P('M' + n(x + 10) + ' ' + n(yb - 6) + ' L' + n(x + 25) + ' ' + n(yb - 21) + ' L' + n(x + 27) + ' ' + n(yb - 19) + ' L' + n(x + 11) + ' ' + n(yb - 2) + ' Z', body));
    o.push(E(x + 26.5, yb - 20.5, 2.8, 1.4, dk(body, 0.2), -45));
    o.push(P('M' + n(x - 10) + ' ' + n(yb - 16) + ' Q' + n(x - 20) + ' ' + n(yb - 10) + ' ' + n(x - 10) + ' ' + n(yb - 3), 'none', dk(body, 0.2), 1.8));
    o.push(P('M' + n(x - 7) + ' ' + n(yb - 18) + ' Q' + n(x) + ' ' + n(yb - 29) + ' ' + n(x + 7) + ' ' + n(yb - 18), 'none', dk(body, 0.2), 1.7));
    return o.join('');
  };
  G.butterfly = function (x, y, c, rot, s) {
    s = s || 1;
    var w1 = petal(x, y, -150, 9 * s, 5 * s), w2 = petal(x, y, -30, 9 * s, 5 * s), w3 = petal(x, y, 160, 6 * s, 3.4 * s), w4 = petal(x, y, 20, 6 * s, 3.4 * s);
    var g = P(w1, c) + P(w2, c) + P(w3, dk(c, 0.2)) + P(w4, dk(c, 0.2)) + P(w1, 'none', '#2A2016', 0.5) + P(w2, 'none', '#2A2016', 0.5) +
      C(x - 5.5 * s, y - 3.4 * s, 0.9 * s, '#FFFFFF') + C(x + 5.5 * s, y - 3.4 * s, 0.9 * s, '#FFFFFF') + E(x, y, 0.7 * s, 4 * s, '#2A2016') +
      P('M' + n(x) + ' ' + n(y - 3.6 * s) + ' q-1.5 -3 -3 -3.6 M' + n(x) + ' ' + n(y - 3.6 * s) + ' q1.5 -3 3 -3.6', 'none', '#2A2016', 0.35);
    return '<g transform="rotate(' + n(rot) + ' ' + n(x) + ' ' + n(y) + ')">' + g + '</g>';
  };
  G.bee = function (x, y, s) {
    s = s || 1;
    return E(x - 1.2 * s, y - 2.8 * s, 2 * s, 3 * s, '#FFFFFF', -25, ' opacity="0.7"') + E(x + 1.2 * s, y - 2.8 * s, 2 * s, 3 * s, '#FFFFFF', 25, ' opacity="0.7"') +
      E(x, y, 3.4 * s, 2.3 * s, '#E8A81E') + E(x - 0.8 * s, y, 0.6 * s, 2.2 * s, '#2A2016') + E(x + s, y, 0.6 * s, 2 * s, '#2A2016') + C(x + 3.4 * s, y, 1.3 * s, '#2A2016');
  };
  G.critters = function () {
    var o = [];
    if (this.tod === 'night') return '';
    if (this.v >= 85 && this.age >= 0.3) {
      o.push(this.robin(206, 178, 1.1, 1));
      var x = 262, y = 150;
      o.push(P('M' + n(x - 12) + ' ' + n(y) + ' L' + n(x + 10) + ' ' + n(y), 'none', '#2E6A8A', 1.6));
      [[-70, 12], [-110, 12], [-60, 10], [-120, 10]].forEach(function (w) {
        o.push(P(petal(x + (w[0] > -90 ? 2 : -2), y, w[0], w[1], 2.4), '#DDEFF6', null, null, ' opacity="0.75"'));
      });
      o.push(C(x + 11, y, 1.8, '#2E6A8A'));
    }
    if (this.v >= 60) {
      var lx = 330, ly = 356;
      o.push(E(lx, ly, 3.2, 2.6, '#D8322A')); o.push(P('M' + n(lx) + ' ' + n(ly - 2.6) + ' L' + n(lx) + ' ' + n(ly + 2.6), 'none', '#1A1410', 0.4));
      o.push(C(lx + 3, ly, 1.4, '#1A1410'));
      [[-1.4, -1], [1.3, 0.8], [-1.2, 1.2]].forEach(function (dd) { o.push(C(lx + dd[0], ly + dd[1], 0.55, '#1A1410')); });
    }
    if (this.v < 60) {
      var sx = 226, sy = 322;
      o.push(P('M' + n(sx - 10) + ' ' + n(sy + 1) + ' Q' + n(sx) + ' ' + n(sy - 1) + ' ' + n(sx + 9) + ' ' + n(sy + 1) + ' L' + n(sx + 12) + ' ' + n(sy - 3) + ' L' + n(sx + 10) + ' ' + n(sy + 2) + ' Z', '#C8B08A'));
      o.push(C(sx, sy - 4, 5, '#A8784A')); o.push(P('M' + n(sx) + ' ' + n(sy - 4) + ' m-3 0 a3 3 0 1 1 3 3 a1.6 1.6 0 1 1 -1.6 -1.6', 'none', '#6E4A2A', 0.6));
      o.push(P('M' + n(sx + 11) + ' ' + n(sy - 3) + ' l1 -3 M' + n(sx + 11) + ' ' + n(sy - 3) + ' l2.4 -2', 'none', '#8A7050', 0.4));
    }
    return o.join('');
  };
  G.todGrade = function () {
    var t = this.tod, o = [], rr, i, x, y;
    if (t === 'dawn') {
      o.push('<rect width="400" height="400" fill="' + this.lg([[0, '#D89AC0', 0.35], [0.5, '#FFB8A8', 0.15], [1, '#6A5A8A', 0.18]]) + '"></rect>');
      o.push('<rect width="400" height="220" fill="' + this.lg([[0, '#E8B8D8', 0.4], [1, '#FFE6D6', 0]]) + '"></rect>');
      o.push('<ellipse cx="200" cy="212" rx="240" ry="16" fill="#FFFFFF" opacity="0.3"' + this.blur(6) + '></ellipse>');
      o.push('<circle cx="60" cy="150" r="140" fill="' + this.rg([[0, '#FFE0C0', 0.6], [1, '#FFE0C0', 0]], { r: 0.5 }) + '" style="mix-blend-mode: screen"></circle>');
    }
    if (t === 'dusk') {
      o.push('<rect width="400" height="400" fill="' + this.lg([[0, '#33286E', 0.66], [0.45, '#B4506E', 0.4], [1, '#1E1236', 0.62]]) + '"></rect>');
      o.push('<circle cx="345" cy="140" r="210" fill="' + this.rg([[0, '#FFC27A', 0.95], [0.35, '#FF8A4A', 0.45], [1, '#FF8A5A', 0]], { r: 0.5 }) + '" style="mix-blend-mode: screen"></circle>');
      o.push('<circle cx="345" cy="140" r="16" fill="#FFE2B0" opacity="0.9"></circle>');
      rr = new Rand(9);
      for (i = 0; i < 10; i++) { x = rr.uniform(20, 380); y = rr.uniform(200, 360); o.push(C(x, y, 5, '#FFE9A0', ' opacity="0.55" style="mix-blend-mode: screen"' + this.blur(1.8)) + C(x, y, 0.9, '#FFF8C4')); }
    }
    if (t === 'night') {
      o.push('<rect width="400" height="400" fill="#1C2A5E" style="mix-blend-mode: multiply" opacity="0.82"></rect>');
      o.push('<circle cx="330" cy="54" r="90" fill="' + this.rg([[0, '#DCE4FF', 0.55], [1, '#DCE4FF', 0]], { r: 0.5 }) + '" style="mix-blend-mode: screen"></circle>');
      o.push('<circle cx="330" cy="54" r="14" fill="#F4EEDA"></circle><circle cx="325" cy="50" r="3" fill="#DDD4BC"></circle><circle cx="336" cy="60" r="2" fill="#DDD4BC"></circle>');
      rr = new Rand(3);
      for (i = 0; i < 40; i++) o.push(C(rr.uniform(0, 400), rr.uniform(0, 140), rr.uniform(0.4, 1.0), '#FFF6DE', ' opacity="' + n(rr.uniform(0.3, 0.9)) + '"'));
      var nf = Math.floor(14 + 16 * this.bloom);
      for (i = 0; i < nf; i++) { x = rr.uniform(20, 380); y = rr.uniform(170, 380); o.push(C(x, y, 6, '#FFF1A0', ' opacity="0.55" style="mix-blend-mode: screen"' + this.blur(2)) + C(x, y, 1.1, '#FFF8C4')); }
      if (this.F.arch && this.age >= 0.75)
        o.push('<circle cx="240" cy="160" r="60" fill="' + this.rg([[0, '#FFD88A', 0.35], [1, '#FFD88A', 0]], { r: 0.5 }) + '" style="mix-blend-mode: screen"></circle>');
    }
    return o.join('');
  };

  // ================= RENDER =================
  G.render = function (label) {
    var self = this, o = [this.background(), this.trees(), this.hedgeLayer(), this.ground()];
    var back1 = [], back2 = [], mid = [], front = [];
    this.plants.forEach(function (p) {
      if (p.depth < 0.4) (p.y < 212 ? back1 : back2).push(p);
      else if (p.depth < 0.9) mid.push(p);
      else front.push(p);
    });
    function draw(list) { return list.map(function (p) { return self.plant(p); }).join(''); }
    o.push('<g' + this.blur(1.1) + '>' + this.grass(0) + draw(back1) + '</g>');
    if (this.age >= 0.3) o.push(this.fence());
    if (this.F.arch && this.age >= 0.75) o.push(this.arch());
    o.push('<g' + this.blur(0.8) + '>' + draw(back2) + '</g>');
    o.push(this.path());
    var midObjs = mid.map(function (p) { return [p.y, self.plant(p)]; });
    if (this.F.birdbath && this.age >= 0.5)
      midObjs.push([318, '<g transform="translate(318 318) scale(1.35) translate(-318 -318)">' + this.birdbath(318, 318) + '</g>']);
    midObjs.sort(function (a, b) { return a[0] - b[0]; });
    o.push('<g' + this.wc(2.2) + '>' + midObjs.map(function (m) { return m[1]; }).join('') + '</g>');
    o.push(this.litter());
    o.push('<g' + this.wc(1.6, 0.04, 'w2') + '>' + this.foliageLayer() + this.grass(1) + draw(front) + this.wateringCan(262, 400) +
      ((this.F.pot && this.age >= 0.35) ? this.pot(34, 404) : '') + '</g>');
    o.push(this.branch());
    o.push(this.critters());
    if (this.v >= 60 && this.tod !== 'night') {
      var bcol = ['#F29A1E', '#5B8DEF', '#FFFFFF', '#E8553D'];
      this.bugs.slice(0, this.v >= 85 ? 4 : 2).forEach(function (b, i) { o.push(self.butterfly(b[0], b[1], bcol[i], (b[2] - 0.5) * 50, 0.9 + b[2] * 0.4)); });
      if (this.v >= 85) { o.push(this.bee(118, 150, 1.3)); o.push(this.bee(300, 190, 1.6)); }
    }
    if (this.d > 0.05) o.push('<rect width="400" height="400" fill="#C4A878" style="mix-blend-mode: color" opacity="' + n(0.4 * this.d) + '"></rect>');
    o.push('<rect width="400" height="400" fill="' + this.lg([[0, '#FFE8B8'], [1, '#FFFFFF']], 1, 0, 0, 1) + '" style="mix-blend-mode: soft-light" opacity="0.45"></rect>');
    o.push(this.todGrade());
    o.push('<rect width="400" height="400" fill="' + this.rg([[0.55, '#1A140C', 0], [1, '#1A140C', 0.28]], { r: 0.74 }) + '"></rect>');
    var gi = this.uid + 'gr';
    this.defs.push('<filter id="' + gi + '" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" stitchTiles="stitch"></feTurbulence>' +
      '<feColorMatrix type="matrix" values="0 0 0 0 0.4 0 0 0 0 0.33 0 0 0 0 0.24 0.3 0 0 0 -0.09"></feColorMatrix></filter>');
    o.push('<rect width="400" height="400" filter="url(#' + gi + ')" style="mix-blend-mode: multiply"></rect>');
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400" width="400" height="400" preserveAspectRatio="xMidYMid slice" role="img" aria-label="' + (label || 'Garden') + '">' +
      '<defs>' + this.defs.join('') + '</defs>' + o.join('') + '</svg>';
  };

  var api = {
    render: function (cfg) { return new Garden(cfg).render(cfg && cfg.label); },
    SPECIES: Object.keys(SPECIES),
    version: 1
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.OffgrowGarden = api;
})(typeof window !== 'undefined' ? window : this);
