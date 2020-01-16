package net.pvtbox.android.tools.diskspace;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

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
public class DiskSpace {
    private final float size;
    private final int idQuantity;

    DiskSpace(float size, int idQuantity) {
        this.size = size;
        this.idQuantity = idQuantity;
    }

    public float getSize() {
        return size;
    }

    public int getIdQuantity() {
        return idQuantity;
    }

    @NonNull
    @SuppressLint("DefaultLocale")
    public String getFormatStringValue() {
        if (Math.round(size) - size == 0) {
            return String.format("%.0f", size);
        } else {
            return String.format("%.1f", size);
        }
    }
}