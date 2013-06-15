/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.testingcamera2;

import android.content.Context;
import android.hardware.photography.CameraAccessException;
import android.hardware.photography.CameraDevice;
import android.hardware.photography.CameraManager;
import android.hardware.photography.CameraProperties;
import android.hardware.photography.CaptureRequest;
import android.hardware.photography.CaptureResult;
import android.hardware.photography.Size;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.SurfaceHolder;
import android.view.Surface;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;

/**
 * A camera controller class that runs in its own thread, to
 * move camera ops off the UI. Generally thread-safe.
 */
public class CameraOps {

    private Thread mOpsThread;
    private Handler mOpsHandler;

    private CameraManager mCameraManager;
    private CameraDevice  mCamera;

    private static final int STATUS_ERROR = 0;
    private static final int STATUS_UNINITIALIZED = 1;
    private static final int STATUS_OK = 2;

    private int mStatus = STATUS_UNINITIALIZED;

    private void checkOk() {
        if (mStatus < STATUS_OK) {
            throw new IllegalStateException(String.format("Device not OK: %d", mStatus ));
        }
    }

    private class OpsHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

        }
    }

    private CameraOps(Context ctx) throws ApiFailureException {
        mCameraManager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        if (mCameraManager == null) {
            throw new ApiFailureException("Can't connect to camera manager!");
        }

        mOpsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mOpsHandler = new OpsHandler();
                Looper.loop();
            }
        }, "CameraOpsThread");
        mOpsThread.start();

        mStatus = STATUS_OK;
    }

    static public CameraOps create(Context ctx) throws ApiFailureException {
        return new CameraOps(ctx);
    }

    public String[] getDevices() throws ApiFailureException{
        checkOk();
        try {
            return mCameraManager.getDeviceIdList();
        } catch (CameraAccessException e) {
            throw new ApiFailureException("Can't query device set", e);
        }
    }

    public void registerCameraListener(CameraManager.CameraListener listener)
            throws ApiFailureException {
        checkOk();
        mCameraManager.registerCameraListener(listener);
    }

    public CameraProperties getDeviceProperties(String cameraId)
            throws CameraAccessException, ApiFailureException {
        checkOk();
        return mCameraManager.getCameraProperties(cameraId);
    }

    public void openDevice(String cameraId)
            throws CameraAccessException, ApiFailureException {
        checkOk();

        if (mCamera != null) {
            throw new IllegalStateException("Already have open camera device");
        }

        mCamera = mCameraManager.openCamera(cameraId);
    }

    public void closeDevice()
            throws ApiFailureException {
        checkOk();

        if (mCamera == null) return;

        try {
            mCamera.close();
        } catch (Exception e) {
            throw new ApiFailureException("can't close device!", e);
        }

        mCamera = null;
    }

    /**
     * Just run preview to a SurfaceView
     */
    public void minimalPreview(SurfaceHolder previewHolder) throws ApiFailureException {
        if (mCamera == null) {
            try {
                String[] devices = mCameraManager.getDeviceIdList();
                if (devices == null || devices.length == 0) {
                    throw new ApiFailureException("no devices");
                }
                mCamera = mCameraManager.openCamera(devices[0]);
            } catch (CameraAccessException e) {
                throw new ApiFailureException("open failure", e);
            }
        }

        mStatus = STATUS_OK;

        try {
            mCamera.stopRepeating();
            mCamera.waitUntilIdle();

            CameraProperties properties = mCamera.getProperties();

            Size[] previewSizes = null;
            if (properties != null) {
                previewSizes = properties.get(
                    CameraProperties.SCALER_AVAILABLE_PROCESSED_SIZES);
            }

            if (previewSizes == null || previewSizes.length == 0) {
                previewHolder.setFixedSize(640, 480);
            } else {
                previewHolder.setFixedSize(previewSizes[0].getWidth(), previewSizes[0].getHeight());
            }

            Surface previewSurface = previewHolder.getSurface();

            List<Surface> outputSurfaces = new ArrayList(1);
            outputSurfaces.add(previewSurface);

            mCamera.configureOutputs(outputSurfaces);

            CaptureRequest previewRequest = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            previewRequest.addTarget(previewSurface);

            mCamera.setRepeatingRequest(previewRequest, null);
        } catch (CameraAccessException e) {
            throw new ApiFailureException("Error setting up minimal preview", e);
        }
    }

}
