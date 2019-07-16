package com.ryan.mediacodecencodec.mediacodec;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.ryan.mediacodecencodec.utils.Constant;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class DecodecThread extends Thread {

    private WeakReference<Activity> WeakReference;
    private LinkedBlockingQueue<byte[]> mH264DataQueue;

    // 解码器
    private MediaCodec mMediaDecodec;
    private static final String MIME = "video/avc";

    private boolean isExit = false;

    public DecodecThread(Activity context, LinkedBlockingQueue<byte[]> queue, Surface surface) {
        WeakReference = new WeakReference<>(context);
        mH264DataQueue = queue;

        initMediaDecodec(surface);
    }

    private void initMediaDecodec(Surface surface) {
        try {
            mMediaDecodec = MediaCodec.createDecoderByType(MIME);
            // 创建媒体编码格式
            MediaFormat format = MediaFormat.createVideoFormat(MIME, Constant.videoW, Constant.videoH);
            format.setInteger(MediaFormat.KEY_BIT_RATE,  Constant.videoBitrate); // 设置比特率
            format.setInteger(MediaFormat.KEY_FRAME_RATE, Constant.videoFrameRate); // 设置帧率

            mMediaDecodec.configure(format, surface, null, 0);
            // 启动解码器
            mMediaDecodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void releaseMediaCodec() {
        isExit = true;
        if (mMediaDecodec != null) {
            mMediaDecodec.stop();
            mMediaDecodec.release();
            mMediaDecodec = null;
        }
    }

    @Override
    public void run() {
        super.run();
        isExit = false;
        Log.d(Constant.TAG, "DecodecThread: start the decodec thread");
        long startMs = System.currentTimeMillis();
        while(!isExit && !Thread.interrupted()) {
            try {
                final byte[] data = mH264DataQueue.take();
                if (data != null && data.length > 0 && mMediaDecodec != null) {
                    // 开始解码
                    Log.d(Constant.TAG, "DecodecThread: data=" + data.length + ", mH264DataQueue.size=" + mH264DataQueue.size());

                    //解码后的数据，包含每一个buffer的元数据信息，例如偏差，在相关解码器中有效的数据大小
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                    //1 准备填充器
                    int dequeueInputIndex = mMediaDecodec.dequeueInputBuffer(0);
                    if (dequeueInputIndex >= 0) {
                        //2 准备填充数据
                        ByteBuffer byteBuffer = mMediaDecodec.getInputBuffer(dequeueInputIndex);
                        if (byteBuffer == null) {
                            continue;
                        }
                        byteBuffer.clear();
                        byteBuffer.put(data, 0, data.length);
                        //3 把数据传给解码器
                        mMediaDecodec.queueInputBuffer(dequeueInputIndex, 0, data.length, 0, 0);
                    } else {
                        Log.e(Constant.TAG,"查询编码器可用输入缓冲区索引错误 dequeueInputIndex="+dequeueInputIndex);
                        continue;
                    }

                    //4 开始解码
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int dequeueOutputIndex = mMediaDecodec.dequeueOutputBuffer(bufferInfo, 0);
                    if (dequeueOutputIndex >= 0) {

                        //帧控制
                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        boolean doRender = (info.size != 0);


                        Log.d(Constant.TAG, "DecodecThread: doRender="+doRender);
                        //对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
                        //调用这个api之后，SurfaceView才有图像
                        mMediaDecodec.releaseOutputBuffer(dequeueOutputIndex, true); //

//                        if (mOnDecodeListener != null) {
//                            mOnDecodeListener.decodeResult(mVideoWidth, mVideoHeight);
//                        }
                    }

                }
                else {
                    Thread.sleep(50);
                }
            }
            catch (Exception e) {

            }
        }
    }
}
