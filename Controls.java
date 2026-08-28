import swiftbot.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class Controls {

    // ==========================================
    // Fields
    // ==========================================

    // Constants
    private static final double DETECTION_THRESHOLD_CM = 50.0; // brief: detect within 50cm (req 2a)
    private static final double BUFFER_ZONE_CM = 30.0;
    private static final double BUFFER_TOLERANCE_CM = 2.0;
    private static final int WANDER_SPEED = 65;
    private static final int CURIOUS_SPEED = 80;
    private static final int SCAREDY_RETREAT_SPEED = 78;
    private static final int SCAREDY_FORWARD_SPEED = 70;
    private static final int TURN_180_SPEED = 70;
    private static final int TURN_180_DURATION = 1450;
    private static final int RECHECK_WAIT_ITERATIONS = 25; // 25 x 200ms = 5 seconds (req 5h)
    private static final double BUFFER_LOWER_CM = 28.0;   // lower bound for backward movement
    private static final double BUFFER_UPPER_CM = 32.0;   // upper bound for forward movement
    private static final int SCAREDY_BLINK_COUNT = 3;
    private static final int CURIOUS_BLINK_COUNT = 6;
    private static final int ENCOUNTER_COOLDOWN_MS = 500;

    private SwiftBotAPI api;
    private Mode currentMode;
    private boolean running = true;
    private boolean paused = false;

    private int encounterCount = 0;
    private int totalEncounterCount = 0; // never resets - used for session log
    private long startTime;
    private long sessionStartTime; // global start time - never resets
    private long lastEncounterTime;
    private long modeStartTime;
    private int modeEncounterCount = 0;
    private int imageCount = 0;

    // Pause tracking for log (req 9c vi)
    private int pauseCount = 0;
    private long pauseStartTime = 0;
    private long totalPauseDuration = 0;

    private final Random random = new Random();
    private final Scanner scanner = new Scanner(System.in);
    private LoggerService logger;
    private final List<String> modeHistory = new ArrayList<>(); // tracks mode changes for log

    // ==========================================
    // Constructor
    // ==========================================

    public Controls(SwiftBotAPI api, Mode mode) {
        this.api = api;
        this.currentMode = (mode == Mode.DUBIOUS) ? resolveDubiousMode() : mode;
        this.startTime = System.currentTimeMillis();
        this.sessionStartTime = this.startTime;
        this.modeStartTime = System.currentTimeMillis();
        this.lastEncounterTime = System.currentTimeMillis() - 3000;
        this.logger = new LoggerService();
        modeHistory.add("Started as: " + this.currentMode);

        File imagesFolder = new File("images");
        if (!imagesFolder.exists()) {
            imagesFolder.mkdir();
        }
    }

    // Randomly picks Curious or Scaredy for Dubious mode (req 7a)
    private Mode resolveDubiousMode() {
        Mode chosen = (random.nextInt(2) == 0) ? Mode.CURIOUS : Mode.SCAREDY;
        System.out.println("=================================================");
        System.out.println("  [ DUBIOUS MODE - Chose: " + chosen + " ]");
        System.out.println("=================================================");
        System.out.println();
        return chosen;
    }

    // ==========================================
    // Start
    // ==========================================

        public void start() throws Exception {
        System.out.println("=================================================");
        System.out.println("  System entering wandering state...");
        System.out.println("  Active mode: " + currentMode);
        System.out.println("=================================================");
        System.out.println();

        // Press X button on robot to stop the program (req 10a)
        api.enableButton(Button.X, () -> {
            System.out.println();
            System.out.println("=================================================");
            System.out.println("  X button pressed. Stopping program.");
            System.out.println("=================================================");
            System.out.println();
            api.stopMove();
            api.disableUnderlights();
            logger.writeSessionLog(currentMode, totalEncounterCount, sessionStartTime,
                                   modeEncounterCount, modeStartTime,
                                   imageCount, pauseCount, totalPauseDuration, modeHistory);
            running = false;
            // Goodbye message (req 10d)
            System.out.println("=================================================");
            System.out.println("   Goodbye! Thank you for using the            ");
            System.out.println("       Detecting Object System                 ");
            System.out.println("=================================================");
            System.out.println();
        });

        wander();
    }

    // ==========================================
    // Main Wandering Loop
    // ==========================================

        private void wander() throws Exception {
        while (running) {

            // Check if user wants to pause or resume (req 11f)
            checkPause();

            if (paused) {
                api.stopMove();
                sleep(200); // poll sensor every 200ms during wander
                continue;
            }

            // Move the robot and print wandering message (req 4f, 12b i)
            System.out.println("Wandering...");
            doWanderMove();

            // If no object detected for 5 seconds, change direction slightly (req 4e)
            long timeSinceLastEncounter = System.currentTimeMillis() - lastEncounterTime;
            if (timeSinceLastEncounter > 5000 && running) {
                System.out.println();
                System.out.println("-------------------------------------------------");
                System.out.println("  No objects detected for 5 seconds.");
                System.out.println("  Changing direction...");
                System.out.println("-------------------------------------------------");
                System.out.println();
                api.stopMove();
                sleep(1000); // wait 1 second (req 4e)
                doSlightlyDifferentMove();
                lastEncounterTime = System.currentTimeMillis();
            }

            // Check if there is an object in front (req 2a)
            if (isObjectDetected()) {

                long currentTime = System.currentTimeMillis();

                // 500ms cooldown to avoid counting same object twice
                if (currentTime - lastEncounterTime > ENCOUNTER_COOLDOWN_MS) {

                    encounterCount++;
                    modeEncounterCount++;
                    totalEncounterCount++;
                    lastEncounterTime = currentTime;

                    System.out.println();
                    System.out.println("=================================================");
                    System.out.println("  OBJECT DETECTED! Encounter number: " + encounterCount);
                    System.out.println("=================================================");
                    System.out.println();

                    // React based on current mode (req 2b)
                    handleEncounter();

                    // Reset cooldown timer after encounter finishes to prevent immediate re-detection
                    lastEncounterTime = System.currentTimeMillis();

                    // Check if more than 3 objects found in under 5 minutes (req 8a, 8b)
                    long timeElapsed = System.currentTimeMillis() - startTime;
                    if (encounterCount > 3 && timeElapsed < 300000) {
                        askUserWhatToDo();
                    }

                    if (!running) {
                        break;
                    }

                    api.stopMove();
                    System.out.println();
                    System.out.println("-------------------------------------------------");
                    System.out.println("  Resuming wandering...");
                    System.out.println("-------------------------------------------------");
                    System.out.println();                }
            }
        }

        // Clean up when program ends (req 10d)
        api.stopMove();
        api.disableUnderlights();
    }

    // ==========================================
    // Wandering Movement
    // ==========================================

        private void doWanderMove() {
        api.fillUnderlights(new int[]{0, 0, 255}); // blue lights (req 4d)

        int choice = random.nextInt(10);

        if (choice < 3) {
            // Go forward - longer duration reduces mini stops
            api.move(WANDER_SPEED, WANDER_SPEED + 15, 3500);
            sleepInterruptible(3500); // forward movement duration
        } else if (choice < 5) {
            // Turn left
            api.move(-WANDER_SPEED, WANDER_SPEED - 15, 800);
            sleepInterruptible(800); // turn left duration
        } else if (choice < 7) {
            // Turn right
            api.move(WANDER_SPEED , -WANDER_SPEED + 15, 800);
            sleepInterruptible(800); // turn right duration
        } else if (choice < 9) {
            // Curve left - one wheel faster creates gradual arc
            api.move(WANDER_SPEED , WANDER_SPEED - 5, 3000);
            sleepInterruptible(3000); // curve left duration
        } else {
            // Curve right - left wheel boost applied
            api.move(WANDER_SPEED + 15, WANDER_SPEED - 20, 3000);
            sleepInterruptible(3000); // curve right duration
        }

        api.stopMove();
    }

        private void doSlightlyDifferentMove() {
        api.fillUnderlights(new int[]{0, 0, 255});

        // Small random turn then continue forward
        if (random.nextInt(2) == 0) {
            api.move(-WANDER_SPEED - 15, WANDER_SPEED, 400); // slight left with boost
        } else {
            api.move(WANDER_SPEED + 15, -WANDER_SPEED, 400); // slight right with boost
        }
        sleep(450); // slight turn duration
        api.stopMove();

        api.move(WANDER_SPEED , WANDER_SPEED + 15, 700); // left wheel boost
        sleep(750); // forward burst after slight turn
        api.stopMove();
    }

    // ==========================================
    // Object Detection
    // ==========================================

        private boolean isObjectDetected() {
        if (!running) return false;
        try {
            // Take 3 readings to confirm object is really there
            double total = 0;
            int validReadings = 0;
            for (int i = 0; i < 3; i++) {
                sleep(300); // allow sensor to stabilise between readings
                double reading = api.useUltrasound() / 10.0;
                System.out.println("Distance reading " + (i+1) + ": " + Math.round(reading * 10.0) / 10.0 + " cm");
                if (reading <= 100) {
                    total += reading;
                    validReadings++;
                }
            }
            if (validReadings == 0) return false;
            if (validReadings < 2) return false; // require 2 valid readings to reduce false detections
            double distanceCm = total / validReadings;
            System.out.println("Average distance: " + Math.round(distanceCm * 10.0) / 10.0 + " cm");
            if (distanceCm < DETECTION_THRESHOLD_CM) {
                System.out.println("Object detected at " + Math.round(distanceCm * 10.0) / 10.0 + " cm");
                return true;
            }
            return false;

        } catch (Exception e) {
            System.out.println("Ultrasound sensor error: " + e.getMessage());
            return false;
        }
    }

        private double getDistance() throws Exception {
        try {
            // Take 3 readings and return average ignoring false readings above 100cm
            double total = 0;
            int valid = 0;
            for (int i = 0; i < 3; i++) {
                sleep(300); // allow sensor to stabilise between readings
                double reading = api.useUltrasound() / 10.0;
                if (reading <= 100) {
                    total += reading;
                    valid++;
                }
            }
            if (valid == 0) return 999;
            if (valid < 2) return 999; // require at least 2 valid readings
            return Math.round((total / (double) valid) * 10.0) / 10.0; // round to 1 decimal place
        } catch (Exception e) {
            System.out.println("Could not get distance reading: " + e.getMessage());
            return 999;
        }
    }

    // ==========================================
    // Encounter Handling
    // ==========================================

        private void handleEncounter() throws Exception {
        if (currentMode == Mode.CURIOUS) {
            doCuriousBehaviour();
        } else {
            doScaredyBehaviour();
        }
    }

    // ==========================================
    // Curious Mode
    // ==========================================

    // Curious mode - moves to maintain 30cm buffer zone (req 5)
    private void doCuriousBehaviour() throws Exception {
        System.out.println();
        System.out.println("  [ CURIOUS MODE - Encounter " + encounterCount + " ]");
        System.out.println("-------------------------------------------------");
        api.fillUnderlights(new int[]{0, 255, 0}); // green lights (req 5e)

        // Move to buffer zone
        moveToBufferZone();
        if (!running) return;

        // Take photo after completing action (req 5g)
        String imagePath = saveImage();
        logger.logEncounter(encounterCount, sessionStartTime, imagePath);

        // Turn off lights after action (req 5f)
        api.disableUnderlights();

        // Wait 3 seconds then recheck object position (req 5h)
        for (int check = 0; check < 2 && running; check++) {
            // Check running every 200ms during the wait so X button responds immediately
            for (int w = 0; w < RECHECK_WAIT_ITERATIONS && running; w++) {
                sleep(200); // check every 200ms during recheck wait
            }
            if (!running) break;

            double newDistance = getDistance();
            System.out.println("Rechecking object position: " + Math.round(newDistance * 10.0) / 10.0 + " cm");

            // If sensor cannot get a valid reading, object is gone - resume wandering
            if (newDistance == 999) {
                System.out.println("Object no longer detected. Resuming wandering.");
                sleep(1000); // wait 1 second before resuming (req 5i)
                doSlightlyDifferentMove();
                break;
            }

            if (newDistance >= BUFFER_ZONE_CM - BUFFER_TOLERANCE_CM && newDistance <= BUFFER_ZONE_CM + BUFFER_TOLERANCE_CM) {
                // Object has not moved - wait 1 second then move in slightly different direction (req 5j)
                System.out.println("Object has not moved. Resuming wandering.");
                sleep(1000); // wait 1 second before resuming (req 5j)
                doSlightlyDifferentMove(); // move in slightly different direction as per brief
                break;
            }

            // Object has moved - repeat buffer behaviour (req 5i)
            System.out.println("Object has moved. Adjusting position...");
            api.fillUnderlights(new int[]{0, 255, 0});
            moveToBufferZone();

            // Take image after adjustment completes (req 5g)
            String adjustImagePath = saveImage();
            logger.logEncounter(encounterCount, sessionStartTime, adjustImagePath);

            // Blink green lights
            System.out.println("Blinking green under lights.");
            for (int i = 0; i < CURIOUS_BLINK_COUNT; i++) {
                api.fillUnderlights(new int[]{0, 255, 0});
                sleep(300); // blink on duration
                api.disableUnderlights();
                sleep(300); // blink off duration
            }

            api.disableUnderlights();
        }

        api.stopMove();
    }

    // Moves forward or backward until the robot is 30cm from the object (req 5a, 5b, 5c, 5d)
    private void moveToBufferZone() {
        try {
            // Take 3 readings and average ignoring false readings above 100cm
            double total = 0;
            int valid = 0;
            for (int i = 0; i < 3; i++) {
                sleep(300); // allow sensor to stabilise between readings
                double r = api.useUltrasound() / 10.0;
                if (r <= 100) { total += r; valid++; }
            }
            double distToObj = (valid > 0) ? total / valid : 30; // default to buffer zone if all readings invalid
            System.out.println("Average distance to object: " + Math.round(distToObj) + "cm");

            // If object is closer than 30cm move backward continuously until at 30cm (req 5b)
            if (distToObj < BUFFER_LOWER_CM) {
                api.fillUnderlights(new int[]{0, 255, 0});
                System.out.println("Moving backwards.");
                while (api.useUltrasound() / 10.0 < BUFFER_LOWER_CM && running && !paused) {
                    checkPause();
                    if (paused) { api.stopMove(); while (paused) { checkPause(); sleep(200); } }
                    api.move(-CURIOUS_SPEED , -CURIOUS_SPEED - 20, 300); // smoother movement chunks
                }
                api.stopMove();
                api.disableUnderlights(); // brief: stop and turn off underlights (req 5b)
                sleep(500); // brief pause after reaching buffer zone
                return;
            }

            // If object is further than 30cm move forward continuously until at 30cm (req 5d)
            if (distToObj > BUFFER_UPPER_CM) {
                api.fillUnderlights(new int[]{0, 255, 0});
                System.out.println("Moving forward slowly...");
                while (api.useUltrasound() / 10.0 > BUFFER_UPPER_CM && running && !paused) {
                    checkPause();
                    if (paused) { api.stopMove(); while (paused) { checkPause(); sleep(200); } }
                    api.move(CURIOUS_SPEED , CURIOUS_SPEED + 20, 300); // smoother movement chunks
                }
                api.stopMove();
                api.disableUnderlights(); // brief: stop and turn off lights (req 5d)
                sleep(500); // brief pause after reaching buffer zone
                return;
            }

            // Exactly at 30cm - stay still and blink green (req 5c)
            System.out.println("At buffer zone. Blinking green under lights.");
            for (int i = 0; i < CURIOUS_BLINK_COUNT; i++) { // blink for 3 seconds (req 5d)
                api.fillUnderlights(new int[]{0, 255, 0});
                sleep(250); // blink on duration
                api.disableUnderlights();
                sleep(250); // blink off duration
            }

        } catch (Exception e) {
            System.out.println("Could not complete buffer zone movement: " + e.getMessage());
            api.stopMove();
        }

        api.stopMove();
    }

    // ==========================================
    // Scaredy Mode
    // ==========================================

    // Scaredy mode - flashes red and runs away (req 6)
    private void doScaredyBehaviour() throws Exception {
        System.out.println();
        System.out.println("  [ SCAREDY MODE - Encounter " + encounterCount + " ]");
        System.out.println("-------------------------------------------------");
        api.fillUnderlights(new int[]{255, 0, 0}); // red lights (req 6d)

        // Take photo first upon detection before anything else (req 6c)
        String imagePath = saveImage();
        logger.logEncounter(encounterCount, sessionStartTime, imagePath);

        // Then blink red lights before retreating (req 6b, 6e)
        System.out.println("Blinking red under lights.");
        for (int i = 0; i < SCAREDY_BLINK_COUNT; i++) {
            api.fillUnderlights(new int[]{255, 0, 0});
            sleep(300); // blink on duration
            api.disableUnderlights();
            sleep(300); // blink off duration
        }

        // Check for pause before moving backward (req 11f)
        checkPause();
        if (paused) { api.stopMove(); while (paused) { checkPause(); sleep(200); } }

        // Move backward until object is beyond 50cm detection threshold (req 6f)
        System.out.println("Moving backwards.");
        api.fillUnderlights(new int[]{255, 0, 0});
        long retreatStart = System.currentTimeMillis();
        while (running && !paused && (System.currentTimeMillis() - retreatStart) < 4000) {
            checkPause();
            if (paused) { api.stopMove(); while (paused) { checkPause(); sleep(200); } }
            // Take 2 readings and average, ignoring false 121cm wall readings
            double r1 = api.useUltrasound() / 10.0;
	    sleep(300); // allow sensor to stabilise between readings
            double r2 = api.useUltrasound() / 10.0;
            sleep(300); // allow sensor to stabilise between readings
            double dist = 999;
            if (r1 <= 100 && r2 <= 100) dist = (r1 + r2) / 2.0;
            else if (r1 <= 100) dist = r1;
            else if (r2 <= 100) dist = r2;
            if (dist > DETECTION_THRESHOLD_CM) break; // moved far enough away
            api.move(-SCAREDY_RETREAT_SPEED, -SCAREDY_RETREAT_SPEED -20, 300); // left wheel boost
        }
        api.stopMove();

        // Check for pause before turning (req 11f)
        checkPause();
        if (paused) { api.stopMove(); while (paused) { checkPause(); sleep(200); } }

        // Turn 180 degrees after moving backward (req 6g)
        if (!running) { api.stopMove(); api.disableUnderlights(); return; }
        System.out.println("Turning 180 degrees.");
        api.move(TURN_180_SPEED, -TURN_180_SPEED, TURN_180_DURATION); // approximate 180 degree turn
        sleepInterruptible(1550); // 180 degree turn duration
        api.stopMove();
        if (!running) { api.disableUnderlights(); return; }

        // Check for pause before moving forward (req 11f)
        checkPause();
        if (paused) { api.stopMove(); while (paused) { checkPause(); sleep(200); } }

        // Move forward for 3 seconds (req 6f)
        if (!running) { api.stopMove(); api.disableUnderlights(); return; }
        System.out.println("Moving forward.");
        api.move(SCAREDY_FORWARD_SPEED , SCAREDY_FORWARD_SPEED +20, 3000); // left wheel boost to compensate drift
        sleepInterruptible(3100); // move forward 3 seconds (req 6f)
        api.stopMove();

        api.disableUnderlights();
    }

    // ==========================================
    // Multiple Encounters Prompt
    // ==========================================

        private void askUserWhatToDo() {
        System.out.println();
        System.out.println("=================================================");
        System.out.println("  !! MORE THAN 3 ENCOUNTERS IN 5 MINUTES !!");
        System.out.println("=================================================");
        System.out.println("  What would you like to do?");
        System.out.println();
        System.out.println("  [1] Continue Exploring");
        System.out.println("  [2] Change Mode");
        System.out.println("  [3] Terminate Program");
        System.out.println("-------------------------------------------------");
        System.out.println();

        int choice = 0;

        // Keep asking until valid input (req 8d)
        while (choice != 1 && choice != 2 && choice != 3) {
            try {
                choice = scanner.nextInt();
            } catch (Exception e) {
                scanner.nextLine();
            }

            if (choice != 1 && choice != 2 && choice != 3) {
                System.out.println("Invalid input, try again"); // req 8d
            }
        }

        if (choice == 1) {
            // Continue exploring
            System.out.println();
            System.out.println("-------------------------------------------------");
            System.out.println("  Continuing in " + currentMode + " mode.");
            System.out.println("-------------------------------------------------");
            System.out.println();
            encounterCount = 0;
            modeEncounterCount = 0;
            startTime = System.currentTimeMillis();
            modeStartTime = System.currentTimeMillis();

        } else if (choice == 2) {
            // Change mode - no log written here, only on final termination
            System.out.println();
            System.out.println("=================================================");
            System.out.println("  Choose a new mode:");
            System.out.println();
            System.out.println("  [1] Curious SwiftBot");
            System.out.println("  [2] Scaredy SwiftBot");
            System.out.println("  [3] Dubious SwiftBot");
            System.out.println("-------------------------------------------------");
            System.out.println();

            int modeChoice = 0;
            while (modeChoice != 1 && modeChoice != 2 && modeChoice != 3) {
                try {
                    modeChoice = scanner.nextInt();
                } catch (Exception e) {
                    scanner.nextLine();
                    System.out.println("Invalid input, try again");
                }
                if (modeChoice != 1 && modeChoice != 2 && modeChoice != 3) {
                    System.out.println("Invalid input, try again");
                }
            }

            // If Dubious selected again pick randomly once (req 7a)
            if (modeChoice == 1) {
                currentMode = Mode.CURIOUS;
            } else if (modeChoice == 2) {
                currentMode = Mode.SCAREDY;
            } else {
                currentMode = resolveDubiousMode();
            }

            // Record mode change in history BEFORE resetting startTime
            long secondsElapsed = (System.currentTimeMillis() - sessionStartTime) / 1000;
            modeHistory.add("Changed to: " + currentMode + " at " + secondsElapsed + " seconds");

            // Reset counters for new mode
            modeEncounterCount = 0;
            modeStartTime = System.currentTimeMillis();
            encounterCount = 0;
            startTime = System.currentTimeMillis();
            // Reset lastEncounterTime so detection works immediately in new mode
            lastEncounterTime = System.currentTimeMillis() - 3000;

            System.out.println("=================================================");
            System.out.println("  Mode changed to: " + currentMode);
            System.out.println("-------------------------------------------------");
            System.out.println("  Active mode: " + currentMode);
            System.out.println("=================================================");
            System.out.println();

        } else {
            // Terminate
            System.out.println();
            System.out.println("=================================================");
            System.out.println("  Terminating program...");
            System.out.println("=================================================");
            System.out.println();
            api.stopMove();
            api.disableUnderlights();
            logger.writeSessionLog(currentMode, totalEncounterCount, sessionStartTime,
                                   modeEncounterCount, modeStartTime,
                                   imageCount, pauseCount, totalPauseDuration, modeHistory);
            running = false;
            System.out.println("=================================================");
            System.out.println("   Goodbye! Thank you for using the            ");
            System.out.println("       Detecting Object System                 ");
            System.out.println("=================================================");
            System.out.println();
        }
    }

    // ==========================================
    // Pause / Resume
    // ==========================================

        private void checkPause() {
        try {
            while (System.in.available() > 0) {
                char input = (char) System.in.read();

                if ((input == 'p' || input == 'P') && !paused) {
                    paused = true;
                    pauseCount++;
                    pauseStartTime = System.currentTimeMillis();
                    api.stopMove();
                    api.disableUnderlights(); // req 11b
                    // Pause messages (req 11c)
                    System.out.println();
                    System.out.println("=================================================");
                    System.out.println("  System Paused...");
                    System.out.println("  Robot paused. Press R to resume.");
                    System.out.println("=================================================");
                    System.out.println();
                }

                if (input == 'r' || input == 'R') {
                    paused = false;
                    // Track how long it was paused
                    totalPauseDuration += System.currentTimeMillis() - pauseStartTime;
                    // Resume message (req 11e)
                    System.out.println();
                    System.out.println("-------------------------------------------------");
                    System.out.println("  System now resuming...");
                    System.out.println("-------------------------------------------------");
                    System.out.println();
                }
            }
        } catch (Exception e) {
            System.out.println("Error checking input: " + e.getMessage());
        }
    }

    // ==========================================
    // Image Capture
    // ==========================================

        private String saveImage() {
        try {
            imageCount++;
            BufferedImage image = api.takeStill(ImageSize.SQUARE_1080x1080);
            String fileName = "images/encounter_" + imageCount + ".jpg";
            File imageFile = new File(fileName);
            ImageIO.write(image, "jpg", imageFile);
            System.out.println("Image saved: " + imageFile.getAbsolutePath());
            return imageFile.getAbsolutePath();

        } catch (IOException e) {
            System.out.println("Could not save image: " + e.getMessage());
            return "no image saved";
        }
    }

    // ==========================================
    // Sleep Utilities
    // ==========================================

        private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            System.out.println("Sleep interrupted: " + e.getMessage());
        }
    }

        private void sleepInterruptible(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && running) {
            checkPause();
            if (paused) {
                api.stopMove();
                while (paused && running) {
                    checkPause();
                    sleep(100);
                }
            }
            sleep(100);
        }
    }
}
