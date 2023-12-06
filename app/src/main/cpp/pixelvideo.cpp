/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "pixelvideo.h"

#include <jni.h>
#include <android/surface_texture.h>
#include <android/surface_texture_jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <memory>


bool checkGlError(const char *funcName) {
    GLint err = glGetError();
    if (err != GL_NO_ERROR) {
        ALOGE("GL error after %s(): 0x%08x\n", funcName, err);
        return true;
    }
    return false;
}

GLuint createShader(GLenum shaderType, const char *src) {
    GLuint shader = glCreateShader(shaderType);
    if (!shader) {
        checkGlError("glCreateShader");
        return 0;
    }
    glShaderSource(shader, 1, &src, NULL);

    GLint compiled = GL_FALSE;
    glCompileShader(shader);
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLogLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLogLen);
        if (infoLogLen > 0) {
            GLchar *infoLog = (GLchar *) malloc(infoLogLen);
            if (infoLog) {
                glGetShaderInfoLog(shader, infoLogLen, NULL, infoLog);
                ALOGE("Could not compile %s shader:\n%s\n",
                      shaderType == GL_VERTEX_SHADER ? "vertex" : "fragment", infoLog);
                free(infoLog);
            }
        }
        glDeleteShader(shader);
        return 0;
    }

    return shader;
}

GLuint createProgram(const char *vtxSrc, const char *fragSrc) {
    GLuint vtxShader = 0;
    GLuint fragShader = 0;
    GLuint program = 0;
    GLint linked = GL_FALSE;

    vtxShader = createShader(GL_VERTEX_SHADER, vtxSrc);
    if (!vtxShader) goto exit;

    fragShader = createShader(GL_FRAGMENT_SHADER, fragSrc);
    if (!fragShader) goto exit;

    program = glCreateProgram();
    if (!program) {
        checkGlError("glCreateProgram");
        goto exit;
    }
    glAttachShader(program, vtxShader);
    glAttachShader(program, fragShader);

    glLinkProgram(program);
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        ALOGE("Could not link program");
        GLint infoLogLen = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLogLen);
        if (infoLogLen) {
            GLchar *infoLog = (GLchar *) malloc(infoLogLen);
            if (infoLog) {
                glGetProgramInfoLog(program, infoLogLen, NULL, infoLog);
                ALOGE("Could not link program:\n%s\n", infoLog);
                free(infoLog);
            }
        }
        glDeleteProgram(program);
        program = 0;
    }

    exit:
    glDeleteShader(vtxShader);
    glDeleteShader(fragShader);
    return program;
}

static void printGlString(const char *name, GLenum s) {
    const char *v = (const char *) glGetString(s);
    ALOGV("GL %s: %s\n", name, v);
}

#define STR(s) #s
#define STRV(s) STR(s)
#define POS_ATTRIB 0
#define UV_ATTRIB 1
#define SAMPLER_UNIFORM 0

static const char VERTEX_SHADER[] =
        "#version 300 es\n"
        "layout(location = " STRV(POS_ATTRIB) ") in vec2 pos;\n"
        "layout(location=" STRV(UV_ATTRIB) ") in vec2 uv;\n"
        "out vec2 vUv;\n"
        "void main() {\n"
        "    gl_Position = vec4(pos, 0.0, 1.0);\n"
        "    vUv = uv;\n"
        "}\n";

static const char FRAGMENT_SHADER[] =
        "#version 300 es\n"
        "#extension GL_OES_EGL_image_external_essl3 : require\n"
        "precision mediump float;\n"
        "layout(location=" STRV(SAMPLER_UNIFORM) ") uniform samplerExternalOES sSampler;\n"
        "in vec2 vUv;\n"
        "out vec4 outColor;\n"
        "void main() {\n"
        "    outColor = texture(sSampler, vUv);\n"
        "}\n";

struct Vertex {
    GLfloat pos[2];
    GLfloat uv[2];
};

const Vertex QUAD[4] = {
        // Square with diagonal < 2 so that it fits in a [-1 .. 1]^2 square
        // regardless of rotation.
        {{-1.0f, -1.0f}, {0.0f, 0.0f}},
        {{1.0f,  -1.0f}, {1.0f, 0.0f}},
        {{-1.0f, 1.0f},  {0.0f, 1.0f}},
        {{1.0f,  1.0f},  {1.0f, 1.0f}},
};

class Renderer {
public:
    static std::unique_ptr<Renderer> create(ASurfaceTexture *surfaceTexture);

