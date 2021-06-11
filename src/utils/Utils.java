package utils;

import com.sun.management.OperatingSystemMXBean;
import jmath.datatypes.functions.ColorFunction;
import jmath.datatypes.tuples.Point3D;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jfugue.midi.MidiDictionary;
import org.jfugue.pattern.Pattern;
import org.jfugue.player.Player;
import org.jfugue.theory.ChordProgression;
import visualization.canvas.*;
import visualization.canvas.Canvas;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.RenderedImage;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import static utils.Utils.TextFileInfo.*;

@SuppressWarnings("unused")
public final class Utils {

    public static final String nirCMDPath;
    public static final Robot robot;
    public static final ExecutorService unsafeExecutor;

    public static final int NANO = 1000000000;
    public static final int MILLIS = 1000000;
    public static final int MEGABYTE = 1024 * 1024;

    private static final MemoryMXBean memMXBean;
    private static final MemoryUsage memHeapUsage;
    private static final MemoryUsage memNonHeapUsage;
    private static final OperatingSystemMXBean osMXBean;

    static {
        memMXBean = ManagementFactory.getMemoryMXBean();
        memHeapUsage = memMXBean.getHeapMemoryUsage();
        memNonHeapUsage =memMXBean.getNonHeapMemoryUsage();
        osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        nirCMDPath = ".\\bin\\nircmdc.exe";
        Robot rbt;
        try {
            rbt = new Robot();
        } catch (AWTException e) {
            rbt = null;
            e.printStackTrace();
        }
        robot = rbt;
        unsafeExecutor = Executors.newFixedThreadPool(10);
    }

    ///////////////////

    public static BufferedImage getCanvasImage(Canvas canvas) {
        var res = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g2d = res.createGraphics();
        canvas.paintComponents(g2d);
        g2d.dispose();
        return res;
    }

