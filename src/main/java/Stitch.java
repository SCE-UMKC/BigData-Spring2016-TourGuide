
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
//import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.GrayF32;
//import boofcv.struct.image.ImageSingleBand;
//import boofcv.struct.image.MultiSpectral;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.transform.homography.HomographyPointOps_F64;
import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.FastQueue;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;


/**
 * Created by smoeller on 3/5/2016.
 * Stitch - takes a list of images, passed as the paths to the images,
 * and stitches them together based on matching features
 * Returns a path to the new combined image on disk
 *
 * Includes code adapted from Peter Abeles' examples from http://boofcv.org
 */


public class Stitch {
    private static String imageDir = "C:\\Users\\smoeller\\Documents\\MSCS\\CS5542\\BigData-Spring2016-TourGuide\\BigData-Spring2016-TourGuide\\data\\";
    private List<String> imgs;

    public Stitch(List<String> imgs) {
        this(imgs, imageDir);
    }

    public Stitch(List<String> imgs, String path) {
        if (imgs.size() < 1) {
            System.out.println("No images, nothing to do");
            System.exit(1);
        }
        imageDir = path;
        this.imgs = new ArrayList<String>();
        Iterator imgItr = imgs.iterator();
        while (imgItr.hasNext()) {
            this.imgs.add(imageDir + imgItr.next());
        }
    }

    private String generateRandomImageName () {
        UUID uuid = UUID.randomUUID();
        return imageDir + uuid.toString() + ".png";
    }

    private ArrayList<BufferedImage> LoadAllImages (List<String> imgs) throws FileNotFoundException {
        ArrayList<BufferedImage> imageS = new ArrayList<BufferedImage>();
        Iterator imgItr = imgs.iterator();
        while (imgItr.hasNext()) {
            String tmpImgStr = (String) imgItr.next();
            inputExists(tmpImgStr);
            BufferedImage tmpImg = UtilImageIO.loadImage(UtilIO.pathExample(tmpImgStr));
            imageS.add(tmpImg);
        }
        return imageS;
    }

    public String OutputImage() throws FileNotFoundException {
        List<String> stitchedImgs = ProcessImageList(imgs);

        while (stitchedImgs.size() > 1) {
            stitchedImgs = ProcessImageList(new ArrayList<String>(stitchedImgs));
            System.out.println("Stitching images, stitched down to " + stitchedImgs.size());
        }

        return stitchedImgs.get(0);
    }

    private List<String> ProcessImageList (List<String> inList) throws FileNotFoundException {
        List<BufferedImage> imageS = LoadAllImages(inList);

        System.out.println("Images loaded: " + imageS.size());
        List<String> middleImages = new ArrayList<String>();

        if (imageS.isEmpty()) {
            System.out.println("No images loaded");
        }
        if (imageS.size() < 2) {
            //Only 1 image, so just return it
            middleImages.add(inList.get(0));
            return middleImages;
        }

        BufferedImage imageA = imageS.get(0);
        System.out.println("stringit: " + imageS.toString());
        //ImageSingleBand inputA = ConvertBufferedImage.convertFromSingle(imageA, null, ImageFloat32.class);
        GrayF32 inputA = ConvertBufferedImage.convertFromSingle(imageA, null, GrayF32.class);

        for(int i = 1; i < imageS.size(); i++) {

            //DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(new ConfigFastHessian(1, 2, 200, 1, 9, 4, 4), null,null, ImageFloat32.class);
            DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(new ConfigFastHessian(1, 2, 200, 1, 9, 4, 4), null,null, GrayF32.class);
            ScoreAssociation<BrightFeature> scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class, true);
            AssociateDescription<BrightFeature> associate = FactoryAssociation.greedy(scorer,2,true);

            // fit the images using a homography.  This works well for rotations and distant objects.
            ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher = FactoryMultiViewRobust.homographyRansac(null,new ConfigRansac(60,3));

            BufferedImage imageB = (BufferedImage) imageS.get(i);
            //ImageSingleBand inputB = ConvertBufferedImage.convertFromSingle(imageB, null, ImageFloat32.class);
            GrayF32 inputB = ConvertBufferedImage.convertFromSingle(imageB, null, GrayF32.class);
            System.out.println("inputB: " + inputB.toString());
            //Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);
            Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);
            String tmpImgName = generateRandomImageName();

