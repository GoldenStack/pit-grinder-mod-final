package boats.jojo.grindbot;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Utils {

    public static double range180(double rot) {
        rot = rot % 360;
        if (rot > 180) {
            rot -= 360;
        }
        return rot;
    }

    public static double fixRotY(double rotY) {
        rotY = rotY % 360;
        while (rotY < 0) {
            rotY = rotY + 360;
        }
        return rotY;
    }

    public static double fixRotX(double rotX) {
        if(rotX > 90) {
            rotX = 90;
        }
        if(rotX < -90) {
            rotX = -90;
        }
        return rotX;
    }

    public static boolean onHypixel() {
        return "hypixel.net".equals(Minecraft.getMinecraft().getCurrentServerData().serverIP);
    }

    public static void setWindowTitle() {
        Display.setTitle("Jojo Grinder - " + Minecraft.getMinecraft().thePlayer.getName());
    }

    public static String compressString(String inputString) {
        byte[] inputBytes = inputString.getBytes(StandardCharsets.UTF_8);

        Deflater deflater = new Deflater();
        deflater.setInput(inputBytes);
        deflater.finish();

        byte[] compressedBytes = new byte[inputBytes.length];
        int compressedLength = deflater.deflate(compressedBytes);
        deflater.end();

        byte[] compressedData = new byte[compressedLength];
        System.arraycopy(compressedBytes, 0, compressedData, 0, compressedLength);

        String compressedString = Base64.getEncoder().encodeToString(compressedData);

        double compressionRatio = (double) compressedString.length() / inputBytes.length;

        if (compressionRatio > 1) {
            GrindBot.LOGGER.warn("compression ratio NOT GOOD: " + compressionRatio);
        }

        return compressedString;
    }

    public static String decompressString(String compressedString) {
        byte[] compressedBytes = Base64.getDecoder().decode(compressedString);

        Inflater inflater = new Inflater();
        inflater.setInput(compressedBytes);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressedBytes.length);
        byte[] buffer = new byte[1024];

        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (DataFormatException e) {
            e.printStackTrace();
        }

        inflater.end();

        byte[] decompressedData = outputStream.toByteArray();

        return new String(decompressedData, StandardCharsets.UTF_8);
    }

}
