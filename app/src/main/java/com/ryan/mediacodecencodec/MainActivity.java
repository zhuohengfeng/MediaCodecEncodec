package com.ryan.mediacodecencodec;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.ryan.mediacodecencodec.mediacodec.DecodecThread;
import com.ryan.mediacodecencodec.mediacodec.EncodecThread;
import com.ryan.mediacodecencodec.utils.Constant;
import com.ryan.mediacodecencodec.utils.FileUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private Camera mCamera;

    private SurfaceView mLocalSurfaceView;

    private volatile boolean mIsPreview = false;
    private EncodecThread mEncodecThread;

    private SurfaceView mRemoteSurfaceView;
    private DecodecThread mDecodecThread;
    private SurfaceHolder mRemoteSurfaceHolder;

    private LinkedBlockingQueue<byte[]> mH264DataQueue = new LinkedBlockingQueue<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FileUtils.deleteFile("/sdcard/h264/test.h264");

        mLocalSurfaceView = findViewById(R.id.local_surface);
        mLocalSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                createCamera(holder);
                // 编码线程
                mEncodecThread = new EncodecThread(MainActivity.this, mH264DataQueue);
                mEncodecThread.start();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                closeCamera();
                mEncodecThread.releaseMediaCodec();
            }
        });


        mRemoteSurfaceView = findViewById(R.id.remote_surface);
        mRemoteSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                // 解码线程
                mDecodecThread = new DecodecThread(MainActivity.this, mH264DataQueue, holder.getSurface());
                mDecodecThread.start();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mDecodecThread.releaseMediaCodec();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraPreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCameraPreview();
    }

    // 打开本地camera, 并启动预览
    private void createCamera(SurfaceHolder surfaceHolder) {
        try {
            mIsPreview = false;
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            Camera.Parameters cameraParameters = mCamera.getParameters();

            Camera.CameraInfo camInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, camInfo);
            int cameraRotationOffset = camInfo.orientation;
            int rotate = (360 + cameraRotationOffset - getDegree()) % 360;

            // 设置预览参数
            cameraParameters.setRotation(rotate); // 设置方向
            int[] max = determineMaximumSupportedFramerate(cameraParameters);
            cameraParameters.setPreviewFpsRange(max[0], max[1]); // 设置最大FPS
            cameraParameters.setPreviewSize(Constant.videoW, Constant.videoH); //设置预览尺寸
            cameraParameters.setPreviewFormat(ImageFormat.NV21); // 设置预览格式
            mCamera.setParameters(cameraParameters);

            // 设置方向
            int displayRotation;
            displayRotation = (cameraRotationOffset - getDegree() + 360) % 360;
            mCamera.setDisplayOrientation(displayRotation);
            mCamera.setPreviewDisplay(surfaceHolder);

            startCameraPreview();
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    // 启动预览
    private void startCameraPreview() {
        if (mCamera != null && !mIsPreview) {
            // 预览格式
            int previewFormat = mCamera.getParameters().getPreviewFormat();
            // 获取预览尺寸
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            // 获取buffer size
            int size = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(previewFormat) / 8; // 这里要除以8，表示的是字节
            Log.d(Constant.TAG, "startCameraPreview previewSize="+previewSize.width+"x"+previewSize.height+", size="+size);

            mCamera.addCallbackBuffer(new byte[size]);
            mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
//                    Log.d(Constant.TAG, "onPreviewFrame data.size="+data.length);
                    // 开始执行编码
                    mEncodecThread.setInputData(data);
//                    mEncodecThread.notifyAll();

                    // 要执行这句，否则回调就停止
                    mCamera.addCallbackBuffer(data);
                }
            });
            mIsPreview = true;
            mCamera.startPreview();
        }
    }

    private void stopCameraPreview() {
        if (mCamera != null) {
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.stopPreview();
            mIsPreview = false;
        }
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mIsPreview = false;
        }
    }


    /**
     * 获取支持的最大帧率
     * @param parameters
     * @return
     */
    public int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
        int[] maxFps = new int[]{0, 0};
        List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
        for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
            int[] interval = it.next();
            if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
                maxFps = interval;
            }
        }
        return maxFps;
    }

    /**
     * 获取当前屏幕旋转角度
     * @return
     */
    private int getDegree() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break; // Natural orientation
            case Surface.ROTATION_90:
                degrees = 90;
                break; // Landscape left
            case Surface.ROTATION_180:
                degrees = 180;
                break;// Upside down
            case Surface.ROTATION_270:
                degrees = 270;
                break;// Landscape right
        }
        return degrees;
    }


}
