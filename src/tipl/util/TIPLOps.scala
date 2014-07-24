package tipl.util
import scala.reflect.ClassTag
import scala.collection.JavaConversions._
import tipl.formats.TImgRO
import tipl.formats.TImg
import tipl.tools.BaseTIPLPluginIn._
import tipl.tools.VFilterScale
import tipl.util.D3int
import tipl.util.D3float
import tipl.tools.FilterScale.filterGenerator
import tipl.spark.SKVoronoi
import tipl.spark.ShapeAnalysis
import org.apache.spark.rdd.RDD
import tipl.tools.BaseTIPLPluginIn

import tipl.spark.DTImg
import tipl.spark.hadoop.WholeTiffFileInputFormat
import tipl.spark.hadoop.WholeByteInputFormat
import tipl.formats.TReader.TSliceReader
import tipl.spark.hadoop.UnreadTiffRDD

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.PairRDDFunctions._

import org.apache.spark.api.java.JavaPairRDD

/**
 * An extension of TImgRO to make the available filters show up
*/
object TIPLOps {
  trait NeighborhoodOperation[T,U] {
    def blockOperation(windSize: D3int,kernel: Option[BaseTIPLPluginIn.morphKernel],mapFun: (Iterable[T] => U)): RDD[(D3int,U)]
  }
  /**
   * Add the appropriate image types to the SparkContext
   */
  implicit class ImageFriendlySparkContext(sc: SparkContext) {
    val defMinPart = sc.defaultMinPartitions
    def tiffFolder(path: String)  = { // DTImg[U]
    		val rawImg = sc.newAPIHadoopFile(path, classOf[WholeTiffFileInputFormat], classOf[String], classOf[TSliceReader])
    		rawImg
    			//	.map(pair => pair._2.toString).setName(path)
    }
    def byteFolder(path: String) = {
      sc.newAPIHadoopFile(path, classOf[WholeByteInputFormat], classOf[String], classOf[Array[Byte]])
    }
  }
  implicit def rddToUnReadRDD(srd: RDD[(String,Array[Byte])]) = new UnreadTiffRDD(srd)
  implicit class TypedReader[T<: TImgRO](cImg: TImgRO) {
    def readSlice(sliceNumber: Int, asType: Int) = {
      TImgTools.isValidType(asType)
    	cImg.getPolyImage(asType,sliceNumber) match {
          case a: Array[Int] => a
          case a: Array[Boolean] => a
          case a: Array[Double] => a
          case a: Array[Byte] => a
          case a: Array[Char] => a
          case a: Array[Float] => a
          case a: Array[Short] => a
          case a: Array[Long] => a
          case _ => throw new IllegalArgumentException("Type Not Found:"+asType) 
        }
    }
  }
  def castAsImageFormat(obj: Any, asType: Int) = {
    TImgTools.isValidType(asType)
    asType match {
      case TImgTools.IMAGETYPE_BOOL => obj.asInstanceOf[Array[Boolean]]
      case TImgTools.IMAGETYPE_CHAR => obj.asInstanceOf[Array[Char]]
      case TImgTools.IMAGETYPE_SHORT => obj.asInstanceOf[Array[Short]]
      case TImgTools.IMAGETYPE_INT => obj.asInstanceOf[Array[Int]]
      case TImgTools.IMAGETYPE_LONG => obj.asInstanceOf[Array[Long]]
      case TImgTools.IMAGETYPE_FLOAT => obj.asInstanceOf[Array[Float]]
      case TImgTools.IMAGETYPE_DOUBLE => obj.asInstanceOf[Array[Double]]
      case _ => throw new IllegalArgumentException("Type Not Found:"+asType+" "+TImgTools.getImageTypeName(asType)) 
    }
  }
  implicit class SlicesToTImg[T<: TSliceReader](srd: RDD[(String,T)])(implicit lm: ClassTag[T])  {
    def loadAsLabels() = {
      val (imDim,firstSlice,imRdd) = loadSlices(TImgTools.IMAGETYPE_LONG)
      imRdd.mapValues{
        cBnd =>
          new TImgBlock(cBnd._1.asInstanceOf[Array[Long]],cBnd._2,cBnd._3)
      }
    }
    def loadAsValues() = {
      val (imDim,firstSlice,imRdd) = loadSlices(TImgTools.IMAGETYPE_DOUBLE)
      
      imRdd.mapValues{
        cBnd =>
          new TImgBlock(cBnd._1.asInstanceOf[Array[Double]],cBnd._2,cBnd._3)
      }
    }
    def loadAsBinary() = {
      transformSlices[Array[Boolean]](TImgTools.IMAGETYPE_BOOL,inObj => inObj.asInstanceOf[Array[Boolean]])
    }
    private[TIPLOps] def transformSlices[A<: Cloneable](asType: Int,transFcn: (Any => A)) = {
      val (imDim,firstSlice,imRdd) = loadSlices(asType)
      val outRdd = imRdd.mapValues{
        cBnd =>
          new TImgBlock(transFcn(cBnd._1),cBnd._2,cBnd._3)
      }
      DTImg.WrapRDD[A](firstSlice,outRdd,asType)
    }
    private[TIPLOps] def loadSlices(asType: Int) = {
      TImgTools.isValidType(asType)
      // sort by file name and then remove the filename
      val srdSorted = srd.sortByKey(true, srd.count.toInt).map(_._2)
      // keep the first element for reference
      val fst = srdSorted.first
      val pos = fst.getPos
      val dim = fst.getDim
      val timgDim = new D3int(dim.x,dim.y,srdSorted.count.toInt)
      val srdArr = srdSorted.
      map(cval => cval.polyReadImage(asType))
      val tbkCls = srdArr.first.getClass
      val srdFinal = srdArr.zipWithIndex.map{
            cBnd => 
              val cPos = new D3int(pos.x,pos.y,pos.z+cBnd._2.toInt)
              (cPos,(cBnd._1,pos,dim))
          }
      
      (timgDim,fst,srdFinal)
      }
    }
  
