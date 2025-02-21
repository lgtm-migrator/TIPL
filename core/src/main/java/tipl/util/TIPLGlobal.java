package tipl.util;

import ij.ImageJ;
import tipl.util.TIPLMongo.ITIPLUsage;
import tipl.util.TIPLMongo.TIPLUsage;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TIPLGlobal {
    public static final int DEBUG_ALL = 5;
    public static final int DEBUG_GC = 4;
    public static final int DEBUG_MSGS = 3;
    public static final int DEBUG_STATUS = 2;
    public static final int DEBUG_BASIC = 1;
    private static int TIPLDebugLevel = DEBUG_BASIC;
    public static final int DEBUG_OFF = 0;
    public static boolean useDaemonThreads = true;

    private static String usageLoginUsername = "27032014";

    /**
     * so the threads do not need to manually be shutdown
     */
    public static final ThreadFactory daemonFactory = new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = Executors.defaultThreadFactory().newThread(
                    runnable);
            thread.setDaemon(useDaemonThreads);
            return thread;
        }
    };
    final static boolean useApacheForCopy = true;
    /**
     * the current runtime being used (whatever that is)
     */
    public static Runtime curRuntime = Runtime.getRuntime();
    /**
     * number of cores available for processing tasks
     */
    public static int availableCores = curRuntime.availableProcessors();
    public static int IJmode = ImageJ.EMBEDDED;
    /**
     * the maximum number of cores allowed to be reading files simultaneously
     */
    static protected int maximumParallelReaders = 1;
    /**
     * The number of readers available on the given node
     */
    static protected AtomicInteger availableReaders = null;
    // ImageJ portion of the code
    protected static ImageJ IJcore = null;
    // Usage portion of the code
    protected static ITIPLUsage tuCore = null;

    /**
     * returns the level (see enumeration above for more on the levels
     *
     * @return
     */
    public static int getDebugLevel() {
        return TIPLDebugLevel;
    }

    /**
     * returns of the debug level is above DEBUG_OFF
     *
     * @return
     */
    public static boolean getDebug() {
        return TIPLDebugLevel >= DEBUG_MSGS;
    }

    public static void setDebug(int debugVal) {
        assert (debugVal >= 0);
        assert (debugVal <= 5);
        TIPLDebugLevel = debugVal;
    }

    /**
     * Run (encourage to run) the garbage collector and provide more feedback on the current memory status (makes debugging easier)
     */
    public static void runGC() {
        long memBefore = getFreeMB();
        System.gc();
        try {
            Thread.sleep(10);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Sleep during GC was aborted");
        }
        long memAfter = getFreeMB();
        if (TIPLDebugLevel >= DEBUG_GC)
            System.out.println("GC Run: " + (memAfter - memBefore) + " MB freed, " + memAfter + "MB avail of " + getTotalMB() + "MB");
    }

    public static long getFreeMB() {
        return curRuntime.freeMemory() / (1024 * 1024);
    }

    public static long getTotalMB() {
        return curRuntime.totalMemory() / (1024 * 1024);
    }

    public static long getUsedMB() {
        return getTotalMB() - getFreeMB();
    }

    /**
     * simple method to get an executor service, eventually allows this to be changed to another / distributed option
     *
     * @param numOfCores
     * @return
     */
    public static ExecutorService requestSimpleES(int numOfCores) {
        return Executors.newFixedThreadPool(Math.min(TIPLGlobal.availableCores, numOfCores), TIPLGlobal.daemonFactory);
    }

    public static ExecutorService requestSimpleES() {
        return requestSimpleES(TIPLGlobal.availableCores);
    }

    public static ExecutorService getIOExecutor() {
        return requestSimpleES(requestAvailableReaderCount());
    }

    /**
     * A factory to create a new parser
     *
     * @author mader
     *
     */
    public static interface ArgumentParserFactory {
        public ArgumentParser getParser(String[] args);
    }
    /**
     * The factory to make a new argument parser (meant to be replaced 
     */
    public static ArgumentParserFactory defaultAPFactory = new ArgumentParserFactory() {
        @Override
        public ArgumentParser getParser(String[] args) {
            return ArgumentParser.CreateArgumentParser(args);
        }
    };
    public static ArgumentParser activeParser(String[] args) {
        return activeParser(defaultAPFactory.getParser(args));
    }

    public static ArgumentParser activeParser(String rawArgs) {
        return activeParser(rawArgs.split("\\s"));
    }

    /**
     * parser which actively changes local, maxcores, maxiothread and other TIPL wide parameters
     *
     * @param sp input argumentparser
     * @return
     */
    public static ArgumentParser activeParser(ArgumentParser sp) {
        sp.createNewLayer("Global Settings");
        TImgTools.getStorage().setScratchDirectory(sp.getOptionPath("@localdir",
                TImgTools.getStorage().getScratchDirectory(), "Directory to save local data to"));

        TImgTools.getStorage().setUseScratch(sp.getOptionBoolean("@local", TImgTools.getStorage()
                .getUseScratch(), "Load image data from local filesystems"));

        TIPLGlobal.availableCores = sp.getOptionInt("@maxcores",
                TIPLGlobal.availableCores,
                "Number of cores/threads to use for processing");

        TIPLGlobal.maximumParallelReaders = sp.getOptionInt("@maxiothread",
                TIPLGlobal.maximumParallelReaders,
                "Number of cores/threads to use for read/write operations");
        TIPLGlobal.setDebug(sp.getOptionInt("@debug",
                TIPLGlobal.getDebugLevel(),
                "Debug level from " + DEBUG_OFF + " to " + DEBUG_ALL));

        TIPLGlobal.usageLoginUsername=sp.getOptionString("@usagelogin",
                TIPLGlobal.usageLoginUsername,"Login for the usage logging system");

        System.setProperty("java.awt.headless", "" + sp.getOptionBoolean("@headless", isHeadless(), "Run TIPL in headless mode"));
        sp.createNewLayer("Application Settings");

        return sp;
    }

    /**
     * A parser designed for jython that returns the script name as a parameter (if needed)
     * @param args the argument array
     */
    public static ArgumentParser jythonParser(String[] args) {
        args[0]="-script="+args[0];
        return activeParser(args);
    }

    /**
     * Get the username for the login to the usage database
     * @return the username
     */
    public static String getUsageLogin() {
        if(usageLoginUsername.equalsIgnoreCase("Medebach"))
            return null; // if it returns null skip the entire usage authentication
        return usageLoginUsername;
    }

    /**
     * Is TIPLGlobal running headless currently
     * @return
     */
    public static boolean isHeadless() {
        return java.awt.GraphicsEnvironment.isHeadless();
    }

    /**
     * Force headless status
     */
    public static void forceHeadless() {
        try {
            Field defaultHeadlessField = java.awt.GraphicsEnvironment.class.getDeclaredField("defaultHeadless");
            defaultHeadlessField.setAccessible(true);
            defaultHeadlessField.set(null,Boolean.FALSE);
            Field headlessField = java.awt.GraphicsEnvironment.class.getDeclaredField("headless");
            headlessField.setAccessible(true);
            headlessField.set(null,Boolean.TRUE);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    /**
     * shutdown an executor service and wait for everything to finish.
     *
     * @param inPool
     */
    public static void waitForever(ExecutorService inPool) {
        inPool.shutdown();
        try {
            inPool.awaitTermination(100, TimeUnit.DAYS);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException(inPool + " executorservice crashed:" + e.getMessage());
        }
    }

    static public int getMaximumReaders() {
        return maximumParallelReaders;
    }

    static public void setMaximumReaders(int newMax) {
        assert (newMax > 0);
        maximumParallelReaders = newMax;
    }

    static public boolean requestReader() {
        if (availableReaders == null) {
            availableReaders = new AtomicInteger(maximumParallelReaders);
        }
        int coreCount = availableReaders.decrementAndGet();
        if (coreCount < 0) {
            returnReader();
            return false;
        }
        return true;
    }

    static public int requestAllReaders() {
        if (availableReaders == null) {
            availableReaders = new AtomicInteger(maximumParallelReaders);
        }
        return availableReaders.getAndSet(0);
    }

    static public int requestAvailableReaderCount() {
        int rCount = requestAllReaders();
        //
        for (int i = 0; i < rCount; i++) returnReader();
        return rCount;
    }

    /**
     * try and get a reader and repeat until it is gotten
     *
     * @return whether or not it was successful
     */
    static public boolean waitForReader() {
        while (!requestReader()) {
            if (TIPLGlobal.getDebug()) System.out.println("Requesting Reader:" + availableReaders.get());
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Interrupted while waiting for reader!");
                return false;
            }
        }
        return true;
    }

    static public void returnReader() {
        availableReaders.incrementAndGet();
    }


    /**
     * Utility Function Section
     */
    @Deprecated
    public static void copyFile(final TypedPath sourceFile, final TypedPath destFile) {
        sourceFile.copyTo(destFile);
    }
    @Deprecated
    public static void copyFile(final String sourceFile, final String destFile) {
        copyFile(TIPLStorageManager.openPath(sourceFile),TIPLStorageManager.openPath(destFile));
    }

    @Deprecated
    public static boolean DeleteFile(final String file) {
        return TIPLStorageManager.openPath(file).delete();
    }


    public static void DeleteTempAtFinish(final TypedPath delName) {
        DeleteTempAtFinish(delName,false);
    }
    /**
     * A function to register the current filename as a temporary file that
     * should be deleted when the runtime finishes
     * @param delName the name of the file or folder to delete (works only locally now)
     * @param recursiveDelete recursively delete if it is a directory (automatically false)
     */
    public static void DeleteTempAtFinish(final TypedPath delName, final boolean recursiveDelete) {
        if (!delName.isLocal()) throw new IllegalArgumentException("File must be local for delete function to work:"+delName.summary());

        curRuntime.addShutdownHook(new Thread() {
            public boolean SimpleDeleteFunction(final TypedPath file, final String whoDel) {
                final boolean success = recursiveDelete ? file.recursiveDelete() : file.delete();

                if (!success) {
                    System.out.println(whoDel + "\t" + "ERROR:" + file
                            + " could not be deleted.");
                    return false;
                } else {
                    System.out.println(whoDel + "\t" + file
                            + " successfully deleted.");
                    return true;
                }
            }
            @Override
            public void run() {
                System.out
                        .println("SHUTHOOK\tChecking to ensure that all temp-files have been deleted");
                SimpleDeleteFunction(delName, "SHUTHOOK");
            }
        });
    }

    /**
     * get executor service for tasks
     */
    public static ExecutorService getTaskExecutor() {
        return Executors.newFixedThreadPool(availableCores, daemonFactory);
    }

    /**
     * reserve cores for an operation that other threads can then no longer user
     */
    public static synchronized int reserveCores(final int desiredCores) {
        final int givenCores = (availableCores > desiredCores) ? desiredCores
                : availableCores;
        availableCores -= givenCores;
        return givenCores;
    }

    /**
     * return the cores when a computation is complete
     */
    public static synchronized void returnCores(final int finishedCores) {
        availableCores += finishedCores;
    }
    public static boolean tryOpen(final String filename) { return tryOpen(TIPLStorageManager.openPath(filename));}
    /**
     * Function to try and open an aim file, return true if it is successful
     */
    public static boolean tryOpen(final TypedPath filename) {
        if (filename.length() > 0) {
            System.out.println("Trying to open ... " + filename);
        } else {
            System.out
                    .println("Filename is empty, assuming that it is not essential and proceeding carefully!! ... ");
            return true;
        }

        try {
            // needs to have positive dimensions
            return (TImgTools.ReadTImg(filename).getDim().prod()>0);
        } catch (final Exception e) {
            TIPLGlobal.runGC();
            return false;
        }

    }

    public static ImageJ getIJInstance() {
        if (Boolean.parseBoolean(System.getProperty("java.awt.headless"))) {
            System.err.println("JVM is running in headless mode, IJcore will be returned as null, careful");
            return null;
        }
        if (IJcore == null) IJcore = new ImageJ(IJmode);
        return IJcore;
    }

    public static void closeAllWindows() {
        ij.WindowManager.closeAllWindows();
        for(Window cWind: Window.getWindows()) {
            cWind.dispose();
        }
    }

    /**
     * Get an instance of the TIPLUsage class for monitoring usage of plugins
     * @return the class to use
     */
    public static ITIPLUsage getUsage() {
        if (tuCore == null) {
            if(getUsageLogin()==null) {
                tuCore=isLocalUsage(22515);
            } else {
                tuCore = TIPLUsage.getTIPLUsage();
            }
        }
        return tuCore;
    }

    public static boolean isLocalOk() {
        return true;
    }

    public static ITIPLUsage isLocalUsage(int checkLocalId) {
        if (checkLocalId == 22515) tuCore = new ITIPLUsage() {

            @Override
            public void registerPlugin(String pluginName, String args) {
                System.out.println("USAGE_PLUGIN:" + pluginName + "," + args);

            }

            @Override
            public void registerImage(String imageName, String dim, String info) {
                System.out.println("USAGE_IMAGE:" + imageName + "," + dim + "," + info);

            }

        };
        return tuCore;
    }



}
