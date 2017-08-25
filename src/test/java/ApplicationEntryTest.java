import com.hesky.ewbfcudawatchdog.ApplicationEntry;
import org.junit.*;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
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

public class ApplicationEntryTest {
    private static final Logger LOG = getLogger(ApplicationEntryTest.class);

    private static final Path TEST_DIR_PATH = Paths.get("C:/testdir");
    private static final Path TEST_MINER_LOG_FILE_PATH = Paths.get("C:/testdir/miner.log");
    private static final Path LOG_DIRECTORY_PATH = Paths.get("C:/testdir/logs");
    private static Path originalMinerLogFilePath;
    private static Path movedMinerLogFilePath;
    private static List<String> programList = Arrays.asList("Core Temp.exe");
    private static List<String> programDirectoriesList = Arrays.asList("C:\\Program Files\\Core Temp");
    private static Path restartFilePath;
    private static Path emptyLinesFilePath;
    private static Path correctRestartFilePath;
    private static Path wrongFilePath;
    private static Path emptyFilePath;
    private static Path gpuStuckedFilePath;
    private static Path statsFilePath = new File("stats.txt").toPath();

    @BeforeClass
    public static void prepare() throws Exception {
        try {
            originalMinerLogFilePath = new File(ApplicationEntryTest.class.getResource("miner.log").toURI()).toPath();
            wrongFilePath = new File(ApplicationEntryTest.class.getResource("wrong-file.txt").toURI()).toPath();
            restartFilePath = new File(ApplicationEntryTest.class.getResource("restart-file.txt").toURI()).toPath();
            emptyLinesFilePath = new File(ApplicationEntryTest.class.getResource("empty-lines-file.txt").toURI()).toPath();
            correctRestartFilePath = new File(ApplicationEntryTest.class.getResource("correct-restart-file.txt").toURI()).toPath();
            emptyFilePath = new File(ApplicationEntryTest.class.getResource("empty-file.txt").toURI()).toPath();
            gpuStuckedFilePath = new File(ApplicationEntryTest.class.getResource("connection-lost-miner.log").toURI()).toPath();
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
        Map<String, String> stats = ApplicationEntry.readStats(statsFilePath);
        stats.put("totalShutdowns", "0");
        stats.put("todayShutdowns", "0");
        stats.put("todayDate", LocalDate.now().toString());
        ApplicationEntry.writeStats(statsFilePath, stats);
    }

    @Before
    public void setUp() throws Exception {
        TimeUnit.MILLISECONDS.sleep(10);
        Files.createDirectory(TEST_DIR_PATH);
        Files.copy(new File(ApplicationEntryTest.class.getClassLoader().getResource("miner.log").toURI()).toPath(), TEST_MINER_LOG_FILE_PATH);
    }

    public void changeProperties(String processName, Path fileName, long sleepDuration) {
        Properties properties = ApplicationEntry.getParams();
        properties.setProperty("processName", processName);
        properties.setProperty("fileName", fileName.toString());
        properties.setProperty("sleepDuration", Long.toString(sleepDuration));
    }

    @Test
    public void testMain() throws Exception {
        changeProperties("Core Temp.exe", TEST_MINER_LOG_FILE_PATH, 10000);
        startApps();
        setMovedMinerLogFilePath();
        LocalDateTime currentDateTime = LocalDateTime.now();
        ApplicationEntry.main(null);
        cancelShutdown();
        tearDown();
        TimeUnit.MILLISECONDS.sleep(200);
        setUp();
        Map<String, String> stats = ApplicationEntry.readStats(statsFilePath);
        Assert.assertEquals("1", stats.get("totalShutdowns"));
        Assert.assertEquals("1", stats.get("todayShutdowns"));
        Assert.assertEquals(currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), stats.get("lastShutdownDateTime"));
        stats.put("todayDate", "2017-08-20");
        ApplicationEntry.writeStats(statsFilePath, stats);
        startApps();
        setMovedMinerLogFilePath();
        currentDateTime = LocalDateTime.now();
        ApplicationEntry.main(null);
        cancelShutdown();
        stats = ApplicationEntry.readStats(statsFilePath);
        Assert.assertEquals("1", stats.get("todayShutdowns"));
        Assert.assertEquals("2", stats.get("totalShutdowns"));
        Assert.assertEquals(currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), stats.get("lastShutdownDateTime"));
        changeStats();
    }

    @Test
    public void testResetShutdowns() throws Exception {
        changeProperties("", Paths.get(""), 10);
        setMovedMinerLogFilePath();
        Map<String, String> stats = ApplicationEntry.readStats(statsFilePath);
        stats.put("todayShutdowns", "1");
        stats.put("totalShutdowns", "1");
        stats.put("todayDate", LocalDate.now().minusDays(1).toString());
        ApplicationEntry.writeStats(statsFilePath, stats);
        Thread thread = new Thread(() -> {
            try {
                ApplicationEntry.main(null);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        TimeUnit.MILLISECONDS.sleep(1000);
        synchronized (ApplicationEntry.class) {
            stats = ApplicationEntry.readStats(statsFilePath);
        }
        thread.stop();
        Assert.assertEquals("1", stats.get("totalShutdowns"));
        Assert.assertEquals("0", stats.get("todayShutdowns"));
        Assert.assertEquals(LocalDate.now().toString(), stats.get("todayDate"));
        changeStats();
    }

    private void cancelShutdown() throws InterruptedException, IOException {
        TimeUnit.SECONDS.sleep(1);
        Process cancelShutdownProcess = Runtime.getRuntime().exec("SHUTDOWN /a");
        Assert.assertEquals(cancelShutdownProcess.waitFor(), 0);
    }

    private void setMovedMinerLogFilePath() {
        movedMinerLogFilePath = LOG_DIRECTORY_PATH.resolve(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm").format(LocalDateTime.now()) + "" +
                ".txt");
    }

    @Test
    public void testRebootComputer() throws Exception {
        ApplicationEntry.rebootComputer(30);
        cancelShutdown();
    }

    private void startApps() throws Exception {
        for (int i = 0; i < programList.size(); i++) {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "cd \"" + programDirectoriesList.get(i) + "\" && \"" + programList.get(i) +
                    "\"");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while (reader.ready()) {
                System.out.println(reader.readLine());
            }
            reader.close();
        }
    }

    @Test
    public void testKillProcess() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        ApplicationEntry.killProcess("Some process.exe");

        startApps();
        for (int i = 0; i < programList.size(); i++) {
            ApplicationEntry.killProcess(programList.get(i));
        }

        Process taskListProcess = runtime.exec("tasklist");
        taskListProcess.waitFor(1, TimeUnit.SECONDS);

        BufferedReader reader2 = new BufferedReader(new InputStreamReader(taskListProcess.getInputStream()));
        while (reader2.ready()) {
            String s = reader2.readLine();
            for (int i = 0; i < programList.size(); i++) {
                if (s.contains(programList.get(i))) {
                    Assert.fail();
                }
            }
        }
        reader2.close();
        Assert.assertTrue(true);
    }

    @Test
    public void testMoveFile() throws Exception {
        Assert.assertTrue(ApplicationEntry.moveFile(TEST_MINER_LOG_FILE_PATH));
        Assert.assertFalse(Files.exists(TEST_MINER_LOG_FILE_PATH));
        Assert.assertTrue(Files.exists(LOG_DIRECTORY_PATH));
        setMovedMinerLogFilePath();
        Assert.assertTrue(Files.exists(movedMinerLogFilePath));
        Assert.assertTrue(Files.size(movedMinerLogFilePath) == Files.size(originalMinerLogFilePath));
        Assert.assertFalse(ApplicationEntry.moveFile(Paths.get("C:/fer/ber.log")));

    }

    @Test
    public void testIsHangingUp() throws Exception {
        Assert.assertTrue(ApplicationEntry.isHangingUp(originalMinerLogFilePath));
        Assert.assertTrue(ApplicationEntry.isHangingUp(restartFilePath));
        Assert.assertTrue(ApplicationEntry.isHangingUp(gpuStuckedFilePath));
        Assert.assertFalse(ApplicationEntry.isHangingUp(emptyFilePath));
        Assert.assertFalse(ApplicationEntry.isHangingUp(emptyLinesFilePath));
        Assert.assertFalse(ApplicationEntry.isHangingUp(wrongFilePath));
        Assert.assertFalse(ApplicationEntry.isHangingUp(correctRestartFilePath));
    }

}