            stitch(imageA, imageB, GrayF32.class);
            renderStitching(imageA, imageB, H);

            WriteImage(imageA, imageB, H, tmpImgName);
            middleImages.add(tmpImgName);
            System.out.println("Created new merged image " + tmpImgName);

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.exit(1);
        }
        return middleImages;
    }

    /*
    private static<FD extends TupleDesc> Homography2D_F64 computeTransform(
            GrayF32 imageA, GrayF32 imageB, DetectDescribePoint<GrayF32,FD> detDesc,
            //ImageSingleBand imageA, ImageSingleBand imageB, DetectDescribePoint<ImageSingleBand,FD> detDesc,
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
    */

    private static<FD extends TupleDesc> void describeImage(
            //ImageSingleBand image, DetectDescribePoint<ImageSingleBand,FD> detDesc,
            GrayF32 image, DetectDescribePoint<GrayF32,FD> detDesc,
            List<Point2D_F64> points, FastQueue<FD> listDescs) {
        detDesc.detect(image);

        listDescs.reset();
        for( int i = 0; i < detDesc.getNumberOfFeatures(); i++ ) {
            points.add( detDesc.getLocation(i).copy() );
            listDescs.grow().setTo(detDesc.getDescription(i));
        }
    }

    private void inputExists(String image) throws FileNotFoundException {
        File file = new File(image);
        if (! file.exists()) {
            System.out.println("Can't find image " + image);
            throw new FileNotFoundException();
        } else {
            System.out.println("Good, image " +image+" exists");
        }
    }

    private static void WriteImage(BufferedImage imageA, BufferedImage imageB, Homography2D_F64 fromAtoB, String outputImg) {
        UtilImageIO.saveImage(imageA, (String) "c:\\img\\tmp1.png");
        UtilImageIO.saveImage(imageB, (String) "c:\\img\\tmp2.png");

        // specify size of output image
        double scale = 0.5;

        // Convert into a BoofCV color format
        //MultiSpectral<ImageFloat32> colorA = ConvertBufferedImage.convertFromMulti(imageA, null, true, ImageFloat32.class);
        //MultiSpectral<ImageFloat32> colorB = ConvertBufferedImage.convertFromMulti(imageB, null, true, ImageFloat32.class);
        Planar<GrayF32> colorA = ConvertBufferedImage.convertFromMulti(imageA, null, true, GrayF32.class);
        Planar<GrayF32> colorB = ConvertBufferedImage.convertFromMulti(imageB, null, true, GrayF32.class);

        /*Planar<GrayF32> colorA = ConvertBufferedImage.convertFromMulti(imageA, null, true, GrayF32.class);
        Planar<GrayF32> colorB = ConvertBufferedImage.convertFromMulti(imageB, null,true, GrayF32.class);
        */

        // Where the output images are rendered into
        //MultiSpectral<GrayF32> work = colorA.createSameShape();
        Planar<GrayF32> work = colorA.createSameShape();

        // Adjust the transform so that the whole image can appear inside of it
        Homography2D_F64 fromAToWork = new Homography2D_F64(scale, 0, colorA.width/4, 0, scale, colorA.height/4, 0, 0, 1);
        Homography2D_F64 fromWorkToA = fromAToWork.invert(null);

        // Used to render the results onto an image
        PixelTransformHomography_F32 model = new PixelTransformHomography_F32();
        //InterpolatePixelS<ImageFloat32> interp = FactoryInterpolation.bilinearPixelS(ImageFloat32.class, BorderType.VALUE);
        InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
        //ImageDistort<MultiSpectral<ImageFloat32>,MultiSpectral<ImageFloat32>> distort = DistortSupport.createDistortMS(ImageFloat32.class, model, interp, false);
        ImageDistort<Planar<GrayF32>,Planar<GrayF32>> distort = DistortSupport.createDistortPL(GrayF32.class, model, interp, false);
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

        UtilImageIO.saveImage(output, "c:\\img\\tmp3.png");

        ConvertBufferedImage.convertTo(work,output,true);

        UtilImageIO.saveImage(output, outputImg);
        //ShowImages.showWindow(output,"Stitched Images", true);
    }



    public static void renderStitching(BufferedImage imageA, BufferedImage imageB, Homography2D_F64 fromAtoB) {
        // specify size of output image
        double scale = 0.5;

        // Convert into a BoofCV color format
        Planar<GrayF32> colorA =
                ConvertBufferedImage.convertFromMulti(imageA, null, true, GrayF32.class);
        Planar<GrayF32> colorB =
                ConvertBufferedImage.convertFromMulti(imageB, null,true, GrayF32.class);

        // Where the output images are rendered into
        Planar<GrayF32> work = colorA.createSameShape();

        // Adjust the transform so that the whole image can appear inside of it
        Homography2D_F64 fromAToWork = new Homography2D_F64(scale,0,colorA.width/4,0,scale,colorA.height/4,0,0,1);
        Homography2D_F64 fromWorkToA = fromAToWork.invert(null);

        // Used to render the results onto an image
        PixelTransformHomography_F32 model = new PixelTransformHomography_F32();
        InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
        ImageDistort<Planar<GrayF32>,Planar<GrayF32>> distort =
                DistortSupport.createDistortPL(GrayF32.class, model, interp, false);
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

        // draw lines around the distorted image to make it easier to see
        Homography2D_F64 fromBtoWork = fromWorkToB.invert(null);
        Point2D_I32 corners[] = new Point2D_I32[4];
        corners[0] = renderPoint(0,0,fromBtoWork);
        corners[1] = renderPoint(colorB.width,0,fromBtoWork);
        corners[2] = renderPoint(colorB.width,colorB.height,fromBtoWork);
        corners[3] = renderPoint(0,colorB.height,fromBtoWork);

        g2.setColor(Color.ORANGE);
        g2.setStroke(new BasicStroke(4));
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawLine(corners[0].x,corners[0].y,corners[1].x,corners[1].y);
        g2.drawLine(corners[1].x,corners[1].y,corners[2].x,corners[2].y);
        g2.drawLine(corners[2].x,corners[2].y,corners[3].x,corners[3].y);
        g2.drawLine(corners[3].x,corners[3].y,corners[0].x,corners[0].y);

        ShowImages.showWindow(output,"Stitched Images", true);
    }

    private static Point2D_I32 renderPoint( int x0 , int y0 , Homography2D_F64 fromBtoWork )
    {
        Point2D_F64 result = new Point2D_F64();
        HomographyPointOps_F64.transform(fromBtoWork, new Point2D_F64(x0, y0), result);
        return new Point2D_I32((int)result.x,(int)result.y);
    }

    public static <T extends ImageGray>
    void stitch( BufferedImage imageA , BufferedImage imageB , Class<T> imageType )
    {
        T inputA = ConvertBufferedImage.convertFromSingle(imageA, null, imageType);
        T inputB = ConvertBufferedImage.convertFromSingle(imageB, null, imageType);

        // Detect using the standard SURF feature descriptor and describer
        DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(
                new ConfigFastHessian(1, 2, 200, 1, 9, 4, 4), null,null, imageType);
        ScoreAssociation<BrightFeature> scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class,true);
        AssociateDescription<BrightFeature> associate = FactoryAssociation.greedy(scorer,2,true);

        // fit the images using a homography.  This works well for rotations and distant objects.
        ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher =
                FactoryMultiViewRobust.homographyRansac(null,new ConfigRansac(60,3));

        Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);

        renderStitching(imageA,imageB,H);
    }

    public static<T extends ImageGray, FD extends TupleDesc> Homography2D_F64
    computeTransform(T imageA, T imageB, DetectDescribePoint<T,FD> detDesc, AssociateDescription<FD> associate, ModelMatcher<Homography2D_F64,AssociatedPair> modelMatcher) {
        // get the length of the description
        List<Point2D_F64> pointsA = new ArrayList<Point2D_F64>();
        FastQueue<FD> descA = UtilFeature.createQueue(detDesc,100);
        List<Point2D_F64> pointsB = new ArrayList<Point2D_F64>();
        FastQueue<FD> descB = UtilFeature.createQueue(detDesc,100);

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

    private static <T extends ImageGray, FD extends TupleDesc>
    void describeImage(T image,
                       DetectDescribePoint<T,FD> detDesc,
                       List<Point2D_F64> points,
                       FastQueue<FD> listDescs) {
        detDesc.detect(image);

        listDescs.reset();
        for( int i = 0; i < detDesc.getNumberOfFeatures(); i++ ) {
            points.add( detDesc.getLocation(i).copy() );
            listDescs.grow().setTo(detDesc.getDescription(i));
        }
    }
}