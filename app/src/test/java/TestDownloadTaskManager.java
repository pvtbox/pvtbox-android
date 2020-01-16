import android.content.Context;
import android.os.HandlerThread;

import junit.framework.TestCase;

import net.pvtbox.android.db.DataBaseService;
import net.pvtbox.android.service.transport.AvailabilityInfo.AvailabilityInfoConsumer;
import net.pvtbox.android.service.transport.AvailabilityInfo.AvailabilityInfoSupplierFile;
import net.pvtbox.android.service.transport.AvailabilityInfo.AvailabilityInfoSupplierPatch;
import net.pvtbox.android.service.transport.Connectivity.ConnectivityService;
import net.pvtbox.android.service.transport.Data.DataSupplier;
import net.pvtbox.android.service.transport.Downloads.DownloadManager;
import net.pvtbox.android.service.transport.Downloads.DownloadTask;
import net.pvtbox.android.tools.FileTool;
import net.pvtbox.android.tools.PatchTool;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;

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
@SuppressWarnings("ALL")
public class TestDownloadTaskManager extends TestCase {
    private Context context;
    private HandlerThread handlerThread;
    private ConnectivityService connectivity;
    private FileTool fileTool;
    private DataBaseService dataBaseService;
    private PatchTool patchTool;
    private DownloadTask.Callback onProgress;

    private AvailabilityInfoSupplierFile infoSupplierFile;
    private AvailabilityInfoSupplierPatch infoSupplierPatch;
    private AvailabilityInfoConsumer infoConsumerFile;
    private AvailabilityInfoConsumer infoConsumerPatch;
    private DataSupplier dataSupplier;

    private DownloadManager manager;

    private DownloadTask task1;
    private DownloadTask task2;
    private DownloadTask task3;
    private DownloadTask task4;
    private DownloadTask task5;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = mock(Context.class);
        handlerThread = mock(HandlerThread.class);
        connectivity = mock(ConnectivityService.class);
        fileTool = mock(FileTool.class);
        dataBaseService = mock(DataBaseService.class);
        patchTool = mock(PatchTool.class);
        onProgress = mock(DownloadTask.Callback.class);

        infoSupplierFile = mock(AvailabilityInfoSupplierFile.class);
        infoSupplierPatch = mock(AvailabilityInfoSupplierPatch.class);
        infoConsumerFile = mock(AvailabilityInfoConsumer.class);
        infoConsumerPatch = mock(AvailabilityInfoConsumer.class);
        dataSupplier = mock(DataSupplier.class);

        manager = new DownloadManager(
                context, connectivity, fileTool, dataBaseService, patchTool,
                false, false);

        manager.availabilityInfoSupplierFile = infoSupplierFile;
        manager.availabilityInfoSupplierPatch = infoSupplierPatch;
        manager.availabilityInfoConsumerFile = infoConsumerFile;
        manager.availabilityInfoConsumerPatch = infoConsumerPatch;
        manager.dataSupplier = dataSupplier;

