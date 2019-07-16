package com.ryan.mediacodecencodec.mediacodec;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.ryan.mediacodecencodec.utils.Constant;
import com.ryan.mediacodecencodec.utils.Util;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class EncodecThread extends Thread {

    private WeakReference<Activity> WeakReference;

    private LinkedBlockingQueue<byte[]> mNV21DataQueue = new LinkedBlockingQueue<>();
    private LinkedBlockingQueue<byte[]> mH264DataQueue;

    // 编码器
    private MediaCodec mMediaEncodec;
    private static final String MIME = "video/avc";

    private boolean isExit = false;

    public EncodecThread(Activity context, LinkedBlockingQueue<byte[]> queue) {
        WeakReference = new WeakReference<>(context);
        mH264DataQueue = queue;

        initMediaEncodec();
    }

    private void initMediaEncodec() {
        try {
            mMediaEncodec = MediaCodec.createEncoderByType(MIME);
            // 创建媒体编码格式
            MediaFormat format = MediaFormat.createVideoFormat(MIME, Constant.videoW, Constant.videoH);
            format.setInteger(MediaFormat.KEY_BIT_RATE,  Constant.videoBitrate); // 设置比特率
            format.setInteger(MediaFormat.KEY_FRAME_RATE, Constant.videoFrameRate); // 设置帧率
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible); // 设置颜色格式， NV21？
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Constant.videoGOP); // 设置GOP = 2s
            mMediaEncodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            // 启动解码器
            mMediaEncodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void releaseMediaCodec() {
        isExit = true;
        if (mMediaEncodec != null) {
            mMediaEncodec.stop();
            mMediaEncodec.release();
            mMediaEncodec = null;
        }
    }

    @Override
    public void run() {
        super.run();
        isExit = false;
        Log.d(Constant.TAG, "EncodecThread: start the encodec thread");
        while(!isExit && !Thread.interrupted()) {
            try {
                // 去除队列进行编码
                final byte[] data = mNV21DataQueue.take();
                if (data != null && data.length > 0 && mMediaEncodec != null) {
                    // 开始解码
                    Log.d(Constant.TAG, "EncodecThread: data="+data.length+", mNV21DataQueue.size="+ mNV21DataQueue.size());
                    // 查询编码器可用输入缓冲区索引
                    int dequeueInputIndex = mMediaEncodec.dequeueInputBuffer(-1);
                    if (dequeueInputIndex >= 0) {
                        ByteBuffer inputBuffer= mMediaEncodec.getInputBuffer(dequeueInputIndex);
                        inputBuffer.clear();
                        inputBuffer.put(data);
                        // 将填充好的输入缓冲器的索引提交给编码器，注意第四个参数是缓冲区的时间戳，微秒单位，后一帧的时间应该大于前一帧
                        mMediaEncodec.queueInputBuffer(dequeueInputIndex, 0, data.length, System.currentTimeMillis(), 0);
                    }
                    else {
                        Log.e(Constant.TAG,"查询编码器可用输入缓冲区索引错误 dequeueInputIndex="+dequeueInputIndex);
                        continue;
                    }

                    // 查询编码好的输出缓冲区索引
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int dequeueOutputIndex = mMediaEncodec.dequeueOutputBuffer(bufferInfo, 0);
                    while (dequeueOutputIndex >= 0) {
                        // 根据索引获取输出缓冲区
                        ByteBuffer outputBuffer = mMediaEncodec.getOutputBuffer(dequeueOutputIndex);
                        // 从缓冲区获取编码成H264的byte[]
                        byte[] outData = new byte[outputBuffer.remaining()];
                        outputBuffer.get(outData, 0, outData.length);

                        Log.d(Constant.TAG, "EncodecThread: 得到H264 outData="+outData.length+", dequeueOutputIndex="+dequeueOutputIndex);
                        // 编码得到H264文件，保存起来调试使用
                        Util.writeFileToSDCard(outData, "H264", "test.h264", true, false);

                        // 把H264保存到解码队列中
                        if (mH264DataQueue != null && outData != null & outData.length > 0) {
                            mH264DataQueue.put(outData);
                        }

                        // 根据输出缓冲区的索引释放该输出缓冲区
                        mMediaEncodec.releaseOutputBuffer(dequeueOutputIndex, false);
                        dequeueOutputIndex = mMediaEncodec.dequeueOutputBuffer(bufferInfo, 0);
                        Log.d(Constant.TAG, "EncodecThread: 释放后 dequeueOutputIndex="+dequeueOutputIndex);
                    }
                }
                else {
                    Thread.sleep(50);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 传入原始数据
    public void setInputData(final byte[] data) {
        try {
            if (data != null && data.length > 0) {
                mNV21DataQueue.put(data);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }






}
