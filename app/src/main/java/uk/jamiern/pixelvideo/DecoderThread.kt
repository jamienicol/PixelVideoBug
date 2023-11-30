package uk.jamiern.pixelvideo

import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

private const val LOGTAG: String = "DecoderThread"

class DecoderThread(asset: AssetFileDescriptor, outputSurface: Surface) : MediaCodec.Callback(),
    Runnable {
    private val mAsset = asset
    private val mOutputSurface = outputSurface
    private lateinit var mExtractor: MediaExtractor
    private lateinit var mDecoder: MediaCodec

    override fun run() {
        Log.i(LOGTAG, "Running decoder thread")

        mExtractor = MediaExtractor()
        mExtractor.setDataSource(mAsset)

        var format: MediaFormat? = null
        var mime: String? = null
        for (i in 0..<mExtractor.trackCount) {
            val currentFormat = mExtractor.getTrackFormat(i)
            val currentMime = currentFormat.getString(MediaFormat.KEY_MIME)
            if (currentMime!!.startsWith("video/", false)) {
                format = currentFormat
                mime = currentMime
                mExtractor.selectTrack(i)
                Log.d(LOGTAG, "Found track $i. mime: $mime")
                break
            }
        }

        mDecoder = MediaCodec.createDecoderByType(mime!!)
        mDecoder.setCallback(this)
        mDecoder.configure(format!!, mOutputSurface, null, 0)
        mDecoder.start()
    }

    override fun onInputBufferAvailable(mc: MediaCodec, bufferId: Int) {
        Log.i(LOGTAG, "onInputBufferAvailable() id: $bufferId")
        val buffer = mc.getInputBuffer(bufferId)
        mExtractor.readSampleData(buffer!!, 0)
        val size = mExtractor.sampleSize
        val presentationTime = mExtractor.sampleTime
        val eos = !mExtractor.advance()
        val flags = if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        mc.queueInputBuffer(bufferId, 0, size.toInt(), presentationTime, flags)
    }

    override fun onOutputBufferAvailable(
        mc: MediaCodec,
        bufferId: Int,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        Log.i(LOGTAG, "onOutputBufferAvailable() id: $bufferId, info: $bufferInfo")
        mc.releaseOutputBuffer(bufferId, true)
    }

    override fun onError(mc: MediaCodec, error: MediaCodec.CodecException) {
        Log.e(LOGTAG, "MediaCodec error: $error")
    }

    override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
        Log.i(LOGTAG, "onOutputFormatChanged() $format")
        Log.d(LOGTAG, "color-format: ${format.getInteger(MediaFormat.KEY_COLOR_FORMAT)}")
        Log.d(LOGTAG, "color-standard: ${format.getInteger(MediaFormat.KEY_COLOR_STANDARD)}")
        Log.d(LOGTAG, "color-range: ${format.getInteger(MediaFormat.KEY_COLOR_RANGE)}")
        Log.d(LOGTAG, "color-transfer: ${format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)}")
    }
}