        task1 = new DownloadTask(
                DownloadTask.Type.File, 100, "file_uuid_1", "object_id_1",
                "event_uuid_1", "name_1", 10 * 1024, "hashsum",
                "path_1", context, connectivity, handlerThread, fileTool,
                null, null, null, onProgress,
                null, null);
        task2 = new DownloadTask(
                DownloadTask.Type.File, 100, "file_uuid_2", "object_id_2",
                "event_uuid_2", "name_2", 10 * 1024, "hashsum",
                "path_2", context, connectivity, handlerThread, fileTool,
                null, null, null, onProgress,
                null, null);
        task3 = new DownloadTask(
                DownloadTask.Type.File, 101, "file_uuid_3", "object_id_3",
                "event_uuid_3", "name_3", 10 * 1024, "hashsum",
                "path_3", context, connectivity, handlerThread, fileTool,
                null, null, null, onProgress,
                null, null);
        task4 = new DownloadTask(
                DownloadTask.Type.File, 100, "file_uuid_4", "object_id_4",
                "event_uuid_4", "name_4", 100 * 1024, "hashsum",
                "path_4", context, connectivity, handlerThread, fileTool,
                null, null, null, onProgress,
                null, null);
        task5 = new DownloadTask(
                DownloadTask.Type.File, 100, "file_uuid_5", "object_id_5",
                "event_uuid_5", "name_5", 50 * 1024, "hashsum",
                "path_5", context, connectivity, handlerThread, fileTool,
                null, null, null, onProgress,
                null, null);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        manager.onDestroy();
    }

    @Test
    public void testDownloadTaskReady() {
        manager.onDownloadTaskReady(task1);
        assertEquals(
                new ArrayList<>(Collections.emptyList()),
                new ArrayList<>(manager.readyDownloadsQueue));
        manager.currentFilesDownloads.put(task1.fileUuid, task1);
        manager.onDownloadTaskReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));
        manager.onDownloadTaskReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskReady(task2);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));
        manager.currentFilesDownloads.put(task2.fileUuid, task2);
        manager.onDownloadTaskReady(task2);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1, task2)),
                new ArrayList<>(manager.readyDownloadsQueue));
        manager.onDownloadTaskReady(task2);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1, task2)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.currentFilesDownloads.put(task3.fileUuid, task3);
        manager.onDownloadTaskReady(task3);
        assertEquals(
                new ArrayList<>(Arrays.asList(task3, task2, task1)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.currentFilesDownloads.put(task4.fileUuid, task4);
        manager.onDownloadTaskReady(task4);
        assertEquals(
                new ArrayList<>(Arrays.asList(task3, task2, task1, task4)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.currentFilesDownloads.put(task5.fileUuid, task5);
        manager.onDownloadTaskReady(task5);
        assertEquals(
                new ArrayList<>(Arrays.asList(task3, task2, task1, task4, task5)),
                new ArrayList<>(manager.readyDownloadsQueue));
        manager.onDownloadTaskReady(task5);
        assertEquals(
                new ArrayList<>(Arrays.asList(task3, task2, task1, task4, task5)),
                new ArrayList<>(manager.readyDownloadsQueue));
    }

    @Test
    public void testDownloadTaskNotReady() {
        manager.onDownloadTaskNotReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.currentFilesDownloads.put(task1.fileUuid, task1);
        manager.onDownloadTaskNotReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskNotReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));
        manager.onDownloadTaskNotReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskReady(task1);
        manager.currentFilesDownloads.put(task2.fileUuid, task2);
        manager.onDownloadTaskNotReady(task2);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskReady(task2);
        manager.onDownloadTaskNotReady(task2);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskReady(task2);
        manager.onDownloadTaskNotReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList(task2)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskNotReady(task2);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));
    }

    @Test
    public void testStartNextTask() {
        manager.currentFilesDownloads.put(task1.fileUuid, task1);
        manager.currentFilesDownloads.put(task2.fileUuid, task2);
        manager.currentFilesDownloads.put(task3.fileUuid, task3);
        manager.onDownloadTaskReady(task1);
        manager.onDownloadTaskReady(task2);
        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList(task2)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(task1, manager.currentDownloadTask);

        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList(task2)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(task1, manager.currentDownloadTask);

        manager.onDownloadTaskReady(task3);
        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList(task3, task2)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(task1, manager.currentDownloadTask);

        manager.onDownloadTaskReady(task4);
        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList(task3, task2)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(task1, manager.currentDownloadTask);

        manager.onDownloadTaskNotReady(task2);
        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList(task3)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(task1, manager.currentDownloadTask);

        manager.onDownloadTaskNotReady(task1);
        assertNull(manager.currentDownloadTask);
        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(task3, manager.currentDownloadTask);

        manager.onDownloadTaskReady(task1);
        manager.onDownloadTaskReady(task2);
        manager.onDownloadTaskNotReady(task3);
        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList(task2)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(task1, manager.currentDownloadTask);

        manager.onDownloadTaskNotReady(task1);
        manager.onDownloadTaskReady(task3);
        manager.onDownloadTaskReady(task1);
        assertEquals(
                new ArrayList<>(Arrays.asList(task3, task2, task1)),
                new ArrayList<>(manager.readyDownloadsQueue));
        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList(task1, task2)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(task3, manager.currentDownloadTask);
    }

    @Test
    public void testDownloadTaskCompleted() {
        manager.currentFilesDownloads.put(task1.fileUuid, task1);
        manager.onDownloadTaskReady(task1);
        manager.startNextTask();
        manager.onDownloadTaskCompleted(task1);
        assertNull(manager.currentDownloadTask);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(0, manager.currentFilesDownloads.size());

        manager.currentFilesDownloads.put(task2.fileUuid, task2);
        manager.currentFilesDownloads.put(task3.fileUuid, task3);
        manager.onDownloadTaskReady(task2);
        manager.onDownloadTaskReady(task3);
        manager.startNextTask();
        assertEquals(task3, manager.currentDownloadTask);
        manager.onDownloadTaskCompleted(task3);
        manager.startNextTask();
        assertEquals(task2, manager.currentDownloadTask);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(
                new ArrayList<>(Arrays.asList(task2)),
                new ArrayList<>(manager.currentFilesDownloads.values()));

        manager.currentFilesDownloads.put(task4.fileUuid, task4);
        manager.onDownloadTaskReady(task4);
        manager.onDownloadTaskCompleted(task2);
        manager.onDownloadTaskCompleted(task4);
        assertNull(manager.currentDownloadTask);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.currentFilesDownloads.values()));

        manager.currentFilesDownloads.put(task5.fileUuid, task5);
        manager.onDownloadTaskCompleted(task5);
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(
                new ArrayList<>(Arrays.asList()),
                new ArrayList<>(manager.currentFilesDownloads.values()));
    }

    @Test
    public void testPauseResume() {
        manager.currentFilesDownloads.put(task1.fileUuid, task1);
        manager.currentFilesDownloads.put(task2.fileUuid, task2);
        manager.currentFilesDownloads.put(task3.fileUuid, task3);

        manager.onDownloadTaskReady(task1);
        manager.startNextTask();
        assertEquals(task1, manager.currentDownloadTask);

        manager.pauseInternal();
        assertNull(manager.currentDownloadTask);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(
                new ArrayList<>(Arrays.asList(task2, task1, task3)),
                new ArrayList<>(manager.currentFilesDownloads.values()));

        manager.startNextTask();
        assertNull(manager.currentDownloadTask);

        manager.onDownloadTaskReady(task2);
        assertNull(manager.currentDownloadTask);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1, task2)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskReady(task3);
        manager.onDownloadTaskNotReady(task3);
        manager.startNextTask();
        assertNull(manager.currentDownloadTask);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1, task2)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.onDownloadTaskCompleted(task2);
        manager.startNextTask();
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));
        assertEquals(
                new ArrayList<>(Arrays.asList(task1, task3)),
                new ArrayList<>(manager.currentFilesDownloads.values()));

        manager.onDownloadTaskReady(task3);
        manager.resumeInternal();
        assertEquals(task3, manager.currentDownloadTask);
        assertEquals(
                new ArrayList<>(Arrays.asList(task1)),
                new ArrayList<>(manager.readyDownloadsQueue));

        manager.pauseInternal();
        assertNull(manager.currentDownloadTask);
        manager.onDownloadTaskCompleted(task3);
        manager.onDownloadTaskNotReady(task1);
        manager.startNextTask();
        assertNull(manager.currentDownloadTask);

        manager.resumeInternal();
        manager.startNextTask();
        assertNull(manager.currentDownloadTask);

        manager.onDownloadTaskReady(task1);
        manager.startNextTask();
        assertEquals(task1, manager.currentDownloadTask);
    }
}
