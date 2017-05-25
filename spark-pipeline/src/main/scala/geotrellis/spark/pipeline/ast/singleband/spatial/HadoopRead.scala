package geotrellis.spark.pipeline.ast.singleband.spatial

import geotrellis.raster._
import geotrellis.spark.pipeline.ast.Read
import geotrellis.spark.pipeline.json
import geotrellis.vector._

import org.apache.spark.rdd.RDD

case class HadoopRead(read: json.ReadHadoop) extends Read[RDD[(ProjectedExtent, Tile)]] {
  def get: RDD[(ProjectedExtent, Tile)] = ???
}