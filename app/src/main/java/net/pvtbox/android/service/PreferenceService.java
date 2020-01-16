package net.pvtbox.android.service;

import android.content.SharedPreferences;

import net.pvtbox.android.application.Const;
import net.pvtbox.android.ui.files.ModeProvider;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
public class PreferenceService {
    private static final String KEY_CELLULAR_NETWORK = "cellular_network";
    private static final String KEY_AUTO_CAMERA_UPDATE = "auto_camera_update";
    private static final String KEY_MAIL = "mail";
    private static final String KEY_USER_HASH = "user_hash";
    private static final String KEY_LAST_EVENT_UUID = "last_event_uuid";
    private static final String KEY_NODE_HASH = "node_hash";
    private static final String KEY_NODE_SIGN = "node_sign";
    private static final String LICENSE_TYPE = "licence_type";
    private static final String CAMERA_FOLDER_UUID = "CAMERA_FOLDER_UUID";
    private static final String FIRST_START = "FIRST_START";
    private static final String SORTING = "SORTING";
    private static final String LOGGED_IN = "NOT_LOGIN";
    private static final String KEY_ROAMING_NETWORK = "ROAMING_NETWORK";
    private static final String EXITED = "EXITED";
    private static final String AUTOSTART = "AUTOSTART";
    private static final String STATISTIC = "STATISTIC";
    private static final String DOWNLOAD_MEDIA = "DOWNLOAD_MEDIA";
    private static final String ASK_SET_PASSCODE = "ASK_SET_PASSCODE";
    private static final String KEY_SELF_HOSTED = "self_hosted";
    private static final String KEY_HOST = "host";
    private static final String KEY_CURRENT_HOST = "current_host";

    @Nullable
    public static String encode(@Nullable String s) {
        if (s == null) return null;
        return base64Encode(xorWithKey(s.getBytes(), Const.Key.getBytes()));
    }

    @Nullable
    public static String decode(@Nullable String s) {
        if (s == null) return null;
        return new String(xorWithKey(base64Decode(s), Const.Key.getBytes()));
    }

