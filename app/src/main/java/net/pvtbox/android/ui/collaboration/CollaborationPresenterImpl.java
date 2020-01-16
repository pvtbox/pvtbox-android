package net.pvtbox.android.ui.collaboration;

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

public class CollaborationPresenterImpl implements CollaborationPresenter {


    private final String selfMail;
    private boolean isOwner;

    public CollaborationPresenterImpl(String email) {
        selfMail = email;
    }

    @Override
    public void setIsOwner(boolean isOwner) {
        this.isOwner = isOwner;
    }

    @Override
    public boolean isOwner() {
        return isOwner;
    }

    @Override
    public String getSelfMail() {
        return selfMail;
    }
}