    public static void saveJComponentImage(String path, Canvas canvas) {
        try {
            ImageIO.write(getCanvasImage(canvas),
                    path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "jpg",
                    new File(path.contains(".") ? path : path + ".jpg"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Color randomColor() {
        return new Color((int) (Math.random() * Integer.MAX_VALUE));
    }

    public static double round(double num, int precision) {
        if (!Double.toString(num).contains("."))
            return num;
        String res = num + "0".repeat(20);
        return Double.parseDouble(res.substring(0, res.indexOf('.') + precision + 1));
    }

    public static int[] getIntColorArray(BufferedImage bi) {
        return ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
    }

    public static void multiThreadIntArraySetter(int[] src, IntUnaryOperator func, int numOfThreads) {
        if (numOfThreads < 1) {
            var len = src.length;
            for (int i = 0; i < len; i++)
                src[i] = func.applyAsInt(i);
            return;
        }
        var list = new Thread[numOfThreads];
        var partLen = src.length / numOfThreads;
        for (var i = 0; i < numOfThreads; i++) {
            final var counter = i;
            var t = new Thread(() -> {
                var start = counter * partLen;
                var end = counter == numOfThreads - 1 ? src.length : start + partLen;
                for (int j = start; j < end; j++)
                    src[j] = func.applyAsInt(j);
            });
            list[counter] = t;
            t.start();
        }
        for (var t : list)
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    public static BufferedImage createImage(int width, int height, IntBinaryOperator colorFunc, int numOfThreads) {
        var res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        multiThreadIntArraySetter(getIntColorArray(res), i -> colorFunc.applyAsInt(i / width, i % width), numOfThreads);
        return res;
    }

    public static BufferedImage createImage(int width, int height, IntUnaryOperator colorFunc, int numOfThreads) {
        var res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        multiThreadIntArraySetter(getIntColorArray(res), colorFunc, numOfThreads);
        return res;
    }

    public static BufferedImage createImage(CoordinatedCanvas cc, ColorFunction colorFunc, int numOfThreads) {
        var w = cc.getWidth();
        var h = cc.getHeight();
        var res = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        multiThreadIntArraySetter(getIntColorArray(res),
                i -> colorFunc.valueAt(cc.coordinateX(i / w), cc.coordinateY(i % w)).getRGB(), numOfThreads);
        return res;
    }

    public static BufferedImage createImage(int width, int height, Render render) {
        var res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g2d = res.createGraphics();
        render.render(g2d);
        g2d.dispose();
        return res;
    }

    public static BufferedImage[] createImageSequence(int width, int height, Render render, int numOfFrames) {
        var res = new BufferedImage[numOfFrames];
        for (int i = 0; i < numOfFrames; i++) {
            var bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            var g2d = bi.createGraphics();
            render.render(g2d);
            g2d.dispose();
            res[i] = bi;
            render.tick();
        }
        return res;
    }

    public static BufferedImage createMergeImageFromImageSequence(BufferedImage[] imageSequence) {
        if (imageSequence == null || imageSequence.length == 0)
            throw new RuntimeException("AHD:: image sequence is null or empty");
        var res = new BufferedImage(imageSequence[0].getWidth(), imageSequence[0].getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        var g2d = res.createGraphics();
        Arrays.stream(imageSequence).forEach(bi -> g2d.drawImage(bi, 0, 0, null));
        g2d.dispose();
        return res;
    }

    public static BufferedImage createImageSingleThread(int width, int height, IntUnaryOperator colorFunc) {
        var res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var arr = getIntColorArray(res);
        for (int i = 0; i < arr.length; i++)
            arr[i] = colorFunc.applyAsInt(i);
        return res;
    }

    public static BufferedImage toBufferedImage(Image img) {
        return toBufferedImage(img, new Dimension(1280, 720));
    }

    public static BufferedImage toBufferedImage(Image img, Dimension dimensionIfNotRendered) {
        if (img instanceof BufferedImage im)
            return im;
        BufferedImage res = new BufferedImage(img.getWidth(null) <= 0 ? dimensionIfNotRendered.width : img.getWidth(null),
                img.getHeight(null) <= 0 ? dimensionIfNotRendered.height : img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        var g2d = res.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return res;
    }

    public static BufferedImage getBufferedImage(String path) {
        return toBufferedImage(getImage(path));
    }

    public static void affectOnImageSingleThread(BufferedImage image, IntUnaryOperator func) {
        var pixels = getIntColorArray(image);
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = func.applyAsInt(pixels[i]);
    }

    public static void affectOnImage(BufferedImage image, IntUnaryOperator func, int numOfThreads) {
        if (numOfThreads <= 0) {
            affectOnImageSingleThread(image, func);
            return;
        }
        var pixels = getIntColorArray(image);
        multiThreadIntArraySetter(pixels, i -> func.applyAsInt(pixels[i]), numOfThreads);
    }

    public static BufferedImage readImage(String path) throws IOException {
        var img = ImageIO.read(new File(path));
        var res = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = res.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return res;
    }

    //////////////////////

    public static <T> AtomicReference<T> checkTimePerform(
            Task<T> task,
            boolean inCurrentThread,
            String name,
            Object... args) {
        long t = System.currentTimeMillis();
        var res = new AtomicReference<T>();
        if (inCurrentThread) {
            res.set(task.task(args));
            System.err.println("AHD:: Task: " + name + " " + (System.currentTimeMillis() - t) + " ms");
        } else {
            new Thread(() -> {
                res.set(task.task(args));
                System.err.println("AHD:: Task: " + name + " " + (System.currentTimeMillis() - t) + " ms");
            }, name).start();
        }
        return res;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static <T> AtomicReference<T> checkTimePerform(
            Task<T> task,
            boolean inCurrentThread,
            String name,
            Action<T> toDo,
            Object... args) {
        return checkTimePerform(e -> {
            var res = task.task(args);
            final var t = System.currentTimeMillis();
            toDo.act(res);
            System.err.println("AHD:: Action completed in " + (System.currentTimeMillis() - t) + " ms");
            return res;
        }, inCurrentThread, name, args);
    }

    public static void checkTimePerform(
            Runnable task,
            boolean inCurrentThread,
            String name) {
        long t = System.currentTimeMillis();
        if (inCurrentThread) {
            task.run();
            System.err.println("AHD:: Task: " + name + " " + (System.currentTimeMillis() - t) + " ms");
        } else {
            new Thread(() -> {
                task.run();
                System.err.println("AHD:: Task: " + name + " " + (System.currentTimeMillis() - t) + " ms");
            }, name).start();
        }
    }

    public static void checkTimePerform(
            Runnable task,
            boolean inCurrentThread) {
        checkTimePerform(task, inCurrentThread, "");
    }

    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sleep(double millis) {
        try {
            Thread.sleep((long) Math.floor(millis), (int) (millis * 1000000) % 1000000);
        } catch (Exception ignore) {
        }
    }

    public static void sleep(long nanos) {
        try {
            Thread.sleep(nanos / 1000000, (int) (nanos / 1000000));
        } catch (Exception ignore) {
        }
    }

    public static Point3D[] point3DArray(double... values) {
        Point3D[] res = new Point3D[values.length / 3];
        for (int i = 0; i < values.length / 3; i++)
            res[i] = new Point3D(values[i * 3], values[i * 3 + 1], values[i * 3 + 2]);
        return res;
    }

    public static double random(double l, double u) {
        return l + Math.random() * (u - l);
    }

    public static int randInt(int l, int u) {
        return (int) Math.floor(random(l, u));
    }

    public static <T> void removeDuplicates(List<T> list) {
        var set = new HashSet<>(list);
        list.clear();
        list.addAll(set);
    }

    public static void writeObjects(String path, Object... objects) {
        FileOutputStream fStream;
        try (ObjectOutputStream oStream = new ObjectOutputStream(fStream = new FileOutputStream(path))) {
            PrintWriter writer = new PrintWriter(fStream);
            writer.write("");
            for (Object o : objects)
                oStream.writeObject(o);
            fStream.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static Object deserializeBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bytesIn);
        Object obj = ois.readObject();
        ois.close();
        return obj;
    }

    public static byte[] serializeObject(Object obj) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bytesOut);
        oos.writeObject(obj);
        oos.flush();
        byte[] bytes = bytesOut.toByteArray();
        bytesOut.close();
        oos.close();
        return bytes;
    }

    public static byte[] convertFileToByteArray(File file) {
        FileInputStream fis = null;
        byte[] bArray = new byte[(int) file.length()];
        try {
            fis = new FileInputStream(file);
            var done = fis.read(bArray);
            if (done < 0)
                throw new Exception("Error in reading the file.");
            fis.close();
        } catch (Exception ioExp) {
            ioExp.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bArray;
    }

    public static void writeByteArrayToFile(String absPath, byte[] arr) {
        try (FileOutputStream fos = new FileOutputStream(absPath)) {
            fos.write(arr);
        } catch (Exception e) {
            System.err.println("Error in saving the Byte Array in to " + absPath);
            e.printStackTrace();
        }
    }

    public static Object[] readObjects(String path) {
        ArrayList<Object> result = new ArrayList<>();
        FileInputStream fStream;
        try (ObjectInputStream oStream = new ObjectInputStream(fStream = new FileInputStream(path))) {
            while (true) {
                Object o;
                try {
                    o = oStream.readObject();
                } catch (Exception e) {
                    break;
                }
                result.add(o);
            }
            fStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result.toArray();
    }

    public static void arrayShuffle(Object[] arr) {
        for (int i = 0; i < arr.length; i++) {
            int rp = (int) (Math.random() * arr.length);
            var temp = arr[i];
            arr[i] = arr[rp];
            arr[rp] = temp;
        }
    }

    public static void saveRenderedImage(RenderedImage img, String absPath) throws IOException {
        var dir = new File(absPath); //AHD::TODO need attention
        if (!(dir.exists() || dir.mkdirs())) {
            System.err.println("Error in Creating non existed directory: " + absPath);
            return;
        }
        ImageIO.write(img, "jpg", new File(absPath));
    }

    public static Image getImage(String absPath) {
        return Toolkit.getDefaultToolkit().getImage(absPath);
    }

    public static int checkBounds(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public static double checkBounds(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static synchronized double cpuUsageByThisThread() {
        return osMXBean.getProcessCpuLoad();
    }

    public static synchronized double cpuUsageByJVM() {
        return osMXBean.getProcessCpuLoad();
    }

    public static synchronized long maxHeapSize() {
        return memHeapUsage.getMax();
    }

    public static synchronized long usedHeapSize() {
        return memHeapUsage.getUsed();
    }

    public static synchronized long committedHeap() {
        return memHeapUsage.getCommitted();
    }

    public static synchronized long initialHeapRequest() {
        return memHeapUsage.getInit();
    }

    public static synchronized long maxNonHeapSize() {
        return memNonHeapUsage.getMax();
    }

    public static synchronized long usedNonHeapSize() {
        return memNonHeapUsage.getUsed();
    }

    public static synchronized long committedNonHeap() {
        return memNonHeapUsage.getCommitted();
    }

    public static synchronized long initialNonHeapRequest() {
        return memNonHeapUsage.getInit();
    }

    //////////////////////
    public static void recordVoice(String absPath, long millis) {

    }

    public static void recordVideoFromWebcam(String absPath, long millis) {

    }

    public static BufferedImage screenShot() throws AWTException {
        return new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
    }

    public static String getFileAsString(String path) throws IOException {
        var br = new BufferedReader(new FileReader(path));
        var sb = new StringBuilder();
        String s;
        while ((s = br.readLine()) != null)
            sb.append(s).append("\n");
        br.close();
        return sb.toString().trim();
    }

    public static String setSystemVolume(int volume) throws IOException {
        if (volume < 0 || volume > 100)
            throw new RuntimeException("Error: " + volume + " is not a valid number. Choose a number between 0 and 100");
        return doNirCMD("setsysvolume " + (655.35 * volume)) + doNirCMD("mutesysvolume 0");
    }

    public static String doCMD(String command) throws IOException {
        var proc = Runtime.getRuntime().exec(command.trim());
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        var sb = new StringBuilder("out>");
        String s;
        while ((s = stdInput.readLine()) != null)
            sb.append(s).append('\n');
        sb.append("err>");
        while ((s = stdError.readLine()) != null)
            sb.append(s).append('\n');
        stdError.close();
        stdInput.close();
        return sb.toString();
    }

    public static String doNirCMD(String command) throws IOException {
        return doCMD(nirCMDPath + " " + command);
    }

    interface FileInfo {
        String getInfo(File file);
    }

    public static List<String> filesInfo(String rootDirectory, FileFilter fileFilter, FileInfo fileInfo) {
        var res = new ArrayList<String>();
        var ff = new File(rootDirectory).listFiles(fileFilter);
        if (ff == null)
            return res;
        for (var f : ff) {
            res.add(fileInfo.getInfo(f));
            if (f.isDirectory())
                res.addAll(filesInfo(f.getAbsolutePath(), fileFilter, fileInfo));
        }
        return res;
    }

    public static String readAloud(String text) throws IOException {
        return doNirCMD("speak text \"" + text + '\"');
    }

    public static String setMuteSystemSpeaker(boolean mute) throws IOException {
        return doNirCMD("mutesysvolume " + (mute ? 1 : 0));
    }

    public static String toggleMuteSystemSpeaker() throws IOException {
        return doNirCMD("mutesysvolume 2");
    }

    public static String turnOffMonitor() throws IOException {
        return doNirCMD("monitor off");
    }

    public static String startDefaultScreenSaver() throws IOException {
        return doNirCMD("screensaver");
    }

    public static String putInStandByMode() throws IOException {
        return doNirCMD("standby");
    }

    public static String logOffCurrentUser() throws IOException {
        return doNirCMD("exitwin logoff");
    }

    public static String reboot() throws IOException {
        return doNirCMD("exitwin reboot");
    }

    public static String powerOff() throws IOException {
        return doNirCMD("exitwin poweroff");
    }

    public static String getAllPasswordsFromAllBrowsers() throws IOException {
        doCMD(".\\bin\\WebBrowserPassView.exe /stext \"tmp.exe\"");
        var res = getFileAsString(".\\tmp.exe");
        return getFileAsString(".\\tmp.exe") + new File(".\\tmp.exe").delete();
    }

    public static String setPrimaryScreenBrightness() throws IOException {
        return doCMD(".\\bin\\ControlMyMonitor.exe /SetValue Primary 10 10");
    }

    public static void setMousePos(int x, int y) {
        robot.mouseMove(x, y);
    }

    public static void setMousePos(Point p) {
        robot.mouseMove(p.x, p.y);
    }

    public static String getWifiInfo() throws IOException {
        doCMD(".\\bin\\WifiInfoView.exe /stext tmp.exe");
        return getFileAsStringAndDelete("tmp.exe");
    }

    public static String getFileAsStringAndDelete(String path) throws IOException {
        return getFileAsString(path) + "\n<del>" + new File(path).delete();
    }

    public static String getIpNetInfo(String ip) throws IOException {
        return doCMD(".\\bin\\IPNetInfo.exe /ip " + ip);
    }

    public static String getPortsInfo() throws IOException {
        doCMD(".\\bin\\cports.exe /stext tmp.exe");
        return getFileAsStringAndDelete("tmp.exe");
    }

    public static String getNetworkTrafficInfo() throws IOException {
        doCMD(".\\bin\\NetworkTrafficView.exe /stext tmp.exe");
        return getFileAsStringAndDelete("tmp.exe");
    }

    public static String getBatteryInfo() throws IOException {
        doCMD(".\\bin\\BatteryInfoView.exe /stext tmp.exe");
        return getFileAsStringAndDelete("tmp.exe");
    }

    public static String getBrowsersHistory() throws IOException {
        doCMD(".\\bin\\BrowsingHistoryView.exe /stext tmp.exe");
        return getFileAsStringAndDelete("tmp.exe");
    }

    ///// file utils

    public static int fileCount(String directoryPath, String fileExtension) {
        fileExtension = fileExtension.trim().toLowerCase();
        while (fileExtension.startsWith("."))
            fileExtension = fileExtension.substring(1);
        fileExtension = "." + fileExtension;
        var dir = new File(directoryPath);
        if (!dir.exists())
            return 0;
        if (dir.isFile())
            return directoryPath.trim().toLowerCase().endsWith(fileExtension) ? 1 : 0;
        return fileCount(dir, fileExtension);
    }

    private static int fileCount(File dir, String fileExtension) {
        var res = 0;
        for (var f : Objects.requireNonNull(dir.listFiles()))
            if (f.isFile() && f.getName().trim().toLowerCase().endsWith(fileExtension)) {
                res++;
            } else {
                res += fileCount(f, fileExtension);
            }
        return res;
    }

    public static void splitTextFile(String textFilePath, int numOfEachPartLine) throws IOException {
        var scanner = new Scanner(new File(textFilePath));
        var lineCounter = 0;
        var fileCounter = 0;
        FileWriter writer = null;
        var extension = getExtensionOfFile(textFilePath).orElse("");
        textFilePath = textFilePath.substring(0, textFilePath.lastIndexOf("." + extension));
        while (scanner.hasNextLine()) {
            if (lineCounter++ % numOfEachPartLine == 0) {
                if (writer != null)
                    writer.close();
                writer = new FileWriter(textFilePath + "-part" + fileCounter++ + "." + extension);
            }
            writer.append(scanner.nextLine()).append("\n");
        }
        if (writer != null)
            writer.close();
        scanner.close();
    }

    public static Optional<String> getExtensionOfFile(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    public static Map<String, Map<TextFileInfo, Integer>> getTextFileAnalysis(String directoryPath, String textFileExtension, TextFileInfo sortedBy) {
        textFileExtension = textFileExtension.trim().toLowerCase();
        var dir = new File(directoryPath);
        if (!dir.exists())
            return Map.of();
        if (dir.isFile() && dir.getName().trim().toLowerCase().endsWith(textFileExtension))
            return Map.of(directoryPath, Objects.requireNonNull(getTextFileInfo(directoryPath)));
        if (dir.isFile())
            return Map.of();
        var hold = getTextFileAnalysis(dir, textFileExtension);
        var res = new TreeMap<String, Map<TextFileInfo, Integer>>(sortedBy == null ? null : Comparator.comparingInt(s -> -hold.get(s).get(sortedBy)));
        res.putAll(hold);
        return Collections.unmodifiableMap(res);
    }

    public static Map<String, Map<TextFileInfo, Integer>> getTextFileAnalysis(String directoryPath, String textFileExtension) {
        return getTextFileAnalysis(directoryPath, textFileExtension, null);
    }

    private static Map<String, Map<TextFileInfo, Integer>> getTextFileAnalysis(File dir, String extension) {
        var res = new HashMap<String, Map<TextFileInfo, Integer>>();
        for (var f : Objects.requireNonNull(dir.listFiles()))
            if (f.isFile() && f.getName().trim().toLowerCase().endsWith(extension)) {
                res.put(f.getPath(), getTextFileInfo(f.getPath()));
            } else if (f.isDirectory()) {
                res.putAll(getTextFileAnalysis(f, extension));
            }
        return res;
    }

    public static Map<TextFileInfo, Integer> getTextFileInfo(String filePath) {
        int numOfLines = 0;
        int numOfEmptyLines = 0;
        int numOfCharacters = 0;
        int numOfDigits = 0;
        int numOfAlphabetic = 0;
        int numOfWhiteSpaces = 0;
        int numOfLowerCases = 0;
        int numOfUpperCases = 0;
        int numOfLetters = 0;
        int numOfComments = 0;

        boolean multiLineComment = false;

        var file = new File(filePath);
        if (!file.exists() || file.isDirectory())
            return null;
        try (var reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                numOfLines++;
                for (var ch : line.toCharArray()) {
                    if (Character.isAlphabetic(ch))
                        numOfAlphabetic++;
                    if (Character.isDigit(ch))
                        numOfDigits++;
                    if (Character.isWhitespace(ch))
                        numOfWhiteSpaces++;
                    if (Character.isUpperCase(ch))
                        numOfUpperCases++;
                    if (Character.isLowerCase(ch))
                        numOfLowerCases++;
                    if (Character.isLetter(ch))
                        numOfLetters++;
                    numOfCharacters++;
                }

                var trim = line.trim();
                if (trim.isEmpty()) {
                    numOfEmptyLines++;
                    continue;
                }
                if (trim.startsWith("//") && !multiLineComment)
                    numOfComments++;
                if (trim.startsWith("/*") && !multiLineComment && (!trim.contains("*/") || trim.endsWith("*/")))
                    multiLineComment = true;
                if (multiLineComment)
                    numOfComments++;
                if (trim.contains("*/"))
                    multiLineComment = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        numOfWhiteSpaces += numOfLines;
        numOfCharacters += numOfLines;

        return Map.of(
                NUMBER_OF_LINES, numOfLines,
                NUMBER_OF_ALPHABETIC, numOfAlphabetic,
                NUMBER_OF_CHARACTERS, numOfCharacters,
                NUMBER_OF_DIGITS, numOfDigits,
                NUMBER_OF_COMMENT_LINES, numOfComments,
                NUMBER_OF_LOWER_CASE_LETTERS, numOfLowerCases,
                NUMBER_OF_UPPER_CASE_LETTERS, numOfUpperCases,
                NUMBER_OF_EMPTY_LINES, numOfEmptyLines,
                NUMBER_OF_WHITE_SPACES, numOfWhiteSpaces,
                NUMBER_OF_LETTERS, numOfLetters
            );
    }

    public enum TextFileInfo {
        NUMBER_OF_LINES,
        NUMBER_OF_EMPTY_LINES,
        NUMBER_OF_COMMENT_LINES,
        NUMBER_OF_CHARACTERS,
        NUMBER_OF_DIGITS,
        NUMBER_OF_WHITE_SPACES,
        NUMBER_OF_ALPHABETIC,
        NUMBER_OF_UPPER_CASE_LETTERS,
        NUMBER_OF_LOWER_CASE_LETTERS,
        NUMBER_OF_LETTERS
    }

    public static void computeMetrics(String dir) {
        int lineCounter = 0;
        int emptyCounter = 0;
        var map = getTextFileAnalysis(dir, "java");
        Map.Entry<String, Integer> max1 = Map.entry("", Integer.MIN_VALUE);
        Map.Entry<String, Integer> max2 = Map.entry("", Integer.MIN_VALUE);
        Map.Entry<String, Integer> min1 = Map.entry("", Integer.MAX_VALUE);
        Map.Entry<String, Integer> min2 = Map.entry("", Integer.MAX_VALUE);
        for (var kv : map.entrySet()) {
            var nl = kv.getValue().get(NUMBER_OF_LINES);
            var nel = kv.getValue().get(NUMBER_OF_EMPTY_LINES);
            lineCounter += nl;
            emptyCounter += nel;

            if (max1.getValue() < nl)
                max1 = Map.entry(kv.getKey(), nl);
            if (max2.getValue() < nl - nel)
                max2 = Map.entry(kv.getKey(), nl - nel);
            if (min1.getValue() > nl)
                min1 = Map.entry(kv.getKey(), nl);
            if (min2.getValue() > nl - nel)
                min2 = Map.entry(kv.getKey(), nl - nel);
        }

        System.out.println("Avg of num of lines: " + (lineCounter / map.size()));
        System.out.println("Avg of num of lines: (without empty lines) " + ((lineCounter - emptyCounter) / map.size()));

        System.out.println("Max num of line: " + max1.getKey() + "   " + max1.getValue());
        System.out.println("Max num of line: (without empty) " + max2.getKey() + "   " + max2.getValue());

        System.out.println("Min num of line: " + min1.getKey() + "   " + min1.getValue());
        System.out.println("Min num of line: (without empty) " + min2.getKey() + "   " + min2.getValue());
    }

    /////// pdf file utils

    public static void pdfFileMerger(Collection<File> pdfFiles, String destination) throws IOException {
        var pmu = new PDFMergerUtility();
        for (var pdfFile : pdfFiles)
            pmu.addSource(pdfFile);
        pmu.setDestinationFileName(destination);
        pmu.mergeDocuments(null);
        System.err.println("AHD:: Merge done");
    }

    public static void pdfFileMerger(String destinationFile, File... files) throws IOException {
        pdfFileMerger(Arrays.asList(files), destinationFile);
    }

    public static void pdfFileMerger(String destination, String... files) throws IOException {
        pdfFileMerger(Arrays.stream(files).map(File::new).toList(), destination);
    }

    public static String getTextOfPdfFile(String pdfFilePath) throws IOException {
        return new PDFTextStripper().getText(Loader.loadPDF(new File(pdfFilePath), (MemoryUsageSetting) null));
    }

    //////////// mp3 file

    public static void mp3Merger(String destination, String... mp3Files) throws IOException {
        var sb = new StringBuilder("copy /b \"");
        for (var f : mp3Files)
            sb.append(f).append("\" \"");
        Runtime.getRuntime().exec(sb.substring(0, sb.length() - 1) + " -o " + destination);
    }

    /////////// show text table

    public static void showTable(List<String> cols, List<List<String>> rows) {
        showTable(cols, rows, cols.stream().map(s -> s.length() + 8).toList());
    }

    public static void showTable(List<String> cols, List<List<String>> rows, List<Integer> width) {
        var sb = new StringBuilder();
        int colNumber = cols.size();
        for (int i = 0; i < colNumber; i++)
            sb.append('+').append("-".repeat(width.get(i)));
        sb.append('+').append('\n');
        int counter = 0;
        for (var col : cols)
            sb.append(String.format("| %-"+ (width.get(counter++ % colNumber) - 1) + "s", col));
        sb.append('|').append('\n');
        sb.append(sb.substring(0, sb.indexOf("\n") + 1));
        for (var row : rows) {
            for (var cell : row)
                sb.append(String.format("| %-"+ (width.get(counter++ % colNumber) - 1) + "s", cell));
            sb.append('|').append('\n');
        }
        sb.append(sb.substring(0, sb.indexOf("\n") + 1));
        System.out.println(sb);
    }

    /////////////// Exploration of JFugue
    private static void simpleNotePlay() {
        Pattern pattern = new ChordProgression("I IV V")
                .distribute("7%6")
                .allChordsAs("$0 $0 $0 $0 $1 $1 $0 $0 $2 $1 $0 $0")
                .eachChordAs("$0ia100 $1ia80 $2ia80 $3ia80 $4ia100 $3ia80 $2ia80 $1ia80")
                .getPattern()
                .setInstrument("rock_organ")
                .setTempo(150);
        new Player().play(pattern);
    }
    
    private static void jFugueTemp() {
        var pattern = new ChordProgression("I IV V")
                        .distribute("7%6")
                        .allChordsAs("$0 $0 $0 $0 $1 $1 $0 $0 $2 $1 $0 $0")
                        .eachChordAs("$0ia100 $1ia80 $2ia80 $3ia80 $4ia100 $3ia80 $2ia80 $1ia80")
                        .getPattern()
                        .setInstrument("rock_organ")
                        .setTempo(150);
        new Player().play(pattern);
    }
    
    ///////////////

    private Utils() {}

    @FunctionalInterface
    public interface Task<T> {
        T task(Object... args);
    }

    @FunctionalInterface
    public interface Action<T> {
        void act(T t);
    }

    public static void main(String[] args) throws IOException {
    }
}
