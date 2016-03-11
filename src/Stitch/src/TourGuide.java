import java.io.FileNotFoundException;

/**
 * Created by smoeller on 3/5/2016.
 */
public class TourGuide {
    public static void main(String[] args) {
        System.out.println("main: Starting");
        Stitch myStitcher = new Stitch("mall2.jpg", "mall3.jpg");
        String panoImage = "";
        try {
            panoImage = myStitcher.OutputImage();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("main: Finished stitching, output image: " + panoImage);
    }
}
