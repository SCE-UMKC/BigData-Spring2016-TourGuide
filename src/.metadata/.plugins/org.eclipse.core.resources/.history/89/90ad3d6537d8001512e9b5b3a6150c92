package ImageId;

import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgcodecs.Imgcodecs;

public class Image {
	private String imageSrc;
	private Mat imageMat;
	private MatOfKeyPoint imageKP;
	private String imageDesc;
	private Mat imageDescriptor;
	private static int featureDetector = FeatureDetector.ORB;
	private static int descriptorExtractor = DescriptorExtractor.ORB;
	
	public Image(String imageSrc) {
		this(imageSrc, "Unknown");
	}
	public Image(String imageSrc, String imageDesc) {
		super();
		this.imageSrc = imageSrc;
		this.imageDesc = imageDesc;
		this.imageKP = new MatOfKeyPoint();
		this.imageDescriptor = new Mat();
		this.imageMat = ReadImage();
		DetectKP();
		ComputeDescriptor();
	}
	
	public String getImageSrc() {
		return imageSrc;
	}
	public void setImageSrc(String imageSrc) {
		this.imageSrc = imageSrc;
	}
	public MatOfKeyPoint getImageKP() {
		return imageKP;
	}
	public String getImageDesc() {
		return imageDesc;
	}
	public Mat getImageDescriptor() {
		return imageDescriptor;
	}
	
	public int getImageCols() {
		return imageMat.cols();
	}
	public int getImageRows() {
		return imageMat.rows();
	}

	private Mat ReadImage() {
		if (imageSrc.startsWith("http:")) {
			//TODO: We have a URL to process
			//For now just return the imread
			return Imgcodecs.imread(imageSrc);
		} else {
			return Imgcodecs.imread(imageSrc);
		}
	}
	private void DetectKP() {
		FeatureDetector detector = FeatureDetector.create(featureDetector);
		detector.detect(imageMat, imageKP);
	}
	private void ComputeDescriptor() {
		DescriptorExtractor de = DescriptorExtractor.create(descriptorExtractor);
		de.compute(imageMat, imageKP, imageDescriptor);
	}
}
