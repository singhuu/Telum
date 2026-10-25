// The sky's stars, drawn instanced: four vertices each, every star read from STARS by its instance
// number. Each is a small quad round the star, stretched along the way it moved over one frame, so
// a star the camera passes never strobes.
out vec2 vAt;       // across the star's path and along it, in pixels
out vec3 vColor;
out float vLength;  // pixels
out float vSigma;   // pixels

uniform vec2 uRes;
uniform vec4 uWordRect; // left, bottom, width, height

void main() {
    int i = gl_InstanceID;
    vec4 star = STARS[i];
    float t = uTime;
    float aspect = uRes.x / uRes.y;
    float px = 1.0 / uRes.y;

    vec3 v = starFrom(star, camD(t));
    vec3 v0 = starFrom(star, camD(t - 1.0 / 60.0));
    vec2 b = project(v);
    vec2 a = v0.z > 0.0 ? project(v0) : b;
    vec2 run = b - a;
    float len = length(run);
    if (len > 0.3) { a = b - run * (0.3 / len); len = 0.3; }

    // Magnitudes squeezed into a range a TV shows well: the faintest stars stay clearly visible, so
    // a local-dimming backlight keeps them, and none outshines Telum.
    float level = sat((5.9 - star.w) / 2.4);
    float k = mix(0.6, 1.0, level);
    k *= clamp(star.z * star.z / dot(v, v), 1.0, 2.0);                // nearer is brighter, up to twice
    if (i < 3) k *= 1.0 + 0.2 * ease(ramp(t, LINE_AT[i], LINE_AT[i] + 0.5)); // the line reaches it
    k *= ease(ramp(t, 0.0, 0.8)) * (1.0 - 0.5 * ease(ramp(t, 5.2, 6.2)));
    // Stars step back from the lockup as it forms, so none sits on the mark or the wordmark.
    vec2 lo = min(uMark.xy + uMark.w * vec2(-26.0, -68.0), uWordRect.xy);
    vec2 hi = max(uMark.xy + uMark.w * vec2(26.0, 14.0), uWordRect.xy + uWordRect.zw);
    float outside = max(max(lo.x - b.x, b.x - hi.x), max(lo.y - b.y, b.y - hi.y));
    k *= mix(1.0, smoothstep(0.0, 0.06, outside), ease(ramp(t, 5.0, 6.0)));
    k *= picture(t) * uMotion;

    bool gone = v.z <= 0.0 || k < 0.003 || abs(b.x) > aspect * 0.5 + 0.02 && abs(a.x) > aspect * 0.5 + 0.02
        || abs(b.y) > 0.52 && abs(a.y) > 0.52;
    if (gone) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }

    float sigma = 1.3 + 0.5 * level;
    vec2 along = len > 1e-6 ? run / len : vec2(1.0, 0.0);
    vec2 across = vec2(-along.y, along.x);
    float cx = float(gl_VertexID & 1) * 2.0 - 1.0;
    float cy = float(gl_VertexID >> 1);
    float pad = sigma * 3.0 * px;
    vec2 pos = mix(a - along * pad, b + along * pad, cy) + across * pad * cx;
    gl_Position = vec4(pos.x / (aspect * 0.5), pos.y / 0.5, 0.0, 1.0);

    vAt = vec2(cx * sigma * 3.0, mix(-sigma * 3.0, len / px + sigma * 3.0, cy));
    vLength = len / px;
    vSigma = sigma;
    // A moving star spreads the same light along its path.
    vColor = tint(STAR_TINT[i]) * min(k, 1.0) * (2.5 * sigma / (2.5 * sigma + vLength));
}
