package uk.jamiern.pixelvideo

import android.content.res.AssetFileDescriptor
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity

private const val LOGTAG: String = "MainActivity"

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var mVideo: AssetFileDescriptor
    private var mDecoder: Decoder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.holder.addCallback(this)

        mVideo = resources.openRawResourceFd(R.raw.video)
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        Log.i(LOGTAG, "surfaceCreated")
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        Log.i(LOGTAG, "surfaceChanged")
        mDecoder = Decoder(mVideo)
        mDecoder!!.start(surfaceHolder.surface)
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        Log.i(LOGTAG, "surfaceDestroyed")
        mDecoder!!.stop()
        mDecoder = null
    }
}