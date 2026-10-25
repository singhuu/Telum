// The intro's picture: the Arrow's line, Telum and the mark, on true black. The sky's stars are
// added on top by stars.vert and stars.frag.
in vec2 vP;
flat in vec4 vTel;
flat in vec4 vFx;
flat in vec4 vAB;
flat in vec2 vD;
flat in vec3 vRun;
out vec4 outColor;

uniform vec2 uRes;
uniform sampler2D uWord; // the wordmark: sharp (r), soft (g) and glow (b)
uniform vec4 uWordRect;  // left, bottom, width, height

const vec3 MINT = vec3(0.379, 0.782, 0.659); // #A4E4D3, Telum's accent, linear
const vec3 WARM = vec3(1.0, 0.55, 0.26);     // Telum's light, a red giant's, rich enough to stay warm when toned
// The mark in the icon's own colours (res/drawable/icon.xml), applied after toning, so the last
// frame matches the logo on the page underneath and is never brighter than it.
const vec3 INK = vec3(0.957, 0.965, 0.988);  // #F4F6FC
const vec3 CORE = vec3(1.0, 0.835, 0.541);   // #FFD58A

/** A soft bump that is exactly zero from [r] outward, so nothing glows past where it should. */
float bump(float d, float r) { float x = sat(1.0 - d / r); return x * x * x; }

/** A Gaussian of width [s], cut to exactly zero at 3 [s]. */
float spot(float d, float s) { return max(exp(-d * d / (2.0 * s * s)) - 0.0111, 0.0) / 0.9889; }

/** One stretch of the Arrow's line, drawn from star a towards star b as [run] goes 0 to 1. */
float stroke(vec2 p, vec2 a, vec2 b, float run, float px) {
    if (run <= 0.0) return 0.0;
    // It stops short of each star, like the lines on a star chart.
    vec2 dir = normalize(b - a);
    vec2 a1 = a + dir * 7.0 * px;
    vec2 head = mix(a1, b - dir * 7.0 * px, run);
    float h;
    float d = segment(p, a1, head, h);
    // The end being drawn is soft, and firms up once the stretch is complete.
    float tip = mix(smoothstep(0.0, 14.0 * px, (1.0 - h) * length(head - a1)), 1.0, ease(ramp(run, 0.85, 1.0)));
    return (spot(d, 0.75 * px) * 0.55 + bump(d, 5.0 * px) * 0.05) * tip;
}

/** Telum before it is a disc: a warm point that brightens on the way in, its glow inside the ring. */
vec3 telumLight(float d, float rho, float approach, float lift, float px) {
    float disc = ease(ramp(rho, 1.5 * px, 6.0 * px));
    float point = (0.9 + 0.35 * lift + 0.9 * approach) * (1.0 - disc);
    vec3 c = WARM * point * spot(d, max(rho, 1.6 * px)) * 1.6;
    float reach = mix(5.0 * px, 1.5 * uMark.z, approach);   // it ends before the dart's tip
    c += vec3(1.0, 0.62, 0.30) * (0.10 + 0.08 * lift + 0.22 * approach) * bump(d, reach);
    return c;
}

/** Signed distance to the dart, in mark units, for a point [q] in mark units. */
float dartDistance(vec2 q) {
    vec2 v[4] = vec2[4](DART_TIP, DART_WING, DART_NOTCH, vec2(-DART_WING.x, DART_WING.y));
    float d = dot(q - v[0], q - v[0]);
    float s = 1.0;
    for (int i = 0; i < 4; i++) {
        int j = i == 0 ? 3 : i - 1;
        vec2 e = v[j] - v[i];
        vec2 w = q - v[i];
        vec2 b = w - e * sat(dot(w, e) / dot(e, e));
        d = min(d, dot(b, b));
        bvec3 c = bvec3(q.y >= v[i].y, q.y < v[j].y, e.x * w.y > e.y * w.x);
        if (all(c) || all(not(c))) s = -s;
    }
    return s * sqrt(d) - DART_ROUND;
}

/** How much of the dart covers [p]. It is revealed from its tip downwards, lit by Telum above it. */
float dart(vec2 p, float t, float px) {
    if (uMark.w <= 0.0) return 0.0;
    vec2 q = (p - uMark.xy) / uMark.w;
    float shape = 1.0 - smoothstep(-px, px, dartDistance(q) * uMark.w);
    float front = mix(-10.0, -66.0, uMotion > 0.5 ? ease(ramp(t, 5.2, 6.0)) : 1.0);
    return shape * smoothstep(front - 4.0, front + 4.0, q.y);
}

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec2 p = vP;
    float t = uTime;
    float px = 1.0 / uRes.y;
    float rho = vTel.z;
    float shown = vFx.w;
    float d = length(p - vTel.xy);

    // Light, toned for the screen. Black stays exactly black.
    vec3 light = vec3(0.0);
    if (vFx.y > 0.0) {
        float line = stroke(p, vAB.xy, vD, vRun.x, px) + stroke(p, vAB.zw, vD, vRun.y, px)
                   + stroke(p, vD, vTel.xy, vRun.z, px);
        light += MINT * line * vFx.y;
    }
    light += telumLight(d, rho, vTel.w, vFx.z, px) * shown;
    vec3 col = pow(aces(light), vec3(1.0 / 2.2));

    // The mark, in the icon's colours. The disc is a little hotter at its centre while it swells.
    float disc = ease(ramp(rho, 1.5 * px, 6.0 * px)) * (1.0 - smoothstep(rho - px, rho + px, d)) * shown;
    float settle = uMotion > 0.5 ? ease(ramp(t, 5.2, 6.0)) : 1.0;
    vec3 hot = mix(vec3(1.0, 0.94, 0.82), CORE, smoothstep(0.0, 1.0, d / max(rho, px)));
    col = mix(col, mix(hot, CORE, settle), disc);
    col = mix(col, INK, dart(p, t, px) * shown);

    vec2 uv = (p - uWordRect.xy) / uWordRect.zw;
    if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
        vec3 m = texture(uWord, vec2(uv.x, 1.0 - uv.y)).rgb;
        float fade = uMotion > 0.5 ? ease(ramp(t, 5.4, 6.2)) : shown;
        col += INK * m.b * 0.035 * fade;
        col = mix(col, INK, m.r * fade);
    }

    // Dither, so faint gradients never band, but only where there is light: black gets none.
    uint gh = hash(uvec2(gl_FragCoord.xy) ^ uvec2(uint(t * 60.0) * 0x9e3779b9u));
    float n = float(gh & 0xffffu) / 65535.0 + float(gh >> 16) / 65535.0 - 1.0;
    col += n * (1.0 / 255.0) * sat(max(col.r, max(col.g, col.b)) * 255.0 - 1.0);
    float a = vFx.x;
    outColor = vec4(max(col, 0.0) * a, a);
}
