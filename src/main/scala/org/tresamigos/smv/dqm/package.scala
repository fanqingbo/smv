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

package org.tresamigos.smv

import org.apache.spark.sql.Column
import org.apache.spark.sql.functions._
import scala.util.matching.Regex

/**
 * DQM (Data Quality Module) providing classes for DF data quality assurance
 *
 * Main class [[org.tresamigos.smv.dqm.SmvDQM]] can be used with the SmvApp/Module
 * Framework or on stand-alone DF.
 * With the SmvApp/Module framework, a `dqm` method is defined on the
 * [[org.tresamigos.smv.SmvDataSet]] level, an can be override to define DQM rules,
 * fixes and policies, which then will be automatically checked when the SmvDataSet
 * get resolved.
 *
 * For working on a stand-alone DF, please refer the SmvDQM class's documentation.
 **/
package object dqm {

  /** BoundRule requires `lower <= col < upper` */
  def BoundRule[T:Ordering](col: Column, lower: T, upper: T) = {
    DQMRule(col >= lower && col < upper, s"BoundRule(${col})", FailNone)
  }

  /** SetRule requires `col in set` */
  def SetRule(col: Column, set: Set[Any]) = {
    DQMRule(col.in(set.toSeq.map{lit(_)}: _*), s"SetRule(${col})", FailNone)
  }

  /** FormatRule requires `col matches fmt` */
  def FormatRule(col: Column, fmt: String) = {
    val check = udf({s: String => s.matches(fmt)})
    DQMRule(check(col), s"FormatRule(${col})", FailNone)
  }
}