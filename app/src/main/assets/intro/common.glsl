// Telum intro: the timeline, the sky's stars and the helpers that every intro shader shares.
//
// Telum is the IAU name of gamma Sagittae, the star at the head of Sagitta, the Arrow. The intro
// shows the naked-eye sky around the Arrow on true black, draws the Arrow in a thin line, then
// pushes slowly towards Telum until the star is the amber core of the Telum mark.
//
// The same files run in the WebGL 2 prototype (launcher/prototype/telum-intro.html) and in the app
// (IntroView, OpenGL ES 3.0). Each shader is the version and precision lines, this file, then its
// own body.
//
// Units: screen positions are in screen heights from the centre, so y runs from -0.5 to 0.5.
// Distances are light years.

uniform float uTime;   // seconds since the first frame
uniform float uMotion; // 1: the full sequence; 0: reduced motion
uniform float uSkip;   // when the viewer skipped, or -1
uniform vec4 uMark;    // the mark: Telum's x and y, its radius (the core), and the mark's unit

const float F = 6.0;               // focal length, screen heights per radian: a 9.5 degree field
const float DEG = 0.017453293;
const float TELUM_LY = 258.4;      // Hipparcos (van Leeuwen 2007): 12.62 mas
const float TELUM_R = 4.243e-6;    // 57.7 solar radii, in light years
const float FULL_END = 7.0;        // also in IntroView and the prototype
const float CALM_END = 3.0;
const float DISSOLVE = 0.5;

// The naked-eye sky (V 5.9 and brighter) in the frame, from Hipparcos: positions from the 1997
// catalogue, distances from the 2007 reduction's parallaxes. The frame is chosen with Telum where
// the lockup puts it, on the centre line and 0.173 screen heights above the middle. x and y are
// degrees on the tangent plane at Telum, as seen from Earth, turned 138.7 degrees so the Arrow
// points up and to the right (turned, never mirrored). Each entry is x, y, light years, V
// magnitude. The Arrow comes first: alpha, beta and delta (whose line is drawn), then zeta and eta.
// Telum is drawn on its own.
const int STAR_COUNT = 30;         // also in IntroView and the prototype
const vec4 STARS[STAR_COUNT] = vec4[STAR_COUNT](
    vec4(-4.279, -1.866,  425.2, 4.39), // alpha Sge
    vec4(-4.478, -1.315,  439.6, 4.39), // beta Sge
    vec4(-2.644, -1.076,  594.1, 3.68), // delta Sge
    vec4(-1.956, -1.274,  255.0, 5.01), // zeta Sge
    vec4(+1.464, +0.613,  160.4, 5.09), // eta Sge
    vec4(+2.127, -4.258,  334.5, 4.57), // HIP 97886
    vec4(+5.885, -5.905,  236.3, 4.66), // HIP 98543
    vec4(+6.896, -2.170,  991.4, 4.79), // HIP 99824
    vec4(+0.732, -3.527,  629.6, 4.90), // HIP 97679
    vec4(-4.033, -4.053,  552.8, 5.00), // HIP 96275
    vec4(+4.138, -1.879,  504.1, 5.08), // HIP 99080
    vec4(-5.586, -5.573,  271.6, 5.14), // HIP 95498
    vec4(+5.588, -0.521, 1918.6, 5.18), // HIP 99853
    vec4(+4.165, -3.614,  221.7, 5.23), // HIP 98636
    vec4(+6.567, -1.234,  437.8, 5.30), // HIP 99951
    vec4(-1.071, +1.689, 1019.2, 5.33), // HIP 98438
    vec4(+7.362, -0.293,  997.4, 5.50), // HIP 100435
    vec4(+6.935, -3.871,  423.6, 5.51), // HIP 99404
    vec4(+7.087, -3.617, 1411.9, 5.51), // HIP 99518
    vec4(+2.496, -5.163, 1689.9, 5.54), // HIP 97765
    vec4(-1.965, +1.874,  455.5, 5.54), // HIP 98234
    vec4(+2.468, -4.278,  470.6, 5.56), // HIP 97961
    vec4(-5.247, -5.666,  237.7, 5.60), // HIP 95560
    vec4(-5.838, -1.184,  480.3, 5.67), // HIP 96516
    vec4(+2.458, -2.651,  160.2, 5.68), // HIP 98375
    vec4(+6.083, +2.610,  475.4, 5.68), // HIP 100754
    vec4(-2.380, +1.714, 3294.5, 5.71), // HIP 98085
    vec4(-0.634, +2.661,   58.0, 5.80), // HIP 98819
    vec4(-5.348, -5.474,  613.1, 5.84), // HIP 95582
    vec4(+4.027, -3.551, 1080.0, 5.88)  // HIP 98609
);
// Each star's colour for tint(), from its B-V index.
const float STAR_TINT[STAR_COUNT] = float[STAR_COUNT](
    0.54, 0.69, 0.84, 0.16, 0.70, 0.09, 0.21, 0.01, 0.03, 0.06, 0.02, 0.67, 0.68, 0.32,
    0.64, 0.99, 0.06, 0.16, 0.89, 0.49, 0.08, 0.10, 0.11, 0.67, 0.30, 0.62, 0.61, 0.44,
    0.98, 0.04);

