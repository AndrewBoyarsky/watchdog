import com.hesky.ewbfcudawatchdog.Main;
import org.junit.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class MainTest {
    private static final Path TEST_DIR_PATH = Paths.get("C:/testdir");
    private static final Path TEST_MINER_LOG_FILE_PATH = Paths.get("C:/testdir/miner.log");
    private static final Path LOG_DIRECTORY_PATH = Paths.get("C:/testdir/logs");
    private static Path originalMinerLogFilePath;
    private static Path movedMinerLogFilePath;
    private static List<String> programList = Arrays.asList("Core Temp.exe");
    private static List<String> programDirectoriesList = Arrays.asList("C:\\Program Files\\Core Temp");

    @BeforeClass
    public static void setUp() throws Exception {
        try {
            originalMinerLogFilePath = new File(MainTest.class.getResource("miner.log").toURI()).toPath();
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
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

    @Before
    public void before() throws Exception {
        Files.createDirectory(TEST_DIR_PATH);
        Files.copy(new File(MainTest.class.getClassLoader().getResource("miner.log").toURI()).toPath(), TEST_MINER_LOG_FILE_PATH);
    }

    @Test
    public void testMain() throws Exception {
        Properties properties = Main.getPROPS();
        properties.setProperty("processName", "Core Temp.exe");
        properties.setProperty("fileName", TEST_MINER_LOG_FILE_PATH.toString());
        startProcesses();
        movedMinerLogFilePath = LOG_DIRECTORY_PATH.resolve(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm").format(LocalDateTime.now()) + "" +
                ".txt");
        Main.main(null);
        TimeUnit.SECONDS.sleep(1);
        Process cancelShutdownProcess = Runtime.getRuntime().exec("SHUTDOWN /a");
        Assert.assertEquals(cancelShutdownProcess.waitFor(), 0);
    }

    @Test
    public void testRebootComputer() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        Main.rebootComputer();
        TimeUnit.SECONDS.sleep(1);
        Process cancelShutdownProcess = runtime.exec("SHUTDOWN /a");
        Assert.assertEquals(cancelShutdownProcess.waitFor(), 0);
    }

    private void startProcesses() throws Exception {
        Runtime runtime = Runtime.getRuntime();
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
        startProcesses();
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < programList.size(); i++) {
            Main.killProcess(programList.get(i));
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
        Main.moveFile(TEST_MINER_LOG_FILE_PATH);
        Assert.assertFalse(Files.exists(TEST_MINER_LOG_FILE_PATH));
        Assert.assertTrue(Files.exists(LOG_DIRECTORY_PATH));
        movedMinerLogFilePath = LOG_DIRECTORY_PATH.resolve(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm").format(LocalDateTime.now()) + "" +
                ".txt");
        Assert.assertTrue(Files.exists(movedMinerLogFilePath));
        Assert.assertTrue(Files.size(movedMinerLogFilePath) == Files.size(originalMinerLogFilePath));

    }

    @Test
    public void testIsHangingUp() throws Exception {
        Assert.assertTrue(Main.isHangingUp(originalMinerLogFilePath));
    }

}