    virtual ~Renderer();

    bool init(ASurfaceTexture *surfaceTexture);

    void resize(int w, int h);

    void render();

private:
    Renderer();

    void draw();

    const EGLContext mEglContext;
    GLuint mProgram;
    GLuint mVB;
    GLuint mVBState;
    GLuint mTexture;
    ASurfaceTexture *mSurfaceTexture = nullptr;
};

std::unique_ptr<Renderer> Renderer::create(ASurfaceTexture *surfaceTexture) {
    std::unique_ptr<Renderer> renderer(new Renderer());
    if (!renderer->init(surfaceTexture)) {
        return nullptr;
    }
    return renderer;
}

Renderer::Renderer()
        : mEglContext(eglGetCurrentContext()), mProgram(0), mVB(0), mVBState(0) {
}

bool Renderer::init(ASurfaceTexture *surfaceTexture) {
    mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    if (!mProgram) return false;

    glGenBuffers(1, &mVB);
    glBindBuffer(GL_ARRAY_BUFFER, mVB);
    glBufferData(GL_ARRAY_BUFFER, sizeof(QUAD), &QUAD[0], GL_STATIC_DRAW);

    glGenVertexArrays(1, &mVBState);
    glBindVertexArray(mVBState);

    glBindBuffer(GL_ARRAY_BUFFER, mVB);
    glVertexAttribPointer(POS_ATTRIB, 2, GL_FLOAT, GL_FALSE, sizeof(Vertex),
                          (const GLvoid *) offsetof(Vertex, pos));
    glVertexAttribPointer(UV_ATTRIB, 2, GL_FLOAT, GL_FALSE,
                          sizeof(Vertex), (const GLvoid *) offsetof(Vertex, uv));
    glEnableVertexAttribArray(POS_ATTRIB);
    glEnableVertexAttribArray(UV_ATTRIB);

    mSurfaceTexture = surfaceTexture;
    glGenTextures(1, &mTexture);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTexture);
    ALOGE("mTexure: %d, mSurfaceTexture: %p", mTexture, mSurfaceTexture);
    ASurfaceTexture_attachToGLContext(mSurfaceTexture, mTexture);

    glUniform1i(SAMPLER_UNIFORM, 0);

    ALOGV("Using OpenGL ES 3.0 renderer");
    return true;
}

Renderer::~Renderer() {
    if (mSurfaceTexture) {
        ASurfaceTexture_release(mSurfaceTexture);
    }
    if (eglGetCurrentContext() != mEglContext) return;
    glDeleteVertexArrays(1, &mVBState);
    glDeleteBuffers(1, &mVB);
    glDeleteProgram(mProgram);
    glDeleteTextures(1, &mTexture);
}

void Renderer::resize(int w, int h) {
    glViewport(0, 0, w, h);
}

void Renderer::render() {
    glClearColor(0.2f, 0.2f, 0.3f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    draw();
    checkGlError("Renderer::render");
}

void Renderer::draw() {
    glUseProgram(mProgram);
    glBindVertexArray(mVBState);
    ASurfaceTexture_updateTexImage(mSurfaceTexture);
    glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, 1);
}

static std::unique_ptr<Renderer> g_renderer = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_uk_jamiern_pixelvideo_RendererView_00024Renderer_00024Companion_init(JNIEnv *env,
                                                                          jobject thiz,
                                                                          jobject surfaceTexture) {
    if (g_renderer) {
        g_renderer.reset();
    }

    printGlString("Version", GL_VERSION);
    printGlString("Vendor", GL_VENDOR);
    printGlString("Renderer", GL_RENDERER);
    printGlString("Extensions", GL_EXTENSIONS);

    ASurfaceTexture *surfTex = ASurfaceTexture_fromSurfaceTexture(env, surfaceTexture);
    if (!surfTex) {
        ALOGE("Failed to obtain ASurfaceTexture");
        return;
    }
    g_renderer = Renderer::create(surfTex);
    if (!g_renderer) {
        ALOGE("Failed to create renderer");
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_uk_jamiern_pixelvideo_RendererView_00024Renderer_00024Companion_resize(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jint width,
                                                                            jint height) {
    if (g_renderer) {
        g_renderer->resize(width, height);
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_uk_jamiern_pixelvideo_RendererView_00024Renderer_00024Companion_drawFrame(JNIEnv *env,
                                                                               jobject thiz) {
    if (g_renderer) {
        g_renderer->render();
    }
}