  /**
   * A version of D3float which can perform simple arithmatic
   */
  @serializable implicit class RichD3float(ip: D3float) {
	 def -(ip2: D3float) = {
	   new D3float(ip.x-ip2.x,ip.y-ip2.y,ip.z-ip2.z)
	 }
	 def +(ip2: D3float) = {
	   new D3float(ip.x+ip2.x,ip.y+ip2.y,ip.z+ip2.z)
	 }
	 def *(iv: Double) = {
	   new D3float(ip.x*iv,ip.y*iv,ip.z*iv)
	 }
  }
/**
   * A version of D3int which can perform simple arithmatic
   */
  @serializable implicit class RichD3int(ip: D3int) {
	 def -(ip2: D3int) = {
	   new D3int(ip.x-ip2.x,ip.y-ip2.y,ip.z-ip2.z)
	 }
	 def +(ip2: D3int) = {
	   new D3int(ip.x+ip2.x,ip.y+ip2.y,ip.z+ip2.z)
	 }
	 def *(iv: Int) = {
	   new D3int(ip.x*iv,ip.y*iv,ip.z*iv)
	 }
	 def *(iv: Float) = {
	   new D3float(ip.x*iv,ip.y*iv,ip.z*iv)
	 }
  }
  /**
   * A TImg class supporting both filters and IO
   */
  implicit class RichTImg(val inputImage: TImgRO) extends TImgRO.TImgOld {
    /**
     * The old reading functions
     */
    val fullTImg = new TImgRO.TImgFull(inputImage)
    @Override def getBoolArray(sliceNumber: Int) = {fullTImg.getBoolArray(sliceNumber)}
    @Override def getByteArray(sliceNumber: Int) = {fullTImg.getByteArray(sliceNumber)}
    @Override def getShortArray(sliceNumber: Int) = {fullTImg.getShortArray(sliceNumber)}
    @Override def getIntArray(sliceNumber: Int) = {fullTImg.getIntArray(sliceNumber)}
    @Override def getFloatArray(sliceNumber: Int) = {fullTImg.getFloatArray(sliceNumber)}
    /** Basic IO 
     *  
     */
    def write(path: String) = {
      TImgTools.WriteTImg(inputImage,path)
    }
    /**
     * The kVoronoi operation
     */
    def kvoronoi(mask: TImgRO): Array[TImg] = {
      val plugObj = new SKVoronoi
      plugObj.LoadImages(Array(inputImage,mask))
      plugObj.execute
      plugObj.ExportImages(mask)
    }
    def shapeAnalysis(outfile: String): Unit = {
      val plugObj = new ShapeAnalysis
      plugObj.LoadImages(Array(inputImage))
      plugObj.setParameter("-csvname="+outfile)
      plugObj.execute
    }
    def filter(size: D3int = new D3int(1,1,1), shape: morphKernel = fullKernel, filter: filterGenerator = null): TImgRO = {
      val plugObj = new VFilterScale
      plugObj.LoadImages(Array(inputImage))
      plugObj.neighborSize=size
      plugObj.neighborKernel=shape
      plugObj.scalingFilterGenerator = filter
      plugObj.execute
      plugObj.ExportImages(inputImage)(0)
    }
  }
}

