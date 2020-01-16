package net.pvtbox.android.db;

import androidx.annotation.NonNull;

import io.realm.DynamicRealm;
import io.realm.FieldAttribute;
import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;

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
public class DataBaseMigration implements RealmMigration {
    @Override
    public void migrate(@NonNull DynamicRealm realm, long oldVersion, long newVersion) {
        RealmSchema schema = realm.getSchema();

        if (oldVersion < 4) {
            RealmObjectSchema device = schema.get("DeviceRealm");
            if (device == null) return;
            device
                    .addField("isLogoutInProgress", boolean.class, FieldAttribute.REQUIRED)
                    .addField("isWipeInProgress", boolean.class, FieldAttribute.REQUIRED)
                    .transform(obj -> {
                        obj.setBoolean("isLogoutInProgress", false);
                        obj.setBoolean("isWipeInProgress", false);
                    });
        }
        if (oldVersion < 5) {
            RealmObjectSchema device = schema.get("DeviceRealm");
            if (device == null) return;
            device
                    .addField("notificationsCount", long.class, FieldAttribute.REQUIRED)
                    .transform(obj -> obj.setLong("notificationsCount", 0));
        }
    }
}