    @NonNull
    private static byte[] xorWithKey(@NonNull byte[] a, @NonNull byte[] key) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ key[i%key.length]);
        }
        return out;
    }

    private static byte[] base64Decode(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }

    private static String base64Encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);

    }

    private final SharedPreferences sharedPreferences;

    public PreferenceService(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    public void dropSetting() {
        sharedPreferences.edit().clear().apply();
    }


    public void setLoggedIn(boolean loggedIn) {
        sharedPreferences.edit()
                .putString(encode(LOGGED_IN), encode(
                        loggedIn ? "trueLoggedInSoMakeAutoLogin" : "falseLoggedIn"))
                .apply();
    }

    public boolean isLoggedIn() {
        return Objects.equals(
                decode(sharedPreferences.getString(encode(LOGGED_IN), encode("false"))),
                "trueLoggedInSoMakeAutoLogin");
    }

    @Nullable
    public String getUserHash() {
        return decode(sharedPreferences.getString(encode(KEY_USER_HASH), null));
    }

    public void setUserHash(String userHash) {
        sharedPreferences
                .edit()
                .putString(encode(KEY_USER_HASH), encode(userHash))
                .apply();
    }

    public void setLastEventUuid(String uuid) {
        sharedPreferences
                .edit()
                .putString(encode(KEY_LAST_EVENT_UUID), encode(uuid))
                .apply();
    }

    @Nullable
    public String getLastEventUuid() {
        return decode(sharedPreferences
                .getString(encode(KEY_LAST_EVENT_UUID), null));
    }

    public void setMail(String mail) {
        sharedPreferences.edit()
                .putString(encode(KEY_MAIL), encode(mail))
                .apply();
    }

    @Nullable
    public String getMail() {
        return decode(sharedPreferences.getString(encode(KEY_MAIL), encode("")));
    }

    public void setCanUseCellular(boolean canUseCellular) {
        sharedPreferences.edit()
                .putString(encode(KEY_CELLULAR_NETWORK), encode(
                        canUseCellular ? "canTrueUseCellular" :"canUseCellularFalse"))
                .apply();
    }

    public boolean canUseCellular() {
        return Objects.equals(decode(sharedPreferences.getString(
                encode(KEY_CELLULAR_NETWORK), encode("canTrueUseCellular"))), "canTrueUseCellular");
    }

    public void setCanUseRoaming(boolean canUseRoaming) {
        sharedPreferences.edit()
                .putString(encode(KEY_ROAMING_NETWORK), encode(
                        canUseRoaming ? "roamingEnabledTrue" : "notAnOption"))
                .apply();
    }

    public boolean canUseRoaming() {
        return Objects.equals(decode(sharedPreferences.getString(
                encode(KEY_ROAMING_NETWORK), encode("roamingEnabledTrue"))),
                "roamingEnabledTrue");
    }

    public void setAutoCameraUpdate(boolean autoCameraUpdate) {
        sharedPreferences.edit()
                .putString(encode(KEY_AUTO_CAMERA_UPDATE),
                        encode(autoCameraUpdate ? "importAutomatically" : "notNeeded"))
                .apply();
    }

    public boolean getAutoCameraUpdate() {
        return Objects.equals(decode(sharedPreferences.getString(
                encode(KEY_AUTO_CAMERA_UPDATE), encode("neededNot"))),
                "importAutomatically");
    }


    @Nullable
    public String getNodeHash() {
        return decode(sharedPreferences.getString(encode(KEY_NODE_HASH), null));
    }

    public void setNodeHash(String nodeHash) {
        sharedPreferences.edit()
                .putString(encode(KEY_NODE_HASH), encode(nodeHash))
                .apply();
    }

    @Nullable
    public String getNodeSign() {
        return decode(sharedPreferences.getString(encode(KEY_NODE_SIGN), null));
    }

    public void setNodeSign(String nodeHash) {
        sharedPreferences.edit()
                .putString(encode(KEY_NODE_SIGN), encode(nodeHash))
                .apply();
    }


    @Nullable
    public String getLicenseType() {
        return decode(sharedPreferences.getString(encode(LICENSE_TYPE), encode("")));
    }

    public void setLicenseType(String licenseType) {
        sharedPreferences.edit()
                .putString(encode(LICENSE_TYPE), encode(licenseType))
                .apply();
    }

    @Nullable
    public String getCameraFolderUuid() {
        return decode(sharedPreferences.getString(encode(CAMERA_FOLDER_UUID), null));
    }


    public void setCameraFolderUuid(String uuid) {
        sharedPreferences.edit()
                .putString(encode(CAMERA_FOLDER_UUID), encode(uuid))
                .apply();
    }

    public boolean isFirstStart() {
        return Objects.requireNonNull(decode(sharedPreferences.getString(encode(FIRST_START), encode("")))).isEmpty();
    }

    public void writeFirstStart() {
        sharedPreferences.edit()
                .putString(encode(FIRST_START), encode("FIRST_START"))
                .apply();
    }

    public void setSorting(@NonNull ModeProvider.Sorting sorting) {
        sharedPreferences.edit()
                .putInt(encode(SORTING), sorting.ordinal())
                .apply();
    }

    public ModeProvider.Sorting getSorting() {
        int ordinal = sharedPreferences
                .getInt(encode(SORTING), ModeProvider.Sorting.date.ordinal());
        return ModeProvider.Sorting.values()[ordinal];
    }

    public void setExited(boolean exited) {
        sharedPreferences.edit()
                .putString(encode(EXITED), encode(exited ? "exited" : "notExited"))
                .apply();
    }

    public boolean isExited() {
        return Objects.equals(decode(sharedPreferences.getString(
                encode(EXITED), encode("notSet"))), "exited");
    }

    public void setAutoStart(boolean checked) {
        sharedPreferences.edit()
                .putString(encode(AUTOSTART),
                        encode(checked ? "autoStartEnabled" : "disabledAutoStart"))
                .apply();
    }

    public boolean isAutoStart() {
        return Objects.equals(decode(
                sharedPreferences.getString(encode(AUTOSTART), encode("autoStartEnabled"))),
                "autoStartEnabled");
    }

    public void setStatisticEnabled(boolean enabled) {
        sharedPreferences.edit().putString(encode(STATISTIC),
                encode(enabled ? "true" : "false")).apply();
    }

    public boolean isStatisticEnabled() {
        return Objects.equals(decode(sharedPreferences.getString(
                encode(STATISTIC), encode("true"))),
                "true");
    }

    public void setMediaDownloadEnabled(boolean enabled) {
        sharedPreferences.edit().putString(encode(DOWNLOAD_MEDIA), encode(
                enabled ? "enabled" : "disabled")).apply();
    }

    public boolean isMediaDownloadEnabled() {
        return Objects.equals(decode(sharedPreferences.getString(
                encode(DOWNLOAD_MEDIA), encode("nope"))), "enabled");
    }

    public boolean askSetPasscode() {
        return Objects.equals(decode(sharedPreferences.getString(
                encode(ASK_SET_PASSCODE), encode("true"))), "true");
    }

    public void unsetAskSetPasscode() {
        sharedPreferences.edit().putString(encode(ASK_SET_PASSCODE),
                encode("doNotAskCauseAlreadyAsked")).apply();
    }

    public boolean isSelfHosted() {
        return Objects.equals(decode(
                sharedPreferences.getString(encode(KEY_SELF_HOSTED), encode("false"))),
                "true");
    }

    public void setSelfHosted(boolean value) {
        sharedPreferences.edit().putString(encode(KEY_SELF_HOSTED), encode(
                value ? "true" : "false")).apply();
    }

    @NonNull
    public String getHost() {
        String host = decode(sharedPreferences.getString(encode(KEY_HOST), null));
        return host == null || host.isEmpty() ? Const.BASE_URL : host;
    }

    public void setHost(String host) {
        sharedPreferences.edit()
                .putString(encode(KEY_HOST), encode(host))
                .apply();
    }

    @NonNull
    public String getCurrentHost() {
        String host = decode(sharedPreferences.getString(encode(KEY_CURRENT_HOST), null));
        return host == null || host.isEmpty() ? Const.BASE_URL : host;
    }

    public void setCurrentHost(String host) {
        sharedPreferences.edit()
                .putString(encode(KEY_CURRENT_HOST), encode(host))
                .apply();
    }
}
