package com.hesky.ewbfcudawatchdog;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class Main {
    private static final Logger LOG = getLogger(Main.class);

    private static final Properties PROPS = new Properties();

    static {
        try {
            PROPS.load(Main.class.getClassLoader().getResourceAsStream("params.properties"));
        }
        catch (IOException e) {
            LOG.info("Wrong file name. ", e);
            System.exit(-1);
        }
    }

    public static Properties getPROPS() {
        return PROPS;
    }

    public static void main(String[] args) {
        LOG.info("===========EWBF CUDA Miner Watchdog v {}==========", PROPS.getProperty("programVersion"));
        Path path = null;
        path = Paths.get(PROPS.getProperty("fileName"));
        LOG.info("Got file with path {}.  Starting program...", path.toString());
        exitLabel:
        while (true) {
            LOG.info("--------------NEW ITERATION--------------");
            LOG.info("Starting check out file " + path);
            try {
                if (isHangingUp(path)) {
                    LOG.warn("Miner is hanging! Killing process " + PROPS.getProperty("processName"));
                    try {
                        killProcess(PROPS.getProperty("processName"));
                        if (moveFile(path)) {
                            do {
                                try {
                                    LOG.info("Starting reboot computer...");
                                    rebootComputer();
                                    LOG.info("Shutdown process has been executed.");
                                    break exitLabel;
                                }
                                catch (IOException e) {
                                    LOG.warn("Shutdown process cannot be executed. Attempt to try again.", e);
                                }
                            } while (true);
                        } else {
                            LOG.warn("File " + path.toString() + " was not moved");
                        }
                    }
                    catch (IOException | InterruptedException e) {
                        LOG.error("Process cannot be killed. ", e);
                    }
                } else {
                    LOG.info("Miner hanging is not detected");
                }
            }
            catch (IOException e) {
                LOG.warn("Checking out is interrupted. ", e);
            }
            try {
                TimeUnit.SECONDS.sleep(Long.parseLong(PROPS.getProperty("sleepDuration")));
            }
            catch (InterruptedException e) {
                LOG.warn("Waiting is interrupted. Program is  executing now.", e);
            }
            LOG.info("Sleep is over. Resume program.");
        }
        LOG.info("========EXIT=========");
    }

    public static void rebootComputer() throws IOException {
        Runtime runtime = Runtime.getRuntime();
        runtime.exec("SHUTDOWN /r /t 10 /f /c  \"ZecMiner is working wrong.\" ");
    }

    public static void killProcess(String name) throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process killProcess = runtime.exec(String.format("TASKKILL /FI \"IMAGENAME eq %s*\"  /F /T", name));
        if (killProcess.waitFor() == 0) {
            LOG.info(String.format("Kill process for %s was successfully finished!", name));
        } else {
            LOG.warn(String.format("Kill process for %s was finished with errors!", name));
            BufferedReader reader = new BufferedReader(new InputStreamReader(killProcess.getErrorStream(), Charset.forName("cp866")));

            StringBuilder builder = new StringBuilder(50);
            while (reader.ready()) {
                builder.append(reader.readLine());
            }
            reader.close();
            LOG.error(builder.toString());
        }
    }

    public static boolean moveFile(Path path) {
        try {
            Path logsDirectory = path.getParent().resolve("logs");
            if (!Files.exists(logsDirectory)) {
                Files.createDirectory(logsDirectory);
                LOG.info("Directory " + path.toString() + " was created");
            }
            Files.copy(path, logsDirectory.resolve(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm").format(LocalDateTime.now()) + ".txt"));
            LOG.info("LogFile " + path.toString() + " was copied to " + logsDirectory.toString());
            Files.delete(path);
            LOG.info("LogFile " + path.toString() + " was successfully removed.");
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
            if (restartStringCandidate.equalsIgnoreCase(PROPS.getProperty("gpu1RestartAttempt"))
                    || restartStringCandidate.equalsIgnoreCase(PROPS.getProperty("gpu0RestartAttempt"))) {
                for (int j = i + 1; j < fileLines.size(); j++) {
                    String threadErrorStringCandidate = fileLines.get(j);
                    if (threadErrorStringCandidate.equalsIgnoreCase(PROPS.getProperty("gpu0ThreadExited46Error"))
                            || threadErrorStringCandidate.equalsIgnoreCase(PROPS.getProperty("gpu1ThreadExited46Error"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
