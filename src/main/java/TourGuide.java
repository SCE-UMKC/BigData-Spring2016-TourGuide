
import org.json.JSONObject;

import javax.imageio.ImageIO;
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
    public static void main(String[] args) {
        System.out.println("main: Starting");

        //Initial setup
        String workingDir = "c:\\img\\";
        int standardHeight = 500;
        Double lat = 39.042349;
        Double lon = -94.588234;

        File folder = new File(workingDir);
        File[] listOfFiles = folder.listFiles();
        List<String> imgList = new ArrayList<String>(Arrays.asList(folder.list()));

        //List of images to work on
        /* List<String> imgList = new ArrayList<String>();
        imgList.add("IMG_5480.JPG");
        imgList.add("IMG_5481.JPG");
        imgList.add("IMG_5482.JPG");
        imgList.add("IMG_5483.JPG"); */


        //resize the images to a standard size
        System.out.println("Total No of Files:"+listOfFiles.length);
        BufferedImage img = null;
        BufferedImage tempJPG = null;
        File newFileJPG = null;

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                System.out.println("Resizing File " + folder.getPath() + listOfFiles[i].getName());
                int pos = listOfFiles[i].getName().lastIndexOf(".");
                try {
                    img = ImageIO.read(new File(folder.getPath() + "\\" + listOfFiles[i].getName()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int newWidth = img.getWidth() * standardHeight / img.getHeight();
                tempJPG = ImageResizer.resizeImage(img, newWidth, standardHeight);
                newFileJPG = new File(folder.getPath() + "\\" + listOfFiles[i].getName().substring(0,pos)+"_New.jpg");
                try {
                    ImageIO.write(tempJPG, "jpg", newFileJPG);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("    Resized to " + newFileJPG);
            }
        }



        //Stitch the images together
        Stitch myStitcher = new Stitch(imgList, workingDir);
        String panoImage = "";
        try {
            panoImage = myStitcher.OutputImage();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("main: Finished stitching, output image: " + panoImage);


        YelpRecommend recommender = new YelpRecommend();

        String searchItem = "ice cream";
        JSONObject results = recommender.searchForBusinessesByLocation(searchItem, lat.toString()+","+lon.toString());
        String clientMessage = "Best match business for " + searchItem + ": " + results.get("name") + ", " + results.get("distance") + " meters away at (" + results.get("latitude") + "," + results.get("longitude") + ")";
        System.out.println(clientMessage);
        try {
            SocketClient.sendToServer(clientMessage + "\n", "10.151.3.35", 1234);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
