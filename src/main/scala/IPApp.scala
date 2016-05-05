/**
  * Created by pradyumnad on 10/07/15.
  */

import java.awt.image.BufferedImage
import java.io.{FileNotFoundException, IOException, File}
import java.nio.file.{Files, Paths}
import java.util.Arrays
import javax.imageio.ImageIO
import scala.collection.JavaConversions._

import net.sourceforge.tess4j.{TesseractException, Tesseract}
import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.RandomForest
import org.apache.spark.mllib.tree.model.RandomForestModel
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.bytedeco.javacpp.opencv_highgui._
import org.json.JSONObject
import org.opencv.core.Core

import scala.collection.mutable

object IPApp {
  val featureVectorsCluster = new mutable.MutableList[String]

  val IMAGE_CATEGORIES = List("rice", "tempura", "toast", "bibimap", "sushi", "spaghetti", "sausage", "oden", "omelet", "jiaozi")
  //val IMAGE_CATEGORIES = List("accordion", "airplanes", "anchor", "ant", "barrel", "bass", "beaver", "binocular", "bonsai")

  /**
    *
    * @param sc     : SparkContext
    * @param images : Images list from the training set
    */
  def extractDescriptors(sc: SparkContext, images: RDD[(String, String)]): Unit = {

    if (Files.exists(Paths.get(IPSettings.FEATURES_PATH))) {
      println(s"${IPSettings.FEATURES_PATH} exists, skipping feature extraction..")
      return
    }

    val data = images.map {
      case (name, contents) => {
        val desc = ImageUtils.descriptors(name.split("file:/")(1))
        val list = ImageUtils.matToString(desc)
        println("-- " + list.size)
        list
      }
    }.reduce((x, y) => x ::: y)

    val featuresSeq = sc.parallelize(data)

    featuresSeq.saveAsTextFile(IPSettings.FEATURES_PATH)
    println("Total size : " + data.size)
  }

  def kMeansCluster(sc: SparkContext): Unit = {
    if (Files.exists(Paths.get(IPSettings.KMEANS_PATH))) {
      println(s"${IPSettings.KMEANS_PATH} exists, skipping clusters formation..")
      return
    }

    // Load and parse the data
    val data = sc.textFile(IPSettings.FEATURES_PATH)
    val parsedData = data.map(s => Vectors.dense(s.split(' ').map(_.toDouble)))

    // Cluster the data into two classes using KMeans
    val numClusters = 400
    val numIterations = 20
    val clusters = KMeans.train(parsedData, numClusters, numIterations)

    // Evaluate clustering by computing Within Set Sum of Squared Errors
    val WSSSE = clusters.computeCost(parsedData)
    println("Within Set Sum of Squared Errors = " + WSSSE)

    clusters.save(sc, IPSettings.KMEANS_PATH)
    println(s"Saves Clusters to ${IPSettings.KMEANS_PATH}")
  }

  def createHistogram(sc: SparkContext, images: RDD[(String, String)]): Unit = {
    if (Files.exists(Paths.get(IPSettings.HISTOGRAM_PATH))) {
      println(s"${IPSettings.HISTOGRAM_PATH} exists, skipping histograms creation..")
      return
    }

    val sameModel = KMeansModel.load(sc, IPSettings.KMEANS_PATH)

    val kMeansCenters = sc.broadcast(sameModel.clusterCenters)

    val categories = sc.broadcast(IMAGE_CATEGORIES)

    val data = images.map {
      case (name, contents) => {

        val vocabulary = ImageUtils.vectorsToMat(kMeansCenters.value)

        val desc = ImageUtils.bowDescriptors(name.split("file:/")(1), vocabulary)
        val list = ImageUtils.matToString(desc)
        println("-- " + list.size)

        val segments = name.split("/")
        val cat = segments(segments.length - 2)
        List(categories.value.indexOf(cat) + "," + list(0))
      }
    }.reduce((x, y) => x ::: y)

    val featuresSeq = sc.parallelize(data)

    featuresSeq.saveAsTextFile(IPSettings.HISTOGRAM_PATH)
    println("Total size : " + data.size)
  }

