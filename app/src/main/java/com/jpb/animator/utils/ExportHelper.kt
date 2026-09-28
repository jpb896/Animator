package com.jpb.animator.utils

import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.ui.graphics.asAndroidPath
import com.jpb.animator.AnimationFrame
import java.io.File

fun exportAnimationNative(
    context: Context,
    frames: List<AnimationFrame>,
    fps: Int = 10,
    width: Int = 1080,
    height: Int = 1080
): Pair<Boolean, String> {
    if (frames.isEmpty()) {
        return Pair(false, "No frames to export")
    }

    val outputFile = File(context.getExternalFilesDir(null), "animation_output.mp4")
    if (outputFile.exists()) {
        outputFile.delete()
    }

    val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
    val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    var codec: MediaCodec? = null
    var muxer: MediaMuxer? = null
    var muxerStarted = false

    try {
        codec = MediaCodec.createEncoderByType(mimeType)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var trackIndex = -1
        val bufferInfo = MediaCodec.BufferInfo()

        // Draw and feed each frame onto the encoder surface
        for (i in frames.indices) {
            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(AndroidColor.WHITE)
                val paint = android.graphics.Paint().apply {
                    color = AndroidColor.BLACK
                    strokeWidth = 8f
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    isAntiAlias = true
                }
                frames[i].paths.forEach { composePath ->
                    canvas.drawPath(composePath.asAndroidPath(), paint)
                }
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }

            // Drain any available output from the encoder
            trackIndex = drainEncoder(codec, muxer, bufferInfo, trackIndex, { muxerStarted }, { muxerStarted = true }, false)
        }

        // Signal the end of the stream and finish draining
        codec.signalEndOfInputStream()

        var drainTimeout = 0
        while (drainTimeout < 10) {
            trackIndex = drainEncoder(codec, muxer, bufferInfo, trackIndex, { muxerStarted }, { muxerStarted = true }, true)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            drainTimeout++
        }

        // Safely stop components only if the muxer started successfully
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
        codec.stop()
        codec.release()

        return Pair(true, outputFile.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
            codec?.stop()
            codec?.release()
        } catch (ignored: Exception) {}
        return Pair(false, e.localizedMessage ?: "Unknown encoding error")
    }
}

private fun drainEncoder(
    codec: MediaCodec,
    muxer: MediaMuxer,
    bufferInfo: MediaCodec.BufferInfo,
    initialTrackIndex: Int,
    isMuxerStarted: () -> Boolean,
    setMuxerStarted: (Boolean) -> Unit,
    endOfStream: Boolean
): Int {
    var trackIndex = initialTrackIndex
    val timeoutUs = 10_000L

    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
    if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
        // No output available yet
    } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        check(!isMuxerStarted()) { "Format changed twice." }
        val newFormat = codec.outputFormat
        trackIndex = muxer.addTrack(newFormat)
        muxer.start()
        setMuxerStarted(true)
    } else if (outputBufferId < 0) {
        // Ignore other status codes
    } else {
        val encodedData = codec.getOutputBuffer(outputBufferId)
        if (encodedData != null) {
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0
            }

            if (bufferInfo.size != 0) {
                if (isMuxerStarted()) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
            }

            codec.releaseOutputBuffer(outputBufferId, false)
        }
    }
    return trackIndex
}