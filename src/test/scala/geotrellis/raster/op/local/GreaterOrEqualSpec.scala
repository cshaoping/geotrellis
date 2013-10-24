package geotrellis.raster.op.local

import geotrellis._
import geotrellis.source._
import geotrellis.process._

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import geotrellis.testutil._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class GreaterOrEqualSpec extends FunSpec 
                   with ShouldMatchers 
                   with TestServer 
                   with RasterBuilders {
  describe("GreaterOrEqual") {
    it("checks int valued raster against int constant") {
      val r = positiveIntegerRaster
      val result = run(GreaterOrEqual(r,5))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          val z = r.get(col,row)
          val rz = result.get(col,row)
          if(z >= 5) rz should be (1)
          else rz should be (0)
        }
      }
    }

    it("checks int valued raster against double constant") {
      val r = probabilityRaster.map(_*100).convert(TypeInt)
      val result = run(GreaterOrEqual(r,69.0))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          val z = r.get(col,row)
          val rz = result.get(col,row)
          if(z >= 69) rz should be (1)
          else rz should be (0)
        }
      }
    }

    it("checks double valued raster against int constant") {
      val r = positiveIntegerRaster.convert(TypeDouble).mapDouble(_.toDouble)
      val result = run(GreaterOrEqual(r,5))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          val z = r.getDouble(col,row)
          val rz = result.get(col,row)
          if(z >= 5.0) rz should be (1)
          else rz should be (0)
        }
      }
    }

    it("checks double valued raster against double constant") {
      val r = probabilityRaster
      val result = run(GreaterOrEqual(r,0.69))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          val z = r.getDouble(col,row)
          val rz = result.getDouble(col,row)
          if(z >= 0.69) rz should be (1)
          else rz should be (0)
        }
      }
    }

    it("checks an integer raster against itself") {
      val r = positiveIntegerRaster
      val result = run(GreaterOrEqual(r,r))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          result.get(col,row) should be (1)
        }
      }
    }

    it("checks an integer raster against a different raster") {
      val r = positiveIntegerRaster
      val r2 = positiveIntegerRaster.map(_*2)
      val result = run(GreaterOrEqual(r,r2))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          result.get(col,row) should be (0)
        }
      }
    }

    it("checks a double raster against itself") {
      val r = probabilityRaster
      val result = run(GreaterOrEqual(r,r))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          result.get(col,row) should be (1)
        }
      }
    }

    it("checks a double raster against a different raster") {
      val r = probabilityRaster
      val r2 = positiveIntegerRaster.mapDouble(_*2.3)
      val result = run(GreaterOrEqual(r,r2))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          result.get(col,row) should be (0)
        }
      }
      val result2 = run(GreaterOrEqual(r2,r))
      for(col <- 0 until r.cols) {
        for(row <- 0 until r.rows) {
          result2.get(col,row) should be (1)
        }
      }
    }

    it("adds two tiled RasterDataSources correctly") {
      val rs1 = RasterDataSource("quad_tiled")
      val rs2 = RasterDataSource("quad_tiled2")

      getSource(rs1 >= rs2) match {
        case Complete(result,success) =>
//          println(success)
          for(row <- 0 until result.rasterExtent.rows) {
            for(col <- 0 until result.rasterExtent.cols) {
              result.get(col,row) should be (1)
            }
          }
        case Error(msg,failure) =>
          println(msg)
          println(failure)
          assert(false)
      }
    }

    it("adds two tiled unequalRasterDataSources correctly") {
      val rs1 = RasterDataSource("quad_tiled")
      val rs2 = RasterDataSource("quad_tiled2") + 1

      getSource(rs1 >= rs2) match {
        case Complete(result,success) =>
//          println(success)
          for(row <- 0 until result.rasterExtent.rows) {
            for(col <- 0 until result.rasterExtent.cols) {
              result.get(col,row) should be (0)
            }
          }
        case Error(msg,failure) =>
          println(msg)
          println(failure)
          assert(false)
      }

      getSource(rs2 >= rs1) match {
        case Complete(result,success) =>
//          println(success)
          for(row <- 0 until result.rasterExtent.rows) {
            for(col <- 0 until result.rasterExtent.cols) {
              result.get(col,row) should be (1)
            }
          }
        case Error(msg,failure) =>
          println(msg)
          println(failure)
          assert(false)
      }
    }

    it("adds three tiled RasterDataSources correctly") {
      val rs1 = createRasterDataSource(
        Array( NODATA,1,1, 1,1,1, 1,1,3,
               1,1,1, 1,1,1, 1,1,3,

               1,1,1, 1,1,1, 1,1,3,
               1,1,1, 1,1,1, 1,1,3),
        3,2,3,2)

      val rs2 = createRasterDataSource(
        Array( NODATA,1,2, 2,2,2, 2,2,2,
               1,1,2, 2,2,2, 2,2,2,

               1,1,1, 2,2,2, 2,2,2,
               1,1,1, 2,2,2, 2,2,2),
        3,2,3,2)

      val rs3 = createRasterDataSource(
        Array( NODATA,3,3, 3,3,3, 3,3,1,
               1,3,3, 3,3,3, 3,3,1,

               1,3,3, 3,3,3, 3,3,1,
               1,3,3, 3,3,3, 3,3,1),
        3,2,3,2)


      getSource(rs1 >= rs2 >= rs3) match {
        case Complete(result,success) =>
//          println(success)
          for(row <- 0 until 4) {
            for(col <- 0 until 9) {
              if(col == 0 || col == 8)
                result.get(col,row) should be (1)
              else
                result.get(col,row) should be (0)
            }
          }
        case Error(msg,failure) =>
          println(msg)
          println(failure)
          assert(false)
      }
    }
  }
}
