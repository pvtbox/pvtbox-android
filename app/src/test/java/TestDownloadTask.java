import android.content.Context;
import android.os.HandlerThread;

import junit.framework.TestCase;

import net.pvtbox.android.service.transport.Downloads.DownloadTask;

import org.junit.Test;

import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.PriorityBlockingQueue;

import static org.junit.Assert.assertNotEquals;
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
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class TestDownloadTask extends TestCase {
    private Context context;
    private HandlerThread handlerThread;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = mock(Context.class);
        handlerThread = mock(HandlerThread.class);
    }


    @Test
    public void testRemoveFromChunks() {
        TreeMap<Long, Long> chunks = new TreeMap<>();
        chunks.put(0L, 131072L);
        chunks.put(196608L, 131072L);
        chunks.put(393216L, 131072L);
        chunks.put(589824L, 131072L);
        DownloadTask.removeFromChunks(262144L, 65536L, chunks);
        assertFalse(chunks.containsKey(327680L));
    }

    @Test
    public void testComparator() {
        DownloadTask task1 = new DownloadTask(
                DownloadTask.Type.File, 100, "fileUuid_1", "objectId_1",
                "eventUuid_1", "name_1", 10 * 1024, "hashsum_1",
                "path_1", context, null, handlerThread,
                null, null, null, null, null,
                null, null);

        assertEquals(task1, task1);
        assertEquals(0, DownloadTask.Comparator.compare(task1, task1));

        DownloadTask task1Copy = new DownloadTask(
                DownloadTask.Type.File, 100, "fileUuid_1", "objectId_1",
                "eventUuid_1", "name_1", 10 * 1024, "hashsum_1",
                "path_1", context, null, handlerThread,
                null, null, null, null, null,
                null, null);
        assertEquals(task1, task1Copy);
        assertEquals(0, DownloadTask.Comparator.compare(task1, task1Copy));
        assertEquals(task1Copy, task1);
        assertEquals(0, DownloadTask.Comparator.compare(task1Copy, task1));

        DownloadTask task2 = new DownloadTask(
                DownloadTask.Type.File, 100, "fileUuid_2", "objectId_2",
                "eventUuid_2", "name_2", 10 * 1024, "hashsum_2",
                "path_2", context, null, handlerThread,
                null, null, null, null, null,
                null, null);
        assertNotEquals(task1, task2);
        assertEquals(-1, DownloadTask.Comparator.compare(task1, task2));
        assertNotEquals(task2, task1);
        assertEquals(1, DownloadTask.Comparator.compare(task2, task1));

        DownloadTask task3 = new DownloadTask(
                DownloadTask.Type.File, 100, "fileUuid_3", "objectId_3",
                "eventUuid_3", "name_3", 50 * 1024, "hashsum_3",
                "path_3", context, null, handlerThread,
                null, null, null, null, null,
                null, null);
        assertNotEquals(task1, task3);
        assertEquals(-1, DownloadTask.Comparator.compare(task1, task3));
        assertNotEquals(task3, task1);
        assertEquals(1, DownloadTask.Comparator.compare(task3, task1));

        DownloadTask task4 = new DownloadTask(
                DownloadTask.Type.File, 10, "fileUuid_4", "objectId_4",
                "eventUuid_4", "name_4", 10 * 1024, "hashsum_4",
                "path_4", context, null, handlerThread,
                null, null, null, null, null,
                null, null);
        assertNotEquals(task1, task4);
        assertEquals(-1, DownloadTask.Comparator.compare(task1, task4));
        assertNotEquals(task4, task1);
        assertEquals(1, DownloadTask.Comparator.compare(task4, task1));

        DownloadTask task5 = new DownloadTask(
                DownloadTask.Type.File, 100, "fileUuid_5", "objectId_5",
                "eventUuid_5", "name_5", 10 * 1024, "hashsum_5",
                "path_5", context, null, handlerThread,
                null, null, null, null, null,
                null, null);
        task5.received = 1024;
        assertNotEquals(task1, task5);
        assertEquals(1, DownloadTask.Comparator.compare(task1, task5));
        assertNotEquals(task5, task1);
        assertEquals(-1, DownloadTask.Comparator.compare(task5, task1));

        Queue<DownloadTask> readyDownloadsQueue = new PriorityBlockingQueue<>(
                100, DownloadTask.Comparator);
        readyDownloadsQueue.add(task1);
        readyDownloadsQueue.add(task2);
        readyDownloadsQueue.add(task3);
        readyDownloadsQueue.add(task4);
        readyDownloadsQueue.add(task5);

        assertEquals(task5, readyDownloadsQueue.poll());
        assertEquals(task1, readyDownloadsQueue.poll());
        assertEquals(task2, readyDownloadsQueue.poll());
        assertEquals(task3, readyDownloadsQueue.poll());
        assertEquals(task4, readyDownloadsQueue.poll());
    }
}
