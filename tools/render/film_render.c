// Software renderer for the RetroCam film shader: Mesa (llvmpipe) GLES 3.0 via EGL surfaceless/pbuffer.
// Mirrors OffscreenFilmRenderer: scene in a 2D texture (row 0 = image top), 33^3 RGB8 3D LUT, topDown=1,
// identity texture matrix, output rendered in TILE x TILE pieces with uQuadRect selecting the piece.
//
// usage: film_render vert frag input.ppm lut.raw uniforms.txt out.ppm [tile]
//   lut.raw      : 33*33*33*3 bytes, index ((b*33+g)*33+r)*3
//   uniforms.txt : lines "name v1 [v2 v3 v4]"; the name uSeed is an int uniform.
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *slurp(const char *path, long *len) {
    FILE *f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "cannot open %s\n", path); exit(2); }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    char *b = malloc(n + 1);
    if (fread(b, 1, n, f) != (size_t)n) exit(2);
    b[n] = 0; fclose(f); if (len) *len = n; return b;
}

static GLuint compile(GLenum type, const char *path) {
    char *src = slurp(path, NULL);
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, (const char **)&src, NULL);
    glCompileShader(s);
    GLint ok; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[8192]; glGetShaderInfoLog(s, sizeof log, NULL, log); fprintf(stderr, "compile %s failed:\n%s\n", path, log); exit(3); }
    free(src); return s;
}

static unsigned char *read_ppm(const char *path, int *w, int *h) {
    FILE *f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "cannot open %s\n", path); exit(2); }
    int maxv;
    if (fscanf(f, "P6 %d %d %d", w, h, &maxv) != 3) { fprintf(stderr, "bad ppm\n"); exit(2); }
    fgetc(f);
    size_t n = (size_t)*w * *h * 3;
    unsigned char *d = malloc(n);
    if (fread(d, 1, n, f) != n) { fprintf(stderr, "short ppm\n"); exit(2); }
    fclose(f); return d;
}

