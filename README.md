# SwiftBot Object Detection

My Year 1 Software Design & Implementation project at Brunel University of London (November 2025 – March 2026), graded **A\***.

Out of 10 possible tasks, I chose **Detect Object**: programming a university-provided SwiftBot robot to wander around, detect nearby objects with its ultrasound sensor, and react differently depending on the mode the user picks.

## Features

- **Mode selection by QR code:** when the program starts, the user has 5 seconds to scan a QR code with the robot's camera to choose a mode. Invalid codes are rejected and the user is asked to try again.
- **Three behaviour modes:**
  - **Curious:** moves towards or away from the object to keep a 30cm buffer zone, with green underlights.
  - **Scaredy:** blinks red underlights, backs away, turns around and moves off.
  - **Dubious:** randomly picks Curious or Scaredy behaviour.
- **Object detection:** uses the ultrasound sensor to detect objects within 50cm while the robot wanders (blue underlights).
- **Encounter tracking:** takes a photo with the camera at each encounter and records it in `encounter_log.txt`.
- **Session log:** when the program ends, it saves and displays a summary in `session_log.txt`, including the mode used, total time, number of encounters, images taken, pauses and mode changes.
- **User controls:** press the X button to stop the program, pause and resume, or change mode after an encounter.

## How it's built

| File | What it does |
|---|---|
| `Main.java` | Welcome screen, QR code scanning and validation, then starts the controller |
| `Mode.java` | Enum for the three modes: `CURIOUS`, `SCAREDY`, `DUBIOUS` |
| `Controls.java` | Main logic: wandering, object detection, mode behaviours, lights, buttons, pause/resume |
| `LoggerService.java` | Writes the encounter log and session log files |

## Challenges and what I learnt

- **Uneven wheels:** the left wheel was weaker than the right, so the robot drifted when it should have gone straight. I adjusted the code to give the left wheel more power so the robot moved evenly.
- **Reliable sensor readings:** I added a short cooldown between encounters so the same object wasn't counted twice.
- **Object-oriented design:** splitting the program into separate classes made it easier to test and debug each part.
- Managing a four-month project on my own taught me to plan my time and meet deadlines, and I presented the finished project to my lecturers.

## Tech used

Java (object-oriented) · SwiftBot API · ultrasound sensor · camera / QR codes · file I/O

## How to run

This program runs on a SwiftBot robot with the university's SwiftBot API library. Compile the four `.java` files with the SwiftBot API on the classpath, run `Main`, then scan a QR code that reads `Curious SwiftBot`, `Scaredy SwiftBot` or `Dubious SwiftBot`.
