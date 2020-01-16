package net.pvtbox.android.tools;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.TreeMap;

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
public class CollectionUtils {
    public static long sum(@NonNull Collection<Long> collection) {
        long sum = 0;
        for (Long i : collection) {
            sum += i;
        }
        return sum;
    }

    public static long sumTreeMap(@NonNull Collection<TreeMap<Long, Long>> collection) {
        long sum = 0;
        for (TreeMap<Long, Long> i: collection) {
            for (Long j : i.values()) {
                sum += j;
            }
        }
        return sum;
    }
}
