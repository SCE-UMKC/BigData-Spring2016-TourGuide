
import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.distort.impl.DistortSupport;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.core.image.border.BorderType;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.gui.image.ShowImages;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageSingleBand;
import boofcv.struct.image.MultiSpectral;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.FastQueue;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Created by smoeller on 3/5/2016.
 * Stitch - takes 2 images, passed as the paths to the images,
 * and stitches them together based on matching features
 * Returns a path to the new combined image on disk
 *
 * Includes code adapted from Peter Abeles' examples from http://boofcv.org
 */


public class Stitch {
    private static String imageDir = "C:\\Users\\smoeller\\Documents\\MSCS\\CS5542\\BigData-Spring2016-TourGuide\\BigData-Spring2016-TourGuide\\data\\";
    private static String outputImage;
    private String img1;
    private String img2;

    public Stitch(String img1, String img2) {
        this(img1, img2, imageDir);
    }

    public Stitch(String img1, String img2, String path) {
        imageDir = path;
        this.img1 = img1;
        this.img2 = img2;
        UUID uuid = UUID.randomUUID();
        outputImage = imageDir + uuid.toString() + ".png";
        System.out.println("Using images: "+imageDir+img1+" and "+imageDir+img2);
        System.out.println("Unique output filename: " + outputImage);
    }

    public String OutputImage() throws FileNotFoundException {
        BufferedImage imageA,imageB;

        inputExists();

        imageA = UtilImageIO.loadImage(UtilIO.pathExample(imageDir+img1));
        imageB = UtilImageIO.loadImage(UtilIO.pathExample(imageDir+img2));

        ImageSingleBand inputA = ConvertBufferedImage.convertFromSingle(imageA, null, ImageFloat32.class);
        ImageSingleBand inputB = ConvertBufferedImage.convertFromSingle(imageB, null, ImageFloat32.class);

        DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(
                new ConfigFastHessian(1, 2, 200, 1, 9, 4, 4), null,null, ImageFloat32.class);
        ScoreAssociation<BrightFeature> scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class, true);
        AssociateDescription<BrightFeature> associate = FactoryAssociation.greedy(scorer,2,true);

        // fit the images using a homography.  This works well for rotations and distant objects.
        ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher =
                FactoryMultiViewRobust.homographyRansac(null,new ConfigRansac(60,3));

        Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);

        WriteImage(imageA, imageB, H);

        return outputImage;
    }

    private static<FD extends TupleDesc> Homography2D_F64 computeTransform(
            ImageSingleBand imageA, ImageSingleBand imageB, DetectDescribePoint<ImageSingleBand,FD> detDesc,
            AssociateDescription<FD> associate,
            ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher) {
        // get the length of the description
        List<Point2D_F64> pointsA = new ArrayList<Point2D_F64>();
        FastQueue<FD> descA = UtilFeature.createQueue(detDesc, 100);
        List<Point2D_F64> pointsB = new ArrayList<Point2D_F64>();
        FastQueue<FD> descB = UtilFeature.createQueue(detDesc, 100);

        // extract feature locations and descriptions from each image
        describeImage(imageA, detDesc, pointsA, descA);
        describeImage(imageB, detDesc, pointsB, descB);

        // Associate features between the two images
        associate.setSource(descA);
        associate.setDestination(descB);
        associate.associate();

        // create a list of AssociatedPairs that tell the model matcher how a feature moved
        FastQueue<AssociatedIndex> matches = associate.getMatches();
        List<AssociatedPair> pairs = new ArrayList<AssociatedPair>();

        for( int i = 0; i < matches.size(); i++ ) {
            AssociatedIndex match = matches.get(i);

            Point2D_F64 a = pointsA.get(match.src);
            Point2D_F64 b = pointsB.get(match.dst);

            pairs.add( new AssociatedPair(a,b,false));
        }

        // find the best fit model to describe the change between these images
        if( !modelMatcher.process(pairs) )
            throw new RuntimeException("Model Matcher failed!");

        // return the found image transform
        return modelMatcher.getModelParameters().copy();
    }

    private static<FD extends TupleDesc> void describeImage(
            ImageSingleBand image, DetectDescribePoint<ImageSingleBand,FD> detDesc,
            List<Point2D_F64> points, FastQueue<FD> listDescs) {
        detDesc.detect(image);

        listDescs.reset();
        for( int i = 0; i < detDesc.getNumberOfFeatures(); i++ ) {
            points.add( detDesc.getLocation(i).copy() );
            listDescs.grow().setTo(detDesc.getDescription(i));
        }
    }

    private void inputExists() throws FileNotFoundException {
        File file1 = new File(imageDir+img1);
        File file2 = new File(imageDir+img2);
        if (! file1.exists()) {
            System.out.println("Can't find image 1: " + imageDir + img1);
            throw new FileNotFoundException();
        } else if (! file2.exists()) {
            System.out.println("Can't find image 2: " + imageDir + img2);
            throw new FileNotFoundException();
        } else {
            System.out.println("Good, both "+img1+" and "+img2+" exist");
        }
    }

    private static void WriteImage(BufferedImage imageA, BufferedImage imageB, Homography2D_F64 fromAtoB) {
        // specify size of output image
        double scale = 0.5;

        // Convert into a BoofCV color format
        MultiSpectral<ImageFloat32> colorA = ConvertBufferedImage.convertFromMulti(imageA, null, true, ImageFloat32.class);
        MultiSpectral<ImageFloat32> colorB = ConvertBufferedImage.convertFromMulti(imageB, null,true, ImageFloat32.class);

        // Where the output images are rendered into
        MultiSpectral<ImageFloat32> work = colorA.createSameShape();

        // Adjust the transform so that the whole image can appear inside of it
        Homography2D_F64 fromAToWork = new Homography2D_F64(scale, 0, colorA.width/4, 0, scale, colorA.height/4, 0, 0, 1);
        Homography2D_F64 fromWorkToA = fromAToWork.invert(null);

        // Used to render the results onto an image
        PixelTransformHomography_F32 model = new PixelTransformHomography_F32();
        InterpolatePixelS<ImageFloat32> interp = FactoryInterpolation.bilinearPixelS(ImageFloat32.class, BorderType.VALUE);
        ImageDistort<MultiSpectral<ImageFloat32>,MultiSpectral<ImageFloat32>> distort = DistortSupport.createDistortMS(ImageFloat32.class, model, interp, false);
        distort.setRenderAll(false);

        // Render first image
        model.set(fromWorkToA);
        distort.apply(colorA,work);

        // Render second image
        Homography2D_F64 fromWorkToB = fromWorkToA.concat(fromAtoB,null);
        model.set(fromWorkToB);
        distort.apply(colorB,work);

        // Convert the rendered image into a BufferedImage
        BufferedImage output = new BufferedImage(work.width,work.height,imageA.getType());
        ConvertBufferedImage.convertTo(work,output,true);

        Graphics2D g2 = output.createGraphics();

        UtilImageIO.saveImage(output, outputImage);
        //ShowImages.showWindow(output,"Stitched Images", true);
    }
}
