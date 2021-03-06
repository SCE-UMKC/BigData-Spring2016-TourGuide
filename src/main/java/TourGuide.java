
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.json.JSONObject;
import org.opencv.core.Core;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by smoeller on 3/5/2016.
 */

public class TourGuide {
    private static String tessdataDir = "C:\\Users\\smoeller\\Documents\\MSCS\\CS5542\\BigData-Spring2016-TourGuide\\BigData-Spring2016-TourGuide";

    private static void TesseractSuccess(String testingDir) {
        Integer success = 0;
        Integer failure = 0;
        File folder = new File("sign_boards/train/" + testingDir);
        List<String> imgList = new ArrayList<String>(Arrays.asList(folder.list()));
        Iterator imgItr = imgList.iterator();
        while (imgItr.hasNext()) {
            String fileName = (String) imgItr.next();
            //System.out.println("Processing " + "sign_boards\\train\\" + testingDir + "\\" + fileName);
            File imageFile = new File("sign_boards\\train\\" + testingDir + "\\" + fileName);
            Tesseract instance = new Tesseract(); //
            BufferedImage bufferedImage = null;
            try {
                bufferedImage = ImageIO.read(imageFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            instance.setDatapath(tessdataDir);
            String result = "";
            try {
                result = instance.doOCR(bufferedImage);
                if (result.toLowerCase().contains(testingDir.toLowerCase())) {
                    System.out.println("OCR Success Results for '" + testingDir + "': " + fileName + ": " + result);
                    success++;
                } else {
                    System.out.println("OCR Fail Results for '" + testingDir + "': " + fileName + ": " + result);
                    failure++;
                }
            } catch (TesseractException e) {
                System.err.println(e.getMessage());
            }
        }
        System.out.println("********Success for " + testingDir + ": " + success + "/" + failure + "\n\n");
    }

    public static void main(String[] args) {
        System.setProperty("java.library.path", "C:\\opencv\\build\\java");

        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        System.out.println("main: Starting");

        //TesseractSuccess("Exit");
        //TesseractSuccess("Stop");
        //System.exit(0);

        //Initial setup
        String workingDir = "c:\\img\\"; //location where the image collection can be found
        int standardHeight = 400; //standard pixel height to normalize all images to
        Double lat = 39.042416;  //current GPS location
        Double lon = -94.590844; //current GPS location
        String searchItem = "coffee"; //The object to search for
        String androidIP = "192.168.1.7"; //IP of the Android device to talk to


        YelpRecommend recommender = new YelpRecommend();


        System.out.println("Reading in images from " + workingDir);
        File folder = new File(workingDir);
        List<String> imgList = new ArrayList<String>(Arrays.asList(folder.list()));

        System.out.println("Resizing all images to " + standardHeight + " pixels high");
        try {
            ImageResizer.resizeImages(folder, standardHeight);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Updating file list to only include the resized images");
        for (int i = 0; i < imgList.size(); i++) {
            imgList.set(i, "New_" + imgList.get(i));
        }


        System.out.println(System.nanoTime() + " Stitching all images together into a single panoramic");
        Stitch myStitcher = new Stitch(imgList, workingDir);
        String panoImage = "";
        try {
            panoImage = myStitcher.OutputImage();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println(System.nanoTime() + " Finished stitching, output image saved as: " + panoImage);



        //Attempt to detect objects in the image
        IdentifyImage imageID = new IdentifyImage();
        String imageDesc = imageID.IdImage(panoImage);
        System.out.println("Image " + panoImage + " was identified as a " + imageDesc);



        //Now to detect any text in the combined image
        File imageFile = new File(panoImage);
        Tesseract instance = new Tesseract(); //
        BufferedImage bufferedImage = null;
        try {
            bufferedImage = ImageIO.read(imageFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        instance.setDatapath(tessdataDir);
        String result = "";
        try {
            result = instance.doOCR(bufferedImage);
            System.out.println("OCR found: " + result);

        } catch (TesseractException e) {
            System.err.println(e.getMessage());
        }



        if (imageDesc.contains(searchItem)) {
            System.out.println("We have found " + searchItem + " in the image objects");
        } else if (result.contains(searchItem)) {
            System.out.println("We have found " + searchItem + " in the image text");
        } else {

            System.out.println("Searching for a business near (" + lat.toString() + "," + lon.toString() + ") that has " + searchItem);
            JSONObject results = recommender.searchForBusinessesByLocation(searchItem, lat.toString() + "," + lon.toString());
            String clientMessage = "";
            if (imageDesc.contains((CharSequence) results.get("name"))) {
                clientMessage = "Found the best match, " + results.get("name") + " in the images taken";
            } else if (result.contains((CharSequence) results.get("name"))) {
                clientMessage = "Found the best match, " + results.get("name") + " in the image text";
            } else {
                clientMessage = "Best match business for " + searchItem + ": " + results.get("name") + ", " + results.get("distance") + " meters away at (" + results.get("latitude") + "," + results.get("longitude") + "), was not found in the images";
            }
            System.out.println(clientMessage);

            try {
                SocketClient.sendToServer(clientMessage + "\n", androidIP, 1234);
                System.out.println("Sent message to user's device at " + androidIP + ":1234 : \"" + clientMessage + "\"");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



        System.out.println("main: End");
    }
}
