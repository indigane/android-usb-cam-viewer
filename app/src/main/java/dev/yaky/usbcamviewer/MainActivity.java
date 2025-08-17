package dev.yaky.usbcamviewer;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

import android.Manifest;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.view.Surface;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.SharedPreferences;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.UVCCameraTextureView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private USBMonitor mUsbMonitor;
    private UVCCamera mCamera;
    private UVCCameraTextureView mUVCCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request access to camera and to record audio
        // (both are required to automatically handle USB cameras)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PERMISSION_DENIED
        || ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PERMISSION_DENIED ) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            }, 0);
        }

        // Set window as edge-to-edge fullscreen
        EdgeToEdge.enable(this);
        var flags = WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
        getWindow().setFlags(flags, flags);

        setContentView(R.layout.activity_main);
        mUVCCameraView = findViewById(R.id.camera_view);

        mUsbMonitor = new USBMonitor(this, mUsbMonitorOnDeviceConnectListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Initialize and start the USB monitor
        mUsbMonitor.register();
        // Request access to the first USB camera
        final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
        final List<UsbDevice> usbDevices = mUsbMonitor.getDeviceList(filter.get(0));
        if (usbDevices.isEmpty()) return;
        final UsbDevice firstUsbDevice = usbDevices.get(0);
        mUsbMonitor.requestPermission(firstUsbDevice);
        // Next step is the onConnect event in the USBMonitor
    }

    @Override
    protected void onStop() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.close();
        }
        mUsbMonitor.unregister();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mUsbMonitor.destroy();
        mUsbMonitor = null;
        super.onDestroy();
    }

    private final USBMonitor.OnDeviceConnectListener mUsbMonitorOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {

        @Override
        public void onAttach(UsbDevice device) {
        }

        @Override
        public void onDettach(UsbDevice device) {
        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            UVCCamera camera = new UVCCamera();
            camera.open(ctrlBlock);

            SharedPreferences settings = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
            String savedResolution = settings.getString(SettingsActivity.KEY_RESOLUTION, null);
            boolean keepAspectRatio = settings.getBoolean(SettingsActivity.KEY_KEEP_ASPECT_RATIO, false);

            int width = 0;
            int height = 0;

            if (savedResolution != null) {
                String[] parts = savedResolution.split("x");
                width = Integer.parseInt(parts[0]);
                height = Integer.parseInt(parts[1]);
            } else {
                var previewSize = camera.getSupportedSizeList().get(0);
                width = previewSize.width;
                height = previewSize.height;
            }

            try {
                camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG);
            } catch (final IllegalArgumentException e) {
                try {
                    // fallback to YUV mode
                    camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE);
                } catch (final IllegalArgumentException e1) {
                    camera.destroy();
                    return;
                }
            }

            if (keepAspectRatio) {
                mUVCCameraView.setAspectRatio((double) width / height);
            } else {
                mUVCCameraView.setAspectRatio(0.0);
            }

            camera.setPreviewDisplay(mUVCCameraView.getSurface());
            camera.startPreview();

            mCamera = camera;
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            mCamera.stopPreview();
            mCamera.close();
            mCamera = null;
        }

        @Override
        public void onCancel(UsbDevice device) {
        }
    };
}