package uk.jamiern.pixelvideo

import android.content.res.AssetFileDescriptor
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

private const val LOGTAG: String = "MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var mVideo: AssetFileDescriptor
    private lateinit var mRenderer: RendererView
    private lateinit var mDecoder: Decoder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mVideo = resources.openRawResourceFd(R.raw.video)
        mDecoder = Decoder(mVideo)

        mRenderer = RendererView(this, mDecoder)
        setContentView(mRenderer)
    }

    override fun onResume() {
        super.onResume()
        mRenderer.onResume()
    }

    override fun onStop() {
        super.onStop()
        mRenderer.onPause()
    }
}