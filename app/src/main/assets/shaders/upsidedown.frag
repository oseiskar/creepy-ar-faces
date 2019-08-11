precision mediump float;

varying vec2 v_TexCoord;
uniform sampler2D u_Texture;

void main() {
    float edgeness = length((v_TexCoord - 0.5) * vec2(1, 1.5)) * 2.0;
    const float lim = 0.9;
    vec2 texCoord = vec2(v_TexCoord.x, -v_TexCoord.y);
    vec4 color = texture2D(u_Texture, texCoord);
    if (edgeness > lim) color.a *= clamp(1.0 - (edgeness - lim) * 4.0, 0.0, 1.0);
    gl_FragColor = color;
}
