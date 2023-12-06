package uk.jamiern.pixelvideo

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val LOGTAG: String = "RendererView"

class RendererView(context: Context?, decoder: Decoder) : GLSurfaceView(context) {

    private var mRenderer = Renderer(decoder)

    init {
        setEGLContextClientVersion(3)
        setRenderer(mRenderer)
    }

    override fun onPause() {
        Log.i(LOGTAG, "RendererView.onPause()")
    }

    override fun onResume() {
        super.onResume()
        Log.i(LOGTAG, "RendererView.onResume()")
    }

    fun getSurface(): Surface {
        return mRenderer.getSurface()
    }

    private class Renderer(decoder: Decoder) : GLSurfaceView.Renderer {
        private var mDecoder = decoder
        private var mSurfaceTexture = SurfaceTexture(false)
        private var mSurface: Surface = Surface(mSurfaceTexture)

        fun getSurface(): Surface {
            return mSurface
        }

        override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
            Log.i(LOGTAG, "Renderer.onSurfaceCreated()")
            init(mSurfaceTexture)
            mDecoder.start(mSurface)
        }

        override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
            Log.i(LOGTAG, "Renderer.onSurfaceChanged()")
            resize(width, height)
        }

        override fun onDrawFrame(unused: GL10?) {
            Log.i(LOGTAG, "Renderer.onDrawFrame()")
            drawFrame()
        }

        companion object {
            init {
                System.loadLibrary("pixelvideo")
            }

            external fun init(surfaceTexture: SurfaceTexture)
            external fun resize(width: Int, height: Int)
            external fun drawFrame()
        }
    }

}