  def generateRandomForestModel(sc: SparkContext): Unit = {
    if (Files.exists(Paths.get(IPSettings.RANDOM_FOREST_PATH))) {
      println(s"${IPSettings.RANDOM_FOREST_PATH} exists, skipping Random Forest model formation..")
      return
    }

    val data = sc.textFile(IPSettings.HISTOGRAM_PATH)
    val parsedData = data.map { line =>
      val parts = line.split(',')
      LabeledPoint(parts(0).toDouble, Vectors.dense(parts(1).split(' ').map(_.toDouble)))
    }

    // Split data into training (70%) and test (30%).
    val splits = parsedData.randomSplit(Array(0.7, 0.3), seed = 11L)
    val training = parsedData
    val test = splits(1)

    // Train a RandomForest model.
    //  Empty categoricalFeaturesInfo indicates all features are continuous.
    val numClasses = 10
    val categoricalFeaturesInfo = Map[Int, Int]()
    //    val numTrees = 10 // Use more in practice.
    //    val featureSubsetStrategy = "auto" // Let the algorithm choose.
    //    val impurity = "gini"
    //    val maxDepth = 4
    val maxBins = 100

    val numOfTrees = 4 to(10, 1)
    val strategies = List("all", "sqrt", "log2", "onethird")
    val maxDepths = 3 to(6, 1)
    val impurities = List("gini", "entropy")

    var bestModel: Option[RandomForestModel] = None
    var bestErr = 1.0
    val bestParams = new mutable.HashMap[Any, Any]()
    var bestnumTrees = 0
    var bestFeatureSubSet = ""
    var bestimpurity = ""
    var bestmaxdepth = 0

    numOfTrees.foreach(numTrees => {
      strategies.foreach(featureSubsetStrategy => {
        impurities.foreach(impurity => {
          maxDepths.foreach(maxDepth => {

            println("numTrees " + numTrees + " featureSubsetStrategy " + featureSubsetStrategy +
              " impurity " + impurity + " maxDepth " + maxDepth)

            val model = RandomForest.trainClassifier(training, numClasses, categoricalFeaturesInfo,
              numTrees, featureSubsetStrategy, impurity, maxDepth, maxBins)

            val predictionAndLabel = test.map { point =>
              val prediction = model.predict(point.features)
              (point.label, prediction)
            }

            val testErr = predictionAndLabel.filter(r => r._1 != r._2).count.toDouble / test.count()
            println("Test Error = " + testErr)
            ModelEvaluation.evaluateModel(predictionAndLabel)

            if (testErr < bestErr) {
              bestErr = testErr
              bestModel = Some(model)

              bestParams.put("numTrees", numTrees)
              bestParams.put("featureSubsetStrategy", featureSubsetStrategy)
              bestParams.put("impurity", impurity)
              bestParams.put("maxDepth", maxDepth)

              bestFeatureSubSet = featureSubsetStrategy
              bestimpurity = impurity
              bestnumTrees = numTrees
              bestmaxdepth = maxDepth
            }
          })
        })
      })
    })

    println("Best Err " + bestErr)
    println("Best params " + bestParams.toArray.mkString(" "))


    val randomForestModel = RandomForest.trainClassifier(parsedData, numClasses, categoricalFeaturesInfo, bestnumTrees, bestFeatureSubSet, bestimpurity, bestmaxdepth, maxBins)


    // Save and load model
    randomForestModel.save(sc, IPSettings.RANDOM_FOREST_PATH)
    println("Random Forest Model generated")
  }

