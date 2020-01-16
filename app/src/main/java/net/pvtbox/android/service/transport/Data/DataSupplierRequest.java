package net.pvtbox.android.service.transport.Data;

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
class DataSupplierRequest {
    public final boolean isFile;
    public final String nodeId;
    public final String objId;
    public final long offset;
    public final long length;

    public DataSupplierRequest(boolean isFile, String nodeId, String objId, long offset, long length) {
        this.isFile = isFile;
        this.nodeId = nodeId;
        this.objId = objId;
        this.offset = offset;
        this.length = length;
    }
}
