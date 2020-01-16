package net.pvtbox.android.tools;

import androidx.annotation.NonNull;

import net.pvtbox.android.R;
import net.pvtbox.android.db.DataBaseService;

import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

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
public class SpeedTool {

    private static final long DELAY_UPDATE_SPEED = 1000;
    private static final int LIMIT_SIZE_QUEUE = 10;
    private final DataBaseService dataBaseService;

    private long downloadSum = 0;
    private long uploadSum = 0;

    @NonNull
    private final Queue<Long> queueUploadSpeed = new ConcurrentLinkedQueue<>();
    @NonNull
    private final Queue<Long> queueDownloadSpeed = new ConcurrentLinkedQueue<>();

    private final Timer timer = new Timer("SpeedToolThread", true);
    private long downloadedTotal = 0;
    private long uploadedTotal = 0;


    public SpeedTool(DataBaseService dataBaseService) {
        this.dataBaseService = dataBaseService;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateSpeed();
            }
        }, DELAY_UPDATE_SPEED, DELAY_UPDATE_SPEED);
    }

    public void onDestroy() {
        timer.cancel();
        timer.purge();
    }

    public void addDownloadValue(long value) {
       downloadSum += value;
    }

    public void addUploadValue(long value) {
        uploadSum += value;
    }

    private void updateSpeed() {
        queueDownloadSpeed.add(downloadSum);
        downloadedTotal += downloadSum;
        downloadSum = 0;
        if (queueDownloadSpeed.size() > LIMIT_SIZE_QUEUE) queueDownloadSpeed.poll();
        queueUploadSpeed.add(uploadSum);
        uploadedTotal += uploadSum;
        uploadSum = 0;
        if (queueUploadSpeed.size() > LIMIT_SIZE_QUEUE) queueUploadSpeed.poll();

        update();
    }

    private void update() {
        double downloadSpeed = calculateSpeed(queueDownloadSpeed);
        double uploadSpeed = calculateSpeed(queueUploadSpeed);
        dataBaseService.updateOwnDeviceSpeedAndSize(
                downloadSpeed, uploadSpeed, downloadedTotal, uploadedTotal);
    }

    private double calculateSpeed(@NonNull Queue<Long> queue) {
        long sum = 0;
        for (Long value : queue) {
            sum += value;
        }

        return sum > 0 ? (double) sum / (double) queue.size() : 0.0;
    }


    @NonNull
    public static Speed getSpeed(double value) {
        for (int i = 0; i <= 2; i++) {
            value = value / 1024D;
            if (value < 1024D) {
                switch (i) {
                    case 0:
                        return new Speed(value, R.string.kb_in_s);
                    case 1:
                        return new Speed(value, R.string.mb_in_s);
                    case 2:
                        return new Speed(value, R.string.Gb_in_s);

                }
            }
        }
        return new Speed(value, R.string.Gb_in_s);
    }

    public static class Speed {
        private final double speed;
        private final int idQuantity;

        Speed(double speed, int idQuantity) {
            this.speed = speed;
            this.idQuantity = idQuantity;
        }

        public double getSpeed() {
            return speed;
        }

        public int getIdQuantity() {
            return idQuantity;
        }
    }

}