int main(int argc, char **argv) {
    if (argc < 7) { fprintf(stderr, "usage: film_render vert frag in.ppm lut.raw uniforms.txt out.ppm [tile]\n"); return 1; }
    int TILE = argc > 7 ? atoi(argv[7]) : 1024;

    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint maj, min;
    if (!eglInitialize(dpy, &maj, &min)) { fprintf(stderr, "eglInitialize failed\n"); return 4; }
    eglBindAPI(EGL_OPENGL_ES_API);
    EGLint cfgAttr[] = { EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                         EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE };
    EGLConfig cfg; EGLint ncfg;
    if (!eglChooseConfig(dpy, cfgAttr, &cfg, 1, &ncfg) || ncfg < 1) { fprintf(stderr, "no config\n"); return 4; }
    EGLint pbAttr[] = { EGL_WIDTH, TILE, EGL_HEIGHT, TILE, EGL_NONE };
    EGLSurface surf = eglCreatePbufferSurface(dpy, cfg, pbAttr);
    EGLint ctxAttr[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttr);
    if (surf == EGL_NO_SURFACE || ctx == EGL_NO_CONTEXT || !eglMakeCurrent(dpy, surf, surf, ctx)) { fprintf(stderr, "context failed 0x%x\n", eglGetError()); return 4; }
    if (getenv("FILM_VERBOSE")) fprintf(stderr, "GL: %s | %s\n", glGetString(GL_RENDERER), glGetString(GL_VERSION));

    GLuint vs = compile(GL_VERTEX_SHADER, argv[1]), fs = compile(GL_FRAGMENT_SHADER, argv[2]);
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs); glAttachShader(prog, fs); glLinkProgram(prog);
    GLint ok; glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) { char log[8192]; glGetProgramInfoLog(prog, sizeof log, NULL, log); fprintf(stderr, "link failed:\n%s\n", log); return 3; }
    glUseProgram(prog);

    int w, h; unsigned char *img = read_ppm(argv[3], &w, &h);
    GLuint scene; glGenTextures(1, &scene);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, scene);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, w, h, 0, GL_RGB, GL_UNSIGNED_BYTE, img);

    long lutLen; unsigned char *lut = (unsigned char *)slurp(argv[4], &lutLen);
    GLuint lt; glGenTextures(1, &lt);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_3D, lt);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
    glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB8, 33, 33, 33, 0, GL_RGB, GL_UNSIGNED_BYTE, lut);
    glActiveTexture(GL_TEXTURE0);

    glUniform1i(glGetUniformLocation(prog, "uScene"), 0);
    glUniform1i(glGetUniformLocation(prog, "uLut"), 1);
    float ident[16] = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    glUniformMatrix4fv(glGetUniformLocation(prog, "uTexMat"), 1, GL_FALSE, ident);
    glUniform1f(glGetUniformLocation(prog, "uTopDown"), 1.0f);

    FILE *uf = fopen(argv[5], "r");
    if (!uf) { fprintf(stderr, "cannot open %s\n", argv[5]); return 2; }
    char line[1024];
    while (fgets(line, sizeof line, uf)) {
        char name[128]; float v[4] = {0,0,0,0};
        int n = sscanf(line, "%127s %f %f %f %f", name, &v[0], &v[1], &v[2], &v[3]) - 1;
        if (n < 1) continue;
        GLint loc = glGetUniformLocation(prog, name);
        if (loc < 0) { if (getenv("FILM_VERBOSE")) fprintf(stderr, "uniform %s inactive\n", name); continue; }
        if (!strcmp(name, "uSeed")) glUniform1i(loc, (int)v[0]);
        else if (n == 1) glUniform1f(loc, v[0]);
        else if (n == 2) glUniform2f(loc, v[0], v[1]);
        else if (n == 3) glUniform3f(loc, v[0], v[1], v[2]);
        else glUniform4f(loc, v[0], v[1], v[2], v[3]);
    }
    fclose(uf);

    GLint quadLoc = glGetUniformLocation(prog, "uQuadRect");

    GLuint fboTex, fbo;
    glGenTextures(1, &fboTex); glBindTexture(GL_TEXTURE_2D, fboTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, TILE, TILE, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fboTex, 0);
    glBindTexture(GL_TEXTURE_2D, scene);

    float quad[8] = {0,0, 1,0, 0,1, 1,1};
    GLuint vao, vbo; glGenVertexArrays(1, &vao); glBindVertexArray(vao);
    glGenBuffers(1, &vbo); glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof quad, quad, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);

    unsigned char *out = malloc((size_t)w * h * 3);
    unsigned char *tile = malloc((size_t)TILE * TILE * 4);
    for (int y0 = 0; y0 < h; y0 += TILE) {
        int th = h - y0 < TILE ? h - y0 : TILE;
        for (int x0 = 0; x0 < w; x0 += TILE) {
            int tw = w - x0 < TILE ? w - x0 : TILE;
            glUniform4f(quadLoc, (float)x0 / w, (float)y0 / h, (float)tw / w, (float)th / h);
            glViewport(0, 0, tw, th);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            glReadPixels(0, 0, tw, th, GL_RGBA, GL_UNSIGNED_BYTE, tile);
            for (int y = 0; y < th; y++)
                for (int x = 0; x < tw; x++) {
                    unsigned char *s = tile + ((size_t)y * tw + x) * 4;
                    unsigned char *d = out + ((size_t)(y0 + y) * w + x0 + x) * 3;
                    d[0] = s[0]; d[1] = s[1]; d[2] = s[2];
                }
        }
    }
    GLenum err = glGetError();
    if (err != GL_NO_ERROR) { fprintf(stderr, "GL error 0x%x\n", err); return 5; }
    FILE *o = fopen(argv[6], "wb");
    fprintf(o, "P6\n%d %d\n255\n", w, h);
    fwrite(out, 1, (size_t)w * h * 3, o);
    fclose(o);
    return 0;
}
