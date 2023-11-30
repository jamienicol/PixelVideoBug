package uk.jamiern.pixelvideo

import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

private const val LOGTAG: String = "Decoder"

class Decoder(source: AssetFileDescriptor) : MediaCodec.Callback() {

    private var mExtractor: MediaExtractor
    private var mDecoder: MediaCodec
    private var mFormat: MediaFormat
    private var mMime: String

    init {
        mExtractor = MediaExtractor()
        mExtractor.setDataSource(source)

        val track = findVideoTrack()
        mExtractor.selectTrack(track)
        mFormat = mExtractor.getTrackFormat(track)
        mMime = mFormat.getString(MediaFormat.KEY_MIME)!!

        Log.d(LOGTAG, "Format: $mFormat")
        mDecoder = MediaCodec.createDecoderByType(mMime)
        mDecoder.setCallback(this)
    }

    private fun findVideoTrack(): Int {
        for (i in 0..<mExtractor.trackCount) {
            val format = mExtractor.getTrackFormat(i)
            Log.i(LOGTAG, "Track $i format: $format")
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("video/", false)) {
                return i
            }
        }
        throw RuntimeException("Could not find video track")
    }

    fun start(surface: Surface) {
        Log.i(LOGTAG, "Decoder.start()")

        mDecoder.configure(mFormat, surface, null, 0)
        mDecoder.start()
    }

    fun stop() {
        Log.i(LOGTAG, "Decoder.stop()")
        mDecoder.stop()
    }

    override fun onInputBufferAvailable(mc: MediaCodec, bufferId: Int) {
        Log.i(LOGTAG, "onInputBufferAvailable() id: $bufferId")
        val buffer = mc.getInputBuffer(bufferId)
        mExtractor.readSampleData(buffer!!, 0)
        val size = mExtractor.sampleSize
        val presentationTime = mExtractor.sampleTime
        val eos = !mExtractor.advance()
        if (eos) {
            mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }
        mc.queueInputBuffer(bufferId, 0, size.toInt(), presentationTime, 0)
    }

    override fun onOutputBufferAvailable(
        mc: MediaCodec,
        bufferId: Int,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        Log.i(LOGTAG, "onOutputBufferAvailable() id: $bufferId, info: $bufferInfo")
        mc.releaseOutputBuffer(bufferId, bufferInfo.presentationTimeUs)
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