package org.example.core;

import java.util.List;

public class DeviceMonitor {
    private final OnDeviceChangeListener listener;
    private volatile boolean isRunning = true;

    // Interface để gửi sự kiện thay đổi thiết bị về UI
    public interface OnDeviceChangeListener {
        void onDevicesChanged(List<String> connectedIDs);
    }

    public DeviceMonitor(OnDeviceChangeListener listener) {
        this.listener = listener;
    }

    public void start() {
        Thread monitorThread = new Thread(() -> {
            while (isRunning) {
                List<String> currentConnectedIDs = ADBCommand.getConnectedDevices();
                if (listener != null) {
                    listener.onDevicesChanged(currentConnectedIDs);
                }
                try {
                    Thread.sleep(2000); // Quét mỗi 2 giây
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public void stop() {
        this.isRunning = false;
    }
}

