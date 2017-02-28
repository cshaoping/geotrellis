/*
 * Copyright 2016 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.spark.io.avro.codecs

import geotrellis.proj4.CRS
import geotrellis.spark._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.avro.codecs.Implicits._
import geotrellis.vector._
import org.apache.avro._
import org.apache.avro.generic._
import org.apache.avro.util.Utf8


trait TemporalProjectedExtentCodec {
  implicit def temporalProjectedExtentCodec = new AvroRecordCodec[TemporalProjectedExtent] {
    def schema: Schema = SchemaBuilder
      .record("TemporalProjectedExtent").namespace("geotrellis.spark")
      .fields()
      .name("extent").`type`(extentCodec.schema).noDefault()
      .name("epsg").`type`().optional().intType()
      .name("proj4").`type`().optional().stringType()
      .name("instant").`type`().longType().noDefault()
      .endRecord()

    def encode(temporalProjectedExtent: TemporalProjectedExtent, rec: GenericRecord): Unit = {
      val crs = temporalProjectedExtent.crs
      rec.put("extent", extentCodec.encode(temporalProjectedExtent.extent))
      if(crs.epsgCode.isDefined) {
        rec.put("epsg", crs.epsgCode.get)
      }
      else {
        rec.put("proj4", crs.toProj4String)
      }
      rec.put("instant", temporalProjectedExtent.instant)
    }

    def decode(rec: GenericRecord): TemporalProjectedExtent = {

      val instant = rec[Long]("instant")
      val crs = if(rec[AnyRef]("epsg") != null) {
        val epsg = rec[Int]("epsg")
        CRS.fromEpsgCode(epsg)
      }
      else {
        val proj4 = rec[Utf8]("proj4")
        CRS.fromString(proj4.toString)
      }

      val extent = extentCodec.decode(rec[GenericRecord]("extent"))

      TemporalProjectedExtent(extent, crs, instant)
    }
  }
}

