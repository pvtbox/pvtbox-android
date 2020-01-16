package net.pvtbox.android.ui.start;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Objects;

/**
*  
*  Pvtbox. Fast and secure file transfer & sync directly across your devices. 
*  Copyright © 2020  Pb Private Cloud Solutions Ltd. 
*  
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*     http://www.apache.org/licenses/LICENSE-2.0
*  
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*  
**/
/**
 * References:
 * https://users.ece.cmu.edu/~tvidas/papers/ASIACCS14.pdf
 * http://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in-the-emulator
 * http://webcache.googleusercontent.com/search?q=cache:7NRl_DBrk2AJ:www.oguzhantopgul.com/2014/12/android-malware-evasion-techniques.html+&cd=6&hl=en&ct=clnk&gl=us
 * https://github.com/Fuzion24/AndroidHostileEnvironmentDetection
 */
public class EmulatorDetector {
    @NonNull
    private static final String tracerpid = "TracerPid";

    public static boolean isEmulator(@NonNull Context context) {
        return hasEmulatorTelephonyProperty(context)
                || isDebuggerConnected()
                || ActivityManager.isUserAMonkey()
                || hasEmulatorBuildProp()
                || ( hasQemuBuildProps(context) && hasQemuCpuInfo() && hasQemuFile() )
                || hasEth0Interface()
                ;
    }

    private static boolean hasEth0Interface() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (intf.getName().equals("eth0"))
                    return true;
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private static boolean hasQemuCpuInfo() {
        try {
            BufferedReader cpuInfoReader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = cpuInfoReader.readLine()) != null) {
                if (line.contains("Goldfish"))
                    return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean hasQemuFile() {
        return new File("/init.goldfish.rc").exists()
                || new File("/sys/qemu_trace").exists()
                || new File("/system/bin/qemud").exists();

    }

    @Nullable
    private static String getProp(@NonNull Context ctx, String propName) {
        try {
            ClassLoader cl = ctx.getClassLoader();
            @SuppressLint("PrivateApi") Class<?> klazz = cl.loadClass("android.os.properties");
            Method getProp = klazz.getMethod("get", String.class);
            Object[] params = {propName};
            return (String) getProp.invoke(klazz, params);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static boolean hasQemuBuildProps(@NonNull Context context) {
        return "goldfish".equals(getProp(context, "ro.hardware"))
                || "ranchu".equals(getProp(context, "ro.hardware"))
                || "generic".equals(getProp(context, "ro.product.device"))
                || "1".equals(getProp(context, "ro.kernel.qemu"))
                || "0".equals(getProp(context, "ro.secure"));
    }

    private static boolean isDebuggerConnected() {
        return Debug.isDebuggerConnected()
                || hasAdbInEmulator()
                || hasTracerPid()
                ;
    }

    private static boolean hasTracerPid() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/self/status")), 1000)) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.length() > tracerpid.length()) {
                    if (line.substring(0, tracerpid.length()).equalsIgnoreCase(tracerpid)) {
                        if (Integer.decode(line.substring(tracerpid.length() + 1).trim()) > 0) {
                            return true;
                        }
                        break;
                    }
                }
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return false;
    }

    private static boolean hasAdbInEmulator() {
        boolean adbInEmulator = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/net/tcp")), 1000)) {
            String line;
            // Skip column names
            reader.readLine();

            ArrayList<tcp> tcpList = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                tcpList.add(tcp.create(line.split("\\W+")));
            }

            reader.close();

            // Adb is always bounce to 0.0.0.0 - though the port can change
            // real devices should be != 127.0.0.1
            int adbPort = -1;
            for (tcp tcpItem : tcpList) {
                if (tcpItem.localIp == 0) {
                    adbPort = tcpItem.localPort;
                    break;
                }
            }

            if (adbPort != -1) {
                for (tcp tcpItem : tcpList) {
                    if ((tcpItem.localIp != 0) && (tcpItem.localPort == adbPort)) {
                        adbInEmulator = true;
                    }
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        return adbInEmulator;
    }

    @SuppressLint("HardwareIds")
    private static boolean hasEmulatorBuildProp() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk") || Build.MODEL.contains("sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.PRODUCT.contains("google_sdk") || Build.PRODUCT.contains("sdk")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.BOARD.contains("unknown")
                || Build.ID.contains("FRF91")
                || Build.MANUFACTURER.contains("unknown")
                || Build.SERIAL == null
                || Build.TAGS.contains("test-keys")
                ;
    }

    private static boolean hasEmulatorTelephonyProperty(@NonNull Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return "Android".equals(Objects.requireNonNull(tm).getNetworkOperatorName())
                || "Android".equals(tm.getSimOperator())
                ;
    }

    static class tcp {

        final long localIp;
        final int localPort;

        @NonNull
        static tcp create(String[] params) {
            return new tcp(params[2], params[3]
            );
        }

        tcp(@NonNull String localIp, @NonNull String localPort) {
            this.localIp = Long.parseLong(localIp, 16);
            this.localPort = Integer.parseInt(localPort, 16);
        }
    }
}