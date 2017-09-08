package com.hesky.ewbfcudawatchdog;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class Main {
    private static final Logger LOG = getLogger(Main.class);
    private static final Properties PARAMS = new Properties();
    private static Duration TOTAL_TIME;
    private static Path statsFilePath = new File("stats.txt").toPath();

    static {
        try {
            PARAMS.load(Main.class.getClassLoader().getResourceAsStream("params.properties"));
        }
        catch (IOException e) {
            LOG.error("Wrong file name", e);
            System.exit(1);
        }
    }

    public static Map<String, String> readStats(Path path) throws IOException {
        List<String> stats = Files.readAllLines(path);
        Map<String, String> map = new HashMap<>();
        for (String stat : stats) {
            String[] pv = stat.split("=");
            map.put(pv[0], pv[1]);
        }
        return map;
    }

    public static void writeStats(Path path, Map<String, String> stats) throws IOException {
        List<String> statStrings = new ArrayList<>();
        stats.forEach((p, v) -> statStrings.add(p + "=" + v));
        Files.deleteIfExists(path);
        Files.write(path, statStrings, StandardOpenOption.CREATE);
    }

    public static Properties getParams() {
        return PARAMS;
    }

    private static String get(String propertyName) {
        return PARAMS.getProperty(propertyName);
    }

    public static void main(String[] args) throws URISyntaxException, IOException {
        Map<String, String> stats = readStats(statsFilePath);
        final LocalDateTime startDateTime = LocalDateTime.now();
        TOTAL_TIME = Duration.parse(stats.get("totalTime"));
        long sleepDurationMillis = Long.parseLong(get("sleepDuration"));
        double sleepDurationSecs = Long.parseLong(get("sleepDuration")) / 1000d;
        Path logFilePath = Paths.get(get("fileName"));
        LocalDateTime currentDateTime = LocalDateTime.now();
        Duration duration = Duration.between(startDateTime, currentDateTime);
        LOG.info("===========WATCHDOG FOR EWBF\'s CUDA ZEC MINER v{}===========", get("programVersion"));
        LOG.info("Got file with path {}.  Starting program...", logFilePath.toString());
        byte i = 2, j = 0;
        exitLabel:
        while (true) {
            LOG.info("--------------NEW ITERATION--------------");
            Duration prevDuration = duration;
            duration = Duration.between(startDateTime, currentDateTime);
            TOTAL_TIME = TOTAL_TIME.plusMillis(duration.toMillis() - prevDuration.toMillis());
            if (i == 2) {
                printTime(duration);
                LOG.info("TODAY SHUTDOWNS - {}. TOTAL SHUTDOWNS - {}. LAST IN {}", stats.get("todayShutdowns"), stats.get("totalShutdowns"), stats
                        .get("lastShutdownDateTime"));
                i = 0;
            }
            if (j == 20) {
                try {
                    stats.put("totalTime", TOTAL_TIME.toString());
                    if (!stats.get("todayDate").equalsIgnoreCase(currentDateTime.toLocalDate().toString())) {
                        stats.put("todayDate", currentDateTime.toLocalDate().toString());
                        stats.put("todayShutdowns", "0");
                    }
                    writeStats(statsFilePath, stats);
                }
                catch (IOException e) {
                    LOG.warn("Cannot write to file.", e);
                }
                j = 0;
            }
            LOG.info("Starting check out file: {}.", logFilePath);
            try {
                if (isHangingUp(logFilePath)) {
                    LOG.warn("Miner is hanging! Killing process {}", get("ewbfZecMinerProcessName"));
                    try {
                        if (killProcess(get("ewbfZecMinerProcessName")) && moveFile(logFilePath)) {
                            do {
                                try {
                                    if (get("needRestart").equals("0")) {

                                        boolean isClaymoreEthMinerActive;
                                        isClaymoreEthMinerActive = killProcess(get("claymoreETHMinerProcessName"));
                                        TimeUnit.MILLISECONDS.sleep(sleepDurationMillis / 10);  // waiting for program closing
                                        if (isClaymoreEthMinerActive) {
                                            startApp(get("claymoreETHMinerProcessName"), get("claymoreEthMinerDirectory"), get
                                                    ("claymoreEthMinerParams"));
                                        }
                                        startApp(get("ewbfZecMinerProcessName"), get("ewbfZecMinerDirectory"), get("ewbfZecMinerParams"));
                                        TimeUnit.MILLISECONDS.sleep(sleepDurationMillis * 2); //waiting for new logs from miner
                                        if (isHangingUp(logFilePath)) {
                                            if (killProcess(get("ewbfZecMinerProcessName")) && moveFile(logFilePath)) {
                                                if (isClaymoreEthMinerActive) {
                                                    killProcess(get("claymoreETHMinerProcessName"));
                                                }
                                                saveStats(stats);
                                                break exitLabel;
                                            }
                                        } else {
                                            LOG.info("Miners restart were successfully");
                                        }
                                    } else {
                                        saveStats(stats);
                                        break exitLabel;
                                    }
                                }
                                catch (IOException e) {
                                    LOG.warn("Shutdown process cannot be executed. Attempt to try again.", e);
                                }
                            } while (true);
                        } else {
                            LOG.warn("File {} was not moved", logFilePath.toString());
                        }
                    }
                    catch (IOException | InterruptedException e) {
                        LOG.error("Process was not killed. ", e);
                    }
                } else {
                    LOG.info("Miner hanging is not detected");
                }
            }
            catch (IOException e) {
                LOG.warn("Checking out is interrupted. ", e);
            }
            try {
                LOG.info("Sleeping for {} seconds", sleepDurationSecs);
                TimeUnit.MILLISECONDS.sleep(sleepDurationMillis);
            }
            catch (InterruptedException e) {
                LOG.warn("Waiting is interrupted. Program is  executing now.", e);
            }
            LOG.info("Sleep is over. Resume program.");
            currentDateTime = LocalDateTime.now();
            i++;
            j++;
        }
        LOG.info("========EXIT=========");
    }

    private static void saveStats(Map<String, String> stats) throws IOException {
        LocalDateTime currentDateTime = LocalDateTime.now();
        LOG.info("Starting reboot computer...");
        stats.put("totalTime", TOTAL_TIME.toString());
        stats.put("totalShutdowns", Long.toString(Long.parseLong(stats.get("totalShutdowns")) + 1));
        if (stats.get("todayDate").equals(currentDateTime.toLocalDate().toString())) {
            stats.put("todayShutdowns", Long.toString(Long.parseLong(stats.get("todayShutdowns")) + 1));
        } else {
            stats.put("todayShutdowns", "1");
            stats.put("todayDate", currentDateTime.toLocalDate().toString());
        }
        stats.put("lastShutdownDateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        writeStats(statsFilePath, stats);
        rebootComputer(20);
        LOG.info("Shutdown process has been executed.");
    }

    public static void startApp(String processName, String processDirectory, String... commandLineAttribs) throws IOException {
        StringBuilder command = new StringBuilder(String.format("cd \"%s\" && \"%s\"", processDirectory, processName));
        if (commandLineAttribs != null) {
            for (String commandLineAttrib : commandLineAttribs) {
                command.append(" ").append(commandLineAttrib);
            }
        }
        LOG.info("Starting {}", command);
        new ProcessBuilder("cmd.exe", "/c", command.toString()).start();
    }

    private static void printTime(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() - (60 * hours);
        LOG.info("Time: {}:{}\nTotal time: {} days {} hours {} minutes", (hours < 10 ? "0" + hours : hours), (minutes < 10 ? "0" + minutes :
                        minutes),
                TOTAL_TIME.toDays(), TOTAL_TIME.toHours() - TOTAL_TIME.toDays() * 24, TOTAL_TIME.toMinutes() - TOTAL_TIME.toHours() *
                        60);
    }

    public static void rebootComputer(int delay) throws IOException {
        Runtime.getRuntime().exec("SHUTDOWN /r /t " + delay + " /f /c  \"ZecMiner is working wrong.\" ");
    }

    public static boolean killProcess(String name) throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process killProcess = runtime.exec(String.format("TASKKILL /IM \"%s*\" /F /T", name));
        InputStream in = killProcess.getErrorStream();

        if (killProcess.waitFor() == 0) {
            LOG.info("Kill process for {} was successfully finished!", name);
            return true;
        } else {
            LOG.warn("Kill process for {} was finished with errors!", name);
            byte[] bytes = new byte[in.available()];
            in.read(bytes);
            String s = new String(bytes, Charset.forName("cp866"));
            LOG.error("Error message from taskkill: {}", s);
            return false;
        }
    }

    public static boolean moveFile(Path path) {
        try {
            Path logsDirectory = path.getParent().resolve("logs");
            if (!Files.exists(logsDirectory)) {
                Files.createDirectory(logsDirectory);
                LOG.info("Directory {} was created", path.toString());
            }
            Path movedFilePath = logsDirectory.resolve(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm").format(LocalDateTime.now()) + ".txt");
            Files.copy(path, movedFilePath);
            LOG.info("LogFile {} was copied to {}", path.toString(), movedFilePath);
            Files.delete(path);
            LOG.info("LogFile {} was successfully removed.", path.toString());
            return true;
        }
        catch (IOException e) {
            LOG.warn("File " + path.toString() + " was not moved.", e);
            return false;
        }
    }

    public static boolean isHangingUp(Path filePath) throws IOException {
        List<String> fileLines = Files.readAllLines(filePath);
        if (fileLines.size() == 0 || fileLines.get(0).isEmpty()) {
            return false;
        }
        for (int i = 0; i < fileLines.size(); i++) {
            String restartStringCandidate = fileLines.get(i);
            if (restartStringCandidate.equalsIgnoreCase(get("gpu0NotResponse"))
                    || restartStringCandidate.equalsIgnoreCase(get("gpu1NotResponse"))) {
                return true;
            }
            if (restartStringCandidate.equalsIgnoreCase(get("gpu1RestartAttempt"))
                    || restartStringCandidate.equalsIgnoreCase(get("gpu0RestartAttempt"))) {
                for (int j = i + 1; j < fileLines.size(); j++) {
                    String errorStringCandidate = fileLines.get(j);
                    if (errorStringCandidate.equalsIgnoreCase(get("gpu0ThreadExited46Error"))
                            || errorStringCandidate.equalsIgnoreCase(get("gpu1ThreadExited46Error"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}