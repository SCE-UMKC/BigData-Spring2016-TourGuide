import java.awt.Graphics;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


import javax.imageio.ImageIO;
/**
 * Created by sindhu on 3/09/2016.
 */
public class ImageResizer {
    public static void main(String[] args) throws IOException {

        File folder = new File("C:\\Users\\smoeller\\Documents\\MSCS\\CS5542\\BigData-Spring2016-TourGuide\\BigData-Spring2016-TourGuide\\data\\");
        File[] listOfFiles = folder.listFiles();
        System.out.println("Total No of Files:"+listOfFiles.length);
        BufferedImage img = null;
        BufferedImage tempPNG = null;
        BufferedImage tempJPG = null;
        File newFilePNG = null;
        File newFileJPG = null;

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                System.out.println("Resizing File " + folder.getPath() + listOfFiles[i].getName());
                int pos = listOfFiles[i].getName().lastIndexOf(".");
                img = ImageIO.read(new File(folder.getPath() + listOfFiles[i].getName()));
                tempJPG = resizeImage(img, img.getWidth(), img.getHeight());
                newFileJPG = new File(folder.getPath() + listOfFiles[i].getName().substring(0,pos)+"_New.jpg");
                ImageIO.write(tempJPG, "jpg", newFileJPG);
                System.out.println("    Resized to " + newFileJPG);
            }
        }
        System.out.println("DONE");
    }

    public static void resizeImages(File folder, int standardHeight) throws IOException {
        File[] listOfFiles = folder.listFiles();

        //resize the images to a standard size
        System.out.println("Total No of Files:"+listOfFiles.length);
        BufferedImage img = null;
        BufferedImage tempJPG = null;
        File newFileJPG = null;

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                System.out.println("Resizing File " + folder.getPath() + listOfFiles[i].getName());
                img = ImageIO.read(new File(folder.getPath() + "\\" + listOfFiles[i].getName()));
                int newWidth = img.getWidth() * standardHeight / img.getHeight();
                tempJPG = ImageResizer.resizeImage(img, newWidth, standardHeight);
                newFileJPG = new File(folder.getPath() + "\\" + "New_" + listOfFiles[i].getName());
                ImageIO.write(tempJPG, "jpg", newFileJPG);
                System.out.println("    Resized to " + newFileJPG);
            }
        }
    }

    /**
     * This function resize the image file and returns the BufferedImage object that can be saved to file system.
     */
    public static BufferedImage resizeImage(final Image image, int width, int height) {

        final BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics2D = bufferedImage.createGraphics();
        graphics2D.setComposite(AlphaComposite.Src);
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.drawImage(image, 0, 0, width, height, null);
        graphics2D.dispose();

        return bufferedImage;
    }
}
