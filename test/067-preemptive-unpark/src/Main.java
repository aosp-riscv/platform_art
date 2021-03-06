/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class Main {
    private static Unsafe UNSAFE;

    public static void main(String[] args) throws Exception {
        setUp();

        ParkTester test = new ParkTester();

        System.out.println("Test starting");

        test.start();
        UNSAFE.unpark(test);

        System.out.println("GC'ing");
        System.gc();
        System.runFinalization();
        System.gc();

        System.out.println("Asking thread to park");
        test.parkNow = true;

        try {
            // Give some time to the ParkTester thread to honor the park command.
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            System.out.println("Main thread interrupted!");
            System.exit(1);
        }

        if (test.success) {
            System.out.println("Test succeeded!");
        } else {
            System.out.println("Test failed.");
            test.printTimes();
            System.out.println("Value of success = " + test.success);
            Thread.sleep(3000);
            System.out.println("Value of success after sleeping = " + test.success);
            test.printTimes();  // In case they weren't ready the first time.
        }
    }

    /**
     * Set up {@link #UNSAFE}.
     */
    public static void setUp() throws Exception{
        /*
         * Subvert the access check to get the unique Unsafe instance.
         * We can do this because there's no security manager
         * installed when running the test.
         */
        Field field = null;
        try {
            field = Unsafe.class.getDeclaredField("THE_ONE");
        } catch (NoSuchFieldException e1) {
            try {
                field = Unsafe.class.getDeclaredField("theUnsafe");
            } catch (NoSuchFieldException e2) {
                throw new RuntimeException("Failed to find THE_ONE or theUnsafe");
            }
        }
        field.setAccessible(true);
        UNSAFE = (Unsafe) field.get(null);
    }

    private static class ParkTester extends Thread {
        public volatile boolean parkNow = false;
        public volatile boolean success = false;
        public volatile long startTime = 0;
        public volatile long elapsedTime = 0;
        public volatile long finishTime = 0;

        public void run() {
            while (!parkNow) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    // Ignore it.
                }
            }

            long start = System.currentTimeMillis();
            UNSAFE.park(false, 500 * 1000000); // 500 msec
            long elapsed = System.currentTimeMillis() - start;

            if (elapsed > 200) {
                success = false;
                System.out.println("park()ed for " + elapsed + " msec");
            } else {
                success = true;
                // println is occasionally very slow.
                // But output still appears before main thread output.
                System.out.println("park() returned quickly");
                finishTime = System.currentTimeMillis();
                startTime = start;
                elapsedTime = elapsed;
            }
        }

        public void printTimes() {
          System.out.println("Started at " + startTime + "ms, took " + elapsedTime
              + "ms, signalled at " + finishTime + "ms");
        }
    }
}
