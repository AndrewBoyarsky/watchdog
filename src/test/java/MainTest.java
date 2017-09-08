import com.hesky.ewbfcudawatchdog.Main;
import org.junit.*;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class MainTest {
    private static final Logger LOG = getLogger(MainTest.class);

    private static final Path TEST_DIR_PATH = Paths.get("C:/testdir");
    private static final Path TEST_MINER_LOG_FILE_PATH = Paths.get("C:/testdir/miner.log");
    private static final Path LOG_DIRECTORY_PATH = Paths.get("C:/testdir/logs");
    private static Path originalMinerLogFilePath;
    private static Path movedMinerLogFilePath;
    private static List<String> programList = Arrays.asList("Core Temp.exe", "UltraISO.exe");
    private static List<String> programDirectoriesList = Arrays.asList("C:\\Program Files\\Core Temp", "C:\\Program Files (x86)\\UltraISO");
    private static Path restartFilePath;
    private static Path emptyLinesFilePath;
    private static Path correctRestartFilePath;
    private static Path wrongFilePath;
    private static Path emptyFilePath;
    private static Path gpuStuckedFilePath;
    private static Path statsFilePath = new File("stats.txt").toPath();
    private static Properties properties = Main.getParams();

    @BeforeClass
    public static void prepare() throws Exception {
        try {
            originalMinerLogFilePath = new File(MainTest.class.getResource("miner.log").toURI()).toPath();
            wrongFilePath = new File(MainTest.class.getResource("wrong-file.txt").toURI()).toPath();
            restartFilePath = new File(MainTest.class.getResource("restart-file.txt").toURI()).toPath();
            emptyLinesFilePath = new File(MainTest.class.getResource("empty-lines-file.txt").toURI()).toPath();
            correctRestartFilePath = new File(MainTest.class.getResource("correct-restart-file.txt").toURI()).toPath();
            emptyFilePath = new File(MainTest.class.getResource("empty-file.txt").toURI()).toPath();
            gpuStuckedFilePath = new File(MainTest.class.getResource("connection-lost-miner.log").toURI()).toPath();
        }
        catch (URISyntaxException e) {
            LOG.error("Cant load file miner.log from resources", e);
        }
    }

    @After
    public void tearDown() throws Exception {
        TimeUnit.MILLISECONDS.sleep(500);
        if (movedMinerLogFilePath != null) {
            Files.deleteIfExists(movedMinerLogFilePath);
        }
        Files.deleteIfExists(LOG_DIRECTORY_PATH);
        Files.deleteIfExists(TEST_MINER_LOG_FILE_PATH);
        Files.deleteIfExists(TEST_DIR_PATH);
    }

    private void changeStats() throws Exception {
        Map<String, String> stats = Main.readStats(statsFilePath);
        stats.put("totalShutdowns", "0");
        stats.put("todayShutdowns", "0");
        stats.put("todayDate", LocalDate.now().toString());
        Main.writeStats(statsFilePath, stats);
    }

    @Before
    public void setUp() throws Exception {
        TimeUnit.MILLISECONDS.sleep(10);
        if (!Files.exists(TEST_DIR_PATH)) {Files.createDirectory(TEST_DIR_PATH);}
        Files.copy(new File(MainTest.class.getClassLoader().getResource("miner.log").toURI()).toPath(), TEST_MINER_LOG_FILE_PATH);

    }

    private void changeProperties(String zecMinerProcessName, String ethMinerProcessName, Path fileName, long sleepDuration, String needRestart) {
        changeProperties(zecMinerProcessName, ethMinerProcessName, fileName, sleepDuration, needRestart, "", "", programDirectoriesList.get(1),
                programDirectoriesList.get(0));
    }

    private void changeProperties(String zecMinerProcessName, String ethMinerProcessName, Path fileName, long sleepDuration, String needRestart,
                                  String ethMinerParams, String zecMinerParams, String ethMinerDirectory, String zecMinerDirectory) {
        properties.setProperty("ewbfZecMinerProcessName", zecMinerProcessName);
        properties.setProperty("fileName", fileName.toString());
        properties.setProperty("sleepDuration", Long.toString(sleepDuration));
        properties.setProperty("claymoreETHMinerProcessName", ethMinerProcessName);
        properties.setProperty("needRestart", needRestart);
        properties.setProperty("claymoreEthMinerParams", ethMinerParams);
        properties.setProperty("ewbfZecMinerParams", zecMinerParams);
        properties.setProperty("claymoreEthMinerDirectory", ethMinerDirectory);
        properties.setProperty("ewbfZecMinerDirectory", zecMinerDirectory);

    }

    private Thread executeMain() throws Exception {
        Thread thread = new Thread(() -> {
            try {
                Main.main(null);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        return thread;
    }

    @Test(timeout = 10000)
    public void testMain() throws Exception {
        changeProperties("Core Temp.exe", "ultraiso", TEST_MINER_LOG_FILE_PATH, 200, "1");
        startApps(1);
        setMovedMinerLogFilePath();
        LocalDateTime currentDateTime = LocalDateTime.now();
        executeMain();
        TimeUnit.MILLISECONDS.sleep(1500);
        cancelShutdown();
        tearDown();
        TimeUnit.MILLISECONDS.sleep(200);
        setUp();
        Map<String, String> stats = Main.readStats(statsFilePath);
        Assert.assertEquals("1", stats.get("totalShutdowns"));
        Assert.assertEquals("1", stats.get("todayShutdowns"));
        Assert.assertEquals(currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), stats.get("lastShutdownDateTime"));
        stats.put("todayDate", "2017-08-20");
        Main.writeStats(statsFilePath, stats);
        startApps(1);
        setMovedMinerLogFilePath();
        currentDateTime = LocalDateTime.now();
        executeMain();
        TimeUnit.MILLISECONDS.sleep(1000);
        cancelShutdown();
        stats = Main.readStats(statsFilePath);
        Assert.assertEquals("1", stats.get("todayShutdowns"));
        Assert.assertEquals("2", stats.get("totalShutdowns"));
        Assert.assertEquals(currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), stats.get("lastShutdownDateTime"));
        changeStats();
    }

    @Test(timeout = 5000)
    public void testResetShutdowns() throws Exception {
        changeProperties("", "", Paths.get(""), 10, "1");
        setMovedMinerLogFilePath();
        Map<String, String> stats = Main.readStats(statsFilePath);
        stats.put("todayShutdowns", "1");
        stats.put("totalShutdowns", "1");
        stats.put("todayDate", LocalDate.now().minusDays(1).toString());
        Main.writeStats(statsFilePath, stats);
        Thread thread = new Thread(() -> {
            try {
                Main.main(null);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        TimeUnit.MILLISECONDS.sleep(1000);
        thread.start();
        TimeUnit.MILLISECONDS.sleep(1000);
        synchronized (Main.class) {
            stats = Main.readStats(statsFilePath);
        }
        thread.stop();
        Assert.assertEquals("1", stats.get("totalShutdowns"));
        Assert.assertEquals("0", stats.get("todayShutdowns"));
        Assert.assertEquals(LocalDate.now().toString(), stats.get("todayDate"));
        changeStats();
    }

    @Test
    public void testStartApp() throws IOException, InterruptedException {
        Main.startApp(programList.get(0), programDirectoriesList.get(0), "");
        TimeUnit.MILLISECONDS.sleep(500);
        Assert.assertTrue(Main.killProcess(programList.get(0)));
    }

    @Test(timeout = 10000)
    public void testRestartMiner() throws Exception {
        changeProperties("Core Temp.exe", "ultraiso", TEST_MINER_LOG_FILE_PATH, 1000, "0");
        startApps(2);
        setMovedMinerLogFilePath();
        Thread thread = executeMain();
        while (true) {
            if (!Files.exists(TEST_MINER_LOG_FILE_PATH)) {
                tearDown();
                TimeUnit.MILLISECONDS.sleep(100);
                setUp();
                break;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        while (thread.isAlive()) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        cancelShutdown();
        changeStats();
    }

    private void cancelShutdown() throws InterruptedException, IOException {
        TimeUnit.SECONDS.sleep(1);
        Process cancelShutdownProcess = Runtime.getRuntime().exec("SHUTDOWN /a");
        Assert.assertEquals(0, cancelShutdownProcess.waitFor());
    }

    private void setMovedMinerLogFilePath() {
        movedMinerLogFilePath = LOG_DIRECTORY_PATH.resolve(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm").format(LocalDateTime.now()) + "" +
                ".txt");
    }

    @Test
    public void testRebootComputer() throws Exception {
        Main.rebootComputer(30);
        cancelShutdown();
    }

    private void startApps(int number) throws Exception {
        for (int i = 0; i < number; i++) {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "cd \"" + programDirectoriesList.get(i) + "\" && \"" + programList.get(i) +
                    "\"");
            Process process = builder.start();
            InputStream in = process.getErrorStream();

            byte[] bytes = new byte[in.available()];
            in.read(bytes);
            LOG.info(bytes.length != 0 ? new String(bytes, Charset.forName("cp866")) : "Program " + programList.get(i) + " in " +
                    programDirectoriesList.get(i) + " was successfully started");
        }
    }

    @Test
    public void testKillProcess() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        Assert.assertFalse(Main.killProcess("Some process.exe"));
        startApps(2);
        TimeUnit.MILLISECONDS.sleep(2000);
        for (String aProgramList : programList) {
            Assert.assertTrue(Main.killProcess(aProgramList));
        }
        for (String aProgramList : programList) {
            Process taskListProcess = runtime.exec("tasklist /FI \"IMAGENAME eq " + aProgramList + "*\"");
            InputStream is = taskListProcess.getInputStream();
            taskListProcess.waitFor();
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            String s = new String(bytes, Charset.forName("cp866"));
            if (s.contains(aProgramList)) {
                Assert.fail("Program " + aProgramList + " was not closed.");
            }
        }
    }

    @Test
    public void testMoveFile() throws Exception {
        Assert.assertTrue(Main.moveFile(TEST_MINER_LOG_FILE_PATH));
        Assert.assertFalse(Files.exists(TEST_MINER_LOG_FILE_PATH));
        Assert.assertTrue(Files.exists(LOG_DIRECTORY_PATH));
        setMovedMinerLogFilePath();
        Assert.assertTrue(Files.exists(movedMinerLogFilePath));
        Assert.assertTrue(Files.size(movedMinerLogFilePath) == Files.size(originalMinerLogFilePath));
        Assert.assertFalse(Main.moveFile(Paths.get("C:/fer/ber.log")));

    }

    @Test
    public void testIsLagging() throws Exception {
        Assert.assertTrue(Main.isHangingUp(originalMinerLogFilePath));
        Assert.assertTrue(Main.isHangingUp(restartFilePath));
        Assert.assertTrue(Main.isHangingUp(gpuStuckedFilePath));
        Assert.assertFalse(Main.isHangingUp(emptyFilePath));
        Assert.assertFalse(Main.isHangingUp(emptyLinesFilePath));
        Assert.assertFalse(Main.isHangingUp(wrongFilePath));
        Assert.assertFalse(Main.isHangingUp(correctRestartFilePath));
    }

}