package ImageId;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;

public class ImageCompare {

	public static void main(String[] args) {
	      System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
	      
	      String compareImage = "c:\\chair1.jpg";
	      
	      IdentifyImage imageID = new IdentifyImage();
	      String imageDesc = imageID.IdImage(compareImage);
	      System.out.println("Image " + compareImage + " was identified as a " + imageDesc);
	      
	}

}
