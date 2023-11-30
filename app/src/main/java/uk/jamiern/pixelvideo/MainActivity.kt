package uk.jamiern.pixelvideo

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private var mDecoderThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        if (mDecoderThread == null) {
            val afd = resources.openRawResourceFd(R.raw.video)
            mDecoderThread = Thread(DecoderThread(afd, surfaceHolder.surface), "DecoderThread")
            mDecoderThread!!.start()
        }
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
    }
}