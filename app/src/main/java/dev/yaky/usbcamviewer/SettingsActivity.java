package dev.yaky.usbcamviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.view.Surface;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.Size;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private USBMonitor mUsbMonitor;
    private UVCCamera mCamera;
    private ListView mResolutionsList;
    private TextView mNoCameraMessage;

    public static final String PREFS_NAME = "CameraSettings";
    public static final String KEY_RESOLUTION = "resolution";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mResolutionsList = findViewById(R.id.resolutions_list);
        mNoCameraMessage = findViewById(R.id.no_camera_message);

        mUsbMonitor = new USBMonitor(this, mUsbMonitorOnDeviceConnectListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUsbMonitor.register();
        final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
        if (filter.isEmpty()) {
            mResolutionsList.setVisibility(ListView.GONE);
            mNoCameraMessage.setVisibility(TextView.VISIBLE);
            mNoCameraMessage.setText("No device filter found");
            return;
        }
        final List<UsbDevice> usbDevices = mUsbMonitor.getDeviceList(filter.get(0));
        if (usbDevices.isEmpty()) {
            mResolutionsList.setVisibility(ListView.GONE);
            mNoCameraMessage.setVisibility(TextView.VISIBLE);
            return;
        }
        mResolutionsList.setVisibility(ListView.VISIBLE);
        mNoCameraMessage.setVisibility(TextView.GONE);
        mUsbMonitor.requestPermission(usbDevices.get(0));
    }

    @Override
    protected void onStop() {
        if (mCamera != null) {
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
            mCamera = new UVCCamera();
            mCamera.open(ctrlBlock);

            final List<Size> supportedSizes = mCamera.getSupportedSizeList();
            final List<String> resolutionStrings = new ArrayList<>();
            for (Size size : supportedSizes) {
                resolutionStrings.add(size.width + "x" + size.height);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(SettingsActivity.this,
                            android.R.layout.simple_list_item_1, resolutionStrings);
                    mResolutionsList.setAdapter(adapter);

                    mResolutionsList.setOnItemClickListener((parent, view, position, id) -> {
                        String selectedResolution = resolutionStrings.get(position);
                        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(KEY_RESOLUTION, selectedResolution);
                        editor.apply();
                        Toast.makeText(SettingsActivity.this, "Resolution saved: " + selectedResolution, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            if (mCamera != null) {
                mCamera.close();
                mCamera = null;
            }
        }

        @Override
        public void onCancel(UsbDevice device) {
        }
    };
}
