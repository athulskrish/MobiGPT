package com.keralatechreach.mobigpt.utils;

import android.app.ActivityManager;
import android.content.Context;
import java.util.Locale;

public class DeviceHardwareHelper {

    public static class SystemHardwareStats {
        public final int cpuCores;
        public final long totalRamBytes;
        public final long availableRamBytes;
        public final int availableRamPercent;
        public final boolean isLowMemory;

        public SystemHardwareStats(int cpuCores, long totalRamBytes, long availableRamBytes, int availableRamPercent, boolean isLowMemory) {
            this.cpuCores = cpuCores;
            this.totalRamBytes = totalRamBytes;
            this.availableRamBytes = availableRamBytes;
            this.availableRamPercent = availableRamPercent;
            this.isLowMemory = isLowMemory;
        }

        public String getFormattedAvailableRam() {
            double gb = availableRamBytes / (1024.0 * 1024.0 * 1024.0);
            return String.format(Locale.US, "%.1f GB Free", gb);
        }

        public String getFormattedSummary() {
            return cpuCores + " Cores • " + getFormattedAvailableRam();
        }
    }

    public static SystemHardwareStats getSystemHardwareStats(Context context) {
        int cores = Runtime.getRuntime().availableProcessors();
        long totalRam = 0;
        long availRam = 0;
        boolean lowMem = false;
        int percent = 0;

        if (context != null) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);
                totalRam = memoryInfo.totalMem;
                availRam = memoryInfo.availMem;
                lowMem = memoryInfo.lowMemory;
                if (totalRam > 0) {
                    percent = (int) ((availRam * 100) / totalRam);
                }
            }
        }

        return new SystemHardwareStats(cores, totalRam, availRam, percent, lowMem);
    }

    public static String getModelReadinessBadge(long modelSizeBytes, long availableRamBytes) {
        if (modelSizeBytes <= 0) {
            return "Custom Model";
        }
        long estimatedRequiredRam = (long) (modelSizeBytes * 1.35);
        if (availableRamBytes > 0 && estimatedRequiredRam > availableRamBytes) {
            return "⚠️ High RAM Usage";
        }
        if (modelSizeBytes < 1_200_000_000L) {
            return "⚡ Ultra Fast";
        } else if (modelSizeBytes < 2_500_000_000L) {
            return "⚖️ Recommended";
        } else {
            return "🔥 Demanding (7B+)";
        }
    }
}