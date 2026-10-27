#version 150

// Patchwork watut preview, pass A: shrink the full-size gui capture to preview size.
// Every output pixel averages the whole block of capture pixels it covers (SampleStep x SampleStep), so thin gui text
// survives the shrink. BlurLevel 1 or 2 adds a horizontal binomial blur, one output pixel per tap.

uniform sampler2D Sampler0;
uniform float SampleStep;
uniform float BlurLevel;

in vec2 texCoord0;
out vec4 fragColor;

// binomial rows (1 4 6 4 1)/16 and (1 8 28 56 70 56 28 8 1)/256, centre first
const float LIGHT[3] = float[3](0.375, 0.25, 0.0625);
const float STRONG[5] = float[5](0.2734375, 0.21875, 0.109375, 0.03125, 0.00390625);

vec4 blockAverage(vec2 centre, vec2 texel, float block) {
    int samples = int(clamp(ceil(block), 1.0, 6.0));
    vec4 total = vec4(0.0);
    for (int row = 0; row < samples; ++row) {
        for (int col = 0; col < samples; ++col) {
            vec2 cell = (vec2(float(col), float(row)) + 0.5) / float(samples) - 0.5;
            total += texture(Sampler0, centre + cell * block * texel);
        }
    }
    return total / float(samples * samples);
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    float block = max(SampleStep, 1.0);
    vec4 here = blockAverage(texCoord0, texel, block);
    if (here.a <= 0.0) {
        discard;
    }

    vec3 colour = here.rgb;
    if (BlurLevel > 0.5) {
        bool strong = BlurLevel > 1.5;
        int reach = strong ? 4 : 2;
        colour = vec3(0.0);
        for (int k = -reach; k <= reach; ++k) {
            float weight = strong ? STRONG[abs(k)] : LIGHT[abs(k)];
            vec2 shifted = texCoord0 + vec2(float(k) * block * texel.x, 0.0);
            colour += blockAverage(shifted, texel, block).rgb * weight;
        }
    }
    fragColor = vec4(colour, here.a);
}
