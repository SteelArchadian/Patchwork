#version 150

// Patchwork watut preview, pass B: optional vertical binomial blur, then fade the image out in a circle so only the
// middle of the player's screen (where the open gui sits) shows. FadeRadius < 0 turns the fade off.

uniform sampler2D Sampler0;
uniform vec2 OutputSize;
uniform float FadeRadius;
uniform float BlurLevel;

in vec2 texCoord0;
out vec4 fragColor;

const float FADE_WIDTH = 16.0;
const float LIGHT[3] = float[3](0.375, 0.25, 0.0625);
const float STRONG[5] = float[5](0.2734375, 0.21875, 0.109375, 0.03125, 0.00390625);

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec4 here = texture(Sampler0, texCoord0);

    vec3 colour = here.rgb;
    if (BlurLevel > 0.5) {
        bool strong = BlurLevel > 1.5;
        int reach = strong ? 4 : 2;
        colour = vec3(0.0);
        for (int k = -reach; k <= reach; ++k) {
            float weight = strong ? STRONG[abs(k)] : LIGHT[abs(k)];
            colour += texture(Sampler0, texCoord0 + vec2(0.0, float(k) * texel.y)).rgb * weight;
        }
    }

    float alpha = here.a;
    if (FadeRadius >= 0.0) {
        float fromCentre = distance(gl_FragCoord.xy, OutputSize * 0.5);
        alpha *= clamp(1.0 - (fromCentre - FadeRadius) / FADE_WIDTH, 0.0, 1.0);
    }
    fragColor = vec4(colour, alpha);
}
