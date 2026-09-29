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
    height: Int = 1080,
    backgroundColorInt: Int = AndroidColor.WHITE
): Pair<Boolean, String> {
    if (frames.isEmpty()) {
        return Pair(false, "No frames to export")
    }

    // Ensure width and height are even numbers (required by hardware H.264 encoders)
    val safeWidth = if (width % 2 != 0) width + 1 else width
    val safeHeight = if (height % 2 != 0) height + 1 else height

    val outputFile = File(context.getExternalFilesDir(null), "animation_output.mp4")
    if (outputFile.exists()) {
        outputFile.delete()
    }

    val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
    val format = MediaFormat.createVideoFormat(mimeType, safeWidth, safeHeight).apply {
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

        // Calculate the exact delay per frame in milliseconds for proper pacing
        val frameIntervalMs = 1_000L / fps.coerceAtLeast(1)

        // Draw and feed each frame onto the encoder surface with precise pacing
        for (i in frames.indices) {
            trackIndex = drainEncoderCompletely(codec, muxer, bufferInfo, trackIndex, { muxerStarted }, { muxerStarted = true })

            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(backgroundColorInt)

                frames[i].layers.forEach { layer ->
                    if (layer.isVisible) {
                        layer.paths.forEach { styledPath ->
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
                    }
                }
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }

            // Pace the frame submission so the hardware encoder processes them smoothly
            Thread.sleep(frameIntervalMs)
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