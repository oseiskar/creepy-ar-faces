precision mediump float;

varying vec2 v_TexCoord;

void main() {
    gl_FragColor.rgb = vec3(v_TexCoord, 0.0);
    gl_FragColor.a = 1.0;
}
