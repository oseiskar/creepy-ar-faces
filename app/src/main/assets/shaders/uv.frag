precision mediump float;

varying vec2 v_TexCoord;
uniform float u_WhichPass;
uniform sampler2D u_Texture;

void main() {
    if (u_WhichPass > 0.0) {
        float edgeness = length((v_TexCoord - 0.5) * vec2(1, 1.5)) * 2.0;
        const float lim = 0.9;

        //vec2 texCoord = (v_TexCoord - 0.5) * 0.7 + 0.5;
        //vec2 texCoord = vec2(v_TexCoord.x, -v_TexCoord.y);
        vec2 texCoord = v_TexCoord;

        vec4 color = texture2D(u_Texture, texCoord);
        if (edgeness > lim) color.a *= clamp(1.0 - (edgeness - lim) * 4.0, 0.0, 1.0);
        color.a *= clamp((v_TexCoord.y - 0.525) * 10.0, 0.0, 1.0);
        //color.a *= edgeness;
        gl_FragColor = color;
    }
    else {
        gl_FragColor.rgb = vec3(v_TexCoord, 0.0);
        gl_FragColor.a = 1.0;
    }
}