// The mark, as in res/drawable/icon.xml: Telum (radius 6.5) above a slim notched dart, a
// spearhead. Mark units are the icon's 96-unit grid, with Telum at the origin and y up; one unit
// is uMark.w on screen.
const vec2 DART_TIP = vec2(0.0, -17.0);
const vec2 DART_WING = vec2(13.0, -60.0);  // the right wing; the left mirrors it
const vec2 DART_NOTCH = vec2(0.0, -49.0);
const float DART_ROUND = 1.5;              // the icon's 3-unit round-joined outline

// ------------------------------------------------------------------ easing

float sat(float x) { return clamp(x, 0.0, 1.0); }
float ramp(float t, float a, float b) { return sat((t - a) / (b - a)); }
float ease(float x) { x = sat(x); return x * x * (3.0 - 2.0 * x); }
float ease5(float x) { x = sat(x); return x * x * x * (x * (x * 6.0 - 15.0) + 10.0); }

// ------------------------------------------------------------------ timeline

float endTime() { return uMotion > 0.5 ? FULL_END : CALM_END; }

/** Telum's radius on screen with the camera [d] light years away, and the reverse. */
float rhoAt(float d) { float s = TELUM_R / d; return F * s / sqrt(max(1.0 - s * s, 1e-12)); }
float distanceFor(float rho) { return TELUM_R / sin(atan(rho / F)); }

/**
 * The camera's distance to Telum. One slow push, in three overlapping parts that read as one
 * glide: the nearer stars drift out of the frame while the camera covers the real distance, it
 * then crosses the last few light years quickly while nothing on screen changes, and finally
 * Telum swells into the mark's core and settles.
 */
float camD(float t) {
    float d1 = distanceFor(uMark.z);
    if (uMotion < 0.5) return d1;
    float across = TELUM_LY - (TELUM_LY - 6.0) * ease(ramp(t, 2.2, 4.0));
    float l = log(across) - log(6.0 / 0.025) * ease(ramp(t, 3.7, 4.4)) - log(0.025 / d1) * ease(ramp(t, 4.0, 5.2));
    return exp(l);
}

/** A star's place relative to the camera, which is [d] light years short of Telum. */
vec3 starFrom(vec4 star, float d) {
    vec3 s = star.z * normalize(vec3(star.xy * DEG, 1.0));
    return s - vec3(0.0, 0.0, TELUM_LY - d);
}

/** Where a point relative to the camera lands on screen. Telum stays where the mark will be. */
vec2 project(vec3 v) { return uMark.xy + F * v.xy / max(v.z, 1e-4); }

/**
 * The line leaves alpha and beta together, reaches delta at about 1.4 s and Telum at 2.2 s. Each
 * star brightens a little as the line reaches it.
 */
const vec4 LINE_AT = vec4(0.8, 0.8, 1.4, 2.2);

/** The picture's opacity. It dissolves into what is underneath at the end, or on a skip. */
float picture(float t) {
    float a = 1.0 - ease(ramp(t, endTime() - DISSOLVE, endTime()));
    if (uSkip >= 0.0) a *= 1.0 - ease(ramp(t, uSkip, uSkip + 0.35));
    return a;
}

// ------------------------------------------------------------------ helpers

uint hash(uint x) {
    x ^= x >> 16; x *= 0x7feb352du;
    x ^= x >> 15; x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}
uint hash(uvec2 v) { return hash(v.x ^ hash(v.y + 0x9e3779b9u)); }

/** A star's colour, blue-white to orange, for k from 0 to 1. Kept soft: these are points of light. */
vec3 tint(float k) {
    vec3 c = mix(vec3(0.72, 0.80, 1.0), vec3(1.0), smoothstep(0.0, 0.35, k));
    c = mix(c, vec3(1.0, 0.90, 0.74), smoothstep(0.5, 0.8, k));
    return mix(c, vec3(1.0, 0.74, 0.50), smoothstep(0.85, 1.0, k));
}

float segment(vec2 p, vec2 a, vec2 b, out float h) {
    vec2 pa = p - a, ba = b - a;
    h = sat(dot(pa, ba) / max(dot(ba, ba), 1e-12));
    return length(pa - ba * h);
}
