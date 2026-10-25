// The picture's full-screen triangle. The timeline is worked out here, once per vertex, and handed
// to every pixel unchanged.
out vec2 vP;
flat out vec4 vTel;  // Telum: x, y, disc radius; how far the approach has come (0 to 1)
flat out vec4 vFx;   // opacity, the line's fade, Telum's lift when the line arrives, the mark's fade-in
flat out vec4 vAB;   // where alpha and beta Sagittae are now
flat out vec2 vD;    // where delta Sagittae is now
flat out vec3 vRun;  // how far the line has been drawn: alpha to delta, beta to delta, delta to Telum

uniform vec2 uRes;

void main() {
    vec2 v = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(v * 2.0 - 1.0, 0.0, 1.0);
    vP = (v - 0.5) * vec2(uRes.x / uRes.y, 1.0);

    float t = uTime;
    bool full = uMotion > 0.5;
    float d = camD(t);
    vTel = vec4(uMark.xy, rhoAt(d), full ? ease(ramp(t, 2.4, 4.8)) : 1.0);
    float line = full ? 1.0 - ease(ramp(t, 2.2, 2.8)) : 0.0;
    float lift = full ? ease(ramp(t, LINE_AT.w, LINE_AT.w + 0.6)) : 0.0;
    float shown = full ? ease(ramp(t, 0.0, 0.8)) : ease(ramp(t, 0.2, 1.2));
    vFx = vec4(picture(t), line, lift, shown);
    vAB = vec4(project(starFrom(STARS[0], d)), project(starFrom(STARS[1], d)));
    vD = project(starFrom(STARS[2], d));

    // One steady stroke from the fletching to the head: the two feathers together, then the shaft.
    float feather = 0.5 * (distance(vAB.xy, vD) + distance(vAB.zw, vD));
    float split = feather / (feather + distance(vD, uMark.xy));
    float s = ease(ramp(t, LINE_AT.x, LINE_AT.w));
    vRun = vec3(sat(s / split), sat(s / split), sat((s - split) / (1.0 - split)));
}
