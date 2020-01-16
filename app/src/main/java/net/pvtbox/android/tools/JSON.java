package net.pvtbox.android.tools;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

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
public class JSON {

    @Nullable
    public static String optString(JSONObject json, String key) {
        return optString(json, key, null);
    }

    @Nullable
    public static String optString(@Nullable JSONObject json, String key, @Nullable String fallback) {
        if (json == null)
            return fallback;
        else if (json.isNull(key))
            return fallback;
        else
            return json.optString(key, fallback);
    }

    public static String optString(JSONArray json, int index) {
        return optString(json, index, null);
    }

    public static String optString(@Nullable JSONArray json, int index, String fallback) {
        if (json == null)
            return fallback;
        else if (json.isNull(index))
            return fallback;
        else
            return json.optString(index, fallback);
    }
}
