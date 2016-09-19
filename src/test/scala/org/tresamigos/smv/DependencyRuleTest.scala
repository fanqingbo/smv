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

import org.scalatest._
import org.tresamigos.smv._

package org.tresamigos.smv {
  /** A shortcut to save full SmvApp set-up during test.  Remove if it makes the tests too brittle */
  object DependencyTests {
    def stages(names: String*): SmvStages = new SmvStages(names.map(new SmvStage(_, None)))
    val TestStages = stages("org.tresamigos.smv.deptest01", "org.tresamigos.smv.deptest02")
  }

  abstract class DependencyTestModule(deps: Seq[SmvDataSet] = Seq.empty) extends SmvModule("Dependency test") {
    final override lazy val parentStage = DependencyTests.TestStages.findStageForDataSet(this)

    final override val requiresDS = deps
    final override def run(i: runParams) = null
  }

  class SameStageDependencyTest extends FlatSpec with Matchers {
    val target = SameStageDependency

    "Same-stage rule" should "pass for non-dependent modules" in {
      target.check(deptest01.input.I) shouldBe 'Empty
    }

    it should "pass for modules that only depend on modules in the same stage" in {
      target.check(deptest01.A) shouldBe 'Empty
    }

    it should "fail for modules that depend other stage" in {
      val res = target.check(deptest02.B)
      res shouldNot be('Empty)
      res.get shouldBe DependencyViolation(target.description, Seq(deptest01.A))
    }

    it should "allow module link to output from another stage" in {
      val res = target.check(deptest03.C)
      res shouldBe 'Empty
    }
  }

  import DependencyTests._

  /** Simple rule-compliant dependency */
  package deptest01 {
    object A extends DependencyTestModule(Seq(input.I)) with SmvOutput
    package input {
      object I extends DependencyTestModule
    }
  }

  /** Simple violation of same-stage rule */
  package deptest02 {
    object B extends DependencyTestModule(Seq(deptest01.A, input.I))
    package input {
      object I extends DependencyTestModule
    }
  }

  /** Cross-stage module link is okay */
  package deptest03 {
    object C extends DependencyTestModule(Seq(L, input.I))
    package input {
      object I extends DependencyTestModule
    }
    object L extends SmvModuleLink(deptest01.A)
  }
}
