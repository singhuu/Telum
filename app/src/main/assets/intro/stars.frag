// One of the sky's stars, added on top of the picture: a clean round point, or a short trail while
// the camera carries it past. Its light ends exactly three widths out, so black stays black.
in vec2 vAt;
in vec3 vColor;
in float vLength;
in float vSigma;
out vec4 outColor;

void main() {
    vec2 e = vec2(vAt.x, vAt.y - clamp(vAt.y, 0.0, vLength));
    float i = max(exp(-dot(e, e) / (2.0 * vSigma * vSigma)) - 0.0111, 0.0) / 0.9889;
    outColor = vec4(vColor * i, 0.0);
}
