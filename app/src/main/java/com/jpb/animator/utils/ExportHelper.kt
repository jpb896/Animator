package com.jpb.animator.utils

import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.ui.graphics.asAndroidPath
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
        val frameIntervalMs = 1000L / fps

        // Draw and feed each frame onto the encoder surface with real-world pacing
        for (i in frames.indices) {
            // Drain any pending output buffers before feeding the next frame
            trackIndex = drainEncoderCompletely(codec, muxer, bufferInfo, trackIndex, { muxerStarted }, { muxerStarted = true })

            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(AndroidColor.WHITE)

                // Draw each styled path with its correct color, width, and style
                frames[i].paths.forEach { styledPath ->
                    val paint = android.graphics.Paint().apply {
                        strokeWidth = styledPath.strokeWidth
                        style = if (styledPath.drawStyle == DrawStyle.FILL) {
                            android.graphics.Paint.Style.FILL
                        } else {
                            android.graphics.Paint.Style.STROKE
                        }
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        isAntiAlias = true
                        color = AndroidColor.argb(
                            styledPath.color.alpha,
                            styledPath.color.red,
                            styledPath.color.green,
                            styledPath.color.blue
                        )
                    }
                    canvas.drawPath(styledPath.path.asAndroidPath(), paint)
                }
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }

            // Pace frames so GraphicBufferSource registers correct timestamps
            try {
                Thread.sleep(frameIntervalMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        // Signal the end of the stream
        codec.signalEndOfInputStream()

        // Continue draining until EOS chunk is received from the encoder
        var sawEos = false
        val startWaitTime = System.currentTimeMillis()
        while (!sawEos && (System.currentTimeMillis() - startWaitTime < 5000)) {
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                check(!muxerStarted) { "Format changed twice." }
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
            } else if (outputBufferId >= 0) {
                val encodedData = codec.getOutputBuffer(outputBufferId)
                if (encodedData != null && bufferInfo.size != 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                codec.releaseOutputBuffer(outputBufferId, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawEos = true
                }
            }
        }

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

private fun drainEncoderCompletely(
    codec: MediaCodec,
    muxer: MediaMuxer,
    bufferInfo: MediaCodec.BufferInfo,
    initialTrackIndex: Int,
    isMuxerStarted: () -> Boolean,
    setMuxerStarted: (Boolean) -> Unit
): Int {
    var trackIndex = initialTrackIndex
    while (true) {
        val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 0L)
        if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
            break
        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            check(!isMuxerStarted()) { "Format changed twice." }
            trackIndex = muxer.addTrack(codec.outputFormat)
            muxer.start()
            setMuxerStarted(true)
        } else if (outputBufferId < 0) {
            // Ignore other status codes
        } else {
            val encodedData = codec.getOutputBuffer(outputBufferId)
            if (encodedData != null) {
                if (bufferInfo.size != 0 && isMuxerStarted()) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                codec.releaseOutputBuffer(outputBufferId, false)
            }
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break
            }
        }
    }
    return trackIndex
}