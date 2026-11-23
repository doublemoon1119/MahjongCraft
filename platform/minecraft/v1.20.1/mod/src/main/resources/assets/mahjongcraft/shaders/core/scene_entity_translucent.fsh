#version 150

// Same as the vanilla translucent entity fragment shader, except that a pixel is dropped by the transparency of
// the texture itself rather than the final transparency, so a faded figure keeps every pixel its opaque version
// shows.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec4 normal;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.1) {
        discard;
    }
    vec4 color = texel * vertexColor * ColorModulator;
    float fogValue = vertexDistance <= FogStart ? 0.0 : (vertexDistance < FogEnd ? smoothstep(FogStart, FogEnd, vertexDistance) : 1.0);
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fogValue * FogColor.a), color.a);
}
