import swiftbot.*;
import java.awt.image.BufferedImage;
public class Main {

    public static void main(String[] args) throws Exception {

    	SwiftBotAPI swiftBot = SwiftBotAPI.INSTANCE;

        // ==========================================
        // Welcome Screen
        // ==========================================

        System.out.println("+=================================================+");
        System.out.println("|                                                 |");
        System.out.println("|    DETECT OBJECT SYSTEM  v1.0                   |");
        System.out.println("|    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~          |");
        System.out.println("|    Autonomous Object Detection & Response       |");
        System.out.println("|                                                 |");
        System.out.println("+=================================================+");
        System.out.println();
        System.out.println("=================================================");
        System.out.println("      Welcome to the Detecting Object System     ");
        System.out.println("=================================================");
        System.out.println("  Curious  - Maintains 30cm from object          ");
        System.out.println("  Scaredy  - Runs away from objects               ");
        System.out.println("  Dubious  - Randomly picks a behaviour           ");
        System.out.println("=================================================");
        System.out.println(" Hello Please scan a valid QR code to select a mode.        ");
        System.out.println("  You have 5 seconds to scan...                   ");
        System.out.println("=================================================");
        System.out.println();

        Mode selectedMode = null;
        

        // ==========================================
        // QR Mode Selection Loop
        // ==========================================

        while (selectedMode == null) {

            selectedMode = scanQR(swiftBot);

            if (selectedMode == null) {
                System.out.println("Invalid QR code scanned please try again.");
                System.out.println("You have 5 seconds to scan...");
                System.out.println();
            }
        }

        // ==========================================
        // Mode Confirmation
        // ==========================================

        System.out.println("=================================================");
        System.out.println("  QR Code Accepted.");
        System.out.println("  Mode Confirmed: " + selectedMode);
        System.out.println("=================================================");
        System.out.println();

        // Start Controller
        Controls controller = new Controls(swiftBot, selectedMode);
        controller.start();
    }

    // ==========================================
    // QR Scan Method (5 second window)
    // ==========================================

    private static Mode scanQR(SwiftBotAPI API) {

        long startTime = System.currentTimeMillis();
        boolean text = false;

        while (!text && (System.currentTimeMillis() - startTime) < 5000) {

            try {
                BufferedImage img = API.getQRImage();
                String decodedText = API.decodeQRImage(img);

                System.out.println("QR detected: [" + decodedText + "]"); //says what the QR decoded says

                if (!decodedText.isEmpty()) {
                    text = true;
                    return validateQR(decodedText);
                }

                Thread.sleep(200); //stops camera from overloading

            } catch (Exception e) {
                System.out.println("Camera error, retrying...");
            }
        }

        return null;
    }

    // ==========================================
    // QR Validation
    // ==========================================

    private static Mode validateQR(String qrData) {

        if (qrData == null) {
            return null;
        }

        qrData = qrData.trim(); //should remove space and line breaks

        if (qrData.equalsIgnoreCase("Curious SwiftBot")) {
            return Mode.CURIOUS;
        }

        if (qrData.equalsIgnoreCase("Scaredy SwiftBot")) {
            return Mode.SCAREDY;
        }

        if (qrData.equalsIgnoreCase("Dubious SwiftBot")) {
            return Mode.DUBIOUS;
        }

        return null;
    }
}
