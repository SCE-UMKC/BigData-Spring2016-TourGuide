
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by smoeller on 3/5/2016.
 */

public class TourGuide {
    private static String tessdataDir = "C:\\Users\\smoeller\\Documents\\MSCS\\CS5542\\BigData-Spring2016-TourGuide\\BigData-Spring2016-TourGuide";


    public static void main(String[] args) {
        System.out.println("main: Starting");

        //Initial setup
        String workingDir = "c:\\img\\"; //location where the image collection can be found
        int standardHeight = 500; //standard pixel height to normalize all images to
        Double lat = 39.042349;  //current GPS location
        Double lon = -94.588234; //current GPS location
        String searchItem = "ice cream"; //The object to search for


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


        System.out.println("Stitching all images together into a single panoramic");
        Stitch myStitcher = new Stitch(imgList, workingDir);
        String panoImage = "";
        try {
            panoImage = myStitcher.OutputImage();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("Finished stitching, output image saved as: " + panoImage);



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
        try {
            String result = instance.doOCR(bufferedImage);
            System.out.println("OCR found: " + result);

        } catch (TesseractException e) {
            System.err.println(e.getMessage());
        }




        System.out.println("Searching for a business near (" + lat.toString()+","+lon.toString() + ") that has " + searchItem);
        JSONObject results = recommender.searchForBusinessesByLocation(searchItem, lat.toString()+","+lon.toString());
        String clientMessage = "Best match business for " + searchItem + ": " + results.get("name") + ", " + results.get("distance") + " meters away at (" + results.get("latitude") + "," + results.get("longitude") + ")";
        System.out.println(clientMessage);
        /*
        try {
            SocketClient.sendToServer(clientMessage + "\n", "10.205.0.107", 1234);
        } catch (IOException e) {
            e.printStackTrace();
        }
        */


        System.out.println("main: End");
    }
}
