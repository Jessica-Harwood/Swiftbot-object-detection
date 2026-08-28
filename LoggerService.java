import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class LoggerService {

    // Saves details of each encounter to the encounter log file
    public void logEncounter(int encounterCount, long startTime, String imagePath) {
        try (FileWriter writer = new FileWriter("encounter_log.txt", true)) {
            long secondsElapsed = (System.currentTimeMillis() - startTime) / 1000;
            writer.write("Encounter " + encounterCount + "\n");
            writer.write("Time since start: " + secondsElapsed + " seconds\n");
            writer.write("Image saved at: " + imagePath + "\n");
            writer.write("\n");

        } catch (IOException e) {
            System.out.println("Could not write encounter log: " + e.getMessage());
        }
    }

    // Writes the full session log on termination (req 9b, 9c)
    public void writeSessionLog(Mode mode, int totalEncounters, long sessionStart,
                                int modeEncounters, long modeStart,
                                int imageCount, int pauseCount, long totalPauseDuration,
                                List<String> modeHistory) {
        try (FileWriter writer = new FileWriter("session_log.txt", true)) {

            long totalSeconds = (System.currentTimeMillis() - sessionStart) / 1000;
            long modeSeconds  = (System.currentTimeMillis() - modeStart) / 1000;
            long pauseSeconds = totalPauseDuration / 1000;

            writer.write("===== SESSION LOG =====\n");
            // req 9c i - modes executed
            writer.write("Mode used: " + mode + "\n");
            // req 9c ii - execution duration
            writer.write("Total session time (seconds): " + totalSeconds + "\n");
            // req 9c iii - encounter count
            writer.write("Total encounters: " + totalEncounters + "\n");
            // req 9c v - image count
            writer.write("Total images taken: " + imageCount + "\n");
            // req 9c iv - image file path
            writer.write("Images saved in: " + new File("images").getAbsolutePath() + "\n");
            // req 9c vi - pause/resume events and durations
            writer.write("Number of times paused: " + pauseCount + "\n");
            writer.write("Total time paused (seconds): " + pauseSeconds + "\n");
            writer.write("\n");
            // mode change history
            writer.write("--- Mode History ---\n");
            for (String entry : modeHistory) {
                writer.write("  " + entry + "\n");
            }
            writer.write("\n");
            writer.write("--- Mode Breakdown ---\n");
            writer.write("Final mode: " + mode + "\n");
            writer.write("Time in final mode (seconds): " + modeSeconds + "\n");
            writer.write("Encounters in final mode: " + modeEncounters + "\n");
            writer.write("Encounter log: " + new File("encounter_log.txt").getAbsolutePath() + "\n");
            writer.write("=======================\n\n");

            // Display log file contents to user on screen (req 9d)
            System.out.println("Session log saved to: " + new File("session_log.txt").getAbsolutePath());
            System.out.println();
            System.out.println("===== SESSION LOG =====");
            System.out.println("Mode used: " + mode);
            System.out.println("Total session time (seconds): " + totalSeconds);
            System.out.println("Total encounters: " + totalEncounters);
            System.out.println("Total images taken: " + imageCount);
            System.out.println("Images saved in: " + new File("images").getAbsolutePath());
            System.out.println("Number of times paused: " + pauseCount);
            System.out.println("Total time paused (seconds): " + pauseSeconds);
            System.out.println("--- Mode History ---");
            for (String entry : modeHistory) {
                System.out.println("  " + entry);
            }
            System.out.println("--- Mode Breakdown ---");
            System.out.println("Final mode: " + mode);
            System.out.println("Time in final mode (seconds): " + modeSeconds);
            System.out.println("Encounters in final mode: " + modeEncounters);
            System.out.println("Encounter log: " + new File("encounter_log.txt").getAbsolutePath());
            System.out.println("=======================");

        } catch (IOException e) {
            // req 9e - report logging failure
            System.out.println("logfile is unable to save");
        }
    }
}