  /**
    * @note Test method for classification on Spark
    * @param sc : Spark Context
    * @return
    */
  def testImageClassification(sc: SparkContext) = {

    val model = KMeansModel.load(sc, IPSettings.KMEANS_PATH)
    val vocabulary = ImageUtils.vectorsToMat(model.clusterCenters)

    val path = "files/101_ObjectCategories/ant/image_0012.jpg"
    val desc = ImageUtils.bowDescriptors(path, vocabulary)

    val testImageMat = imread(path)
    imshow("Test Image", testImageMat)

    val histogram = ImageUtils.matToVector(desc)

    println("-- Histogram size : " + histogram.size)
    println(histogram.toArray.mkString(" "))

    val nbModel = RandomForestModel.load(sc, IPSettings.RANDOM_FOREST_PATH)
    //println(nbModel.labels.mkString(" "))

    val p = nbModel.predict(histogram)
    println(s"Predicting test image : " + IMAGE_CATEGORIES(p.toInt))

    waitKey(0)
  }

  /**
    * @note Test method for classification from Client
    * @param sc   : Spark Context
    * @param path : Path of the image to be classified
    */
  def classifyImage(sc: SparkContext, path: String): Double = {

    val model = KMeansModel.load(sc, IPSettings.KMEANS_PATH)
    val vocabulary = ImageUtils.vectorsToMat(model.clusterCenters)

    val desc = ImageUtils.bowDescriptors(path, vocabulary)

    val histogram = ImageUtils.matToVector(desc)

    println("--Histogram size : " + histogram.size)

    val nbModel = RandomForestModel.load(sc, IPSettings.RANDOM_FOREST_PATH)
    //println(nbModel.labels.mkString(" "))

    val p = nbModel.predict(histogram)
    //    println(s"Predicting test image : " + IMAGE_CATEGORIES(p.toInt))

    p
  }

