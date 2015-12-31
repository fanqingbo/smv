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

class ModuleCrcConsistencyTest extends SmvTestUtil {

  test("test moduleCrc changed or not") {
    object Module1 extends SmvModule("test Module1") {
      override def version() = 1
      override def requiresDS() = Nil
      override def run(i: runParams) = null
    }

    assert(Module1.datasetCRC === 2036878497l)
  }

  test("test moduleCrc on SmvFile"){
    object file extends SmvCsvFile("./" + testDataDir +  "CsvTest/test1", CsvAttributes.defaultCsvWithHeader)
    assert(file.datasetCRC === 3726969404l)
  }
}
