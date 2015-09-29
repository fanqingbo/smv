/*
 * This file is licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tresamigos.smv.cds

import org.apache.spark.sql.types.DateUtils

import scala.math.floor

import org.apache.spark.sql.types._
import org.apache.spark.sql.catalyst.expressions._

import org.tresamigos.smv._

/**
 * SmvGDO - SMV GroupedData Operator
 *
 * Used with smvMapGroup method of SmvGroupedData.
 *
 * Examples:
 *   val res1 = df.smvGroupBy('k).smvMapGroup(gdo1).agg(sum('v) as 'sumv, sum('v2) as 'sumv2)
 *   val res2 = df.smvGroupBy('k).smvMapGroup(gdo2).toDF
 **/
private[smv] abstract class SmvGDO extends Serializable{
  def inGroupKeys: Seq[String]
  def createInGroupMapping(smvSchema:StructType): Iterable[Row] => Iterable[Row]
  def createOutSchema(inSchema: StructType): StructType
}

/**
 * Compute the quantile bin number within a group in a given DataFrame.
 * The algorithm assumes there are three columns in the input.
 * The value column is the column that the quantile bins will be computed.
 * The value column must be numeric (int, long, float, double).
 * The output will contain all the input columns plus value_total, value_rsum, and
 * value_quantile column with a value in the range 1 to num_bins.
 */
private[smv] class SmvQuantile(valueCol: String, numBins: Int) extends SmvGDO {

  val inGroupKeys = Nil

  def createOutSchema(inSchema: StructType) = {
    val oldFields = inSchema.fields
    val newFields = List(
      StructField(valueCol + "_total", DoubleType, true),
      StructField(valueCol + "_rsum", DoubleType, true),
      StructField(valueCol + "_quantile", IntegerType, true))
    StructType(oldFields ++ newFields)
  }

  /** bound bin number value to range [1,numBins] */
  private def binBound(binNum: Int) = {
    if (binNum < 1) 1 else if (binNum > numBins) numBins else binNum
  }

  /**
   * compute the quantile for a given group of rows (all rows are assumed to have the same group id)
   * Input: Array[Row(groupids*, keyid, value, value_double)]
   * Output: Array[Row(groupids*, keyid, value, value_total, value_rsum, value_quantile)]
   */
  def createInGroupMapping(inSchema:StructType): Iterable[Row] => Iterable[Row] = {
    val ordinal = inSchema.getIndices(valueCol)(0)
    val valueField = inSchema(valueCol)
    val getValueAsDouble: Row => Double = {r =>
      valueField.numeric.toDouble(r(ordinal))
    }

    {it: Iterable[Row] =>
      val inGroup = it.toSeq
      val valueTotal = inGroup.map(r => getValueAsDouble(r)).sum
      val binSize = valueTotal / numBins
      var runSum: Double = 0.0
      inGroup.sortBy(r => getValueAsDouble(r)).map{r =>
        runSum = runSum + getValueAsDouble(r)
        val bin = binBound(floor(runSum / binSize).toInt + 1)
        val newValsDouble = Seq(valueTotal, runSum)
        val newValsInt = Seq(bin)
        new GenericRow(Array[Any](r.toSeq ++ newValsDouble ++ newValsInt: _*))
      }
    }
  }
}

/**
 * User defined "chuck" mapping function
 * see the `chunkBy` and `chunkByPlus` method of [[org.tresamigos.smv.SmvDFHelper]] for details
 **/
@deprecated("will remove after 1.3")
case class SmvChunkUDF(
  para: Seq[Symbol],
  outSchema: StructType,
  eval: List[Seq[Any]] => List[Seq[Any]]
)

/* Add back chunkByPlus for project migration */
private[smv] class SmvChunkUDFGDO(cudf: SmvChunkUDF, isPlus: Boolean) extends SmvGDO {
  val inGroupKeys = Nil

  def createOutSchema(inSchema: StructType) = {
    if (isPlus)
      inSchema.mergeSchema(cudf.outSchema)
    else
      cudf.outSchema
  }

  def createInGroupMapping(inSchema: StructType) = {
    val ordinals = inSchema.getIndices(cudf.para.map{s => s.name}: _*)

    { it: Iterable[Row] =>
      val inGroup = it.toList
      val input = inGroup.map{r => ordinals.map{i => r(i)}.toSeq}
      val output = cudf.eval(input)
      if (isPlus) {
        inGroup.zip(output).map{case (orig, added) =>
          Row((orig.toSeq ++ added): _*)
        }
      } else {
        output.map{r => Row(r: _*)}
      }
    }
  }
}

private[smv] class FillPanelWithNull(t: String, p: panel.Panel, keys: Seq[String]) extends  SmvGDO {
  val inGroupKeys = Nil

  def createOutSchema(inSchema: StructType) = inSchema

  def createInGroupMapping(inSchema: StructType): Iterable[Row] => Iterable[Row] = {
    val keyOrdinals = inSchema.getIndices(keys: _*).toList
    val timeOrdinal = inSchema.getIndices(t)(0)
    println(timeOrdinal)
    println(inSchema.fields.size)
    val tmplt = new GenericMutableRow(inSchema.fields.size)
    var rows: Map[Int, Row] = Map()

    { it =>
      it.zipWithIndex.foreach { case (r, i) =>
        if (i == 0) {
          keyOrdinals.foreach(ki => tmplt.update(ki, r(ki)))
          rows = rows ++ p.createValues().map { rt =>
            tmplt.update(timeOrdinal, DateUtils.toJavaDate(rt))
            (rt, tmplt.copy())
          }
        }
        val rt = r(timeOrdinal) match {
          case d: java.sql.Date => DateUtils.fromJavaDate(d)
          case d: Int => d
          case _ => throw new IllegalArgumentException(s"types other than Date or Int are not supported")
        }
        if (p.hasInRange(rt)) rows = rows.updated(rt, r)
      }
      rows.values
    }
  }
}