  def main(args: Array[String]) {
    val conf = new SparkConf()
      .setAppName(s"IPApp")
      .setMaster("local[*]")
      .set("spark.executor.memory", "6g")
      .set("spark.driver.memory", "6g")
    val ssc = new StreamingContext(conf, Seconds(2))
    val sc = ssc.sparkContext

    val images = sc.wholeTextFiles(s"${IPSettings.INPUT_DIR}/*/*.jpg")

    /**
      * Extracts Key Descriptors from the Training set
      * Saves it to a text file
      */
    extractDescriptors(sc, images)

    /**
      * Reads the Key descriptors and forms a 'K' cluster
      * Saves the centers as a text file
      */
    kMeansCluster(sc)

    /**
      * Forms a labeled Histogram using the Training set
      * Saves it in the form of label, [Histogram]
      *
      * This shall be used as a input to Naive Bayes to create a model
      */
    createHistogram(sc, images)

    /**
      * From the labeled Histograms a Naive Bayes Model is created
      */
    generateRandomForestModel(sc)

    //    testImageClassification(sc)

    val testImages = sc.wholeTextFiles(s"${IPSettings.TEST_INPUT_DIR}/*/*.jpg")
    val testImagesArray = testImages.collect()
    var predictionLabels = List[String]()
    testImagesArray.foreach(f => {
      println("Test image: " + f._1)
      val splitStr = f._1.split("file:/")
      val predictedClass: Double = classifyImage(sc, splitStr(1))
      val segments = f._1.split("/")
      val cat = segments(segments.length - 2)
      val GivenClass = IMAGE_CATEGORIES.indexOf(cat)
      println(s"Predicting test image : " + cat + " as " + IMAGE_CATEGORIES(predictedClass.toInt))
      predictionLabels = predictedClass + ";" + GivenClass :: predictionLabels
    })

    val pLArray = predictionLabels.toArray

    predictionLabels.foreach(f => {
      val ff = f.split(";")
      println("Prediction labels: " + ff(0), ff(1))
    })
    val predictionLabelsRDD = sc.parallelize(pLArray)


    val pRDD = predictionLabelsRDD.map(f => {
      val ff = f.split(";")
      (ff(0).toDouble, ff(1).toDouble)
    })
    val accuracy = 1.0 * pRDD.filter(x => x._1 == x._2).count() / testImages.count

    println("Accuracy: " + accuracy)
    ModelEvaluation.evaluateModel(pRDD)





    val tessdataDir = "C:\\Users\\smoeller\\Documents\\MSCS\\CS5542\\BigData-Spring2016-TourGuide\\BigData-Spring2016-TourGuide"
    System.setProperty("java.library.path", "C:\\opencv\\build\\java")
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

    System.out.println("main: Finished training, now to analyze the data we received")

    //Initial setup
    val workingDir: String = "c:\\img\\"
    val standardHeight: Int = 500
    val lat: Double = 39.042349
    val lon: Double = -94.588234
    val searchItem: String = "jeans"
    val androidIP: String = "10.126.0.159"


    val recommender: YelpRecommend = new YelpRecommend

/*
    System.out.println("Reading in images from " + workingDir)
    val folder: File = new File(workingDir)
    val imgList: java.util.List = new java.util.ArrayList(Arrays.asList(folder.list))

    System.out.println("Resizing all images to " + standardHeight + " pixels high")
    try {
      ImageResizer.resizeImages(folder, standardHeight)
    }
    catch {
      case e: IOException => {
        e.printStackTrace
      }
    }


    System.out.println("Updating file list to only include the resized images")
    {
      var i: Int = 0
      while (i < imgList.size) {
        {
          val newImgName = "New_" + imgList.get(i)
          imgList.set(i, newImgName)
        }
        ({
          i += 1; i - 1
        })
      }
    }



    System.out.println("Stitching all images together into a single panoramic")
    val myStitcher: Stitch = new Stitch(imgList, workingDir)
    var panoImage: String = ""
    try {
      panoImage = myStitcher.OutputImage
    }
    catch {
      case e: FileNotFoundException => {
        e.printStackTrace
      }
    }
    System.out.println("Finished stitching, output image saved as: " + panoImage)



    //Attempt to detect objects in the image
    val imageID: IdentifyImage = new IdentifyImage
    val imageDesc: String = imageID.IdImage(panoImage)
    System.out.println("Image " + panoImage + " was identified as a " + imageDesc)



    //Now to detect any text in the combined image
    val imageFile: File = new File(panoImage)
    val instance: Tesseract = new Tesseract
    var bufferedImage: BufferedImage = null
    try {
      bufferedImage = ImageIO.read(imageFile)
    }
    catch {
      case e: IOException => {
        e.printStackTrace
      }
    }
    instance.setDatapath(tessdataDir)
    var result: String = ""
    try {
      result = instance.doOCR(bufferedImage)
      System.out.println("OCR found: " + result)
    }
    catch {
      case e: TesseractException => {
        System.err.println(e.getMessage)
      }
    }



    if (imageDesc.contains(searchItem)) {
      System.out.println("We have found " + searchItem + " in the image objects")
    }
    else if (result.contains(searchItem)) {
      System.out.println("We have found " + searchItem + " in the image text")
    }
    else {
      System.out.println("Searching for a business near (" + lat.toString + "," + lon.toString + ") that has " + searchItem)
      val results: JSONObject = recommender.searchForBusinessesByLocation(searchItem, lat.toString + "," + lon.toString)
      var clientMessage: String = ""
      if (imageDesc.contains(results.get("name").asInstanceOf[CharSequence])) {
        clientMessage = "Found the best match, " + results.get("name") + " in the images taken"
      }
      else if (result.contains(results.get("name").asInstanceOf[CharSequence])) {
        clientMessage = "Found the best match, " + results.get("name") + " in the image text"
      }
      else {
        clientMessage = "Best match business for " + searchItem + ": " + results.get("name") + ", " + results.get("distance") + " meters away at (" + results.get("latitude") + "," + results.get("longitude") + "), was not found in the images"
      }
      System.out.println(clientMessage)
      try {
        SocketClient.sendToServer(clientMessage + "\n", androidIP, 1234)
      }
      catch {
        case e: IOException => {
          e.printStackTrace
        }
      }
    }
*/


    System.out.println("main: End")
  }
}