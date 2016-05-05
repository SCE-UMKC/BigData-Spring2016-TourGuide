import java.util.ListIterator;
import java.util.Vector;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class IdentifyImage {
    private Vector<Image> imageCompareSet;
    static int comparisonMethod = Imgproc.TM_CCOEFF_NORMED;

    public IdentifyImage() {
        this.imageCompareSet = new Vector<Image>();
        LoadCompareSet();
    }

    private void LoadCompareSet() {
        String directory = "C:\\Users\\smoeller\\Documents\\MSCS\\CS5542\\BigData-Spring2016-TourGuide\\BigData-Spring2016-TourGuide\\data";
        imageCompareSet.add(new Image(directory + "\\stick-figure1.jpg", "Tall Stick Person"));
        imageCompareSet.add(new Image(directory + "\\stick-figure2.jpg", "Wide Stick Person"));
        imageCompareSet.add(new Image(directory + "\\Get-Direction-Left.png", "Left directions"));
        imageCompareSet.add(new Image(directory + "\\Get-Direction-Right.png", "Right directions"));
        imageCompareSet.add(new Image(directory + "\\chair1.jpg", "Wooden chair"));
        imageCompareSet.add(new Image(directory + "\\chair2.jpg", "Folding chair"));
        imageCompareSet.add(new Image(directory + "\\foldingchair1.jpg", "Folding chair"));
        imageCompareSet.add(new Image(directory + "\\gap1.jpg", "Gap"));
        imageCompareSet.add(new Image(directory + "\\buckle1.jpg", "Buckle"));
        imageCompareSet.add(new Image(directory + "\\forever211.png", "Forever 21"));
		/*
		 * Add lots of images however we decide to do it. Could be by reading a file
		 * of image locations & descriptions, or pulling the info from a database,
		 * or just listing a bunch here (for smaller scale tests)
		 */
    }
    private float compareImagePair(Image img1, Image img2) {
        float bestMatch = (float) 0.0;
        Mat result = new Mat();
        Imgproc.matchTemplate(img1.getImageDescriptor(), img2.getImageDescriptor(), result, comparisonMethod);
        System.out.println("Comparitor2 is " + result.size());
        for (int i = 0; i < result.rows(); i++)
            for (int j = 0; j < result.cols(); j++)
                if(result.get(i, j)[0] > bestMatch) {
                    bestMatch = (float) result.get(i, j)[0];
                }
        return bestMatch;
    }

    public String IdImage(String imgSrc) {
        Image img = new Image(imgSrc);
        return this.IdImage(img);
    }
    public String IdImage(Image img) {
        ListIterator<Image> iter = imageCompareSet.listIterator();
        float bestMatchRate = (float) 0.0;
        String bestMatchDesc = "Unknown";
        while (iter.hasNext()) {
            Image tmpImg = iter.next();
            float thisMatchRate = compareImagePair(img, tmpImg);
            if (thisMatchRate > bestMatchRate) {
                //Found a new best batch to record
                bestMatchRate = thisMatchRate;
                bestMatchDesc = tmpImg.getImageDesc();
                //System.out.println("Found a new best match: " + bestMatchDesc + " (" + bestMatchRate + ")");
            }
        }
        if (bestMatchRate < 0.50) {
            //We must have at least a 50% confidence in the match, or not worth noting
            bestMatchDesc = "";
        }
        return bestMatchDesc;
    }
}