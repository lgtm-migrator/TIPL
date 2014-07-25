package tipl.util
import scala.reflect.ClassTag
import scala.collection.JavaConversions._
import tipl.formats.TImgRO
import tipl.formats.TImg
import tipl.tools.BaseTIPLPluginIn._
import tipl.tools.VFilterScale

import tipl.tools.FilterScale.filterGenerator
import tipl.spark.SKVoronoi
import tipl.spark.ShapeAnalysis
import org.apache.spark.rdd.RDD
import tipl.tools.BaseTIPLPluginIn

import tipl.spark.DTImg
import tipl.spark.hadoop.WholeTiffFileInputFormat
import tipl.spark.hadoop.WholeByteInputFormat
import tipl.formats.TReader.TSliceReader

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
    override def getBoolArray(sliceNumber: Int) = {fullTImg.getBoolArray(sliceNumber)}
    override def getByteArray(sliceNumber: Int) = {fullTImg.getByteArray(sliceNumber)}
    override def getShortArray(sliceNumber: Int) = {fullTImg.getShortArray(sliceNumber)}
    override def getIntArray(sliceNumber: Int) = {fullTImg.getIntArray(sliceNumber)}
    override def getFloatArray(sliceNumber: Int) = {fullTImg.getFloatArray(sliceNumber)}
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

